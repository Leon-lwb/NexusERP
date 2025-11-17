package com.nexus.core.prd.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.prd.domain.PrdProductionPlan;

/**
 * 生产计划Mapper接口
 *
 * @author nexus
 * @date 2025-11-13
 */
public interface PrdProductionPlanMapper extends BaseMapper<PrdProductionPlan> {
    /**
     * 查询生产计划
     *
     * @param planId 生产计划主键
     * @return 生产计划
     */
    public PrdProductionPlan selectPrdProductionPlanByPlanId(Long planId);

    /**
     * 查询生产计划列表
     *
     * @param prdProductionPlan 生产计划
     * @return 生产计划集合
     */
    public List<PrdProductionPlan> selectPrdProductionPlanList(PrdProductionPlan prdProductionPlan);

    /**
     * 新增生产计划
     *
     * @param prdProductionPlan 生产计划
     * @return 结果
     */
    public int insertPrdProductionPlan(PrdProductionPlan prdProductionPlan);

    /**
     * 修改生产计划
     *
     * @param prdProductionPlan 生产计划
     * @return 结果
     */
    public int updatePrdProductionPlan(PrdProductionPlan prdProductionPlan);

    /**
     * 删除生产计划
     *
     * @param planId 生产计划主键
     * @return 结果
     */
    public int deletePrdProductionPlanByPlanId(Long planId);

    /**
     * 批量删除生产计划
     *
     * @param planIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deletePrdProductionPlanByPlanIds(Long[] planIds);
}
