package com.nexus.common.ai.decision.dto;

import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.enums.DecisionRiskLevel;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record DecisionActionProposal(
    Long actionId,
    DecisionActionType actionType,
    String title,
    String displayReason,
    DecisionBusinessRef target,
    Map<String, Object> payload,
    DecisionRiskLevel riskLevel,
    boolean approvalRequired,
    String permissionCode,
    String executorCode,
    int sequence,
    List<Long> dependencyIds
) implements Serializable {
}
