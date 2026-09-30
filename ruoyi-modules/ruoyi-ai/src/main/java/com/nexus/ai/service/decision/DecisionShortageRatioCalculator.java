package com.nexus.ai.service.decision;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用于单次已冻结决策运行的纯计算、无量纲缺料计算器。
 * 调用方须先核验 ERP 来源事实、UOM 身份、快照完整性和证据键，
 * 再将这些事实传入；本类不执行 I/O，也不做单位换算。
 * 缺失事实绝不按零缺料处理。
 */
public final class DecisionShortageRatioCalculator {
    private static final int SCALE = 8;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    /**
     * 逐物料计算 r=G/D，再逐方案计算等权重的原始比率 R，
     * 最后在完整候选集合 C 上以最小值和最大值归一化，得到 N。
     * 任一需求、方案、UOM 或来源不完整时，拒绝整个 C 集合。
     * 这只是分析结果，不是采购授权，也不会更新排序。
     */
    public CalculationResult calculate(FrozenRun run, List<ScenarioFacts> candidates) {
        if (run == null || blank(run.runId()) || run.windowStart() == null || run.windowEnd() == null
            || run.windowStart().isAfter(run.windowEnd()) || blank(run.snapshotEvidenceKey())) {
            throw missing("frozen run/window/snapshot evidence");
        }
        if (candidates == null || candidates.isEmpty()) {
            throw missing("complete candidate set");
        }
        List<ScenarioFacts> scenarios = frozenList(candidates);
        Map<String, MaterialDemand> demands = aggregateDemands(run);
        Set<String> scenarioCodes = new HashSet<>();
        List<ScenarioRatio> raw = new ArrayList<>(scenarios.size());
        for (ScenarioFacts scenario : scenarios) {
            if (scenario == null || blank(scenario.scenarioCode()) || !scenarioCodes.add(scenario.scenarioCode())
                || !run.runId().equals(scenario.runId()) || !run.windowStart().equals(scenario.windowStart())
                || !run.windowEnd().equals(scenario.windowEnd()) || blank(scenario.simulationEvidenceKey())) {
                throw missing("scenario identity/window/simulation evidence");
            }
            Map<String, GapFact> gaps = validateGaps(run, scenario, demands);
            Map<String, MaterialRatio> materials = new LinkedHashMap<>();
            BigDecimal sumRatios = BigDecimal.ZERO;
            for (MaterialDemand demand : demands.values()) {
                GapFact gap = gaps.get(demand.materialId());
                if (!demand.baseUomId().equals(gap.baseUomId()) || gap.maxGap().compareTo(demand.requiredQty()) > 0) {
                    throw missing("gap exceeds its own material demand or has a different base UOM");
                }
                BigDecimal ratio = gap.maxGap().divide(demand.requiredQty(), SCALE, ROUNDING);
                sumRatios = sumRatios.add(ratio);
                materials.put(demand.materialId(), new MaterialRatio(demand.materialId(), demand.baseUomId(),
                    demand.requiredQty(), gap.maxGap(), ratio, gap.sourceEvidenceKey()));
            }
            BigDecimal rawRatio = demands.isEmpty() ? ZERO
                : sumRatios.divide(BigDecimal.valueOf(demands.size()), SCALE, ROUNDING);
            raw.add(new ScenarioRatio(scenario.scenarioCode(), materials, rawRatio, ZERO,
                scenario.simulationEvidenceKey()));
        }
        BigDecimal min = raw.stream().map(ScenarioRatio::rawRatio).min(BigDecimal::compareTo).orElseThrow();
        BigDecimal max = raw.stream().map(ScenarioRatio::rawRatio).max(BigDecimal::compareTo).orElseThrow();
        List<ScenarioRatio> normalized = new ArrayList<>(raw.size());
        for (ScenarioRatio scenario : raw) {
            BigDecimal value = raw.size() == 1 || min.compareTo(max) == 0 ? ZERO
                : scenario.rawRatio().subtract(min).divide(max.subtract(min), SCALE, ROUNDING);
            normalized.add(new ScenarioRatio(scenario.scenarioCode(), scenario.materials(), scenario.rawRatio(),
                value, scenario.simulationEvidenceKey()));
        }
        return new CalculationResult(run.runId(), run.windowStart(), run.windowEnd(), demands, normalized,
            run.snapshotEvidenceKey());
    }

