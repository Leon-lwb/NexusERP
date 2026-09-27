package com.nexus.ai.service.decision;

import com.nexus.ai.config.AiProperties;
import com.nexus.common.ai.decision.dto.DecisionKpiSet;
import com.nexus.common.ai.decision.dto.DecisionScenario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        List<DecisionScenario> candidates = scenarios.stream().map(DecisionScenarioRankingService::knownZeroCost).toList();
        List<DecisionScenario> complete = candidates.stream().filter(s -> validKpis(s.kpis())).toList();
        List<DecisionScenario> scored = new ArrayList<>(scenarios.size());
        for (DecisionScenario scenario : candidates) {
            if (!validKpis(scenario.kpis())) {
                List<String> reasons = new ArrayList<>();
                if (scenario.infeasibleReasons() != null) reasons.addAll(scenario.infeasibleReasons());
                reasons.add("MISSING_KPI");
                scored.add(copy(scenario, false, List.copyOf(reasons), null, 0));
                continue;
            }
            DecisionKpiSet k = scenario.kpis();
            double objective = (
                weights.getTardinessWeight() * normalized(complete, s -> s.kpis().totalTardinessDays(), k.totalTardinessDays())
                + weights.getDelayedCountWeight() * normalized(complete, s -> s.kpis().delayedWorkOrderCount(), k.delayedWorkOrderCount())
                + weights.getShortageWeight() * normalized(complete, s -> s.kpis().totalShortageGap().doubleValue(), k.totalShortageGap().doubleValue())
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
            ranked.add(copy(s, s.feasible(), s.infeasibleReasons(), s.objectiveScore(), i + 1));
        }
        return List.copyOf(ranked);
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
            k.overloadedResourceCount(), k.changedWorkOrderCount(), BigDecimal.ZERO);
        return new DecisionScenario(s.scenarioCode(), s.scenarioName(), s.strategy(), s.feasible(),
            s.infeasibleReasons(), withCost, s.objectiveScore(), s.rank(), s.overrides(),
            s.evidenceKeys(), s.schedulePreview(), s.inputHash(), s.delayedOrders(), s.bottleneckResources());
    }

    private static boolean validKpis(DecisionKpiSet k) {
        return k != null && k.totalTardinessDays() >= 0 && k.delayedWorkOrderCount() >= 0
            && k.changedWorkOrderCount() >= 0 && k.totalShortageGap() != null
            && k.totalShortageGap().signum() >= 0 && k.actionCost() != null
            && k.actionCost().signum() >= 0 && k.maxResourceUtilization() != null
            && k.maxResourceUtilization().signum() >= 0;
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
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean
            || value instanceof java.time.temporal.Temporal || value instanceof Enum<?>) return value;
        throw new IllegalArgumentException("方案包含不可复制的可变值");
    }

}
