package org.dromara.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.wm.domain.WmStockTakingLine;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@AutoMapper(target = WmStockTakingLine.class)
public class WmStockTakingLineVo implements Serializable {

    @ExcelProperty("盘点行ID")
    private Long lineId;

    @ExcelProperty("盘点计划ID")
    private Long planId;

    @ExcelProperty("物料ID")
    private Long materialId;

    @ExcelProperty("物料编码")
    private String materialCode;

    @ExcelProperty("物料名称")
    private String materialName;

    @ExcelProperty("物料规格")
    private String materialSpec;

    @ExcelProperty("单位ID")
    private String baseUomId;

    @ExcelProperty("单位名称")
    private String baseUomName;

    @ExcelProperty("批次ID")
    private Long batchId;

    @ExcelProperty("批次编码")
    private String batchCode;

    @ExcelProperty("库位ID")
    private Long locationId;

    @ExcelProperty("库位编码")
    private String locationCode;

    @ExcelProperty("库位名称")
    private String locationName;

    @ExcelProperty("库存ID")
    private Long stockId;

    @ExcelProperty("账面数量")
    private BigDecimal bookQuantity;

    @ExcelProperty("实际数量")
    private BigDecimal actualQuantity;

    @ExcelProperty("差异数量")
    private BigDecimal differenceQuantity;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("备注")
    private String remark;
}
