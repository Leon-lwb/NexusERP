package com.nexus.ai.service.decision.executor;

import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.core.event.UserNotificationEvent;
import com.nexus.common.core.exception.ServiceException;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

/**
 * 仅发送内部通知，不宣称已联系供应商或创建 Flowable 待办。
 */
abstract class AbstractDecisionNotificationExecutor implements DecisionActionExecutor {
    private final ApplicationEventPublisher publisher;
    protected AbstractDecisionNotificationExecutor(ApplicationEventPublisher publisher) { this.publisher = publisher; }
    @Override public void precheck(DecisionActionProposal action, DecisionActor actor) {
        if (action == null || action.actionType() != type() || action.actionId() == null || actor == null
            || actor.permissions() == null || !actor.permissions().contains(type().permissionCode())
                && !actor.permissions().contains("*:*:*")) throw new ServiceException("通知执行身份无效");
        if (action.payload() == null || action.payload().get("runId") == null)
            throw new ServiceException("通知缺少关联决策运行");
    }
    @Override public DecisionActionExecutionResult execute(DecisionActionProposal action, DecisionActor actor) {
        precheck(action, actor);
        Object target = action.payload().get("targetUserId");
        if (!(target instanceof String id) || !id.matches("[1-9][0-9]{0,18}"))
            throw new ServiceException("内部通知缺少明确的负责人");
        Long userId;
        try { userId = Long.parseLong(id); }
        catch (NumberFormatException error) { throw new ServiceException("内部通知接收人ID无效"); }
        String runId = String.valueOf(action.payload().get("runId"));
        if (!runId.matches("[1-9][0-9]{0,18}")) throw new ServiceException("通知运行ID无效");
        publisher.publishEvent(new UserNotificationEvent(actor.tenantId(), userId, "ai_decision",
            "ai_decision_run", runId, action.title(), action.displayReason(),
            "{\"idempotencyKey\":\"" + action.idempotencyKey() + "\"}",
            "/ai/decision/detail?runId=" + runId));
        return new DecisionActionExecutionResult(true, false, "SYS_NOTIFICATION", null, null,
            Map.of("acceptedInternally", true, "runId", runId), null, null);
    }
    @Override public DecisionActionExecutionResult verify(DecisionActionProposal action,
        DecisionActionExecutionResult result, DecisionActor actor) {
        return result; // 事件已被内部接收，不代表外部送达。
    }
    @Override public DecisionActionExecutionResult compensate(DecisionActionProposal action,
        DecisionActionExecutionResult result, DecisionActor actor) {
        throw new ServiceException("站内通知已发布，不可撤销");
    }
    @Override public boolean isRetryable(Throwable error) { return false; }
}
