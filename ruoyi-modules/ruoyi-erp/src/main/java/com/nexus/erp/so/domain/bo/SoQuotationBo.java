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

/**
 * 销售报价业务对象 so_quotation
 *
 * @author ruoyi
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SoQuotation.class)
public class SoQuotationBo extends BaseEntity {

    /** 报价ID */
    @NotNull(message = "报价ID不能为空", groups = {EditGroup.class})
    private Long quotationId;

    /** 报价单号 */
    @NotBlank(message = "报价单号不能为空", groups = {AddGroup.class, EditGroup.class})
    private String quotationNo;

    /** 客户ID */
    private Long clientId;
    /** 客户名称 */
    private String clientName;
    /** 销售代表ID */
    private Long salesRepId;
    /** 销售代表名称 */
    private String salesRepName;
    /** 报价日期 */
    private Date quotationDate;
    /** 有效期至 */
    private Date validUntil;
    /** 币种 */
    private String currency;
    /** 汇率 */
    private BigDecimal exchangeRate;
    /** 总金额 */
    private BigDecimal totalAmount;
    /** 税额 */
    private BigDecimal taxAmount;
    /** 折扣额 */
    private BigDecimal discountAmount;
    /** 折扣率 */
    private BigDecimal discountRate;
    /** 付款条款 */
    private String paymentTerms;
    /** 交货条款 */
    private String deliveryTerms;
    /** 状态 */
    private String status;
    /** 审批流程ID */
    private Long selectFlowId;
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

    /** 报价明细行列表 */
    private List<SoQuotationLine> soQuotationLineList;
}
