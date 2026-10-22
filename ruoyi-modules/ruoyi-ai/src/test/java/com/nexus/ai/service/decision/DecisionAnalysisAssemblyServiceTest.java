package com.nexus.ai.service.decision;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.enums.DecisionRiskLevel;
import com.nexus.common.ai.decision.exception.DecisionEvidenceException;
import com.nexus.common.ai.decision.util.SavedScheduleCapacity;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.core.exception.ServiceException;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class DecisionAnalysisAssemblyServiceTest {
    private final DecisionAnalysisAssemblyService service =
            new DecisionAnalysisAssemblyService(
                    new DeliveryRiskScoringService(new AiProperties()),
                    new DecisionEvidenceService(),
                    new DecisionSourceFactVerifier(),
                    new DecisionSummaryService(mock(ChatModelFactory.class)),
                    new DecisionPolicyGuardService());

    @Test void deterministicBaselineAndKpisAreRootedInRereadSourceFacts() {
        var ref = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 2L, "WO-2");
        var at = LocalDateTime.of(2026, 9, 26, 9, 0);
        var snapshot = new DeliveryContextSnapshot(ref, List.of(),
            List.of(new DeliveryContextSnapshot.WorkOrderFact(2L, "WO-2", 3L,
                BigDecimal.TEN, LocalDate.of(2026, 9, 28), 1L, 1L)),
            Map.of(3L, List.of(new DeliveryContextSnapshot.RoutingStepFact(4L, 5L, 1, 1L, 1L, BigDecimal.ONE))),
            List.of(new DeliveryContextSnapshot.RequirementFact(301L, 3L, "M-3", "物料", LocalDate.of(2026, 9, 28),
                BigDecimal.ONE, 2L, "WO-2", 1L)),
            List.of(new DeliveryContextSnapshot.StockFact(3L, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO)),
            List.of(), Map.of(LocalDate.of(2026, 9, 27), Map.of("WORKSTATION:1", BigDecimal.valueOf(8))),
            List.of(), savedSchedule("5.6", "5.6", "8"),
            List.of(new DeliveryContextSnapshot.SupplierMetricFact(7L, BigDecimal.valueOf(98), 10, at)),
            "hash", at, List.of());
        AiDecisionRun run = new AiDecisionRun();
        run.setId(5L); run.setBizType("WORK_ORDER"); run.setBizId(2L);
        run.setScoreConfigJson("{\"horizonDays\":30}");
        var basis = new DecisionShortageBasis("5", "snapshot:hash", at.toLocalDate(), at.toLocalDate().plusDays(29),
            List.of(new DecisionShortageBasis.Requirement(301L, 3L, LocalDate.of(2026, 9, 28), BigDecimal.ONE, 1L)),
            List.of(new DecisionShortageBasis.Gap(3L, 1L, BigDecimal.ZERO, null,
                "DERIVED:SHORTAGE:BASELINE_CURRENT_3:max_gap")), "DERIVED:SHORTAGE:BASELINE_CURRENT:complete");
        var k = new DecisionKpiSet(0, 0, 0, 0, "2026-09-28", 0, BigDecimal.ZERO, null,
            BigDecimal.valueOf(70), 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, basis);
        var scenario = new DecisionScenario("BASELINE_CURRENT", "基线", "BASELINE_CURRENT", true, List.of(),
            k, BigDecimal.ZERO, 1, Map.of(), List.of("snapshot:hash"), List.of(), "scenario-specific-fingerprint", List.of(), List.of());
        var shortage = new ShortageSimulationResult(false, 0, null, BigDecimal.ZERO, List.of(),
            k, List.of(), List.of());
        var observed = SavedScheduleCapacity.measure(snapshot.currentSchedule());
        assertNotNull(observed);
        assertEquals(new BigDecimal("70.0"), observed.maxUtilization());
        assertEquals(0, observed.overloadedResources());
        var result = assertDoesNotThrow(() -> service.prepare(run, snapshot, shortage, List.of(scenario), List.of(), null));
        var ratioEvidence = result.evidence().stream().filter(e -> "shortage_gap_ratio".equals(e.metricCode()))
            .findFirst().orElseThrow();
        assertEquals("ratio", ratioEvidence.unit());
        assertEquals("5", result.baselineKpis().shortageBasis().runId());
        assertSame(basis, scenario.kpis().shortageBasis(), "Flow 已绑定的来源依据不能在装配阶段被改写");
        assertTrue(ratioEvidence.derivedFromEvidenceKeys().contains("ERP:REQUIREMENT:301:required_qty"));
        assertTrue(ratioEvidence.derivedFromEvidenceKeys().contains("ERP:REQUIREMENT:301:base_uom_id"));
        assertTrue(ratioEvidence.derivedFromEvidenceKeys().contains("DERIVED:SHORTAGE:BASELINE_CURRENT_3:max_gap"));
        assertEquals(0, k.maxResourceUtilization().compareTo(observed.maxUtilization()));
        assertEquals(0d, result.riskScore());
        assertTrue(result.evidence().stream().anyMatch(e -> e.evidenceKey().equals("ERP:STOCK:3:stock_qty")
            && e.dataVersion().equals("hash")));
        assertTrue(result.evidence().stream().anyMatch(e -> e.evidenceKey().equals("DERIVED:CALC:RISK:risk_score")
            && e.derivedFromEvidenceKeys().contains("ERP:STOCK:3:stock_qty")));
    }

    @Test void unsafeNumericNarrationFallsBackWithoutChangingTrustedRiskOrEvidence() {
        var ref = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 2L, "WO-2");
        var at = LocalDateTime.of(2026, 9, 26, 9, 0);
        var snapshot = new DeliveryContextSnapshot(ref, List.of(),
            List.of(new DeliveryContextSnapshot.WorkOrderFact(2L, "WO-2", 3L,
                BigDecimal.TEN, LocalDate.of(2026, 9, 28), 1L, 1L)),
            Map.of(3L, List.of(new DeliveryContextSnapshot.RoutingStepFact(4L, 5L, 1, 1L, 1L, BigDecimal.ONE))),
            List.of(new DeliveryContextSnapshot.RequirementFact(3L, "M-3", "物料", LocalDate.of(2026, 9, 28),
                BigDecimal.ONE, 2L, "WO-2")),
            List.of(new DeliveryContextSnapshot.StockFact(3L, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO)),
            List.of(), Map.of(LocalDate.of(2026, 9, 27), Map.of("WORKSTATION:1", BigDecimal.valueOf(8))),
            List.of(), savedSchedule("5.6", "5.6", "8"),
            List.of(new DeliveryContextSnapshot.SupplierMetricFact(7L, BigDecimal.valueOf(98), 10, at)),
            "hash", at, List.of());
        AiDecisionRun run = new AiDecisionRun();
        run.setId(5L); run.setBizType("WORK_ORDER"); run.setBizId(2L);
        var k = new DecisionKpiSet(0, 0, 0, 0, "2026-09-28", 0, BigDecimal.ZERO, null,
            BigDecimal.valueOf(70), 0, 0, BigDecimal.ZERO);
        var scenario = new DecisionScenario("BASELINE_CURRENT", "基线", "BASELINE_CURRENT", true, List.of(),
            k, BigDecimal.ZERO, 1, Map.of(), List.of("snapshot:hash"), List.of(), "scenario-specific-fingerprint", List.of(), List.of());
        var shortage = new ShortageSimulationResult(false, 0, null, BigDecimal.ZERO, List.of(),
            k, List.of(), List.of());
        var raw = """
            {"executiveSummary":"风险数值 55 ，请核对","riskExplanation":"核验业务证据","rootCauseExplanations":[],"scenarioTradeoffs":[],"recommendationRationale":"人工审核","evidenceCitations":[],"uncertaintyNotes":[],"operatorChecklist":[]}
            """;
        var deterministic = service.prepare(run, snapshot, shortage, List.of(scenario), List.of(), null);
        var result = assertDoesNotThrow(() -> service.prepare(run, snapshot, shortage, List.of(scenario), List.of(), raw));
        assertTrustedFactsEqual(deterministic, result);
        assertEquals(0d, result.riskScore());
        assertEquals("TEMPLATE_FALLBACK", result.narrative().get("summaryMode"));
        assertTrue(result.warnings().stream().anyMatch(w -> w.startsWith("SUMMARY_GUARD_REJECTED:")));
        assertTrue(result.evidence().stream().anyMatch(e -> e.evidenceKey().equals("ERP:STOCK:3:stock_qty")));
        assertTrue(result.actions().isEmpty());
    }

    @Test void unknownModelRootCauseFallsBackWithoutAuthorizingNewBusinessFacts() {
        var ref = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 2L, "WO-2");
        var at = LocalDateTime.of(2026, 9, 26, 9, 0);
        var snapshot = new DeliveryContextSnapshot(ref, List.of(),
            List.of(new DeliveryContextSnapshot.WorkOrderFact(2L, "WO-2", 3L,
                BigDecimal.TEN, LocalDate.of(2026, 9, 28), 1L, 1L)),
            Map.of(3L, List.of(new DeliveryContextSnapshot.RoutingStepFact(4L, 5L, 1, 1L, 1L, BigDecimal.ONE))),
            List.of(new DeliveryContextSnapshot.RequirementFact(3L, "M-3", "物料", LocalDate.of(2026, 9, 28),
                BigDecimal.ONE, 2L, "WO-2")),
            List.of(new DeliveryContextSnapshot.StockFact(3L, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO)),
            List.of(), Map.of(LocalDate.of(2026, 9, 27), Map.of("WORKSTATION:1", BigDecimal.valueOf(8))),
            List.of(), savedSchedule("5.6", "5.6", "8"),
            List.of(new DeliveryContextSnapshot.SupplierMetricFact(7L, BigDecimal.valueOf(98), 10, at)),
            "hash", at, List.of());
        AiDecisionRun run = new AiDecisionRun();
        run.setId(5L); run.setBizType("WORK_ORDER"); run.setBizId(2L);
        var k = new DecisionKpiSet(0, 0, 0, 0, "2026-09-28", 0, BigDecimal.ZERO, null,
            BigDecimal.valueOf(70), 0, 0, BigDecimal.ZERO);
        var scenario = new DecisionScenario("BASELINE_CURRENT", "基线", "BASELINE_CURRENT", true, List.of(),
            k, BigDecimal.ZERO, 1, Map.of(), List.of("snapshot:hash"), List.of(), "scenario-specific-fingerprint", List.of(), List.of());
        var shortage = new ShortageSimulationResult(false, 0, null, BigDecimal.ZERO, List.of(),
            k, List.of(), List.of());
        var raw = """
            {"executiveSummary":"请核对业务","riskExplanation":"核验业务证据","rootCauseExplanations":[{"code":"MODEL_ONLY_CAUSE","explanation":"需要复核"}],"scenarioTradeoffs":[],"recommendationRationale":"人工审核","evidenceCitations":[],"uncertaintyNotes":[],"operatorChecklist":[]}
            """;
        var deterministic = service.prepare(run, snapshot, shortage, List.of(scenario), List.of(), null);
        var result = assertDoesNotThrow(() -> service.prepare(run, snapshot, shortage, List.of(scenario), List.of(), raw));
        assertTrustedFactsEqual(deterministic, result);
        assertEquals(0d, result.riskScore());
        assertEquals("TEMPLATE_FALLBACK", result.narrative().get("summaryMode"));
        assertTrue(result.warnings().stream().anyMatch(w -> w.startsWith("SUMMARY_GUARD_REJECTED:")));
        assertTrue(result.evidence().stream().anyMatch(e -> e.evidenceKey().equals("ERP:STOCK:3:stock_qty")));
        assertTrue(result.actions().isEmpty());
    }

    @Test void forgedSavedBaselineCapacityCannotBeMadeTrustedByAScenarioResult() {
        var ref = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 2L, "WO-2");
        var at = LocalDateTime.of(2026, 9, 26, 9, 0);
        var saved = savedSchedule("12", "12", "8");
        var snapshot = new DeliveryContextSnapshot(ref, List.of(),
            List.of(new DeliveryContextSnapshot.WorkOrderFact(2L, "WO-2", 3L,
                BigDecimal.TEN, LocalDate.of(2026, 9, 28), 1L, 1L)),
            Map.of(3L, List.of(new DeliveryContextSnapshot.RoutingStepFact(4L, 5L, 1, 1L, 1L, BigDecimal.ONE))),
            List.of(new DeliveryContextSnapshot.RequirementFact(3L, "M-3", "material",
                LocalDate.of(2026, 9, 28), BigDecimal.ONE, 2L, "WO-2")),
            List.of(new DeliveryContextSnapshot.StockFact(3L, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO)),
            List.of(), Map.of(LocalDate.of(2026, 9, 27), Map.of("WORKSTATION:1", BigDecimal.valueOf(8))),
            List.of(), saved,
            List.of(new DeliveryContextSnapshot.SupplierMetricFact(7L, BigDecimal.valueOf(98), 10, at)),
            "hash", at, List.of());
        AiDecisionRun run = new AiDecisionRun();
        run.setId(5L); run.setBizType("WORK_ORDER"); run.setBizId(2L);
        var forged = new DecisionKpiSet(0, 0, 0, 0, "2026-09-28", 0, BigDecimal.ZERO, null,
            BigDecimal.ZERO, 0, 0, BigDecimal.ZERO);
        var scenario = new DecisionScenario("BASELINE_CURRENT", "baseline", "EDD", true, List.of(),
            forged, BigDecimal.ZERO, 1, Map.of(), List.of("snapshot:hash"), List.of(), "scenario-hash", List.of(), List.of());
        var shortage = new ShortageSimulationResult(false, 0, null, BigDecimal.ZERO, List.of(),
            forged, List.of(), List.of());
        var observed = SavedScheduleCapacity.measure(snapshot.currentSchedule());
        assertNotNull(observed, "The source must be sound; only the scenario's zero KPI is forged");
        assertEquals(new BigDecimal("150.0"), observed.maxUtilization());
        var rejected = assertThrows(ServiceException.class, () -> service.prepare(run, snapshot, shortage,
            List.of(scenario), List.of(), null));
        assertEquals("已保存排程利用率缺少可信产能日历，或与 ERP 源事实不一致", rejected.getMessage());
    }
    @Test void unverifiableSavedScheduleCannotPublishForgedZeroUtilization() {
        var input = evidenceFixture();
        var source = input.snapshot();
        var zeroKpis = new DecisionKpiSet(0, 0, 0, 0, "2026-09-28", 0, BigDecimal.ZERO, null,
            BigDecimal.ZERO, 0, 0, BigDecimal.ZERO);
        var forged = new DecisionScenario("BASELINE_CURRENT", "基线", "BASELINE_CURRENT", true, List.of(),
            zeroKpis, BigDecimal.ZERO, 1, Map.of(), List.of("snapshot:hash"), List.of(),
            "scenario-specific-fingerprint", List.of(), List.of());
        var summaries = mock(DecisionSummaryService.class);
        var policy = mock(DecisionPolicyGuardService.class);
        var checked = new DecisionAnalysisAssemblyService(new DeliveryRiskScoringService(new AiProperties()),
            new DecisionEvidenceService(), new DecisionSourceFactVerifier(), summaries, policy);
        var saved = source.currentSchedule();
        var invalidCases = Map.of(
            "legacy version zero", withScheduleField(saved, "capacityCalcVersion", 0),
            "unpublished plan", withScheduleField(saved, "status", "DRAFT"),
            "cross-tenant plan", withScheduleField(saved, "tenantId", "100001"),
            "missing own bookings", withScheduleField(saved, "capacityBookings", List.of()),
            "cross-tenant booking", withScheduleRowField(saved, "capacityBookings", "tenantId", "100001"),
            "unreconciled all-plan ledger", withScheduleRowField(saved, "capacityCalendars", "ledgerHours", "0"),
            "no load at first task day", withScheduleRowField(saved, "tasks", "startTime", "2026-09-26"));
        for (var entry : invalidCases.entrySet()) {
            var invalid = entry.getValue();
            assertNull(SavedScheduleCapacity.measure(invalid), entry.getKey());
            var rejected = assertThrows(ServiceException.class, () -> checked.prepare(input.run(),
                withSchedule(source, invalid), input.shortage(), List.of(forged), List.of(), null), entry.getKey());
            assertEquals("已保存排程利用率缺少可信产能日历，或与 ERP 源事实不一致",
                rejected.getMessage(), entry.getKey());
        }
        verifyNoInteractions(summaries, policy);
    }

    @Test void sameHashCannotHideTamperedSavedResourceCalendarAcrossReread() {
        var ref = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 2L, "WO-2");
        var at = LocalDateTime.of(2026, 9, 26, 9, 0);
        var task = Map.<String, Object>of("taskId", "4", "woId", "2", "resourceType", "WORKSTATION",
            "resourceId", "1", "processHours", "12", "startTime", "2026-09-27", "endTime", "2026-09-27");
        var originalCalendar = Map.<String, Object>of("calendarId", "8", "calendarDate", "2026-09-27",
            "resourceType", "WORKSTATION", "resourceId", "1", "plannedHours", "12", "effectiveHours", "8");
        var changedCalendar = Map.<String, Object>of("calendarId", "8", "calendarDate", "2026-09-27",
            "resourceType", "WORKSTATION", "resourceId", "1", "plannedHours", "13", "effectiveHours", "8");
        var original = sourceSnapshot(ref, at, task, originalCalendar);
        var changed = sourceSnapshot(ref, at, task, changedCalendar);
        assertEquals(new BigDecimal("150.0"), SavedScheduleCapacity.measure(original.currentSchedule()).maxUtilization());
        assertEquals(new BigDecimal("162.5"), SavedScheduleCapacity.measure(changed.currentSchedule()).maxUtilization());
        var verifier = new DecisionSourceFactVerifier();
        var roots = verifier.numericFacts(5L, original);
        assertTrue(roots.stream().anyMatch(f -> "ERP:CALENDAR:8:planned_hours".equals(f.evidenceKey())));
        assertTrue(roots.stream().anyMatch(f -> "ERP:SCHEDULE_TASK:4:process_hours".equals(f.evidenceKey())));
        var result =
                new DecisionResult(
                        5L,
                        ref,
                        50d,
                        DecisionRiskLevel.MEDIUM,
                        "HIGH",
                        List.of(),
                        null,
                        List.of(),
                        null,
                        Map.of(),
                        roots,
                        List.of(),
                        List.of());
        assertThrows(ServiceException.class, () -> verifier.assertProvenance(result, original, changed));
        var differentResource = sourceSnapshot(ref, at, task, Map.of(
            "calendarId", "8", "calendarDate", "2026-09-27",
            "resourceType", "WORKSTATION", "resourceId", "999", "plannedHours", "12",
            "effectiveHours", "8"));
        assertThrows(ServiceException.class, () -> verifier.assertProvenance(result, original, differentResource),
            "The same calendar value must not be rebound to another workstation under the old hash");
    }

    @Test void rejectedSafeNarrativeStillFailsClosedAfterFallback() {
        var ref = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 2L, "WO-2");
        var at = LocalDateTime.of(2026, 9, 26, 9, 0);
        var snapshot = new DeliveryContextSnapshot(ref, List.of(),
            List.of(new DeliveryContextSnapshot.WorkOrderFact(2L, "WO-2", 3L,
                BigDecimal.TEN, LocalDate.of(2026, 9, 28), 1L, 1L)),
            Map.of(3L, List.of(new DeliveryContextSnapshot.RoutingStepFact(4L, 5L, 1, 1L, 1L, BigDecimal.ONE))),
            List.of(new DeliveryContextSnapshot.RequirementFact(3L, "M-3", "物料", LocalDate.of(2026, 9, 28),
                BigDecimal.ONE, 2L, "WO-2")),
            List.of(new DeliveryContextSnapshot.StockFact(3L, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO)),
            List.of(), Map.of(LocalDate.of(2026, 9, 27), Map.of("WORKSTATION:1", BigDecimal.valueOf(8))),
            List.of(), savedSchedule("5.6", "5.6", "8"),
            List.of(new DeliveryContextSnapshot.SupplierMetricFact(7L, BigDecimal.valueOf(98), 10, at)),
            "hash", at, List.of());
        AiDecisionRun run = new AiDecisionRun();
        run.setId(5L); run.setBizType("WORK_ORDER"); run.setBizId(2L);
        var k = new DecisionKpiSet(0, 0, 0, 0, "2026-09-28", 0, BigDecimal.ZERO, null,
            BigDecimal.valueOf(70), 0, 0, BigDecimal.ZERO);
        var scenario = new DecisionScenario("BASELINE_CURRENT", "基线", "BASELINE_CURRENT", true, List.of(),
            k, BigDecimal.ZERO, 1, Map.of(), List.of("snapshot:hash"), List.of(), "scenario-specific-fingerprint", List.of(), List.of());
        var shortage = new ShortageSimulationResult(false, 0, null, BigDecimal.ZERO, List.of(),
            k, List.of(), List.of());
        var raw = """
            {"executiveSummary":"风险数值 55 ，请核对","riskExplanation":"核验业务证据","rootCauseExplanations":[],"scenarioTradeoffs":[],"recommendationRationale":"人工审核","evidenceCitations":[],"uncertaintyNotes":[],"operatorChecklist":[]}
            """;
        var guard = mock(DecisionPolicyGuardService.class);
        var refusal = new DecisionPolicyGuardService.PolicyResult(false,
            List.of(new DecisionPolicyGuardService.PolicyViolation("SOURCE_FACT_REJECTED", "BLOCK", "evidence", "拒绝")),
            null, true, true);
        when(guard.validate(any(), any(), anyList())).thenReturn(refusal);
        var checkedService =
                new DecisionAnalysisAssemblyService(
                        new DeliveryRiskScoringService(new AiProperties()),
                        new DecisionEvidenceService(),
                        new DecisionSourceFactVerifier(),
                        new DecisionSummaryService(mock(ChatModelFactory.class)),
                        guard);
        assertThrows(ServiceException.class, () -> checkedService.prepare(run, snapshot, shortage,
            List.of(scenario), List.of(), raw));
        verify(guard, times(2)).validate(any(), any(), anyList());
    }

    private static void assertTrustedFactsEqual(DecisionResult expected, DecisionResult actual) {
        assertEquals(expected.runId(), actual.runId());
        assertEquals(expected.businessRef(), actual.businessRef());
        assertEquals(expected.riskScore(), actual.riskScore());
        assertEquals(expected.riskLevel(), actual.riskLevel());
        assertEquals(expected.confidenceLevel(), actual.confidenceLevel());
        assertEquals(expected.rootCauseCodes(), actual.rootCauseCodes());
        assertEquals(expected.baselineKpis(), actual.baselineKpis());
        assertEquals(expected.scenarios(), actual.scenarios());
        assertEquals(expected.recommendedScenarioCode(), actual.recommendedScenarioCode());
        assertEquals(expected.evidence(), actual.evidence());
        assertEquals(expected.actions(), actual.actions());
    }

    @Test void explicitMissingEvidenceRejectsBeforeAnyModelOrGuardConclusion() {
        var input = evidenceFixture();
        var source = input.snapshot();
        for (var reason : List.of(DecisionEvidenceException.Reason.ROUTING_EVIDENCE_MISSING,
            DecisionEvidenceException.Reason.SCHEDULE_EVIDENCE_MISSING,
            DecisionEvidenceException.Reason.CAPACITY_EVIDENCE_MISSING)) {
            var snapshot = new DeliveryContextSnapshot(source.rootBusinessRef(), source.salesOrders(), source.workOrders(),
                reason == DecisionEvidenceException.Reason.ROUTING_EVIDENCE_MISSING ? Map.of() : source.routingByMaterial(),
                source.requirements(), source.stocks(), source.arrivals(),
                reason == DecisionEvidenceException.Reason.CAPACITY_EVIDENCE_MISSING ? Map.of() : source.capacityByDate(),
                source.holidays(), reason == DecisionEvidenceException.Reason.SCHEDULE_EVIDENCE_MISSING ? Map.of() : source.currentSchedule(),
                source.supplierMetrics(), source.snapshotHash(), source.snapshotAt(), source.warnings(), source.workOrderSalesLinks());
            var summaries = mock(DecisionSummaryService.class);
            var policy = mock(DecisionPolicyGuardService.class);
            var checked = new DecisionAnalysisAssemblyService(new DeliveryRiskScoringService(new AiProperties()),
                new DecisionEvidenceService(), new DecisionSourceFactVerifier(), summaries, policy);
            var rejected = assertThrows(DecisionEvidenceException.class, () -> checked.prepare(input.run(), snapshot,
                input.shortage(), List.of(input.scenario()), List.of(), null));
            assertEquals(reason, rejected.reason());
            assertEquals(reason.publicMessage(), rejected.getMessage());
            verifyNoInteractions(summaries, policy);
        }
    }

    /**
     * 复现已发布方案的终态工单被上游过滤后的快照，不把空分析范围误报为缺少产能。
     * 运行行的业务类型缺失时，仍以快照根类型作为门禁事实。
     */
    @Test void publishedPlanWithoutAnalyzableWorkOrdersDoesNotReportMissingCapacity() {
        var input = evidenceFixture();
        var source = input.snapshot();
        var schedule = new HashMap<>(source.currentSchedule());
        schedule.put("capacityPlanTasks", source.currentSchedule().get("tasks"));
        schedule.put("capacityPlanDayLoads", source.currentSchedule().get("taskDayLoads"));
        schedule.put("capacityLedgerBookings", source.currentSchedule().get("capacityBookings"));
        for (String field : List.of("workOrderIds", "tasks", "taskDayLoads", "capacityBookings")) {
            schedule.put(field, List.of());
        }
        var snapshot = new DeliveryContextSnapshot(
            new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, 1L, "APS-1"),
            List.of(), List.of(), Map.of(), List.of(), List.of(), List.of(), Map.of(),
            source.holidays(), schedule, List.of(), source.snapshotHash(), source.snapshotAt(),
            List.of("NO_WORK_ORDER: 当前决策范围没有生产工单"));
        input.run().setBizType(null);
        input.run().setBizId(1L);
        var unavailable = new DecisionScenario("BASELINE_CURRENT", "基线", "BASELINE_CURRENT", false,
            List.of("NO_SAVED_SCHEDULE"), null, null, 1, Map.of(), List.of("snapshot:hash"),
            List.of(), "hash", List.of(), List.of());
        var summaries = mock(DecisionSummaryService.class);
        var policy = mock(DecisionPolicyGuardService.class);
        var checked = new DecisionAnalysisAssemblyService(new DeliveryRiskScoringService(new AiProperties()),
            new DecisionEvidenceService(), new DecisionSourceFactVerifier(), summaries, policy);

        assertNotNull(SavedScheduleCapacity.measure(source.currentSchedule()));
        assertFalse(snapshot.currentSchedule().isEmpty());
        assertFalse(((List<?>) snapshot.currentSchedule().get("capacityCalendars")).isEmpty());
        assertFalse(((List<?>) snapshot.currentSchedule().get("capacityPlanTasks")).isEmpty());
        assertFalse(((List<?>) snapshot.currentSchedule().get("capacityLedgerBookings")).isEmpty());
        assertTrue(snapshot.capacityByDate().isEmpty());
        assertNull(SavedScheduleCapacity.measure(snapshot.currentSchedule()));
        var error = assertThrows(DecisionEvidenceException.class, () -> checked.prepare(input.run(), snapshot,
            input.shortage(), List.of(unavailable), List.of(), null));
        assertEquals("NO_ANALYZABLE_WORK_ORDER", error.reason().code());
        assertEquals("当前决策范围没有可分析工单，不能发布风险结论。", error.getMessage());
        verifyNoInteractions(summaries, policy);
    }

    /**
     * 即使历史排程的产能可核验，也不能为没有可分析工单的范围发布风险结论。
     */
    @Test void trustedHistoricalCapacityCannotReplaceAnalyzableWorkOrders() {
        var input = evidenceFixture();
        var source = input.snapshot();
        var snapshot = new DeliveryContextSnapshot(
            new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, 1L, "APS-1"),
            source.salesOrders(), List.of(), source.routingByMaterial(), source.requirements(),
            source.stocks(), source.arrivals(), source.capacityByDate(), source.holidays(),
            source.currentSchedule(), source.supplierMetrics(), source.snapshotHash(), source.snapshotAt(),
            List.of());
        input.run().setBizType("SCHEDULE_PLAN");
        input.run().setBizId(1L);

        assertNotNull(SavedScheduleCapacity.measure(snapshot.currentSchedule()));
        assertFalse(snapshot.capacityByDate().isEmpty());
        var error = assertThrows(DecisionEvidenceException.class, () -> service.prepare(input.run(), snapshot,
            input.shortage(), List.of(input.scenario()), List.of(), null));
        assertEquals("NO_ANALYZABLE_WORK_ORDER", error.reason().code());
    }

    /**
     * 缺料窗口空范围继续遵循原有排程、产能证据门禁，不普遍引入空工单错误。
     * 即使运行行记录为排程方案，也必须以快照根类型为准，不能扩大拒绝语义。
     */
    @Test void emptyShortageHorizonRetainsOriginalScheduleAndCapacityEvidenceReasons() {
        var input = evidenceFixture();
        var source = input.snapshot();
        input.run().setBizType("SCHEDULE_PLAN");
        var summaries = mock(DecisionSummaryService.class);
        var policy = mock(DecisionPolicyGuardService.class);
        var checked = new DecisionAnalysisAssemblyService(new DeliveryRiskScoringService(new AiProperties()),
            new DecisionEvidenceService(), new DecisionSourceFactVerifier(), summaries, policy);

        for (var reason : List.of(DecisionEvidenceException.Reason.SCHEDULE_EVIDENCE_MISSING,
            DecisionEvidenceException.Reason.CAPACITY_EVIDENCE_MISSING)) {
            var snapshot = new DeliveryContextSnapshot(
                new DecisionBusinessRef(DecisionBusinessType.SHORTAGE_HORIZON, null, null),
                List.of(), List.of(), Map.of(), List.of(), List.of(), List.of(), Map.of(),
                source.holidays(), reason == DecisionEvidenceException.Reason.SCHEDULE_EVIDENCE_MISSING
                    ? Map.of() : source.currentSchedule(),
                List.of(), source.snapshotHash(), source.snapshotAt(), List.of());
            var error = assertThrows(DecisionEvidenceException.class, () -> checked.prepare(input.run(), snapshot,
                input.shortage(), List.of(input.scenario()), List.of(), null));
            assertEquals(reason, error.reason());
            assertEquals(reason.publicMessage(), error.getMessage());
        }
        verifyNoInteractions(summaries, policy);
    }

    @Test void realUnavailableSavedPlanShapeStillExposesScheduleEvidenceCode() {
        var input = evidenceFixture(); var source = input.snapshot();
        var snapshot = new DeliveryContextSnapshot(source.rootBusinessRef(), source.salesOrders(), source.workOrders(),
            source.routingByMaterial(), source.requirements(), source.stocks(), source.arrivals(), source.capacityByDate(),
            source.holidays(), Map.of(), source.supplierMetrics(), source.snapshotHash(), source.snapshotAt(), source.warnings());
        var unavailable = new DecisionScenario("BASELINE_CURRENT", "baseline", "BASELINE_CURRENT", false,
            List.of("NO_SAVED_SCHEDULE"), null, BigDecimal.ZERO, 1, Map.of(), List.of("snapshot:hash"),
            List.of(), "hash", List.of(), List.of());
        var error = assertThrows(DecisionEvidenceException.class, () -> service.prepare(input.run(), snapshot,
            input.shortage(), List.of(unavailable), List.of(), null));
        assertEquals(DecisionEvidenceException.Reason.SCHEDULE_EVIDENCE_MISSING, error.reason());
    }
    @Test void missingComputedKpisWithPresentSourceEvidenceRemainGenericFailure() {
        var input = evidenceFixture();
        var unavailable = new DecisionScenario("BASELINE_CURRENT", "baseline", "BASELINE_CURRENT", false,
            List.of("SHORTAGE_UNKNOWN"), null, BigDecimal.ZERO, 1, Map.of(), List.of("snapshot:hash"),
            List.of(), "hash", List.of(), List.of());
        assertThrows(ServiceException.class, () -> service.prepare(input.run(), input.snapshot(),
            input.shortage(), List.of(unavailable), List.of(), null));
    }

    @Test void assemblyRejectsCroppedAndExpandedRatioWindowsUsingFrozenRunConfig() {
        for (int basisDays : new int[] {1, 31}) {
            var fixture = ratioEvidenceFixture(basisDays, basisDays == 1);
            fixture.run().setScoreConfigJson("{\"horizonDays\":30}");
            var failure = assertThrows(ServiceException.class, () -> service.prepare(fixture.run(), fixture.snapshot(),
                fixture.shortage(), List.of(fixture.scenario()), List.of(), null));
            assertTrue(failure.getMessage().contains("窗口"));
        }
    }

    @Test void assemblyRejectsNewRatioWithoutTrustedRunHorizonInsteadOfInferringFromBasis() {
        for (String config : new String[] {null, "{}", "{\"horizonDays\":0}", "{\"horizonDays\":91}",
            "{\"horizonDays\":30.5}", "{\"horizonDays\":\"30\"}"}) {
            var fixture = ratioEvidenceFixture(30, false);
            fixture.run().setScoreConfigJson(config);
            assertThrows(ServiceException.class, () -> service.prepare(fixture.run(), fixture.snapshot(),
                fixture.shortage(), List.of(fixture.scenario()), List.of(), null));
        }
    }

    @Test void legacyAssemblyWithoutNewRatioDoesNotRequireOrBackfillFrozenHorizon() {
        var fixture = evidenceFixture();
        var result = assertDoesNotThrow(() -> service.prepare(fixture.run(), fixture.snapshot(), fixture.shortage(),
            List.of(fixture.scenario()), List.of(), null));
        assertNull(fixture.run().getScoreConfigJson());
        assertSame(fixture.scenario().kpis(), result.baselineKpis());
        assertNull(result.baselineKpis().shortageGapRatio());
        assertNull(result.baselineKpis().shortageBasis());
    }

    private EvidenceFixture ratioEvidenceFixture(int basisDays, boolean omitDemand) {
        var original = evidenceFixture();
        var source = original.snapshot();
        var row = source.requirements().get(0);
        var snapshot = new DeliveryContextSnapshot(source.rootBusinessRef(), source.salesOrders(), source.workOrders(),
            source.routingByMaterial(), List.of(new DeliveryContextSnapshot.RequirementFact(301L, row.materialId(),
                row.materialCode(), row.materialName(), row.requiredDate(), row.requiredQty(), row.workOrderId(), row.workOrderNo(), 1L)),
            source.stocks(), source.arrivals(), source.capacityByDate(), source.holidays(), source.currentSchedule(),
            source.supplierMetrics(), source.snapshotHash(), source.snapshotAt(), source.warnings());
        var start = snapshot.snapshotAt().toLocalDate();
        var basis = new DecisionShortageBasis("5", "snapshot:" + snapshot.snapshotHash(), start,
            start.plusDays(basisDays - 1L), omitDemand ? List.of() : List.of(new DecisionShortageBasis.Requirement(
                301L, row.materialId(), row.requiredDate(), row.requiredQty(), 1L)),
            omitDemand ? List.of() : List.of(new DecisionShortageBasis.Gap(row.materialId(), 1L, BigDecimal.ZERO, null,
                "DERIVED:SHORTAGE:BASELINE_CURRENT_" + row.materialId() + ":max_gap")),
            "DERIVED:SHORTAGE:BASELINE_CURRENT:complete");
        var k = original.scenario().kpis();
        var ratioKpi = new DecisionKpiSet(k.impactedOrderCount(), k.delayedWorkOrderCount(), k.totalTardinessDays(),
            k.maxTardinessDays(), k.makespanDate(), k.shortageMaterialCount(), k.totalShortageGap(), k.earliestShortageDate(),
            k.maxResourceUtilization(), k.overloadedResourceCount(), k.changedWorkOrderCount(), k.actionCost(), BigDecimal.ZERO, basis);
        var s = original.scenario();
        var scenario = new DecisionScenario(s.scenarioCode(), s.scenarioName(), s.strategy(), s.feasible(), s.infeasibleReasons(),
            ratioKpi, s.objectiveScore(), s.rank(), s.overrides(), s.evidenceKeys(), s.schedulePreview(), s.inputHash(),
            s.delayedOrders(), s.bottleneckResources());
        return new EvidenceFixture(original.run(), snapshot, original.shortage(), scenario);
    }

    private EvidenceFixture evidenceFixture() {
        var ref = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 2L, "WO-2");
        var at = LocalDateTime.of(2026, 9, 26, 9, 0);
        var snapshot = new DeliveryContextSnapshot(ref, List.of(),
            List.of(new DeliveryContextSnapshot.WorkOrderFact(2L, "WO-2", 3L,
                BigDecimal.TEN, LocalDate.of(2026, 9, 28), 1L, 1L)),
            Map.of(3L, List.of(new DeliveryContextSnapshot.RoutingStepFact(4L, 5L, 1, 1L, 1L, BigDecimal.ONE))),
            List.of(new DeliveryContextSnapshot.RequirementFact(3L, "M-3", "物料", LocalDate.of(2026, 9, 28),
                BigDecimal.ONE, 2L, "WO-2")),
            List.of(new DeliveryContextSnapshot.StockFact(3L, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO)),
            List.of(), Map.of(LocalDate.of(2026, 9, 27), Map.of("WORKSTATION:1", BigDecimal.valueOf(8))),
            List.of(), savedSchedule("5.6", "5.6", "8"),
            List.of(new DeliveryContextSnapshot.SupplierMetricFact(7L, BigDecimal.valueOf(98), 10, at)),
            "hash", at, List.of());
        AiDecisionRun run = new AiDecisionRun();
        run.setId(5L); run.setBizType("WORK_ORDER"); run.setBizId(2L);
        var k = new DecisionKpiSet(0, 0, 0, 0, "2026-09-28", 0, BigDecimal.ZERO, null,
            BigDecimal.valueOf(70), 0, 0, BigDecimal.ZERO);
        var scenario = new DecisionScenario("BASELINE_CURRENT", "基线", "BASELINE_CURRENT", true, List.of(),
            k, BigDecimal.ZERO, 1, Map.of(), List.of("snapshot:hash"), List.of(), "scenario-specific-fingerprint", List.of(), List.of());
        var shortage = new ShortageSimulationResult(false, 0, null, BigDecimal.ZERO, List.of(),
            k, List.of(), List.of());
        return new EvidenceFixture(run, snapshot, shortage, scenario);
    }
    private record EvidenceFixture(AiDecisionRun run, DeliveryContextSnapshot snapshot,
                                   ShortageSimulationResult shortage, DecisionScenario scenario) { }

    private Map<String, Object> withScheduleField(Map<String, Object> schedule, String field, Object value) {
        var copy = new HashMap<>(schedule);
        copy.put(field, value);
        return copy;
    }

    private Map<String, Object> withScheduleRowField(Map<String, Object> schedule, String rows,
                                                      String field, Object value) {
        var row = (Map<?, ?>) ((List<?>) schedule.get(rows)).get(0);
        Map<String, Object> copy = new HashMap<>();
        row.forEach((key, original) -> copy.put((String) key, original));
        copy.put(field, value);
        return withScheduleField(schedule, rows, List.of(copy));
    }

    private DeliveryContextSnapshot withSchedule(DeliveryContextSnapshot source, Map<String, Object> schedule) {
        return new DeliveryContextSnapshot(source.rootBusinessRef(), source.salesOrders(), source.workOrders(),
            source.routingByMaterial(), source.requirements(), source.stocks(), source.arrivals(),
            source.capacityByDate(), source.holidays(), schedule, source.supplierMetrics(),
            source.snapshotHash(), source.snapshotAt(), source.warnings(), source.workOrderSalesLinks());
    }

    private DeliveryContextSnapshot sourceSnapshot(DecisionBusinessRef ref, LocalDateTime at,
                                                   Map<String, Object> task, Map<String, Object> calendar) {
        return new DeliveryContextSnapshot(ref, List.of(), List.of(), Map.of(), List.of(), List.of(),
            List.of(), Map.of(), List.of(), savedSchedule(task, calendar), List.of(), "same-hash", at, List.of());
    }

    private Map<String, Object> savedSchedule(String taskHours, String plannedHours, String effectiveHours) {
        return savedSchedule(
            Map.of("taskId", "4", "woId", "2", "resourceType", "WORKSTATION",
                "resourceId", "1", "processHours", taskHours,
                "startTime", "2026-09-27", "endTime", "2026-09-27"),
            Map.of("calendarId", "8", "calendarDate", "2026-09-27", "resourceType", "WORKSTATION",
                "resourceId", "1", "plannedHours", plannedHours, "effectiveHours", effectiveHours));
    }

    private Map<String, Object> savedSchedule(Map<String, Object> task, Map<String, Object> calendar) {
        /** 服务端将本计划的预订与同租户全部方案的日历账册分别提供。 */
        String tenantId = "000000";
        return Map.of("planId", "1", "tenantId", tenantId, "status", "PUBLISHED", "capacityCalcVersion", 2,
            "tasks", List.of(task),
            "taskDayLoads", List.of(Map.of("loadId", "9", "tenantId", tenantId, "planId", "1",
                "taskId", task.get("taskId"), "resourceType", task.get("resourceType"),
                "resourceId", task.get("resourceId"), "loadDate", "2026-09-27",
                "plannedHours", task.get("processHours"))),
            "capacityBookings", List.of(Map.of("bookingId", "10", "tenantId", tenantId, "planId", "1",
                "taskId", task.get("taskId"), "calendarId", calendar.get("calendarId"),
                "loadDate", "2026-09-27", "bookedHours", task.get("processHours"))),
            "capacityCalendars", List.of(Map.of("calendarId", calendar.get("calendarId"),
                "tenantId", tenantId, "status", "ACTIVE", "calendarDate", calendar.get("calendarDate"),
                "resourceType", calendar.get("resourceType"), "resourceId", calendar.get("resourceId"),
                "plannedHours", calendar.get("plannedHours"), "ledgerHours", calendar.get("plannedHours"),
                "effectiveHours", calendar.get("effectiveHours"))));
    }
    @Test void trustedSourceFactVerifierIsAvailableAsSpringBean() {
        assertNotNull(DecisionSourceFactVerifier.class.getAnnotation(Component.class));
    }

    @Test void missingBaselineDoesNotClaimLowRiskOrFabricateNumbers() {
        var snapshot = new DeliveryContextSnapshot(new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 2L, "WO-2"),
            List.of(), List.of(), Map.of(), List.of(), List.of(), List.of(), Map.of(), List.of(),
            Map.of(), List.of(), "hash", LocalDateTime.of(2026, 9, 26, 9, 0), List.of());
        AiDecisionRun run = new AiDecisionRun();
        run.setId(5L); run.setBizType("WORK_ORDER"); run.setBizId(2L);
        assertThrows(ServiceException.class, () -> service.prepare(run, snapshot, null, List.of(), List.of(), null));
    }
}
