package com.nexus.core.prd.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.prd.domain.PrdWorkOrder;

/**
 * 生产工单Mapper接口
 *
 * @author nexus
 * @date 2025-12-16
 */
public interface PrdWorkOrderMapper extends BaseMapper<PrdWorkOrder> {
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
     * 删除生产工单
     *
     * @param woId 生产工单主键
     * @return 结果
     */
    public int deletePrdWorkOrderByWoId(Long woId);

    /**
     * 批量删除生产工单
     *
     * @param woIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deletePrdWorkOrderByWoIds(Long[] woIds);
}
