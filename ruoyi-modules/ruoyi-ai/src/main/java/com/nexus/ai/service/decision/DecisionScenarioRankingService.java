package com.nexus.ai.service.decision;

import com.nexus.ai.config.AiProperties;
import com.nexus.common.ai.decision.dto.DecisionKpiSet;
import com.nexus.common.ai.decision.dto.DecisionScenario;
import com.nexus.common.ai.decision.dto.DecisionShortageBasis;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.core.exception.ServiceException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * 可测量的确定性多目标排序，不可行建议始终排在末尾。
 */
@Service
@RequiredArgsConstructor
public class DecisionScenarioRankingService {
    private final AiProperties properties;

    /** 实际Flow先绑定服务端运行与快照，再重算比例；模型和ERP不能自行指定运行身份。 */
    public List<DecisionScenario> rank(
            Long runId,
            DeliveryContextSnapshot snapshot,
            int horizonDays,
            List<DecisionScenario> scenarios) {
        if (runId == null
                || runId <= 0
                || snapshot == null
                || horizonDays < 1
                || horizonDays > 90
                || scenarios == null) throw new ServiceException("排名缺少服务端冻结运行范围");
        List<DecisionScenario> bound = new ArrayList<>();
        for (var scenario : scenarios) {
            if (scenario == null) throw new ServiceException("排名候选缺失");
            var k = scenario.kpis();
            if (k == null || k.shortageBasis() == null) { bound.add(scenario); continue; }
            var old = k.shortageBasis();
            if (old.runId() != null && !runId.toString().equals(old.runId()))
                throw new ServiceException("推演依据属于其他运行");
            var basis = new DecisionShortageBasis(runId.toString(), old.snapshotEvidenceKey(), old.windowStart(), old.windowEnd(),
                old.requirements(), old.gaps(), old.simulationEvidenceKey());
            var frozen = new DecisionKpiSet(k.impactedOrderCount(), k.delayedWorkOrderCount(), k.totalTardinessDays(), k.maxTardinessDays(),
                k.makespanDate(), k.shortageMaterialCount(), k.totalShortageGap(), k.earliestShortageDate(), k.maxResourceUtilization(),
                k.overloadedResourceCount(), k.changedWorkOrderCount(), k.actionCost(), k.shortageGapRatio(), basis);
            bound.add(new DecisionScenario(scenario.scenarioCode(), scenario.scenarioName(), scenario.strategy(), scenario.feasible(),
                scenario.infeasibleReasons(), frozen, scenario.objectiveScore(), scenario.rank(), scenario.overrides(), scenario.evidenceKeys(),
                scenario.schedulePreview(), scenario.inputHash(), scenario.delayedOrders(), scenario.bottleneckResources()));
        }
        var ranked = rank(bound);
        var verifier = new DecisionSourceFactVerifier();
        for (var scenario : ranked) verifier.assertShortageBasis(runId, scenario.kpis(), snapshot, horizonDays);
        return ranked;
    }

