package com.nexus.ai.service.decision;

import com.nexus.ai.config.AiProperties;
import com.nexus.common.ai.decision.dto.ApsScenarioCompareResult;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionScenario;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 限定范围的假设仿真入口，仅允许 ERP 接口装配和模拟工业数据。
 */
@Service
@RequiredArgsConstructor
public class ApsWhatIfSimulationService {
    private static final List<String> DEFAULT_CODES =
        List.of("BASELINE_CURRENT", "APS_EDD", "APS_CR", "APS_CR_EXPEDITE");

    private final AiProperties properties;
    private final IndustrialDecisionDataPort port;

    public ApsScenarioCompareResult compare(DeliveryContextSnapshot snapshot, List<String> scenarioCodes,
                                            int horizonDays, DecisionActor actor) {
        if (snapshot == null || actor == null || horizonDays < 1
            || horizonDays > properties.getDecision().getMaxHorizonDays()) {
            throw new IllegalArgumentException("决策快照、执行人或推演时间窗口无效");
        }
        int max = properties.getDecision().getMaxScenarios();
        if (max < 1) throw new IllegalArgumentException("未配置可用的推演方案数");
        List<String> codes = scenarioCodes == null || scenarioCodes.isEmpty()
            ? DEFAULT_CODES.subList(0, Math.min(DEFAULT_CODES.size(), max)) : List.copyOf(scenarioCodes);
        if (codes.size() > max || codes.stream().anyMatch(code -> code.isBlank())
            || new HashSet<>(codes).size() != codes.size()) {
            throw new IllegalArgumentException("方案编号必须非空、唯一且不超过配置上限");
        }
        ApsScenarioCompareResult result = port.simulateSchedules(snapshot, codes, horizonDays, actor);
        if (result == null || result.scenarios() == null || result.scenarios().isEmpty()) {
            throw new IllegalArgumentException("ERP 未返回可展示的推演方案");
        }
        for (DecisionScenario scenario : result.scenarios()) {
            if (scenario == null || scenario.scenarioCode() == null || scenario.scenarioCode().isBlank()
                || scenario.evidenceKeys() == null || scenario.feasible() && scenario.kpis() == null) {
                throw new IllegalArgumentException("ERP 推演方案缺少编号、证据引用或可行方案 KPI");
            }
        }
        if (result.scenarios().stream().anyMatch(DecisionScenario::feasible)) return result;
        // 保留所有失败方案以便持久化和展示；编排器保存对比结果后，
        // 才允许抛出 NO_FEASIBLE_SCENARIO。
        List<String> warnings = new ArrayList<>();
        if (result.warnings() != null) warnings.addAll(result.warnings());
        if (!warnings.contains("NO_FEASIBLE_SCENARIO")) warnings.add("NO_FEASIBLE_SCENARIO");
        return new ApsScenarioCompareResult(List.copyOf(result.scenarios()), List.copyOf(warnings));
    }
}
