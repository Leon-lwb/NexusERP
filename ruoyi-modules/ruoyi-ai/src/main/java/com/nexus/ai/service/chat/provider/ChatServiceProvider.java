package com.nexus.ai.service.chat.provider;

import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

/**
 * AI 厂商 ChatModel 构建策略
 * <p>每个厂商一个实现，由 {@link ChatServiceProviderFactory} 按 {@link #getProviderType()} 自动发现并注册</p>
 * <p>对应 ai_provider.provider_type 字段（大写）</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
public interface ChatServiceProvider {

    /**
     * 厂商编码，对应 ai_provider.provider_type（大写）
     *
     * @return 厂商编码
     */
    String getProviderType();

    /**
     * 构建流式模型（含推理维度）
     * <p>实现应依据 {@link AiChatRequest#getEnableThinking()} 与 {@link #supportsThinking()}
     * 决定是否启用 returnThinking（推理 token 流）</p>
     *
     * @param config  模型配置（含 apiHost/apiKey/modelName/providerType 等已填充的瞬态字段）
     * @param request 聊天请求（含 enableThinking 开关）
     * @return 流式模型实例
     */
    StreamingChatModel buildStreamingChatModel(AiModel config, AiChatRequest request);

    /**
     * 构建同步模型（Agent 模式用，默认 OpenAI 兼容）
     * <p>非 OpenAI 兼容厂商（Ollama/DashScope/ZhiPu）应覆写此方法以使用原生 SDK 同步模型</p>
     *
     * @param config      模型配置
     * @param temperature 温度参数
     * @return 同步模型实例
     */
    default ChatModel buildChatModel(AiModel config, double temperature) {
        return dev.langchain4j.model.openai.OpenAiChatModel.builder()
            .baseUrl(config.getApiHost())
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .temperature(temperature)
            .maxTokens(config.getMaxTokens())
            .build();
    }

    /**
     * 是否支持推理 token 流（returnThinking）
     * <p>OpenAI 兼容类厂商返回 true；Ollama/DashScope/Qwen/ZhiPu 等原生 SDK 厂商返回 false</p>
     *
     * @return 是否支持推理
     */
    default boolean supportsThinking() {
        return true;
    }
}
