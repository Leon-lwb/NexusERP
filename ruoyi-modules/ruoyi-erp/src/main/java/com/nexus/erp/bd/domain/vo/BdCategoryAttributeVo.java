package com.nexus.erp.bd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.bd.domain.BdCategoryAttribute;

import java.io.Serial;
import java.io.Serializable;

/**
 * 物料分类属性模板视图对象 bd_category_attribute
 *
 * @author nexus
 * @date 2026-07-21
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BdCategoryAttribute.class)
public class BdCategoryAttributeVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 属性ID
     */
    @ExcelProperty(value = "属性ID")
    private Long attrId;

    /**
     * 所属分类ID
     */
    @ExcelProperty(value = "所属分类ID")
    private Long categoryId;

    /**
     * 属性编码
     */
    @ExcelProperty(value = "属性编码")
    private String attrCode;

    /**
     * 属性名称
     */
    @ExcelProperty(value = "属性名称")
    private String attrName;

    /**
     * 数据类型 STRING/NUMBER/ENUM/DATE/BOOLEAN
     */
    @ExcelProperty(value = "数据类型")
    private String dataType;

    /**
     * 枚举可选项(逗号分隔,ENUM时用)
     */
    private String enumValues;

    /**
     * 单位(NUMBER时用)
     */
    @ExcelProperty(value = "单位")
    private String unit;

    /**
     * 是否必填 0否1是
     */
    @ExcelProperty(value = "是否必填")
    private Long requiredFlag;

    /**
     * 显示排序
     */
    @ExcelProperty(value = "显示排序")
    private Long sortOrder;

    /**
     * 备注
     */
    private String remark;
}
