package com.nexus.common.ai.decision.dto;

import java.io.Serializable;
import java.util.Map;

public record DecisionWorkflowStartCommand(
    Long runId,
    String flowKey,
    String tenantId,
    Long userId,
    String userName,
    Map<String, Object> input
) implements Serializable {
}
