package com.nexus.pay.service.wechat;

import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;
import com.wechat.pay.java.service.payments.nativepay.model.QueryOrderByIdRequest;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import lombok.extern.slf4j.Slf4j;
import com.nexus.pay.domain.PayOrder;
import com.nexus.pay.service.IPayOrderService;
import com.nexus.pay.util.MoneyUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;

/**
 * 微信支付实现
 *
 * @author ZlH
 */
@Slf4j
@Service("pay:service:wechat")
@ConditionalOnProperty(prefix = "pay.wechat", name = "enabled", havingValue = "true")
public class WxPayServiceImpl implements IWxPayService {

    private final IPayOrderService payOrderService;
    private final NativePayService nativePayService;
    private final WxPayConfig wxPayAppConfig;
    private final NotificationParser notificationParser;
    private final RefundService refundService;

    public WxPayServiceImpl(IPayOrderService payOrderService, NativePayService nativePayService,
                            WxPayConfig wxPayAppConfig, NotificationParser notificationParser,
                            RefundService refundService) {
        this.payOrderService = payOrderService;
        this.nativePayService = nativePayService;
        this.wxPayAppConfig = wxPayAppConfig;
        this.notificationParser = notificationParser;
        this.refundService = refundService;
    }

    @Override
    public String payUrl(PayOrder payOrder) {
        PrepayRequest request = new PrepayRequest();
        Amount amount = new Amount();
        // 微信 API 金额单位为"分"(整数),pay_order 存元,需元->分;超 int 范围抛异常(失败优于负数扣款)
        amount.setTotal(Math.toIntExact(MoneyUtil.yuanToFen(payOrder.getTotalAmount())));
        request.setAmount(amount);
        request.setAppid(wxPayAppConfig.getAppId());
        request.setMchid(wxPayAppConfig.getMerchantId());
        request.setDescription(payOrder.getOrderContent());
        String orderNotifyUrl = wxPayAppConfig.getNotifyUrl() + "/" + payOrder.getOrderNumber() + "/pay";
        request.setNotifyUrl(orderNotifyUrl);
        request.setOutTradeNo(payOrder.getOrderNumber());
        PrepayResponse response = nativePayService.prepay(request);
        return response.getCodeUrl();
    }

    private String getTradeState(Transaction.TradeStateEnum tradeState) {
        return switch (tradeState) {
            case SUCCESS -> "已支付";
            case REFUND -> "已退款";
            case NOTPAY -> "未支付";
            case CLOSED -> "已关闭";
            case REVOKED -> "已撤销";
            case USERPAYING -> "用户支付中";
            case PAYERROR -> "支付错误";
            case ACCEPT -> "已接收待支付";
            default -> "未知状态" + tradeState;
        };
    }

    @Override
    public String notify(HttpServletRequest request, HttpServletResponse response, PayOrder payOrder, String type) {
        String timeStamp = request.getHeader("Wechatpay-Timestamp");
        String nonce = request.getHeader("Wechatpay-Nonce");
        String signature = request.getHeader("Wechatpay-Signature");
        String certSn = request.getHeader("Wechatpay-Serial");
        try {
            String requestBody = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
            RequestParam requestParam = new RequestParam.Builder()
                    .serialNumber(certSn)
                    .nonce(nonce)
                    .signature(signature)
                    .timestamp(timeStamp)
                    .body(requestBody)
                    .build();
            if ("pay".equals(type)) {
                Transaction transaction = notificationParser.parse(requestParam, Transaction.class);
                payOrder.setOrderStatus(getTradeState(transaction.getTradeState()));
                payOrder.setThirdNumber(transaction.getTransactionId());
                // 微信回调金额单位为"分",pay_order 存元,需分->元
                payOrder.setActualAmount(MoneyUtil.fenToYuan(transaction.getAmount().getTotal()));
                payOrderService.updatePayOrder(payOrder);
                return "success";
            } else if ("refund".equals(type)) {
                RefundNotification transaction = notificationParser.parse(requestParam, RefundNotification.class);
                payOrder.setOrderStatus(switch (transaction.getRefundStatus()) {
                    case SUCCESS -> "已退款";
                    case PROCESSING -> "退款处理中";
                    case CLOSED -> "退款关闭";
                    case ABNORMAL -> "退款异常";
                    default -> "未知状态" + transaction.getRefundStatus();
                });
                payOrderService.updatePayOrder(payOrder);
                return "success";
            } else {
                return "fail";
            }
        } catch (Exception e) {
            log.error("微信支付回调处理失败", e);
            return "fail";
        }
    }

    @Override
    public PayOrder query(PayOrder payOrder) {
        QueryOrderByIdRequest queryRequest = new QueryOrderByIdRequest();
        queryRequest.setMchid(wxPayAppConfig.getMerchantId());
        queryRequest.setTransactionId(payOrder.getThirdNumber());
        try {
            Transaction result = nativePayService.queryOrderById(queryRequest);
            // 微信查询返回金额单位为"分",pay_order 存元,需分->元
            payOrder.setActualAmount(MoneyUtil.fenToYuan(result.getAmount().getTotal()));
            payOrder.setOrderStatus(getTradeState(result.getTradeState()));
            payOrderService.updatePayOrder(payOrder);
        } catch (com.wechat.pay.java.core.exception.ServiceException e) {
            log.error("微信支付查询失败 code={}, message={}", e.getErrorCode(), e.getErrorMessage());
        }
        return payOrder;
    }

    @Override
    public PayOrder refund(PayOrder payOrder) {
        CreateRequest request = new CreateRequest();
        request.setTransactionId(payOrder.getThirdNumber());
        request.setOutRefundNo(payOrder.getOrderNumber());
        request.setOutTradeNo(payOrder.getOrderNumber());
        AmountReq amount = new AmountReq();
        amount.setCurrency("CNY");
        // 微信退款金额单位为"分",pay_order 存元,需元->分
        amount.setTotal(MoneyUtil.yuanToFen(payOrder.getTotalAmount()));
        amount.setRefund(MoneyUtil.yuanToFen(payOrder.getTotalAmount()));
        request.setAmount(amount);
        String orderNotifyUrl = wxPayAppConfig.getNotifyUrl() + "/" + payOrder.getOrderNumber() + "/refund";
        request.setNotifyUrl(orderNotifyUrl);
        Refund refund = refundService.create(request);
        String status = switch (refund.getStatus()) {
            case SUCCESS -> "已退款";
            case PROCESSING -> "退款处理中";
            case CLOSED -> "退款关闭";
            case ABNORMAL -> "退款异常";
            default -> "未知状态" + refund.getStatus();
        };
        payOrder.setOrderStatus(status);
        payOrderService.updateStatus(payOrder.getOrderNumber(), status);
        return payOrder;
    }

}
