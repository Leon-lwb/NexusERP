package com.nexus.common.trace.port;

/**
 * MES提供在制使用和严格切换就绪检查，避免主数据更改追踪规则割裂既有事实。
 */
public interface MaterialTrackingUsagePort {
    /**
     * 核对物料是否仍有未结清在制事实，供策略切换守卫使用。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param materialId 当前业务行实际物料标识。
     * @return 存在尚未结清的在制事实时返回真。
     */
    boolean hasUnsettledProduction(String tenantId, Long materialId);

    /**
     * 对库存、在制及身份来源执行就绪核验，结构或证据缺失时拒绝严格切换。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param materialId 当前业务行实际物料标识。
     */
    void assertReadyForStrict(String tenantId, Long materialId);
}
