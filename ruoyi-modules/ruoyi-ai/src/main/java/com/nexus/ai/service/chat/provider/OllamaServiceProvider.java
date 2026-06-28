package com.nexus.ai.service.chat.provider;

import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ollama 本地模型厂商策略 —— 原生 SDK
 * <p>Ollama 原生 SDK 不支持 returnThinking，推理模式对该厂商无效</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Component
@RequiredArgsConstructor
public class OllamaServiceProvider implements ChatServiceProvider {

    private final List<ChatModelListener> chatModelListeners;

    @Override
    public String getProviderType() {
        return "OLLAMA";
    }

    @Override
    public boolean supportsThinking() {
        return false;
    }

    @Override
    public StreamingChatModel buildStreamingChatModel(AiModel config, AiChatRequest request) {
        return OllamaStreamingChatModel.builder()
            .baseUrl(config.getApiHost())
            .modelName(config.getModelName())
            .temperature(config.getTemperature() != null ? config.getTemperature().doubleValue() : 0.7)
            .listeners(chatModelListeners)
            .build();
    }

    @Override
    public ChatModel buildChatModel(AiModel config, double temperature) {
        return OllamaChatModel.builder()
            .baseUrl(config.getApiHost())
            .modelName(config.getModelName())
            .temperature(temperature)
            .listeners(chatModelListeners)
            .build();
    }
}
