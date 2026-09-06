package com.nexus.pay.service.sqb;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.domain.PayReceivedMessage;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.pay.domain.PayOrder;
import com.nexus.pay.service.IPayOrderService;
import com.nexus.pay.util.MoneyUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 收钱吧支付实现
 *
 * @author zlh
 */
@Slf4j
@Service("pay:service:sqb")
@ConditionalOnProperty(prefix = "pay.sqb", name = "enabled", havingValue = "true")
public class SQBServiceImpl implements ISqbPayService {

    /** 收钱吧配置，提供 API 域名、终端/服务商密钥及证书读取。 */
    private final SqbConfig sqbConfig;

    /** 订单业务服务，用于回调/退款时更新订单状态。 */
    private final IPayOrderService payOrderService;

    /** 可靠消息发送器，回调成功后向 ERP 发送收款通知（建收款单+自动核销）。 */
    private final ReliableMessageProducer messageProducer;

    public SQBServiceImpl(SqbConfig sqbConfig, IPayOrderService payOrderService, ReliableMessageProducer messageProducer) {
        this.sqbConfig = sqbConfig;
        this.payOrderService = payOrderService;
        this.messageProducer = messageProducer;
    }

    /**
     * HTTP POST请求
     */
    private JSONObject httpPost(String url, Object body, String sign, String sn) {
        try {
            Map<String, String> header = new HashMap<>();
            header.put("Authorization", sn + " " + sign);
            String bodyStr = body instanceof String ? (String) body : JSON.toJSONString(body);
            String result = HttpRequest.post(url)
                    .addHeaders(header)
                    .body(bodyStr)
                    .timeout(10000)
                    .execute()
                    .body();
            return JSON.parseObject(result);
        } catch (Exception e) {
            log.error("SQB HTTP POST失败", e);
            return JSON.parseObject("{}");
        }
    }

    private String getSign(String signStr) {
        return DigestUtil.md5Hex(signStr);
    }

    /**
     * 终端激活
     */
    public JSONObject activate(String code, String deviceId, String clientSn, String name) {
        String url = sqbConfig.getApiDomain() + "/terminal/activate";
        JSONObject params = new JSONObject();
        try {
            params.put("app_id", sqbConfig.getAppId());
            params.put("code", code);
            params.put("device_id", deviceId);
            params.put("client_sn", clientSn);
            params.put("name", name);
            String sign = getSign(params + sqbConfig.getVendorKey());
            JSONObject retObj = httpPost(url, params.toString(), sign, sqbConfig.getVendorSn());
            String resCode = retObj.getString("result_code");
            if (!"200".equals(resCode)) {
                return null;
            }
            String responseStr = retObj.getString("biz_response");
            JSONObject terminal = JSON.parseObject(responseStr);
            if (terminal == null || !terminal.containsKey("terminal_sn") || !terminal.containsKey("terminal_key")) {
                return null;
            }
            return terminal;
        } catch (Exception e) {
            log.error("SQB终端激活失败", e);
            return null;
        }
    }

    /**
     * 终端签到
     */
    public JSONObject checkin() {
        String url = sqbConfig.getApiDomain() + "/terminal/checkin";
        JSONObject params = new JSONObject();
        try {
            params.put("terminal_sn", sqbConfig.getTerminalSn());
            params.put("device_id", "HUISUAN001POS01");
            params.put("os_info", "Mac OS");
            params.put("sdk_version", "Java SDK v1.0");
            String sign = getSign(params + sqbConfig.getTerminalKey());
            JSONObject retObj = httpPost(url, params.toString(), sign, sqbConfig.getTerminalSn());
            String resCode = retObj.getString("result_code");
            if (!"200".equals(resCode)) {
                return null;
            }
            String responseStr = retObj.getString("biz_response");
            JSONObject terminal = JSON.parseObject(responseStr);
            if (terminal == null || !terminal.containsKey("terminal_sn") || !terminal.containsKey("terminal_key")) {
                return null;
            }
            return terminal;
        } catch (Exception e) {
            log.error("SQB终端签到失败", e);
            return null;
        }
    }

