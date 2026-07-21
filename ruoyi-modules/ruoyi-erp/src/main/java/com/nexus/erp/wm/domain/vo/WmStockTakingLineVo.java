package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmStockTakingLine;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 盘点单行视图对象 wm_stock_taking_line
 *
 * @author nexus
 */
@Data
@AutoMapper(target = WmStockTakingLine.class)
public class WmStockTakingLineVo implements Serializable {

    /**
     * 盘点行ID
     */
    @ExcelProperty("盘点行ID")
    private Long lineId;

    /**
     * 盘点计划ID
     */
    @ExcelProperty("盘点计划ID")
    private Long planId;

    /**
     * 物料ID
     */
    @ExcelProperty("物料ID")
    private Long materialId;

    /**
     * 物料编码
     */
    @ExcelProperty("物料编码")
    private String materialCode;

    /**
     * 物料名称
     */
    @ExcelProperty("物料名称")
    private String materialName;

    /**
     * 物料规格
     */
    @ExcelProperty("物料规格")
    private String materialSpec;

    /**
     * 单位ID
     */
    @ExcelProperty("单位ID")
    private String baseUomId;

    /**
     * 单位名称
     */
    @ExcelProperty("单位名称")
    private String baseUomName;

    /**
     * 批次ID
     */
    @ExcelProperty("批次ID")
    private Long batchId;

    /**
     * 批次编码
     */
    @ExcelProperty("批次编码")
    private String batchCode;

    /**
     * 库位ID
     */
    @ExcelProperty("库位ID")
    private Long locationId;

    /**
     * 库位编码
     */
    @ExcelProperty("库位编码")
    private String locationCode;

    /**
     * 库位名称
     */
    @ExcelProperty("库位名称")
    private String locationName;

    /**
     * 库存ID
     */
    @ExcelProperty("库存ID")
    private Long stockId;

    /**
     * 账面数量
     */
    @ExcelProperty("账面数量")
    private BigDecimal bookQuantity;

    /**
     * 实际数量
     */
    @ExcelProperty("实际数量")
    private BigDecimal actualQuantity;

    /**
     * 差异数量
     */
    @ExcelProperty("差异数量")
    private BigDecimal differenceQuantity;

    /**
     * 状态
     */
    @ExcelProperty("状态")
    private String status;

    /**
     * 备注
     */
    @ExcelProperty("备注")
    private String remark;
}
