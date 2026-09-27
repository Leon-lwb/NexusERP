package com.nexus.ai.service.decision.executor;

import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.core.exception.ServiceException;

/**
 * 是否存在草稿及能否安全补偿，仅由 ERP 判定。
 */
abstract class AbstractErpDraftExecutor implements DecisionActionExecutor {
    private final IndustrialDecisionDataPort port;
    protected AbstractErpDraftExecutor(IndustrialDecisionDataPort port) { this.port = port; }
    @Override public void precheck(DecisionActionProposal action, DecisionActor actor) {
        if (action == null || action.actionType() != type() || action.actionId() == null
            || action.actionId() <= 0 || actor == null || actor.permissions() == null
            || !actor.permissions().contains(type().permissionCode()) && !actor.permissions().contains("*:*:*"))
            throw new ServiceException("决策草稿执行人或动作不合法");
    }
    @Override public DecisionActionExecutionResult execute(DecisionActionProposal action, DecisionActor actor) {
        precheck(action, actor);
        return port.executeBusinessAction(action, actor);
    }
    @Override public DecisionActionExecutionResult verify(DecisionActionProposal action,
        DecisionActionExecutionResult result, DecisionActor actor) {
        precheck(action, actor);
        return port.verifyBusinessAction(action, result, actor);
    }
    @Override public DecisionActionExecutionResult compensate(DecisionActionProposal action,
        DecisionActionExecutionResult result, DecisionActor actor) {
        precheck(action, actor);
        if (result == null || !result.success()) throw new ServiceException("没有可补偿的业务草稿");
        return port.compensateBusinessAction(action, result, actor);
    }
    @Override public boolean isRetryable(Throwable error) { return false; }
}
