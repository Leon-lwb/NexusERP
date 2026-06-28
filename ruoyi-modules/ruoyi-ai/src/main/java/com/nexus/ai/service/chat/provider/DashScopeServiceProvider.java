package com.nexus.ai.service.chat.provider;

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
 * 阿里 DashScope（通义千问）厂商策略 —— 原生 SDK
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
    public String getProviderType() {
        return "DASHSCOPE";
    }

    @Override
    public boolean supportsThinking() {
        return false;
    }

    @Override
    public StreamingChatModel buildStreamingChatModel(AiModel config, AiChatRequest request) {
        return QwenStreamingChatModel.builder()
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .listeners(chatModelListeners)
            .build();
    }

    @Override
    public ChatModel buildChatModel(AiModel config, double temperature) {
        return QwenChatModel.builder()
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .build();
    }
}
