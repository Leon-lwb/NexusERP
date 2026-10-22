package com.nexus.flow.node.industrial;

import com.nexus.ai.service.decision.DecisionPersistenceService;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.flow.node.FlowExecutionContext;

import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class DeliveryContextNodeProvider extends AbstractIndustrialNodeProvider {
    private final DecisionPersistenceService persistence;
    private final IndustrialDecisionDataPort port;
    public DeliveryContextNodeProvider(DecisionNodeSupport support, DecisionPersistenceService persistence,
                                       IndustrialDecisionDataPort port) {
        super(support); this.persistence = persistence; this.port = port;
    }
    @Override
    public String getNodeType() { return "industrial.deliveryContext"; }
    @Override
    protected Object run(FlowExecutionContext ctx, DecisionActor actor) {
        long id = support.requireRunId(ctx);
        var row = persistence.requireRun(id, actor.tenantId());
        var ref = new DecisionBusinessRef(DecisionBusinessType.valueOf(row.getBizType()), row.getBizId(), row.getBizCode());
        var snapshot = port.loadDeliveryContext(ref, persistence.requireHorizonDays(row), actor);
        if (snapshot == null
                || !ref.type().equals(snapshot.rootBusinessRef().type())
                || !Objects.equals(ref.id(), snapshot.rootBusinessRef().id()))
            throw new ServiceException("ERP 返回的快照不属于本次运行");
        persistence.saveSnapshot(id, snapshot);
        return snapshot;
    }
}
