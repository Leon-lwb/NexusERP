package com.nexus.ai.observability;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.embedding.listener.EmbeddingModelErrorContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext;
import lombok.extern.slf4j.Slf4j;

/**
 * EmbeddingModel 调用监听器
 * <p>实现 langchain4j 的 {@link EmbeddingModelListener} 接口（标注 {@code @Experimental}），记录 Embedding 调用的请求、响应和错误</p>
 * <p>langchain4j 1.18.0 起 {@code OpenAiEmbeddingModel.builder().listeners()} 和
 * {@code OllamaEmbeddingModel.builder().listeners()} 均已可用，此监听器已通过
 * {@code LangChain4jObservabilityConfig} 注册到 {@code EmbeddingProviderFactory}</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
@Experimental
public class AiEmbeddingModelListener implements EmbeddingModelListener {

    /**
     * Embedding 请求回调：记录请求的文本段数量（debug 级别）。
     *
     * @param requestContext 请求上下文
     */
    @Override
    public void onRequest(EmbeddingModelRequestContext requestContext) {
        log.debug("[EmbeddingModel] 请求: segments={}", requestContext.textSegments().size());
    }

    /**
     * Embedding 响应回调：记录生成的向量数量。
     *
     * @param responseContext 响应上下文
     */
    @Override
    public void onResponse(EmbeddingModelResponseContext responseContext) {
        var response = responseContext.response();
        log.info("[EmbeddingModel] 响应: embeddingCount={}", response.content().size());
    }

    /**
     * Embedding 错误回调：记录调用异常信息。
     *
     * @param errorContext 错误上下文
     */
    @Override
    public void onError(EmbeddingModelErrorContext errorContext) {
        log.error("[EmbeddingModel] 错误: segments={}, error={}",
            errorContext.textSegments().size(),
            errorContext.error().getMessage());
    }
}