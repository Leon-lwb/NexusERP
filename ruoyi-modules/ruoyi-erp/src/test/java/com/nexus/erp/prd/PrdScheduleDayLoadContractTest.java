package com.nexus.erp.prd;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Tag("dev")
class PrdScheduleDayLoadContractTest {
    @Test
    void scheduleDayLoadHasTenantScopedEntityAndPersistenceMapper() {
        assertDoesNotThrow(() -> Class.forName("com.nexus.erp.prd.domain.PrdScheduleTaskDayLoad"),
            "new schedules need a persisted tenant-scoped per-day source record");
        assertDoesNotThrow(() -> Class.forName("com.nexus.erp.prd.mapper.PrdScheduleTaskDayLoadMapper"),
            "day-load rows need a dedicated MyBatis persistence path");
    }
}
