package com.nexus.ai.service.rag.embedding;

import com.nexus.common.ai.domain.entity.AiModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;

import java.util.List;

/**
 * 嵌入模型构建策略接口
 * <p>不同厂商实现各自的构建逻辑，通过 Spring 自动注册</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
public interface EmbeddingProvider {

    /**
     * 获取厂商类型标识
     *
     * @return 厂商类型（如 OPENAI、OLLAMA、DASHSCOPE、ZHIPU）
     */
    String getProviderType();

    /**
     * 构建 EmbeddingModel 实例
     *
     * @param config 模型配置
     * @return EmbeddingModel 实例
     */
    EmbeddingModel build(AiModel config);

    /**
     * 构建 EmbeddingModel 实例（含监听器）
     * <p>默认实现忽略监听器，子类可覆写以注入监听器</p>
     *
     * @param config    模型配置
     * @param listeners Embedding 监听器列表
     * @return EmbeddingModel 实例
     */
    default EmbeddingModel build(AiModel config, List<EmbeddingModelListener> listeners) {
        return build(config);
    }
}
