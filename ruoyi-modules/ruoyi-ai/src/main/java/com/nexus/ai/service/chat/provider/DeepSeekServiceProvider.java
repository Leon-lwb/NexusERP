package com.nexus.ai.service.chat.provider;

import org.springframework.stereotype.Component;

/**
 * DeepSeek 厂商策略（OpenAI 兼容接口）
 * <p>支持推理 token 流（DeepSeek-R1 等推理模型）</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Component
public class DeepSeekServiceProvider extends AbstractOpenAiCompatibleProvider {

    @Override
    public String getProviderType() {
        return "DEEPSEEK";
    }
}
