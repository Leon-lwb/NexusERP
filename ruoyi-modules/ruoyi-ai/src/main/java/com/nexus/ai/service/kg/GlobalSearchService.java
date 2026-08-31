package com.nexus.ai.service.kg;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.common.ai.domain.entity.AiKgCommunity;
import com.nexus.common.ai.domain.entity.AiKnowledgeGraph;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.service.IAiModelService;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识图谱全局检索服务（GraphRAG global search）
 * <p>基于社区摘要的 map-reduce：每个社区摘要生成候选答案（map）→ 综合合成最终答案（reduce）。
 * 适用于"这批文档主要涉及哪些业务关系"类整体/主题问题（局部检索无法覆盖）。</p>
 *
 * @author NexusERP
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalSearchService {

    /** 图谱存储策略，提供图谱与社区摘要的读写。 */
    private final GraphStoreStrategy graphStoreStrategy;
    /** ChatModel 工厂，用于获取全局检索用的同步模型。 */
    private final ChatModelFactory chatModelFactory;
    /** AI 模型业务服务，用于解析默认摘要模型。 */
    private final IAiModelService aiModelService;
    /** AI 配置属性，提供全局检索社区数上限等参数。 */
    private final AiProperties aiProperties;

    /** 单社区候选答案最大字符 */
    private static final int MAX_CANDIDATE_CHARS = 400;
    /** reduce 阶段候选答案最大条数（控 token） */
    private static final int MAX_REDUCE_CANDIDATES = 30;

    /**
     * 全局检索
     *
     * @param knowledgeId 知识库ID
     * @param query       查询问题
     * @return {answer, communityCount, communities}
     */
    public Map<String, Object> globalSearch(Long knowledgeId, String query) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (query == null || query.isBlank()) {
            result.put("answer", "查询问题为空");
            result.put("communityCount", 0);
            result.put("communities", List.of());
            return result;
        }

        AiKnowledgeGraph graph = graphStoreStrategy.findGraphByKnowledge(knowledgeId);
        if (graph == null || !"2".equals(graph.getGraphStatus())) {
            result.put("answer", "知识图谱尚未构建完成，无法执行全局检索");
            result.put("communityCount", 0);
            result.put("communities", List.of());
            return result;
        }

        List<AiKgCommunity> communities = graphStoreStrategy.listSummarizedCommunities(graph.getId());
        if (communities.isEmpty()) {
            result.put("answer", "未找到社区摘要，请先完成图谱构建");
            result.put("communityCount", 0);
            result.put("communities", List.of());
            return result;
        }

        // 限制参与 map 的社区数（按 entity_count 取 topN）
        int maxForGlobal = aiProperties.getKnowledgeGraph().getMaxCommunitiesForGlobal();
        if (communities.size() > maxForGlobal) {
            communities = communities.subList(0, maxForGlobal);
        }

        Long modelId = resolveModelId();
        ChatModel chatModel = modelId != null ? chatModelFactory.getChatModel(modelId) : null;
        if (chatModel == null) {
            result.put("answer", "未配置可用的 LLM 模型");
            result.put("communityCount", communities.size());
            result.put("communities", List.of());
            return result;
        }

        // Map：每社区生成候选答案
        List<String> candidates = new ArrayList<>();
        List<String> communityLabels = new ArrayList<>();
        for (AiKgCommunity c : communities) {
            String label = "社区" + (c.getCommunityIndex() != null ? c.getCommunityIndex() : "?")
                + "（" + (c.getEntityCount() != null ? c.getEntityCount() : 0) + " 实体）";
            communityLabels.add(label);
            try {
                String candidate = chatModel.chat(buildMapPrompt(c.getSummary(), query));
                if (candidate != null && candidate.length() > MAX_CANDIDATE_CHARS) {
                    candidate = candidate.substring(0, MAX_CANDIDATE_CHARS);
                }
                candidates.add(candidate != null ? candidate : "");
            } catch (Exception e) {
                log.warn("[KG] 全局检索 map 失败 communityId={}, error={}", c.getId(), e.getMessage());
                candidates.add("");
            }
        }

        // Reduce：合成最终答案
        String answer;
        try {
            answer = chatModel.chat(buildReducePrompt(query, candidates));
        } catch (Exception e) {
            log.error("[KG] 全局检索 reduce 失败", e);
            answer = "全局检索合成失败：" + e.getMessage();
        }

        result.put("answer", answer);
        result.put("communityCount", communities.size());
        result.put("communities", communityLabels);
        return result;
    }

    private String buildMapPrompt(String summary, String query) {
        String s = summary != null ? summary : "";
        return "你是知识图谱分析助手。基于以下社区摘要回答问题；若摘要与问题无关，仅回复\"无关\"。\n\n"
            + "社区摘要：\n" + s + "\n\n"
            + "问题：" + query + "\n\n"
            + "请给出简短答案（≤150字）：";
    }

    private String buildReducePrompt(String query, List<String> candidates) {
        List<String> limited = candidates.size() > MAX_REDUCE_CANDIDATES
            ? candidates.subList(0, MAX_REDUCE_CANDIDATES) : candidates;
        StringBuilder sb = new StringBuilder();
        sb.append("以下是多个社区针对同一问题的候选答案，请综合成一个对问题的最终答案。")
            .append("若多数候选为\"无关\"，请说明文档中可能无相关信息。\n\n");
        sb.append("问题：").append(query).append("\n\n");
        sb.append("候选答案：\n");
        for (int i = 0; i < limited.size(); i++) {
            String c = limited.get(i);
            if (c == null || c.isBlank()) {
                continue;
            }
            sb.append("[").append(i + 1).append("] ").append(c).append("\n");
        }
        sb.append("\n最终答案：");
        return sb.toString();
    }

    private Long resolveModelId() {
        Long configId = aiProperties.getKnowledgeGraph().getExtractionModelId();
        if (configId != null) {
            return configId;
        }
        AiModel def = aiModelService.selectDefaultModel();
        return def != null ? def.getId() : null;
    }
}
