package com.nexus.ai.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nexus.common.ai.domain.entity.AiMessage;
import com.nexus.common.ai.mapper.AiMessageMapper;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

// 同名类型说明：本文件同时使用 com.nexus.common.ai.domain.entity.AiMessage 和
// dev.langchain4j.data.message.AiMessage，后者保留全限定名以区分。

/**
 * 会话记忆持久化存储
 *
 * <p>将聊天消息持久化到 ai_message 表，支持多轮对话上下文记忆
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersistentChatMemoryStore implements ChatMemoryStore {

    /** 聊天消息表 Mapper，负责会话消息读写与去重。 */
    private final AiMessageMapper aiMessageMapper;

    /**
     * 内存缓存：sessionId -> 最近一次保存的 ASSISTANT 消息内容，避免每次 updateMessages 都查库去重。
     *
     * <p>有界 + 2h 访问过期：会话被放弃但不显式删除时，条目自动逐出，避免长期运行内存泄漏。 缓存语义为「最近一次保存内容」，丢失后可从 DB 重建，加 TTL 安全。
     */
    private final Cache<Long, String> lastSavedContentCache =
            Caffeine.newBuilder().maximumSize(1000).expireAfterAccess(2, TimeUnit.HOURS).build();

    /**
     * 根据 sessionId 获取历史消息，转换为 LangChain4j ChatMessage 列表
     *
     * @param memoryId 会话ID（即 sessionId）
     * @return ChatMessage 列表
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Long sessionId = toLong(memoryId);
        if (sessionId == null) {
            return new ArrayList<>();
        }

        List<AiMessage> entities = aiMessageMapper.selectList(
            new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getSessionId, sessionId)
                .orderByAsc(AiMessage::getCreateTime)
        );

        List<ChatMessage> messages = new ArrayList<>();
        for (AiMessage entity : entities) {
            String role = entity.getRole();
            if (role == null) {
                log.warn("消息角色为空, messageId={}", entity.getId());
                continue;
            }
            switch (role) {
                case "USER" -> messages.add(UserMessage.from(entity.getContent()));
                case "ASSISTANT" -> messages.add(dev.langchain4j.data.message.AiMessage.from(entity.getContent()));
                case "SYSTEM" -> messages.add(SystemMessage.from(entity.getContent()));
                default -> log.warn("未知的消息角色: {}", role);
            }
        }
        return messages;
    }

    /**
     * 更新消息（增量保存 AI 回复）
     * <p>LangChain4j 会在对话过程中调用此方法更新消息列表。
     * 只保存最后一条纯文本 AI 消息，过滤工具调用请求，并与数据库去重。</p>
     *
     * @param memoryId 会话ID
     * @param messages  消息列表
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        Long sessionId = toLong(memoryId);
        if (sessionId == null) {
            return;
        }

        ChatMessage lastMessage = messages.get(messages.size() - 1);
        if (!(lastMessage instanceof dev.langchain4j.data.message.AiMessage aiMsg)) {
            return;
        }

        // 过滤包含工具调用请求的中间消息，只保存最终文本回复
        if (aiMsg.hasToolExecutionRequests()) {
            log.debug("跳过工具调用请求消息, memoryId={}", memoryId);
            return;
        }

        String content = aiMsg.text();
        if (content == null || content.isEmpty()) {
            return;
        }

        // 去重：优先使用内存缓存，缓存未命中时查库
        String cachedContent = lastSavedContentCache.getIfPresent(sessionId);
        if (cachedContent != null && cachedContent.equals(content)) {
            log.debug("AI 回复已存在（缓存命中），跳过重复保存, sessionId={}", sessionId);
            return;
        }
        if (cachedContent == null) {
            // 缓存未命中，查库确认
            List<AiMessage> existing = aiMessageMapper.selectList(
                new LambdaQueryWrapper<AiMessage>()
                    .eq(AiMessage::getSessionId, sessionId)
                    .eq(AiMessage::getRole, "ASSISTANT")
                    .orderByDesc(AiMessage::getCreateTime)
                    .last("LIMIT 1")
            );
            if (!existing.isEmpty() && existing.get(0).getContent().equals(content)) {
                log.debug("AI 回复已存在，跳过重复保存, sessionId={}", sessionId);
                lastSavedContentCache.put(sessionId, content);
                return;
            }
        }

        AiMessage entity = new AiMessage();
        entity.setSessionId(sessionId);
        entity.setRole("ASSISTANT");
        entity.setContent(content);
        aiMessageMapper.insert(entity);
        lastSavedContentCache.put(sessionId, content);
        log.debug("PersistentChatMemoryStore 自动保存 AI 回复, sessionId={}, length={}", sessionId, content.length());
    }

    /**
     * 删除会话消息
     *
     * @param memoryId 会话ID
     */
    @Override
    public void deleteMessages(Object memoryId) {
        Long sessionId = toLong(memoryId);
        if (sessionId != null) {
            aiMessageMapper.delete(
                new LambdaQueryWrapper<AiMessage>()
                    .eq(AiMessage::getSessionId, sessionId)
            );
            lastSavedContentCache.invalidate(sessionId);
        }
    }

    private Long toLong(Object memoryId) {
        if (memoryId instanceof Long l) {
            return l;
        }
        if (memoryId instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(memoryId.toString());
        } catch (NumberFormatException e) {
            log.warn("无法将 memoryId 转换为 Long: {}", memoryId);
            return null;
        }
    }
}