    public List<DecisionScenario> rank(List<DecisionScenario> scenarios) {
        if (scenarios == null || scenarios.isEmpty() || scenarios.size() > properties.getDecision().getMaxScenarios()) {
            throw new IllegalArgumentException("方案数量不在允许范围内");
        }
        Set<String> codes = new HashSet<>();
        for (DecisionScenario scenario : scenarios) {
            if (scenario == null || scenario.scenarioCode() == null || scenario.scenarioCode().isBlank()
                || !codes.add(scenario.scenarioCode())) {
                throw new IllegalArgumentException("方案编号必须非空且唯一");
            }
        }
        AiProperties.DecisionRankingConfig weights = properties.getDecision().getRanking();
        if (weights == null || !Double.isFinite(weights.weightSum()) || weights.weightSum() <= 0) {
            throw new IllegalArgumentException("方案评分权重无效");
        }
        // 无成本重排可证明动作成本为零；没有估算的催交方案不能如此处理，
        // 保留原始 ERP 指标以便审计。
        // 先用完整的同运行来源集合重算 R/N；外部传入的裸比例不能绕过物料/UOM 门禁。
        Map<String, DecisionShortageRatioCalculator.ScenarioRatio> ratios = shortageRatios(scenarios);
        List<DecisionScenario> candidates = scenarios.stream().map(s -> withRatio(s,
            ratios.containsKey(s.scenarioCode()) ? ratios.get(s.scenarioCode()).rawRatio() : null))
            .map(DecisionScenarioRankingService::knownZeroCost).toList();
        List<DecisionScenario> complete = candidates.stream().filter(s -> validKpis(s.kpis())).toList();
        boolean completeComparison = complete.size() == candidates.size();
        List<DecisionScenario> scored = new ArrayList<>(scenarios.size());
        for (DecisionScenario scenario : candidates) {
            if (!completeComparison || !validKpis(scenario.kpis())) {
                List<String> reasons = new ArrayList<>();
                if (scenario.infeasibleReasons() != null) reasons.addAll(scenario.infeasibleReasons());
                if (!completeComparison) reasons.add("INCOMPLETE_SCENARIO_COMPARISON");
                reasons.add(scenario.kpis() != null && !validShortageGapRatio(scenario.kpis())
                    ? "SHORTAGE_GAP_RATIO_UNRANKABLE" : "MISSING_KPI");
                scored.add(copy(scenario, false, List.copyOf(reasons), null, 0));
                continue;
            }
            DecisionKpiSet k = scenario.kpis();
            double objective = (
                weights.getTardinessWeight() * normalized(complete, s -> s.kpis().totalTardinessDays(), k.totalTardinessDays())
                + weights.getDelayedCountWeight() * normalized(complete, s -> s.kpis().delayedWorkOrderCount(), k.delayedWorkOrderCount())
                + weights.getShortageWeight() * ratios.get(scenario.scenarioCode()).normalizedRatio().doubleValue()
                + weights.getUtilizationWeight() * normalized(complete, s -> Math.max(0, s.kpis().maxResourceUtilization().doubleValue() - 80),
                    Math.max(0, k.maxResourceUtilization().doubleValue() - 80))
                + weights.getActionCostWeight() * normalized(complete, s -> s.kpis().actionCost().doubleValue(), k.actionCost().doubleValue())
                + weights.getPlanChangeWeight() * normalized(complete, s -> s.kpis().changedWorkOrderCount(), k.changedWorkOrderCount())
                + (scenario.feasible() ? 0 : weights.getInfeasibleWeight())) / weights.weightSum();
            if (!Double.isFinite(objective)) throw new IllegalArgumentException("方案评分结果非有限数值");
            scored.add(copy(scenario, scenario.feasible(), scenario.infeasibleReasons(),
                BigDecimal.valueOf(objective).setScale(6, RoundingMode.HALF_UP), 0));
        }
        scored.sort(Comparator.comparing(DecisionScenario::feasible).reversed()
            .thenComparing(DecisionScenario::objectiveScore, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(s -> s.kpis() == null ? Integer.MAX_VALUE : s.kpis().changedWorkOrderCount())
            .thenComparing(s -> s.kpis() == null ? null : s.kpis().actionCost(),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(DecisionScenario::scenarioCode));
        List<DecisionScenario> ranked = new ArrayList<>(scored.size());
        for (int i = 0; i < scored.size(); i++) {
            DecisionScenario s = scored.get(i);
            ranked.add(copy(s, s.feasible(), s.infeasibleReasons(), s.objectiveScore(),
                s.objectiveScore() == null ? 0 : i + 1));
        }
        return List.copyOf(ranked);
    }

    private static Map<String, DecisionShortageRatioCalculator.ScenarioRatio> shortageRatios(
        List<DecisionScenario> scenarios) {
        var calculator = new DecisionShortageRatioCalculator();
        DecisionShortageRatioCalculator.FrozenRun frozen = null;
        List<DecisionShortageRatioCalculator.ScenarioFacts> facts = new ArrayList<>();
        try {
            for (DecisionScenario scenario : scenarios) {
                if (scenario.kpis() == null) return Map.of(); // 任一已生成候选缺KPI时整次比较不可推荐。
                DecisionShortageBasis basis = scenario.kpis().shortageBasis();
                if (basis == null || basis.requirements() == null || basis.gaps() == null
                    || basis.runId() == null || !basis.runId().matches("[1-9][0-9]{0,18}")
                    || scenario.evidenceKeys() == null
                    || !scenario.evidenceKeys().contains(basis.snapshotEvidenceKey())
                    || !scenario.evidenceKeys().contains(basis.simulationEvidenceKey())
                    || !("DERIVED:SHORTAGE:" + scenario.scenarioCode() + ":complete").equals(basis.simulationEvidenceKey()))
                    return Map.of();
                List<DecisionShortageRatioCalculator.RequirementRow> demands = new ArrayList<>();
                for (var row : basis.requirements()) {
                    if (!positive(row.detailId()) || !positive(row.materialId()) || !positive(row.baseUomId())
                        || !scenario.evidenceKeys().contains("ERP:REQUIREMENT:" + row.detailId() + ":required_qty")
                        || !scenario.evidenceKeys().contains("ERP:REQUIREMENT:" + row.detailId() + ":base_uom_id"))
                        return Map.of();
                    demands.add(new DecisionShortageRatioCalculator.RequirementRow(row.materialId().toString(),
                        row.detailId().toString(), row.requiredDate(), row.requiredQty(), row.baseUomId().toString()));
                }
                demands.sort(Comparator.comparing(DecisionShortageRatioCalculator.RequirementRow::detailId));
                var current = new DecisionShortageRatioCalculator.FrozenRun(basis.runId(), basis.windowStart(),
                    basis.windowEnd(), demands, basis.snapshotEvidenceKey());
                if (frozen == null) frozen = current;
                else if (!frozen.equals(current)) return Map.of();
                List<DecisionShortageRatioCalculator.GapFact> gaps = new ArrayList<>();
                for (var gap : basis.gaps()) {
                    if (!positive(gap.materialId()) || !positive(gap.baseUomId())
                        || !scenario.evidenceKeys().contains(gap.sourceEvidenceKey())
                        || !("DERIVED:SHORTAGE:" + scenario.scenarioCode() + "_" + gap.materialId() + ":max_gap")
                            .equals(gap.sourceEvidenceKey())) return Map.of();
                    gaps.add(new DecisionShortageRatioCalculator.GapFact(gap.materialId().toString(),
                        gap.maxGap(), gap.baseUomId().toString(), gap.peakDate(), gap.sourceEvidenceKey()));
                }
                facts.add(new DecisionShortageRatioCalculator.ScenarioFacts(basis.runId(), scenario.scenarioCode(),
                    basis.windowStart(), basis.windowEnd(), gaps, basis.simulationEvidenceKey()));
            }
            var computed = calculator.calculate(frozen, facts);
            Map<String, DecisionShortageRatioCalculator.ScenarioRatio> result = new LinkedHashMap<>();
            computed.scenarios().forEach(ratio -> result.put(ratio.scenarioCode(), ratio));
            for (var scenario : scenarios) {
                if (scenario.kpis() == null || scenario.kpis().shortageGapRatio() == null) continue;
                if (scenario.kpis().shortageGapRatio().compareTo(result.get(scenario.scenarioCode()).rawRatio()) != 0)
                    return Map.of(); // 拒绝伪造比例，不静默纠正后再推荐。
            }
            return result;
        } catch (IllegalArgumentException invalid) {
            // 数量、分母、日期或集合不可比较时保留原始 KPI，只撤销新评分资格。
            return Map.of();
        }
    }

    private static boolean positive(Long id) { return id != null && id > 0; }

    private static DecisionScenario withRatio(DecisionScenario s, BigDecimal ratio) {
        if (s.kpis() == null) return s;
        DecisionKpiSet k = s.kpis();
        var calculated = new DecisionKpiSet(k.impactedOrderCount(), k.delayedWorkOrderCount(),
            k.totalTardinessDays(), k.maxTardinessDays(), k.makespanDate(), k.shortageMaterialCount(),
            k.totalShortageGap(), k.earliestShortageDate(), k.maxResourceUtilization(),
            k.overloadedResourceCount(), k.changedWorkOrderCount(), k.actionCost(), ratio, k.shortageBasis());
        return new DecisionScenario(s.scenarioCode(), s.scenarioName(), s.strategy(), s.feasible(),
            s.infeasibleReasons(), calculated, s.objectiveScore(), s.rank(), s.overrides(),
            s.evidenceKeys(), s.schedulePreview(), s.inputHash(), s.delayedOrders(), s.bottleneckResources());
    }

    private static DecisionScenario knownZeroCost(DecisionScenario s) {
        if (s.kpis() == null || s.kpis().actionCost() != null ||
            !("BASELINE_CURRENT".equals(s.scenarioCode()) || "APS_EDD".equals(s.scenarioCode())
                || "APS_CR".equals(s.scenarioCode())) ||
            s.overrides() != null && !s.overrides().isEmpty()) return s;
        DecisionKpiSet k = s.kpis();
        DecisionKpiSet withCost = new DecisionKpiSet(k.impactedOrderCount(), k.delayedWorkOrderCount(),
            k.totalTardinessDays(), k.maxTardinessDays(), k.makespanDate(), k.shortageMaterialCount(),
            k.totalShortageGap(), k.earliestShortageDate(), k.maxResourceUtilization(),
            k.overloadedResourceCount(), k.changedWorkOrderCount(), BigDecimal.ZERO,
            k.shortageGapRatio(), k.shortageBasis());
        return new DecisionScenario(s.scenarioCode(), s.scenarioName(), s.strategy(), s.feasible(),
            s.infeasibleReasons(), withCost, s.objectiveScore(), s.rank(), s.overrides(),
            s.evidenceKeys(), s.schedulePreview(), s.inputHash(), s.delayedOrders(), s.bottleneckResources());
    }

    private static boolean validKpis(DecisionKpiSet k) {
        return k != null && k.totalTardinessDays() >= 0 && k.delayedWorkOrderCount() >= 0
            && k.changedWorkOrderCount() >= 0 && validShortageGapRatio(k) && k.actionCost() != null
            && k.actionCost().signum() >= 0 && k.maxResourceUtilization() != null
            && k.maxResourceUtilization().signum() >= 0;
    }

    private static boolean validShortageGapRatio(DecisionKpiSet k) {
        return k.shortageGapRatio() != null && k.shortageGapRatio().signum() >= 0
            && k.shortageGapRatio().compareTo(BigDecimal.ONE) <= 0;
    }

    private static double normalized(List<DecisionScenario> scenarios, ToDoubleFunction<DecisionScenario> metric,
                                     double value) {
        if (scenarios.isEmpty()) return 0;
        double low = scenarios.stream().mapToDouble(metric).min().orElse(value);
        double high = scenarios.stream().mapToDouble(metric).max().orElse(value);
        return high == low ? 0 : (value - low) / (high - low);
    }

    private static DecisionScenario copy(DecisionScenario s, boolean feasible, List<String> reasons,
                                         BigDecimal score, int rank) {
        return new DecisionScenario(s.scenarioCode(), s.scenarioName(), s.strategy(), feasible,
            reasons == null ? List.of() : List.copyOf(reasons), s.kpis(), score, rank,
            s.overrides() == null ? Map.of() : deepMap(s.overrides()),
            s.evidenceKeys() == null ? List.of() : List.copyOf(s.evidenceKeys()),
            s.schedulePreview() == null ? List.of() : s.schedulePreview().stream()
                .map(DecisionScenarioRankingService::deepMap).toList(),
            s.inputHash(), s.delayedOrders() == null ? List.of() : List.copyOf(s.delayedOrders()),
            s.bottleneckResources() == null ? List.of() : List.copyOf(s.bottleneckResources()));
    }

    private static Map<String, Object> deepMap(Map<String, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, deepValue(value)));
        return Collections.unmodifiableMap(copy); // 真实 APS 预览允许空值，不能使用 Map.copyOf。
    }

    private static Object deepValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> {
                if (!(key instanceof String text)) throw new IllegalArgumentException("方案覆盖的键必须为字符串");
                copy.put(text, deepValue(item));
            });
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(item -> copy.add(deepValue(item)));
            return Collections.unmodifiableList(copy);
        }
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Temporal
                || value instanceof Enum<?>) return value;
        throw new IllegalArgumentException("方案包含不可复制的可变值");
    }

}
