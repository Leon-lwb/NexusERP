package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmProductionIssueLine;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@AutoMapper(target = WmProductionIssueLine.class)
public class WmProductionIssueLineVo implements Serializable {

    private Long lineId;
    private Long issueId;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private Long materialId;

    @ExcelProperty("物料编码")
    private String materialCode;

    @ExcelProperty("物料名称")
    private String materialName;

    @ExcelProperty("物料规格")
    private String materialSpec;

    private String baseUomId;
    private String baseUomName;
    private Long batchId;
    private String batchCode;
    private Long locationId;
    private String locationCode;
    private String locationName;

    @ExcelProperty("数量")
    private BigDecimal quantity;

    @ExcelProperty("BOM用量")
    private BigDecimal bomQty;

    @ExcelProperty("损耗率")
    private BigDecimal lossRate;

    @ExcelProperty("应领数量")
    private BigDecimal requiredQty;

    @ExcelProperty("已领数量")
    private BigDecimal issuedQty;

    @ExcelProperty("本次领料")
    private BigDecimal currentQty;

    @ExcelProperty("欠领数量")
    private BigDecimal shortQty;

    private Long requirementDetailId;

    @ExcelProperty("行状态")
    private String lineStatus;

    @ExcelProperty("单价")
    private BigDecimal unitPrice;

    @ExcelProperty("金额")
    private BigDecimal amount;

    private String remark;
}
