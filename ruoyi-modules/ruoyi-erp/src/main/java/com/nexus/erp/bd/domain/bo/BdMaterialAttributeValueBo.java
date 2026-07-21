package com.nexus.erp.bd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.bd.domain.BdMaterialAttributeValue;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 物料扩展属性值业务对象 bd_material_attribute_value
 *
 * @author nexus
 * @date 2026-07-21
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BdMaterialAttributeValue.class, reverseConvertGenerate = false)
public class BdMaterialAttributeValueBo extends BaseEntity {

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
