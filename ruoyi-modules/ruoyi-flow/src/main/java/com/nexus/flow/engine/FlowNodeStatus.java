package com.nexus.flow.engine;

/**
 * 流程节点执行状态
 *
 * @author NexusERP
 */
public enum FlowNodeStatus {

    /** 等待执行 */
    PENDING,

    /** 执行中 */
    RUNNING,

    /** 执行成功 */
    SUCCEEDED,

    /** 执行失败 */
    FAILED,

    /** 跳过（分支未命中/流程失败/停止） */
    SKIPPED
}
