package com.nexus.common.ai.decision.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record DecisionOutcome(
    DecisionKpiSet beforeKpis,
    DecisionKpiSet projectedAfterKpis,
    DecisionKpiSet actualAfterKpis,
    Map<String, Object> delta,
    List<Long> effectiveActionIds,
    List<Long> pendingActionIds,
    LocalDateTime measuredAt,
    String outcomeConfidence
) implements Serializable {
}
