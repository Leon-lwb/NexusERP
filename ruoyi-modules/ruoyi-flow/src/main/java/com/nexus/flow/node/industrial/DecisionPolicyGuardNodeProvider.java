package com.nexus.flow.node.industrial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.service.decision.DecisionAnalysisAssemblyService;
import com.nexus.ai.service.decision.DecisionPersistenceService;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.flow.node.FlowExecutionContext;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * 风险计算与摘要均由服务端控制，模型输出仅视作不可信文本。
 */
@Component
public class DecisionPolicyGuardNodeProvider extends AbstractIndustrialNodeProvider {
    private final DecisionPersistenceService persistence;
    private final DecisionAnalysisAssemblyService analysis;
    private final ObjectMapper mapper;
    public DecisionPolicyGuardNodeProvider(DecisionNodeSupport support, DecisionPersistenceService persistence,
                                           DecisionAnalysisAssemblyService analysis, ObjectMapper mapper) {
        super(support); this.persistence = persistence; this.analysis = analysis; this.mapper = mapper;
    }
    @Override
    public String getNodeType() { return "industrial.decisionGuard"; }
    @Override
    protected Object run(FlowExecutionContext ctx, DecisionActor actor) {
        long id = support.requireRunId(ctx);
        var row = persistence.requireRun(id, actor.tenantId());
        var shortage = support.read(ctx.getState().get("nodes.shortage.output"), ShortageSimulationResult.class);
        JsonNode ranked = ctx.getState().get("nodes.rank.output.scenarios");
        if (ranked == null || !ranked.isArray() || ranked.isEmpty()) throw new ServiceException("缺少可信方案排名");
        List<DecisionScenario> scenarios;
        try { scenarios = mapper.readerForListOf(DecisionScenario.class).readValue(ranked);
        } catch (IOException badInput) {
            throw new ServiceException("方案排名无法解码");
        }
        List<RetrievalHit> hits = List.of();
        if (ctx.getGraph() != null && ctx.getGraph().getNodes().containsKey("rag")) {
            try {
                JsonNode hitNode = ctx.getState().get("nodes.rag.output.hits");
                if (hitNode != null && hitNode.isArray()) hits = mapper.readerForListOf(RetrievalHit.class).readValue(hitNode);
            } catch (Exception ignored) { /* 可选检索内容不得作为数值事实。 */ }
        }
        String llm = null;
        if (ctx.getGraph() != null && ctx.getGraph().getNodes().containsKey("llm")) {
            try {
                JsonNode text = ctx.getState().get("nodes.llm.output.content");
                if (text != null && text.isTextual()) llm = text.asText();
            } catch (IllegalArgumentException ignored) { /* 使用服务端模板。 */ }
        }
        return analysis.prepare(row, persistence.requireSnapshot(id, actor.tenantId()),
            shortage, scenarios, hits, llm);
    }
}
