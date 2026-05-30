package com.nexus.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.so.domain.SoQuotationLine;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 销售报价行视图对象 so_quotation_line
 *
 * @author ruoyi
 */
@Data
@AutoMapper(target = SoQuotationLine.class)
public class SoQuotationLineVo implements Serializable {

    /** 行ID */
    private Long lineId;
    /** 报价ID */
    private Long quotationId;
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
    /** 基本计量单位ID */
    private String baseUomId;
    /** 基本计量单位名称 */
    private String baseUomName;

    /** 数量 */
    @ExcelProperty("数量")
    private BigDecimal quantity;

    /** 单价 */
    @ExcelProperty("单价")
    private BigDecimal unitPrice;

    /** 金额 */
    @ExcelProperty("金额")
    private BigDecimal amount;

    /** 税率 */
    @ExcelProperty("税率")
    private BigDecimal taxRate;

    /** 税额 */
    @ExcelProperty("税额")
    private BigDecimal taxAmount;

    /** 交货日期 */
    @ExcelProperty("交货日期")
    private Date deliveryDate;

    /** 排序号 */
    private Integer sortOrder;
    /** 备注 */
    private String remark;
}
