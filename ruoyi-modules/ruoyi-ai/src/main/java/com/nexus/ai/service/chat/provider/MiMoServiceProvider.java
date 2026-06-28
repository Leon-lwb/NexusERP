package com.nexus.ai.service.chat.provider;

import org.springframework.stereotype.Component;

/**
 * 小米 MiMo 厂商策略（OpenAI 兼容接口）
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Component
public class MiMoServiceProvider extends AbstractOpenAiCompatibleProvider {

    @Override
    public String getProviderType() {
        return "XIAOMI";
    }
}
