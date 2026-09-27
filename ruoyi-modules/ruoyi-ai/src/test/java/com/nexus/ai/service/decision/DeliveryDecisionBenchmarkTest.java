package com.nexus.ai.service.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.ai.service.decision.executor.DecisionActionExecutor;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.*;
import com.nexus.common.ai.decision.port.DecisionWorkflowPort;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.ai.domain.entity.AiDecisionAction;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static com.nexus.ai.service.decision.DecisionTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 实测服务级检查，不宣称验证外部模型、数据库或 HTTP 夹具。
 */
class DeliveryDecisionBenchmarkTest {
    private final AiProperties config = new AiProperties();
    private final DeliveryRiskScoringService scoring = new DeliveryRiskScoringService(config);

    static Stream<Arguments> riskExamples() {
        return Stream.of(
            Arguments.of("DELIVERY-001", 0, Integer.MAX_VALUE, 65.0, 98.0, true,
                DecisionRiskLevel.LOW, "HIGH", List.of()),
            Arguments.of("DELIVERY-006", 3, 5, 70.0, 60.0, true,
                DecisionRiskLevel.HIGH, "HIGH", List.of("MATERIAL_SHORTAGE", "SUPPLIER_DELAY")),
            Arguments.of("DELIVERY-020", 3, Integer.MAX_VALUE, 110.0, 98.0, true,
                DecisionRiskLevel.HIGH, "HIGH", List.of("CAPACITY_BOTTLENECK")),
            Arguments.of("DELIVERY-026", 0, Integer.MAX_VALUE, 70.0, 98.0, false,
                null, "LOW", List.of("SCHEDULE_EVIDENCE_INSUFFICIENT")),
            Arguments.of("DELIVERY-033", 6, 5, 110.0, 80.0, true,
                DecisionRiskLevel.CRITICAL, "HIGH", List.of("MATERIAL_SHORTAGE", "CAPACITY_BOTTLENECK"))
        );
    }

    @ParameterizedTest(name = "{0} deterministic risk and provenance gate")
    @MethodSource("riskExamples")
    void deterministicRiskCases(String caseId, int tardiness, Integer firstShortage, Double utilization,
                                Double supplierRate, boolean routesComplete, DecisionRiskLevel expectedLevel,
                                String confidence, List<String> requiredCauses) {
        var result = scoring.score(new DeliveryRiskScoringService.RiskInput(tardiness, firstShortage,
            utilization, supplierRate, 5, true, routesComplete, true, true, true, true));
        assertEquals(expectedLevel, result.level(), caseId);
        assertEquals(confidence, result.confidence(), caseId);
        assertTrue(result.rootCauseCodes().containsAll(requiredCauses), caseId);
        if (!routesComplete) assertNull(result.score(), "Missing routing must not publish a false LOW risk");
    }

    @Test void scenarioRankingPrefersLowerTardinessWithoutAnExternalModel() {
        var ranking = new DecisionScenarioRankingService(config);
        var baseline = scenario("BASELINE_CURRENT", true, kpis(3, 8, 0), 0);
        var improved = scenario("APS_EDD", true, kpis(1, 2, 1), 0);
        assertEquals("APS_EDD", ranking.rank(List.of(baseline, improved)).get(0).scenarioCode());
    }

    @Test void control001StaleApprovalCannotStartExecution() {
        var store = mock(DecisionPersistenceService.class);
        var data = mock(IndustrialDecisionDataPort.class);
        var workflow = mock(DecisionWorkflowPort.class);
        var service = new DecisionApprovalService(store, data, workflow);
        AiDecisionRun waiting = waitingRun("hash-old");
        waiting.setBizType("WORK_ORDER"); waiting.setBizId(9001L); waiting.setVersion(0);
        when(store.requireRun(10L, "000000")).thenReturn(waiting);
        when(data.canAccess(any(), any())).thenReturn(true);
        when(data.checkFreshness(any(), eq("hash-old"), any()))
            .thenReturn(new DecisionFreshnessResult(false, "hash-new", List.of("stock")));
        assertThrows(ServiceException.class, () -> service.approve(10L, approvalRequest(), actor()));
        verify(store).transition(10L, DecisionRunStatus.WAITING_APPROVAL, DecisionRunStatus.STALE);
        verifyNoInteractions(workflow);
    }

