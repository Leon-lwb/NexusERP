package com.nexus.iot.pipeline.payload;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * MQTT 属性上报消息负载，对齐 IoT 文档 16.3。
 * <p>
 * 示例：{@code {"property":"temperature","value":25.5,"dataType":"NUMERIC","unit":"°C","timestamp":1234567890,"quality":"GOOD"}}
 * <ul>
 *   <li>{@code property} 必填，属性编码；</li>
 *   <li>{@code value} 必填，数值 / 字符串 / 布尔；</li>
 *   <li>{@code dataType} 可选，NUMERIC/TEXT/BOOL，缺失按 value 类型推断；</li>
 *   <li>{@code timestamp} 可选，设备采集时间戳（毫秒），缺失用服务端时间；</li>
 *   <li>{@code quality} 可选，GOOD/BAD/UNCERTAIN，默认 GOOD。</li>
 * </ul>
 *
 * @author nexus
 */
@Data
public class IotPropertyPayload implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 属性编码
     */
    private String property;

    /**
     * 属性值（数值 / 字符串 / 布尔）
     */
    private Object value;

    /**
     * 数据类型：NUMERIC/TEXT/BOOL
     */
    private String dataType;

    /**
     * 单位
     */
    private String unit;

    /**
     * 设备采集时间戳（毫秒）
     */
    private Long timestamp;

    /**
     * 数据质量：GOOD/BAD/UNCERTAIN
     */
    private String quality;

}
