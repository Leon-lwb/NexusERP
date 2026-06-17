package com.nexus.iot.pipeline.payload;

import com.nexus.iot.device.domain.IotDeviceData;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 设备属性值 → {@code iot_device_data} 三值列解析器。
 * <p>
 * 按 {@code dataType} 将 payload 的 {@code value} 落到 {@code numericValue} / {@code textValue} / {@code boolValue} 之一：
 * <ul>
 *   <li>NUMERIC → numericValue（BigDecimal）；</li>
 *   <li>BOOL → boolValue（Boolean）；</li>
 *   <li>TEXT / 未指定 → 优先按 value 实际类型推断，无法推断则落 textValue。</li>
 * </ul>
 *
 * @author nexus
 */
@Component
public class IotDataValueResolver {

    /**
     * 将 payload value 写入实体对应列。
     *
     * @param data   目标实体
     * @param value  原始值
     * @param dataType 数据类型（可为 null）
     */
    public void applyValue(IotDeviceData data, Object value, String dataType) {
        if (value == null) {
            return;
        }
        String type = dataType == null ? "" : dataType.toUpperCase();
        switch (type) {
            case "NUMERIC" -> applyNumeric(data, value);
            case "BOOL" -> applyBool(data, value);
            case "TEXT" -> data.setTextValue(String.valueOf(value));
            default -> inferAndApply(data, value);
        }
    }

    private void applyNumeric(IotDeviceData data, Object value) {
        try {
            data.setNumericValue(new BigDecimal(value.toString()));
        } catch (NumberFormatException e) {
            // 声明 NUMERIC 但无法解析，降级落文本
            data.setTextValue(String.valueOf(value));
        }
    }

    private void applyBool(IotDeviceData data, Object value) {
        if (value instanceof Boolean b) {
            data.setBoolValue(b);
        } else {
            String s = String.valueOf(value);
            data.setBoolValue("true".equalsIgnoreCase(s) || "1".equals(s));
        }
    }

    private void inferAndApply(IotDeviceData data, Object value) {
        if (value instanceof Number n) {
            data.setNumericValue(new BigDecimal(n.toString()));
        } else if (value instanceof Boolean b) {
            data.setBoolValue(b);
        } else {
            data.setTextValue(String.valueOf(value));
        }
    }

}
