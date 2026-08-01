package com.nexus.pay.service.alipay;

import com.alipay.easysdk.factory.Factory;
import com.alipay.easysdk.kernel.Config;
import com.alipay.easysdk.payment.common.models.AlipayTradeRefundResponse;
import com.alipay.easysdk.payment.facetoface.models.AlipayTradePrecreateResponse;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.pay.domain.PayOrder;
import com.nexus.pay.service.IPayOrderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.domain.PayReceivedMessage;
import com.nexus.common.mq.producer.ReliableMessageProducer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝支付实现
 *
 * @author zlh
 */
@Slf4j
@Service("pay:service:alipay")
@ConditionalOnProperty(prefix = "pay.alipay", name = "enabled", havingValue = "true")
public class AliPayServiceImpl implements IAliPayService {

    /** 订单业务服务，用于回调/查询时更新与查询订单。 */
    private final IPayOrderService payOrderService;

    /** 支付宝 SDK 全局配置。 */
    private final Config config;

    /** MQ 生产者，支付成功后通知 ERP 建收款单+核销(F1 AR/AP)。 */
    private final ReliableMessageProducer messageProducer;

    public AliPayServiceImpl(IPayOrderService payOrderService, Config config, ReliableMessageProducer messageProducer) {
        this.payOrderService = payOrderService;
        this.config = config;
        this.messageProducer = messageProducer;
    }

    /**
     * 支付宝回调业务处理（预留扩展点，目前为空实现）。
     *
     * @param params 支付宝回调参数集合
     */
    @Override
    public void callback(Map<String, String> params) {
        // 预留回调处理
    }

    /**
     * 生成支付宝支付链接：优先当面付预下单获取二维码链接，失败回退到 PC 网页支付表单。
     *
     * @param payOrder 支付订单（含订单内容、订单号、实际金额）
     * @return 可用于扫码或跳转的支付链接/表单字符串
     */
    @Override
    public String payUrl(PayOrder payOrder) {
        try {
            String orderNotifyUrl = config.notifyUrl + "/" + payOrder.getOrderNumber() + "/pay";
            // 支付宝 API 金额单位为"元",pay_order 现已存元,直接取即可
            String totalAmount = payOrder.getActualAmount().toPlainString();

            // 当面付预下单 — 生成可供支付宝APP扫码支付的链接
            AlipayTradePrecreateResponse response = Factory.Payment.FaceToFace().preCreate(
                    payOrder.getOrderContent(),
                    payOrder.getOrderNumber(),
                    totalAmount);
            log.info("支付宝预下单响应: code={}, msg={}, qrCode={}",
                    response.code, response.msg, response.qrCode);

            if ("10000".equals(response.code) && StringUtils.isNotBlank(response.qrCode)) {
                return response.qrCode;
            }

            // 如果当面付不可用，回退到PC网页支付
            log.warn("当面付预下单失败(code={}, msg={})，回退到网页支付",
                    response.code, response.msg);
            var pageResponse = Factory.Payment.Page().pay(
                    payOrder.getOrderContent(),
                    payOrder.getOrderNumber(),
                    totalAmount,
                    orderNotifyUrl);
            return pageResponse.getBody();

        } catch (Exception e) {
            log.error("创建支付宝支付URL失败", e);
            throw new ServiceException("创建支付宝支付URL失败: " + e.getMessage());
        }
    }

