package com.nexus.flow.node.industrial;

import com.nexus.ai.service.decision.DecisionActionExecutionService;
import com.nexus.ai.service.decision.DecisionPersistenceService;
import com.nexus.ai.service.decision.DecisionFinalizeService;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.flow.node.FlowExecutionContext;
import org.springframework.stereotype.Component;

import java.util.Map;

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
    @Override public String getNodeType() { return "industrial.executeApprovedActions"; }
    @Override protected Object run(FlowExecutionContext ctx, DecisionActor actor) {
        long runId = support.requireRunId(ctx);
        execution.executeApproved(runId, actor);
        finalizer.finalizeRun(runId, actor);
        return Map.of("runId", String.valueOf(runId),
            "status", persistence.requireRun(runId, actor.tenantId()).getStatus());
    }
}
