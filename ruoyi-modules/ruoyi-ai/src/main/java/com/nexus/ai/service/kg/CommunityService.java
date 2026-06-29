package com.nexus.ai.service.kg;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.common.ai.domain.entity.AiKgCommunity;
import com.nexus.common.ai.domain.entity.AiKgEntity;
import com.nexus.common.ai.domain.entity.AiKgRelation;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识图谱社区服务（GraphRAG 范式）
 * <p>{@link #detectCommunities} 用加权标签传播分簇并落库；{@link #summarizeCommunities} 为每个社区生成 LLM 摘要。</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityService {

    private final GraphStoreStrategy graphStoreStrategy;
    private final ChatModelFactory chatModelFactory;
    private final AiProperties aiProperties;

    /** 社区摘要 prompt 中列出的最大实体/关系数（控 token） */
    private static final int MAX_PROMPT_ENTITIES = 30;
    private static final int MAX_PROMPT_RELATIONS = 30;
    /** 社区摘要单条最大字符 */
    private static final int MAX_SUMMARY_CHARS = 5000;

    /**
     * 社区检测：标签传播分簇 → 落库 ai_kg_community → 回填 entity.community_id
     *
     * @param graphId 图谱ID
     * @return 社区数（实体为空返回 0）
     */
    public int detectCommunities(Long graphId) {
        // 清理旧社区（增量重算支持）
        graphStoreStrategy.deleteCommunitiesByGraphId(graphId);

        List<AiKgEntity> entities = graphStoreStrategy.listEntitiesByGraphId(graphId);
        if (entities.isEmpty()) {
            return 0;
        }
        List<AiKgRelation> relations = graphStoreStrategy.listRelationsByGraphId(graphId);

        // 构建无向加权邻接表（边权 = relation.strength）
        Map<Long, Map<Long, Integer>> adjacency = new LinkedHashMap<>();
        for (AiKgEntity e : entities) {
            adjacency.put(e.getId(), new LinkedHashMap<>());
        }
        for (AiKgRelation r : relations) {
            Long s = r.getSourceEntityId();
            Long t = r.getTargetEntityId();
            if (s == null || t == null) {
                continue;
            }
            int w = r.getStrength() != null ? r.getStrength() : 5;
            adjacency.computeIfAbsent(s, k -> new LinkedHashMap<>()).merge(t, w, Integer::sum);
            adjacency.computeIfAbsent(t, k -> new LinkedHashMap<>()).merge(s, w, Integer::sum);
        }

        Map<Long, Long> labels = LabelPropagationClustering.cluster(adjacency,
            aiProperties.getKnowledgeGraph().getLabelPropagationMaxIter());

        // 按标签分组
        Map<Long, List<AiKgEntity>> groups = new LinkedHashMap<>();
        for (AiKgEntity e : entities) {
            Long label = labels.getOrDefault(e.getId(), e.getId());
            groups.computeIfAbsent(label, k -> new ArrayList<>()).add(e);
        }
        groups = capCommunities(groups);

        // 落库社区 + 构建 entityId → communityId 映射
        Map<Long, Long> entityIdToCommunityId = new LinkedHashMap<>();
        int index = 0;
        for (Map.Entry<Long, List<AiKgEntity>> g : groups.entrySet()) {
            Set<Long> entityIds = g.getValue().stream().map(AiKgEntity::getId).collect(Collectors.toSet());
            int relationCount = 0;
            for (AiKgRelation r : relations) {
                if (entityIds.contains(r.getSourceEntityId()) && entityIds.contains(r.getTargetEntityId())) {
                    relationCount++;
                }
            }
            AiKgCommunity community = new AiKgCommunity();
            community.setGraphId(graphId);
            community.setCommunityLevel(0);
            community.setCommunityIndex(index++);
            community.setEntityCount(g.getValue().size());
            community.setRelationCount(relationCount);
            community.setSummaryStatus("0");
            community.setTokenUsed(0L);
            graphStoreStrategy.insertCommunity(community);
            for (AiKgEntity e : g.getValue()) {
                entityIdToCommunityId.put(e.getId(), community.getId());
            }
        }
        graphStoreStrategy.assignCommunityIds(entityIdToCommunityId);
        return groups.size();
    }

    /**
     * 为社区生成 LLM 摘要（串行；失败标 '3' 不中断）
     *
     * @param graphId  图谱ID
     * @param modelId  摘要用的 LLM 模型ID
     */
    public void summarizeCommunities(Long graphId, Long modelId) {
        List<AiKgCommunity> communities = graphStoreStrategy.listCommunitiesByGraphId(graphId);
        if (communities.isEmpty()) {
            return;
        }
        // 按社区分组实体/关系
        Map<Long, List<AiKgEntity>> entitiesByCommunity = groupEntitiesByCommunity(graphId);
        Map<Long, List<AiKgRelation>> relationsByCommunity = groupRelationsByCommunity(graphId, entitiesByCommunity);

        ChatModel chatModel = modelId != null ? chatModelFactory.getChatModel(modelId) : null;

        for (AiKgCommunity community : communities) {
            if (!"0".equals(community.getSummaryStatus()) && !"3".equals(community.getSummaryStatus())) {
                continue;
            }
            try {
                community.setSummaryStatus("1");
                graphStoreStrategy.updateCommunity(community);

                String prompt = buildSummaryPrompt(community,
                    entitiesByCommunity.getOrDefault(community.getId(), List.of()),
                    relationsByCommunity.getOrDefault(community.getId(), List.of()));

                String summary = chatModel != null ? chatModel.chat(prompt) : "";
                if (summary != null && summary.length() > MAX_SUMMARY_CHARS) {
                    summary = summary.substring(0, MAX_SUMMARY_CHARS);
                }
                community.setSummary(summary);
                community.setSummaryStatus("2");
                community.setTokenUsed(captureTokens());
                community.setErrorMessage(null);
            } catch (Exception e) {
                log.warn("[KG] 社区摘要生成失败 graphId={}, communityId={}, error={}", graphId, community.getId(), e.getMessage());
                community.setSummaryStatus("3");
                community.setErrorMessage(truncate(e.getMessage(), 1000));
            }
            graphStoreStrategy.updateCommunity(community);
        }
    }

    /**
     * 构建社区摘要 prompt
     */
    private String buildSummaryPrompt(AiKgCommunity community, List<AiKgEntity> entities, List<AiKgRelation> relations) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个知识图谱分析专家。以下是知识图谱中的一个社区（一组相关实体及其关系），请用中文生成一段不超过200字的摘要，概括该社区的主题与核心关系。\n\n");
        sb.append("=== 社区实体 ===\n");
        int ec = 0;
        for (AiKgEntity e : entities) {
            if (ec++ >= MAX_PROMPT_ENTITIES) {
                sb.append("...（共 ").append(entities.size()).append(" 个实体）\n");
                break;
            }
            sb.append("- [").append(e.getEntityType() != null ? e.getEntityType() : "其他").append("] ")
                .append(e.getEntityName());
            if (e.getDescription() != null && !e.getDescription().isBlank()) {
                sb.append("：").append(e.getDescription());
            }
            sb.append("\n");
        }
        sb.append("\n=== 社区关系 ===\n");
        // 关系展示需要实体名，构建 id→name 映射
        Map<Long, String> nameMap = entities.stream()
            .collect(Collectors.toMap(AiKgEntity::getId, AiKgEntity::getEntityName, (a, b) -> a));
        int rc = 0;
        for (AiKgRelation r : relations) {
            if (rc++ >= MAX_PROMPT_RELATIONS) {
                sb.append("...（共 ").append(relations.size()).append(" 条关系）\n");
                break;
            }
            sb.append("- ").append(nameMap.getOrDefault(r.getSourceEntityId(), "?"))
                .append(" --").append(r.getRelationType() != null ? r.getRelationType() : "关联").append("--> ")
                .append(nameMap.getOrDefault(r.getTargetEntityId(), "?")).append("\n");
        }
        sb.append("\n请直接输出摘要文字，不要附加任何说明或代码块标记。");
        return sb.toString();
    }

    /**
     * 社区数上限保护：超出时将小社区合并到最大社区
     */
    private Map<Long, List<AiKgEntity>> capCommunities(Map<Long, List<AiKgEntity>> groups) {
        int maxCommunities = aiProperties.getKnowledgeGraph().getMaxCommunities();
        if (groups.size() <= maxCommunities) {
            return groups;
        }
        List<Map.Entry<Long, List<AiKgEntity>>> sorted = new ArrayList<>(groups.entrySet());
        sorted.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));
        Long topLabel = sorted.get(0).getKey();
        Set<Long> kept = sorted.subList(0, maxCommunities).stream()
            .map(Map.Entry::getKey).collect(Collectors.toSet());
        Map<Long, List<AiKgEntity>> merged = new LinkedHashMap<>();
        for (Map.Entry<Long, List<AiKgEntity>> e : groups.entrySet()) {
            Long target = kept.contains(e.getKey()) ? e.getKey() : topLabel;
            merged.computeIfAbsent(target, k -> new ArrayList<>()).addAll(e.getValue());
        }
        return merged;
    }

    private Map<Long, List<AiKgEntity>> groupEntitiesByCommunity(Long graphId) {
        List<AiKgEntity> entities = graphStoreStrategy.listEntitiesByGraphId(graphId);
        Map<Long, List<AiKgEntity>> map = new LinkedHashMap<>();
        for (AiKgEntity e : entities) {
            if (e.getCommunityId() == null) {
                continue;
            }
            map.computeIfAbsent(e.getCommunityId(), k -> new ArrayList<>()).add(e);
        }
        return map;
    }

    private Map<Long, List<AiKgRelation>> groupRelationsByCommunity(Long graphId,
                                                                     Map<Long, List<AiKgEntity>> entitiesByCommunity) {
        Map<Long, Long> entityIdToCommunity = new LinkedHashMap<>();
        entitiesByCommunity.forEach((communityId, list) ->
            list.forEach(e -> entityIdToCommunity.put(e.getId(), communityId)));
        List<AiKgRelation> relations = graphStoreStrategy.listRelationsByGraphId(graphId);
        Map<Long, List<AiKgRelation>> map = new LinkedHashMap<>();
        for (AiKgRelation r : relations) {
            Long communityId = entityIdToCommunity.get(r.getSourceEntityId());
            if (communityId == null) {
                continue;
            }
            map.computeIfAbsent(communityId, k -> new ArrayList<>()).add(r);
        }
        return map;
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
