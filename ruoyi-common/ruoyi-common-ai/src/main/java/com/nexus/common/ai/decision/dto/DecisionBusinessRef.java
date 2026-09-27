package com.nexus.common.ai.decision.dto;

import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import java.io.Serializable;

public record DecisionBusinessRef(
    DecisionBusinessType type,
    Long id,
    String code
) implements Serializable {
}
