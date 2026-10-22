package com.nexus.flow.node.industrial;

import com.nexus.ai.service.decision.DecisionActionExecutionService;
import com.nexus.ai.service.decision.DecisionFinalizeService;
import com.nexus.ai.service.decision.DecisionPersistenceService;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.flow.node.FlowExecutionContext;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class DecisionExecuteApprovedActionsNodeProvider extends AbstractIndustrialNodeProvider {
    private final DecisionActionExecutionService execution;
    private final DecisionPersistenceService persistence;
    private final DecisionFinalizeService finalizer;
    public DecisionExecuteApprovedActionsNodeProvider(DecisionNodeSupport support,
        DecisionActionExecutionService execution, DecisionPersistenceService persistence,
        DecisionFinalizeService finalizer) {
        super(support); this.execution = execution; this.persistence = persistence; this.finalizer = finalizer;
    }
    @Override
    public String getNodeType() { return "industrial.executeApprovedActions"; }
    @Override
    protected Object run(FlowExecutionContext ctx, DecisionActor actor) {
        long runId = support.requireRunId(ctx);
        execution.executeApproved(runId, actor);
        var run = persistence.requireRun(runId, actor.tenantId());
        if ("EXECUTING".equals(run.getStatus())) {
            if (Integer.valueOf(2).equals(run.getDecisionContractVersion()))
                throw new DecisionActionExecutionService.PurchaseExecutionPendingException();
            throw new ServiceException("执行尚未收敛，不能宣布完成");
        }
        if (!Set.of("SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "ROLLED_BACK")
                .contains(run.getStatus())) throw new ServiceException("业务运行尚非终态，禁止复盘");
        // 执行服务已持久化复盘时不重复测量、写审计或发送完成事件。
        if (run.getOutcomeJson() == null) finalizer.finalizeRun(runId, actor);
        return Map.of("runId", String.valueOf(runId), "status", run.getStatus());
    }
}
