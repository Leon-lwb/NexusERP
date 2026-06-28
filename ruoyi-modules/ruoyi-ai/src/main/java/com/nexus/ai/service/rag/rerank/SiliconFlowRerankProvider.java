package com.nexus.ai.service.rag.rerank;

import com.nexus.common.ai.domain.entity.AiModel;
import org.springframework.stereotype.Component;

/**
 * 硅基流动（SiliconFlow）Rerank 厂商
 * <p>端点：{apiHost}/rerank（apiHost 通常为 https://api.siliconflow.cn/v1）</p>
 * <p>鉴权：Bearer apiKey</p>
 * <p>常用模型：BAAI/bge-reranker-v2-m3</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Component
public class SiliconFlowRerankProvider extends AbstractHttpRerankProvider {

    private static final String DEFAULT_URL = "https://api.siliconflow.cn/v1/rerank";

    @Override
    public String getProviderType() {
        return "SILICONFLOW";
    }

    @Override
    protected String buildUrl(AiModel config) {
        String apiHost = config.getApiHost();
        if (apiHost == null || apiHost.isBlank()) {
            return DEFAULT_URL;
        }
        if (apiHost.endsWith("/rerank")) {
            return apiHost;
        }
        if (apiHost.endsWith("/v1")) {
            return apiHost + "/rerank";
        }
        return apiHost.endsWith("/") ? apiHost + "rerank" : apiHost + "/rerank";
    }

    @Override
    protected String authHeader(AiModel config) {
        return "Bearer " + config.getApiKey();
    }
}
