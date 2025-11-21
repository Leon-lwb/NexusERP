package com.nexus.core.prd.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;

import com.nexus.common.constant.NexusConstants;
import com.nexus.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.nexus.core.prd.mapper.PrdProductionPlanMapper;
import com.nexus.core.prd.domain.PrdProductionPlan;
import com.nexus.core.prd.service.IPrdProductionPlanService;
import com.nexus.common.exception.ServiceException;
import com.nexus.common.utils.SecurityUtils;
import cn.hutool.core.bean.BeanUtil;

import static com.nexus.common.constant.NexusConstants.*;

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

    /**
     * 更新生产计划版本
     *
     * @param prdProductionPlan
     * @return
     */
    @Override
    public int updatePrdProductionPlanVersion(PrdProductionPlan prdProductionPlan) {

        // 查询原生产计划
        PrdProductionPlan originalPlan = baseMapper.selectPrdProductionPlanByPlanId(prdProductionPlan.getPlanId());
        if (originalPlan == null) {
            throw new ServiceException("生产计划不存在");
        }

        // 检查状态是否为草稿(DRAFT)
        if (!NexusConstants.STATUS_DRAFT.equals(originalPlan.getStatus())) {
            throw new ServiceException("该生产计划不为草稿，无法保存！");
        }

        // 创建新版本的生产计划
        PrdProductionPlan newPlan = new PrdProductionPlan();
        // 复制原计划的所有属性
        BeanUtil.copyProperties(prdProductionPlan, newPlan);
        // 版本号加1
        if (originalPlan.getVersion() != null) {
            newPlan.setVersion(originalPlan.getVersion() + 1);
        } else {
            newPlan.setVersion(1L);
        }
        // 设置更新时间和更新人
        newPlan.setUpdateTime(DateUtils.getNowDate());
        String username = SecurityUtils.getUsername();
        newPlan.setUpdateBy(username);
        // 设置为当前版本
        newPlan.setIsCurrent(NexusConstants.VERSION_INIT);
        // 修改原计划为非当前版本
        originalPlan.setIsCurrent(NexusConstants.VERSION_NOT_CURRENT);
        //updateById(originalPlan);
        baseMapper.updatePrdProductionPlan(originalPlan);

        // 插入新版本的生产计划
        return prdProductionPlanMapper.insertPrdProductionPlan(newPlan);
    }
}