    private static Map<String, MaterialDemand> aggregateDemands(FrozenRun run) {
        Map<String, DemandAccumulator> accumulators = new LinkedHashMap<>();
        Set<String> detailIds = new HashSet<>();
        for (RequirementRow row : run.requirements()) {
            if (row == null || blank(row.materialId()) || blank(row.detailId()) || !detailIds.add(row.detailId())
                || row.requiredDate() == null || row.requiredDate().isBefore(run.windowStart())
                || row.requiredDate().isAfter(run.windowEnd()) || row.requiredQty() == null
                || row.requiredQty().signum() <= 0 || blank(row.baseUomId())) {
                throw missing("frozen requirement detail/date/net quantity/base UOM");
            }
            DemandAccumulator accumulator = accumulators.get(row.materialId());
            if (accumulator == null) {
                accumulator = new DemandAccumulator(row.baseUomId());
                accumulators.put(row.materialId(), accumulator);
            } else if (!accumulator.baseUomId.equals(row.baseUomId())) {
                throw missing("conflicting base UOMs for one material");
            }
            accumulator.requiredQty = accumulator.requiredQty.add(row.requiredQty());
            accumulator.detailIds.add(row.detailId());
        }
        Map<String, MaterialDemand> demands = new LinkedHashMap<>();
        accumulators.forEach((materialId, accumulator) -> demands.put(materialId,
            new MaterialDemand(materialId, accumulator.baseUomId, accumulator.requiredQty, accumulator.detailIds)));
        return immutableMap(demands);
    }

    private static Map<String, GapFact> validateGaps(FrozenRun run, ScenarioFacts scenario,
                                                       Map<String, MaterialDemand> demands) {
        Map<String, GapFact> gaps = new LinkedHashMap<>();
        for (GapFact gap : scenario.gaps()) {
            if (gap == null || blank(gap.materialId()) || gap.maxGap() == null || gap.maxGap().signum() < 0
                || blank(gap.baseUomId()) || blank(gap.sourceEvidenceKey())
                || gap.maxGap().signum() > 0 && gap.peakDate() == null
                || gap.peakDate() != null && (gap.peakDate().isBefore(run.windowStart())
                    || gap.peakDate().isAfter(run.windowEnd()))
                || gaps.putIfAbsent(gap.materialId(), gap) != null) {
                throw missing("unique material gap/base UOM/peak date/source evidence");
            }
        }
        /** 条目缺失或多余均须拒绝；零缺口也必须由明确且有证据的 GapFact 表达。 */
        if (!gaps.keySet().equals(demands.keySet())) {
            throw missing("scenario material coverage differs from frozen requirements");
        }
        return gaps;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static IllegalArgumentException missing(String reason) {
        return new IllegalArgumentException("MISSING_SOURCE_OR_UNIT: " + reason);
    }

    private static <T> List<T> frozenList(List<T> source) {
        if (source == null || source.stream().anyMatch(item -> item == null)) {
            throw missing("incomplete list of frozen facts");
        }
        return List.copyOf(source);
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        if (source == null || source.entrySet().stream().anyMatch(e -> e.getKey() == null || e.getValue() == null)) {
            throw missing("incomplete calculated material facts");
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static final class DemandAccumulator {
        private final String baseUomId;
        private final List<String> detailIds = new ArrayList<>();
        private BigDecimal requiredQty = BigDecimal.ZERO;

        private DemandAccumulator(String baseUomId) {
            this.baseUomId = baseUomId;
        }
    }

    /** Q 包含单次运行及其窗口内已净算的正数量；完整性由证据证明。 */
    public record FrozenRun(String runId, LocalDate windowStart, LocalDate windowEnd,
                            List<RequirementRow> requirements, String snapshotEvidenceKey) {
        public FrozenRun {
            requirements = frozenList(requirements);
        }
    }

    /** 一条已核验的需求明细，以物料的基础 UOM 表示，尚未抵扣库存和入库量。 */
    public record RequirementRow(String materialId, String detailId, LocalDate requiredDate,
                                 BigDecimal requiredQty, String baseUomId) {
    }

    /** 某方案的全部显式缺口事实；simulationEvidenceKey 也用于证明空结果。 */
    public record ScenarioFacts(String runId, String scenarioCode, LocalDate windowStart, LocalDate windowEnd,
                                List<GapFact> gaps, String simulationEvidenceKey) {
        public ScenarioFacts {
            gaps = frozenList(gaps);
        }
    }

    /** 峰值缺口仅针对当前物料；缺口为零时仍须提供来源证据键。 */
    public record GapFact(String materialId, BigDecimal maxGap, String baseUomId,
                          LocalDate peakDate, String sourceEvidenceKey) {
    }

    public record MaterialDemand(String materialId, String baseUomId, BigDecimal requiredQty,
                                 List<String> detailIds) {
        public MaterialDemand {
            detailIds = frozenList(detailIds);
        }
    }

    public record MaterialRatio(String materialId, String baseUomId, BigDecimal requiredQty, BigDecimal maxGap,
                                BigDecimal ratio, String sourceEvidenceKey) {
    }

    /** rawRatio=R；normalizedRatio=N。N=0 并不意味着不存在原始缺料。 */
    public record ScenarioRatio(String scenarioCode, Map<String, MaterialRatio> materials,
                                BigDecimal rawRatio, BigDecimal normalizedRatio, String simulationEvidenceKey) {
        public ScenarioRatio {
            materials = immutableMap(materials);
        }
    }

    public record CalculationResult(String runId, LocalDate windowStart, LocalDate windowEnd,
                                    Map<String, MaterialDemand> demands, List<ScenarioRatio> scenarios,
                                    String snapshotEvidenceKey) {
        public CalculationResult {
            demands = immutableMap(demands);
            scenarios = frozenList(scenarios);
        }
    }
}