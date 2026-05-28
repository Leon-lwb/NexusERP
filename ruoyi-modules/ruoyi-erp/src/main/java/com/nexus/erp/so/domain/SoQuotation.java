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

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("so_quotation")
public class SoQuotation extends TenantEntity {

    @TableId(value = "quotation_id")
    private Long quotationId;

    private String quotationNo;
    private Long clientId;
    private String clientName;
    private Long salesRepId;
    private String salesRepName;
    private Date quotationDate;
    private Date validUntil;
    private String currency;
    private BigDecimal exchangeRate;
    private BigDecimal totalAmount;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal discountRate;
    private String paymentTerms;
    private String deliveryTerms;
    private String status;
    private Long selectFlowId;
    private String remark;
    private String attr1;
    private String attr2;
    private Long attr3;
    private Long attr4;

    @TableField(exist = false)
    private List<SoQuotationLine> soQuotationLineList;

    @TableLogic
    private String delFlag;
}
