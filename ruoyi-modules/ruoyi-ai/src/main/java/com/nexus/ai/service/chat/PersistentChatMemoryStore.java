package com.nexus.ai.service.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

/**
 * 会话记忆持久化存储
 * <p>将聊天消息持久化到 ai_message 表，支持多轮对话上下文记忆</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PersistentChatMemoryStore implements ChatMemoryStore {

    private final AiMessageMapper aiMessageMapper;

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
     * <p>LangChain4j 会在对话过程中调用此方法更新消息列表，
     * 我们只保存最后一条 AI 消息（增量保存）</p>
     *
     * @param memoryId 会话ID
     * @param messages  消息列表
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // LangChain4j 会在对话过程中多次调用此方法
        // 我们不在每次调用时都写入数据库，而是在 AiServiceFacade 中显式保存
        // 此方法仅记录日志，实际保存在 AiServiceFacade 中处理
        log.debug("PersistentChatMemoryStore.updateMessages called, memoryId={}, messageCount={}", memoryId, messages.size());
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
