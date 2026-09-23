package com.nexus.common.ai.decision.enums;

import java.util.EnumSet;

public enum DecisionRunStatus {
    CREATED, RUNNING, WAITING_APPROVAL, APPROVED, EXECUTING, SUCCEEDED,
    PARTIAL_SUCCESS, FAILED, STALE, SUPERSEDED, REJECTED, ROLLING_BACK,
    ROLLED_BACK, CANCELLED;

    private static final EnumSet<DecisionRunStatus> TERMINAL = EnumSet.of(
        SUCCEEDED, PARTIAL_SUCCESS, FAILED, SUPERSEDED, REJECTED, ROLLED_BACK, CANCELLED
    );

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }
}
