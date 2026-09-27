package com.nexus.ai.service.decision;

import com.nexus.common.ai.domain.entity.AiDecisionAction;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionApprovalRequest;
import com.nexus.common.ai.decision.dto.DecisionKpiSet;
import com.nexus.common.ai.decision.dto.DecisionScenario;
import com.nexus.common.ai.decision.dto.DeliveryRiskRunRequest;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.enums.DecisionExecutionStatus;
import com.nexus.common.ai.decision.enums.DecisionRunStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DecisionTestFixtures {
    private DecisionTestFixtures() { }

    static DecisionActor actor() {
        return new DecisionActor("000000", 1L, "admin",
            Set.of("ai:decision:run", "ai:decision:approve", "ai:decision:execute",
                "pu:requisition:add", "prd:schedule:run"));
    }

    static DeliveryRiskRunRequest request(boolean forceRestart) {
        DeliveryRiskRunRequest request = new DeliveryRiskRunRequest();
        request.setBizType(DecisionBusinessType.SCHEDULE_PLAN);
        request.setBizId(9001L);
        request.setHorizonDays(30);
        request.setForceRestart(forceRestart);
        return request;
    }

    static DecisionKpiSet kpis(int delayed, int tardiness, int changed) {
        return new DecisionKpiSet(1, delayed, tardiness, tardiness,
            "2026-10-01", 0, BigDecimal.ZERO, null,
            BigDecimal.valueOf(80), 0, changed, BigDecimal.ZERO);
    }

    static DecisionScenario scenario(String code, boolean feasible, DecisionKpiSet kpis, double score) {
        return new DecisionScenario(code, code, code.replace("APS_", ""), feasible,
            feasible ? List.of() : List.of("不可行"), kpis, BigDecimal.valueOf(score), 0,
            Map.of(), List.of(), List.of(), "sha256-input", List.of(), List.of());
    }

    static AiDecisionRun run(long id, DecisionRunStatus status) {
        AiDecisionRun run = new AiDecisionRun();
        run.setId(id);
        run.setTenantId("000000");
        run.setStatus(status.name());
        run.setVersion(0);
        return run;
    }

    static AiDecisionRun waitingRun(String hash) {
        AiDecisionRun run = run(10L, DecisionRunStatus.WAITING_APPROVAL);
        run.setInputSnapshotHash(hash);
        return run;
    }

    static AiDecisionAction succeededAction(long id) {
        AiDecisionAction action = new AiDecisionAction();
        action.setId(id);
        action.setRunId(10L);
        action.setExecutionStatus(DecisionExecutionStatus.SUCCEEDED.name());
        action.setVersion(0);
        return action;
    }

    static DecisionApprovalRequest approvalRequest() {
        DecisionApprovalRequest request = new DecisionApprovalRequest();
        request.setRunVersion(0);
        request.setApprovedActionIds(List.of(20L));
        request.setRejectedActionIds(List.of());
        request.setComment("批准竞赛演示动作");
        request.setExpectedSnapshotHash("hash-old");
        return request;
    }
}
