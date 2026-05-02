package org.dromara.pay.service.alipay;

import org.dromara.pay.service.PayService;

import java.util.Map;

/**
 * 支付宝支付接口
 */
public interface IAliPayService extends PayService {

    void callback(Map<String, String> params);

}
