package com.nexus.common.mq.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 支付收款通知消息(pay -> ERP,跨库)。
 * <p>pay 模块支付回调验签成功后发送,ERP 侧 PayReceivedConsumer 消费:
 * 建 AR 收款单(source=PAY_CALLBACK)-> GL 过账 -> FIFO 自动核销该客户未清发票。</p>
 *
 * @author nexus
 * @date 2026-07-30
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PayReceivedMessage extends BaseMessage {

    private static final long serialVersionUID = 1L;

    /** 租户ID(字符串,避免雪花ID精度丢失) */
    private String tenantIdStr;

    /** 支付订单号(pay_order.order_number,跨库关联) */
    private String payOrderNumber;

    /** 实际支付金额(元) */
    private BigDecimal amount;

    /** 第三方流水号 */
    private String thirdPartyNo;

    /** 业务类型 SO_ORDER/AR_RECEIPT */
    private String bizType;

    /** 业务单据ID(销售订单ID等) */
    private Long bizId;

    /** 客户ID(AR 收款核销用) */
    private Long clientId;

    /** 支付渠道 alipay/wechat/sqb */
    private String payType;
}
