package com.nexus.erp.so.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
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
     * 发票代码（数电票时代可与号码合并）
     */
    private String invoiceCode;

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
     * 购买方纳税人识别号
     */
    private String buyerTaxId;

    /**
     * 购买方发票抬头
     */
    private String buyerTitle;

    /**
     * 购买方开户行
     */
    private String buyerBankName;

    /**
     * 购买方银行账号
     */
    private String buyerBankAccount;

    /**
     * 购买方地址、电话
     */
    private String buyerAddressPhone;

    /**
     * 销售订单ID
     */
    private Long soId;

    /**
     * 合同ID
     */
    private Long contractId;

    /**
     * 红冲原发票ID（NULL=蓝字正数）
     */
    private Long originalInvoiceId;

    /**
     * 是否红字发票 0-否 1-是
     */
    private String redFlag;

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
     * 发票状态 DRAFT-草稿 APPROVING-审批中 ISSUED-已开 VERIFIED-已核销 REJECTED-已驳回 CANCELLED-已作废
     */
    private String status;

    /**
     * 审批流程ID
     */
    private Long selectFlowId;

    /**
     * 开票渠道 MANUAL-手工 ELECTRONIC-电子发票平台
     */
    private String issueChannel;

    /**
     * 数电票号码（平台返回）
     */
    private String eInvoiceNo;

    /**
     * 发票PDF的OSS对象ID（私有桶存ossId）
     */
    private Long pdfOssId;

    /**
     * 发票OFD的OSS对象ID
     */
    private Long ofdOssId;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

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
