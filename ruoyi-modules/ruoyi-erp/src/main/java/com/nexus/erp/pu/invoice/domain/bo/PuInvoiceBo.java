package com.nexus.erp.pu.invoice.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.pu.invoice.domain.PuInvoice;
import com.nexus.erp.pu.invoice.domain.PuInvoiceLine;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 采购进项发票业务对象 pu_invoice
 * <p>认证状态(cert_status)/已核销金额(verified_amount)/红冲字段由专用端点管理,不在此暴露。</p>
 *
 * @author nexus
 * @date 2026-07-16
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PuInvoice.class, reverseConvertGenerate = false)
public class PuInvoiceBo extends BaseEntity {

    /** 发票ID */
    private Long invoiceId;

    /** 发票号 */
    @NotBlank(message = "发票号不能为空")
    private String invoiceNo;

    /** 发票代码 */
    private String invoiceCode;

    /** 发票类型 */
    private String invoiceType;

    /** 供应商ID */
    private Long vendorId;
    /** 供应商名称 */
    private String vendorName;

    /** 销售方纳税人识别号 */
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

    /** 已核销金额(仅展示;Service 以专用端点维护) */
    private BigDecimal verifiedAmount;

    /** 发票状态 */
    private String status;

    /** 来源渠道 MANUAL-手工 ELECTRONIC-电子发票平台 */
    private String issueChannel;

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

    /** 发票明细行列表 */
    private List<PuInvoiceLine> puInvoiceLineList;

    /** 批量删除ID集合 */
    private Long[] ids;
}
