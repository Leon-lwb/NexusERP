package com.nexus.ai.service.decision;

import com.nexus.ai.config.AiProperties;
import com.nexus.common.ai.decision.dto.DecisionKpiSet;
import com.nexus.common.ai.decision.dto.DecisionScenario;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.nexus.ai.service.decision.DecisionTestFixtures.kpis;
import static com.nexus.ai.service.decision.DecisionTestFixtures.scenario;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class DecisionScenarioRankingServiceTest {
    private final AiProperties properties = new AiProperties();
    private final DecisionScenarioRankingService ranking = new DecisionScenarioRankingService(properties);

    @Test
    void lowerTardinessWinsAndRankedCopiesDoNotChangeTheInputs() {
        DecisionScenario baseline = scenario("BASELINE_CURRENT", true, kpis(4, 12, 3), 0);
        DecisionScenario candidate = scenario("APS_CR", true, kpis(1, 2, 5), 0);
        List<DecisionScenario> ranked = ranking.rank(List.of(baseline, candidate));
        assertEquals("APS_CR", ranked.get(0).scenarioCode());
        assertEquals(1, ranked.get(0).rank());
        assertEquals(0, candidate.rank());
        assertEquals(0, BigDecimal.ZERO.compareTo(candidate.objectiveScore()));
        assertTrue(ranked.get(0).objectiveScore().compareTo(ranked.get(1).objectiveScore()) < 0);
    }

    @Test
    void infeasibleScenarioNeverBeatsFeasibleOneEvenWithZeroTardiness() {
        DecisionScenario infeasible = scenario("APS_CR", false, kpis(0, 0, 0), 0);
        DecisionScenario feasible = scenario("APS_EDD", true, kpis(3, 8, 8), 0);
        assertEquals("APS_EDD", ranking.rank(List.of(infeasible, feasible)).get(0).scenarioCode());
    }

    @Test
    void equalMetricsTieBreakByChangesThenCostThenLexicalCode() {
        DecisionScenario first = scenario("APS_B", true, kpis(1, 2, 2), 0);
        DecisionScenario second = scenario("APS_A", true, kpis(1, 2, 2), 0);
        assertEquals(List.of("APS_A", "APS_B"), ranking.rank(List.of(first, second)).stream()
            .map(DecisionScenario::scenarioCode).toList());
        DecisionScenario unchanged = scenario("APS_Z", true, kpis(1, 2, 1), 0);
        assertEquals("APS_Z", ranking.rank(List.of(first, unchanged)).get(0).scenarioCode());
    }

    @Test
    void nullKpiIsRetainedButCannotBeatFeasibleProposal() {
        DecisionScenario missing = scenario("APS_CR", true, null, 0);
        DecisionScenario feasible = scenario("APS_EDD", true, kpis(1, 2, 0), 0);
        List<DecisionScenario> ranked = ranking.rank(List.of(missing, feasible));
        assertEquals("APS_EDD", ranked.get(0).scenarioCode());
        assertFalse(ranked.get(1).feasible());
        assertTrue(ranked.get(1).infeasibleReasons().contains("MISSING_KPI"));
    }

    @Test
    void equalMetricsNormalizeToZeroAndScoresAreRepeatable() {
        DecisionScenario a = scenario("APS_A", true, kpis(1, 1, 1), 7);
        DecisionScenario b = scenario("APS_B", true, kpis(1, 1, 1), 7);
        List<DecisionScenario> ranked = ranking.rank(List.of(b, a));
        assertEquals(0, ranked.get(0).objectiveScore().compareTo(BigDecimal.ZERO));
        assertEquals(ranked, ranking.rank(List.of(a, b)));
    }

    @Test
    void actionCostAffectsRankingWhenOtherMetricsAreEqual() {
        DecisionKpiSet cheap = kpis(1, 1, 1);
        DecisionKpiSet expensive = new DecisionKpiSet(1, 1, 1, 1, "2026-10-01", 0,
            BigDecimal.ZERO, null, BigDecimal.valueOf(80), 0, 1, new BigDecimal("100"));
        DecisionScenario a = scenario("APS_Z", true, cheap, 0);
        DecisionScenario b = scenario("APS_A", true, expensive, 0);
        assertEquals("APS_Z", ranking.rank(List.of(b, a)).get(0).scenarioCode());
    }

    @Test
    void duplicateCodeAndNoCandidatesAreRejected() {
        DecisionScenario a = scenario("APS_A", true, kpis(1, 1, 1), 0);
        assertThrows(IllegalArgumentException.class, () -> ranking.rank(List.of()));
        assertThrows(IllegalArgumentException.class, () -> ranking.rank(List.of(a, a)));
    }

    @Test
    void actualApsPreviewRetainsNullableResourceFieldsWithoutCrashing() {
        Map<String, Object> previewRow = new java.util.LinkedHashMap<>();
        previewRow.put("woId", "11");
        previewRow.put("stepId", null);  // 真实 ERP 预览不包含缺失的步骤标识。
        previewRow.put("resourceId", null);
        DecisionScenario base = scenario("APS_EDD", true, kpis(0, 0, 0), 0);
        DecisionScenario withPreview = new DecisionScenario(base.scenarioCode(), base.scenarioName(),
            base.strategy(), base.feasible(), base.infeasibleReasons(), base.kpis(), null, 0,
            base.overrides(), base.evidenceKeys(), List.of(previewRow), base.inputHash(),
            base.delayedOrders(), base.bottleneckResources());
        DecisionScenario ranked = ranking.rank(List.of(withPreview)).get(0);
        assertNull(ranked.schedulePreview().get(0).get("resourceId"));
        assertEquals("11", ranked.schedulePreview().get(0).get("woId"));
    }
    @Test
    void erpCandidatesWithoutCostEstimateMayRankOnlyIfTheyRequireNoCostAction() {
        DecisionKpiSet noAction = new DecisionKpiSet(1, 1, 1, 1, "2026-10-01", 0,
            BigDecimal.ZERO, null, BigDecimal.valueOf(84), 0, 0, null);
        DecisionScenario edd = scenario("APS_EDD", true, noAction, 0);
        DecisionScenario expedite = scenario("APS_CR_EXPEDITE", true, noAction, 0);
        DecisionScenario baseline = scenario("BASELINE_CURRENT", true,
            new DecisionKpiSet(1, 1, 1, 1, "2026-10-01", 0, BigDecimal.ZERO, null,
                null, 0, 0, null), 0);
        List<DecisionScenario> ranked = ranking.rank(List.of(expedite, baseline, edd));
        assertEquals("APS_EDD", ranked.get(0).scenarioCode());
        assertTrue(ranked.get(0).feasible());
        assertEquals(0, BigDecimal.ZERO.compareTo(ranked.get(0).kpis().actionCost()));
        assertFalse(ranked.get(1).feasible());
        assertFalse(ranked.get(2).feasible());
        assertEquals(1, ranked.stream().filter(DecisionScenario::feasible).count());
        assertNull(edd.kpis().actionCost());
    }

    @Test
    void equalUrgentCandidateKpisUseTheDocumentedLexicalTieBreakNotAGoldenBias() {
        var same = new DecisionKpiSet(1, 0, 0, 0, "2026-10-03", 0,
            BigDecimal.ZERO, null, BigDecimal.valueOf(20), 0, 1, BigDecimal.ZERO);
        var edd = scenario("APS_EDD", true, same, 0);
        var cr = scenario("APS_CR", true, same, 0);
        assertEquals(List.of("APS_CR", "APS_EDD"), ranking.rank(List.of(edd, cr)).stream()
            .map(DecisionScenario::scenarioCode).toList());
        assertEquals(List.of("APS_CR", "APS_EDD"), ranking.rank(List.of(cr, edd)).stream()
            .map(DecisionScenario::scenarioCode).toList());
    }

    @Test
    void noFeasibleMaterialPlanCannotAcquireARecommendationFromItsRankingPosition() {
        var shortage = new DecisionKpiSet(1, 0, 0, 0, "2026-10-03", 2,
            BigDecimal.valueOf(40), "2026-10-03", BigDecimal.valueOf(20), 0, 1, BigDecimal.ZERO);
        var ranked = ranking.rank(List.of(scenario("APS_EDD", false, shortage, 0),
            scenario("APS_CR", false, shortage, 0)));
        assertEquals("APS_CR", ranked.get(0).scenarioCode());
        assertTrue(ranked.stream().noneMatch(DecisionScenario::feasible));
    }

    @Test
    void expeditedArrivalOverridesAreDeepCopiedBeforeRanking() {
        Map<String, Object> dates = new java.util.LinkedHashMap<>();
        dates.put("1001", "2026-09-26");
        Map<String, Object> overrides = new java.util.LinkedHashMap<>();
        overrides.put("purchaseArrivalOverrides", dates);
        DecisionScenario base = scenario("APS_CR_EXPEDITE", true, kpis(1, 1, 1), 0);
        DecisionScenario expedite = new DecisionScenario(base.scenarioCode(), base.scenarioName(),
            base.strategy(), base.feasible(), base.infeasibleReasons(), base.kpis(), null, 0,
            overrides, base.evidenceKeys(), base.schedulePreview(), base.inputHash(),
            base.delayedOrders(), base.bottleneckResources());
        DecisionScenario ranked = ranking.rank(List.of(expedite)).get(0);
        dates.put("1001", "2026-10-10");
        @SuppressWarnings("unchecked")
        Map<String, Object> rankedDates = (Map<String, Object>) ranked.overrides().get("purchaseArrivalOverrides");
        assertEquals("2026-09-26", rankedDates.get("1001"));
        assertThrows(UnsupportedOperationException.class, () -> rankedDates.put("1001", "2026-09-27"));
    }

    @Test
    void feasibleExpediteWithLineCountProxyCanWinAgainstMateriallyInfeasibleCr() {
        DecisionKpiSet crKpi = new DecisionKpiSet(1, 1, 2, 2, "2026-10-01", 1,
            BigDecimal.TEN, "2026-09-25", BigDecimal.valueOf(80), 0, 1, null);
        DecisionKpiSet expediteKpi = new DecisionKpiSet(1, 1, 2, 2, "2026-10-01", 0,
            BigDecimal.ZERO, null, BigDecimal.valueOf(80), 0, 1, BigDecimal.ONE);
        DecisionScenario cr = scenario("APS_CR", false, crKpi, 0);
        DecisionScenario expedite = scenario("APS_CR_EXPEDITE", true, expediteKpi, 0);
        List<DecisionScenario> ranked = ranking.rank(List.of(cr, expedite));
        assertEquals("APS_CR_EXPEDITE", ranked.get(0).scenarioCode());
        assertEquals(1, ranked.get(0).rank());
        assertTrue(ranked.get(0).feasible());
        assertEquals(0, BigDecimal.ONE.compareTo(ranked.get(0).kpis().actionCost()));
    }
}
