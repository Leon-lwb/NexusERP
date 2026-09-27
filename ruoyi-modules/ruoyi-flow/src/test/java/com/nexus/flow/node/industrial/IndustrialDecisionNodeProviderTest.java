package com.nexus.flow.node.industrial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.service.decision.DecisionPersistenceService;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.state.FlowState;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IndustrialDecisionNodeProviderTest {
    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new com.nexus.common.json.config.JacksonConfig().registerJavaTimeModule())
        .registerModule(new com.nexus.common.web.config.JacksonConfig().bigNumberModule());
    private final DecisionPersistenceService store = mock(DecisionPersistenceService.class);
    private final IndustrialDecisionDataPort dataPort = mock(IndustrialDecisionDataPort.class);

    private static org.springframework.beans.factory.ObjectProvider<com.nexus.ai.service.decision.DeliveryRiskDecisionOrchestrator>
    orchestratorProvider(com.nexus.ai.service.decision.DeliveryRiskDecisionOrchestrator orchestrator) {
        org.springframework.beans.factory.ObjectProvider<com.nexus.ai.service.decision.DeliveryRiskDecisionOrchestrator> provider =
            mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getObject()).thenReturn(orchestrator);
        return provider;
    }
    @Test void actionProposalSendsReadyAfterVerifiedCompletion() throws Exception {
        var events = mock(com.nexus.ai.service.sse.AiDecisionSseMessageHelper.class);
        var support = spy(new DecisionNodeSupport(store, dataPort, mapper));
        var ctx = context("tenant-A", "{\"runId\":\"21\"}");
        ctx.setNodeId("actionProposal"); ctx.setNodeType("industrial.actionProposal");
        var ref = new com.nexus.common.ai.decision.dto.DecisionBusinessRef(
            com.nexus.common.ai.decision.enums.DecisionBusinessType.WORK_ORDER, 8L, "WO-8");
        var result = new com.nexus.common.ai.decision.dto.DecisionResult(21L, ref, 60,
            com.nexus.common.ai.decision.enums.DecisionRiskLevel.HIGH, "HIGH", java.util.List.of("DELIVERY_DELAY", "MATERIAL_SHORTAGE"), null,
            java.util.List.of(), null, java.util.Map.of("summaryMode", "TEMPLATE_FALLBACK",
                "executiveSummary", "人工检查", "riskExplanation", "原因", "rootCauseExplanations", java.util.List.of(),
                "scenarioTradeoffs", java.util.List.of(), "recommendationRationale", "需审批",
                "evidenceCitations", java.util.List.of(), "uncertaintyNotes", java.util.List.of(),
                "operatorChecklist", java.util.List.of()), java.util.List.of(), java.util.List.of(), java.util.List.of());
        ctx.getState().putNodeOutput("guard", mapper.valueToTree(result));
        var required = java.time.LocalDate.of(2026, 10, 3);
        var shortage = new com.nexus.common.ai.decision.dto.ShortageSimulationResult(true, 1, required,
            java.math.BigDecimal.TEN, java.util.List.of(new com.nexus.common.ai.decision.dto.ShortageSimulationResult.Item(
                100L, "MAT-100", "material", required, java.math.BigDecimal.TEN,
                java.math.BigDecimal.valueOf(12), java.util.List.of(8L), java.util.List.of(),
                "PURCHASE_DELAY", java.util.List.of("ERP:MAT:100"))), null, java.util.List.of(), java.util.List.of());
        ctx.getState().putNodeOutput("shortage", mapper.valueToTree(shortage));
        doReturn(new DecisionActor("tenant-A", 7L, "trusted", Set.of("ai:decision:run"))).when(support).actor(ctx);
        var snapshot = new com.nexus.common.ai.decision.dto.DeliveryContextSnapshot(ref,
            java.util.List.of(), java.util.List.of(new com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.WorkOrderFact(
                8L, "WO-8", 100L, java.math.BigDecimal.TEN, required, 1L, 9L)),
            java.util.Map.of(), java.util.List.of(new com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.RequirementFact(
                100L, "MAT-100", "material", required, java.math.BigDecimal.TEN, 8L, "WO-8")), java.util.List.of(),
            java.util.List.of(new com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.ArrivalFact(
                100L, 500L, required.plusDays(11), java.math.BigDecimal.valueOf(12), 700L)),
            java.util.Map.of(), java.util.List.of(), java.util.Map.of(), java.util.List.of(),
            "hash", java.time.LocalDateTime.of(2026, 9, 26, 10, 0), java.util.List.of());
        when(store.requireSnapshot(21L, "tenant-A")).thenReturn(snapshot);
        when(store.requireRun(21L, "tenant-A")).thenReturn(new AiDecisionRun());
        when(store.requireHorizonDays(any())).thenReturn(30);
        var proposals = mock(com.nexus.ai.service.decision.DecisionActionProposalService.class);
        var guard = mock(com.nexus.ai.service.decision.DecisionPolicyGuardService.class);
        when(guard.validate(any(), any(), any())).thenReturn(new com.nexus.ai.service.decision.DecisionPolicyGuardService.PolicyResult(
            true, java.util.List.of(), null, false, true));
        when(proposals.propose(any())).thenReturn(java.util.List.of());
        when(store.saveActions(anyLong(), any(), any())).thenReturn(java.util.List.of());
        var orchestrator = mock(com.nexus.ai.service.decision.DeliveryRiskDecisionOrchestrator.class);
        var node = new DecisionActionProposalNodeProvider(support, store, proposals, guard,
            orchestratorProvider(orchestrator), events);
        try (var spring = mockStatic(cn.hutool.extra.spring.SpringUtil.class);
             var login = mockStatic(com.nexus.common.satoken.utils.LoginHelper.class)) {
            spring.when(() -> com.nexus.common.core.utils.SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(com.nexus.common.satoken.utils.LoginHelper::isLogin).thenReturn(false);
            node.execute(ctx);
        }
        var ordered = inOrder(orchestrator, events);
        ordered.verify(orchestrator).completeAnalysis(eq(21L), any(), eq("TEMPLATE_FALLBACK"), any());
        ordered.verify(events).sendReady(7L, 21L, "HIGH", null, 0);
        verify(proposals).propose(argThat(input -> input.rootCauses().containsAll(
            Set.of("SCHEDULE_DELAY", "MATERIAL_SHORTAGE", "PURCHASE_DELAY"))
            && input.inTransitCovers() && input.materialLines().size() == 1
            && "100".equals(input.materialLines().get(0).get("materialId"))
            && "500".equals(input.materialLines().get(0).get("purchaseLineId"))));
    }

    @Test void unlinkedShortageCannotCreateAnyDraft() throws Exception {
        var purchase = new com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.ArrivalFact(
            999L, 500L, java.time.LocalDate.of(2026, 10, 18), java.math.BigDecimal.valueOf(12), 700L);
        verifyShortageProposal(999L, purchase, true);
    }

    @Test void timelyPurchaseCannotBeMisreportedAsDelayedArrival() throws Exception {
        var purchase = new com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.ArrivalFact(
            100L, 500L, java.time.LocalDate.of(2026, 10, 2), java.math.BigDecimal.valueOf(12), 700L);
        verifyShortageProposal(100L, purchase, false);
    }
    @Test void forgedAffectedWorkOrderCannotReuseARealMaterialToCreateDraft() throws Exception {
        var purchase = new com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.ArrivalFact(
            100L, 500L, java.time.LocalDate.of(2026, 10, 18), java.math.BigDecimal.valueOf(12), 700L);
        verifyShortageProposal(100L, purchase, true, 999L);
    }

    private void verifyShortageProposal(long shortageMaterial,
            com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.ArrivalFact arrival,
            boolean shouldFail) throws Exception {
        verifyShortageProposal(shortageMaterial, arrival, shouldFail, 8L);
    }

    private void verifyShortageProposal(long shortageMaterial,
            com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.ArrivalFact arrival,
            boolean shouldFail, long affectedWorkOrderId) throws Exception {
        var support = spy(new DecisionNodeSupport(store, dataPort, mapper));
        var ctx = context("tenant-A", "{\"runId\":\"21\"}");
        ctx.setNodeId("actionProposal"); ctx.setNodeType("industrial.actionProposal");
        var ref = new com.nexus.common.ai.decision.dto.DecisionBusinessRef(
            com.nexus.common.ai.decision.enums.DecisionBusinessType.WORK_ORDER, 8L, "WO-8");
        var narrative = java.util.Map.<String, Object>of("summaryMode", "TEMPLATE_FALLBACK",
            "executiveSummary", "人工检查", "riskExplanation", "原因", "rootCauseExplanations", java.util.List.of(),
            "scenarioTradeoffs", java.util.List.of(), "recommendationRationale", "需审批",
            "evidenceCitations", java.util.List.of(), "uncertaintyNotes", java.util.List.of(),
            "operatorChecklist", java.util.List.of());
        var result = new com.nexus.common.ai.decision.dto.DecisionResult(21L, ref, 60,
            com.nexus.common.ai.decision.enums.DecisionRiskLevel.HIGH, "HIGH",
            java.util.List.of("MATERIAL_SHORTAGE"), null, java.util.List.of(), null,
            narrative, java.util.List.of(), java.util.List.of(), java.util.List.of());
        ctx.getState().putNodeOutput("guard", mapper.valueToTree(result));
        var required = java.time.LocalDate.of(2026, 10, 3);
        var shortage = new com.nexus.common.ai.decision.dto.ShortageSimulationResult(true, 1, required,
            java.math.BigDecimal.TEN, java.util.List.of(new com.nexus.common.ai.decision.dto.ShortageSimulationResult.Item(
                shortageMaterial, "MAT", "material", required, java.math.BigDecimal.TEN,
                java.math.BigDecimal.valueOf(12), java.util.List.of(affectedWorkOrderId), java.util.List.of(),
                "PURCHASE_DELAY", java.util.List.of("ERP:MAT"))), null, java.util.List.of(), java.util.List.of());
        ctx.getState().putNodeOutput("shortage", mapper.valueToTree(shortage));
        doReturn(new DecisionActor("tenant-A", 7L, "trusted", Set.of("ai:decision:run"))).when(support).actor(ctx);
        var snapshot = new com.nexus.common.ai.decision.dto.DeliveryContextSnapshot(ref,
            java.util.List.of(), java.util.List.of(new com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.WorkOrderFact(
                8L, "WO-8", 100L, java.math.BigDecimal.TEN, required, 1L, 9L)),
            java.util.Map.of(), java.util.List.of(new com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.RequirementFact(
                100L, "MAT-100", "material", required, java.math.BigDecimal.TEN, 8L, "WO-8")),
            java.util.List.of(), java.util.List.of(arrival), java.util.Map.of(), java.util.List.of(),
            java.util.Map.of(), java.util.List.of(), "hash", java.time.LocalDateTime.of(2026, 9, 26, 10, 0), java.util.List.of());
        when(store.requireSnapshot(21L, "tenant-A")).thenReturn(snapshot);
        when(store.requireRun(21L, "tenant-A")).thenReturn(new AiDecisionRun());
        when(store.requireHorizonDays(any())).thenReturn(30);
        var proposals = mock(com.nexus.ai.service.decision.DecisionActionProposalService.class);
        var guard = mock(com.nexus.ai.service.decision.DecisionPolicyGuardService.class);
        when(guard.validate(any(), any(), any())).thenReturn(new com.nexus.ai.service.decision.DecisionPolicyGuardService.PolicyResult(
            true, java.util.List.of(), null, false, true));
        when(proposals.propose(any())).thenReturn(java.util.List.of());
        when(store.saveActions(anyLong(), any(), any())).thenReturn(java.util.List.of());
        var node = new DecisionActionProposalNodeProvider(support, store, proposals, guard,
            orchestratorProvider(mock(com.nexus.ai.service.decision.DeliveryRiskDecisionOrchestrator.class)),
            mock(com.nexus.ai.service.sse.AiDecisionSseMessageHelper.class));
        try (var spring = mockStatic(cn.hutool.extra.spring.SpringUtil.class);
             var login = mockStatic(com.nexus.common.satoken.utils.LoginHelper.class)) {
            spring.when(() -> com.nexus.common.core.utils.SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(com.nexus.common.satoken.utils.LoginHelper::isLogin).thenReturn(false);
            if (shouldFail) assertThrows(ServiceException.class, () -> node.execute(ctx));
            else node.execute(ctx);
        }
        if (shouldFail) verifyNoInteractions(proposals);
        else verify(proposals).propose(argThat(input -> !input.inTransitCovers()
            && !input.rootCauses().contains("PURCHASE_DELAY")
            && input.materialLines().size() == 1
            && !input.materialLines().get(0).containsKey("purchaseLineId")));
    }
    @Test void narrativeMetadataDoesNotBreakGuardBeforeActionProposal() throws Exception {
        var support = new DecisionNodeSupport(store, dataPort, mapper);
        var narrative = java.util.Map.of("executiveSummary", "风险评估",
            "riskExplanation", "规则计算", "rootCauseExplanations", java.util.List.of(),
            "scenarioTradeoffs", java.util.List.of(), "recommendationRationale", "人工审核",
            "evidenceCitations", java.util.List.of(), "uncertaintyNotes", java.util.List.of(),
            "operatorChecklist", java.util.List.of(), "summaryMode", "TEMPLATE_FALLBACK");
        var parsed = support.readNarrative(support.write(narrative));
        assertEquals("风险评估", parsed.executiveSummary());
    }

    @Test void revokedPermissionIsRejectedEvenWhenFrozenRunStillHasOldRights() throws Exception {
        var rights = mock(com.nexus.common.core.service.PermissionService.class);
        var support = new DecisionNodeSupport(store, dataPort, mapper, null, rights);
        var ctx = context("tenant-A", "{\"runId\":\"21\"}");
        AiDecisionRun run = new AiDecisionRun();
        run.setId(21L); run.setTenantId("tenant-A"); run.setBizType("WORK_ORDER"); run.setBizId(8L);
        when(store.requireRun(21L, "tenant-A")).thenReturn(run);
        when(store.trustedActor(21L, "tenant-A")).thenReturn(new DecisionActor("tenant-A", 7L,
            "creator", Set.of("ai:decision:run")));
        when(rights.getMenuPermission(7L)).thenReturn(Set.of("other:query"));
        try (var spring = mockStatic(cn.hutool.extra.spring.SpringUtil.class);
             var login = mockStatic(com.nexus.common.satoken.utils.LoginHelper.class)) {
            spring.when(() -> com.nexus.common.core.utils.SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(com.nexus.common.satoken.utils.LoginHelper::isLogin).thenReturn(false);
            assertThrows(ServiceException.class, () -> support.actor(ctx));
        }
        verifyNoInteractions(dataPort);
    }

    @Test void executionWorkerUsesApproverRightsWithoutRequiringAnalysisRunPermission() throws Exception {
        var rights = mock(com.nexus.common.core.service.PermissionService.class);
        var support = new DecisionNodeSupport(store, dataPort, mapper, null, rights);
        var ctx = context("tenant-A", "{\"runId\":\"21\"}");
        ctx.setNodeType("industrial.executeApprovedActions");
        AiDecisionRun run = new AiDecisionRun();
        run.setId(21L); run.setTenantId("tenant-A"); run.setBizType("WORK_ORDER"); run.setBizId(8L);
        when(store.requireRun(21L, "tenant-A")).thenReturn(run);
        Set<String> approvalRights = Set.of("ai:decision:approve", "ai:decision:execute");
        when(store.trustedExecutionActor(21L, "tenant-A"))
            .thenReturn(new DecisionActor("tenant-A", 7L, "approver", approvalRights));
        when(rights.getMenuPermission(7L)).thenReturn(approvalRights);
        when(dataPort.canAccess(any(), any())).thenReturn(true);
        try (var spring = mockStatic(cn.hutool.extra.spring.SpringUtil.class);
             var login = mockStatic(com.nexus.common.satoken.utils.LoginHelper.class)) {
            spring.when(() -> com.nexus.common.core.utils.SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(com.nexus.common.satoken.utils.LoginHelper::isLogin).thenReturn(false);
            assertEquals(approvalRights, support.actor(ctx).permissions());
        }
    }

    @Test void revokedAnalysisPermissionFailsTheOwnedRunRatherThanLeavingItRunning() throws Exception {
        var rights = mock(com.nexus.common.core.service.PermissionService.class);
        var support = new DecisionNodeSupport(store, dataPort, mapper, null, rights);
        var ctx = context("tenant-A", "{\"runId\":\"21\"}");
        ctx.setNodeType("industrial.deliveryContext");
        AiDecisionRun row = new AiDecisionRun();
        row.setId(21L); row.setTenantId("tenant-A"); row.setBizType("WORK_ORDER");
        row.setStatus("RUNNING");
        when(store.requireRun(21L, "tenant-A")).thenReturn(row);
        when(store.trustedActor(21L, "tenant-A"))
            .thenReturn(new DecisionActor("tenant-A", 7L, "worker", Set.of("ai:decision:run")));
        when(rights.getMenuPermission(7L)).thenReturn(Set.of("other:query"));
        try (var spring = mockStatic(cn.hutool.extra.spring.SpringUtil.class);
             var login = mockStatic(com.nexus.common.satoken.utils.LoginHelper.class)) {
            spring.when(() -> com.nexus.common.core.utils.SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(com.nexus.common.satoken.utils.LoginHelper::isLogin).thenReturn(false);
            assertThrows(ServiceException.class, () -> support.output(ctx, () -> java.util.Map.of("status", "unsafe")));
        }
        verify(store).fail(21L, "DECISION_NODE_FAILED", "工业决策节点执行失败");
        verifyNoInteractions(dataPort);
    }

    @Test void revokedExecutionPermissionInvalidatesPendingApprovalWithoutRunningAction() throws Exception {
        var rights = mock(com.nexus.common.core.service.PermissionService.class);
        var support = new DecisionNodeSupport(store, dataPort, mapper, null, rights);
        var ctx = context("tenant-A", "{\"runId\":\"21\"}");
        ctx.setNodeType("industrial.executeApprovedActions");
        AiDecisionRun row = new AiDecisionRun();
        row.setId(21L); row.setTenantId("tenant-A"); row.setBizType("WORK_ORDER");
        row.setStatus("APPROVED");
        when(store.requireRun(21L, "tenant-A")).thenReturn(row);
        when(store.trustedExecutionActor(21L, "tenant-A"))
            .thenReturn(new DecisionActor("tenant-A", 7L, "approver", Set.of("ai:decision:execute")));
        when(rights.getMenuPermission(7L)).thenReturn(Set.of());
        try (var spring = mockStatic(cn.hutool.extra.spring.SpringUtil.class);
             var login = mockStatic(com.nexus.common.satoken.utils.LoginHelper.class)) {
            spring.when(() -> com.nexus.common.core.utils.SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(com.nexus.common.satoken.utils.LoginHelper::isLogin).thenReturn(false);
            assertThrows(ServiceException.class, () -> support.output(ctx, () -> java.util.Map.of("status", "unsafe")));
        }
        verify(store).transition(21L, com.nexus.common.ai.decision.enums.DecisionRunStatus.APPROVED,
            com.nexus.common.ai.decision.enums.DecisionRunStatus.STALE);
        verifyNoInteractions(dataPort);
    }

    @Test void missingTenantAndSpoofedInputCannotResolveActor() throws Exception {
        var support = new DecisionNodeSupport(store, dataPort, mapper);
        var ctx = context("", "{\"runId\":\"21\",\"tenantId\":\"attacker\"}");
        assertThrows(ServiceException.class, () -> support.actor(ctx));
        verifyNoInteractions(store, dataPort);
        ctx.setTenantId("tenant-A");
        var run = new AiDecisionRun();
        run.setId(21L);
        run.setTenantId("tenant-B");
        when(store.requireRun(21L, "tenant-A")).thenReturn(run);
        assertThrows(ServiceException.class, () -> support.actor(ctx));
        verifyNoInteractions(dataPort);
    }

    @Test void industrialNodeSendsStructuredProgressForTheTrustedRun() throws Exception {
        var sse = mock(com.nexus.ai.service.sse.AiDecisionSseMessageHelper.class);
        var support = new DecisionNodeSupport(store, dataPort, mapper, sse);
        var ctx = context("tenant-A", "{\"runId\":\"21\"}");
        ctx.setNodeId("aps"); ctx.setNodeType("industrial.apsCompare"); ctx.setNodeName("排产对比");
        AiDecisionRun row = new AiDecisionRun();
        row.setId(21L); row.setTenantId("tenant-A"); row.setBizType("WORK_ORDER"); row.setBizId(8L);
        when(store.requireRun(21L, "tenant-A")).thenReturn(row);
        when(store.trustedActor(21L, "tenant-A")).thenReturn(new DecisionActor("tenant-A", 7L,
            "trusted", Set.of("ai:decision:run")));
        when(dataPort.canAccess(any(), any())).thenReturn(true);
        try (var spring = mockStatic(cn.hutool.extra.spring.SpringUtil.class);
             var login = mockStatic(com.nexus.common.satoken.utils.LoginHelper.class)) {
            spring.when(() -> com.nexus.common.core.utils.SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(com.nexus.common.satoken.utils.LoginHelper::isLogin).thenReturn(false);
            support.output(ctx, () -> java.util.Map.of("status", "SUCCEEDED"));
        }
        verify(sse).sendNodeStart(eq(7L), eq(21L), anyLong(), eq("aps"), eq("排产对比"));
        verify(sse).sendNodeEnd(eq(7L), eq(21L), anyLong(), eq("aps"), eq("排产对比"), eq("SUCCEEDED"), anyLong(), anyString());
    }

    @Test void workerThreadReestablishesTenantBeforeLoadingTrustedActor() throws Exception {
        var support = new DecisionNodeSupport(store, dataPort, mapper);
        var ctx = context("tenant-A", "{\"runId\":\"21\",\"userId\":\"999\"}");
        AiDecisionRun run = new AiDecisionRun();
        run.setId(21L);
        run.setTenantId("tenant-A");
        run.setBizType("WORK_ORDER");
        run.setBizId(8L);
        when(store.requireRun(21L, "tenant-A")).thenReturn(run);
        when(store.trustedActor(21L, "tenant-A")).thenReturn(new DecisionActor("tenant-A", 7L, "trusted", Set.of("ai:decision:run")));
        when(dataPort.canAccess(any(), any())).thenReturn(true);
        try (var spring = mockStatic(cn.hutool.extra.spring.SpringUtil.class);
             var login = mockStatic(com.nexus.common.satoken.utils.LoginHelper.class)) {
            spring.when(() -> com.nexus.common.core.utils.SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(com.nexus.common.satoken.utils.LoginHelper::isLogin).thenReturn(false);
            var output = support.output(ctx, () -> {
                assertEquals("tenant-A", com.nexus.common.tenant.helper.TenantHelper.getDynamic());
                assertEquals(7L, support.actor(ctx).userId());
                return java.util.Map.of("runId", "21");
            });
            assertEquals("21", output.get("runId").asText());
        }
    }

    @Test void systemGraphHasRegisteredIndustrialProvidersWithNoEditableTenantOrRunId() throws Exception {
        var types = java.util.Map.of(
            "DeliveryContextNodeProvider", "industrial.deliveryContext",
            "BaselineRiskNodeProvider", "industrial.baselineRisk",
            "ShortageSimulationNodeProvider", "industrial.shortageSimulation",
            "ApsScenarioCompareNodeProvider", "industrial.apsCompare",
            "ScenarioRankNodeProvider", "industrial.scenarioRank",
            "DecisionPolicyGuardNodeProvider", "industrial.decisionGuard",
            "DecisionActionProposalNodeProvider", "industrial.actionProposal",
            "DecisionExecuteApprovedActionsNodeProvider", "industrial.executeApprovedActions");
        for (var entry : types.entrySet()) {
            var clazz = Class.forName("com.nexus.flow.node.industrial." + entry.getKey());
            var provider = (com.nexus.flow.node.FlowNodeProvider) org.mockito.Mockito.mock(clazz,
                org.mockito.Mockito.CALLS_REAL_METHODS);
            assertEquals(entry.getValue(), provider.getNodeType());
            var schemas = provider.getParamSchemas();
            assertTrue(schemas.stream().noneMatch(field ->
                "runId".equalsIgnoreCase(field.getName()) || "tenantId".equalsIgnoreCase(field.getName())));
        }
    }

    @Test void ragNodeKeepsStructuredHitProvenanceWithoutNumericIdentifiers() throws Exception {
        var retriever = mock(com.nexus.ai.service.rag.RetrieverService.class);
        var provider = new com.nexus.flow.node.ai.RagNodeProvider(retriever);
        var ctx = context("tenant-A", "{\"runId\":\"21\"}");
        ctx.setNodeName("rag");
        ctx.setConfig(mapper.readTree("{\"knowledgeId\":12,\"queryTemplate\":\"交付风险\"}"));
        var hit = new com.nexus.common.ai.decision.dto.RetrievalHit(9007199254740993L,
            9007199254740994L, 9007199254740995L, "制度", "正文", .8,
            null, .9, null, "v1", null, "RAG_DOCUMENT");
        when(retriever.retrieveHits(eq(12L), anyString(), isNull())).thenReturn(java.util.List.of(hit));
        var json = provider.execute(ctx);
        assertEquals("9007199254740995", json.path("hits").get(0).path("chunkId").asText());
        assertEquals("制度", json.path("hits").get(0).path("documentName").asText());
        assertEquals("正文", json.path("chunks").get(0).asText());
    }

    private FlowExecutionContext context(String tenant, String input) throws Exception {
        var ctx = new FlowExecutionContext();
        ctx.setTenantId(tenant);
        ctx.setState(new FlowState(mapper.readTree(input)));
        return ctx;
    }
}
