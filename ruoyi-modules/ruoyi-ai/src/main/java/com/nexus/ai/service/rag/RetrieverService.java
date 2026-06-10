package com.nexus.ai.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.ai.domain.entity.AiKnowledge;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.service.IAiKnowledgeService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库检索服务
 * <p>基于向量相似度检索知识库中的相关片段，支持混合检索和 RRF 融合</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrieverService {

    private final VectorStoreService vectorStoreService;
    private final IAiKnowledgeService aiKnowledgeService;
    private final AiKnowledgeChunkMapper chunkMapper;
    private final JdbcTemplate jdbcTemplate;

    /** 默认最大返回结果数 */
    private static final int DEFAULT_MAX_RESULTS = 5;

    /** 默认最低相似度阈值 */
    private static final double DEFAULT_MIN_SCORE = 0.5;

    /** RRF 常数 K */
    private static final int RRF_K = 60;

    /** 重排序扩大倍数 */
    private static final int RERANK_EXPANSION_FACTOR = 3;

    /**
     * 检索与查询相关的知识片段（指定知识库）
     *
     * @param knowledgeId 知识库ID
     * @param query       查询文本
     * @return 相关片段内容列表（按相似度降序）
     */
    public List<String> retrieve(Long knowledgeId, String query) {
        AiKnowledge config = getKnowledgeConfig(knowledgeId);
        int maxResults = config.getRetrieveLimit() != null ? config.getRetrieveLimit() : DEFAULT_MAX_RESULTS;
        double minScore = config.getSimilarityThreshold() != null ? config.getSimilarityThreshold() : DEFAULT_MIN_SCORE;

        // 启用重排序时扩大召回量
        int expandFactor = (config.getEnableRerank() != null && config.getEnableRerank() == 1) ? RERANK_EXPANSION_FACTOR : 1;

        List<String> results;
        if (config.getEnableHybrid() != null && config.getEnableHybrid() == 1) {
            // 混合检索：向量 + MySQL 全文索引 + RRF 融合
            results = hybridRetrieve(knowledgeId, query, maxResults * expandFactor, minScore, config.getHybridAlpha());
        } else {
            // 纯向量检索
            results = vectorRetrieve(knowledgeId, query, maxResults * expandFactor, minScore);
        }

        // 截断到最终数量
        return results.subList(0, Math.min(maxResults, results.size()));
    }

    /**
     * 检索与查询相关的知识片段（不指定知识库，全库搜索）
     */
    public List<String> retrieve(String query) {
        return vectorRetrieve(null, query, DEFAULT_MAX_RESULTS, DEFAULT_MIN_SCORE);
    }

    /**
     * 向量检索
     */
    private List<String> vectorRetrieve(Long knowledgeId, String query, int maxResults, double minScore) {
        List<EmbeddingMatch<TextSegment>> matches;
        if (knowledgeId != null) {
            matches = vectorStoreService.search(knowledgeId, query, maxResults, minScore);
        } else {
            matches = vectorStoreService.search(query, maxResults, minScore);
        }
        return matches.stream().map(match -> match.embedded().text()).collect(Collectors.toList());
    }

    /**
     * 混合检索：向量搜索 + MySQL 全文索引 + RRF 融合
     *
     * @param knowledgeId 知识库ID
     * @param query       查询文本
     * @param maxResults  最大返回结果数
     * @param minScore    最低相似度阈值
     * @param alpha       混合检索权重（0=纯向量，1=纯关键词）
     * @return 融合后的片段内容列表
     */
    private List<String> hybridRetrieve(Long knowledgeId, String query, int maxResults, double minScore, Double alpha) {
        if (alpha == null) alpha = 0.5;

        // 1. 向量搜索
        List<EmbeddingMatch<TextSegment>> vectorMatches = vectorStoreService.search(knowledgeId, query, maxResults, minScore);

        // 2. MySQL 全文搜索
        List<AiKnowledgeChunk> fulltextChunks = fulltextSearch(knowledgeId, query, maxResults);

        // 3. RRF 融合
        Map<Long, Double> scoreMap = new HashMap<>();
        Map<Long, String> contentMap = new HashMap<>();

        // 向量搜索结果打分
        for (int i = 0; i < vectorMatches.size(); i++) {
            EmbeddingMatch<TextSegment> match = vectorMatches.get(i);
            String chunkIdStr = match.embedded().metadata().getString("chunkId");
            Long chunkId = chunkIdStr != null ? Long.parseLong(chunkIdStr) : null;
            if (chunkId != null) {
                double rrfScore = (1 - alpha) * (1.0 / (RRF_K + i + 1));
                scoreMap.merge(chunkId, rrfScore, Double::sum);
                contentMap.putIfAbsent(chunkId, match.embedded().text());
            }
        }

        // 全文搜索结果打分
        for (int i = 0; i < fulltextChunks.size(); i++) {
            AiKnowledgeChunk chunk = fulltextChunks.get(i);
            double rrfScore = alpha * (1.0 / (RRF_K + i + 1));
            scoreMap.merge(chunk.getId(), rrfScore, Double::sum);
            contentMap.putIfAbsent(chunk.getId(), chunk.getContent());
        }

        // 按 RRF 分数降序排列
        return scoreMap.entrySet().stream()
            .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
            .limit(maxResults)
            .map(entry -> contentMap.get(entry.getKey()))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * MySQL 全文检索（ngram 分词器）
     */
    private List<AiKnowledgeChunk> fulltextSearch(Long knowledgeId, String query, int limit) {
        try {
            String sql = "SELECT id, knowledge_id, document_id, content, chunk_index FROM ai_knowledge_chunk " +
                "WHERE knowledge_id = ? AND MATCH(content) AGAINST(? IN NATURAL LANGUAGE MODE) " +
                "ORDER BY MATCH(content) AGAINST(? IN NATURAL LANGUAGE MODE) DESC LIMIT ?";
            return jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    AiKnowledgeChunk chunk = new AiKnowledgeChunk();
                    chunk.setId(rs.getLong("id"));
                    chunk.setKnowledgeId(rs.getLong("knowledge_id"));
                    chunk.setDocumentId(rs.getLong("document_id"));
                    chunk.setContent(rs.getString("content"));
                    chunk.setChunkIndex(rs.getInt("chunk_index"));
                    return chunk;
                },
                knowledgeId, query, query, limit);
        } catch (Exception e) {
            log.warn("[RetrieverService] MySQL 全文检索失败，回退到纯向量检索: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 构建增强提示词（RAG 上下文注入）
     *
     * @param knowledgeId 知识库ID
     * @param query       原始查询
     * @return 增强后的提示词
     */
    public String augmentPrompt(Long knowledgeId, String query) {
        List<String> relevantChunks = retrieve(knowledgeId, query);
        return buildAugmentedPrompt(relevantChunks, query);
    }

    /**
     * 构建增强提示词（不指定知识库）
     *
     * @param query 原始查询
     * @return 增强后的提示词
     */
    public String augmentPrompt(String query) {
        List<String> relevantChunks = retrieve(query);
        return buildAugmentedPrompt(relevantChunks, query);
    }

    /**
     * 构建增强提示词
     */
    private String buildAugmentedPrompt(List<String> relevantChunks, String query) {
        if (relevantChunks.isEmpty()) {
            return query;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("请根据以下参考资料回答问题。如果参考资料中没有相关信息，请根据你的知识回答。\n\n");
        sb.append("=== 参考资料 ===\n");
        for (int i = 0; i < relevantChunks.size(); i++) {
            sb.append("[").append(i + 1).append("] ").append(relevantChunks.get(i)).append("\n\n");
        }
        sb.append("=== 参考资料 END ===\n\n");
        sb.append("问题：").append(query);
        return sb.toString();
    }

    /**
     * 获取知识库配置（从 VO 映射到 Entity 字段）
     *
     * @param knowledgeId 知识库ID
     * @return 知识库配置实体
     * @throws IllegalArgumentException 知识库不存在时抛出
     */
    private AiKnowledge getKnowledgeConfig(Long knowledgeId) {
        var vo = aiKnowledgeService.selectById(knowledgeId);
        if (vo == null) {
            throw new IllegalArgumentException("知识库不存在: " + knowledgeId);
        }
        // VO -> Entity 字段映射（直接使用 VO 的 getter）
        AiKnowledge config = new AiKnowledge();
        config.setRetrieveLimit(vo.getRetrieveLimit());
        config.setSimilarityThreshold(vo.getSimilarityThreshold());
        config.setEnableRerank(vo.getEnableRerank());
        config.setEnableHybrid(vo.getEnableHybrid());
        config.setHybridAlpha(vo.getHybridAlpha());
        return config;
    }
}
