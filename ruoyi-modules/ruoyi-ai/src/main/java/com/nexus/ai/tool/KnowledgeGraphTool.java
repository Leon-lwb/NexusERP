package com.nexus.ai.tool;

import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.service.kg.GlobalSearchService;
import com.nexus.ai.service.kg.GraphStoreStrategy;
import com.nexus.common.ai.domain.entity.AiKgEntity;
import com.nexus.common.ai.domain.entity.AiKgRelation;
import com.nexus.common.ai.domain.entity.AiKnowledgeGraph;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识图谱查询工具（供 Supervisor Agent 调用）
 * <p>提供实体关系遍历（局部）与全局摘要检索（全局）两个能力，与 RAG 的被动图谱召回互补：</p>
 * <ul>
 *   <li>{@link #queryGraphRelation} 主动查询实体关系结构（1-2 跳），供 Agent 做关系推理</li>
 *   <li>{@link #globalSummary} 基于社区摘要合成整体/主题问题的答案</li>
 * </ul>
 * <p>知识库范围取自 {@link AiRequestContext#getKnowledgeId()}（由 AiServiceFacade 在 Agent 模式注入）。</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeGraphTool {

    /** 图谱存储策略，提供实体/关系查询与按知识库定位 */
    private final GraphStoreStrategy graphStoreStrategy;
    /** 全局检索服务，基于社区摘要合成整体答案 */
    private final GlobalSearchService globalSearchService;

    /**
     * 根据实体名称查询知识图谱中该实体的关系（1 跳或 2 跳），返回 Markdown 关系表
     *
     * @param entityName 实体名称
     * @param hops       跳数，1 或 2，默认 1
     * @return Markdown 关系表或提示信息
     */
    @Tool("根据实体名称查询当前知识库知识图谱中该实体的关系（1跳或2跳邻居），返回 Markdown 关系表")
    public String queryGraphRelation(@P("实体名称") String entityName, @P(value = "跳数，1 或 2，默认 1", defaultValue = "1") Integer hops) {
        Long knowledgeId = AiRequestContext.getKnowledgeId();
        if (knowledgeId == null) {
            return "未选择知识库，无法查询图谱";
        }
        AiKnowledgeGraph graph = graphStoreStrategy.findGraphByKnowledge(knowledgeId);
        if (graph == null || !"2".equals(graph.getGraphStatus())) {
            return "知识图谱尚未构建完成";
        }
        AiKgEntity entity = graphStoreStrategy.findEntity(graph.getId(), entityName, null);
        if (entity == null) {
            return "未在图谱中找到实体：" + entityName;
        }

        int h = (hops == null || hops < 1) ? 1 : Math.min(hops, 2);
        Set<Long> ids = new HashSet<>();
        ids.add(entity.getId());
        Set<Long> neighbors = (h == 2)
            ? graphStoreStrategy.findTwoHopEntityIds(graph.getId(), ids)
            : graphStoreStrategy.findNeighborEntityIds(graph.getId(), ids);
        ids.addAll(neighbors);

        List<AiKgRelation> relations = graphStoreStrategy.listRelationsByEntities(graph.getId(), ids);
        Map<Long, String> nameMap = graphStoreStrategy.listEntitiesByGraphId(graph.getId()).stream()
            .collect(Collectors.toMap(AiKgEntity::getId, AiKgEntity::getEntityName, (a, b) -> a));

        if (relations.isEmpty()) {
            return "实体 " + entityName + " 在图谱中没有关系";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("实体 ").append(entityName).append(" 的关系（").append(h).append("跳，共 ").append(relations.size()).append(" 条）：\n\n");
        sb.append("| 头实体 | 关系 | 尾实体 | 描述 | 强度 |\n|---|---|---|---|---|\n");
        for (AiKgRelation r : relations) {
            sb.append("| ").append(nameMap.getOrDefault(r.getSourceEntityId(), "?"))
                .append(" | ").append(r.getRelationType() != null ? r.getRelationType() : "关联")
                .append(" | ").append(nameMap.getOrDefault(r.getTargetEntityId(), "?"))
                .append(" | ").append(r.getDescription() != null ? r.getDescription() : "")
                .append(" | ").append(r.getStrength() != null ? r.getStrength() : "")
                .append(" |\n");
        }
        return sb.toString();
    }

    /**
     * 对当前知识库执行全局检索：基于社区摘要合成对整体/主题问题的答案
     *
     * @param query 查询问题
     * @return 合成答案
     */
    @Tool("对当前知识库执行全局检索：基于社区摘要合成对整体/主题问题的答案（适用于\"这批文档主要涉及什么\"类问题）")
    public String globalSummary(@P("查询问题") String query) {
        Long knowledgeId = AiRequestContext.getKnowledgeId();
        if (knowledgeId == null) {
            return "未选择知识库，无法执行全局检索";
        }
        Map<String, Object> result = globalSearchService.globalSearch(knowledgeId, query);
        Object answer = result.get("answer");
        Object count = result.get("communityCount");
        return String.valueOf(answer) + "\n\n（基于 " + count + " 个社区摘要合成）";
    }
}
