package com.nexus.common.flow.execution;

/** 不明外部结果默认禁止自动重放。 */
public enum FlowReplayPolicy {
    SAFE,
    IDEMPOTENT,
    NON_REPLAYABLE
}
