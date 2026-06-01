package com.nexus.erp.pu.service;

/**
 * 采购收货联动服务接口
 * <p>
 * 定义采购订单到货通知与物料收货的联动生成操作。
 * 在采购订单已批准后，调用该服务自动创建到货通知单和物料收货单。
 *
 * @author nexus
 */
public interface PuPurchaseReceivingService {

    /**
     * 根据采购订单创建到货通知单并自动生成物料收货单
     *
     * @param orderId 采购订单ID
     * @return 到货通知单ID
     */
    Long createArrivalNoticeAndReceive(Long orderId);
}
