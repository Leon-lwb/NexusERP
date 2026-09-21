package com.nexus.iot.event.domain.bo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * IoT设备事件查询业务对象。
 *
 * @author nexus
 */
@Data
public class IotDeviceEventBo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 事件ID
     */
    private Long eventId;

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 设备编码（模糊）
     */
    private String deviceCode;

    /**
     * 产品ID
     */
    private Long productId;

    /**
     * 事件编码（模糊）
     */
    private String eventCode;

    /**
     * 事件级别
     */
    private String eventLevel;

    /**
     * 处理状态: PENDING/PROCESSED/IGNORED/FAILED
     */
    private String processStatus;

    /**
     * 是否仅看已联动告警的事件
     */
    private Boolean hasAlert;

    /**
     * 事件时间起（yyyy-MM-dd HH:mm:ss）
     */
    private String beginTime;

    /**
     * 事件时间止（yyyy-MM-dd HH:mm:ss）
     */
    private String endTime;
}
