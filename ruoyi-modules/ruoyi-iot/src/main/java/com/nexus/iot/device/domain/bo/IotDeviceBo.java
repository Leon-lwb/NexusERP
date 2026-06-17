package com.nexus.iot.device.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.iot.device.domain.IotDevice;

/**
 * IoT设备业务对象 iot_device
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = IotDevice.class, reverseConvertGenerate = false)
public class IotDeviceBo extends BaseEntity {

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 设备编码(MQTT ClientID)
     */
    @NotBlank(message = "设备编码不能为空")
    private String deviceCode;

    /**
     * 设备名称
     */
    @NotBlank(message = "设备名称不能为空")
    private String deviceName;

    /**
     * 产品ID
     */
    @NotNull(message = "产品ID不能为空")
    private Long productId;

    /**
     * 协议类型: MQTT/OPC_UA/MODBUS/HTTP
     */
    private String protocolType;

    /**
     * 连接状态: ONLINE/OFFLINE
     */
    private String connectionStatus;

    /**
     * IP地址
     */
    private String ipAddress;

    /**
     * 固件版本
     */
    private String firmwareVersion;

    /**
     * 位置
     */
    private String location;

    /**
     * 关联MES设备ID(接口查询,无外键)
     */
    private Long mesEquipmentId;

    /**
     * 状态: ACTIVE/INACTIVE
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;

}
