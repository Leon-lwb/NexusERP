package com.nexus.iot.pipeline.payload;

import com.nexus.iot.device.domain.IotDeviceData;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 设备属性值解析器单元测试。
 *
 * @author nexus
 */
@Tag("dev")
class IotDataValueResolverTest {

    private final IotDataValueResolver resolver = new IotDataValueResolver();

    @Test
    void applyValue_numeric() {
        IotDeviceData data = new IotDeviceData();
        resolver.applyValue(data, 25.5, "NUMERIC");
        assertEquals(new BigDecimal("25.5"), data.getNumericValue());
        assertNull(data.getTextValue());
        assertNull(data.getBoolValue());
    }

    @Test
    void applyValue_numericFromString() {
        IotDeviceData data = new IotDeviceData();
        resolver.applyValue(data, "85.0", "NUMERIC");
        assertEquals(new BigDecimal("85.0"), data.getNumericValue());
    }

    @Test
    void applyValue_numericUnparseable_fallsBackToText() {
        IotDeviceData data = new IotDeviceData();
        resolver.applyValue(data, "abc", "NUMERIC");
        assertNull(data.getNumericValue());
        assertEquals("abc", data.getTextValue());
    }

    @Test
    void applyValue_bool() {
        IotDeviceData data = new IotDeviceData();
        resolver.applyValue(data, true, "BOOL");
        assertTrue(data.getBoolValue());
    }

    @Test
    void applyValue_boolFromString() {
        IotDeviceData data = new IotDeviceData();
        resolver.applyValue(data, "1", "BOOL");
        assertTrue(data.getBoolValue());
    }

    @Test
    void applyValue_text() {
        IotDeviceData data = new IotDeviceData();
        resolver.applyValue(data, "running", "TEXT");
        assertEquals("running", data.getTextValue());
    }

    @Test
    void applyValue_inferNumericWhenTypeMissing() {
        IotDeviceData data = new IotDeviceData();
        resolver.applyValue(data, 100, null);
        assertEquals(new BigDecimal("100"), data.getNumericValue());
    }

    @Test
    void applyValue_nullValue_noop() {
        IotDeviceData data = new IotDeviceData();
        resolver.applyValue(data, null, "NUMERIC");
        assertNull(data.getNumericValue());
        assertNull(data.getTextValue());
        assertNull(data.getBoolValue());
    }

}
