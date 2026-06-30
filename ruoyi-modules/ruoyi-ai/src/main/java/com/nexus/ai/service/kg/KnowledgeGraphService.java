package com.nexus.ai.service.kg;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.ai.agent.GraphExtractionAgent;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.ai.service.kg.dto.GraphExtractionResult;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.domain.entity.AiKgEntity;
import com.nexus.common.ai.domain.entity.AiKgRelation;
import com.nexus.common.ai.domain.entity.AiKnowledgeGraph;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.mapper.AiKnowledgeGraphMapper;
import com.nexus.common.ai.service.IAiModelService;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 知识图谱构建服务
 * <p>两阶段异步构建：阶段一逐 chunk 抽取实体/关系（同名合并、增量可重跑、token 自动采集）；
 * 阶段二社区检测 + 社区摘要。</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeGraphService {

    private final GraphStoreStrategy graphStoreStrategy;
    private final CommunityService communityService;
    private final ChatModelFactory chatModelFactory;
    private final IAiModelService aiModelService;
    private final AiProperties aiProperties;
    private final AiKnowledgeChunkMapper chunkMapper;
    private final AiKnowledgeGraphMapper graphMapper;

    @Qualifier("aiExecutor")
    private final Executor aiExecutor;

    /** 默认实体类型（可被 config 覆盖） */
    private static final String DEFAULT_ENTITY_TYPES =
        "物料,客户,供应商,订单,采购订单,工单,BOM,车间,工序,设备,仓库,产品,其他";
    private static final String DEFAULT_RELATION_TYPES =
        "供应,采购,包含,生产于,存储于,隶属于,关联";

    /**
     * 异步触发图谱构建
     *
     * @param knowledgeId 知识库ID
     * @param modelId     抽取用模型ID（null 用配置/默认模型）
     * @param userId      触发用户ID
     * @param tenantId    租户ID（字符串，保留前导零如 "000000"）
     * @param userName    用户名称
     */
    @com.baomidou.lock.annotation.Lock4j(keys = {"#knowledgeId"})
    public void buildGraph(Long knowledgeId, Long modelId, Long userId, String tenantId, String userName) {
        // 调用线程预检查防重（缩小与异步内防重的竞态窗口，避免双击重复提交任务）
        AiKnowledgeGraph existing = graphStoreStrategy.findGraphByKnowledge(knowledgeId);
        if (existing != null && "1".equals(existing.getGraphStatus())) {
            log.info("[KG] 图谱构建中，跳过重复触发 knowledgeId={}", knowledgeId);
            return;
        }
        aiExecutor.execute(() -> {
            try {
                // 异步线程恢复租户上下文（Sa-Token ThreadLocal 不跨线程，多租户拦截器需 TenantHelper.dynamic 恢复，否则 insert 时 tenant_id 不被填充）
                com.nexus.common.tenant.helper.TenantHelper.dynamic(tenantId, () -> {
                    buildGraphInternal(knowledgeId, modelId, userId, tenantId, userName);
                });
            } catch (Exception e) {
                log.error("[KG] 图谱构建异常 knowledgeId={}", knowledgeId, e);
            }
        });
    }

    /**
     * 构建主流程（同步，由 {@link #buildGraph} 提交到 aiExecutor，调用方需已用 TenantHelper.dynamic 恢复租户上下文）
     */
    void buildGraphInternal(Long knowledgeId, Long modelId, Long userId, String tenantIdStr, String userName) {
        // 防重：已有构建中的图谱则跳过
        AiKnowledgeGraph existing = graphStoreStrategy.findGraphByKnowledge(knowledgeId);
        if (existing != null && "1".equals(existing.getGraphStatus())) {
            log.info("[KG] 图谱构建中，跳过重复触发 knowledgeId={}", knowledgeId);
            return;
        }

        AiKnowledgeGraph graph = graphStoreStrategy.findOrCreateGraph(knowledgeId, modelId,
            DEFAULT_ENTITY_TYPES, DEFAULT_RELATION_TYPES);

        Long finalModelId = resolveModelId(modelId);
        String modelName = resolveModelName(finalModelId);

        try {
            // AiRequestContext 的 tenantId 给 AiChatModelListener 用（token 统计按租户），parse 回 Long
            Long tenantIdLong = (tenantIdStr != null && !tenantIdStr.isEmpty()) ? Long.parseLong(tenantIdStr) : null;
            AiRequestContext.set(tenantIdLong, userId, userName, null, finalModelId, modelName);

            // 阶段一：实体关系抽取
            ChatModel chatModel = chatModelFactory.getChatModel(finalModelId);
            GraphExtractionAgent agent = dev.langchain4j.service.AiServices.builder(GraphExtractionAgent.class)
                .chatModel(chatModel)
                .build();

            List<AiKnowledgeChunk> pendingChunks = chunkMapper.selectList(Wrappers.<AiKnowledgeChunk>lambdaQuery()
                .eq(AiKnowledgeChunk::getKnowledgeId, knowledgeId)
                .eq(AiKnowledgeChunk::getExtractionStatus, "0")
                .orderByAsc(AiKnowledgeChunk::getChunkIndex));

            Map<String, AiKgEntity> entityCache = new HashMap<>();
            long totalTokens = 0;
            int processedCount = 0;
            int failedCount = 0;

            for (AiKnowledgeChunk chunk : pendingChunks) {
                try {
                    int[] counts = extractChunk(graph.getId(), chunk, agent, entityCache);
                    processedCount++;
                    totalTokens += chunk.getTokenUsed() != null ? chunk.getTokenUsed() : 0;
                    log.debug("[KG] chunk 抽取完成 chunkId={}, entities={}, relations={}", chunk.getId(), counts[0], counts[1]);
                } catch (Exception e) {
                    failedCount++;
                    log.warn("[KG] chunk 抽取失败 chunkId={}, error={}", chunk.getId(), e.getMessage());
                }
            }

            // 阶段二：社区检测 + 摘要（仅在有新 chunk 处理时执行；增量无新文档时保留现有社区，避免重复生成摘要浪费 token）
            int communityCount = 0;
            if (!pendingChunks.isEmpty()) {
                try {
                    communityCount = communityService.detectCommunities(graph.getId());
                    communityService.summarizeCommunities(graph.getId(), finalModelId);
                } catch (Exception e) {
                    log.warn("[KG] 社区检测/摘要失败 graphId={}, error={}", graph.getId(), e.getMessage());
                }
            } else {
                communityCount = graphStoreStrategy.listCommunitiesByGraphId(graph.getId()).size();
            }

            // 收尾：更新图谱状态与计数
            int nodeCount = graphStoreStrategy.listEntitiesByGraphId(graph.getId()).size();
            int relationCount = graphStoreStrategy.listRelationsByGraphId(graph.getId()).size();
            int summarizedCount = graphStoreStrategy.listSummarizedCommunities(graph.getId()).size();
            long failedCommunityCount = graphStoreStrategy.countCommunitiesBySummaryStatus(graph.getId(), "3");
            graph.setNodeCount(nodeCount);
            graph.setRelationshipCount(relationCount);
            graph.setCommunityCount(communityCount);
            graph.setGraphStatus(processedCount == 0 && failedCount > 0 ? "3" : "2");
            // 拼接切片抽取失败与社区摘要失败提示（不阻断完成，前端据此 msgWarning）
            StringBuilder err = new StringBuilder();
            if (failedCount > 0) {
                err.append(failedCount).append(" 个切片抽取失败");
            }
            if (failedCommunityCount > 0) {
                if (err.length() > 0) {
                    err.append("；");
                }
                err.append(failedCommunityCount).append(" 个社区摘要失败");
            }
            graph.setErrorMessage(err.length() > 0 ? err.toString() : null);
            graphStoreStrategy.updateGraph(graph);

            log.info("[KG] 图谱构建完成 knowledgeId={}, nodes={}, relations={}, communities={}, summaries={}, tokens={}",
                knowledgeId, nodeCount, relationCount, communityCount, summarizedCount, totalTokens);
        } catch (Exception e) {
            graph.setGraphStatus("3");
            graph.setErrorMessage(truncate(e.getMessage(), 1000));
            graphStoreStrategy.updateGraph(graph);
            throw e;
        } finally {
            AiRequestContext.clear();
        }
    }

    /**
     * 抽取单个 chunk 的实体/关系
     *
     * @return int[]{entityCount, relationCount}
     */
    private int[] extractChunk(Long graphId, AiKnowledgeChunk chunk, GraphExtractionAgent agent,
                                Map<String, AiKgEntity> entityCache) {
        chunk.setExtractionStatus("1");
        chunkMapper.updateById(chunk);

        GraphExtractionResult result = null;
        try {
            String json = agent.extract(chunk.getContent());
            result = GraphExtractionParser.parse(json);
            if (result == null) {
                // 重试一次
                String retryJson = agent.extract(chunk.getContent()
                    + "\n\n[注意] 上次输出无法解析为JSON，请严格只输出JSON对象。");
                result = GraphExtractionParser.parse(retryJson);
            }
            if (result == null) {
                result = GraphExtractionParser.regexFallback(json);
            }
        } catch (Exception e) {
            chunk.setExtractionStatus("3");
            chunk.setKgErrorMessage(truncate(e.getMessage(), 1000));
            chunkMapper.updateById(chunk);
            return new int[]{0, 0};
        }

        AiProperties.KnowledgeGraphConfig cfg = aiProperties.getKnowledgeGraph();
        double threshold = cfg.getConfidenceThreshold();
        try {
            // 置信度过滤 + 数量上限
            List<GraphExtractionResult.EntityDto> entities = filterAndCap(
                result.getEntities(), threshold, cfg.getMaxEntitiesPerChunk());
            List<GraphExtractionResult.RelationDto> relations = filterAndCap(
                result.getRelations(), threshold, cfg.getMaxRelationsPerChunk());

            // 同名实体合并 + 落库
            for (GraphExtractionResult.EntityDto dto : entities) {
                mergeEntity(graphId, dto, chunk.getId(), entityCache);
            }
            // 关系落库
            int relationCount = 0;
            for (GraphExtractionResult.RelationDto dto : relations) {
                if (persistRelation(graphId, chunk.getId(), dto)) {
                    relationCount++;
                }
            }

            long used = captureTokens();
            chunk.setEntityCount(entities.size());
            chunk.setRelationCount(relationCount);
            chunk.setTokenUsed(used);
            chunk.setExtractionStatus("2");
            chunk.setKgErrorMessage(null);
            chunkMapper.updateById(chunk);
            return new int[]{entities.size(), relationCount};
        } catch (Exception e) {
            // 后处理异常（filterAndCap NPE / mergeEntity DB 超长 / persistRelation 写库失败等）置失败状态可重试，避免 chunk 永久卡在 "1"
            log.error("[KG] chunk 后处理失败 chunkId={}, error={}", chunk.getId(), e.getMessage(), e);
            chunk.setExtractionStatus("3");
            chunk.setKgErrorMessage(truncate(e.getMessage(), 1000));
            chunkMapper.updateById(chunk);
            return new int[]{0, 0};
        }
    }

    /**
     * 同名实体合并：按 (normalize(name)+type) 合并；不命中则新增
     */
    private void mergeEntity(Long graphId, GraphExtractionResult.EntityDto dto, Long chunkId,
                              Map<String, AiKgEntity> entityCache) {
        String type = dto.getType() != null ? dto.getType() : "其他";
        String key = normalize(dto.getName()) + "|" + type;
        Double confidence = dto.getConfidence() != null ? dto.getConfidence() : 0.5;

        AiKgEntity entity = entityCache.get(key);
        if (entity == null) {
            entity = graphStoreStrategy.findEntity(graphId, dto.getName(), type);
            if (entity != null) {
                entityCache.put(key, entity);
            }
        }
        if (entity != null) {
            // 合并：mention_count++，description 拼接，confidence 取 max
            entity.setMentionCount((entity.getMentionCount() == null ? 0 : entity.getMentionCount()) + 1);
            String desc = dto.getDescription();
            if (desc != null && !desc.isBlank()) {
                String existing = entity.getDescription();
                if (existing == null || !existing.contains(desc)) {
                    String merged = existing == null ? desc : existing + "；" + desc;
                    // 截断防止超 varchar(1024) 触发 DB 异常
                    if (merged.length() > 1000) {
                        merged = merged.substring(0, 997) + "...";
                    }
                    entity.setDescription(merged);
                }
            }
            if (confidence > (entity.getConfidence() == null ? 0 : entity.getConfidence())) {
                entity.setConfidence(confidence);
            }
            graphStoreStrategy.updateEntity(entity);
        } else {
            entity = new AiKgEntity();
            entity.setGraphId(graphId);
            entity.setEntityName(dto.getName());
            entity.setEntityType(type);
            entity.setDescription(dto.getDescription());
            entity.setConfidence(confidence);
            entity.setSourceChunkId(chunkId);
            entity.setMentionCount(1);
            graphStoreStrategy.insertEntity(entity);
            entityCache.put(key, entity);
        }
    }

    /**
     * 关系落库（去重：同 source/target/type 取 strength/confidence 较大者更新）
     *
     * @return 是否成功落库（悬空关系返回 false）
     */
    private boolean persistRelation(Long graphId, Long chunkId, GraphExtractionResult.RelationDto dto) {
        AiKgEntity source = graphStoreStrategy.findEntity(graphId, dto.getSource(), null);
        AiKgEntity target = graphStoreStrategy.findEntity(graphId, dto.getTarget(), null);
        if (source == null || target == null) {
            return false;
        }
        String type = dto.getType() != null ? dto.getType() : "关联";
        int strength = dto.getStrength() != null ? dto.getStrength() : 5;
        Double confidence = dto.getConfidence() != null ? dto.getConfidence() : 0.5;

        AiKgRelation existing = graphStoreStrategy.findRelation(graphId, source.getId(), target.getId(), type);
        if (existing != null) {
            if (strength > (existing.getStrength() == null ? 0 : existing.getStrength())) {
                existing.setStrength(strength);
            }
            if (confidence > (existing.getConfidence() == null ? 0 : existing.getConfidence())) {
                existing.setConfidence(confidence);
            }
            graphStoreStrategy.updateRelation(existing);
        } else {
            AiKgRelation relation = new AiKgRelation();
            relation.setGraphId(graphId);
            relation.setSourceEntityId(source.getId());
            relation.setTargetEntityId(target.getId());
            relation.setRelationType(type);
            relation.setDescription(dto.getDescription());
            relation.setStrength(strength);
            relation.setConfidence(confidence);
            relation.setSourceChunkId(chunkId);
            graphStoreStrategy.insertRelation(relation);
        }
        return true;
    }

    /**
     * 获取构建进度
     */
    public Map<String, Object> getProgress(Long knowledgeId) {
        Map<String, Object> progress = new LinkedHashMap<>();
        AiKnowledgeGraph graph = graphStoreStrategy.findGraphByKnowledge(knowledgeId);
        if (graph == null) {
            progress.put("graphStatus", null);
            progress.put("total", 0);
            progress.put("processed", 0);
            progress.put("failed", 0);
            progress.put("nodeCount", 0);
            progress.put("relationCount", 0);
            progress.put("communityCount", 0);
            progress.put("communityTotal", 0);
            progress.put("communitySummarized", 0);
            progress.put("stage", null);
            return progress;
        }
        Long total = chunkMapper.selectCount(Wrappers.<AiKnowledgeChunk>lambdaQuery()
            .eq(AiKnowledgeChunk::getKnowledgeId, knowledgeId));
        Long processed = chunkMapper.selectCount(Wrappers.<AiKnowledgeChunk>lambdaQuery()
            .eq(AiKnowledgeChunk::getKnowledgeId, knowledgeId)
            .in(AiKnowledgeChunk::getExtractionStatus, "2", "3"));
        Long failed = chunkMapper.selectCount(Wrappers.<AiKnowledgeChunk>lambdaQuery()
            .eq(AiKnowledgeChunk::getKnowledgeId, knowledgeId)
            .eq(AiKnowledgeChunk::getExtractionStatus, "3"));
        long communityTotal = graphStoreStrategy.countCommunities(graph.getId());
        long communitySummarized = graphStoreStrategy.countCommunitiesBySummaryStatus(graph.getId(), "2");
        progress.put("graphStatus", graph.getGraphStatus());
        progress.put("total", total);
        progress.put("processed", processed);
        progress.put("failed", failed);
        progress.put("nodeCount", graph.getNodeCount());
        progress.put("relationCount", graph.getRelationshipCount());
        progress.put("communityCount", graph.getCommunityCount());
        progress.put("communityTotal", communityTotal);
        progress.put("communitySummarized", communitySummarized);
        progress.put("stage", deriveStage(graph.getGraphStatus(), processed, total, communityTotal));
        return progress;
    }

    /**
     * 推导构建阶段（前端阶段化进度展示用，无需新增 DB 字段）
     * <p>依据 graphStatus + chunk 抽取进度 + 社区落库情况推导：
     * EXTRACTING（实体抽取中）/ DETECTING（社区检测中）/ SUMMARIZING（社区摘要中 M/N）/ DONE / FAILED / PENDING</p>
     */
    private String deriveStage(String graphStatus, long processed, long total, long communityTotal) {
        if ("2".equals(graphStatus)) {
            return "DONE";
        }
        if ("3".equals(graphStatus)) {
            return "FAILED";
        }
        if ("0".equals(graphStatus)) {
            return "PENDING";
        }
        // graphStatus == "1" 构建中
        if (total > 0 && processed < total) {
            return "EXTRACTING";
        }
        // 抽取已结束：社区未落库 → 检测中；已落库 → 摘要中
        return communityTotal == 0 ? "DETECTING" : "SUMMARIZING";
    }

    /**
     * 全量重建：清实体/关系/社区 + 重置 chunk 状态 + 重新构建（异步）
     * <p>语义为强制全量重抽，不受 buildGraphInternal 的构建中防重拦截（先置 status=0）。
     * 全量重抽耗时较长，必须异步执行避免阻塞 HTTP 线程。</p>
     */
    @com.baomidou.lock.annotation.Lock4j(keys = {"#knowledgeId"})
    public void rebuild(Long knowledgeId, Long modelId, Long userId, String tenantIdStr, String userName) {
        aiExecutor.execute(() -> {
            try {
                com.nexus.common.tenant.helper.TenantHelper.dynamic(tenantIdStr, () -> {
                    AiKnowledgeGraph graph = graphStoreStrategy.findGraphByKnowledge(knowledgeId);
                    if (graph != null) {
                        graphStoreStrategy.deleteGraphData(graph.getId());
                        // 强制重置为未构建，避免 buildGraphInternal 防重跳过
                        graph.setGraphStatus("0");
                        graphStoreStrategy.updateGraph(graph);
                    }
                    // 重置该知识库全部 chunk 的抽取状态
                    chunkMapper.update(null, Wrappers.<AiKnowledgeChunk>lambdaUpdate()
                        .set(AiKnowledgeChunk::getExtractionStatus, "0")
                        .set(AiKnowledgeChunk::getEntityCount, 0)
                        .set(AiKnowledgeChunk::getRelationCount, 0)
                        .set(AiKnowledgeChunk::getTokenUsed, 0)
                        .set(AiKnowledgeChunk::getKgErrorMessage, null)
                        .eq(AiKnowledgeChunk::getKnowledgeId, knowledgeId));
                    buildGraphInternal(knowledgeId, modelId, userId, tenantIdStr, userName);
                });
            } catch (Exception e) {
                log.error("[KG] 图谱重建异常 knowledgeId={}", knowledgeId, e);
            }
        });
    }

    /**
     * 删除图谱（实体/关系/社区 + 实例行），保留 chunk 内容（重置抽取状态）
     */
    public void deleteGraph(Long knowledgeId) {
        AiKnowledgeGraph graph = graphStoreStrategy.findGraphByKnowledge(knowledgeId);
        if (graph == null) {
            return;
        }
        graphStoreStrategy.deleteGraphData(graph.getId());
        graphMapper.deleteById(graph.getId());
        chunkMapper.update(null, Wrappers.<AiKnowledgeChunk>lambdaUpdate()
            .set(AiKnowledgeChunk::getExtractionStatus, "0")
            .set(AiKnowledgeChunk::getEntityCount, 0)
            .set(AiKnowledgeChunk::getRelationCount, 0)
            .set(AiKnowledgeChunk::getTokenUsed, 0)
            .set(AiKnowledgeChunk::getKgErrorMessage, null)
            .eq(AiKnowledgeChunk::getKnowledgeId, knowledgeId));
    }

    // -------------------- 辅助方法 --------------------

    private <T> List<T> filterAndCap(List<T> list, double threshold, int max) {
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        List<T> filtered = new ArrayList<>();
        for (T item : list) {
            Double conf = extractConfidence(item);
            if (conf == null || conf >= threshold) {
                filtered.add(item);
            }
        }
        if (filtered.size() > max) {
            // null 安全排序（null confidence 排最后），避免 comparingDouble 自动拆箱导致 NPE
            filtered.sort((a, b) -> {
                Double ca = extractConfidence(a);
                Double cb = extractConfidence(b);
                if (ca == null && cb == null) return 0;
                if (ca == null) return 1;
                if (cb == null) return -1;
                return Double.compare(cb, ca);  // 降序，高置信度优先
            });
            filtered = new ArrayList<>(filtered.subList(0, max));
        }
        return filtered;
    }

    private Double extractConfidence(Object item) {
        if (item instanceof GraphExtractionResult.EntityDto e) {
            return e.getConfidence();
        } else if (item instanceof GraphExtractionResult.RelationDto r) {
            return r.getConfidence();
        }
        return null;
    }

    private String normalize(String name) {
        if (name == null) {
            return "";
        }
        String s = name.trim().toLowerCase().replaceAll("\\s+", "");
        // 去除常见后缀
        s = s.replaceAll("(公司|有限公司|股份有限公司|股份公司)$", "");
        return s;
    }

    private Long resolveModelId(Long requestModelId) {
        if (requestModelId != null) {
            return requestModelId;
        }
        Long configId = aiProperties.getKnowledgeGraph().getExtractionModelId();
        if (configId != null) {
            return configId;
        }
        AiModel def = aiModelService.selectDefaultModel();
        return def != null ? def.getId() : null;
    }

    private String resolveModelName(Long modelId) {
        if (modelId == null) {
            return "unknown";
        }
        AiModel model = aiModelService.selectModelById(modelId);
        return model != null && model.getModelName() != null ? model.getModelName() : "unknown";
    }

    private long captureTokens() {
        Integer in = AiRequestContext.getLastInputTokens();
        Integer out = AiRequestContext.getLastOutputTokens();
        return (in == null ? 0 : in) + (out == null ? 0 : out);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
