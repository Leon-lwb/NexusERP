package com.nexus.iot.device.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * IoT设备实例对象 iot_device
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("iot_device")
public class IotDevice extends TenantEntity {

    /**
     * 设备ID
     */
    @TableId(value = "device_id")
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
     * 产品名称(冗余)
     */
    private String productName;

    /**
     * 协议类型: MQTT/OPC_UA/MODBUS/HTTP
     */
    private String protocolType;

    /**
     * 连接状态: ONLINE/OFFLINE
     */
    private String connectionStatus;

    /**
     * 最后在线时间
     */
    private Date lastOnlineTime;

    /**
     * 设备密钥(MQTT认证)
     */
    private String deviceSecret;

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
     * 删除标志(0正常 1删除)
     */
    @TableLogic
    private String delFlag;

}
