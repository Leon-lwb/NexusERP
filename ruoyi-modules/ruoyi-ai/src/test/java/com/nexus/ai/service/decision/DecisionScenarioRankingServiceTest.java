package com.nexus.ai.service.decision;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.ai.config.AiProperties;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DecisionKpiSet;
import com.nexus.common.ai.decision.dto.DecisionScenario;
import com.nexus.common.ai.decision.dto.DecisionShortageBasis;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            BigDecimal.ZERO, null, BigDecimal.valueOf(80), 0, 1, new BigDecimal("100"), BigDecimal.ZERO);
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
        Map<String, Object> previewRow = new LinkedHashMap<>();
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
            BigDecimal.ZERO, null, BigDecimal.valueOf(84), 0, 0, null, BigDecimal.ZERO);
        DecisionScenario edd = scenario("APS_EDD", true, noAction, 0);
        DecisionScenario expedite = scenario("APS_CR_EXPEDITE", true, noAction, 0);
        DecisionScenario baseline = scenario("BASELINE_CURRENT", true,
            new DecisionKpiSet(1, 1, 1, 1, "2026-10-01", 0, BigDecimal.ZERO, null,
                null, 0, 0, null, BigDecimal.ZERO), 0);
        List<DecisionScenario> ranked = ranking.rank(List.of(expedite, baseline, edd));
        assertTrue(ranked.stream().noneMatch(DecisionScenario::feasible), "缺必需KPI时整次候选比较不可推荐");
        assertTrue(ranked.stream().allMatch(item -> item.objectiveScore() == null && item.rank() == 0));
        var completeOnly = ranking.rank(List.of(edd)).get(0);
        assertTrue(completeOnly.feasible());
        assertNotNull(completeOnly.objectiveScore());
        assertEquals(0, BigDecimal.ZERO.compareTo(completeOnly.kpis().actionCost()));
        assertNull(edd.kpis().actionCost());
    }

    @Test
    void equalUrgentCandidateKpisUseTheDocumentedLexicalTieBreakNotAGoldenBias() {
        var same = new DecisionKpiSet(1, 0, 0, 0, "2026-10-03", 0,
            BigDecimal.ZERO, null, BigDecimal.valueOf(20), 0, 1, BigDecimal.ZERO, BigDecimal.ZERO);
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
            BigDecimal.valueOf(40), "2026-10-03", BigDecimal.valueOf(20), 0, 1, BigDecimal.ZERO,
            new BigDecimal("0.5"));
        var ranked = ranking.rank(List.of(scenario("APS_EDD", false, shortage, 0),
            scenario("APS_CR", false, shortage, 0)));
        assertEquals("APS_CR", ranked.get(0).scenarioCode());
        assertTrue(ranked.stream().noneMatch(DecisionScenario::feasible));
    }

    @Test
    void expeditedArrivalOverridesAreDeepCopiedBeforeRanking() {
        Map<String, Object> dates = new LinkedHashMap<>();
        dates.put("1001", "2026-09-26");
        Map<String, Object> overrides = new LinkedHashMap<>();
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
            BigDecimal.TEN, "2026-09-25", BigDecimal.valueOf(80), 0, 1, null,
            new BigDecimal("0.5"));
        DecisionKpiSet expediteKpi = new DecisionKpiSet(1, 1, 2, 2, "2026-10-01", 0,
            BigDecimal.ZERO, null, BigDecimal.valueOf(80), 0, 1, BigDecimal.ONE, BigDecimal.ZERO);
        DecisionScenario cr = scenario("APS_CR", false, crKpi, 0);
        DecisionScenario expedite = scenario("APS_CR_EXPEDITE", true, expediteKpi, 0);
        List<DecisionScenario> ranked = ranking.rank(List.of(cr, expedite));
        assertEquals("APS_CR_EXPEDITE", ranked.get(0).scenarioCode());
        assertEquals(1, ranked.get(0).rank());
        assertTrue(ranked.get(0).feasible());
        assertEquals(0, BigDecimal.ONE.compareTo(ranked.get(0).kpis().actionCost()));
    }
    @Test
    void missingVerifiedShortageRatioCannotBeRankedFromRawAggregate() {
        // totalShortageGap 可能混合千克、个、升；缺少经核验比例时不得继续使用原始聚合排名。
        DecisionKpiSet largeRawGap = new DecisionKpiSet(1, 0, 0, 0, "2026-10-01", 1,
            BigDecimal.valueOf(100), "2026-10-01", BigDecimal.valueOf(80), 0, 0, BigDecimal.ZERO);
        DecisionKpiSet smallRawGap = new DecisionKpiSet(1, 0, 0, 0, "2026-10-01", 1,
            BigDecimal.ZERO, null, BigDecimal.valueOf(80), 0, 0, BigDecimal.ZERO);

        List<DecisionScenario> ranked = ranking.rank(List.of(
            scenario("APS_CR", true, largeRawGap, 0),
            scenario("APS_EDD", true, smallRawGap, 0)));

        assertTrue(ranked.stream().allMatch(s -> !s.feasible()));
        assertTrue(ranked.stream().allMatch(s -> s.infeasibleReasons().contains("SHORTAGE_GAP_RATIO_UNRANKABLE")));
        assertTrue(ranked.stream().allMatch(s -> s.objectiveScore() == null));
    }

    @Test
    void shortageRankingUsesVerifiedRatioInsteadOfRawAggregate() {
        DecisionKpiSet lowerRatio = new DecisionKpiSet(1, 0, 0, 0, "2026-10-01", 1,
            BigDecimal.valueOf(100), "2026-10-01", BigDecimal.valueOf(80), 0, 0, BigDecimal.ZERO,
            new BigDecimal("0.25"));
        DecisionKpiSet higherRatio = new DecisionKpiSet(1, 0, 0, 0, "2026-10-01", 1,
            BigDecimal.ONE, "2026-10-01", BigDecimal.valueOf(80), 0, 0, BigDecimal.ZERO,
            new BigDecimal("0.50"));

        List<DecisionScenario> ranked = ranking.rank(List.of(
            scenario("APS_EDD", true, higherRatio, 0),
            scenario("APS_CR", true, lowerRatio, 0)));

        assertEquals("APS_CR", ranked.get(0).scenarioCode());
    }

    @Test
    void ratioNumberWithoutPerMaterialSourceMustRemainUnrankable() {
        // 单个比例数值不能充当已核验的物料需求、基础单位和缺口来源。
        var unproven = new DecisionKpiSet(1, 0, 0, 0, "2026-10-01", 1,
            BigDecimal.TEN, "2026-10-01", BigDecimal.valueOf(80), 0, 0, BigDecimal.ZERO,
            new BigDecimal("0.25"));
        var ranked = ranking.rank(List.of(DecisionTestFixtures.scenario("APS_CR", true, unproven, 0))).get(0);
        assertFalse(ranked.feasible());
        assertNull(ranked.objectiveScore());
        assertTrue(ranked.infeasibleReasons().contains("SHORTAGE_GAP_RATIO_UNRANKABLE"));
    }

    /** 以下仅为数学单测输入，不计入真实来源案例或运行态验收。 */
    private static DecisionKpiSet kpis(int delayed, int tardiness, int changed) {
        var k = DecisionTestFixtures.kpis(delayed, tardiness, changed);
        return new DecisionKpiSet(k.impactedOrderCount(), k.delayedWorkOrderCount(), k.totalTardinessDays(),
            k.maxTardinessDays(), k.makespanDate(), k.shortageMaterialCount(), k.totalShortageGap(),
            k.earliestShortageDate(), k.maxResourceUtilization(), k.overloadedResourceCount(),
            k.changedWorkOrderCount(), k.actionCost(), BigDecimal.ZERO);
    }

    private static DecisionScenario scenario(String code, boolean feasible, DecisionKpiSet k, double score) {
        var original = DecisionTestFixtures.scenario(code, feasible, k, score);
        if (k == null || k.shortageGapRatio() == null) return original;
        LocalDate start = LocalDate.of(2026, 10, 1);
        String simulationKey = "DERIVED:SHORTAGE:" + code + ":complete";
        String gapKey = "DERIVED:SHORTAGE:" + code + "_1:max_gap";
        var basis = new DecisionShortageBasis("10", "snapshot:ratio-test", start, start.plusDays(29),
            List.of(new DecisionShortageBasis.Requirement(1L, 1L, start, new BigDecimal("100"), 1L)),
            List.of(new DecisionShortageBasis.Gap(1L, 1L, k.shortageGapRatio().multiply(new BigDecimal("100")),
                start, gapKey)), simulationKey);
        var sourced = new DecisionKpiSet(k.impactedOrderCount(), k.delayedWorkOrderCount(), k.totalTardinessDays(),
            k.maxTardinessDays(), k.makespanDate(), k.shortageMaterialCount(), k.totalShortageGap(),
            k.earliestShortageDate(), k.maxResourceUtilization(), k.overloadedResourceCount(),
            k.changedWorkOrderCount(), k.actionCost(), k.shortageGapRatio(), basis);
        return new DecisionScenario(code, original.scenarioName(), original.strategy(), feasible,
            original.infeasibleReasons(), sourced, original.objectiveScore(), 0, original.overrides(),
            List.of(basis.snapshotEvidenceKey(), simulationKey, gapKey, "ERP:REQUIREMENT:1:required_qty",
                "ERP:REQUIREMENT:1:base_uom_id"),
            original.schedulePreview(), original.inputHash(), original.delayedOrders(), original.bottleneckResources());
    }

    @Test
    void mixedMaterialUnitsUseEqualDimensionlessRatiosAndCompleteCandidateNormalization() {
        var ranked = ranking.rank(List.of(materialScenario("S1", "5", "0", "10", "20", 1L),
            materialScenario("S2", "0", "10", "10", "20", 1L),
            materialScenario("S3", "0", "0", "10", "20", 1L)));
        assertEquals("S3", ranked.get(0).scenarioCode());
        assertEquals(new BigDecimal("0E-8"), ranked.get(0).kpis().shortageGapRatio());
        BigDecimal component =
                BigDecimal.valueOf(
                                properties.getDecision().getRanking().getShortageWeight()
                                        / properties.getDecision().getRanking().weightSum())
                        .setScale(6, RoundingMode.HALF_UP);
        for (var item : ranked.subList(1, 3)) {
            assertEquals(new BigDecimal("0.25000000"), item.kpis().shortageGapRatio());
            assertEquals(component, item.objectiveScore());
        }
        var alone = ranking.rank(List.of(materialScenario("S1", "5", "0", "10", "20", 1L))).get(0);
        assertEquals(new BigDecimal("0.25000000"), alone.kpis().shortageGapRatio());
        assertEquals(0, alone.objectiveScore().signum()); // 相对分为零不改写原始缺料比例。
        var tied = ranking.rank(List.of(materialScenario("S1", "5", "0", "10", "20", 1L),
            materialScenario("S2", "0", "10", "10", "20", 1L)));
        assertTrue(tied.stream().allMatch(item -> item.objectiveScore().signum() == 0));
    }

    @Test
    void proportionallyConvertedVerifiedMaterialQuantitiesDoNotChangeRanking() {
        var base = ranking.rank(List.of(materialScenario("S1", "5", "0", "10", "20", 1L),
            materialScenario("S2", "0", "10", "10", "20", 1L)));
        // 仅验证已经由调用方核验的等比例换算；排名器自身不擅自把千克换成克。
        var converted = ranking.rank(List.of(materialScenario("S1", "5000", "0", "10000", "20", 3L),
            materialScenario("S2", "0", "10", "10000", "20", 3L)));
        assertEquals(base.stream().map(DecisionScenario::scenarioCode).toList(),
            converted.stream().map(DecisionScenario::scenarioCode).toList());
        assertEquals(base.stream().map(DecisionScenario::objectiveScore).toList(),
            converted.stream().map(DecisionScenario::objectiveScore).toList());
    }

    @Test
    void missingUnitOrMismatchedDemandSetRevokesTheWholeComparisonInsteadOfFavoringKnownRows() {
        var good = materialScenario("S1", "5", "0", "10", "20", 1L);
        for (var broken : List.of(materialScenario("S2", "0", "10", "10", "20", null),
            materialScenario("S2", "0", "10", "11", "20", 1L),
            materialScenario("S2", "11", "0", "10", "20", 1L),
            materialScenario("S2", "0", "0", "0", "20", 1L))) {
            var ranked = ranking.rank(List.of(good, broken));
            assertTrue(ranked.stream().allMatch(item -> item.objectiveScore() == null && item.rank() == 0));
            assertTrue(ranked.stream().allMatch(item -> item.infeasibleReasons().contains("SHORTAGE_GAP_RATIO_UNRANKABLE")));
        }
    }

    private static DecisionScenario materialScenario(String code, String aGap, String bGap,
                                                       String aDemand, String bDemand, Long aUom) {
        LocalDate start = LocalDate.of(2026, 10, 1);
        String aKey = "DERIVED:SHORTAGE:" + code + "_1:max_gap";
        String bKey = "DERIVED:SHORTAGE:" + code + "_2:max_gap";
        String simulation = "DERIVED:SHORTAGE:" + code + ":complete";
        var basis = new DecisionShortageBasis("10", "snapshot:ratio-test", start, start.plusDays(29),
            List.of(new DecisionShortageBasis.Requirement(1L, 1L, start, new BigDecimal(aDemand), aUom),
                new DecisionShortageBasis.Requirement(2L, 2L, start, new BigDecimal(bDemand), 2L)),
            List.of(new DecisionShortageBasis.Gap(1L, aUom, new BigDecimal(aGap), start, aKey),
                new DecisionShortageBasis.Gap(2L, 2L, new BigDecimal(bGap), start, bKey)), simulation);
        var k = new DecisionKpiSet(0, 0, 0, 0, null, 0, new BigDecimal(aGap).add(new BigDecimal(bGap)),
            null, BigDecimal.valueOf(80), 0, 0, BigDecimal.ZERO, null, basis);
        return new DecisionScenario(code, code, code, true, List.of(), k, null, 0, Map.of(),
            List.of(basis.snapshotEvidenceKey(), simulation, aKey, bKey, "ERP:REQUIREMENT:1:required_qty",
                "ERP:REQUIREMENT:1:base_uom_id", "ERP:REQUIREMENT:2:required_qty", "ERP:REQUIREMENT:2:base_uom_id"),
            List.of(), "sha256-input", List.of(), List.of());
    }

    @Test
    void oneMissingCandidateKpiWithdrawsEveryScoreAndRecommendationPosition() {
        var complete = scenario("APS_EDD", true, kpis(0, 0, 0), 0);
        var missing = scenario("APS_CR", true, null, 0);
        var ranked = ranking.rank(List.of(complete, missing));
        assertTrue(ranked.stream().noneMatch(DecisionScenario::feasible));
        assertTrue(ranked.stream().allMatch(item -> item.objectiveScore() == null && item.rank() == 0));
        assertEquals(0, complete.rank(), "撤销本次资格不能重写输入或历史结论");
    }

    @Test
    @SuppressWarnings("unchecked")
    void actualServerRunBindsProducerBasisBeforeRankAndRejectsWrongWindow() throws Exception {
        var original = materialScenario("APS_EDD", "5", "0", "10", "20", 1L);
        var k = original.kpis(); var b = k.shortageBasis();
        var pending = new DecisionShortageBasis(null, b.snapshotEvidenceKey(), b.windowStart(), b.windowEnd(),
            b.requirements(), b.gaps(), b.simulationEvidenceKey());
        var produced = new DecisionKpiSet(k.impactedOrderCount(), k.delayedWorkOrderCount(), k.totalTardinessDays(), k.maxTardinessDays(),
            k.makespanDate(), k.shortageMaterialCount(), k.totalShortageGap(), k.earliestShortageDate(), k.maxResourceUtilization(),
            k.overloadedResourceCount(), k.changedWorkOrderCount(), k.actionCost(), null, pending);
        var scenario = new DecisionScenario(original.scenarioCode(), original.scenarioName(), original.strategy(), original.feasible(),
            original.infeasibleReasons(), produced, null, 0, original.overrides(), original.evidenceKeys(), original.schedulePreview(),
            original.inputHash(), original.delayedOrders(), original.bottleneckResources());
        var start = b.windowStart();
        var snapshot =
                new DeliveryContextSnapshot(
                        new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 10L, "WO-10"),
                        List.of(),
                        List.of(),
                        Map.of(),
                        List.of(
                                new DeliveryContextSnapshot.RequirementFact(
                                        1L,
                                        1L,
                                        "A",
                                        "物料A",
                                        start,
                                        new BigDecimal("10"),
                                        10L,
                                        "WO-10",
                                        1L),
                                new DeliveryContextSnapshot.RequirementFact(
                                        2L,
                                        2L,
                                        "B",
                                        "物料B",
                                        start,
                                        new BigDecimal("20"),
                                        10L,
                                        "WO-10",
                                        2L)),
                        List.of(),
                        List.of(),
                        Map.of(),
                        List.of(),
                        Map.of(),
                        List.of(),
                        "ratio-test",
                        start.atStartOfDay(),
                        List.of());
        var method =
                ranking.getClass()
                        .getMethod(
                                "rank",
                                Long.class,
                                DeliveryContextSnapshot.class,
                                int.class,
                                List.class);
        var ranked = (List<DecisionScenario>) method.invoke(ranking, 900L, snapshot, 30, List.of(scenario));
        assertEquals("900", ranked.get(0).kpis().shortageBasis().runId());
        assertEquals(new BigDecimal("0.25000000"), ranked.get(0).kpis().shortageGapRatio());
        assertNotNull(ranked.get(0).objectiveScore());
        assertThrows(
                InvocationTargetException.class,
                () -> method.invoke(ranking, 900L, snapshot, 1, List.of(scenario)),
                "不能由客户端或推演依据裁剪冻结窗口");
        assertNull(scenario.kpis().shortageBasis().runId(), "绑定不能原地改写ERP返回或历史运行");
    }

}
