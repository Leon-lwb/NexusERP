package com.nexus.ai.service.chat.provider;

import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

/**
 * AI 调用协议 ChatModel 构建策略
 * <p>每个<b>协议</b>一个实现，由 {@link ChatServiceProviderFactory} 按 {@link #getProtocol()}
 * 自动发现并注册；运行时按模型配置的 api_protocol（缺省时按厂商默认协议）路由。</p>
 * <p>协议码对应 ai_model.api_protocol 字段（字典 ai_model_api_protocol，
 * 见 {@link com.nexus.common.ai.enums.AiModelProtocolEnum}）：一个协议实现覆盖全部
 * 兼容厂商（如 OPENAI_CHAT_COMPLETIONS 同时覆盖 OpenAI/DeepSeek/MiniMax/中转站等），
 * 厂商差异仅体现在 apiHost/apiKey/modelName 配置上。</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
public interface ChatServiceProvider {

    /**
     * 协议编码，对应 ai_model.api_protocol 字典值（大写）
     *
     * @return 协议编码
     */
    String getProtocol();

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
     * 构建同步模型（Agent 模式用）
     * <p>每个协议实现必须提供（抽象方法，防止未来适配器漏写后静默回退 OpenAI 行为）；
     * OpenAI Chat Completions 协议复用 {@link AbstractOpenAiCompatibleProvider} 的实现，
     * 其余协议用各自 SDK 的同步模型。</p>
     *
     * @param config      模型配置
     * @param temperature 温度参数
     * @return 同步模型实例
     */
    ChatModel buildChatModel(AiModel config, double temperature);

    /**
     * 是否支持推理 token 流（returnThinking）
     * <p>OpenAI Chat Completions / Responses / Anthropic Messages / Gemini
     * 等协议返回 true（Task 4 新适配器已支持）；Ollama/DashScope/ZhiPu 原生 SDK 协议返回 false</p>
     *
     * @return 是否支持推理
     */
    default boolean supportsThinking() {
        return true;
    }
}
