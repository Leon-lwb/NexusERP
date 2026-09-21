package com.nexus.iot.pipeline.payload;

import com.nexus.iot.device.domain.IotDeviceData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 设备属性值 → {@code iot_device_data} 三值列解析器。
 * <p>
 * 按 {@code dataType} 将 payload 的 {@code value} 落到 {@code numericValue} / {@code textValue} / {@code boolValue} 之一，
 * 并返回**实际生效**的数据类型，由调用方回写 {@code data_type} 列，保证列语义与实际值列一致（IOT-PIPE-03）：
 * <ul>
 *   <li>NUMERIC → numericValue；解析失败降级落 textValue，生效类型纠正为 TEXT；</li>
 *   <li>BOOL → boolValue（"true"/"1" 视为 true）；</li>
 *   <li>TEXT / STRING → textValue；</li>
 *   <li>未指定 / 其他 → 按值内容推断：{@code true|false} → BOOL，可解析为数字 → NUMERIC，其余 → TEXT。</li>
 * </ul>
 * 注意：Redis Stream 中的 value 已被生产端字符串化，故推断**必须基于字符串内容**，
 * 不能依赖 {@code value instanceof Number}（该分支在真实链路上永不成立，属历史死代码）。
 *
 * @author nexus
 */
@Component
public class IotDataValueResolver {

    /** 数值类型。 */
    public static final String TYPE_NUMERIC = "NUMERIC";
    /** 布尔类型。 */
    public static final String TYPE_BOOL = "BOOL";
    /** 文本类型。 */
    public static final String TYPE_TEXT = "TEXT";

    /**
     * 将 payload value 写入实体对应列，并返回实际生效的数据类型。
     *
     * @param data     目标实体
     * @param value    原始值（Stream 链路中为字符串）
     * @param dataType 声明的数据类型（可为 null / 空，表示未指定）
     * @return 实际生效的数据类型（NUMERIC / BOOL / TEXT）；value 为 null 时原样返回 dataType
     */
    public String applyValue(IotDeviceData data, Object value, String dataType) {
        if (value == null) {
            return dataType;
        }
        String type = dataType == null ? "" : dataType.trim().toUpperCase();
        String effective = switch (type) {
            case TYPE_NUMERIC -> applyNumeric(data, value);
            case TYPE_BOOL -> applyBool(data, value);
            case TYPE_TEXT, "STRING" -> applyText(data, value);
            default -> inferAndApply(data, value);
        };
        // 回写实际生效类型，保证 data_type 列语义与实际值列一致（IOT-PIPE-03）
        data.setDataType(effective);
        return effective;
    }

    /**
     * 按声明 NUMERIC 落 numericValue；无法解析时降级落 textValue 并把生效类型纠正为 TEXT。
     */
    private String applyNumeric(IotDeviceData data, Object value) {
        try {
            data.setNumericValue(new BigDecimal(String.valueOf(value).trim()));
            return TYPE_NUMERIC;
        } catch (NumberFormatException e) {
            // 声明 NUMERIC 但无法解析：落文本列，同时把 data_type 纠正为 TEXT，避免列语义与数据不符
            data.setTextValue(String.valueOf(value));
            return TYPE_TEXT;
        }
    }

    /**
     * 按声明 BOOL 落 boolValue（兼容 "true"/"1" 字符串）。
     */
    private String applyBool(IotDeviceData data, Object value) {
        if (value instanceof Boolean b) {
            data.setBoolValue(b);
        } else {
            String s = String.valueOf(value).trim();
            data.setBoolValue("true".equalsIgnoreCase(s) || "1".equals(s));
        }
        return TYPE_BOOL;
    }

    /**
     * 按声明 TEXT/STRING 落 textValue。
     */
    private String applyText(IotDeviceData data, Object value) {
        data.setTextValue(String.valueOf(value));
        return TYPE_TEXT;
    }

    /**
     * 未声明类型时按值内容推断类型并落对应列，返回推断出的生效类型。
     */
    private String inferAndApply(IotDeviceData data, Object value) {
        if (value instanceof Boolean b) {
            data.setBoolValue(b);
            return TYPE_BOOL;
        }
        String s = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) {
            data.setBoolValue(Boolean.parseBoolean(s));
            return TYPE_BOOL;
        }
        try {
            data.setNumericValue(new BigDecimal(s));
            return TYPE_NUMERIC;
        } catch (NumberFormatException ignored) {
            data.setTextValue(String.valueOf(value));
            return TYPE_TEXT;
        }
    }

}
