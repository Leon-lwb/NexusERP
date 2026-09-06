package com.nexus.erp.wm.service;

import com.nexus.erp.wm.domain.bo.WmSubcontractReceiveBo;
import com.nexus.erp.wm.domain.vo.WmSubcontractReceiveVo;

import java.util.List;

/**
 * 委外收货单 Service 接口 (PCBA 闭环)
 *
 * @author nexus
 */
public interface IWmSubcontractReceiveService {

    /**
     * 按委外订单查询收货单列表
     *
     * @param subcontractId 委外订单ID
     * @return 收货单列表
     */
    List<WmSubcontractReceiveVo> listBySubcontract(Long subcontractId);

    /**
     * 查询收货单详情
     *
     * @param receiveId 收货单ID
     * @return 收货单详情
     */
    WmSubcontractReceiveVo selectById(Long receiveId);

    /**
     * 创建委外收货单(默认目标仓=半成品仓路由, 可覆盖)
     *
     * @param bo 收货单业务对象
     * @return 收货单ID
     */
    Long createReceive(WmSubcontractReceiveBo bo);

    /**
     * 删除收货单(仅 DRAFT)
     *
     * @param receiveId 收货单ID
     * @return 删除行数
     */
    int deleteReceive(Long receiveId);

    /**
     * 收货过账: 订单维度加锁, 策略入库(自动建批次+精确库存行), 原子累加订单 actualQty,
     * 累计达计划量自动完工
     *
     * @param receiveId 收货单ID
     */
    void postReceive(Long receiveId);
}
