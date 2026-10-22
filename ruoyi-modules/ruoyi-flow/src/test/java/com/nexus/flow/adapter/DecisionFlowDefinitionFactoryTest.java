package com.nexus.flow.adapter;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.config.AiPromptTemplateInitializer;
import com.nexus.ai.config.AiProperties;
import com.nexus.flow.model.FlowGraph;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

class DecisionFlowDefinitionFactoryTest {
    private final DecisionFlowDefinitionFactory factory =
            new DecisionFlowDefinitionFactory(new ObjectMapper(), new AiProperties());

    @Test void analysisGraphIsExecutableAndContainsTrustedStageOrder() {
        var flow = factory.analysisFlow(10L, 20L);
        assertEquals("system_delivery_risk_analysis_v1", flow.getFlowKey());
        assertEquals("PUBLISHED", flow.getStatus());
        var graph = FlowGraph.parse(flow.getConfigJson());
        graph.validate(t -> true);
        var order = graph.getTopologicalOrder();
        assertTrue(order.indexOf("context") < order.indexOf("shortage"));
        assertTrue(order.indexOf("shortage") < order.indexOf("guard"));
        assertTrue(order.indexOf("guard") < order.indexOf("actionProposal"));
        assertEquals("industrial.deliveryContext", graph.getNode("context").getType());
        assertEquals("industrial.actionProposal", graph.getNode("actionProposal").getType());
        assertEquals("rag", graph.getNode("rag").getType());
        assertEquals("llm", graph.getNode("llm").getType());
        assertEquals(90, graph.getNode("llm").getTimeoutSeconds());
    }

    @Test void industrialFlowUsesTheSameStrictSummaryContractAsDirectRetry() {
        var config = FlowGraph.parse(factory.analysisFlow(10L, null).getConfigJson()).getNode("llm").getConfig();
        assertEquals(
                AiPromptTemplateInitializer.DECISION_SUMMARY_SYSTEM_PROMPT,
                config.path("systemPrompt").asText());
        assertEquals("{{nodes.rank.output.summaryPrompt}}", config.path("promptTemplate").asText());
    }

    @Test void legacyAnalysisGraphIsByteForByteCompatibleWithStoredThirtySecondTemplate() throws Exception {
        assertEquals(frozenGraph("legacy-30-model10-knowledge20.json"), factory.legacyAnalysisFlow(10L, 20L).getConfigJson());
        var graph = FlowGraph.parse(factory.legacyAnalysisFlow(10L, 20L).getConfigJson());
        assertEquals(30, graph.getNode("llm").getTimeoutSeconds());
        assertEquals(2, graph.getNode("llm").getRetryMaxTries());
        assertEquals(factory.analysisFlow(10L, 20L).getFlowKey(), factory.legacyAnalysisFlow(10L, 20L).getFlowKey());
    }

    @Test void releasedNinetySecondLegacyGraphMatchesFrozenHistoricalBytes() throws Exception {
        assertEquals(frozenGraph("released-90-model22.json"), factory.releasedDefaultAnalysisFlow(22L, null).getConfigJson());
    }

    static String frozenGraph(String name) throws IOException {
        try (var in = DecisionFlowDefinitionFactoryTest.class.getResourceAsStream("/decision-flow/" + name)) {
            assertNotNull(in, "Historical graph fixture must exist");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test void configuredTimeoutIsBoundedAndChangesOnlyModelNode() {
        var props = new AiProperties();
        props.getFlow().setIndustrialDecisionLlmTimeoutSeconds(120);
        var configured = new DecisionFlowDefinitionFactory(new ObjectMapper(), props);
        var current = FlowGraph.parse(configured.analysisFlow(22L, null).getConfigJson());
        assertEquals(120, current.getNode("llm").getTimeoutSeconds());
        assertEquals(600, current.getNode("guard").getTimeoutSeconds());
        props.getFlow().setIndustrialDecisionLlmTimeoutSeconds(121);
        assertThrows(IllegalArgumentException.class, () -> configured.analysisFlow(22L, null));
    }

    @Test void missingModelAndKnowledgeFallsBackToTrustedDeterministicGuard() {
        var graph = FlowGraph.parse(factory.analysisFlow(null, null).getConfigJson());
        assertFalse(graph.getNodes().containsKey("llm"));
        assertFalse(graph.getNodes().containsKey("rag"));
        assertTrue(graph.getNodes().containsKey("guard"));
        assertEquals("industrial.executeApprovedActions", FlowGraph.parse(factory.executionFlow().getConfigJson())
            .getNode("execute").getType());
    }
    @Test void analysisDisplayNameIsChineseWithoutChangingSystemIdentity() {
        var flow = factory.analysisFlow(10L, 20L);
        assertEquals("工业交付风险分析", flow.getFlowName());
        assertEquals("system_delivery_risk_analysis_v1", flow.getFlowKey());
        assertEquals("PUBLISHED", flow.getStatus());
        assertEquals(1, flow.getVersion());
        assertEquals("MANUAL", flow.getTriggerType());
        assertEquals(1, flow.getMaxRetry());
    }

    @Test void executionDisplayNameIsChineseWithoutChangingProtectedAction() {
        var flow = factory.executionFlow();
        assertEquals("工业交付风险执行", flow.getFlowName());
        assertEquals("system_delivery_risk_execution_v1", flow.getFlowKey());
        assertEquals(1, flow.getVersion());
        assertEquals("PUBLISHED", flow.getStatus());
        var graph = FlowGraph.parse(flow.getConfigJson());
        assertEquals(1, graph.getNodes().size());
        assertEquals("industrial.executeApprovedActions", graph.getNode("execute").getType());
    }
}
