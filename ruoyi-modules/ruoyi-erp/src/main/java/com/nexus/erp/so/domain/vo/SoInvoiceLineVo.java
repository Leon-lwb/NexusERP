package com.nexus.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.so.domain.SoInvoiceLine;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 销售发票明细行视图对象 so_invoice_line
 *
 * <p>字段集 = 实体减去审计字段（tenantId/delFlag/createBy/createDept/createTime/updateBy/updateTime），
 * 保留全部业务字段。特别注意 {@code soLineId} 与 {@code contractLineId}：前端不直接使用，
 * 但后端 {@code SoInvoiceServiceImpl.adjustInvoicedQtyOnEdit} 要靠 {@code soLineId}
 * 判断明细是否换绑订单行 —— VO 若丢掉它，编辑自动生成的草稿发票时会被误判为「换绑」，
 * 先释放旧订单行占用、再按 null 不占用，导致 invoiced_qty 归零而发票仍在（重新打开超开缺口）。</p>
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SoInvoiceLine.class)
public class SoInvoiceLineVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 发票行ID */
    private Long lineId;

    /** 发票ID */
    private Long invoiceId;

    /** 行号 */
    @ExcelProperty("行号")
    private String lineNo;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    @ExcelProperty("物料编码")
    private String materialCode;

    /** 物料名称 */
    @ExcelProperty("物料名称")
    private String materialName;

    /** 规格型号 */
    @ExcelProperty("规格型号")
    private String materialSpec;

    /** 数量 */
    @ExcelProperty("数量")
    private BigDecimal quantity;

    /** 单位 */
    @ExcelProperty("单位")
    private String unit;

    /** 单价 */
    @ExcelProperty("单价")
    private BigDecimal unitPrice;

    /** 行金额（不含税） */
    @ExcelProperty("行金额")
    private BigDecimal lineAmount;

    /** 税率（百分数：13 表示 13%） */
    @ExcelProperty("税率")
    private BigDecimal taxRate;

    /** 税额 */
    @ExcelProperty("税额")
    private BigDecimal taxAmount;

    /** 价税合计 */
    @ExcelProperty("价税合计")
    private BigDecimal totalAmount;

    /** 销售订单行ID（回写 invoiced_qty 的关联键，必须保留） */
    private Long soLineId;

    /** 合同行ID */
    private Long contractLineId;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 预留字段1 */
    private String attr1;
    /** 预留字段2 */
    private String attr2;
    /** 预留字段3 */
    private Long attr3;
    /** 预留字段4 */
    private Long attr4;
}
