package com.nexus.common.trace.port;

import com.nexus.common.trace.TrackingPolicy;

/**
 * 读取ERP物料的可信策略快照。
 */
public interface MaterialPolicyPort {
    /**
     * 读取物料当前可信追踪策略；历史单据仍使用各自已经冻结的版本。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param materialId 当前业务行实际物料标识。
     * @return 该实际业务来源对应的追踪策略。
     */
    TrackingPolicy policy(String tenantId, Long materialId);
}
