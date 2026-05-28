package com.nexus.erp.so.domain.bo;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.so.domain.SoQuotation;
import com.nexus.erp.so.domain.SoQuotationLine;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SoQuotation.class)
public class SoQuotationBo extends BaseEntity {

    @NotNull(message = "报价ID不能为空", groups = {EditGroup.class})
    private Long quotationId;

    @NotBlank(message = "报价单号不能为空", groups = {AddGroup.class, EditGroup.class})
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

    private List<SoQuotationLine> soQuotationLineList;
}
