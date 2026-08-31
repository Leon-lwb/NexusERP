package com.nexus.ai.service.chat.provider;

import com.nexus.ai.service.model.AiProviderPolicyRegistry;
import org.springframework.stereotype.Component;

/**
 * OpenAI Chat Completions 协议策略（协议实现唯一，覆盖全部 OpenAI 兼容厂商）
 * <p>协议 OPENAI_CHAT_COMPLETIONS（/v1/chat/completions，业界通用）：OpenAI、DeepSeek、
 * MiniMax、小米、方舟、Moonshot、SiliconFlow、Mistral、xAI、OpenRouter、OpenCode Go、
 * 自定义 API 等厂商的对话模型全部经本策略构建，厂商差异只体现在
 * ai_provider.api_host / api_key 与 ai_model.model_name 配置上。</p>
 * <p>构建逻辑复用 {@link AbstractOpenAiCompatibleProvider}（含 returnThinking 推理流、
 * 请求级 maxOutputTokens 封顶、180s 读超时、Token 用量 listeners）。</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Component
public class OpenAiServiceProvider extends AbstractOpenAiCompatibleProvider {

    @Override
    public String getProtocol() {
        return AiProviderPolicyRegistry.OPENAI_CHAT_COMPLETIONS;
    }
}
