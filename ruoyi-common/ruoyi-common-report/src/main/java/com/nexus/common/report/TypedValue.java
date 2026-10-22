package com.nexus.common.report;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.*;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

// 同名类型说明：本文件同时使用 java.util.Date 和 java.sql.Date，后者保留全限定名以区分。

/**
 * 精确传输值，标识符和十进制数保存为字符串并保留明确类型。
 *
 * @param type 受控类型
 * @param value 当前契约的value配置或状态
 * @param displayValue 随快照冻结的字典名称，仅用于字符串展示
 */
public record TypedValue(
        ValueType type,
        String value,
        @JsonInclude(JsonInclude.Include.NON_NULL) String displayValue) {

    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    /** 字典名称不替换精确原值，空值与非字符串不接受显示覆盖。 */
    public TypedValue {
        if (type != ValueType.STRING || value == null || displayValue == null || displayValue.isBlank())
            displayValue = null;
    }

    /**
     * 兼容历史精确值及旧快照的构造方式。
     *
     * @param type 受控类型
     * @param value 精确原值
     */
    public TypedValue(ValueType type, String value) {
        this(type, value, null);
    }

    /**
     * 读取展示文本，计算、比较与条码继续读取原值。
     *
     * @return 固定显示名称、业务时间或原值，空值返回空文本
     */
    public String displayText() {
        if (type == ValueType.DATETIME && value != null) return displayTime(value);
        return displayValue != null ? displayValue : Objects.toString(value, "");
    }

    /** 时间展示统一到业务时区及秒，历史无偏移时间按业务本地时间读取，异常原文保留。 */
    private static String displayTime(String raw) {
        String normalized = raw.replace(' ', 'T');
        try {
            return OffsetDateTime.parse(normalized).atZoneSameInstant(BUSINESS_ZONE).format(DISPLAY_TIME);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(normalized).format(DISPLAY_TIME);
            } catch (DateTimeParseException invalid) {
                return raw;
            }
        }
    }

    /**
     * 名称变更不改变业务编码的相等语义。
     *
     * @param other 待比较精确值
     * @return 类型和原值是否相等
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof TypedValue typed
                && type == typed.type
                && Objects.equals(value, typed.value);
    }

    /**
     * 按类型和原值生成散列，字典名称不参与分组和联动。
     *
     * @return 业务值散列
     */
    @Override
    public int hashCode() {
        return 31 * Objects.hashCode(type) + Objects.hashCode(value);
    }

    /**
     * 保持历史原值表示，避免新增名称改变已发出的交叉钻取坐标。
     *
     * @return 与原两字段精确值一致的字符串
     */
    @Override
    public String toString() {
        return "TypedValue[type=" + type + ", value=" + value + "]";
    }

    /**
     * 完成 {@code of} 对应的精确类型值处理。
     *
     * @param type 当前操作的 {@code type} 输入
     * @param raw 当前操作的 {@code raw} 输入
     * @return 当前处理返回的固定结果
     */
    public static TypedValue of(ValueType type, Object raw) {
        if (raw instanceof TypedValue typed) {
            if (typed.type() != type) throw new IllegalArgumentException("Typed result mismatch");
            return typed;
        }
        if (raw == null) return new TypedValue(type, null);
        String value =
                switch (type) {
                    case DECIMAL -> new BigDecimal(raw.toString()).toPlainString();
                    case ID, INTEGER -> new BigInteger(raw.toString()).toString();
                    case BOOLEAN -> {
                        String v = raw.toString();
                        if (v.equals("1") || v.equalsIgnoreCase("true")) yield "true";
                        if (v.equals("0") || v.equalsIgnoreCase("false")) yield "false";
                        throw new IllegalArgumentException("Invalid boolean result");
                    }
                    case DATE -> {
                        if (raw instanceof LocalDate date) yield date.toString();
                        if (raw instanceof LocalDateTime time) yield time.toLocalDate().toString();
                        yield raw instanceof java.sql.Date date
                                ? date.toLocalDate().toString()
                                : raw instanceof Date date
                                        ? date.toInstant()
                                                .atZone(ZoneId.of("Asia/Shanghai"))
                                                .toLocalDate()
                                                .toString()
                                        : LocalDate.parse(raw.toString()).toString();
                    }
                    case DATETIME -> {
                        if (raw instanceof LocalDate date)
                            yield date.atStartOfDay(ZoneId.of("Asia/Shanghai"))
                                    .toOffsetDateTime()
                                    .toString();
                        if (raw instanceof LocalDateTime time)
                            yield time.atZone(ZoneId.of("Asia/Shanghai"))
                                    .toOffsetDateTime()
                                    .toString();
                        yield raw instanceof Timestamp time
                                ? time.toLocalDateTime()
                                        .atZone(ZoneId.of("Asia/Shanghai"))
                                        .toOffsetDateTime()
                                        .toString()
                                : raw instanceof Date date
                                        ? date.toInstant().toString()
                                        : raw.toString();
                    }
                    default -> raw.toString();
                };
        return new TypedValue(type, value);
    }
}
