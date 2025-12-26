package com.nexus.core.sch.service;

import java.util.List;
import com.nexus.core.sch.domain.SchPlan;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 排班计划Service接口
 *
 * @author nexus
 * @date 2025-12-25
 */
public interface ISchPlanService extends IService<SchPlan> {
    /**
     * 查询排班计划
     *
     * @param planId 排班计划主键
     * @return 排班计划
     */
    public SchPlan selectSchPlanByPlanId(Long planId);

    /**
     * 查询排班计划列表
     *
     * @param schPlan 排班计划
     * @return 排班计划集合
     */
    public List<SchPlan> selectSchPlanList(SchPlan schPlan);

    /**
     * 新增排班计划
     *
     * @param schPlan 排班计划
     * @return 结果
     */
    public int insertSchPlan(SchPlan schPlan);

    /**
     * 修改排班计划
     *
     * @param schPlan 排班计划
     * @return 结果
     */
    public int updateSchPlan(SchPlan schPlan);

    /**
     * 批量删除排班计划
     *
     * @param planIds 需要删除的排班计划主键集合
     * @return 结果
     */
    public int deleteSchPlanByPlanIds(Long[] planIds);

    /**
     * 删除排班计划信息
     *
     * @param planId 排班计划主键
     * @return 结果
     */
    public int deleteSchPlanByPlanId(Long planId);
}
