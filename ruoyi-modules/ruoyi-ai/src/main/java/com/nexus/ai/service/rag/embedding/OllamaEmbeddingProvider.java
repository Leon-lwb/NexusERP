package com.nexus.ai.service.rag.embedding;

import com.nexus.common.ai.domain.entity.AiModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ollama Embedding 构建
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Component
public class OllamaEmbeddingProvider implements EmbeddingProvider {

    @Override
    public String getProviderType() {
        return "OLLAMA";
    }

    @Override
    public EmbeddingModel build(AiModel config) {
        return OllamaEmbeddingModel.builder()
            .baseUrl(config.getApiHost())
            .modelName(config.getModelName())
            .build();
    }

    @Override
    public EmbeddingModel build(AiModel config, List<EmbeddingModelListener> listeners) {
        return OllamaEmbeddingModel.builder()
            .baseUrl(config.getApiHost())
            .modelName(config.getModelName())
            .listeners(listeners)
            .build();
    }
}
