package org.dromara.pay.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.pay.domain.PayOrder;

/**
 * 支付通道SPI接口
 * <p>
 * 各支付通道（支付宝、微信、收钱吧）实现此接口，
 * 通过Spring bean名称 "pay:service:{channel}" 注册，
 * 由PayController按通道名动态查找。
 * </p>
 */
public interface PayService {

    /**
     * 获取支付链接/表单
     */
    String payUrl(PayOrder payOrder);

    /**
     * 支付/退款回调处理
     */
    String notify(HttpServletRequest servletRequest, HttpServletResponse response, PayOrder payOrder, String type)
            throws Exception;

    /**
     * 查询支付状态
     */
    PayOrder query(PayOrder payOrder);

    /**
     * 发起退款
     */
    PayOrder refund(PayOrder payOrder);

}
