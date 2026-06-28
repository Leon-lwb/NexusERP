package com.nexus.ai.service.chat.provider;

import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * OpenAI 兼容类厂商构建基类
 * <p>适用于 OpenAI/DeepSeek/MiniMax/MiMo/CustomApi 等 OpenAI 兼容接口厂商</p>
 * <p>子类只需实现 {@link #getProviderType()}，构建逻辑复用本类</p>
 * <p>支持 returnThinking 推理 token 流（{@link #supportsThinking()} 默认 true）</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
public abstract class AbstractOpenAiCompatibleProvider implements ChatServiceProvider {

    /**
     * Spring 收集的 ChatModelListener 列表（含 AiChatModelListener，用于 Token 用量采集）
     */
    @Autowired
    protected List<ChatModelListener> chatModelListeners;

    @Override
    public StreamingChatModel buildStreamingChatModel(AiModel config, AiChatRequest request) {
        boolean thinking = Boolean.TRUE.equals(request.getEnableThinking()) && supportsThinking();
        double temperature = config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.7;
        return OpenAiStreamingChatModel.builder()
            .baseUrl(normalizeBaseUrl(config.getApiHost()))
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .temperature(temperature)
            .maxTokens(config.getMaxTokens())
            .listeners(chatModelListeners)
            .returnThinking(thinking)
            .build();
    }

    @Override
    public ChatModel buildChatModel(AiModel config, double temperature) {
        return OpenAiChatModel.builder()
            .baseUrl(normalizeBaseUrl(config.getApiHost()))
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .temperature(temperature)
            .maxTokens(config.getMaxTokens())
            .listeners(chatModelListeners)
            .build();
    }

    /**
     * 去除 baseUrl 末尾的 /，避免拼接路径时出现双斜杠
     *
     * @param baseUrl 原始地址
     * @return 规范化后的地址
     */
    protected String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("API 请求地址(apiHost)不能为空");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