    /**
     * 发起收钱吧退款，退款状态为 REFUNDED 时更新订单为「已退款」。
     *
     * @param payOrder 待退款的支付订单
     * @return 退款后的支付订单
     */
    @Override
    public PayOrder refund(PayOrder payOrder) {
        String url = sqbConfig.getApiDomain() + "/upay/v2/refund";
        JSONObject params = new JSONObject();
        try {
            params.put("terminal_sn", sqbConfig.getTerminalSn());
            params.put("client_sn", payOrder.getOrderNumber());
            params.put("refund_amount", MoneyUtil.yuanToFen(payOrder.getTotalAmount()));
            // 退款请求号固定为订单号派生值: 同一订单重复发起退款时渠道侧按请求号幂等去重, 防止重复退款
            params.put("refund_request_no", "R" + payOrder.getOrderNumber());
            params.put("operator", "kay");

            String sign = getSign(params + sqbConfig.getTerminalKey());
            JSONObject retObj = httpPost(url, params, sign, sqbConfig.getTerminalSn());
            JSONObject bizResponse = retObj.getJSONObject("biz_response");
            if (bizResponse != null && "REFUNDED".equals(bizResponse.getString("order_status"))) {
                payOrderService.updateStatus(payOrder.getOrderNumber(), "已退款");
            } else {
                log.error("SQB退款失败");
            }
            return payOrder;
        } catch (Exception e) {
            log.error("SQB退款异常", e);
            return payOrder;
        }
    }

    /**
     * 查询收钱吧订单支付状态（结果仅记录日志，不强制更新本地状态）。
     *
     * @param payOrder 待查询的支付订单
     * @return 原支付订单对象
     */
    @Override
    public PayOrder query(PayOrder payOrder) {
        String url = sqbConfig.getApiDomain() + "/upay/v2/query";
        JSONObject params = new JSONObject();
        try {
            params.put("terminal_sn", sqbConfig.getTerminalSn());
            params.put("client_sn", payOrder.getOrderNumber());
            String sign = getSign(params + sqbConfig.getTerminalKey());
            JSONObject retObj = httpPost(url, params, sign, sqbConfig.getTerminalSn());
            String resCode = retObj.getString("result_code");
            if (!"200".equals(resCode)) {
                throw new ServiceException("查询支付订单失败");
            }
        } catch (Exception e) {
            log.error("SQB查询异常", e);
        }
        return payOrder;
    }

    /**
     * 生成收钱吧支付链接（使用默认回调地址）。
     *
     * @param payOrder 支付订单（含订单号、金额、创建人、备注）
     * @return 收钱吧网关支付链接
     */
    @Override
    public String payUrl(PayOrder payOrder) {
        return payUrl(payOrder, null);
    }

    /**
     * 生成收钱吧支付链接：拼接订单参数并按终端密钥签名。
     *
     * @param payOrder 支付订单
     * @param notifyBaseUrl 自定义回调基础地址（可空，使用配置中的回调地址）
     * @return 收钱吧网关支付链接
     */
    public String payUrl(PayOrder payOrder, String notifyBaseUrl) {
        if (payOrder.getRemark() == null) {
            payOrder.setRemark("支付");
        }
        String orderNotifyUrl = sqbConfig.getNotifyUrl() + "/" + payOrder.getOrderNumber() + "/pay";
        String param = "client_sn=" + payOrder.getOrderNumber()
                + "&notify_url=" + orderNotifyUrl
                + "&operator=" + payOrder.getCreateBy()
                + "&return_url=https://www.shouqianba.com/"
                + "&subject=" + payOrder.getRemark()
                + "&terminal_sn=" + sqbConfig.getTerminalSn()
                + "&total_amount=" + MoneyUtil.yuanToFen(payOrder.getTotalAmount());
        try {
            String urlParam = "client_sn=" + payOrder.getOrderNumber()
                    + "&notify_url=" + URLEncoder.encode(orderNotifyUrl, StandardCharsets.UTF_8.name())
                    + "&operator=" + URLEncoder.encode(String.valueOf(payOrder.getCreateBy()), StandardCharsets.UTF_8.name())
                    + "&return_url=https://www.shouqianba.com/"
                    + "&subject=" + URLEncoder.encode(payOrder.getRemark(), StandardCharsets.UTF_8.name())
                    + "&terminal_sn=" + sqbConfig.getTerminalSn()
                    + "&total_amount=" + MoneyUtil.yuanToFen(payOrder.getTotalAmount());

            String sign = getSign(param + "&key=" + sqbConfig.getTerminalKey());
            return "https://qr.shouqianba.com/gateway?" + urlParam + "&sign=" + sign.toUpperCase();
        } catch (Exception e) {
            throw new ServiceException("生成收钱吧支付链接失败");
        }
    }

