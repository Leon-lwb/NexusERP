package com.nexus.erp.pu.service;

/**
 * 采购收货回写Service接口
 *
 * @author nexus
 */
public interface IPuPurchaseReceiveWritebackService {

    /**
     * 回写采购订单行已收货数量
     *
     * @param receiveId 收货单ID
     * @param purId     采购订单ID
     */
    void writeBackReceivedQty(Long receiveId, Long purId);
}
