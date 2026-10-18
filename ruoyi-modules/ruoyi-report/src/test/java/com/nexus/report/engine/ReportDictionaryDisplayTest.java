package com.nexus.report.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;

import org.junit.jupiter.api.Test;

/** 验证字典名称随精确值保存，兼容旧快照且不改变编码的比较语义。 */
class ReportDictionaryDisplayTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void frozenDictionaryNameIsUsedForDisplay() throws Exception {
        var value = json.readValue(
                "{\"type\":\"STRING\",\"value\":\"ENTERPRISE\",\"displayValue\":\"企业\"}",
                TypedValue.class);
        assertEquals("企业", ReportCalculator.display(value, 0));
        assertEquals("ENTERPRISE", value.value());
        assertEquals(new TypedValue(ValueType.STRING, "ENTERPRISE"), value);
        assertEquals(new TypedValue(ValueType.STRING, "ENTERPRISE").hashCode(), value.hashCode());
        var restored = json.readValue(json.writeValueAsString(value), TypedValue.class);
        assertEquals("企业", ReportCalculator.display(restored, 0));
        assertEquals("ENTERPRISE", restored.value());
        assertSame(value, TypedValue.of(ValueType.STRING, value));
    }

    @Test
    void historicalValuesAndExactNumbersKeepTheirOriginalShape() throws Exception {
        var value = json.readValue("{\"type\":\"STRING\",\"value\":\"UNKNOWN\"}", TypedValue.class);
        assertEquals("UNKNOWN", ReportCalculator.display(value, 0));
        assertFalse(json.writeValueAsString(value).contains("displayValue"));
        assertEquals("9223372036854775807", TypedValue.of(ValueType.ID, "9223372036854775807").value());
        assertEquals("9007199254740993.01", ReportCalculator.display(TypedValue.of(ValueType.DECIMAL, "9007199254740993.005"), 2));
    }

    @Test
    void absentNamesAndNullValuesDoNotInventLabels() throws Exception {
        var blank = json.readValue("{\"type\":\"STRING\",\"value\":\"UNKNOWN\",\"displayValue\":\"  \"}", TypedValue.class);
        assertEquals("UNKNOWN", ReportCalculator.display(blank, 0));
        var empty = json.readValue("{\"type\":\"STRING\",\"value\":null,\"displayValue\":\"企业\"}", TypedValue.class);
        assertEquals("", ReportCalculator.display(empty, 0));
    }

    /** 历史坐标依赖原两字段字符串，名称不参与原值表示和散列。 */
    @Test
    void historicalValueRepresentationAndHashAreUnaffectedByLabels() {
        var original = new TypedValue(ValueType.STRING, "001");
        var labelled = new TypedValue(ValueType.STRING, "001", "类别名称");
        assertEquals("TypedValue[type=STRING, value=001]", original.toString());
        assertEquals(original.toString(), labelled.toString());
        assertEquals(31 * ValueType.STRING.hashCode() + "001".hashCode(), labelled.hashCode());
    }
}
