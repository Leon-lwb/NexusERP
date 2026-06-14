package com.nexus.erp.so.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售发票对象 so_invoice
 *
 * @author nexus
 * @date 2026-06-14
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("so_invoice")
public class SoInvoice extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 发票ID
     */
    @TableId(value = "invoice_id")
    private Long invoiceId;

    /**
     * 发票号
     */
    private String invoiceNo;

    /**
     * 发票类型
     */
    private String invoiceType;

    /**
     * 客户ID
     */
    private Long clientId;

    /**
     * 客户名称
     */
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
    private Date invoiceDate;

    /**
     * 币种
     */
    private String currency;

    /**
     * 汇率
     */
    private BigDecimal exchangeRate;

    /**
     * 金额（不含税）
     */
    private BigDecimal amount;

    /**
     * 税额
     */
    private BigDecimal taxAmount;

    /**
     * 价税合计
     */
    private BigDecimal totalAmount;

    /**
     * 已核销金额
     */
    private BigDecimal verifiedAmount;

    /**
     * 发票状态 DRAFT-草稿 ISSUED-已开 VERIFIED-已核销 CANCELLED-已作废
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 扩展字段1
     */
    private String attr1;

    /**
     * 扩展字段2
     */
    private String attr2;

    /**
     * 扩展字段3
     */
    private Long attr3;

    /**
     * 扩展字段4
     */
    private Long attr4;

    /**
     * 发票明细行列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<SoInvoiceLine> soInvoiceLineList;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}
