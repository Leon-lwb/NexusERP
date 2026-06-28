package com.nexus.ai.service.chat;

import com.nexus.ai.service.chat.provider.ChatServiceProviderFactory;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.ai.service.IAiModelService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 模型工厂（带缓存 + 厂商策略路由）
 * <p>根据数据库配置动态构建 ChatModel 和 StreamingChatModel，构建逻辑委托给
 * {@link ChatServiceProviderFactory}（按 providerType 路由到对应厂商策略实现）</p>
 * <p>缓存职责：
 * <ul>
 *   <li>同步模型按 modelId 缓存</li>
 *   <li>流式模型按 {@code modelId + ":" + (enableThinking?"T":"F")} 缓存，避免推理/非推理实例串用</li>
 *   <li>Agent 专用同步模型按 modelId 缓存（低 temperature）</li>
 * </ul>
 * 模型配置变更时由 {@code AiModelChangeListener} 监听 {@code AiModelChangeEvent} 调用 {@link #evictCache(Long)} 清除</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatModelFactory {

    private final IAiModelService aiModelService;
    private final ChatServiceProviderFactory providerFactory;

    /** 同步模型缓存（key = modelId） */
    private final ConcurrentHashMap<Long, ChatModel> chatModelCache = new ConcurrentHashMap<>();

    /** 流式模型缓存（key = modelId + ":" + (enableThinking?"T":"F")） */
    private final ConcurrentHashMap<String, StreamingChatModel> streamingModelCache = new ConcurrentHashMap<>();

    /** Agent 专用同步模型缓存（key = modelId，使用低 temperature） */
    private final ConcurrentHashMap<Long, ChatModel> agentModelCache = new ConcurrentHashMap<>();

    /** Agent 模式 temperature（低温度提高 SQL 生成准确性） */
    private static final double AGENT_TEMPERATURE = 0.1;

    /**
     * 获取同步模型（带缓存）
     *
     * @param modelId 模型ID
     * @return ChatModel 实例
     */
    public ChatModel getChatModel(Long modelId) {
        return chatModelCache.computeIfAbsent(modelId, id -> {
            AiModel config = aiModelService.selectModelById(id);
            if (config == null) {
                throw new IllegalArgumentException("未找到模型配置，modelId=" + id);
            }
            return providerFactory.get(config.getProviderType()).buildChatModel(config, defaultTemperature(config));
        });
    }

    /**
     * 获取流式模型（带缓存，含推理维度）
     * <p>缓存 key 含 enableThinking，避免推理模型实例（returnThinking=true）与非推理实例串用</p>
     *
     * @param modelId 模型ID
     * @param request 聊天请求（含 enableThinking 开关）
     * @return StreamingChatModel 实例
     */
    public StreamingChatModel getStreamingChatModel(Long modelId, AiChatRequest request) {
        boolean thinking = request != null && Boolean.TRUE.equals(request.getEnableThinking());
        String key = modelId + ":" + (thinking ? "T" : "F");
        return streamingModelCache.computeIfAbsent(key, k -> {
            AiModel config = aiModelService.selectModelById(modelId);
            if (config == null) {
                throw new IllegalArgumentException("未找到模型配置，modelId=" + modelId);
            }
            return providerFactory.get(config.getProviderType()).buildStreamingChatModel(config, request);
        });
    }

    /**
     * 获取流式模型（兼容旧调用，等价于 enableThinking=false）
     *
     * @param modelId 模型ID
     * @return StreamingChatModel 实例
     */
    public StreamingChatModel getStreamingChatModel(Long modelId) {
        return getStreamingChatModel(modelId, new AiChatRequest());
    }

    /**
     * 获取 Agent 专用同步模型（低 temperature，提高 SQL 生成准确性）
     *
     * @param modelId 模型ID
     * @return ChatModel 实例（temperature=0.1）
     */
    public ChatModel getAgentChatModel(Long modelId) {
        return agentModelCache.computeIfAbsent(modelId, id -> {
            AiModel config = aiModelService.selectModelById(id);
            if (config == null) {
                throw new IllegalArgumentException("未找到模型配置，modelId=" + id);
            }
            return providerFactory.get(config.getProviderType()).buildChatModel(config, AGENT_TEMPERATURE);
        });
    }

    /**
     * 根据模型名称获取同步模型
     *
     * @param modelName 模型名称
     * @return ChatModel 实例
     */
    public ChatModel getChatModelByName(String modelName) {
        AiModel config = aiModelService.selectModelByName(modelName);
        if (config == null) {
            throw new IllegalArgumentException("未找到模型配置: " + modelName);
        }
        return getChatModel(config.getId());
    }

    /**
     * 清除指定模型缓存（模型配置变更时由 AiModelChangeListener 调用）
     * <p>streamingModelCache 需清除 modelId 对应的 T/F 两个键</p>
     *
     * @param modelId 模型ID
     */
    public void evictCache(Long modelId) {
        String prefix = modelId + ":";
        streamingModelCache.keySet().removeIf(k -> k.startsWith(prefix));
        chatModelCache.remove(modelId);
        agentModelCache.remove(modelId);
    }

    /**
     * 清除全部模型缓存
     */
    public void evictAllCache() {
        streamingModelCache.clear();
        chatModelCache.clear();
        agentModelCache.clear();
    }

    /**
     * 取模型配置的默认温度（null 时回退 0.7）
     */
    private double defaultTemperature(AiModel config) {
        return config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.7;
    }
}
