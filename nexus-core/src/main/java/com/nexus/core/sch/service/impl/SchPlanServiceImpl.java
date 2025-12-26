package com.nexus.core.sch.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.sch.mapper.SchPlanMapper;
import com.nexus.core.sch.domain.SchPlan;
import com.nexus.core.sch.service.ISchPlanService;

/**
 * 排班计划Service业务层处理
 *
 * @author nexus
 * @date 2025-12-25
 */
@Service
public class SchPlanServiceImpl extends ServiceImpl<SchPlanMapper, SchPlan> implements ISchPlanService {

    @Autowired
    private SchPlanMapper schPlanMapper;

    /**
     * 查询排班计划
     *
     * @param planId 排班计划主键
     * @return 排班计划
     */
    @Override
    public SchPlan selectSchPlanByPlanId(Long planId) {
        return schPlanMapper.selectSchPlanByPlanId(planId);
    }

    /**
     * 查询排班计划列表
     *
     * @param schPlan 排班计划
     * @return 排班计划
     */
    @Override
    public List<SchPlan> selectSchPlanList(SchPlan schPlan) {
        return schPlanMapper.selectSchPlanList(schPlan);
    }

    /**
     * 新增排班计划
     *
     * @param schPlan 排班计划
     * @return 结果
     */
    @Override
    public int insertSchPlan(SchPlan schPlan) {
                schPlan.setCreateTime(DateUtils.getNowDate());
            return schPlanMapper.insertSchPlan(schPlan);
    }

    /**
     * 修改排班计划
     *
     * @param schPlan 排班计划
     * @return 结果
     */
    @Override
    public int updateSchPlan(SchPlan schPlan) {
                schPlan.setUpdateTime(DateUtils.getNowDate());
        return schPlanMapper.updateSchPlan(schPlan);
    }

    /**
     * 批量删除排班计划
     *
     * @param planIds 需要删除的排班计划主键
     * @return 结果
     */
    @Override
    public int deleteSchPlanByPlanIds(Long[] planIds) {
        return schPlanMapper.deleteSchPlanByPlanIds(planIds);
    }

    /**
     * 删除排班计划信息
     *
     * @param planId 排班计划主键
     * @return 结果
     */
    @Override
    public int deleteSchPlanByPlanId(Long planId) {
        return schPlanMapper.deleteSchPlanByPlanId(planId);
    }
}
