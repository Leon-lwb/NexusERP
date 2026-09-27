package com.nexus.common.ai.decision.dto;

import com.nexus.common.ai.decision.enums.DecisionRiskLevel;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record DecisionResult(
    Long runId,
    DecisionBusinessRef businessRef,
    double riskScore,
    DecisionRiskLevel riskLevel,
    String confidenceLevel,
    List<String> rootCauseCodes,
    DecisionKpiSet baselineKpis,
    List<DecisionScenario> scenarios,
    String recommendedScenarioCode,
    Map<String, Object> narrative,
    List<EvidenceItem> evidence,
    List<DecisionActionProposal> actions,
    List<String> warnings
) implements Serializable {
}
