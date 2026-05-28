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

/**
 * 计量单位对象 bd_unit_of_measure
 *
 * @author nexus
 * @date 2025-09-30
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("bd_unit_of_measure")
public class BdUnitOfMeasure extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 单位ID
     */
    @TableId(value = "uom_id")
    private Long uomId;

    /**
     * 单位编码
     */
    @ExcelProperty("单位编码")
    private String uomCode;

    /**
     * 单位名称
     */
    @ExcelProperty("单位名称")
    private String uomName;

    /**
     * 单位符号(kg、m、pcs等)
     */
    @ExcelProperty("单位符号(kg、m、pcs等)")
    private String uomSymbol;

    /**
     * 单位类别(重量、长度、数量等)
     */
    @ExcelProperty("单位类别(重量、长度、数量等)")
    private String category;

    /**
     * 是否基本单位 0:否 1:是
     */
    @ExcelProperty("是否基本单位 0:否 1:是")
    private Long isBase;

    /**
     * 基本单位ID
     */
    private Long primaryId;

    /**
     * 基本单位名称
     */
    @ExcelProperty("基本单位名称")
    private String primaryName;

    /**
     * 转换率(相对于基本单位)
     */
    private BigDecimal conversionRate;

    /**
     * 状态 0:停用 1:启用
     */
    @ExcelProperty("状态 0:停用 1:启用")
    private Long status;

    /**
     * 排序
     */
    @ExcelProperty("排序")
    private Long sortOrder;

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
