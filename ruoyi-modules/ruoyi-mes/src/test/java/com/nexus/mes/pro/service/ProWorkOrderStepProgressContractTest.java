package com.nexus.mes.pro.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** 工单步骤独立重建能力必须存在，不能靠重放已入账报工修补投影。 */
class ProWorkOrderStepProgressContractTest {
    @Test
    void progressServiceSupportsIndependentRebuildAndSharedParentLocks() {
        assertDoesNotThrow(() -> {
            Class<?> type = Class.forName("com.nexus.mes.pro.service.ProWorkOrderStepProgressService");
            type.getMethod("rebuild", Long.class);
            type.getMethod("rebuildForCard", Long.class);
            type.getMethod("lockForWorkOrder", Long.class);
            type.getMethod("lockForCard", Long.class);
            type.getMethod("lockForSn", String.class);
        }, "真实写链必须有独立投影服务，重建不等于再次完工");
    }
}
