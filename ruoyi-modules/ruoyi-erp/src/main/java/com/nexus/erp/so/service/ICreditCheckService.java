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
     * 释放已冻结的信用额度
     *
     * @param soId 销售订单ID
     */
    void release(Long soId);

    /**
     * 获取客户已冻结的信用额度总额
     *
     * @param clientId 客户ID
     * @return 已冻结额度
     */
    BigDecimal getUsedAmount(Long clientId);
}
