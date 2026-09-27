package com.nexus.common.ai.decision.dto;

import java.io.Serializable;
import java.math.BigDecimal;

public record DecisionKpiSet(
    int impactedOrderCount,
    int delayedWorkOrderCount,
    int totalTardinessDays,
    int maxTardinessDays,
    String makespanDate,
    int shortageMaterialCount,
    BigDecimal totalShortageGap,
    String earliestShortageDate,
    BigDecimal maxResourceUtilization,
    int overloadedResourceCount,
    int changedWorkOrderCount,
    BigDecimal actionCost
) implements Serializable {
}
