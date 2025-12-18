package com.nexus.core.prd.service;

import java.util.List;
import com.nexus.core.prd.domain.PrdWorkOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 生产工单Service接口
 *
 * @author nexus
 * @date 2025-12-16
 */
public interface IPrdWorkOrderService extends IService<PrdWorkOrder> {
    /**
     * 查询生产工单
     *
     * @param woId 生产工单主键
     * @return 生产工单
     */
    public PrdWorkOrder selectPrdWorkOrderByWoId(Long woId);

    /**
     * 查询生产工单列表
     *
     * @param prdWorkOrder 生产工单
     * @return 生产工单集合
     */
    public List<PrdWorkOrder> selectPrdWorkOrderList(PrdWorkOrder prdWorkOrder);

    /**
     * 新增生产工单
     *
     * @param prdWorkOrder 生产工单
     * @return 结果
     */
    public int insertPrdWorkOrder(PrdWorkOrder prdWorkOrder);

    /**
     * 修改生产工单
     *
     * @param prdWorkOrder 生产工单
     * @return 结果
     */
    public int updatePrdWorkOrder(PrdWorkOrder prdWorkOrder);

    /**
     * 批量删除生产工单
     *
     * @param woIds 需要删除的生产工单主键集合
     * @return 结果
     */
    public int deletePrdWorkOrderByWoIds(Long[] woIds);

    /**
     * 删除生产工单信息
     *
     * @param woId 生产工单主键
     * @return 结果
     */
    public int deletePrdWorkOrderByWoId(Long woId);
}
