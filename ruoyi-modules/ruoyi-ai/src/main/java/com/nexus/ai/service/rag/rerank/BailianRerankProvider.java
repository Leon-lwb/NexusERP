package com.nexus.ai.service.rag.rerank;

import com.nexus.common.ai.domain.entity.AiModel;
import org.springframework.stereotype.Component;

/**
 * 阿里百炼（DashScope）Rerank 厂商
 * <p>端点：{apiHost}/compatible-api/v1/reranks（OpenAI 兼容）</p>
 * <p>鉴权：Bearer apiKey</p>
 * <p>百炼 Rerank 模型（如 gte-rerank）挂在 DashScope provider 下，故 providerType=DASHSCOPE</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Component
public class BailianRerankProvider extends AbstractHttpRerankProvider {

    @Override
    public String getProviderType() {
        return "DASHSCOPE";
    }

    @Override
    protected String buildUrl(AiModel config) {
        return config.getApiHost() + "/compatible-api/v1/reranks";
    }

    @Override
    protected String authHeader(AiModel config) {
        return "Bearer " + config.getApiKey();
    }
}
