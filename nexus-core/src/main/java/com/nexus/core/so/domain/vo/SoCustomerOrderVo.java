
package com.nexus.core.so.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户订单对象 so_customer_order vo
 *
 * @author nexus
 * @date 2025-10-21
 */
@Data
public class SoCustomerOrderVo extends BasePageDtoRequest {

    /**
     * 客户订单ID
     */
    private Long coId;
    /**
     * 客户订单号
     */
    private String coNo;
    /**
     * 客户ID
     */
    private Long clientId;
    /**
     * 客户下单日期
     */
    private LocalDateTime orderDate;
    /**
     * 订单总金额（未税）
     */
    private BigDecimal totalAmount;
    /**
     * 订单状态
     */
    private String status;
    /**
     * 订单优先级
     */
    private Long priority;
    /**
     * 客户采购订单号
     */
    private String customerPoNo;
    /**
     * 订单来源
     */
    private String sourceChannel;

}