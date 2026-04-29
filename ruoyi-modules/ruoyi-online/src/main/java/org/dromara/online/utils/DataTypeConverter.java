package org.dromara.online.utils;

import org.dromara.common.core.exception.ServiceException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DataTypeConverter {
    private static final DateTimeFormatter DATETIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static Object convertValue(String columnType, Object value) {
        if (value == null) return null;

        try {
            return switch (columnType.toUpperCase()) {
                case "DATE" ->
                    LocalDateTime.parse(value.toString(), DATE_FORMAT);
                case "DATETIME" ->
                    LocalDateTime.parse(value.toString(), DATETIME_FORMAT);
                case "DECIMAL", "NUMERIC", "DOUBLE" ->
                    new BigDecimal(value.toString().replaceAll(",", ""));
                default -> value;
            };
        } catch (Exception e) {
            throw new ServiceException("字段类型转换失败: " + e.getMessage());
        }
    }
}
