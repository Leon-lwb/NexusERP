package com.nexus.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.so.domain.SoReturnLine;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 销售退货行视图对象 so_return_line
 *
 * @author ruoyi
 */
@Data
@AutoMapper(target = SoReturnLine.class)
public class SoReturnLineVo implements Serializable {

    /** 行ID */
    private Long lineId;
    /** 退货ID */
    private Long returnId;
    /** 销售订单行ID */
    private Long soLineId;
    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    @ExcelProperty("物料编码")
    private String materialCode;

    /** 物料名称 */
    @ExcelProperty("物料名称")
    private String materialName;

    /** 物料规格 */
    @ExcelProperty("物料规格")
    private String materialSpec;

    /** 退货数量 */
    @ExcelProperty("退货数量")
    private BigDecimal returnQuantity;

    /** 单价 */
    @ExcelProperty("单价")
    private BigDecimal unitPrice;

    /** 金额 */
    @ExcelProperty("金额")
    private BigDecimal amount;

    /** 退货原因 */
    @ExcelProperty("退货原因")
    private String returnReason;

    /** 备注 */
    private String remark;
}
