package com.nexus.core.prd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.prd.mapper.PrdWorkOrderMapper;
import com.nexus.core.prd.domain.PrdWorkOrder;
import com.nexus.core.prd.service.IPrdWorkOrderService;

/**
 * 生产工单Service业务层处理
 *
 * @author nexus
 * @date 2025-12-16
 */
@Service
public class PrdWorkOrderServiceImpl extends ServiceImpl<PrdWorkOrderMapper, PrdWorkOrder> implements IPrdWorkOrderService {

    @Autowired
    private PrdWorkOrderMapper prdWorkOrderMapper;

    /**
     * 查询生产工单
     *
     * @param woId 生产工单主键
     * @return 生产工单
     */
    @Override
    public PrdWorkOrder selectPrdWorkOrderByWoId(Long woId) {
        return prdWorkOrderMapper.selectPrdWorkOrderByWoId(woId);
    }

    /**
     * 查询生产工单列表
     *
     * @param prdWorkOrder 生产工单
     * @return 生产工单
     */
    @Override
    public List<PrdWorkOrder> selectPrdWorkOrderList(PrdWorkOrder prdWorkOrder) {
        return prdWorkOrderMapper.selectPrdWorkOrderList(prdWorkOrder);
    }

    /**
     * 新增生产工单
     *
     * @param prdWorkOrder 生产工单
     * @return 结果
     */
    @Override
    public int insertPrdWorkOrder(PrdWorkOrder prdWorkOrder) {
                prdWorkOrder.setCreateTime(DateUtils.getNowDate());
            return prdWorkOrderMapper.insertPrdWorkOrder(prdWorkOrder);
    }

    /**
     * 修改生产工单
     *
     * @param prdWorkOrder 生产工单
     * @return 结果
     */
    @Override
    public int updatePrdWorkOrder(PrdWorkOrder prdWorkOrder) {
                prdWorkOrder.setUpdateTime(DateUtils.getNowDate());
        return prdWorkOrderMapper.updatePrdWorkOrder(prdWorkOrder);
    }

    /**
     * 批量删除生产工单
     *
     * @param woIds 需要删除的生产工单主键
     * @return 结果
     */
    @Override
    public int deletePrdWorkOrderByWoIds(Long[] woIds) {
        return prdWorkOrderMapper.deletePrdWorkOrderByWoIds(woIds);
    }

    /**
     * 删除生产工单信息
     *
     * @param woId 生产工单主键
     * @return 结果
     */
    @Override
    public int deletePrdWorkOrderByWoId(Long woId) {
        return prdWorkOrderMapper.deletePrdWorkOrderByWoId(woId);
    }
}