    @Test void control002UnauthorizedBusinessWriteIsBlockedByTheRealGuard() {
        var verdict = new DecisionPolicyGuardService().validateActions(
            List.of(Map.of("actionType", "PUBLISH_SCHEDULE_PLAN", "approvalRequired", false)));
        assertFalse(verdict.passed());
        assertTrue(verdict.violations().stream().anyMatch(issue -> "ACTION_NOT_ALLOWED".equals(issue.code())));
    }

    @Test void control003UnavailableModelUsesTemplateFallbackWithoutLeakingCredentials() {
        ChatModelFactory models = mock(ChatModelFactory.class);
        when(models.getChatModel(1L)).thenThrow(new IllegalStateException("sk-secret-provider-failed"));
        var summary = new DecisionSummaryService(models).summarize(new DecisionSummaryService.SummaryInput(
            10L, new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 9001L, "WO-9001"),
            DecisionRiskLevel.HIGH, 55, List.of("MATERIAL_SHORTAGE"),
            List.of(scenario("APS_EDD", true, kpis(1, 2, 1), .1)), "APS_EDD",
            List.of(), List.of(), 1L, null));
        assertEquals("TEMPLATE_FALLBACK", summary.mode());
        assertFalse(summary.executiveSummary().contains("sk-secret"));
    }

    @Test void control004DuplicateApprovalCannotDispatchAnExecutionFlow() {
        var store = mock(DecisionPersistenceService.class);
        var workflow = mock(DecisionWorkflowPort.class);
        var service = new DecisionApprovalService(store, mock(IndustrialDecisionDataPort.class), workflow);
        AiDecisionRun alreadyApproved = waitingRun("hash-old");
        alreadyApproved.setStatus("APPROVED"); alreadyApproved.setVersion(1);
        when(store.requireRun(10L, "000000")).thenReturn(alreadyApproved);
        assertThrows(ServiceException.class, () -> service.approve(10L, approvalRequest(), actor()));
        verifyNoInteractions(workflow);
        verify(store, never()).setActionApproval(anyLong(), any(), any(), any(), any());
    }

    @Test void control005OneSuccessfulAndOneFailedActionFinishesPartialSuccess() {
        var store = mock(DecisionPersistenceService.class);
        DecisionActionExecutor scheduleExecutor = mock(DecisionActionExecutor.class);
        DecisionActionExecutor noticeExecutor = mock(DecisionActionExecutor.class);
        when(scheduleExecutor.type()).thenReturn(DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT);
        when(noticeExecutor.type()).thenReturn(DecisionActionType.SEND_MANAGER_NOTIFICATION);
        var service = new DecisionActionExecutionService(store, List.of(scheduleExecutor, noticeExecutor),
            new ObjectMapper());
        when(store.requireRun(10L, "000000")).thenReturn(run(10L, DecisionRunStatus.APPROVED));
        when(store.listActions(10L)).thenReturn(List.of(
            approvedAction(20L, 1, DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT),
            approvedAction(21L, 2, DecisionActionType.SEND_MANAGER_NOTIFICATION)));
        var success = new DecisionActionExecutionResult(true, false, "PRD_SCHEDULE_PLAN", 600L,
            "DEMO-PLAN", Map.of("status", "DRAFT"), null, null);
        var failure = new DecisionActionExecutionResult(false, false, null, null, null,
            Map.of(), "NOTICE_FAILED", "站内通知失败");
        when(scheduleExecutor.execute(any(), any())).thenReturn(success);
        when(scheduleExecutor.verify(any(), eq(success), any())).thenReturn(success);
        when(noticeExecutor.execute(any(), any())).thenReturn(failure);
        service.executeApproved(10L, actor());
        verify(store).transition(10L, DecisionRunStatus.EXECUTING, DecisionRunStatus.PARTIAL_SUCCESS);
        verify(store).saveActionResult(20L, success, 1);
        verify(store).saveActionResult(21L, failure, 1);
    }

    private static AiDecisionAction approvedAction(long id, int sequence, DecisionActionType type) {
        var action = new AiDecisionAction();
        action.setId(id); action.setRunId(10L); action.setActionType(type.name());
        action.setApprovalStatus("APPROVED"); action.setExecutionStatus("NOT_STARTED");
        action.setTargetBizType("WORK_ORDER"); action.setTargetBizId(9001L);
        action.setPermissionCode(type.permissionCode()); action.setExecutorCode(type.executorCode());
        action.setRiskLevel("HIGH"); action.setApprovalRequired("Y");
        action.setPayloadJson("{\"runId\":\"10\"}"); action.setDependencyIdsJson("[]");
        action.setSequenceNo(sequence);
        return action;
    }

    static Stream<Arguments> allCases() throws Exception {
        Path root = fixtureRoot();
        ObjectMapper mapper = new ObjectMapper();
        return Files.readAllLines(root.resolve("doc/ai/benchmark/delivery-risk/cases.jsonl")).stream().map(line -> {
            try {
                var fixture = mapper.readTree(line);
                return Arguments.of(fixture.path("caseId").asText(), fixture);
            } catch (Exception invalid) { throw new IllegalArgumentException("Benchmark JSONL is invalid", invalid); }
        });
    }

    @ParameterizedTest(name = "{0} golden decision contract")
    @MethodSource("allCases")
    void eachCaseUsesMockedScopedFactsAndRealScoringRankingGuardAndProposals(
        String caseId, com.fasterxml.jackson.databind.JsonNode fixture) {
        var facts = fixture.path("facts");
        String tenant = "000000";
        long businessId = Long.parseLong(fixture.path("bizId").asText());
        var ref = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER,
            businessId, fixture.path("bizCode").asText());
        IndustrialDecisionDataPort data = mock(IndustrialDecisionDataPort.class);
        var supplier = new DeliveryContextSnapshot.SupplierMetricFact(700L,
            BigDecimal.valueOf(facts.path("supplierOnTimeRate").asDouble()),
            facts.path("supplierSampleCount").asInt(), java.time.LocalDateTime.of(2026, 9, 26, 10, 0));
        var snapshot = new DeliveryContextSnapshot(ref, List.of(), List.of(), Map.of(), List.of(),
            List.of(), List.of(), Map.of(), List.of(), Map.of(), List.of(supplier),
            "fixture-" + caseId, java.time.LocalDateTime.of(2026, 9, 26, 10, 0), List.of());
        when(data.loadDeliveryContext(eq(ref), eq(30), eq(actor()))).thenReturn(snapshot);
        DeliveryContextSnapshot scoped = data.loadDeliveryContext(ref, 30, actor());
        assertEquals(ref, scoped.rootBusinessRef(), caseId);
        var observed = scoped.supplierMetrics().get(0);
        var risk = scoring.score(new DeliveryRiskScoringService.RiskInput(
            facts.path("tardinessDays").asInt(), facts.path("daysUntilFirstShortage").asInt(),
            facts.path("maxUtilization").asDouble(), observed.onTimeRate().doubleValue(),
            observed.sampleCount(), facts.path("currentSchedulePresent").asBoolean(),
            facts.path("routesComplete").asBoolean(), facts.path("stockComplete").asBoolean(),
            facts.path("mrpComplete").asBoolean(), facts.path("purchaseEvidenceComplete").asBoolean(),
            facts.path("capacityComplete").asBoolean()));
        var expectedRisk = fixture.path("expectedRiskLevel");
        assertEquals(expectedRisk.isNull() ? null : DecisionRiskLevel.valueOf(expectedRisk.asText()), risk.level(), caseId);
        assertEquals(fixture.path("expectedConfidence").asText(), risk.confidence(), caseId);
        var expectedCauses = new java.util.ArrayList<String>();
        fixture.path("expectedRootCauses").forEach(code -> expectedCauses.add(code.asText()));
        assertEquals(expectedCauses, risk.rootCauseCodes(), caseId);
        if (risk.level() == null) {
            assertEquals("FAILED", fixture.path("expectedStatus").asText());
            return; // 数据缺口不得产生低风险推荐或可执行动作。
        }
        int tardiness = facts.path("tardinessDays").asInt();
        int shortage = facts.path("shortageQty").asInt();
        int utilization = facts.path("maxUtilization").asInt();
        String recommendedCode = fixture.path("recommendedScenarioCode").isNull() ? null
            : fixture.path("recommendedScenarioCode").asText();
        // 候选仿真仅依赖输入事实，不依赖黄金推荐。
        var ranked = new DecisionScenarioRankingService(config).rank(simulatedCandidates(facts, caseId));
        var selected = ranked.stream().filter(DecisionScenario::feasible).findFirst()
            .map(DecisionScenario::scenarioCode).orElse(null);
        assertEquals(recommendedCode, selected, caseId);
        var causesForActions = new HashSet<>(risk.rootCauseCodes());
        if (causesForActions.contains("DELIVERY_DELAY")) causesForActions.add("SCHEDULE_DELAY");
        boolean delayedArrival = facts.path("purchaseArrivalDelayDays").asInt() > 0;
        if (delayedArrival) causesForActions.add("PURCHASE_DELAY");
        var lines = shortage == 0 ? List.<Map<String, Object>>of()
            : List.<Map<String, Object>>of(new java.util.LinkedHashMap<>(Map.of(
                "materialId", String.valueOf(businessId + 1000), "gap", String.valueOf(shortage),
                "requiredDate", "2026-10-03")));
        if (delayedArrival && !lines.isEmpty()) lines.get(0).put("purchaseLineId", String.valueOf(businessId + 2000));
        List<DecisionActionProposal> actions = new DecisionActionProposalService().propose(
            new DecisionActionProposalService.ProposalInput(10L, tenant, ref, risk.level(), causesForActions,
                recommendedCode, delayedArrival, lines, null, List.of(String.valueOf(businessId)),
                java.time.LocalDate.of(2026, 9, 26), 30, null));
        var actualTypes = actions.stream().map(action -> action.actionType().name()).toList();
        var required = new java.util.ArrayList<String>();
        fixture.path("requiredActions").forEach(type -> required.add(type.asText()));
        assertEquals(required, actualTypes, caseId);
        fixture.path("forbiddenActions").forEach(type -> assertFalse(actualTypes.contains(type.asText()), caseId));
        var result = new DecisionResult(10L, ref, risk.score(), risk.level(), risk.confidence(),
            risk.rootCauseCodes(), ranked.stream().filter(item -> "BASELINE_CURRENT".equals(item.scenarioCode()))
                .findFirst().orElseThrow().kpis(), ranked, recommendedCode,
            Map.of(), List.of(), actions, List.of());
        var narrative = new DecisionPolicyGuardService.DecisionNarrative("建议人工复核", "依据只读事实计算",
            List.of(), List.of(), "请人工审批", List.of(), List.of(), List.of());
        var verdict = new DecisionPolicyGuardService().validate(result, narrative, actions);
        assertTrue(verdict.passed(), caseId + " must not bypass action/narrative guard: " + verdict.violations());
    }

    private static Path fixtureRoot() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("doc/ai/benchmark/delivery-risk/cases.jsonl"))) {
            root = root.getParent();
        }
        assertNotNull(root, "Missing deterministic Benchmark cases.jsonl");
        return root;
    }

    @Test void isolatedSeedAndResetSqlCoverAllFortyCasesWithoutTouchingPlusData() throws Exception {
        Path root = fixtureRoot().resolve("doc/ai/benchmark/delivery-risk/seed");
        String master = Files.readString(root.resolve("demo-master-data.sql"));
        String business = Files.readString(root.resolve("demo-business-data.sql"));
        String reset = Files.readString(root.resolve("reset-demo-data.sql"));
        for (int number = 1; number <= 40; number++) {
            String code = "DEMO-AI-DECISION-%03d".formatted(number);
            assertTrue(master.contains(code), "Missing master data: " + code);
            assertTrue(business.contains(code), "Missing business data: " + code);
        }
        for (String sql : List.of(master, business, reset)) {
            assertTrue(sql.contains("DATABASE() = 'nexus_ai_decision_demo_erp'"));
            assertFalse(sql.toUpperCase().contains("TRUNCATE "));
            assertFalse(sql.toUpperCase().contains("DROP DATABASE"));
        }
        assertTrue(reset.contains("`wo_no` LIKE 'DEMO-AI-DECISION-%'"));
        assertFalse(reset.contains("DELETE FROM prd_work_order;"));
    }

    @Test void allFortyFixtureIdsAndCodesMustBeUniqueAndVerifiable() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("doc/ai/benchmark/delivery-risk/cases.jsonl"))) {
            root = root.getParent();
        }
        assertNotNull(root, "Missing deterministic Benchmark cases.jsonl");
        var lines = Files.readAllLines(root.resolve("doc/ai/benchmark/delivery-risk/cases.jsonl"));
        assertEquals(40, lines.size(), "Exactly 40 delivery cases, excluding CONTROL-* cases");
        Set<String> ids = new HashSet<>();
        Set<String> codes = new HashSet<>();
        ObjectMapper mapper = new ObjectMapper();
        for (String line : lines) {
            var sample = mapper.readTree(line);
            assertTrue(sample.path("caseId").asText().matches("DELIVERY-[0-9]{3}"));
            assertTrue(sample.path("bizId").isTextual());
            assertTrue(sample.path("bizId").asText().matches("[1-9][0-9]{0,18}"));
            assertTrue(ids.add(sample.path("bizId").asText()));
            assertTrue(codes.add(sample.path("bizCode").asText()));
            assertTrue(sample.path("bizCode").asText().startsWith("DEMO-AI-DECISION-"));
            assertTrue(sample.path("horizonDays").asInt() >= 1);
            assertTrue(sample.hasNonNull("expectedRiskLevel") || sample.path("expectedConfidence").asText().equals("LOW"));
            if (sample.path("expectedRiskLevel").isNull()) {
                assertTrue(sample.path("expectedFailurePattern").isTextual()
                    && !sample.path("expectedFailurePattern").asText().isBlank(),
                    "Negative case must have an independently checkable failure reason");
            }
            assertTrue(sample.path("expectedRootCauses").isArray());
            assertTrue(sample.path("requiredActions").isArray());
            assertTrue(sample.path("forbiddenActions").isArray());
        }
        assertEquals(40, ids.size());
    }
    private static List<DecisionScenario> simulatedCandidates(com.fasterxml.jackson.databind.JsonNode facts, String caseId) {
        int delay = facts.path("tardinessDays").asInt();
        int gap = facts.path("shortageQty").asInt();
        int load = facts.path("maxUtilization").asInt();
        boolean coveredArrival = facts.path("purchaseArrivalDelayDays").asInt() > 0;
        var candidates = new java.util.ArrayList<DecisionScenario>();
        for (String code : List.of("BASELINE_CURRENT", "APS_EDD", "APS_CR", "APS_CR_EXPEDITE")) {
            boolean baseline = "BASELINE_CURRENT".equals(code);
            boolean expedite = "APS_CR_EXPEDITE".equals(code);
            int candidateDelay = baseline ? delay : 0;
            int candidateGap = expedite && coveredArrival ? 0 : gap;
            int candidateLoad = !baseline && load > 90 ? 75 : load;
            var kpi = new DecisionKpiSet(1, candidateDelay > 0 ? 1 : 0, candidateDelay, candidateDelay,
                null, candidateGap > 0 ? 1 : 0, BigDecimal.valueOf(candidateGap), null,
                BigDecimal.valueOf(candidateLoad), candidateLoad > 100 ? 1 : 0,
                baseline ? 0 : 1, expedite ? BigDecimal.ONE : BigDecimal.ZERO);
            boolean feasible = candidateGap == 0 && candidateLoad <= 100;
            candidates.add(new DecisionScenario(code, code, "service-fixture", feasible,
                feasible ? List.of() : List.of("MATERIAL_OR_CAPACITY_CONSTRAINT"), kpi, BigDecimal.ZERO, 0,
                Map.of(), List.of(), List.of(), "fixture-" + caseId, List.of(), List.of()));
        }
        return List.copyOf(candidates);
    }

}
