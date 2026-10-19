package com.nexus.common.trace.port;
import com.nexus.common.trace.TrackingPolicy;
/** 读取ERP物料的可信策略快照。 */
public interface MaterialPolicyPort { TrackingPolicy policy(String tenantId,Long materialId); }
