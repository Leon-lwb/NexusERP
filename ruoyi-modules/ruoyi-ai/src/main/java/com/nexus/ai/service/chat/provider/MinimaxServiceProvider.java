package com.nexus.ai.service.chat.provider;

import org.springframework.stereotype.Component;

/**
 * MiniMax 厂商策略（OpenAI 兼容接口）
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Component
public class MinimaxServiceProvider extends AbstractOpenAiCompatibleProvider {

    @Override
    public String getProviderType() {
        return "MINIMAX";
    }
}
