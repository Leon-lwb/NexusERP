package com.nexus.erp.pu.invoice.domain;

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
 * 采购进项发票对象 pu_invoice
 * <p>镜像 {@code so_invoice},字段对偶:客户↔供应商、so_id↔po_id、购买方↔销售方。
 * 额外含进项认证子状态机(cert_status)。</p>
 *
 * @author nexus
 * @date 2026-07-16
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pu_invoice")
public class PuInvoice extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /** 发票ID */
    @TableId(value = "invoice_id")
    private Long invoiceId;

    /** 发票号 */
    private String invoiceNo;

    /** 发票代码 */
    private String invoiceCode;

    /** 发票类型 VAT_SPECIAL-增值税专用 VAT_NORMAL-增值税普通 OTHER-其他 */
    private String invoiceType;

    /** 供应商ID */
    private Long vendorId;

    /** 供应商名称 */
    private String vendorName;

    /** 销售方纳税人识别号(供应商) */
    private String sellerTaxId;
    /** 销售方发票抬头 */
    private String sellerTitle;
    /** 销售方开户行 */
    private String sellerBankName;
    /** 销售方银行账号 */
    private String sellerBankAccount;
    /** 销售方地址、电话 */
    private String sellerAddressPhone;

    /** 采购订单ID */
    private Long poId;

    /** 采购合同ID */
    private Long contractId;

    /** 红冲原发票ID(NULL=蓝字正数) */
    private Long originalInvoiceId;

    /** 是否红字发票 0-否 1-是 */
    private String redFlag;

    /** 开票日期 */
    private Date invoiceDate;

    /** 币种 */
    private String currency;

    /** 汇率 */
    private BigDecimal exchangeRate;

    /** 金额(不含税) */
    private BigDecimal amount;

    /** 税额 */
    private BigDecimal taxAmount;

    /** 价税合计 */
    private BigDecimal totalAmount;

    /** 已核销(已付款)金额 */
    private BigDecimal verifiedAmount;

    /**
     * 发票状态 DRAFT-草稿 ISSUED-已收 VERIFIED-已核销 CANCELLED-已作废
     * <p>进项发票收票登记无需内部审批流转,故省略 so 的 APPROVING/REJECTED;DRAFT->ISSUED 为"收票确认"。</p>
     */
    private String status;

    /** 认证状态 UNCERTIFIED-未认证 CERTIFIED-已认证 DEDUCTED-已抵扣 TRANSFERRED-已转出 */
    private String certStatus;

    /** 认证日期 */
    private Date certDate;

    /** 可抵扣税额 */
    private BigDecimal deductibleTaxAmount;

    /** 来源渠道 MANUAL-手工录入 ELECTRONIC-电子发票平台同步 */
    private String issueChannel;

    /** 数电票号码(平台返回) */
    private String eInvoiceNo;

    /** 发票PDF的OSS对象ID(私有桶存ossId) */
    private Long pdfOssId;

    /** 发票OFD的OSS对象ID */
    private Long ofdOssId;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    /** 备注 */
    private String remark;

    /** 扩展字段1 */
    private String attr1;
    /** 扩展字段2 */
    private String attr2;
    /** 扩展字段3 */
    private Long attr3;
    /** 扩展字段4 */
    private Long attr4;

    /** 发票明细行列表(非数据库字段) */
    @TableField(exist = false)
    private List<PuInvoiceLine> puInvoiceLineList;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