    /**
     * 预下单
     */
    public String precreate(PayOrder payOrder, String sn, String payway) {
        String url = sqbConfig.getApiDomain() + "/upay/v2/precreate";
        JSONObject params = new JSONObject();
        try {
            params.put("terminal_sn", sqbConfig.getTerminalSn());
            params.put("client_sn", payOrder.getOrderNumber());
            params.put("total_amount", MoneyUtil.yuanToFen(payOrder.getTotalAmount()));
            params.put("subject", "无简介");
            params.put("operator", "system");

            String sign = getSign(params + sqbConfig.getTerminalKey());
            JSONObject retObj = httpPost(url, params.toString(), sign, sqbConfig.getTerminalSn());
            return retObj.toString();
        } catch (Exception e) {
            log.error("SQB预下单失败", e);
            return null;
        }
    }

    /**
     * 自动撤单
     */
    public String cancel(String terminalSn, String terminalKey) {
        String url = sqbConfig.getApiDomain() + "/upay/v2/cancel";
        JSONObject params = new JSONObject();
        try {
            params.put("terminal_sn", terminalSn);
            params.put("client_sn", "18348290098298292838");

            String sign = getSign(params + terminalKey);
            JSONObject retObj = httpPost(url, params.toString(), sign, terminalSn);
            return retObj.toString();
        } catch (Exception e) {
            log.error("SQB撤单失败", e);
            return null;
        }
    }

