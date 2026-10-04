package com.nexus.erp.bd.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;

import java.math.BigDecimal;

/**
 * 计量单位业务对象 bd_unit_of_measure
 *
 * @author nexus
 * @date 2025-09-30
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = BdUnitOfMeasure.class, reverseConvertGenerate = false)
public class BdUnitOfMeasureBo extends BaseEntity {

    /**
     * 单位ID
     */
    private Long uomId;

    /**
     * 单位编码
     */
    @NotBlank(message = "单位编码不能为空")
    private String uomCode;

    /**
     * 单位名称
     */
    @NotBlank(message = "单位名称不能为空")
    private String uomName;

    /**
     * 单位符号(kg、m、pcs等)
     */
    private String uomSymbol;

    /**
     * 单位类别(重量、长度、数量等)
     */
    private String category;

    /**
     * 是否基本单位 0:否 1:是
     */
    private Long isBase;

    /**
     * 基本单位ID
     */
    private Long primaryId;

    /**
     * 基本单位名称
     */
    private String primaryName;

    /**
     * 转换率(相对于基本单位)
     */
    private BigDecimal conversionRate;

    /** 数量小数精度；普通编辑只登记事实，不能授予核验资格。 */
    private Integer quantityScale;

    /**
     * 状态 0:停用 1:启用
     */
    private Long status;

    /**
     * 排序
     */
    private Long sortOrder;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;

    /**
     * 排除的单位ID（用于选择基本单位时排除自身）
     */
    private Long excludeId;

}
