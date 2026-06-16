package com.nexus.erp.bd.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * BOM替代料对象 bd_bom_alternative
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("bd_bom_alternative")
public class BdBomAlternative extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 替代料ID
     */
    @TableId(value = "alternative_id")
    private Long alternativeId;

    /**
     * BOM明细ID
     */
    @ExcelProperty("BOM明细ID")
    private Long detailId;

    /**
     * BOM主表ID
     */
    @ExcelProperty("BOM主表ID")
    private Long bomId;

    /**
     * 替代物料ID
     */
    @ExcelProperty("替代物料ID")
    private Long altMaterialId;

    /**
     * 替代物料编码
     */
    @ExcelProperty("替代物料编码")
    private String altMaterialCode;

    /**
     * 替代物料名称
     */
    @ExcelProperty("替代物料名称")
    private String altMaterialName;

    /**
     * 替代用量
     */
    @ExcelProperty("替代用量")
    private BigDecimal altQuantity;

    /**
     * 优先级
     */
    @ExcelProperty("优先级")
    private Integer priority;

    /**
     * 替代类型: FULL-完全替代, PARTIAL-部分替代, INTERCHANGEABLE-互换
     */
    @ExcelProperty("替代类型")
    private String substitutionType;

    /**
     * 生效日期
     */
    @ExcelProperty("生效日期")
    private LocalDate effectiveDate;

    /**
     * 失效日期
     */
    @ExcelProperty("失效日期")
    private LocalDate expiryDate;

    /**
     * 状态
     */
    @ExcelProperty("状态")
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}
