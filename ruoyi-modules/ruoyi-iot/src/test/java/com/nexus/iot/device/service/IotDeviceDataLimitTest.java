package com.nexus.iot.device.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 设备历史数据查询条数归一化单元测试（IOT-DATA-01）。
 * <p>
 * LIMIT 必须下推到 DB，禁止全量载入内存后截断；此处校验归一化规则。
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class IotDeviceDataLimitTest {

    @Test
    void normalizeLimit_nullOrNonPositive_fallsBackToDefault() {
        assertEquals(1000, IIotDeviceDataService.normalizeLimit(null));
        assertEquals(1000, IIotDeviceDataService.normalizeLimit(0));
        assertEquals(1000, IIotDeviceDataService.normalizeLimit(-5));
    }

    @Test
    void normalizeLimit_withinRange_kept() {
        assertEquals(500, IIotDeviceDataService.normalizeLimit(500));
        assertEquals(1, IIotDeviceDataService.normalizeLimit(1));
    }

    @Test
    void normalizeLimit_overMax_clampedToMax() {
        assertEquals(1000, IIotDeviceDataService.normalizeLimit(100000));
        assertEquals(1000, IIotDeviceDataService.MAX_HISTORY_LIMIT);
    }

}
