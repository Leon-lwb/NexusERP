package com.nexus.online.utils;

import com.nexus.common.core.exception.ServiceException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 数据类型转换器
 * <p>将动态表字段的字符串值按列类型转换为对应 Java 类型（日期、日期时间、数值），
 * 用于在线表单数据读写时的类型适配。</p>
 *
 * @author wanghuan
 */
public class DataTypeConverter {
    /** 日期时间格式：yyyy-MM-dd HH:mm:ss。 */
    private static final DateTimeFormatter DATETIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 日期格式：yyyy-MM-dd。 */
    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 按列类型将字符串值转换为对应的 Java 对象。
     *
     * @param columnType 数据库列类型（DATE / DATETIME / DECIMAL / NUMERIC / DOUBLE 等，大小写不敏感）
     * @param value 待转换的原始值
     * @return 转换后的对象；非上述类型时原样返回
     * @throws ServiceException 当类型转换失败时抛出
     */
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
