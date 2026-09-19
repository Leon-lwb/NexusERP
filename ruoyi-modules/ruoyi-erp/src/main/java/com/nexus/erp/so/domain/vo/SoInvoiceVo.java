package com.nexus.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.common.translation.annotation.Translation;
import com.nexus.common.translation.constant.TransConstant;
import com.nexus.erp.so.domain.SoInvoice;
import com.nexus.erp.so.domain.SoInvoiceLine;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

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
     * 发票代码
     */
    @ExcelProperty(value = "发票代码")
    private String invoiceCode;

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

    /** 购买方纳税人识别号 */
    private String buyerTaxId;
    /** 购买方发票抬头 */
    private String buyerTitle;
    /** 购买方开户行 */
    private String buyerBankName;
    /** 购买方银行账号 */
    private String buyerBankAccount;
    /** 购买方地址、电话 */
    private String buyerAddressPhone;

    /**
     * 销售订单ID
     */
    private Long soId;

    /**
     * 合同ID
     */
    private Long contractId;

    /** 红冲原发票ID（NULL=蓝字正数） */
    private Long originalInvoiceId;
    /** 是否红字发票 0-否 1-是 */
    private String redFlag;

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

    /** 开票渠道 MANUAL-手工 ELECTRONIC-电子发票平台 */
    private String issueChannel;
    /** 数电票号码（平台返回） */
    private String eInvoiceNo;
    /** 发票PDF的OSS对象ID */
    private Long pdfOssId;
    /** 发票PDF访问地址（由 pdfOssId 翻译；私有桶返回临时签名URL） */
    @Translation(type = TransConstant.OSS_ID_TO_URL, mapper = "pdfOssId")
    private String pdfUrl;
    /** 发票OFD的OSS对象ID */
    private Long ofdOssId;
    /** 发票OFD访问地址（由 ofdOssId 翻译） */
    @Translation(type = TransConstant.OSS_ID_TO_URL, mapper = "ofdOssId")
    private String ofdUrl;
    /** 乐观锁版本号 */
    private Integer version;

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

    /**
     * 发票明细行列表。
     * <p>用 {@link SoInvoiceLineVo} 而非实体，避免把 tenantId/delFlag/createBy 等审计字段暴露给前端。</p>
     */
    private List<SoInvoiceLineVo> soInvoiceLineList;
}
