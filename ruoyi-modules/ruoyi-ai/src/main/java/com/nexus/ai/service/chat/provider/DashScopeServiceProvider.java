package com.nexus.ai.service.chat.provider;

import com.nexus.ai.service.model.AiProviderPolicyRegistry;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 阿里 DashScope（通义千问）协议策略（DASHSCOPE_NATIVE）—— 原生 SDK
 * <p>使用 langchain4j-community-dashscope 的 QwenStreamingChatModel / QwenChatModel</p>
 * <p>原生 SDK 不支持 returnThinking，推理模式对该厂商无效（{@link #supportsThinking()} 返回 false）</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Component
@RequiredArgsConstructor
public class DashScopeServiceProvider implements ChatServiceProvider {

    private final List<ChatModelListener> chatModelListeners;

    @Override
    public String getProtocol() {
        return AiProviderPolicyRegistry.DASHSCOPE_NATIVE;
    }

    @Override
    public boolean supportsThinking() {
        return false;
    }

    @Override
    public StreamingChatModel buildStreamingChatModel(AiModel config, AiChatRequest request) {
        float temperature = config.getTemperature() != null ? config.getTemperature().floatValue() : 0.7f;
        return QwenStreamingChatModel.builder()
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .temperature(temperature)
                .maxTokens(ChatServiceProvider.outputLimit(config, request))
                .listeners(chatModelListeners)
                .build();
    }

    @Override
    public ChatModel buildChatModel(AiModel config, double temperature) {
        // 注意：QwenChatModelBuilder 未提供 timeout 选项（langchain4j-community-dashscope
        // 1.22.0-beta32），超时由其内部 HTTP 客户端默认值决定；与
        // AbstractOpenAiCompatibleProvider 的 180s 显式超时不同，此处无法配置
        return QwenChatModel.builder()
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .temperature((float) temperature)
            .maxTokens(config.getMaxTokens())
            .listeners(chatModelListeners)
            .build();
    }
}
