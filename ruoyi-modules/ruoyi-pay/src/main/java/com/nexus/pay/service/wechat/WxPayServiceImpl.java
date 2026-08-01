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

    /** 订单业务服务，用于回调/查询时更新订单状态。 */
    private final IPayOrderService payOrderService;

    /** 微信 Native 支付服务，用于生成支付二维码。 */
    private final NativePayService nativePayService;

    /** 微信支付配置，提供商户号、AppID、回调地址等。 */
    private final WxPayConfig wxPayAppConfig;

    /** 微信回调通知解析器，用于验签与报文解析。 */
    private final NotificationParser notificationParser;

    /** 微信退款服务。 */
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

    /**
     * 生成微信 Native 支付二维码链接：下单后返回 code_url 供前端渲染二维码。
     *
     * @param payOrder 支付订单（含订单内容、订单号、金额）
     * @return 微信支付二维码链接（code_url）
     */
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

    /**
     * 处理微信支付/退款异步回调：验签解析报文，更新订单状态/第三方单号/实际金额。
     *
     * @param request HTTP 请求，承载微信回调头与报文体
     * @param response HTTP 响应
     * @param payOrder 关联支付订单
     * @param type 通知类型，pay 为支付、refund 为退款
     * @return 处理结果，"success" 表示成功，"fail" 表示失败
     */
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
                // 安全校验：回调订单号必须与路径订单号一致，防止重放他单回调
                String outTradeNo = transaction.getOutTradeNo();
                if (outTradeNo == null || !outTradeNo.equals(payOrder.getOrderNumber())) {
                    log.error("微信支付回调订单号不匹配: 回调={}, 订单={}", outTradeNo, payOrder.getOrderNumber());
                    return "fail";
                }
                // 安全校验：回调金额必须与本地订单金额一致（分→元比对），防止金额篡改
                long cbFen = transaction.getAmount() != null ? transaction.getAmount().getTotal() : -1L;
                long localFen = MoneyUtil.yuanToFen(payOrder.getTotalAmount());
                if (cbFen != localFen) {
                    log.error("微信支付回调金额不一致: 回调={}分, 订单={}分, 订单号={}", cbFen, localFen, payOrder.getOrderNumber());
                    return "fail";
                }
                // 幂等：仅待支付/支付中订单可更新为已支付；已支付/已退款等终态重复回调直接忽略
                if (!"已支付".equals(payOrder.getOrderStatus())
                        && !"未支付".equals(payOrder.getOrderStatus())
                        && !"用户支付中".equals(payOrder.getOrderStatus())) {
                    log.info("微信支付回调重复或终态订单 {}，忽略: 当前状态={}", payOrder.getOrderNumber(), payOrder.getOrderStatus());
                    return "success";
                }
                payOrder.setOrderStatus(getTradeState(transaction.getTradeState()));
                payOrder.setThirdNumber(transaction.getTransactionId());
                // 微信回调金额单位为"分",pay_order 存元,需分->元
                payOrder.setActualAmount(MoneyUtil.fenToYuan(cbFen));
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

    /**
     * 查询微信订单状态并同步实际金额与状态到本地订单。
     *
     * @param payOrder 待查询的支付订单（需含第三方订单号）
     * @return 刷新状态后的支付订单
     */
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

    /**
     * 发起微信退款，并按退款状态更新订单状态（已退款/处理中/异常等）。
     *
     * @param payOrder 待退款的支付订单
     * @return 退款后的支付订单
     */
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
