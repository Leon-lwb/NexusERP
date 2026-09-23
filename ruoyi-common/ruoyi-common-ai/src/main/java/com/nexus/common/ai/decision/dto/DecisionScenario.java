package com.nexus.common.ai.decision.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DecisionScenario(
    String scenarioCode,
    String scenarioName,
    String strategy,
    boolean feasible,
    List<String> infeasibleReasons,
    DecisionKpiSet kpis,
    BigDecimal objectiveScore,
    int rank,
    Map<String, Object> overrides,
    List<String> evidenceKeys,
    List<Map<String, Object>> schedulePreview
) implements Serializable {
}
