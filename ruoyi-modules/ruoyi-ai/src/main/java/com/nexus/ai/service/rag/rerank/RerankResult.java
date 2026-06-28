package com.nexus.ai.service.rag.rerank;

/**
 * Rerank 单条结果
 *
 * @param index          原始文档索引
 * @param relevanceScore 相关性分数（0-1，越大越相关）
 * @param document       原始文档文本
 * @author NexusERP
 * @since 2.0.0
 */
public record RerankResult(int index, double relevanceScore, String document) {
}
