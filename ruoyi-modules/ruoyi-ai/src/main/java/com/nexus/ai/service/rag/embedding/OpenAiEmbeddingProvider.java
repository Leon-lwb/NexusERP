package com.nexus.ai.service.rag.embedding;

import com.nexus.common.ai.domain.entity.AiModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.stereotype.Component;

/**
 * OpenAI 兼容接口 Embedding 构建
 * <p>适用于 OpenAI、DashScope、ZhiPu 等走 OpenAI 兼容协议的厂商</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Component
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    @Override
    public String getProviderType() {
        return "OPENAI";
    }

    @Override
    public EmbeddingModel build(AiModel config) {
        return OpenAiEmbeddingModel.builder()
            .baseUrl(config.getApiHost())
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .build();
    }
}
