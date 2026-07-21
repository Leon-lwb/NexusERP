package com.nexus.erp.bd.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.bd.domain.BdMaterialAttributeValue;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 物料扩展属性值视图对象 bd_material_attribute_value
 *
 * @author nexus
 * @date 2026-07-21
 */
@Data
@AutoMapper(target = BdMaterialAttributeValue.class)
public class BdMaterialAttributeValueVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 值ID
     */
    private Long valueId;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 属性ID
     */
    private Long attrId;

    /**
     * 数据类型(冗余自模板)
     */
    private String dataType;

    /**
     * 数值型值
     */
    private BigDecimal numericValue;

    /**
     * 字符串/枚举值
     */
    private String textValue;

    /**
     * 布尔值 0/1
     */
    private Long boolValue;

    /**
     * 日期值
     */
    private Date dateValue;

    /**
     * 备注
     */
    private String remark;
}
