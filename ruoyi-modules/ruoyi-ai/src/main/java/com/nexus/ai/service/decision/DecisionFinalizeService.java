package com.nexus.ai.service.decision;

import com.nexus.ai.service.sse.AiDecisionSseMessageHelper;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.tenant.helper.TenantHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 执行后审计边界，宣布完成前先持久化结果。
 */
@Service
@RequiredArgsConstructor
public class DecisionFinalizeService {
    private final DecisionPersistenceService persistence;
    private final DecisionOutcomeService outcomes;
    private final AiDecisionSseMessageHelper events;

    public void finalizeRun(Long runId, DecisionActor actor) {
        TenantHelper.dynamic(actor.tenantId(), () -> {
            var status = persistence.requireRun(runId, actor.tenantId()).getStatus();
            if (status == null || !java.util.Set.of("SUCCEEDED", "PARTIAL_SUCCESS", "FAILED", "ROLLED_BACK").contains(status))
                return; // 未完成不测量、不写 outcome，也不把待确认断点作为失败关闭。
            var outcome = outcomes.measure(runId, actor);
            persistence.saveOutcome(runId, outcome);
            events.sendOutcome(actor.userId(), runId, outcome);
            events.sendDone(actor.userId(), runId, status);
        });
    }
}
