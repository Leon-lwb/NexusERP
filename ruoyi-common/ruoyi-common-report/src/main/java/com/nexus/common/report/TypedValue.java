package com.nexus.common.report;
import java.math.*;
import java.time.*;
public record TypedValue(ValueType type, String value) {
    public static TypedValue of(ValueType type, Object raw) {
        if (raw == null) return new TypedValue(type, null);
        String value = switch (type) {
            case DECIMAL -> new BigDecimal(raw.toString()).toPlainString();
            case ID, INTEGER -> new BigInteger(raw.toString()).toString();
            case BOOLEAN -> {
                String v = raw.toString();
                if (v.equals("1") || v.equalsIgnoreCase("true")) yield "true";
                if (v.equals("0") || v.equalsIgnoreCase("false")) yield "false";
                throw new IllegalArgumentException("Invalid boolean result");
            }
            case DATE -> raw instanceof java.sql.Date date ? date.toLocalDate().toString()
                : raw instanceof java.util.Date date ? date.toInstant().atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString() : LocalDate.parse(raw.toString()).toString();
            case DATETIME -> raw instanceof java.sql.Timestamp time ? time.toLocalDateTime().atZone(ZoneId.of("Asia/Shanghai")).toOffsetDateTime().toString()
                : raw instanceof java.util.Date date ? date.toInstant().toString() : raw.toString();
            default -> raw.toString();
        };
        return new TypedValue(type, value);
    }
}
