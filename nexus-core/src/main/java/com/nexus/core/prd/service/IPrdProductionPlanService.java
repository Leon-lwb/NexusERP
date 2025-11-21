package com.nexus.core.prd.service;

import java.util.List;
import com.nexus.core.prd.domain.PrdProductionPlan;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 生产计划Service接口
 *
 * @author nexus
 * @date 2025-11-13
 */
public interface IPrdProductionPlanService extends IService<PrdProductionPlan> {
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
     * 批量删除生产计划
     *
     * @param planIds 需要删除的生产计划主键集合
     * @return 结果
     */
    public int deletePrdProductionPlanByPlanIds(Long[] planIds);

    /**
     * 删除生产计划信息
     *
     * @param planId 生产计划主键
     * @return 结果
     */
    public int deletePrdProductionPlanByPlanId(Long planId);

    /**
     * 更新生产计划版本
     *
     * @param prdProductionPlan
     * @return 影响行数
     */
    int updatePrdProductionPlanVersion(PrdProductionPlan prdProductionPlan);
}
