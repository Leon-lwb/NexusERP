package com.nexus.flow.node.industrial;

import com.nexus.ai.service.decision.DecisionPersistenceService;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.flow.node.FlowExecutionContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BaselineRiskNodeProvider extends AbstractIndustrialNodeProvider {
    private final DecisionPersistenceService persistence;
    public BaselineRiskNodeProvider(DecisionNodeSupport support, DecisionPersistenceService persistence) {
        super(support); this.persistence = persistence;
    }
    @Override public String getNodeType() { return "industrial.baselineRisk"; }
    @Override protected Object run(FlowExecutionContext ctx, DecisionActor actor) {
        var snapshot = persistence.requireSnapshot(support.requireRunId(ctx), actor.tenantId());
        // ERP 缺料与 APS 完成后，再计算最终综合风险。
        return Map.of("snapshotHash", snapshot.snapshotHash(), "scheduleKnown",
            snapshot.currentSchedule() != null && !snapshot.currentSchedule().isEmpty(),
            "capacityKnown", snapshot.capacityByDate() != null && !snapshot.capacityByDate().isEmpty());
    }
}
