package com.nexus.common.trace.port;

import com.nexus.common.trace.WorkOrderTrackingPolicySnapshot;

/**
 * 提供不可变的库存工单下发证据，历史读取不回退到当前物料策略。
 */
public interface WorkOrderTrackingPolicyPort {
    /**
     * 读取真实库存工单下发时的策略原档，缺失时返回明确缺口而不回退到现行物料配置。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param erpWoId ERP 生产工单主键。
     * @param materialId 当前业务行实际物料标识。
     * @return 原工单物料策略快照及来源证据状态。
     */
    WorkOrderTrackingPolicySnapshot find(String tenantId, Long erpWoId, Long materialId);
}
