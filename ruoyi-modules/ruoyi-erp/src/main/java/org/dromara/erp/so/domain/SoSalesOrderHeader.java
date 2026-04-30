package org.dromara.erp.so.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售订单对象 so_sales_order_header
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("so_sales_order_header")
public class SoSalesOrderHeader extends TenantEntity {

    @TableId(value = "so_id")
    private Long soId;

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

    @TableField(exist = false)
    private List<SoSalesOrderLine> soSalesOrderLineList;

    @TableLogic
    private String delFlag;
}
