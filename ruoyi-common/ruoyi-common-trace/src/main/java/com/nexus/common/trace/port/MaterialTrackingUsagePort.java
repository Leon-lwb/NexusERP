package com.nexus.common.trace.port;
/** MES提供在制使用和严格切换就绪检查，避免主数据更改追踪规则割裂既有事实。 */
public interface MaterialTrackingUsagePort {
 boolean hasUnsettledProduction(String tenantId,Long materialId);
 void assertReadyForStrict(String tenantId,Long materialId);
}
