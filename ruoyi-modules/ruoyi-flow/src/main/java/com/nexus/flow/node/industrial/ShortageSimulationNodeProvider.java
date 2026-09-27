package com.nexus.flow.node.industrial;

import com.nexus.ai.service.decision.DecisionPersistenceService;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.flow.node.FlowExecutionContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ShortageSimulationNodeProvider extends AbstractIndustrialNodeProvider {
    private final DecisionPersistenceService persistence;
    private final IndustrialDecisionDataPort port;
    public ShortageSimulationNodeProvider(DecisionNodeSupport support, DecisionPersistenceService persistence,
                                          IndustrialDecisionDataPort port) {
        super(support); this.persistence = persistence; this.port = port;
    }
    @Override public String getNodeType() { return "industrial.shortageSimulation"; }
    @Override protected Object run(FlowExecutionContext ctx, DecisionActor actor) {
        long id = support.requireRunId(ctx);
        var row = persistence.requireRun(id, actor.tenantId());
        var result = port.simulateShortage(persistence.requireSnapshot(id, actor.tenantId()),
            persistence.requireHorizonDays(row), Map.of(), actor);
        if (result == null) throw new ServiceException("ERP 缺料推演未返回结果");
        return result;
    }
}
