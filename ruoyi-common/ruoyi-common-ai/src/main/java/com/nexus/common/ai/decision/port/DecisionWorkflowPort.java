package com.nexus.common.ai.decision.port;

import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionWorkflowStartCommand;

public interface DecisionWorkflowPort {
    /**
     * 异步启动前必须验证 Task 12 定义的版本化系统专用有向无环图。
     */
    default void validateAnalysis(DecisionWorkflowStartCommand command) {
        throw new com.nexus.common.core.exception.ServiceException("工业决策分析流程未通过校验");
    }

    Long startAnalysis(DecisionWorkflowStartCommand command);

    Long startExecution(DecisionWorkflowStartCommand command);

    boolean stop(Long flowInstanceId, DecisionActor actor);
}
