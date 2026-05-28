package com.nexus.pay.service.sqb;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.pay.domain.PayOrder;
import com.nexus.pay.service.IPayOrderService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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

    private final SqbConfig sqbConfig;
    private final IPayOrderService payOrderService;

    public SQBServiceImpl(SqbConfig sqbConfig, IPayOrderService payOrderService) {
        this.sqbConfig = sqbConfig;
        this.payOrderService = payOrderService;
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

    @Override
    public PayOrder refund(PayOrder payOrder) {
        String url = sqbConfig.getApiDomain() + "/upay/v2/refund";
        JSONObject params = new JSONObject();
        try {
            params.put("terminal_sn", sqbConfig.getTerminalSn());
            params.put("client_sn", payOrder.getOrderNumber());
            params.put("refund_amount", payOrder.getTotalAmount());
            params.put("refund_request_no", "1");
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

    @Override
    public String payUrl(PayOrder payOrder) {
        return payUrl(payOrder, null);
    }

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
                + "&total_amount=" + Long.parseLong(payOrder.getTotalAmount());
        try {
            String urlParam = "client_sn=" + payOrder.getOrderNumber()
                    + "&notify_url=" + URLEncoder.encode(orderNotifyUrl, StandardCharsets.UTF_8.name())
                    + "&operator=" + URLEncoder.encode(String.valueOf(payOrder.getCreateBy()), StandardCharsets.UTF_8.name())
                    + "&return_url=https://www.shouqianba.com/"
                    + "&subject=" + URLEncoder.encode(payOrder.getRemark(), StandardCharsets.UTF_8.name())
                    + "&terminal_sn=" + sqbConfig.getTerminalSn()
                    + "&total_amount=" + Long.parseLong(payOrder.getTotalAmount());

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
            params.put("total_amount", payOrder.getTotalAmount());
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

    @Override
    public String notify(HttpServletRequest request, HttpServletResponse response, PayOrder payOrder, String type) {
        try {
            String requestBody = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
            JSONObject jsonObject = JSON.parseObject(requestBody);
            String sign = request.getHeader("Authorization");
            if (!validateSign(requestBody, sign)) {
                throw new ServiceException("收钱吧支付回调验签失败");
            }
            log.info("SQB回调：{}", jsonObject);
            return "success";
        } catch (IOException e) {
            log.error("SQB回调处理失败", e);
            return "fail";
        }
    }

}
