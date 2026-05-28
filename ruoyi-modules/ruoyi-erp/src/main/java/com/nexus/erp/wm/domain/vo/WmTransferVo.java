package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmTransfer;
import com.nexus.erp.wm.domain.WmTransferLine;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmTransfer.class)
public class WmTransferVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "调拨单ID")
    private Long transferId;

    @ExcelProperty(value = "调拨单编号")
    private String transferNo;

    @ExcelProperty(value = "来源仓库")
    private String fromWarehouseName;

    @ExcelProperty(value = "目标仓库")
    private String toWarehouseName;

    @ExcelProperty(value = "调拨类型")
    private String transferType;

    @ExcelProperty(value = "总数量")
    private BigDecimal totalQuantity;

    @ExcelProperty(value = "状态")
    private String status;

    @ExcelProperty(value = "调拨日期")
    private Date transferDate;

    @ExcelProperty(value = "执行日期")
    private Date executeDate;

    private Long selectFlowId;
    private String operatorName;
    private String remark;

    private List<WmTransferLine> wmTransferLineList;
}
