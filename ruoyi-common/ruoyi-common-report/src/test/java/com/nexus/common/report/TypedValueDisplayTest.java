package com.nexus.common.report;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 时间仅在展示时格式化，原始值、快照及钻取身份保持稳定。 */
class TypedValueDisplayTest {
    @ParameterizedTest
    @CsvSource({
        "2026-10-17T16:30:45.123456789Z,2026-10-18 00:30:45",
        "2026-10-18T00:00+08:00,2026-10-18 00:00:00",
        "2026-10-18T00:30:45-04:00,2026-10-18 12:30:45",
        "2026-10-18 09:05:06.123,2026-10-18 09:05:06",
        "2026-10-18T09:05,2026-10-18 09:05:00"
    })
    void dateTimesUseBusinessTimezoneAndSeconds(String raw, String expected) {
        var value = new TypedValue(ValueType.DATETIME, raw);
        int hash = value.hashCode();
        assertEquals(expected, value.displayText());
        assertEquals(raw, value.value());
        assertEquals(new TypedValue(ValueType.DATETIME, raw), value);
        assertEquals(hash, value.hashCode());
        assertEquals("TypedValue[type=DATETIME, value=" + raw + "]", value.toString());
        assertSame(value, TypedValue.of(ValueType.DATETIME, value));
    }

    @Test
    void datesNullsInvalidValuesAndUnrelatedTypesRemainSafe() {
        assertEquals("2024-02-29", new TypedValue(ValueType.DATE, "2024-02-29").displayText());
        assertEquals("", new TypedValue(ValueType.DATETIME, null).displayText());
        assertEquals("invalid-time", new TypedValue(ValueType.DATETIME, "invalid-time").displayText());
        assertEquals("2026-02-30T00:00:00Z", new TypedValue(ValueType.DATETIME, "2026-02-30T00:00:00Z").displayText());
        assertEquals("2026-10-17T16:30:45Z", new TypedValue(ValueType.STRING, "2026-10-17T16:30:45Z").displayText());
        assertEquals("9007199254740993.123400", new TypedValue(ValueType.DECIMAL, "9007199254740993.123400").displayText());
        assertEquals("9223372036854775807", new TypedValue(ValueType.ID, "9223372036854775807").displayText());
    }
}
