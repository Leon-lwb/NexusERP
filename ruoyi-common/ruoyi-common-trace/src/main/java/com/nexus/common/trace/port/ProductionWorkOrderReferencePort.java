package com.nexus.common.trace.port;
/** Read before an ERP authorization write transaction; identifiers alone cannot establish a MES work order. */
public interface ProductionWorkOrderReferencePort {
    void verify(String tenantId,Long mesWoId,Long erpWoId);
}
