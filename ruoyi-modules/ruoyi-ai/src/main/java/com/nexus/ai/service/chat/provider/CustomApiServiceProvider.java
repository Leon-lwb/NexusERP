package com.nexus.ai.service.chat.provider;

import org.springframework.stereotype.Component;

/**
 * 自定义 API 厂商策略（OpenAI 兼容接口）
 * <p>适用于仅通过通用 HTTP 协议接入的第三方大模型服务，通过 apiHost/apiKey/modelName 复用</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Component
public class CustomApiServiceProvider extends AbstractOpenAiCompatibleProvider {

    @Override
    public String getProviderType() {
        return "CUSTOM_API";
    }
}
