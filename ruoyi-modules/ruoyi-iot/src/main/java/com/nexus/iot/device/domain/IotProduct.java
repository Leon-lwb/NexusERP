package com.nexus.iot.device.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

/**
 * IoT产品(设备模板)对象 iot_product
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("iot_product")
public class IotProduct extends TenantEntity {

    /**
     * 产品ID
     */
    @TableId(value = "product_id")
    private Long productId;

    /**
     * 产品编码
     */
    private String productCode;

    /**
     * 产品名称
     */
    private String productName;

    /**
     * 产品类型: DEVICE/GATEWAY
     */
    private String productType;

    /**
     * 协议类型: MQTT/OPC_UA/MODBUS/HTTP
     */
    private String protocolType;

    /**
     * 描述
     */
    private String description;

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
