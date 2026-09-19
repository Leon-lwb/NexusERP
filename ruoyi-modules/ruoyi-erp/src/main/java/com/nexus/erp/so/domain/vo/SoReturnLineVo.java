package com.nexus.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.so.domain.SoReturnLine;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 销售退货行视图对象 so_return_line
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
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

    /** 基本计量单位ID（退货表单按它解析单位名称，缺失会导致表单回写 null） */
    private String baseUomId;

    /** 基本计量单位名称 */
    private String baseUomName;

    /** 退货数量 */
    @ExcelProperty("退货数量")
    private BigDecimal returnQuantity;

    /** 单价 */
    @ExcelProperty("单价")
    private BigDecimal unitPrice;

    /** 金额 */
    @ExcelProperty("金额")
    private BigDecimal amount;

    /** 税率（百分数：13 表示 13%） */
    @ExcelProperty("税率")
    private BigDecimal taxRate;

    /** 税额 */
    @ExcelProperty("税额")
    private BigDecimal taxAmount;

    /** 退货原因 */
    @ExcelProperty("退货原因")
    private String returnReason;

    /** 排序号 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;
}
