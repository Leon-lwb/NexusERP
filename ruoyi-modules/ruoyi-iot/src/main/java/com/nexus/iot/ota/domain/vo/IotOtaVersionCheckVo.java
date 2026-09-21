package com.nexus.iot.ota.domain.vo;

import lombok.Data;

/**
 * 设备版本一致性核对结果（OTA MVP 第 5 项）。
 * <p>
 * 数据来源：{@code iot_device.firmware_version}（设备上报的当前版本，升级成功时由回执回写）
 * 与固件的 {@code version}（目标版本）比对。
 *
 * @author nexus
 */
@Data
public class IotOtaVersionCheckVo {

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 设备编码
     */
    private String deviceCode;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 设备当前版本（{@code iot_device.firmware_version}，可能为空=未知）
     */
    private String currentVersion;

    /**
     * 目标版本（固件 version）
     */
    private String targetVersion;

    /**
     * 版本是否一致（本查询只返回不一致的设备，故恒为 false，保留字段便于前端统一渲染）
     */
    private Boolean match;

    /**
     * 是否**落后**于目标版本（分段数值比较；当前版本为空视为落后）
     */
    private Boolean behind;
}
