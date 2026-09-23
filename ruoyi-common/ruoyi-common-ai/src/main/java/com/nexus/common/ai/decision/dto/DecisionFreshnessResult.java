package com.nexus.common.ai.decision.dto;

import java.io.Serializable;
import java.util.List;

public record DecisionFreshnessResult(
    boolean fresh,
    String currentHash,
    List<String> changedFields
) implements Serializable {
}
