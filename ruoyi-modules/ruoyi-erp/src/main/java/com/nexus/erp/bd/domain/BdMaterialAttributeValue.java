package com.nexus.erp.bd.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 物料扩展属性值对象 bd_material_attribute_value
 *
 * @author nexus
 * @date 2026-07-21
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("bd_material_attribute_value")
public class BdMaterialAttributeValue extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 值ID
     */
    @TableId(value = "value_id")
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
     * 删除标志
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;
}
