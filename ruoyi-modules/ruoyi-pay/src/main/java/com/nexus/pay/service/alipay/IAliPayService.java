package com.nexus.pay.service.alipay;

import com.nexus.pay.service.PayService;

import java.util.Map;

/**
 * 支付宝支付接口
 */
public interface IAliPayService extends PayService {

    /**
     * 支付宝回调业务处理（预留扩展点，目前未实现具体逻辑）。
     *
     * @param params 支付宝回调参数集合
     */
    void callback(Map<String, String> params);

}
