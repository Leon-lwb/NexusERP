package com.nexus.iot.device.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * IoT 设备运维视图对象（在线状态 / 最后上报时间 / 固件版本 / 分组标签）。
 * <p>
 * §4#7 要求"补设备详情/列表的在线状态、最后上报时间、固件版本等运维字段"：
 * 在线状态（{@code connection_status}）、最后在线时间（{@code last_online_time}）、
 * 固件版本（{@code firmware_version}）复用 {@code iot_device} 既有列，**未重复建列**；
 * "最后数据上报时间"由消费端节流写入 Redis（{@code iot:lastreport:{deviceId}}）后在此聚合返回。
 *
 * @author nexus
 */
@Data
public class IotDeviceOpsVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 设备编码(MQTT ClientID)
     */
    private String deviceCode;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 产品ID
     */
    private Long productId;

    /**
     * 产品名称
     */
    private String productName;

    /**
     * 协议类型
     */
    private String protocolType;

    /**
     * 生命周期状态: ACTIVE/INACTIVE/DECOMMISSIONED
     */
    private String status;

    /**
     * 连接状态: ONLINE/OFFLINE（实时值优先取 Redis iot:status）
     */
    private String connectionStatus;

    /**
     * 最后在线时间
     */
    private Date lastOnlineTime;

    /**
     * 最后数据上报时间（Redis iot:lastreport，消费端节流写入）
     */
    private Date lastReportTime;

    /**
     * 固件版本
     */
    private String firmwareVersion;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 位置
     */
    private String location;

    /**
     * 关联MES设备ID
     */
    private Long mesEquipmentId;

    /**
     * 所属分组ID集合（多对多）
     */
    private List<Long> groupIds;

    /**
     * 已绑定标签ID集合（多对多）
     */
    private List<Long> tagIds;
}
