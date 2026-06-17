package com.nexus.iot.device.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.iot.device.domain.IotProduct;

/**
 * IoT产品业务对象 iot_product
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = IotProduct.class, reverseConvertGenerate = false)
public class IotProductBo extends BaseEntity {

    /**
     * 产品ID
     */
    private Long productId;

    /**
     * 产品编码
     */
    @NotBlank(message = "产品编码不能为空")
    private String productCode;

    /**
     * 产品名称
     */
    @NotBlank(message = "产品名称不能为空")
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
     * 批量删除ID集合
     */
    private Long[] ids;

}