    /**
     * 处理支付宝异步回调：验签通过后更新订单状态为「已支付」并写入第三方订单号。
     *
     * @param request HTTP 请求，承载回调参数
     * @param response HTTP 响应
     * @param payOrder 关联支付订单（可由订单号反查）
     * @param type 通知类型
     * @return 验签结果，"success" 表示处理成功，"fail" 表示失败
     */
    @Override
    public String notify(HttpServletRequest request, HttpServletResponse response, PayOrder payOrder, String type) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap != null && !parameterMap.isEmpty()) {
            Map<String, String> params = new HashMap<>();
            for (String name : parameterMap.keySet()) {
                params.put(name, request.getParameter(name));
            }
            String orderNumber = params.get("out_trade_no");
            String theTradeNo = params.get("trade_no");
            String tradeStatus = params.get("trade_status");
            String totalAmount = params.get("total_amount");
            // 订单号缺失或订单不存在：拒绝，让渠道重试
            if (orderNumber == null) {
                log.error("支付宝回调缺少 out_trade_no");
                return "fail";
            }
            try {
                payOrder = payOrderService.selectPayOrderByOrderNumber(orderNumber);
                if (payOrder == null) {
                    log.error("支付宝回调订单不存在: orderNumber={}", orderNumber);
                    return "fail";
                }
                // 验签失败必须返回 "fail" 让支付宝重试，不能静默吞掉
                if (!Factory.Payment.Common().verifyNotify(params)) {
                    log.error("支付宝回调验签失败: orderNumber={}", orderNumber);
                    return "fail";
                }
                // 幂等：已支付/已退款/已关闭等终态订单的重复回调直接忽略
                String cur = payOrder.getOrderStatus();
                if ("已支付".equals(cur) || "已退款".equals(cur) || "已关闭".equals(cur)) {
                    log.info("支付宝重复回调，忽略: orderNumber={}, 状态={}", orderNumber, cur);
                    return "success";
                }
                // 仅接受支付成功终态，WAIT_BUYER_PAY 等非终态不更新
                if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
                    log.warn("支付宝回调非成功状态: orderNumber={}, tradeStatus={}", orderNumber, tradeStatus);
                    return "success";
                }
                // 金额校验：回调金额必须与本地订单金额一致，防止金额篡改
                if (totalAmount != null && payOrder.getActualAmount() != null
                        && new java.math.BigDecimal(totalAmount).compareTo(payOrder.getActualAmount()) != 0) {
                    log.error("支付宝回调金额不一致: 回调={}, 订单={}, orderNumber={}",
                            totalAmount, payOrder.getActualAmount(), orderNumber);
                    return "fail";
                }
                payOrder.setPayType("alipay");
                payOrder.setThirdNumber(theTradeNo);
                payOrder.setOrderStatus("已支付");
                payOrderService.updatePayOrder(payOrder);
                sendPayReceived(payOrder);
                return "success";
            } catch (Exception e) {
                log.error("支付宝回调处理失败", e);
                return "fail";
            }
        }
        return "fail";
    }

    /**
     * 发送支付收款通知 MQ(ERP 侧 PayReceivedConsumer 建收款单 + FIFO 自动核销)。
     */
    private void sendPayReceived(PayOrder payOrder) {
        try {
            PayReceivedMessage msg = new PayReceivedMessage();
            msg.setPayOrderNumber(payOrder.getOrderNumber());
            msg.setAmount(payOrder.getActualAmount());
            msg.setThirdPartyNo(payOrder.getThirdNumber());
            msg.setBizType(payOrder.getBizType());
            msg.setBizId(payOrder.getBizId());
            msg.setClientId(payOrder.getClientId());
            msg.setPayType("alipay");
            msg.setTenantIdStr(payOrder.getTenantId());
            msg.setSourceModule("pay");
            messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE, RoutingKeyConstants.PAY_RECEIVED_CALLBACK, msg);
            log.info("发送支付收款通知: orderNumber={}, amount={}", payOrder.getOrderNumber(), payOrder.getActualAmount());
        } catch (Exception e) {
            log.error("发送支付收款通知失败: orderNumber={}", payOrder.getOrderNumber(), e);
        }
    }

    /**
     * 查询支付宝订单状态并同步到本地订单（交易不存在时置「未支付」）。
     *
     * @param payOrder 待查询的支付订单
     * @return 刷新状态后的支付订单
     */
    @Override
    public PayOrder query(PayOrder payOrder) {
        try {
            var response = Factory.Payment.Common().query(payOrder.getOrderNumber());
            log.info("支付宝查询响应: code={}, tradeStatus={}, subMsg={}",
                    response.code, response.tradeStatus, response.subMsg);

            if ("10000".equals(response.code)) {
                String tradeStatus = response.tradeStatus;
                String orderStatus = switch (tradeStatus) {
                    case "TRADE_SUCCESS", "TRADE_FINISHED" -> "已支付";
                    case "WAIT_BUYER_PAY" -> "待支付";
                    case "TRADE_CLOSED" -> "已关闭";
                    default -> "未知状态" + tradeStatus;
                };
                payOrderService.updateStatus(payOrder.getOrderNumber(), orderStatus);
                payOrder.setOrderStatus(orderStatus);
            } else if ("40004".equals(response.code)) {
                // 交易不存在（沙箱环境可能返回此状态）
                log.warn("支付宝查询: 交易不存在, orderNumber={}", payOrder.getOrderNumber());
                payOrder.setOrderStatus("未支付");
            } else {
                log.warn("支付宝查询返回非预期状态: code={}, subMsg={}",
                        response.code, response.subMsg);
            }
            return payOrder;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            // 沙箱环境可能有SDK兼容问题，不要因为查询失败就炸掉
            log.warn("支付宝查询异常(沙箱兼容问题): {}", e.getMessage());
            return payOrder;
        }
    }

    /**
     * 发起支付宝退款，成功后更新订单状态为「已退款」。
     *
     * @param payOrder 待退款的支付订单
     * @return 退款后的支付订单
     */
    @Override
    public PayOrder refund(PayOrder payOrder) {
        try {
            AlipayTradeRefundResponse response = Factory.Payment.Common().refund(
                    payOrder.getOrderNumber(),
                    payOrder.getActualAmount().toPlainString());
            if ("10000".equals(response.code)) {
                payOrderService.updateStatus(payOrder.getOrderNumber(), "已退款");
            } else {
                throw new ServiceException("支付宝退款失败：" + response.subMsg);
            }
            return payOrder;
        } catch (Exception e) {
            throw new ServiceException("支付宝退款异常：" + e.getMessage());
        }
    }

}
