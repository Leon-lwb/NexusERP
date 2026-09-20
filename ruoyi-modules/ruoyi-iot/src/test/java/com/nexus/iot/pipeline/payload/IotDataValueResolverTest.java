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
@Tag("local")
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

    // ---------------- IOT-PIPE-03：data_type 与实际值列一致性 ----------------

    /** 空 dataType 时按字符串内容推断（Stream 链路 value 已被字符串化，不能再依赖 instanceof Number）。 */
    @Test
    void applyValue_blankType_infersNumericFromString() {
        IotDeviceData data = new IotDeviceData();
        String type = resolver.applyValue(data, "25.5", "");
        assertEquals("NUMERIC", type);
        assertEquals("NUMERIC", data.getDataType());
        assertEquals(new BigDecimal("25.5"), data.getNumericValue());
    }

    @Test
    void applyValue_blankType_infersBoolFromString() {
        IotDeviceData data = new IotDeviceData();
        String type = resolver.applyValue(data, "true", null);
        assertEquals("BOOL", type);
        assertEquals("BOOL", data.getDataType());
        assertTrue(data.getBoolValue());
        assertNull(data.getNumericValue());
    }

    @Test
    void applyValue_blankType_infersText() {
        IotDeviceData data = new IotDeviceData();
        String type = resolver.applyValue(data, "SN001", "");
        assertEquals("TEXT", type);
        assertEquals("TEXT", data.getDataType());
        assertEquals("SN001", data.getTextValue());
        assertNull(data.getNumericValue());
    }

    /** 声明 NUMERIC 但非数值 → 落 text_value，data_type 必须纠正为 TEXT（修复前恒记 NUMERIC）。 */
    @Test
    void applyValue_numericDeclaredButTextValue_correctsTypeToText() {
        IotDeviceData data = new IotDeviceData();
        String type = resolver.applyValue(data, "abc", "NUMERIC");
        assertEquals("TEXT", type);
        assertEquals("TEXT", data.getDataType());
        assertNull(data.getNumericValue());
        assertEquals("abc", data.getTextValue());
    }

    @Test
    void applyValue_stringTypeAlias_landsText() {
        IotDeviceData data = new IotDeviceData();
        String type = resolver.applyValue(data, "running", "STRING");
        assertEquals("TEXT", type);
        assertEquals("TEXT", data.getDataType());
        assertEquals("running", data.getTextValue());
    }

    @Test
    void applyValue_boolDeclaredFromString_keepsBool() {
        IotDeviceData data = new IotDeviceData();
        String type = resolver.applyValue(data, "false", "BOOL");
        assertEquals("BOOL", type);
        assertEquals("BOOL", data.getDataType());
        assertFalse(data.getBoolValue());
    }

}
