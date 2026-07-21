package com.nexus.erp.bd.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

/**
 * 物料分类属性模板对象 bd_category_attribute
 *
 * @author nexus
 * @date 2026-07-21
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("bd_category_attribute")
public class BdCategoryAttribute extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 属性ID
     */
    @TableId(value = "attr_id")
    private Long attrId;

    /**
     * 所属分类ID
     */
    @ExcelProperty("所属分类ID")
    private Long categoryId;

    /**
     * 属性编码
     */
    @ExcelProperty("属性编码")
    private String attrCode;

    /**
     * 属性名称
     */
    @ExcelProperty("属性名称")
    private String attrName;

    /**
     * 数据类型 STRING/NUMBER/ENUM/DATE/BOOLEAN
     */
    @ExcelProperty("数据类型")
    private String dataType;

    /**
     * 枚举可选项(逗号分隔,ENUM时用)
     */
    private String enumValues;

    /**
     * 单位(NUMBER时用)
     */
    @ExcelProperty("单位")
    private String unit;

    /**
     * 是否必填 0否1是
     */
    @ExcelProperty("是否必填")
    private Long requiredFlag;

    /**
     * 显示排序
     */
    @ExcelProperty("显示排序")
    private Long sortOrder;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;

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
