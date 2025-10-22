
package com.nexus.core.so.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售订单对象 so_sales_order_header vo
 *
 * @author nexus
 * @date 2025-10-22
 */
@Data
public class SoSalesOrderHeaderVo extends BasePageDtoRequest {

    /**
     * 销售订单ID
     */
    private Long soId;
    /**
     * 销售订单编号
     */
    private String soNo;
    /**
     * 来源客户订单
     */
    private Long coId;
    /**
     * 来源客户ID
     */
    private Long clientId;
    /**
     * 销售代表
     */
    private Long salesRepId;
    /**
     * 订单生成日期
     */
    private LocalDateTime orderDate;
    /**
     * 承诺交货日
     */
    private LocalDateTime promiseDate;
    /**
     * 客户要求交货日
     */
    private LocalDateTime requestedDate;
    /**
     * 发货地址
     */
    private Long deliveryAddressId;
    /**
     * 开票地址ID
     */
    private Long invoiceAddressId;
    /**
     * 付款条款
     */
    private String paymentTerms;
    /**
     * 货币
     */
    private String currency;
    /**
     * 汇率
     */
    private BigDecimal exchangeRate;
    /**
     * 含税总金额
     */
    private BigDecimal totalAmount;
    /**
     * 税款
     */
    private BigDecimal taxAmount;
    /**
     * 整单折扣
     */
    private BigDecimal discountAmount;
    /**
     * 整单折扣率
     */
    private BigDecimal discountRate;
    /**
     * 信用状态
     */
    private String creditStatus;
    /**
     * 信用检查时间
     */
    private LocalDateTime creditCheckTime;
    /**
     * 信用拦截人
     */
    private Long creditBlockedBy;
    /**
     * 订单状态
     */
    private String orderStatus;
    /**
     * 项目ID
     */
    private Long projectId;
    /**
     * 是否启用项目配套发料
     */
    private Integer isPaEnabled;
    /**
     * 确认时间
     */
    private LocalDateTime confirmedTime;
    /**
     * 完全履行时间
     */
    private LocalDateTime fulfilledTime;
    /**
     * 结束时间
     */
    private LocalDateTime closedTime;
    /**
     * 取消时间
     */
    private LocalDateTime cancelledTime;
    /**
     * 取消原因
     */
    private String cancelledReason;
    /**
     * 销售组织ID
     */
    private Long salesOrgId;
    /**
     * 分销渠道
     */
    private String distributionChannel;
    /**
     * 客户参考号
     */
    private String customerRefNo;

}