package com.nexus.ai.service.decision;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.ai.decision.dto.DecisionKpiSet;
import com.nexus.common.ai.decision.dto.DecisionResult;
import com.nexus.common.ai.decision.dto.DecisionScenario;
import com.nexus.common.ai.decision.dto.EvidenceItem;
import com.nexus.common.ai.decision.dto.RetrievalHit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Tag("dev")
class DecisionEvidenceServiceTest {
    private final DecisionEvidenceService service = new DecisionEvidenceService();

    @Test
    void duplicateEvidenceCollapsesToStableKey() {
        EvidenceItem first = item("ERP", "STOCK", 10L, "available_qty", "5");
        List<EvidenceItem> normalized = service.normalize(List.of(first, first));
        assertEquals(1, normalized.size());
        assertEquals("ERP:STOCK:10:available_qty", normalized.get(0).evidenceKey());
        assertEquals(normalized.get(0), service.indexByKey(normalized).get("ERP:STOCK:10:available_qty"));
    }

    @Test
    void conflictingValuesAtSameEvidenceKeyFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> service.normalize(List.of(
            item("ERP", "STOCK", 10L, "available_qty", "5"),
            item("ERP", "STOCK", 10L, "available_qty", "50"))));
    }

    @Test
    void numericDerivedEvidenceKeepsParentKeysAndCannotInventUnboundNumber() {
        EvidenceItem derived = service.derivedNumber("delay_days", "预计拖期天数", "4", "day",
            List.of("ERP:ORDER:1:promise_date", "ERP:SCHEDULE:2:makespan"));
        assertTrue(derived.verifiable());
        assertEquals("DERIVED:CALC:delay_days:delay_days", derived.evidenceKey());
        assertEquals(2, derived.derivedFromEvidenceKeys().size());
        assertThrows(IllegalArgumentException.class, () -> service.derivedNumber("delay_days", "拖期", "4", "day", List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.derivedNumber("delay_days", "拖期", "随便写", "day", List.of("a")));
    }

    @Test
    void sameTextFromTwoDifferentChunksKeepsTwoNormativeEvidenceItems() {
        RetrievalHit first = new RetrievalHit(7L, 11L, 1001L, "SOP", "相同文本", 0.7, null,
            null, null, "v1", LocalDate.of(2026, 9, 23), "RAG");
        RetrievalHit second = new RetrievalHit(7L, 12L, 1002L, "SOP", "相同文本", 0.6, null,
            null, null, "v1", LocalDate.of(2026, 9, 23), "RAG");
        List<EvidenceItem> items = service.fromRetrievalHits(List.of(first, second));
        assertEquals(2, items.size());
        assertTrue(items.stream().allMatch(EvidenceItem::normative));
        assertEquals(List.of("RAG:RAG:1001:content", "RAG:RAG:1002:content"),
            items.stream().map(EvidenceItem::evidenceKey).toList());
    }

    @Test
    void numericResultWithoutSourceFailsCoverageGate() {
        DecisionResult result = new DecisionResult(10L, null, 50.0, null, "LOW", List.of(),
            null, List.of(), null, null, List.of(), List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> service.assertNumericEvidenceCoverage(result));
    }

    @Test
    void missingDocumentNameCannotCreateVerifiedNormativeEvidence() {
        RetrievalHit orphan = new RetrievalHit(7L, 11L, 1001L, null, "旧文本", .7,
            null, null, null, null, null, "UNVERIFIED");
        assertThrows(IllegalArgumentException.class, () -> service.fromRetrievalHits(List.of(orphan)));
    }

    @Test
    void numericEvidenceForOneScenarioDoesNotProveDifferentScenarioKpis() {
        var baseline = DecisionTestFixtures.kpis(1, 3, 0);
        var other =
                new DecisionKpiSet(
                        1,
                        1,
                        3,
                        3,
                        baseline.makespanDate(),
                        0,
                        BigDecimal.valueOf(5),
                        null,
                        baseline.maxResourceUtilization(),
                        0,
                        0,
                        BigDecimal.ZERO);
        List<EvidenceItem> provided = new ArrayList<>();
        provided.add(scoped("RISK", "risk_score", "50"));
        provided.add(scoped("APS_EDD", "impacted_order_count", "1"));
        provided.add(scoped("APS_EDD", "delayed_work_order_count", "1"));
        provided.add(scoped("APS_EDD", "total_tardiness_days", "3"));
        provided.add(scoped("APS_EDD", "max_tardiness_days", "3"));
        provided.add(scoped("APS_EDD", "shortage_material_count", "0"));
        provided.add(scoped("APS_EDD", "overloaded_resource_count", "0"));
        provided.add(scoped("APS_EDD", "changed_work_order_count", "0"));
        provided.add(scoped("APS_EDD", "total_shortage_gap", "0"));
        provided.add(scoped("APS_EDD", "max_resource_utilization", "80"));
        provided.add(scoped("APS_EDD", "action_cost", "0"));
        DecisionResult result = new DecisionResult(10L, null, 50, null, "HIGH", List.of(), null,
            List.of(DecisionTestFixtures.scenario("APS_EDD", true, baseline, 0),
                DecisionTestFixtures.scenario("APS_CR", true, other, 0)), null,
            null, provided, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> service.assertNumericEvidenceCoverage(result));
    }

    @Test
    void scenarioEvidenceKeysMustNameEachNumericalFactTheyClaimToSupport() {
        var kpi = DecisionTestFixtures.kpis(1, 3, 0);
        List<EvidenceItem> evidence = new ArrayList<>();
        evidence.add(scoped("RISK", "risk_score", "50"));
        for (String metric : List.of("impacted_order_count", "delayed_work_order_count",
            "total_tardiness_days", "max_tardiness_days", "shortage_material_count",
            "overloaded_resource_count", "changed_work_order_count", "total_shortage_gap",
            "max_resource_utilization", "action_cost")) {
            String value = switch (metric) {
                case "impacted_order_count", "delayed_work_order_count" -> "1";
                case "total_tardiness_days", "max_tardiness_days" -> "3";
                case "max_resource_utilization" -> "80";
                default -> "0";
            };
            evidence.add(scoped("APS_EDD", metric, value));
        }
        var unbound = DecisionTestFixtures.scenario("APS_EDD", true, kpi, 0);
        DecisionResult result = new DecisionResult(10L, null, 50, null, "HIGH", List.of(),
            null, List.of(unbound), null, null, evidence, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> service.assertNumericEvidenceCoverage(result));

        List<String> keys = service.normalize(evidence).stream()
            .filter(item -> "APS_EDD".equals(item.sourceCode())).map(EvidenceItem::evidenceKey).toList();
        var bound =
                new DecisionScenario(
                        unbound.scenarioCode(),
                        unbound.scenarioName(),
                        unbound.strategy(),
                        unbound.feasible(),
                        unbound.infeasibleReasons(),
                        unbound.kpis(),
                        unbound.objectiveScore(),
                        unbound.rank(),
                        unbound.overrides(),
                        keys,
                        unbound.schedulePreview(),
                        unbound.inputHash(),
                        unbound.delayedOrders(),
                        unbound.bottleneckResources());
        DecisionResult valid = new DecisionResult(10L, null, 50, null, "HIGH", List.of(),
            null, List.of(bound), null, null, evidence, List.of(), List.of());
        assertDoesNotThrow(() -> service.assertNumericEvidenceCoverage(valid));
    }

    @Test
    void sourceIdsSortNumericallyNotByDecimalTextOrInputOrder() {
        List<EvidenceItem> normalized = service.normalize(List.of(
            item("ERP", "STOCK", 10L, "qty", "10"),
            item("ERP", "STOCK", 2L, "qty", "2")));
        assertEquals(List.of(2L, 10L), normalized.stream().map(EvidenceItem::sourceId).toList());
    }

    @Test
    void equivalentNumericFormattingAndUnitsNormalizeWithoutChangingTheirMagnitude() {
        EvidenceItem chinese = new EvidenceItem(10L, null, "ERP", "SCHEDULE", 99L, "99", "排产",
            "delay_days", "拖期", "NUMBER", "4.00", "天", null, "v1", BigDecimal.ONE,
            true, false, List.of(), null);
        EvidenceItem canonical = new EvidenceItem(10L, null, "ERP", "SCHEDULE", 99L, "99", "排产",
            "delay_days", "拖期", "NUMBER", "4", "day", null, "v1", BigDecimal.ONE,
            true, false, List.of(), null);
        var result = service.normalize(List.of(chinese, canonical));
        assertEquals(1, result.size());
        assertEquals("day", result.get(0).unit());
        assertEquals("4", result.get(0).value());
    }

    @Test
    void sourceFactsWithUnnamedMetricsRetainStableIdentityAndMeetDatabaseNameConstraint() {
        var source = new EvidenceItem(99L, null, "ERP", "ARRIVAL", 11L, "11", null,
            "remaining_qty", null, "NUMBER", "1", null, LocalDateTime.of(2026, 9, 26, 10, 0),
            "hash-1", null, true, false, List.of(), null);
        EvidenceItem normalized = service.normalize(List.of(source)).get(0);
        assertEquals("remaining_qty", normalized.metricName());
        assertEquals("ERP:ARRIVAL:11:remaining_qty", normalized.evidenceKey());
        assertEquals("1", normalized.value());
    }


    @Test
    void shortageRatioCannotBorrowRawAggregateEvidence() {
        // 相同数值的原始跨单位聚合，不是无量纲比例的来源证据。
        var k =
                new DecisionKpiSet(
                        1,
                        1,
                        3,
                        3,
                        "2026-10-01",
                        0,
                        BigDecimal.ZERO,
                        null,
                        BigDecimal.valueOf(80),
                        0,
                        0,
                        BigDecimal.ZERO,
                        new BigDecimal("0.25"));
        var provided = new ArrayList<EvidenceItem>();
        provided.add(scoped("RISK", "risk_score", "50"));
        for (String metric : List.of("impacted_order_count", "delayed_work_order_count",
            "total_tardiness_days", "max_tardiness_days", "shortage_material_count",
            "overloaded_resource_count", "changed_work_order_count", "total_shortage_gap",
            "max_resource_utilization", "action_cost")) {
            String value = switch (metric) {
                case "impacted_order_count", "delayed_work_order_count" -> "1";
                case "total_tardiness_days", "max_tardiness_days" -> "3";
                case "max_resource_utilization" -> "80";
                default -> "0";
            };
            provided.add(scoped("APS_CR", metric, value));
        }
        var original = DecisionTestFixtures.scenario("APS_CR", true, k, 0);
        var scenario =
                new DecisionScenario(
                        "APS_CR",
                        "CR",
                        "CR",
                        true,
                        List.of(),
                        k,
                        BigDecimal.ZERO,
                        1,
                        original.overrides(),
                        service.normalize(provided).stream()
                                .filter(e -> "APS_CR".equals(e.sourceCode()))
                                .map(EvidenceItem::evidenceKey)
                                .toList(),
                        List.of(),
                        original.inputHash(),
                        List.of(),
                        List.of());
        var result = new DecisionResult(10L, null, 50, null, "HIGH", List.of(), null,
            List.of(scenario), null, null, provided, List.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> service.assertNumericEvidenceCoverage(result));
    }

    private static EvidenceItem scoped(String scope, String metric, String value) {
        return new EvidenceItem(10L, null, "ERP", "SCENARIO", null, scope, scope,
            metric, metric, "NUMBER", value, null, LocalDateTime.of(2026, 9, 23, 10, 0),
            "v1", BigDecimal.ONE, true, false, List.of(), null);
    }

    private static EvidenceItem item(String domain, String type, Long id, String metric, String value) {
        return new EvidenceItem(10L, null, domain, type, id, String.valueOf(id), type,
            metric, metric, "NUMBER", value, null,
            LocalDateTime.of(2026, 9, 23, 10, 0), "v1", BigDecimal.ONE,
            true, false, List.of(), null);
    }
}
