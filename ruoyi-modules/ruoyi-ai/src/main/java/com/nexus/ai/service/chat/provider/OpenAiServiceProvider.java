package com.nexus.ai.service.chat.provider;

import org.springframework.stereotype.Component;

/**
 * OpenAI 厂商策略
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Component
public class OpenAiServiceProvider extends AbstractOpenAiCompatibleProvider {

    @Override
    public String getProviderType() {
        return "OPENAI";
    }
}
