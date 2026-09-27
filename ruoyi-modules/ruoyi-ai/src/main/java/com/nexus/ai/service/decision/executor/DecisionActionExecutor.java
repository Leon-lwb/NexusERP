package com.nexus.ai.service.decision.executor;

import com.nexus.common.ai.decision.dto.DecisionActionExecutionResult;
import com.nexus.common.ai.decision.dto.DecisionActionProposal;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.enums.DecisionActionType;

public interface DecisionActionExecutor {
    DecisionActionType type();
    void precheck(DecisionActionProposal action, DecisionActor actor);
    DecisionActionExecutionResult execute(DecisionActionProposal action, DecisionActor actor);
    DecisionActionExecutionResult verify(DecisionActionProposal action, DecisionActionExecutionResult result, DecisionActor actor);
    DecisionActionExecutionResult compensate(DecisionActionProposal action, DecisionActionExecutionResult result, DecisionActor actor);
    boolean isRetryable(Throwable error);
}
