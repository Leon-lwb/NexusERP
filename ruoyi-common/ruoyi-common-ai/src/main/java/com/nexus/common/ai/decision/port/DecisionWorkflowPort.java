package com.nexus.common.ai.decision.port;

import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionWorkflowStartCommand;

public interface DecisionWorkflowPort {
    Long startAnalysis(DecisionWorkflowStartCommand command);

    Long startExecution(DecisionWorkflowStartCommand command);

    boolean stop(Long flowInstanceId, DecisionActor actor);
}
