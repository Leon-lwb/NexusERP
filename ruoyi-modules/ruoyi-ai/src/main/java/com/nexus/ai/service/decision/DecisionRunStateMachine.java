package com.nexus.ai.service.decision;

import com.nexus.common.ai.decision.enums.DecisionRunStatus;
import com.nexus.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;
import static com.nexus.common.ai.decision.enums.DecisionRunStatus.*;

/**
 * 权威状态有向无环图，通过数据库比较更新原子执行允许的状态迁移。
 */
@Component
public class DecisionRunStateMachine {
    private static final Map<DecisionRunStatus, Set<DecisionRunStatus>> EDGES = Map.of(
        CREATED, Set.of(RUNNING, CANCELLED, FAILED),
        RUNNING, Set.of(WAITING_APPROVAL, SUCCEEDED, FAILED, CANCELLED),
        WAITING_APPROVAL, Set.of(APPROVED, REJECTED, STALE, CANCELLED),
        STALE, Set.of(SUPERSEDED, CANCELLED),
        APPROVED, Set.of(EXECUTING, STALE, CANCELLED),
        EXECUTING, Set.of(SUCCEEDED, PARTIAL_SUCCESS, FAILED, ROLLING_BACK),
        ROLLING_BACK, Set.of(ROLLED_BACK, FAILED)
    );

    public boolean canTransition(DecisionRunStatus from, DecisionRunStatus to) {
        return from != null && to != null && EDGES.getOrDefault(from, Set.of()).contains(to);
    }

    public void requireTransition(DecisionRunStatus from, DecisionRunStatus to) {
        if (!canTransition(from, to)) throw new ServiceException("非法决策状态流转: " + from + " -> " + to);
    }
}
