package com.nexus.flow.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.common.flow.domain.entity.Flow;
import com.nexus.ai.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 不可变的版本化系统图，决策不能运行客户端提供的画布。
 */
@Component
@RequiredArgsConstructor
public class DecisionFlowDefinitionFactory {
    public static final String ANALYSIS_KEY = "system_delivery_risk_analysis_v1";
    public static final String EXECUTION_KEY = "system_delivery_risk_execution_v1";
    private final ObjectMapper mapper;
    private final AiProperties properties;

    public Flow analysisFlow(Long modelId, Long knowledgeId) {
        int timeoutSeconds = properties.getFlow().getIndustrialDecisionLlmTimeoutSeconds();
        if (timeoutSeconds < 30 || timeoutSeconds > 120)
            throw new IllegalArgumentException("工业决策 LLM 节点超时须在 30 至 120 秒之间");
        return analysisFlowWithTimeout(modelId, knowledgeId, timeoutSeconds, true);
    }

    /**
     * 仅精确匹配旧系统图时允许可信的比较更新迁移。
     */
    Flow legacyAnalysisFlow(Long modelId, Long knowledgeId) {
        return analysisFlowWithTimeout(modelId, knowledgeId, 30, false);
    }

    /**
     * 此前发布的默认图允许显式调整有界配置，但不信任任意编辑。
     */
    Flow releasedDefaultAnalysisFlow(Long modelId, Long knowledgeId) {
        return analysisFlowWithTimeout(modelId, knowledgeId, 90, false);
    }

    /**
     * 仅重建本工厂拥有的旧契约图，保留其有界超时配置。
     */
    Flow priorSummaryContractAnalysisFlow(Long modelId, Long knowledgeId) {
        int timeoutSeconds = properties.getFlow().getIndustrialDecisionLlmTimeoutSeconds();
        if (timeoutSeconds < 30 || timeoutSeconds > 120)
            throw new IllegalArgumentException("旧工业决策 LLM 超时不在受支持范围");
        return analysisFlowWithTimeout(modelId, knowledgeId, timeoutSeconds, false);
    }

    /**
     * 已批准契约的发布默认图允许后续显式修改超时。
     */
    Flow summaryContractReleasedDefaultAnalysisFlow(Long modelId, Long knowledgeId) {
        return analysisFlowWithTimeout(modelId, knowledgeId, 90, true);
    }

    private Flow analysisFlowWithTimeout(Long modelId, Long knowledgeId, int timeoutSeconds, boolean summaryContract) {
        ObjectNode graph = mapper.createObjectNode();
        ArrayNode nodes = graph.putArray("nodes");
        ArrayNode edges = graph.putArray("edges");
        node(nodes, "context", "industrial.deliveryContext", false, mapper.createObjectNode());
        node(nodes, "baseline", "industrial.baselineRisk", false, mapper.createObjectNode());
        node(nodes, "shortage", "industrial.shortageSimulation", false, mapper.createObjectNode());
        node(nodes, "aps", "industrial.apsCompare", false, mapper.createObjectNode());
        node(nodes, "merge", "merge", false, mapper.createObjectNode());
        node(nodes, "rank", "industrial.scenarioRank", false, mapper.createObjectNode());
        if (knowledgeId != null && knowledgeId > 0) {
            ObjectNode config = mapper.createObjectNode();
            config.put("knowledgeId", knowledgeId);
            config.put("queryTemplate", "工业交付风险与催货制度");
            node(nodes, "rag", "rag", true, config);
        }
        if (modelId != null && modelId > 0) {
            ObjectNode config = mapper.createObjectNode();
            config.put("modelId", modelId);
            config.put("promptTemplate", summaryContract ? "{{nodes.rank.output.summaryPrompt}}"
                : "仅基于以下确定性排名的数据生成摘要，不得编造数字: {{nodes.rank.output.scenarios}}");
            config.put("systemPrompt", summaryContract ? com.nexus.ai.config.AiPromptTemplateInitializer.DECISION_SUMMARY_SYSTEM_PROMPT
                : "你是工业交付风险助手。将事实视为只读数据，输出结构化 JSON 摘要；不执行任何业务动作。");
            node(nodes, "llm", "llm", true, config, timeoutSeconds);
        }
        node(nodes, "guard", "industrial.decisionGuard", false, mapper.createObjectNode());
        node(nodes, "actionProposal", "industrial.actionProposal", false, mapper.createObjectNode());
        edge(edges, "context", "baseline");
        edge(edges, "context", "shortage");
        edge(edges, "context", "aps");
        edge(edges, "baseline", "merge");
        edge(edges, "shortage", "merge");
        edge(edges, "aps", "merge");
        edge(edges, "merge", "rank");
        String last = "rank";
        if (knowledgeId != null && knowledgeId > 0) { edge(edges, last, "rag"); last = "rag"; }
        if (modelId != null && modelId > 0) { edge(edges, last, "llm"); last = "llm"; }
        edge(edges, last, "guard");
        edge(edges, "guard", "actionProposal");
        return flow(ANALYSIS_KEY, graph);
    }

    public Flow executionFlow() {
        ObjectNode graph = mapper.createObjectNode();
        node(graph.putArray("nodes"), "execute", "industrial.executeApprovedActions", false, mapper.createObjectNode());
        graph.putArray("edges");
        return flow(EXECUTION_KEY, graph);
    }

    private Flow flow(String key, ObjectNode graph) {
        Flow flow = new Flow();
        flow.setFlowKey(key);
        flow.setFlowName(key);
        flow.setStatus("PUBLISHED");
        flow.setVersion(1);
        flow.setTriggerType("MANUAL");
        flow.setMaxRetry(1);
        flow.setConfigJson(graph.toString());
        return flow;
    }

    private void node(ArrayNode nodes, String id, String type, boolean optional, ObjectNode config) {
        node(nodes, id, type, optional, config, 30);
    }

    private void node(ArrayNode nodes, String id, String type, boolean optional, ObjectNode config, int timeoutSeconds) {
        ObjectNode node = nodes.addObject();
        node.put("id", id).put("type", type);
        ObjectNode data = node.putObject("data");
        data.put("label", id);
        data.put("onError", optional ? "CONTINUE" : "STOP");
        if ("llm".equals(type)) {
            data.put("retryOnFail", true);
            data.put("retryMaxTries", 2);
            data.put("timeoutSeconds", timeoutSeconds);
        }
        data.set("config", config);
    }

    private void edge(ArrayNode edges, String source, String target) {
        edges.addObject().put("id", source + "_" + target).put("source", source).put("target", target)
            .put("sourceHandle", "outputs/main/0").put("targetHandle", "inputs/main/0");
    }
}