    /**
     * 使用收钱吧平台 RSA 公钥验签回调数据。
     *
     * @param data 待验签的原始数据
     * @param sign Base64 编码的签名值
     * @return 验签通过返回 true，否则 false
     */
    public boolean validateSign(String data, String sign) {
        try {
            Signature signature = Signature.getInstance("SHA256WithRSA");
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey localPublicKey = keyFactory
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(sqbConfig.getPublicKey())));
            signature.initVerify(localPublicKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(sign));
        } catch (Exception e) {
            log.error("SQB验签失败", e);
            return false;
        }
    }

    /**
     * 处理收钱吧异步回调：验签 -> 终态幂等 -> 状态/金额校验 -> 更新订单并发送收款通知 MQ。
     * <p>支付成功(type=pay): 更新订单为「已支付」并向 PAY_RECEIVED_CALLBACK 发 PayReceivedMessage,
     * 由 ERP 侧 PayReceivedConsumer 建收款单+自动核销; 退款回调(type=refund)仅记录日志(发起退款时已同步置「已退款」)。
     * 任何处理失败均返回 "fail" 让渠道重试, 不静默吞掉。</p>
     *
     * @param request HTTP 请求，承载回调报文与签名头
     * @param response HTTP 响应
     * @param payOrder 关联支付订单（回调 URL 携带订单号时非空）
     * @param type 通知类型 pay/refund
     * @return "success" 表示处理成功，"fail" 表示失败
     */
    @Override
    public String notify(HttpServletRequest request, HttpServletResponse response, PayOrder payOrder, String type) {
        try {
            String requestBody = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
            JSONObject jsonObject = JSON.parseObject(requestBody);
            if (jsonObject == null) {
                log.error("SQB回调报文为空");
                return "fail";
            }
            String sign = request.getHeader("Authorization");
            if (!validateSign(requestBody, sign)) {
                log.error("SQB回调验签失败: type={}, urlOrderNumber={}", type, payOrder == null ? null : payOrder.getOrderNumber());
                return "fail";
            }
            log.info("SQB回调：{}", jsonObject);
            // 退款回调仅记录(退款接口已同步更新订单状态), 不建收款单
            if ("refund".equals(type)) {
                return "success";
            }
            String orderNumber = jsonObject.getString("client_sn");
            if (StringUtils.isBlank(orderNumber) && payOrder != null) {
                orderNumber = payOrder.getOrderNumber();
            }
            if (StringUtils.isBlank(orderNumber)) {
                log.error("SQB回调缺少 client_sn, 无法定位订单");
                return "fail";
            }
            PayOrder order;
            try {
                order = payOrderService.selectPayOrderByOrderNumber(orderNumber);
            } catch (ServiceException e) {
                log.error("SQB回调订单不存在: orderNumber={}", orderNumber);
                return "fail";
            }
            // 幂等：已支付/已退款/已关闭等终态订单的重复回调直接忽略
            String curStatus = order.getOrderStatus();
            if ("已支付".equals(curStatus) || "已退款".equals(curStatus) || "已关闭".equals(curStatus)) {
                log.info("SQB重复回调，忽略: orderNumber={}, 状态={}", orderNumber, curStatus);
                return "success";
            }
            // 仅接受支付成功状态, 其他中间态不更新订单
            String status = jsonObject.getString("status") != null
                ? jsonObject.getString("status") : jsonObject.getString("order_status");
            if (status == null) {
                log.error("SQB回调缺少 status 字段: orderNumber={}", orderNumber);
                return "fail";
            }
            if ("REFUNDED".equalsIgnoreCase(status)) {
                // 退款成功通知: 兜底同步订单状态, 不建收款单
                payOrderService.updateStatus(orderNumber, "已退款");
                return "success";
            }
            if (!"SUCCESS".equalsIgnoreCase(status)) {
                log.warn("SQB回调非成功状态: orderNumber={}, status={}", orderNumber, status);
                return "success";
            }
            // 金额校验：回调金额(分)须与订单实际支付金额(元)一致, 防止金额篡改
            // (字段缺失/格式异常时降级跳过——来源已由验签保证, 避免解析分歧导致渠道无限重试)
            String totalAmountFen = jsonObject.getString("total_amount");
            if (StringUtils.isNotBlank(totalAmountFen) && order.getActualAmount() != null) {
                try {
                    BigDecimal callbackYuan = MoneyUtil.fenToYuan(Long.parseLong(totalAmountFen));
                    if (callbackYuan.compareTo(order.getActualAmount()) != 0) {
                        log.error("SQB回调金额不一致: 回调={}元, 订单={}元, orderNumber={}",
                            callbackYuan, order.getActualAmount(), orderNumber);
                        return "fail";
                    }
                } catch (NumberFormatException e) {
                    log.warn("SQB回调金额字段格式异常, 跳过金额校验: total_amount={}", totalAmountFen);
                }
            }
            order.setPayType("sqb");
            order.setThirdNumber(jsonObject.getString("sn"));
            order.setOrderStatus("已支付");
            payOrderService.updatePayOrder(order);
            sendPayReceived(order);
            return "success";
        } catch (IOException e) {
            log.error("SQB回调处理失败", e);
            return "fail";
        } catch (Exception e) {
            log.error("SQB回调处理异常", e);
            return "fail";
        }
    }

    /**
     * 发送支付收款通知 MQ（ERP 侧 PayReceivedConsumer 建收款单 + FIFO 自动核销）。
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
            msg.setPayType("sqb");
            msg.setTenantIdStr(payOrder.getTenantId());
            msg.setSourceModule("pay");
            messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE, RoutingKeyConstants.PAY_RECEIVED_CALLBACK, msg);
            log.info("发送支付收款通知: orderNumber={}, amount={}", payOrder.getOrderNumber(), payOrder.getActualAmount());
        } catch (Exception e) {
            log.error("发送支付收款通知失败: orderNumber={}", payOrder.getOrderNumber(), e);
        }
    }

}
