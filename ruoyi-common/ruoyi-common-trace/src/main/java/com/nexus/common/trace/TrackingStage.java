package com.nexus.common.trace;

/**
 * 审计阶段不授权改变库存；严格阶段校验全部身份明细。
 */
public enum TrackingStage {
    /**
     * 兼容审核阶段，保留旧事实并显式核对证据缺口。
     */
    AUDIT,
    /**
     * 严格阶段，追踪物料缺少完整实际身份时拒绝过账。
     */
    STRICT
}
