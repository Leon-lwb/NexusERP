package com.nexus.common.report;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.stream.Stream;

/** JDBC 日期值转换与既有精确传输契约的回归测试。 */
class TypedValueDateTest {
    @Test
    void localDateTimeConvertsToDate() {
        var raw = LocalDateTime.of(2026, 10, 18, 0, 30, 45, 123456789);

        assertEquals(new TypedValue(ValueType.DATE, "2026-10-18"), TypedValue.of(ValueType.DATE, raw));
    }

    @ParameterizedTest
    @MethodSource("dateValues")
    void dateValuesPreserveBusinessDates(Object raw, String expected) {
        assertEquals(new TypedValue(ValueType.DATE, expected), TypedValue.of(ValueType.DATE, raw));
    }

    static Stream<Arguments> dateValues() {
        return Stream.of(
                Arguments.of(LocalDate.of(2026, 10, 18), "2026-10-18"),
                Arguments.of(LocalDate.of(1970, 1, 1), "1970-01-01"),
                Arguments.of(
                        LocalDateTime.of(2026, 10, 18, 23, 59, 59, 999999999), "2026-10-18"),
                Arguments.of(java.sql.Date.valueOf("2024-02-29"), "2024-02-29"),
                Arguments.of(
                        Timestamp.from(Instant.parse("2026-10-17T15:59:59.999999999Z")),
                        "2026-10-17"),
                Arguments.of(
                        Timestamp.from(Instant.parse("2026-10-17T16:00:00Z")), "2026-10-18"),
                Arguments.of(
                        Date.from(Instant.parse("2026-10-17T15:59:59.999Z")), "2026-10-17"),
                Arguments.of(Date.from(Instant.parse("2026-10-17T16:00:00Z")), "2026-10-18"),
                Arguments.of("2024-02-29", "2024-02-29"));
    }

    @ParameterizedTest
    @MethodSource("dateTimeValues")
    void dateTimeValuesPreserveOffsetsAndPrecision(Object raw, String expected) {
        assertEquals(
                new TypedValue(ValueType.DATETIME, expected), TypedValue.of(ValueType.DATETIME, raw));
    }

    static Stream<Arguments> dateTimeValues() {
        return Stream.of(
                Arguments.of(
                        LocalDateTime.of(2026, 10, 18, 0, 30, 45, 123456789),
                        "2026-10-18T00:30:45.123456789+08:00"),
                Arguments.of(
                        LocalDateTime.of(2026, 10, 18, 0, 0), "2026-10-18T00:00+08:00"),
                Arguments.of(LocalDate.of(2026, 10, 18), "2026-10-18T00:00+08:00"),
                Arguments.of(LocalDate.of(2024, 2, 29), "2024-02-29T00:00+08:00"),
                Arguments.of(
                        Timestamp.valueOf("2026-10-18 00:30:45.123456789"),
                        "2026-10-18T00:30:45.123456789+08:00"),
                Arguments.of(
                        Timestamp.valueOf("2026-10-18 00:00:00"), "2026-10-18T00:00+08:00"),
                Arguments.of(
                        Date.from(Instant.parse("2026-10-17T16:30:45.123Z")),
                        "2026-10-17T16:30:45.123Z"),
                Arguments.of(
                        OffsetDateTime.parse("2026-10-18T00:30:45.123-04:00"),
                        "2026-10-18T00:30:45.123-04:00"),
                Arguments.of("2026-10-18T00:30:45+08:00", "2026-10-18T00:30:45+08:00"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "2026-10-18T00:30:45",
                "2026-10-18 00:30:45.123",
                "2026-10-18T00:30:45+08:00",
                "2026-02-30",
                "18/10/2026",
                "2026-10-18junk",
                "2026-10-18 ",
                ""
            })
    void dateStringsRemainStrict(String raw) {
        assertThrows(DateTimeParseException.class, () -> TypedValue.of(ValueType.DATE, raw));
    }

    @ParameterizedTest
    @EnumSource(ValueType.class)
    void nullValuesKeepTheirDeclaredType(ValueType type) {
        assertEquals(new TypedValue(type, null), TypedValue.of(type, null));
    }

    @ParameterizedTest
    @MethodSource("preciseNumbers")
    void dateSupportPreservesNumericPrecision(ValueType type, Object raw, String expected) {
        assertEquals(new TypedValue(type, expected), TypedValue.of(type, raw));
    }

    static Stream<Arguments> preciseNumbers() {
        return Stream.of(
                Arguments.of(ValueType.ID, Long.MAX_VALUE, "9223372036854775807"),
                Arguments.of(ValueType.INTEGER, Long.MAX_VALUE, "9223372036854775807"),
                Arguments.of(ValueType.INTEGER, Long.MIN_VALUE, "-9223372036854775808"),
                Arguments.of(
                        ValueType.DECIMAL,
                        new BigDecimal("9007199254740993.123400"),
                        "9007199254740993.123400"),
                Arguments.of(
                        ValueType.DECIMAL, new BigDecimal("1E+20"), "100000000000000000000"),
                Arguments.of(ValueType.DECIMAL, Long.MAX_VALUE, "9223372036854775807"));
    }
}
