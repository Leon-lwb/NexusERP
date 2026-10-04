package com.nexus.flow.node.industrial;

import com.nexus.ai.service.decision.DecisionScenarioRankingService;
import com.nexus.ai.service.decision.DecisionAnalysisAssemblyService;
import com.nexus.ai.service.decision.DecisionPersistenceService;
import com.nexus.ai.service.decision.DecisionSummaryService;
import com.nexus.common.ai.decision.dto.ShortageSimulationResult;
import java.util.List;
import com.nexus.common.ai.decision.dto.ApsScenarioCompareResult;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.flow.node.FlowExecutionContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ScenarioRankNodeProvider extends AbstractIndustrialNodeProvider {
    private final DecisionScenarioRankingService ranking;
    private final DecisionPersistenceService persistence;
    private final DecisionAnalysisAssemblyService analysis;
    private final DecisionSummaryService summaries;
    public ScenarioRankNodeProvider(DecisionNodeSupport support, DecisionScenarioRankingService ranking,
                                    DecisionPersistenceService persistence, DecisionAnalysisAssemblyService analysis,
                                    DecisionSummaryService summaries) {
        super(support); this.ranking = ranking; this.persistence = persistence;
        this.analysis = analysis; this.summaries = summaries;
    }
    @Override public String getNodeType() { return "industrial.scenarioRank"; }
    @Override protected Object run(FlowExecutionContext ctx, DecisionActor actor) {
        var compare = support.read(ctx.getState().get("nodes.aps.output"), ApsScenarioCompareResult.class);
        long id = support.requireRunId(ctx);
        var run = persistence.requireRun(id, actor.tenantId());
        var snapshot = persistence.requireSnapshot(id, actor.tenantId());
        var ranked = ranking.rank(id, snapshot, persistence.requireHorizonDays(run), compare.scenarios());
        var shortage = support.read(ctx.getState().get("nodes.shortage.output"), ShortageSimulationResult.class);
        // 向模型请求文字前，构建相同的确定性风险及带来源证据。
        // modelId 与原始文本为空；本准备阶段不调用模型或持久化动作。
        var facts = analysis.prepare(run, snapshot, shortage, ranked, List.of(), null);
        var prompt = summaries.renderPrompt(new DecisionSummaryService.SummaryInput(facts.runId(),
            facts.businessRef(), facts.riskLevel(), facts.riskScore(), facts.rootCauseCodes(), facts.scenarios(),
            facts.recommendedScenarioCode(), facts.evidence(), List.of(), null, null));
        return Map.of("scenarios", ranked, "summaryPrompt", prompt);
    }
}
