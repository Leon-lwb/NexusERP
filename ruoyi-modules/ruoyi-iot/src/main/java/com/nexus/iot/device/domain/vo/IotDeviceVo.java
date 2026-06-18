package com.nexus.iot.device.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.linpeilie.annotations.AutoMapper;

import lombok.Data;
import com.nexus.iot.device.domain.IotDevice;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * IoT设备视图对象 iot_device
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = IotDevice.class)
public class IotDeviceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 设备ID
     */
    @ExcelProperty(value = "设备ID")
    private Long deviceId;

    /**
     * 设备编码(MQTT ClientID)
     */
    @ExcelProperty(value = "设备编码")
    private String deviceCode;

    /**
     * 设备名称
     */
    @ExcelProperty(value = "设备名称")
    private String deviceName;

    /**
     * 产品ID
     */
    @ExcelProperty(value = "产品ID")
    private Long productId;

    /**
     * 产品名称(冗余)
     */
    @ExcelProperty(value = "产品名称")
    private String productName;

    /**
     * 协议类型: MQTT/OPC_UA/MODBUS/HTTP
     */
    @ExcelProperty(value = "协议类型")
    private String protocolType;

    /**
     * 连接状态: ONLINE/OFFLINE
     */
    @ExcelProperty(value = "连接状态")
    private String connectionStatus;

    /**
     * 最后在线时间
     */
    @ExcelProperty(value = "最后在线时间")
    private Date lastOnlineTime;

    /**
     * 设备密钥(MQTT认证)
     * API 响应中隐藏，防止泄露认证凭证
     */
    @JsonIgnore
    private String deviceSecret;

    /**
     * IP地址
     */
    @ExcelProperty(value = "IP地址")
    private String ipAddress;

    /**
     * 固件版本
     */
    @ExcelProperty(value = "固件版本")
    private String firmwareVersion;

    /**
     * 位置
     */
    @ExcelProperty(value = "位置")
    private String location;

    /**
     * 关联MES设备ID(接口查询,无外键)
     */
    private Long mesEquipmentId;

    /**
     * 状态: ACTIVE/INACTIVE
     */
    @ExcelProperty(value = "状态")
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}
