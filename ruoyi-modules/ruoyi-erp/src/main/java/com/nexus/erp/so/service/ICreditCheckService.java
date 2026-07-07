package com.nexus.erp.so.service;

import java.math.BigDecimal;

/**
 * 信用检查Service接口
 *
 * @author nexus
 */
public interface ICreditCheckService {

    /**
     * 信用检查并冻结额度，若信用不足则抛出异常
     *
     * @param clientId    客户ID
     * @param soId        销售订单ID
     * @param orderAmount 订单金额
     */
    void checkAndFreeze(Long clientId, Long soId, BigDecimal orderAmount);

    /**
     * 释放已冻结/已用的信用额度（close/cancel 调用）
     *
     * @param soId 销售订单ID
     */
    void release(Long soId);

    /**
     * 将已冻结的信用额度转为已用（fulfill 调用）。
     * <p>语义：订单已交付形成应收，信用仍占用，但状态从"冻结"转为"已用"，
     * 用于区分未交付承诺与已交付应收。</p>
     *
     * @param soId 销售订单ID
     */
    void convertToUsed(Long soId);

    /**
     * 获取客户已占用的信用额度总额（FROZEN + USED）
     *
     * @param clientId 客户ID
     * @return 已占用额度
     */
    BigDecimal getUsedAmount(Long clientId);
}
