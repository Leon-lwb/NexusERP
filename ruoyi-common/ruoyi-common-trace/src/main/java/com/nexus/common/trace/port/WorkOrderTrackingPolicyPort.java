package com.nexus.common.trace.port;

import com.nexus.common.trace.WorkOrderTrackingPolicySnapshot;

/** Immutable ERP work-order evidence; implementations never fall back to current material policy. */
public interface WorkOrderTrackingPolicyPort {
    WorkOrderTrackingPolicySnapshot find(String tenantId, Long erpWoId, Long materialId);
}
