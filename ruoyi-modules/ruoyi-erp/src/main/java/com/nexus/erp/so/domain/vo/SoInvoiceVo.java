package com.nexus.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.so.domain.SoInvoice;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 销售发票视图对象 so_invoice
 *
 * @author nexus
 * @date 2026-06-14
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SoInvoice.class)
public class SoInvoiceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 发票ID
     */
    @ExcelProperty(value = "发票ID")
    private Long invoiceId;

    /**
     * 发票号
     */
    @ExcelProperty(value = "发票号")
    private String invoiceNo;

    /**
     * 发票类型
     */
    @ExcelProperty(value = "发票类型")
    private String invoiceType;

    /**
     * 客户ID
     */
    private Long clientId;

    /**
     * 客户名称
     */
    @ExcelProperty(value = "客户名称")
    private String clientName;

    /**
     * 销售订单ID
     */
    private Long soId;

    /**
     * 合同ID
     */
    private Long contractId;

    /**
     * 开票日期
     */
    @ExcelProperty(value = "开票日期")
    private Date invoiceDate;

    /**
     * 币种
     */
    @ExcelProperty(value = "币种")
    private String currency;

    /**
     * 金额（不含税）
     */
    @ExcelProperty(value = "金额")
    private BigDecimal amount;

    /**
     * 税额
     */
    @ExcelProperty(value = "税额")
    private BigDecimal taxAmount;

    /**
     * 价税合计
     */
    @ExcelProperty(value = "价税合计")
    private BigDecimal totalAmount;

    /**
     * 已核销金额
     */
    @ExcelProperty(value = "已核销金额")
    private BigDecimal verifiedAmount;

    /**
     * 发票状态
     */
    @ExcelProperty(value = "状态")
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}
