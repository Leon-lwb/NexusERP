package com.nexus.common.ai.decision.dto;

import java.io.Serializable;
import java.util.Map;

public record DecisionActionExecutionResult(
    boolean success,
    boolean retryable,
    String resultBizType,
    Long resultBizId,
    String resultBizCode,
    Map<String, Object> result,
    String errorCode,
    String errorMessage
) implements Serializable {
}
