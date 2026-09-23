package com.nexus.common.ai.decision.dto;

import com.nexus.common.ai.decision.enums.DecisionRunStatus;
import java.io.Serializable;
import java.time.LocalDateTime;

public record DecisionRunAcceptedVo(
    Long runId,
    DecisionRunStatus status,
    String sceneCode,
    LocalDateTime acceptedAt
) implements Serializable {
}
