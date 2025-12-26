package com.nexus.core.sch.mapper;

import java.util.List;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.core.sch.domain.SchPlan;

/**
 * 排班计划Mapper接口
 *
 * @author nexus
 * @date 2025-12-25
 */
public interface SchPlanMapper extends BaseMapper<SchPlan> {
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
     * 删除排班计划
     *
     * @param planId 排班计划主键
     * @return 结果
     */
    public int deleteSchPlanByPlanId(Long planId);

    /**
     * 批量删除排班计划
     *
     * @param planIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteSchPlanByPlanIds(Long[] planIds);
}
