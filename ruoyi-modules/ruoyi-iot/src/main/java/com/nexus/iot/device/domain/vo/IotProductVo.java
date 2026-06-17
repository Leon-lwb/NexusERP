package com.nexus.iot.device.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.iot.device.domain.IotProduct;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * IoT产品视图对象 iot_product
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = IotProduct.class)
public class IotProductVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 产品ID
     */
    @ExcelProperty(value = "产品ID")
    private Long productId;

    /**
     * 产品编码
     */
    @ExcelProperty(value = "产品编码")
    private String productCode;

    /**
     * 产品名称
     */
    @ExcelProperty(value = "产品名称")
    private String productName;

    /**
     * 产品类型: DEVICE/GATEWAY
     */
    @ExcelProperty(value = "产品类型")
    private String productType;

    /**
     * 协议类型: MQTT/OPC_UA/MODBUS/HTTP
     */
    @ExcelProperty(value = "协议类型")
    private String protocolType;

    /**
     * 描述
     */
    private String description;

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
