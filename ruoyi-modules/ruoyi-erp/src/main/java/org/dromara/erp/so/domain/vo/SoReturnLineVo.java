package org.dromara.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.so.domain.SoReturnLine;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@AutoMapper(target = SoReturnLine.class)
public class SoReturnLineVo implements Serializable {

    private Long lineId;
    private Long returnId;
    private Long soLineId;
    private Long materialId;

    @ExcelProperty("物料编码")
    private String materialCode;

    @ExcelProperty("物料名称")
    private String materialName;

    @ExcelProperty("物料规格")
    private String materialSpec;

    @ExcelProperty("退货数量")
    private BigDecimal returnQuantity;

    @ExcelProperty("单价")
    private BigDecimal unitPrice;

    @ExcelProperty("金额")
    private BigDecimal amount;

    @ExcelProperty("退货原因")
    private String returnReason;

    private String remark;
}
