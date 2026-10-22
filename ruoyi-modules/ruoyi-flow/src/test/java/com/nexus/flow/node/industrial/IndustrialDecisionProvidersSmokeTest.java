package com.nexus.flow.node.industrial;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.hutool.extra.spring.SpringUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.service.decision.*;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.*;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.json.config.JacksonConfig;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.flow.adapter.DecisionFlowDefinitionFactory;
import com.nexus.flow.model.FlowGraph;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.state.FlowState;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

// 同名类型说明：本文件同时使用 com.nexus.common.json.config.JacksonConfig 和
// com.nexus.common.web.config.JacksonConfig，后者保留全限定名以区分。

class IndustrialDecisionProvidersSmokeTest {
    private final ObjectMapper mapper =
            new ObjectMapper()
                    .registerModule(new JacksonConfig().registerJavaTimeModule())
                    .registerModule(
                            new com.nexus.common.web.config.JacksonConfig().bigNumberModule());
    private final DecisionPersistenceService store = mock(DecisionPersistenceService.class);
    private final IndustrialDecisionDataPort port = mock(IndustrialDecisionDataPort.class);
    private final DecisionNodeSupport support = new DecisionNodeSupport(store, port, mapper);
    private final DecisionActor actor = new DecisionActor("TENANT-A", 7L, "analyst", Set.of("ai:decision:run", "ai:decision:execute"));
    private final DecisionBusinessRef ref = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 99L, "WO-99");
    private final LocalDateTime observed = LocalDateTime.of(2026, 9, 26, 10, 0);

    private AiDecisionRun run() {
        var row = new AiDecisionRun(); row.setId(21L); row.setTenantId("TENANT-A");
        row.setBizType("WORK_ORDER"); row.setBizId(99L); row.setBizCode("WO-99");
        row.setStatus("RUNNING");
        row.setScoreConfigJson("{\"horizonDays\":30,\"scenarioCodes\":[\"BASELINE_CURRENT\"]}");
        return row;
    }
    private DeliveryContextSnapshot snapshot() {
        return new DeliveryContextSnapshot(ref, List.of(), List.of(), Map.of(), List.of(),
            List.of(new DeliveryContextSnapshot.StockFact(1L, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO)),
            List.of(), Map.of(LocalDate.of(2026, 9, 26), Map.of("R", BigDecimal.TEN)), List.of(),
            Map.of("planId", "88"), List.of(), "hash", observed, List.of());
    }
    private FlowExecutionContext context(String type, String nodeId) throws Exception {
        var ctx = new FlowExecutionContext();
        ctx.setTenantId("TENANT-A"); ctx.setNodeType(type); ctx.setNodeName(nodeId); ctx.setNodeId(nodeId);
        ctx.setState(new FlowState(mapper.readTree("{\"runId\":\"21\",\"userId\":\"999\"}")));
        return ctx;
    }
    private void trusted() {
        when(store.requireRun(21L, "TENANT-A")).thenReturn(run());
        when(store.trustedActor(21L, "TENANT-A")).thenReturn(actor);
        when(store.trustedExecutionActor(21L, "TENANT-A")).thenReturn(actor);
        when(port.canAccess(any(), eq(actor))).thenReturn(true);
        when(store.requireHorizonDays(any())).thenReturn(30);
        when(store.requireSnapshot(21L, "TENANT-A")).thenReturn(snapshot());
    }
    private <T> T inTenant(Supplier<T> supplier) {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class);
             MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            return supplier.get();
        }
    }

    @Test void contextLoadsScopedSnapshotAndPersistsBeforeOutput() throws Exception {
        trusted();
        when(port.loadDeliveryContext(eq(ref), eq(30), eq(actor))).thenReturn(snapshot());
        var provider = new DeliveryContextNodeProvider(support, store, port);
        var ctx = context(provider.getNodeType(), "context");
        var output = inTenant(() -> provider.execute(ctx));
        assertEquals("hash", output.get("snapshotHash").asText());
        verify(store).saveSnapshot(eq(21L), any(DeliveryContextSnapshot.class));
    }
    @Test void baselineReadsOnlyTenantScopedPersistedSnapshot() throws Exception {
        trusted(); var provider = new BaselineRiskNodeProvider(support, store);
        assertTrue(inTenant(() -> provider.execute(contextUnchecked(provider.getNodeType(), "baseline"))).get("capacityKnown").asBoolean());
    }
    @Test void shortageDelegatesToFreshnessEnforcingErpPort() throws Exception {
        trusted();
        var simulation = new ShortageSimulationResult(false, 0, null, BigDecimal.ZERO, List.of(), null, List.of(), List.of());
        when(port.simulateShortage(any(), eq(30), eq(Map.of()), eq(actor))).thenReturn(simulation);
        var provider = new ShortageSimulationNodeProvider(support, store, port);
        assertFalse(inTenant(() -> provider.execute(contextUnchecked(provider.getNodeType(), "shortage"))).get("hasRisk").asBoolean());
        verify(port).simulateShortage(any(), eq(30), eq(Map.of()), eq(actor));
    }
    @Test void apsUsesOnlyFrozenScenarioCodes() throws Exception {
        trusted(); var aps = mock(ApsWhatIfSimulationService.class);
        when(aps.compare(any(), eq(List.of("BASELINE_CURRENT")), eq(30), eq(actor)))
            .thenReturn(new ApsScenarioCompareResult(List.of(), List.of()));
        var provider = new ApsScenarioCompareNodeProvider(support, store, aps, mapper);
        assertTrue(inTenant(() -> provider.execute(contextUnchecked(provider.getNodeType(), "aps"))).get("scenarios").isArray());
    }
    @Test void rankDelegatesToDeterministicService() throws Exception {
        trusted(); var ranking = mock(DecisionScenarioRankingService.class);
        when(ranking.rank(anyLong(), any(), anyInt(), anyList())).thenReturn(List.of());
        var analysis = mock(DecisionAnalysisAssemblyService.class);
        var summaries = mock(DecisionSummaryService.class);
        var facts = new DecisionResult(21L, ref, 50, DecisionRiskLevel.HIGH, "HIGH", List.of("MATERIAL_SHORTAGE"),
            null, List.of(), null, Map.of(), List.of(), List.of(), List.of());
        when(analysis.prepare(any(), any(), any(), anyList(), anyList(), isNull())).thenReturn(facts);
        when(summaries.renderPrompt(any())).thenReturn("trusted summary prompt");
        var provider = new ScenarioRankNodeProvider(support, ranking, store, analysis, summaries);
        var ctx = context(provider.getNodeType(), "rank");
        ctx.getState().putNodeOutput("aps", mapper.valueToTree(new ApsScenarioCompareResult(List.of(), List.of())));
        ctx.getState().putNodeOutput("shortage", mapper.valueToTree(
            new ShortageSimulationResult(false, 0, null, BigDecimal.ZERO, List.of(), null, List.of(), List.of())));
        var output = inTenant(() -> provider.execute(ctx));
        assertTrue(output.get("scenarios").isArray());
        assertEquals("trusted summary prompt", output.get("summaryPrompt").asText());
        var promptInput = ArgumentCaptor.forClass(DecisionSummaryService.SummaryInput.class);
        verify(summaries).renderPrompt(promptInput.capture());
        assertEquals(facts.riskScore(), promptInput.getValue().riskScore());
        assertEquals(facts.rootCauseCodes(), promptInput.getValue().rootCauseCodes());
        assertEquals(facts.evidence(), promptInput.getValue().evidence());
        assertNull(promptInput.getValue().modelId());
        verify(ranking).rank(eq(21L), any(), eq(30), anyList());
    }
    @Test void unverifiedFactsCannotBecomeModelPromptContext() throws Exception {
        trusted();
        var ranking = mock(DecisionScenarioRankingService.class);
        when(ranking.rank(anyLong(), any(), anyInt(), anyList())).thenReturn(List.of());
        var analysis = mock(DecisionAnalysisAssemblyService.class);
        var summaries = mock(DecisionSummaryService.class);
        when(analysis.prepare(any(), any(), any(), anyList(), anyList(), isNull()))
                .thenThrow(new ServiceException("缺少可信业务源事实"));
        var provider = new ScenarioRankNodeProvider(support, ranking, store, analysis, summaries);
        var ctx = context(provider.getNodeType(), "rank");
        ctx.getState().putNodeOutput("aps", mapper.valueToTree(new ApsScenarioCompareResult(List.of(), List.of())));
        ctx.getState().putNodeOutput("shortage", mapper.valueToTree(
            new ShortageSimulationResult(false, 0, null, BigDecimal.ZERO, List.of(), null, List.of(), List.of())));
        assertThrows(ServiceException.class, () -> inTenant(() -> provider.execute(ctx)));
        verifyNoInteractions(summaries);
        verify(store, never()).saveActions(anyLong(), anyList(), any());
    }

    @Test void guardCanOnlyPublishServerComputedResult() throws Exception {
        trusted(); var analysis = mock(DecisionAnalysisAssemblyService.class);
        var proposed = new DecisionResult(21L, ref, 50, DecisionRiskLevel.HIGH, "HIGH", List.of(), null,
            List.of(), null, Map.of(), List.of(), List.of(), List.of());
        when(analysis.prepare(any(), any(), any(), anyList(), anyList(), isNull())).thenReturn(proposed);
        var provider = new DecisionPolicyGuardNodeProvider(support, store, analysis, mapper);
        var ctx = context(provider.getNodeType(), "guard");
        ctx.setGraph(
                FlowGraph.parse(
                        new DecisionFlowDefinitionFactory(mapper, new AiProperties())
                                .analysisFlow(null, null)
                                .getConfigJson()));
        ctx.getState().putNodeOutput("shortage", mapper.valueToTree(
            new ShortageSimulationResult(false, 0, null, BigDecimal.ZERO, List.of(), null, List.of(), List.of())));
        ctx.getState().putNodeOutput("rank", mapper.valueToTree(Map.of("scenarios", List.of(Map.of("scenarioCode", "BASELINE_CURRENT", "inputHash", "fingerprint")))));
        assertEquals("21", inTenant(() -> provider.execute(ctx)).get("runId").asText());
        verify(analysis).prepare(any(), any(), any(), anyList(), anyList(), isNull());
    }
    @Test void executionNodeRestoresApproverRatherThanFlowInputUser() throws Exception {
        trusted(); var execution = mock(DecisionActionExecutionService.class);
        var finalizer = mock(DecisionFinalizeService.class);
        AiDecisionRun terminal = run(); terminal.setStatus("SUCCEEDED");
        when(store.requireRun(21L, "TENANT-A")).thenReturn(terminal);
        var provider = new DecisionExecuteApprovedActionsNodeProvider(support, execution, store, finalizer);
        var ctx = context(provider.getNodeType(), "execute");
        assertEquals("SUCCEEDED", inTenant(() -> provider.execute(ctx)).get("status").asText());
        verify(store).trustedExecutionActor(21L, "TENANT-A");
        verify(execution).executeApproved(21L, actor);
        verify(finalizer).finalizeRun(21L, actor);
    }
    private FlowExecutionContext contextUnchecked(String type, String name) {
        try { return context(type, name); }
        catch (Exception impossible) { throw new AssertionError(impossible); }
    }
}
