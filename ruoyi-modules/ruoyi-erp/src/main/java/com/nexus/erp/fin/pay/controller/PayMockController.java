package com.nexus.erp.fin.pay.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.domain.R;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.domain.PayReceivedMessage;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.common.web.core.BaseController;
import com.nexus.common.tenant.helper.TenantHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 支付回调模拟入口(最小 pay 收款入口)。
 * <p>模拟第三方支付渠道(支付宝/微信)收款成功回调:发送 {@link PayReceivedMessage} 到
 * PAY_RECEIVED_CALLBACK 路由,由既有 {@code PayReceivedConsumer} 消费
 * (建 AR 收款单 → GL 过账 → FIFO 自动核销)。解决 pay 模块缺失导致的自动收款断链。</p>
 *
 * @author nexus
 * @date 2026-08-01
 */
@RequiredArgsConstructor
@DS("erp")
@RestController
@RequestMapping("/erp/fin/pay/mock")
public class PayMockController extends BaseController {

    private final ReliableMessageProducer messageProducer;

    /** 模拟渠道收款回调:向 PAY_RECEIVED_CALLBACK 发 PayReceivedMessage */
    @SaCheckPermission("fin:pay:callback")
    @Log(title = "模拟支付收款回调", businessType = BusinessType.INSERT)
    @PostMapping("/callback")
    public R<Void> callback(@Validated @RequestBody PayCallbackRequest request) {
        PayReceivedMessage msg = new PayReceivedMessage();
        msg.setTenantIdStr(TenantHelper.getTenantId());
        msg.setPayOrderNumber(request.getPayOrderNumber());
        msg.setAmount(request.getAmount());
        msg.setThirdPartyNo(request.getThirdPartyNo());
        msg.setBizType("PAY");
        msg.setClientId(request.getClientId());
        msg.setPayType(request.getPayType());
        messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE, RoutingKeyConstants.PAY_RECEIVED_CALLBACK, msg);
        return R.ok("模拟收款回调已发送");
    }

    /** 模拟回调请求体 */
    public static class PayCallbackRequest {

        /** 支付订单号(渠道侧) */
        private String payOrderNumber;
        /** 实收金额 */
        private BigDecimal amount;
        /** 第三方流水号 */
        private String thirdPartyNo;
        /** 客户ID(AR 核销对象) */
        private Long clientId;
        /** 支付渠道 alipay/wechat */
        private String payType;

        public String getPayOrderNumber() {
            return payOrderNumber;
        }

        public void setPayOrderNumber(String payOrderNumber) {
            this.payOrderNumber = payOrderNumber;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public String getThirdPartyNo() {
            return thirdPartyNo;
        }

        public void setThirdPartyNo(String thirdPartyNo) {
            this.thirdPartyNo = thirdPartyNo;
        }

        public Long getClientId() {
            return clientId;
        }

        public void setClientId(Long clientId) {
            this.clientId = clientId;
        }

        public String getPayType() {
            return payType;
        }

        public void setPayType(String payType) {
            this.payType = payType;
        }
    }
}
