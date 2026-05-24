package org.dromara.erp.so.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.erp.so.domain.SoSalesOrderHeader;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售订单业务对象 so_sales_order_header
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SoSalesOrderHeader.class, reverseConvertGenerate = false)
public class SoSalesOrderHeaderBo extends BaseEntity {

    private Long soId;

    @NotBlank(message = "销售订单编号不能为空")
    private String soNo;

    private Long coId;
    private Long clientId;
    private String clientName;
    private Long salesRepId;
    private String salesRepName;
    private Date orderDate;
    private Date promiseDate;
    private Date requestedDate;
    private Long deliveryAddressId;
    private Long invoiceAddressId;
    private String deliveryAddress;
    private String invoiceAddress;
    private String paymentTerms;
    private String currency;
    private BigDecimal exchangeRate;
    private BigDecimal totalAmount;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal discountRate;
    private String creditStatus;
    private Date creditCheckTime;
    private Long creditBlockedBy;
    private String orderStatus;
    private Long projectId;
    private Integer isPaEnabled;
    private Date confirmedTime;
    private Date fulfilledTime;
    private Date closedTime;
    private Date cancelledTime;
    private String cancelledReason;
    private Long salesOrgId;
    private String distributionChannel;
    private String customerRefNo;
    private String attr1;
    private String attr2;
    private Long attr3;
    private Long attr4;
    private String remark;

    private Long selectFlowId;

    private List<SoSalesOrderLineBo> soSalesOrderLineList;

    private Long[] ids;
}
