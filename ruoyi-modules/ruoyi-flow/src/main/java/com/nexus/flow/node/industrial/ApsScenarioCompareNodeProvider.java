package com.nexus.flow.node.industrial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.service.decision.ApsWhatIfSimulationService;
import com.nexus.ai.service.decision.DecisionPersistenceService;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.flow.node.FlowExecutionContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ApsScenarioCompareNodeProvider extends AbstractIndustrialNodeProvider {
    private final DecisionPersistenceService persistence;
    private final ApsWhatIfSimulationService simulation;
    private final ObjectMapper mapper;
    public ApsScenarioCompareNodeProvider(DecisionNodeSupport support, DecisionPersistenceService persistence,
                                          ApsWhatIfSimulationService simulation, ObjectMapper mapper) {
        super(support); this.persistence = persistence; this.simulation = simulation; this.mapper = mapper;
    }
    @Override public String getNodeType() { return "industrial.apsCompare"; }
    @Override protected Object run(FlowExecutionContext ctx, DecisionActor actor) {
        long id = support.requireRunId(ctx);
        var row = persistence.requireRun(id, actor.tenantId());
        try {
            var codes = mapper.readTree(row.getScoreConfigJson()).path("scenarioCodes");
            if (!codes.isArray() || codes.isEmpty()) throw new ServiceException("运行未冻结方案范围");
            List<String> selected = new ArrayList<>();
            codes.forEach(code -> selected.add(code.asText()));
            return simulation.compare(persistence.requireSnapshot(id, actor.tenantId()),
                selected, persistence.requireHorizonDays(row), actor);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new ServiceException("无法读取已冻结的方案范围");
        }
    }
}
