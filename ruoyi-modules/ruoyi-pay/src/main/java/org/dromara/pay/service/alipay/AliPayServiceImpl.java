package org.dromara.pay.service.alipay;

import com.alipay.easysdk.factory.Factory;
import com.alipay.easysdk.kernel.Config;
import com.alipay.easysdk.payment.common.models.AlipayTradeRefundResponse;
import com.alipay.easysdk.payment.facetoface.models.AlipayTradePrecreateResponse;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.pay.domain.PayOrder;
import org.dromara.pay.service.IPayOrderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

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

    private final IPayOrderService payOrderService;
    private final Config config;

    public AliPayServiceImpl(IPayOrderService payOrderService, Config config) {
        this.payOrderService = payOrderService;
        this.config = config;
    }

    @Override
    public void callback(Map<String, String> params) {
        // 预留回调处理
    }

    @Override
    public String payUrl(PayOrder payOrder) {
        try {
            String orderNotifyUrl = config.notifyUrl + "/" + payOrder.getOrderNumber() + "/pay";
            String totalAmount = String.valueOf(Double.parseDouble(payOrder.getActualAmount()) / 100);

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
            payOrder = payOrderService.selectPayOrderByOrderNumber(orderNumber);
            payOrder.setPayType("alipay");
            payOrder.setThirdNumber(theTradeNo);
            try {
                if (Factory.Payment.Common().verifyNotify(params)) {
                    payOrder.setOrderStatus("已支付");
                    payOrderService.updatePayOrder(payOrder);
                }
                return "success";
            } catch (Exception e) {
                log.error("支付宝回调处理失败", e);
            }
        }
        return "fail";
    }

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

    @Override
    public PayOrder refund(PayOrder payOrder) {
        try {
            AlipayTradeRefundResponse response = Factory.Payment.Common().refund(
                    payOrder.getOrderNumber(),
                    String.valueOf(Double.parseDouble(payOrder.getActualAmount()) / 100));
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
