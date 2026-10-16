package com.nexus.common.ai.engineering;

/** 工程任务状态机；所有终态均不可逆，不确定的编辑器写入禁止自动重排。 */
public enum EngineeringTaskState {
    CREATED,
    WAITING_DEVICE,
    RUNNING,
    SUCCEEDED,
    PARTIAL_SUCCESS,
    FAILED,
    CANCELED,
    EXPIRED;

    /** 判断目标状态是否为协议允许的下一状态。 */
    public boolean canTransitionTo(EngineeringTaskState target) {
        if (target == null || target == this) return false;
        return switch (this) {
            case CREATED -> target == WAITING_DEVICE || target == CANCELED || target == FAILED;
            case WAITING_DEVICE -> target == RUNNING || target == CANCELED || target == EXPIRED;
            case RUNNING ->
                    target == SUCCEEDED
                            || target == PARTIAL_SUCCESS
                            || target == FAILED
                            || target == CANCELED;
            default -> false;
        };
    }

    /** 是否已结束，结束任务仅允许读取已确认产物。 */
    public boolean isTerminal() {
        return this != CREATED && this != WAITING_DEVICE && this != RUNNING;
    }
}
