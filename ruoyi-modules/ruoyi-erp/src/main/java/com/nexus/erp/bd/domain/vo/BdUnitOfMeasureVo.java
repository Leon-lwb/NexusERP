package com.nexus.erp.bd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.bd.domain.BdUnitOfMeasure;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 计量单位视图对象 bd_unit_of_measure
 *
 * @author ruoyi
 * @date 2025-09-30
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = BdUnitOfMeasure.class)
public class BdUnitOfMeasureVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 单位ID
     */
    @ExcelProperty(value = "单位ID")
    private Long uomId;

    /**
     * 单位编码
     */
    @ExcelProperty(value = "单位编码")
    private String uomCode;

    /**
     * 单位名称
     */
    @ExcelProperty(value = "单位名称")
    private String uomName;

    /**
     * 单位符号(kg、m、pcs等)
     */
    @ExcelProperty(value = "单位符号(kg、m、pcs等)")
    private String uomSymbol;

    /**
     * 单位类别(重量、长度、数量等)
     */
    @ExcelProperty(value = "单位类别(重量、长度、数量等)")
    private String category;

    /**
     * 是否基本单位 0:否 1:是
     */
    @ExcelProperty(value = "是否基本单位 0:否 1:是")
    private Long isBase;

    /**
     * 基本单位ID
     */
    private Long primaryId;

    /**
     * 基本单位名称
     */
    @ExcelProperty(value = "基本单位名称")
    private String primaryName;

    /**
     * 转换率(相对于基本单位)
     */
    private BigDecimal conversionRate;

    /**
     * 状态 0:停用 1:启用
     */
    @ExcelProperty(value = "状态 0:停用 1:启用")
    private Long status;

    /**
     * 排序
     */
    @ExcelProperty(value = "排序")
    private Long sortOrder;

}
