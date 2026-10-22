package com.nexus.ai.service.rag;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.service.kg.GraphStoreStrategy;
import com.nexus.common.ai.decision.dto.RetrievalHit;
import com.nexus.common.ai.domain.entity.AiKgEntity;
import com.nexus.common.ai.domain.entity.AiKgRelation;
import com.nexus.common.ai.domain.entity.AiKnowledge;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.domain.entity.AiKnowledgeDocument;
import com.nexus.common.ai.domain.entity.AiKnowledgeGraph;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.mapper.AiKnowledgeDocumentMapper;
import com.nexus.common.ai.service.IAiKnowledgeService;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.Map;
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
    private final RerankService rerankService;
    private final IAiModelService aiModelService;
    private final GraphStoreStrategy graphStoreStrategy;
    private final AiProperties aiProperties;
    private final AiKnowledgeDocumentMapper documentMapper;

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
        return retrieve(knowledgeId, query, null);
    }

    /**
     * 检索与查询相关的知识片段（指定知识库，支持参数覆盖）
     *
     * <p>用于检索测试面板，允许临时覆盖知识库配置参数
     *
     * <p>入口门：向量索引非 READY（REBUILDING/FAILED）的知识库直接拒绝检索—— 该入口同时承接对话 RAG（{@code
     * AiServiceFacade.augmentPrompt}）与 检索测试面板（{@code AiKnowledgeController.retrieval}）。
     *
     * @param knowledgeId 知识库ID
     * @param query 查询文本
     * @param overrides 覆盖参数（topK/threshold/enableRerank/rerankModel/enableHybrid/hybridAlpha），可为
     *     null
     * @return 相关片段内容列表
     */
    public List<String> retrieve(Long knowledgeId, String query, Map<String, Object> overrides) {
        return retrieveHits(knowledgeId, query, overrides).stream().map(RetrievalHit::content).toList();
    }

    public List<RetrievalHit> retrieveHits(Long knowledgeId, String query) {
        return retrieveHits(knowledgeId, query, null);
    }

    /**
     * 结构化结果在倒数排名融合及可选重排过程中保留独立片段标识。
     */
    public List<RetrievalHit> retrieveHits(Long knowledgeId, String query, Map<String, Object> overrides) {
        AiKnowledge config = getKnowledgeConfig(knowledgeId);
        assertIndexReady(config);
        if (overrides != null) applyOverrides(config, overrides);
        int maxResults = config.getRetrieveLimit() != null ? config.getRetrieveLimit() : DEFAULT_MAX_RESULTS;
        double minScore = config.getSimilarityThreshold() != null
            ? config.getSimilarityThreshold() : DEFAULT_MIN_SCORE;
        int expand = config.getEnableRerank() != null && config.getEnableRerank() == 1
            ? RERANK_EXPANSION_FACTOR : 1;
        String model = config.getEmbeddingModel();
        List<Candidate> candidates = config.getEnableHybrid() != null && config.getEnableHybrid() == 1
            ? hybridRetrieveCandidates(knowledgeId, query, maxResults * expand, minScore,
                config.getHybridAlpha(), model)
            : vectorRetrieveCandidates(knowledgeId, query, maxResults * expand, minScore, model);
        // 仅返回文本的重排接口无法区分内容相同的片段，保留原融合或向量顺序，
        // 不得把选中的重复文本任意关联到某个文档。
        boolean distinctTexts = candidates.stream().map(Candidate::content).distinct().count() == candidates.size();
        if (config.getEnableRerank() != null && config.getEnableRerank() == 1
            && candidates.size() > 1 && distinctTexts) {
            Long rerankModelId = null;
            if (config.getRerankModel() != null && !config.getRerankModel().isEmpty()) {
                AiModel rerankModel = aiModelService.selectModelByName(config.getRerankModel());
                if (rerankModel != null) rerankModelId = rerankModel.getId();
            }
            List<String> ordered = rerankService.rerank(query,
                candidates.stream().map(Candidate::content).toList(), maxResults, rerankModelId);
            candidates = restoreCandidatesByText(candidates, ordered);
            log.info("[Retriever] 重排序完成: knowledgeId={}, 结果数={}", knowledgeId, candidates.size());
        }
        List<Candidate> selected = candidates.subList(0, Math.min(maxResults, candidates.size()));
        // 向量元数据可能落后于片段更新；声明规范来源前，先根据当前
        // 租户过滤后的关系表行核验标识与文本。
        Set<Long> chunkIds = selected.stream().map(Candidate::chunkId)
            .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, AiKnowledgeChunk> storedChunks = new HashMap<>();
        if (!chunkIds.isEmpty()) {
            List<AiKnowledgeChunk> current = chunkMapper.selectList(new LambdaQueryWrapper<AiKnowledgeChunk>()
                .eq(AiKnowledgeChunk::getKnowledgeId, knowledgeId)
                .in(AiKnowledgeChunk::getId, chunkIds));
            if (current != null) current.forEach(chunk -> storedChunks.put(chunk.getId(), chunk));
        }
        Set<Long> documentIds = selected.stream().map(Candidate::documentId)
            .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, AiKnowledgeDocument> documents = new HashMap<>();
        if (!documentIds.isEmpty()) {
            documentMapper.selectList(new LambdaQueryWrapper<AiKnowledgeDocument>()
                .eq(AiKnowledgeDocument::getKnowledgeId, knowledgeId)
                .in(AiKnowledgeDocument::getId, documentIds)
                .eq(AiKnowledgeDocument::getStatus, "2"))
                .forEach(doc -> documents.put(doc.getId(), doc));
        }
        List<RetrievalHit> results = new ArrayList<>();
        for (Candidate candidate : selected) {
            AiKnowledgeDocument document = documents.get(candidate.documentId());
            AiKnowledgeChunk currentChunk = storedChunks.get(candidate.chunkId());
            Date versionTime = document == null ? null
                : document.getUpdateTime() != null ? document.getUpdateTime() : document.getCreateTime();
            String version = versionTime == null ? null : Long.toString(versionTime.getTime());
            boolean verified = version != null && currentChunk != null
                && Objects.equals(currentChunk.getKnowledgeId(), knowledgeId)
                && Objects.equals(currentChunk.getDocumentId(), candidate.documentId())
                && Objects.equals(currentChunk.getContent(), candidate.content());
            // 兼容旧文本检索，同时明确标记不可验证的命中项。
            results.add(new RetrievalHit(knowledgeId, candidate.documentId(), candidate.chunkId(),
                document == null ? null : document.getFileName(), candidate.content(),
                candidate.vectorScore(), candidate.keywordRank(), null, candidate.graphPath(), version,
                null, verified ? candidate.sourceType() : "UNVERIFIED"));
        }
        return List.copyOf(results);
    }

    /**
     * 旧重排接口仅返回文本；按文本维护先进先出队列，不将文本用作唯一键。
     */
    private static List<Candidate> restoreCandidatesByText(List<Candidate> candidates, List<String> ordered) {
        Map<String, Deque<Candidate>> queues = new LinkedHashMap<>();
        candidates.forEach(c -> queues.computeIfAbsent(c.content(), ignored -> new ArrayDeque<>()).addLast(c));
        List<Candidate> result = new ArrayList<>();
        if (ordered == null) return result;
        for (String text : ordered) {
            Deque<Candidate> queue = queues.get(text);
            if (queue != null && !queue.isEmpty()) result.add(queue.removeFirst());
        }
        return result;
    }

    /**
     * 应用参数覆盖到知识库配置
     *
     * <p>用于检索测试面板，临时覆盖知识库的默认配置参数
     *
     * @param config 知识库配置实体
     * @param overrides 覆盖参数 Map
     */
    private void applyOverrides(AiKnowledge config, Map<String, Object> overrides) {
        if (overrides.containsKey("topK") && overrides.get("topK") != null) {
            config.setRetrieveLimit(Integer.valueOf(overrides.get("topK").toString()));
        }
        if (overrides.containsKey("threshold") && overrides.get("threshold") != null) {
            config.setSimilarityThreshold(Double.valueOf(overrides.get("threshold").toString()));
        }
        if (overrides.containsKey("enableRerank") && overrides.get("enableRerank") != null) {
            Object val = overrides.get("enableRerank");
            config.setEnableRerank(Boolean.TRUE.equals(val) ? 1 : 0);
        }
        if (overrides.containsKey("rerankModel") && overrides.get("rerankModel") != null) {
            config.setRerankModel(overrides.get("rerankModel").toString());
        }
        if (overrides.containsKey("enableHybrid") && overrides.get("enableHybrid") != null) {
            Object val = overrides.get("enableHybrid");
            config.setEnableHybrid(Boolean.TRUE.equals(val) ? 1 : 0);
        }
        if (overrides.containsKey("hybridAlpha") && overrides.get("hybridAlpha") != null) {
            config.setHybridAlpha(Double.valueOf(overrides.get("hybridAlpha").toString()));
        }
    }

    /**
     * 检索与查询相关的知识片段（不指定知识库，全库搜索）
     *
     * @param query 查询文本
     * @return 相关片段内容列表
     */
    public List<String> retrieve(String query) {
        return vectorRetrieve(null, query, DEFAULT_MAX_RESULTS, DEFAULT_MIN_SCORE, null);
    }

    /**
     * 向量检索
     *
     * @param knowledgeId       知识库ID，为 null 时全库搜索（仅 InMemory 模式支持）
     * @param query             查询文本
     * @param maxResults        最大返回结果数
     * @param minScore          最低相似度阈值
     * @param embeddingModelName 使用的 嵌入模型名称（可为 null，使用默认模型）
     * @return 片段内容列表
     */
    private List<String> vectorRetrieve(Long knowledgeId, String query, int maxResults, double minScore, String embeddingModelName) {
        List<EmbeddingMatch<TextSegment>> matches;
        if (knowledgeId != null) {
            matches = vectorStoreService.search(knowledgeId, query, maxResults, minScore, embeddingModelName);
        } else {
            matches = vectorStoreService.search(query, maxResults, minScore);
        }
        return matches.stream().map(match -> match.embedded().text()).collect(Collectors.toList());
    }

    /**
     * 混合检索：向量搜索 + MySQL 全文索引 + RRF 融合
     *
     * @param knowledgeId       知识库ID
     * @param query             查询文本
     * @param maxResults        最大返回结果数
     * @param minScore          最低相似度阈值
     * @param alpha             混合检索权重（0=纯向量，1=纯关键词）
     * @param embeddingModelName 使用的 嵌入模型名称（可为 null）
     * @return 融合后的片段内容列表
     */
    private List<Candidate> vectorRetrieveCandidates(Long knowledgeId, String query, int maxResults,
                                                      double minScore, String embeddingModelName) {
        List<EmbeddingMatch<TextSegment>> matches = vectorStoreService.search(
            knowledgeId, query, maxResults, minScore, embeddingModelName);
        return matches.stream().map(match -> candidate(knowledgeId, match)).toList();
    }

    private Candidate candidate(Long knowledgeId, EmbeddingMatch<TextSegment> match) {
        var metadata = match.embedded().metadata();
        Long foundKnowledge = parseId(metadata.getString("knowledgeId"));
        if (foundKnowledge != null && !foundKnowledge.equals(knowledgeId)) {
            throw new ServiceException("向量命中不属于当前知识库");
        }
        return new Candidate(parseId(metadata.getString("chunkId")),
            parseId(metadata.getString("documentId")), match.embedded().text(), match.score(),
            null, null, "VECTOR");
    }

    private static Long parseId(String raw) {
        if (raw == null) return null;
        try { return Long.valueOf(raw); }
        catch (NumberFormatException ex) { return null; }
    }

    /**
     * 向量、MySQL 全文及可选图谱召回，倒数排名融合严格以片段标识为键。
     */
    private List<Candidate> hybridRetrieveCandidates(Long knowledgeId, String query, int maxResults,
                                                     double minScore, Double alpha, String embeddingModelName) {
        double keywordWeight = alpha == null ? 0.5 : alpha;
        List<EmbeddingMatch<TextSegment>> vectors = vectorStoreService.search(
            knowledgeId, query, maxResults, minScore, embeddingModelName);
        List<AiKnowledgeChunk> fulltext = fulltextSearch(knowledgeId, query, maxResults);
        Map<Long, Double> scores = new HashMap<>();
        Map<Long, Candidate> candidates = new HashMap<>();
        for (int i = 0; i < vectors.size(); i++) {
            Candidate c = candidate(knowledgeId, vectors.get(i));
            if (c.chunkId() == null) continue; // 沿用旧混合检索的融合范围。
            scores.merge(c.chunkId(), (1 - keywordWeight) / (RRF_K + i + 1.0), Double::sum);
            candidates.putIfAbsent(c.chunkId(), c);
        }
        for (int i = 0; i < fulltext.size(); i++) {
            AiKnowledgeChunk chunk = fulltext.get(i);
            if (chunk.getId() == null) continue;
            scores.merge(chunk.getId(), keywordWeight / (RRF_K + i + 1.0), Double::sum);
            Candidate old = candidates.get(chunk.getId());
            candidates.put(chunk.getId(), new Candidate(chunk.getId(), chunk.getDocumentId(),
                chunk.getContent(), old == null ? null : old.vectorScore(),
                i + 1, old == null ? null : old.graphPath(), "HYBRID"));
        }
        AiProperties.KnowledgeGraphConfig graphCfg = aiProperties.getKnowledgeGraph();
        if (graphCfg.isEnabled()) {
            List<AiKnowledgeChunk> graphChunks = performGraphRecall(knowledgeId, query, maxResults, graphCfg);
            for (int i = 0; i < graphChunks.size(); i++) {
                AiKnowledgeChunk chunk = graphChunks.get(i);
                if (chunk.getId() == null) continue;
                scores.merge(chunk.getId(), graphCfg.getGraphWeight() / (RRF_K + i + 1.0), Double::sum);
                Candidate old = candidates.get(chunk.getId());
                candidates.put(chunk.getId(), new Candidate(chunk.getId(), chunk.getDocumentId(),
                    chunk.getContent(), old == null ? null : old.vectorScore(),
                    old == null ? null : old.keywordRank(), null, "GRAPH"));
            }
        }
        return scores.entrySet().stream().sorted(Map.Entry.<Long, Double>comparingByValue().reversed()
                .thenComparing(Map.Entry::getKey)).limit(maxResults)
            .map(entry -> candidates.get(entry.getKey())).filter(Objects::nonNull).toList();
    }

    private record Candidate(Long chunkId, Long documentId, String content, Double vectorScore,
                             Integer keywordRank, String graphPath, String sourceType) { }

    /**
     * MySQL 全文检索（ngram 分词器）
     *
     * @param knowledgeId 知识库ID
     * @param query       查询文本
     * @param limit       最大返回结果数
     * @return 匹配的片段列表，全文检索失败时返回空列表
     */
    private List<AiKnowledgeChunk> fulltextSearch(Long knowledgeId, String query, int limit) {
        try {
            String tenantId = TenantHelper.getTenantId();
            if (tenantId == null || tenantId.isBlank()) return List.of(); // JDBC 绕过 MyBatis 租户插件，必须显式隔离。
            String sql = "SELECT id, knowledge_id, document_id, content, chunk_index FROM ai_knowledge_chunk " +
                "WHERE knowledge_id = ? AND tenant_id = ? AND MATCH(content) AGAINST(? IN NATURAL LANGUAGE MODE) " +
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
                knowledgeId, tenantId, query, query, limit);
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
     * 构建增强提示词（将检索结果注入原始查询）
     *
     * @param relevantChunks 相关片段列表
     * @param query          原始查询文本
     * @return 增强后的提示词，无相关片段时直接返回原始查询
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
        config.setRerankModel(vo.getRerankModel());
        config.setEnableHybrid(vo.getEnableHybrid());
        config.setHybridAlpha(vo.getHybridAlpha());
        config.setEmbeddingModel(vo.getEmbeddingModel());
        config.setVectorIndexStatus(vo.getVectorIndexStatus());
        return config;
    }

    /**
     * 索引状态门：向量索引非 READY（REBUILDING/FAILED）的知识库拒绝检索
     * <p>状态为 null 的存量行按 READY 放行（列默认值 READY，迁移前数据宽容处理）。</p>
     *
     * @param config 知识库配置（含 vectorIndexStatus）
     * @throws ServiceException 索引正在重建或重建失败时抛出
     */
    private void assertIndexReady(AiKnowledge config) {
        String indexStatus = config.getVectorIndexStatus();
        if (indexStatus != null && !KnowledgeIndexRebuildService.STATUS_READY.equals(indexStatus)) {
            throw new ServiceException("知识库索引正在重建或重建失败，请完成重建后再检索");
        }
    }

    /**
     * 图谱局部召回（局部搜索）：实体链接 → 1 跳邻居 → 回溯来源 chunk
     * <p>仅图谱构建完成（status=2）时生效；失败回退空列表，不阻断 RRF 融合</p>
     *
     * @param knowledgeId 知识库ID
     * @param query       查询文本
     * @param limit       最大返回数
     * @param cfg         知识图谱配置
     * @return 回溯到的来源 chunk 列表（按来源实体 mention_count 降序）
     */
    private List<AiKnowledgeChunk> performGraphRecall(Long knowledgeId, String query, int limit,
                                                       AiProperties.KnowledgeGraphConfig cfg) {
        try {
            AiKnowledgeGraph graph = graphStoreStrategy.findGraphByKnowledge(knowledgeId);
            if (graph == null || !"2".equals(graph.getGraphStatus())) {
                return List.of();
            }
            List<AiKgEntity> allEntities = graphStoreStrategy.listEntitiesByGraphId(graph.getId());
            if (allEntities.isEmpty()) {
                return List.of();
            }
            Map<Long, AiKgEntity> entityById = new HashMap<>();
            Map<Long, Long> chunkToMaxMention = new HashMap<>();
            List<AiKgEntity> hits = new ArrayList<>();
            for (AiKgEntity e : allEntities) {
                entityById.put(e.getId(), e);
                if (e.getSourceChunkId() != null) {
                    long mc = e.getMentionCount() != null ? e.getMentionCount() : 0;
                    chunkToMaxMention.merge(e.getSourceChunkId(), mc, Math::max);
                }
                String name = e.getEntityName();
                if (name != null && !name.isBlank() && query.contains(name)) {
                    hits.add(e);
                }
            }
            if (hits.isEmpty()) {
                return List.of();
            }
            Set<Long> hitIds = hits.stream().map(AiKgEntity::getId).collect(Collectors.toSet());

            // 1 跳邻居（限 maxNeighbors）
            Set<Long> neighborIds = graphStoreStrategy.findNeighborEntityIds(graph.getId(), hitIds);
            if (neighborIds.size() > cfg.getMaxNeighbors()) {
                neighborIds = new HashSet<>(new ArrayList<>(neighborIds).subList(0, cfg.getMaxNeighbors()));
            }

            // 收集候选 chunkId：命中实体 + 邻居实体 + 相关关系的来源 chunk
            Set<Long> candidateChunkIds = new HashSet<>();
            for (AiKgEntity e : hits) {
                if (e.getSourceChunkId() != null) {
                    candidateChunkIds.add(e.getSourceChunkId());
                }
            }
            for (Long nbId : neighborIds) {
                AiKgEntity nb = entityById.get(nbId);
                if (nb != null && nb.getSourceChunkId() != null) {
                    candidateChunkIds.add(nb.getSourceChunkId());
                }
            }
            Set<Long> relatedIds = new HashSet<>(hitIds);
            relatedIds.addAll(neighborIds);
            List<AiKgRelation> rels = graphStoreStrategy.listRelationsByEntities(graph.getId(), relatedIds);
            for (AiKgRelation r : rels) {
                if (r.getSourceChunkId() != null) {
                    candidateChunkIds.add(r.getSourceChunkId());
                }
            }

            // 按来源实体 最大提及次数 降序，截 limit
            List<Long> ordered = candidateChunkIds.stream()
                .sorted(Comparator.comparingLong((Long c) -> chunkToMaxMention.getOrDefault(c, 0L)).reversed())
                .limit(limit)
                .collect(Collectors.toList());
            if (ordered.isEmpty()) {
                return List.of();
            }
            List<AiKnowledgeChunk> chunks = graphStoreStrategy.findSourceChunks(ordered);
            Map<Long, AiKnowledgeChunk> chunkById = new HashMap<>();
            for (AiKnowledgeChunk c : chunks) {
                chunkById.put(c.getId(), c);
            }
            List<AiKnowledgeChunk> result = new ArrayList<>();
            for (Long id : ordered) {
                AiKnowledgeChunk c = chunkById.get(id);
                if (c != null) {
                    result.add(c);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[RetrieverService] 图谱召回失败，回退: {}", e.getMessage());
            return List.of();
        }
    }
}
