package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmProductionRecpt;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@AutoMapper(target = WmProductionRecpt.class)
public class WmProductionRecptVo implements Serializable {

    @ExcelProperty("生产入库ID")
    private Long recptId;

    @ExcelProperty("入库单号")
    private String recptCode;

    @ExcelProperty("工单ID")
    private Long woId;

    @ExcelProperty("工单编号")
    private String woNo;

    @ExcelProperty("物料ID")
    private Long materialId;

    @ExcelProperty("物料编码")
    private String materialCode;

    @ExcelProperty("物料名称")
    private String materialName;

    @ExcelProperty("规格")
    private String materialSpec;

    @ExcelProperty("入库数量")
    private BigDecimal quantity;

    @ExcelProperty("仓库ID")
    private Long warehouseId;

    @ExcelProperty("仓库编码")
    private String warehouseCode;

    @ExcelProperty("仓库名称")
    private String warehouseName;

    @ExcelProperty("库位ID")
    private Long locationId;

    @ExcelProperty("库位编码")
    private String locationCode;

    @ExcelProperty("库位名称")
    private String locationName;

    @ExcelProperty("入库日期")
    private Date recptDate;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("备注")
    private String remark;
}
