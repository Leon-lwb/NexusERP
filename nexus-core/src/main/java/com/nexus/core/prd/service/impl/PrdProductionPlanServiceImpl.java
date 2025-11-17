package com.nexus.core.prd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.prd.mapper.PrdProductionPlanMapper;
import com.nexus.core.prd.domain.PrdProductionPlan;
import com.nexus.core.prd.service.IPrdProductionPlanService;

/**
 * 生产计划Service业务层处理
 *
 * @author nexus
 * @date 2025-11-13
 */
@Service
public class PrdProductionPlanServiceImpl extends ServiceImpl<PrdProductionPlanMapper, PrdProductionPlan> implements IPrdProductionPlanService {

    @Autowired
    private PrdProductionPlanMapper prdProductionPlanMapper;

    /**
     * 查询生产计划
     *
     * @param planId 生产计划主键
     * @return 生产计划
     */
    @Override
    public PrdProductionPlan selectPrdProductionPlanByPlanId(Long planId) {
        return prdProductionPlanMapper.selectPrdProductionPlanByPlanId(planId);
    }

    /**
     * 查询生产计划列表
     *
     * @param prdProductionPlan 生产计划
     * @return 生产计划
     */
    @Override
    public List<PrdProductionPlan> selectPrdProductionPlanList(PrdProductionPlan prdProductionPlan) {
        return prdProductionPlanMapper.selectPrdProductionPlanList(prdProductionPlan);
    }

    /**
     * 新增生产计划
     *
     * @param prdProductionPlan 生产计划
     * @return 结果
     */
    @Override
    public int insertPrdProductionPlan(PrdProductionPlan prdProductionPlan) {
                prdProductionPlan.setCreateTime(DateUtils.getNowDate());
            return prdProductionPlanMapper.insertPrdProductionPlan(prdProductionPlan);
    }

    /**
     * 修改生产计划
     *
     * @param prdProductionPlan 生产计划
     * @return 结果
     */
    @Override
    public int updatePrdProductionPlan(PrdProductionPlan prdProductionPlan) {
                prdProductionPlan.setUpdateTime(DateUtils.getNowDate());
        return prdProductionPlanMapper.updatePrdProductionPlan(prdProductionPlan);
    }

    /**
     * 批量删除生产计划
     *
     * @param planIds 需要删除的生产计划主键
     * @return 结果
     */
    @Override
    public int deletePrdProductionPlanByPlanIds(Long[] planIds) {
        return prdProductionPlanMapper.deletePrdProductionPlanByPlanIds(planIds);
    }

    /**
     * 删除生产计划信息
     *
     * @param planId 生产计划主键
     * @return 结果
     */
    @Override
    public int deletePrdProductionPlanByPlanId(Long planId) {
        return prdProductionPlanMapper.deletePrdProductionPlanByPlanId(planId);
    }
}
