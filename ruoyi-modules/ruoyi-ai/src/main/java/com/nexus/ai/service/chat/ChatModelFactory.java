package com.nexus.ai.service.chat;

import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.service.IAiModelService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 模型工厂（带缓存）
 * <p>根据数据库配置动态构建 ChatModel 和 StreamingChatModel，避免每次请求重建实例</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatModelFactory {

    private final IAiModelService aiModelService;

    /** 同步模型缓存（key = modelId） */
    private final ConcurrentHashMap<Long, ChatModel> chatModelCache = new ConcurrentHashMap<>();

    /** 流式模型缓存（key = modelId） */
    private final ConcurrentHashMap<Long, StreamingChatModel> streamingModelCache = new ConcurrentHashMap<>();

    /**
     * 获取同步模型（带缓存）
     *
     * @param modelId 模型ID
     * @return ChatModel 实例
     */
    public ChatModel getChatModel(Long modelId) {
        return chatModelCache.computeIfAbsent(modelId, id -> {
            AiModel config = aiModelService.selectModelById(id);
            return buildChatModel(config);
        });
    }

    /**
     * 获取流式模型（带缓存）
     *
     * @param modelId 模型ID
     * @return StreamingChatModel 实例
     */
    public StreamingChatModel getStreamingChatModel(Long modelId) {
        return streamingModelCache.computeIfAbsent(modelId, id -> {
            AiModel config = aiModelService.selectModelById(id);
            return buildStreamingChatModel(config);
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
     * 清除指定模型缓存（模型配置变更时调用）
     *
     * @param modelId 模型ID
     */
    public void evictCache(Long modelId) {
        chatModelCache.remove(modelId);
        streamingModelCache.remove(modelId);
    }

    /**
     * 清除全部模型缓存
     */
    public void evictAllCache() {
        chatModelCache.clear();
        streamingModelCache.clear();
    }

    /**
     * 构建同步模型
     * <p>目前所有厂商均走 OpenAI 兼容接口，后续按 providerType 分支处理</p>
     *
     * @param config 模型配置
     * @return ChatModel 实例
     */
    private ChatModel buildChatModel(AiModel config) {
        return OpenAiChatModel.builder()
            .baseUrl(config.getApiHost())
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .temperature(config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.7)
            .maxTokens(config.getMaxTokens())
            .build();
    }

    /**
     * 构建流式模型（SSE 流式对话必须使用）
     *
     * @param config 模型配置
     * @return StreamingChatModel 实例
     */
    private StreamingChatModel buildStreamingChatModel(AiModel config) {
        return OpenAiStreamingChatModel.builder()
            .baseUrl(config.getApiHost())
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .temperature(config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.7)
            .maxTokens(config.getMaxTokens())
            .build();
    }
}
