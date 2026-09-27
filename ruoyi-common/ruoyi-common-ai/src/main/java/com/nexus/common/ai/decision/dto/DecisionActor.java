package com.nexus.common.ai.decision.dto;

import java.io.Serializable;
import java.util.Set;

public record DecisionActor(
    String tenantId,
    Long userId,
    String userName,
    Set<String> permissions
) implements Serializable {
}
