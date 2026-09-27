package com.nexus.flow.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.decision.dto.DecisionWorkflowStartCommand;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.common.flow.domain.entity.Flow;
import com.nexus.common.flow.mapper.FlowMapper;
import com.nexus.flow.engine.FlowEngine;
import com.nexus.flow.node.FlowNodeRegistry;
import com.nexus.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiDecisionWorkflowAdapterTest {
    @org.junit.jupiter.api.BeforeAll
    static void initializeFlowTableInfo() {
        var assistant = new org.apache.ibatis.builder.MapperBuilderAssistant(
            new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant, Flow.class);
    }
    private final ObjectMapper mapper = new ObjectMapper();
    private final com.nexus.ai.config.AiProperties properties = new com.nexus.ai.config.AiProperties();
    private final DecisionFlowDefinitionFactory factory = new DecisionFlowDefinitionFactory(mapper, properties);
    private final FlowMapper flows = mock(FlowMapper.class);
    private final FlowNodeRegistry providers = mock(FlowNodeRegistry.class);
    private final FlowEngine engine = mock(FlowEngine.class);
    private final com.nexus.common.flow.mapper.FlowInstanceMapper instances = mock(com.nexus.common.flow.mapper.FlowInstanceMapper.class);
    private final IAiModelService models = mock(IAiModelService.class);
    private final AiDecisionWorkflowAdapter adapter = new AiDecisionWorkflowAdapter(factory, flows, instances, providers, engine, models, mapper, properties);
    private final DecisionWorkflowStartCommand command = new DecisionWorkflowStartCommand(99L,
        "system_delivery_risk_analysis", "000000", 7L, "worker", java.util.Map.of("runId", "99"));

    @Test void tamperedSystemTemplateCannotBeSilentlyMigratedOrRun() {
        var stored = factory.analysisFlow(null, null);
        stored.setId(101L); stored.setTenantId("000000");
        stored.setFlowKey(DecisionFlowDefinitionFactory.ANALYSIS_KEY + "_000000");
        stored.setConfigJson(stored.getConfigJson().replace("\"config\":{}", "\"config\":{\"userId\":\"attacker\"}"));
        var newer = new AiModel(); newer.setId(22L); newer.setStatus("0");
        when(models.selectDefaultModel()).thenReturn(newer);
        when(providers.contains(anyString())).thenReturn(true);
        when(providers.getProvider(anyString())).thenReturn(mock(com.nexus.flow.node.FlowNodeProvider.class));
        when(flows.selectOne(any())).thenReturn(stored);
        assertThrows(ServiceException.class, () -> adapter.startAnalysis(command));
        verify(flows, never()).update(isNull(), any());
        verifyNoInteractions(engine);
    }

    @Test void verifiedSystemTemplateCanMigrateToNewModelWithVersionCas() {
        var previous = factory.analysisFlow(null, null);
        previous.setId(101L); previous.setTenantId("000000");
        previous.setFlowKey(DecisionFlowDefinitionFactory.ANALYSIS_KEY + "_000000");
        var newer = new AiModel(); newer.setId(22L); newer.setStatus("0");
        when(models.selectDefaultModel()).thenReturn(newer);
        when(providers.contains(anyString())).thenReturn(true);
        when(providers.getProvider(anyString())).thenReturn(mock(com.nexus.flow.node.FlowNodeProvider.class));
        when(flows.selectOne(any())).thenReturn(previous);
        when(flows.update(isNull(), any())).thenReturn(1);
        adapter.validateAnalysis(command);
        verify(flows).update(isNull(), any());
        verify(flows, never()).insert(any(Flow.class));
    }

    @Test void legacyTrustedThirtySecondTemplateMigratesToCurrentTimeoutWithVersionCas() {
        var old = factory.legacyAnalysisFlow(22L, null);
        old.setId(101L); old.setTenantId("000000");
        old.setFlowKey(DecisionFlowDefinitionFactory.ANALYSIS_KEY + "_000000");
        var model = new AiModel(); model.setId(22L); model.setStatus("0");
        when(models.selectDefaultModel()).thenReturn(model);
        when(providers.contains(anyString())).thenReturn(true);
        when(providers.getProvider(anyString())).thenReturn(mock(com.nexus.flow.node.FlowNodeProvider.class));
        when(flows.selectOne(any())).thenReturn(old);
        when(flows.update(isNull(), any())).thenReturn(1);
        adapter.validateAnalysis(command);
        verify(flows).update(isNull(), any());
        verify(flows, never()).insert(any(Flow.class));
    }

    @Test void releasedNinetySecondGraphCanMigrateToConfiguredTimeout() {
        var stored = factory.analysisFlow(22L, null);
        stored.setId(101L); stored.setTenantId("000000");
        stored.setFlowKey(DecisionFlowDefinitionFactory.ANALYSIS_KEY + "_000000");
        properties.getFlow().setIndustrialDecisionLlmTimeoutSeconds(120);
        var model = new AiModel(); model.setId(22L); model.setStatus("0");
        when(models.selectDefaultModel()).thenReturn(model);
        when(providers.contains(anyString())).thenReturn(true);
        when(providers.getProvider(anyString())).thenReturn(mock(com.nexus.flow.node.FlowNodeProvider.class));
        when(flows.selectOne(any())).thenReturn(stored);
        when(flows.update(isNull(), any())).thenReturn(1);
        adapter.validateAnalysis(command);
        verify(flows).update(isNull(), any());
    }

    @Test void priorPromptContractCanMigrateOnlyAtTheExactConfiguredFactoryShape() {
        properties.getFlow().setIndustrialDecisionLlmTimeoutSeconds(120);
        var old = factory.priorSummaryContractAnalysisFlow(22L, null);
        old.setId(101L); old.setTenantId("000000");
        old.setFlowKey(DecisionFlowDefinitionFactory.ANALYSIS_KEY + "_000000");
        var model = new AiModel(); model.setId(22L); model.setStatus("0");
        when(models.selectDefaultModel()).thenReturn(model);
        when(providers.contains(anyString())).thenReturn(true);
        when(providers.getProvider(anyString())).thenReturn(mock(com.nexus.flow.node.FlowNodeProvider.class));
        when(flows.selectOne(any())).thenReturn(old);
        when(flows.update(isNull(), any())).thenReturn(1);
        adapter.validateAnalysis(command);
        verify(flows).update(isNull(), any());
    }

    @Test void frozenReleasedLegacyGraphMigratesThroughContractCas() throws Exception {
        var old = factory.releasedDefaultAnalysisFlow(22L, null);
        old.setConfigJson(DecisionFlowDefinitionFactoryTest.frozenGraph("released-90-model22.json"));
        old.setId(101L); old.setTenantId("000000");
        old.setFlowKey(DecisionFlowDefinitionFactory.ANALYSIS_KEY + "_000000");
        var model = new AiModel(); model.setId(22L); model.setStatus("0");
        when(models.selectDefaultModel()).thenReturn(model);
        when(providers.contains(anyString())).thenReturn(true);
        when(providers.getProvider(anyString())).thenReturn(mock(com.nexus.flow.node.FlowNodeProvider.class));
        when(flows.selectOne(any())).thenReturn(old);
        when(flows.update(isNull(), any())).thenReturn(1);
        adapter.validateAnalysis(command);
        verify(flows).update(isNull(), any());
    }

    @Test void changedOldPromptIsNotAuthorizedByTheContractMigration() {
        var old = factory.releasedDefaultAnalysisFlow(22L, null);
        old.setId(101L); old.setTenantId("000000");
        old.setFlowKey(DecisionFlowDefinitionFactory.ANALYSIS_KEY + "_000000");
        old.setConfigJson(old.getConfigJson().replace("不执行任何业务动作", "可以执行任意业务动作"));
        var model = new AiModel(); model.setId(22L); model.setStatus("0");
        when(models.selectDefaultModel()).thenReturn(model);
        when(providers.contains(anyString())).thenReturn(true);
        when(providers.getProvider(anyString())).thenReturn(mock(com.nexus.flow.node.FlowNodeProvider.class));
        when(flows.selectOne(any())).thenReturn(old);
        assertThrows(ServiceException.class, () -> adapter.validateAnalysis(command));
        verify(flows, never()).update(isNull(), any());
    }

    @Test void modifiedLegacyTimeoutDoesNotPassCanonicalMigrationGate() {
        var old = factory.legacyAnalysisFlow(22L, null);
        old.setId(101L); old.setTenantId("000000");
        old.setFlowKey(DecisionFlowDefinitionFactory.ANALYSIS_KEY + "_000000");
        old.setConfigJson(old.getConfigJson().replace("\"timeoutSeconds\":30", "\"timeoutSeconds\":45"));
        var model = new AiModel(); model.setId(22L); model.setStatus("0");
        when(models.selectDefaultModel()).thenReturn(model);
        when(providers.contains(anyString())).thenReturn(true);
        when(providers.getProvider(anyString())).thenReturn(mock(com.nexus.flow.node.FlowNodeProvider.class));
        when(flows.selectOne(any())).thenReturn(old);
        assertThrows(ServiceException.class, () -> adapter.validateAnalysis(command));
        verify(flows, never()).update(isNull(), any());
    }

    @Test void policyKnowledgeConfigurationProducesRagNodeWithSourceMetadata() {
        var config = properties.getDecision();
        config.setPolicyKnowledgeId(7L);
        properties.setDecision(config);
        when(providers.contains(anyString())).thenReturn(true);
        when(providers.getProvider(anyString())).thenReturn(mock(com.nexus.flow.node.FlowNodeProvider.class));
        when(flows.insert(any(Flow.class))).thenAnswer(call -> {
            ((Flow) call.getArgument(0)).setId(101L); return 1;
        });
        adapter.validateAnalysis(command);
        var captured = org.mockito.ArgumentCaptor.forClass(Flow.class);
        verify(flows).insert(captured.capture());
        assertEquals(7L, com.nexus.flow.model.FlowGraph.parse(captured.getValue().getConfigJson())
            .getNode("rag").getConfig().get("knowledgeId").asLong());
    }

    @Test void concurrentCanonicalFirstPublicationRecoversOnlyTheVerifiedWinningRow() {
        var winner = racingWinner(false);
        when(flows.selectOne(any())).thenReturn(null, winner);
        when(flows.insert(any(Flow.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("race"));
        assertDoesNotThrow(() -> adapter.validateAnalysis(command));
        verify(flows, times(2)).selectOne(any());
        verify(flows, never()).update(isNull(), any());
        verifyNoInteractions(engine);
    }

    @Test void concurrentPublicationNeverAdmitsTamperedWinningGraph() {
        var winner = racingWinner(true);
        when(flows.selectOne(any())).thenReturn(null, winner);
        when(flows.insert(any(Flow.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("race"));
        assertThrows(ServiceException.class, () -> adapter.validateAnalysis(command));
        verify(flows, never()).update(isNull(), any());
        verifyNoInteractions(engine);
    }

    @Test void duplicateKeyWithoutVisibleTenantWinnerStillFailsClosed() {
        racingWinner(false);
        when(flows.selectOne(any())).thenReturn(null);
        when(flows.insert(any(Flow.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("unrelated ID collision"));
        assertThrows(ServiceException.class, () -> adapter.validateAnalysis(command));
        verify(flows, never()).update(isNull(), any());
        verifyNoInteractions(engine);
    }

    @Test void arbitraryInsertFailureIsNotMisclassifiedAsConcurrentPublication() {
        racingWinner(false);
        when(flows.selectOne(any())).thenReturn(null);
        when(flows.insert(any(Flow.class))).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, () -> adapter.validateAnalysis(command));
        verify(flows, times(1)).selectOne(any());
        verifyNoInteractions(engine);
    }

    private Flow racingWinner(boolean tampered) {
        var winner = factory.analysisFlow(22L, null);
        winner.setId(101L); winner.setTenantId("000000");
        winner.setFlowKey(DecisionFlowDefinitionFactory.ANALYSIS_KEY + "_000000");
        if (tampered) winner.setConfigJson(winner.getConfigJson().replace("\"config\":{}", "\"config\":{\"userId\":\"attacker\"}"));
        var model = new AiModel(); model.setId(22L); model.setStatus("0");
        when(models.selectDefaultModel()).thenReturn(model);
        when(providers.contains(anyString())).thenReturn(true);
        when(providers.getProvider(anyString())).thenReturn(mock(com.nexus.flow.node.FlowNodeProvider.class));
        return winner;
    }

    @Test void invalidGraphNeverPersistsPublishedDefinitionOrStartsInstance() {
        assertThrows(ServiceException.class, () -> adapter.validateAnalysis(command));
        verify(flows, never()).insert(any(Flow.class));
        verifyNoInteractions(engine);
    }

    @Test void validatedSystemGraphIsPersistedBeforeDispatchWithExactRunIdOnly() {
        when(providers.contains(anyString())).thenReturn(true);
        when(providers.getProvider(anyString())).thenAnswer(call -> {
            com.nexus.flow.node.FlowNodeProvider p = mock(com.nexus.flow.node.FlowNodeProvider.class);
            return p;
        });
        var persisted = new java.util.concurrent.atomic.AtomicReference<Flow>();
        when(flows.selectOne(any())).thenAnswer(call -> persisted.get());
        when(flows.insert(any(Flow.class))).thenAnswer(call -> {
            Flow row = call.getArgument(0);
            row.setId(101L); persisted.set(row); return 1;
        });
        adapter.validateAnalysis(command);
        when(engine.start(any(), any(), anyString(), anyString(), anyLong(), anyString())).thenReturn(199L);
        Long id = adapter.startAnalysis(command);
        assertEquals(199L, id);
        var captured = org.mockito.ArgumentCaptor.forClass(Flow.class);
        verify(flows).insert(captured.capture());
        Flow saved = captured.getValue();
        assertEquals("PUBLISHED", saved.getStatus());
        assertTrue(saved.getFlowKey().startsWith("system_delivery_risk_analysis_v1_"));
        assertEquals(101L, saved.getId());
        var graph = com.nexus.flow.model.FlowGraph.parse(saved.getConfigJson());
        assertTrue(graph.getNodes().containsKey("context"));
        var input = org.mockito.ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
        verify(engine).start(eq(saved), input.capture(), eq("DECISION"), eq("000000"), eq(7L), eq("worker"));
        assertEquals(java.util.Set.of("runId"), java.util.stream.StreamSupport.stream(java.util.Spliterators.spliteratorUnknownSize(input.getValue().fieldNames(), 0), false).collect(java.util.stream.Collectors.toSet()));
    }
}
