package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmTransferLine;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmTransferLine.class)
public class WmTransferLineVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "行ID")
    private Long lineId;

    private Long transferId;

    @ExcelProperty(value = "物料编码")
    private String materialCode;

    @ExcelProperty(value = "物料名称")
    private String materialName;

    @ExcelProperty(value = "规格型号")
    private String materialSpec;

    @ExcelProperty(value = "批次号")
    private String batchCode;

    @ExcelProperty(value = "来源库位")
    private String fromLocationName;

    @ExcelProperty(value = "目标库位")
    private String toLocationName;

    @ExcelProperty(value = "数量")
    private BigDecimal quantity;

    private String remark;
}
