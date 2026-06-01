package com.nexus.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.so.domain.SoPriceListLine;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 销售价格表行视图对象
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SoPriceListLine.class)
public class SoPriceListLineVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 行ID */
    private Long lineId;

    /** 价格表ID */
    @ExcelProperty(value = "价格表ID")
    private Long priceListId;

    /** 物料ID */
    @ExcelProperty(value = "物料ID")
    private Long materialId;

    /** 单价 */
    @ExcelProperty(value = "单价")
    private BigDecimal unitPrice;

    /** 最小数量 */
    @ExcelProperty(value = "最小数量")
    private BigDecimal minQty;

    /** 最大数量 */
    @ExcelProperty(value = "最大数量")
    private BigDecimal maxQty;

    /** 折扣率 */
    @ExcelProperty(value = "折扣率")
    private BigDecimal discountRate;
}
