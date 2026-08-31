package com.nexus.ai.service.rag.rerank;

import com.nexus.common.ai.domain.entity.AiModel;

import java.util.List;

/**
 * 专用 Rerank 厂商策略（Cross-Encoder 重排 API）
 * <p>替代 RerankService 的 LLM-as-judge 兜底方案，由 {@link RerankProviderFactory} 按
 * providerType 自动发现</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
public interface RerankProvider {

    /**
     * 厂商编码，对应 ai_provider.provider_type（大写）
     * <p>百炼 Rerank 挂在 DashScope 下 → DASHSCOPE；智谱 → ZHIPU；硅基流动 → SILICONFLOW</p>
     *
     * @return 厂商编码
     */
    String getProviderType();

    /**
     * 对 documents 按 query 相关性重排
     *
     * @param query    查询文本
     * @param documents 候选文档列表
     * @param topN     返回 Top N
     * @param config   Rerank 模型配置（含 apiHost/apiKey/modelName/providerType）
     * @return 按相关性降序排列的重排结果
     */
    List<RerankResult> rerank(String query, List<String> documents, int topN, AiModel config);
}
