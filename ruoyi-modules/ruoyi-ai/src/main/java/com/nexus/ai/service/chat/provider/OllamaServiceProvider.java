package com.nexus.ai.service.chat.provider;

import com.nexus.ai.service.model.AiProviderPolicyRegistry;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Ollama 本地模型协议策略（OLLAMA_NATIVE）—— 原生 SDK
 * <p>Ollama 原生 SDK 不支持 returnThinking，推理模式对该厂商无效</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Component
@RequiredArgsConstructor
public class OllamaServiceProvider implements ChatServiceProvider {

    private final List<ChatModelListener> chatModelListeners;

    /** Ollama 单次调用超时秒数（本地推理模型如 deepseek-r1 较慢，设大避免 timeout） */
    private static final long OLLAMA_TIMEOUT_SECONDS = 300;

    @Override
    public String getProtocol() {
        return AiProviderPolicyRegistry.OLLAMA_NATIVE;
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
            .numPredict(config.getMaxTokens())
            .timeout(Duration.ofSeconds(OLLAMA_TIMEOUT_SECONDS))
            .listeners(chatModelListeners)
            .build();
    }

    @Override
    public ChatModel buildChatModel(AiModel config, double temperature) {
        return OllamaChatModel.builder()
            .baseUrl(config.getApiHost())
            .modelName(config.getModelName())
            .temperature(temperature)
            .numPredict(config.getMaxTokens())
            .timeout(Duration.ofSeconds(OLLAMA_TIMEOUT_SECONDS))
            .listeners(chatModelListeners)
            .build();
    }
}
