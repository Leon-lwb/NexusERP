package com.nexus.erp.bd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.bd.domain.BdCategoryAttribute;

/**
 * 物料分类属性模板业务对象 bd_category_attribute
 *
 * @author nexus
 * @date 2026-07-21
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BdCategoryAttribute.class, reverseConvertGenerate = false)
public class BdCategoryAttributeBo extends BaseEntity {

    /**
     * 属性ID
     */
    private Long attrId;

    /**
     * 所属分类ID
     */
    private Long categoryId;

    /**
     * 属性编码
     */
    @NotBlank(message = "属性编码不能为空")
    private String attrCode;

    /**
     * 属性名称
     */
    @NotBlank(message = "属性名称不能为空")
    private String attrName;

    /**
     * 数据类型 STRING/NUMBER/ENUM/DATE/BOOLEAN
     */
    @NotBlank(message = "数据类型不能为空")
    private String dataType;

    /**
     * 枚举可选项(逗号分隔,ENUM时用)
     */
    private String enumValues;

    /**
     * 单位(NUMBER时用)
     */
    private String unit;

    /**
     * 是否必填 0否1是
     */
    private Long requiredFlag;

    /**
     * 显示排序
     */
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
     * 批量删除ID集合
     */
    private Long[] ids;
}
