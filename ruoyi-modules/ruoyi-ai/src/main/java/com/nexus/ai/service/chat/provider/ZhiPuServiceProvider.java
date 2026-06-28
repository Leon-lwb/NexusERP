package com.nexus.ai.service.chat.provider;

import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;
import dev.langchain4j.community.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.community.model.zhipu.ZhipuAiStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 智谱 AI（GLM）厂商策略 —— 原生 SDK
 * <p>使用 langchain4j-community-zhipu-ai 的 ZhipuAiStreamingChatModel / ZhipuAiChatModel</p>
 * <p>注意：智谱 builder 用 {@code .model()} 而非 {@code .modelName()}（与其他厂商不同）</p>
 * <p>原生 SDK 不支持 returnThinking，推理模式对该厂商无效</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Component
@RequiredArgsConstructor
public class ZhiPuServiceProvider implements ChatServiceProvider {

    private final List<ChatModelListener> chatModelListeners;

    @Override
    public String getProviderType() {
        return "ZHIPU";
    }

    @Override
    public boolean supportsThinking() {
        return false;
    }

    @Override
    public StreamingChatModel buildStreamingChatModel(AiModel config, AiChatRequest request) {
        // 智谱用 .model() 而非 .modelName()
        return ZhipuAiStreamingChatModel.builder()
            .apiKey(config.getApiKey())
            .model(config.getModelName())
            .listeners(chatModelListeners)
            .build();
    }

    @Override
    public ChatModel buildChatModel(AiModel config, double temperature) {
        // 智谱用 .model() 而非 .modelName()
        return ZhipuAiChatModel.builder()
            .apiKey(config.getApiKey())
            .model(config.getModelName())
            .build();
    }
}
