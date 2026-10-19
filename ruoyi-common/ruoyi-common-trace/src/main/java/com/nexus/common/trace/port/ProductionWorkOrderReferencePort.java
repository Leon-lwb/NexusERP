package com.nexus.common.trace.port;

/**
 * 在库存授权写事务前读取真实制造工单，仅有标识不能替代工单来源证明。
 */
public interface ProductionWorkOrderReferencePort {
    /**
     * 只读核验制造工单与库存原工单的实际对应关系，不能仅凭两个标识建立授权。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param mesWoId MES 生产工单主键。
     * @param erpWoId ERP 生产工单主键。
     */
    void verify(String tenantId, Long mesWoId, Long erpWoId);
}
