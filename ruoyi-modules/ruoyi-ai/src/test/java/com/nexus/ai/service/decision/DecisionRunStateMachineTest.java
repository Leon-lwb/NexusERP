package com.nexus.ai.service.decision;

import com.nexus.common.ai.decision.enums.DecisionRunStatus;
import com.nexus.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static com.nexus.common.ai.decision.enums.DecisionRunStatus.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class DecisionRunStateMachineTest {
    private final DecisionRunStateMachine machine = new DecisionRunStateMachine();

    @Test void waitingApprovalCanApproveRejectOrBecomeStaleButCannotSkipExecution() {
        assertTrue(machine.canTransition(WAITING_APPROVAL, APPROVED));
        assertTrue(machine.canTransition(WAITING_APPROVAL, REJECTED));
        assertTrue(machine.canTransition(WAITING_APPROVAL, STALE));
        assertFalse(machine.canTransition(WAITING_APPROVAL, SUCCEEDED));
    }

    @Test void terminalStatesNeverRestartAndTransitionsRejectNulls() {
        for (var terminal : DecisionRunStatus.values()) if (terminal.isTerminal()) {
            for (var candidate : DecisionRunStatus.values()) assertFalse(machine.canTransition(terminal, candidate));
        }
        assertFalse(machine.canTransition(null, RUNNING));
        assertFalse(machine.canTransition(CREATED, null));
        assertThrows(ServiceException.class, () -> machine.requireTransition(SUCCEEDED, RUNNING));
    }

    @Test void allBusinessTransitionsFollowApprovedDAG() {
        assertTrue(machine.canTransition(CREATED, RUNNING));
        assertTrue(machine.canTransition(RUNNING, WAITING_APPROVAL));
        assertTrue(machine.canTransition(APPROVED, EXECUTING));
        assertTrue(machine.canTransition(EXECUTING, ROLLING_BACK));
        assertTrue(machine.canTransition(ROLLING_BACK, ROLLED_BACK));
        assertTrue(machine.canTransition(STALE, SUPERSEDED));
        assertFalse(machine.canTransition(CREATED, EXECUTING));
        assertFalse(machine.canTransition(RUNNING, APPROVED));
    }
}
