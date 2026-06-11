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
 * <p>注意：当前 langchain4j 1.13.0 的 {@code OpenAiEmbeddingModel.builder()} 不支持 {@code listeners()} 注入，
 * 此监听器暂未注册到模型构建器中，待 langchain4j 后续版本支持后启用</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
@Experimental
public class AiEmbeddingModelListener implements EmbeddingModelListener {

    @Override
    public void onRequest(EmbeddingModelRequestContext requestContext) {
        log.debug("[EmbeddingModel] 请求: segments={}", requestContext.textSegments().size());
    }

    @Override
    public void onResponse(EmbeddingModelResponseContext responseContext) {
        var response = responseContext.response();
        log.info("[EmbeddingModel] 响应: embeddingCount={}", response.content().size());
    }

    @Override
    public void onError(EmbeddingModelErrorContext errorContext) {
        log.error("[EmbeddingModel] 错误: segments={}, error={}",
            errorContext.textSegments().size(),
            errorContext.error().getMessage());
    }
}
