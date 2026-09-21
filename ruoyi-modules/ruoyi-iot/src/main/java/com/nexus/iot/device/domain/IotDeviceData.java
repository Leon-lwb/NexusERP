package com.nexus.iot.device.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * IoT设备数据(原始)对象 iot_device_data
 * <p>
 * 高频时序数据，不继承 BaseEntity/TenantEntity：
 * 该表仅含 tenant_id + create_time，无 del_flag 及完整审计列，
 * 继承基类会触发不存在的列自动填充导致 INSERT 报错。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@TableName("iot_device_data")
public class IotDeviceData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 数据ID
     */
    @TableId(value = "data_id")
    private Long dataId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 设备编码(冗余)
     */
    private String deviceCode;

    /**
     * 属性编码
     */
    private String propertyCode;

    /**
     * 属性名称
     */
    private String propertyName;

    /**
     * 数据类型: NUMERIC/TEXT/BOOL
     */
    private String dataType;

    /**
     * 数值型值
     */
    private BigDecimal numericValue;

    /**
     * 文本型值
     */
    private String textValue;

    /**
     * 布尔型值(0/1)
     */
    private Boolean boolValue;

    /**
     * 采集时间(设备侧)
     */
    private Date collectTime;

    /**
     * 接收时间(服务端)
     */
    private Date receiveTime;

    /**
     * 数据质量: GOOD/BAD/UNCERTAIN
     */
    private String quality;

    /**
     * 数据来源: MQTT/OPC_UA/MODBUS/HTTP
     */
    private String source;

    /**
     * 来源消息ID(Redis Stream entry id)
     * <p>
     * 唯一键 {@code uk_source_msg_id}：Stream 重投（XACK 丢失 / 消费进程在落库后 ACK 前被 kill）
     * 时同一 entry id 二次入库会命中唯一键冲突，消费端视为「已处理」直接 ACK，实现幂等去重。
     */
    private String sourceMsgId;

    /**
     * 创建时间
     */
    private Date createTime;

}
