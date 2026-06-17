package com.nexus.common.iot.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * IoT 设备告警 DTO。
 * <p>
 * 跨模块契约对象：IoT 侧发现设备异常时构造此对象，通过 {@code IMesAndonService} 传递给 MES 触发安灯呼叫，
 * 亦可序列化为 SSE 消息推送给前端。字段对齐 IoT 文档 17.2。
 *
 * @author nexus
 */
@Data
public class IotAlert implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * IoT 设备ID
     */
    private Long deviceId;

    /**
     * 设备编码
     */
    private String deviceCode;

    /**
     * 关联 MES 设备ID（可选，无外键，接口查询）
     */
    private Long mesEquipmentId;

    /**
     * 告警类型：TEMPERATURE/VIBRATION/PRESSURE/OTHER
     */
    private String alertType;

    /**
     * 告警级别：HIGH/MEDIUM/LOW
     */
    private String alertLevel;

    /**
     * 告警描述
     */
    private String alertMessage;

    /**
     * 触发值
     */
    private BigDecimal value;

    /**
     * 阈值
     */
    private BigDecimal threshold;

    /**
     * 租户ID
     */
    private String tenantId;

}
