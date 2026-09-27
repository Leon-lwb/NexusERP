package com.nexus.common.ai.decision.dto;

import java.io.Serializable;
import java.util.List;

public record ApsScenarioCompareResult(
    List<DecisionScenario> scenarios,
    List<String> warnings
) implements Serializable {
}
