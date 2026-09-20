package com.nexus.mes.tg.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.tg.domain.TgMaintenancePlan;
import com.nexus.mes.tg.domain.bo.TgMaintenancePlanBo;
import com.nexus.mes.tg.domain.vo.TgMaintenancePlanVo;
import com.nexus.mes.tg.mapper.TgMaintenancePlanMapper;
import com.nexus.mes.tg.service.ITgMaintenancePlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;

/**
 * MES工装保养计划 Service 业务层实现
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("tgMaintenancePlanService")
public class TgMaintenancePlanServiceImpl implements ITgMaintenancePlanService {

    private final TgMaintenancePlanMapper baseMapper;

    @Override
    public TableDataInfo<TgMaintenancePlanVo> selectPageList(TgMaintenancePlanBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<TgMaintenancePlan> lqw = buildQueryWrapper(bo);
        Page<TgMaintenancePlanVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public TgMaintenancePlanVo selectById(Long planId) {
        return baseMapper.selectVoById(planId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(TgMaintenancePlanBo bo) {
        // MES-TG-07: 同一工装同一触发类型只允许一条计划(与 uk_tooling_trigger 唯一约束同口径),
        // 避免 listMaintenanceDue 逐条判断导致到期列表重复行
        Long dup = baseMapper.selectCount(Wrappers.<TgMaintenancePlan>lambdaQuery()
            .eq(TgMaintenancePlan::getToolingId, bo.getToolingId())
            .eq(TgMaintenancePlan::getTriggerType, bo.getTriggerType()));
        if (dup != null && dup > 0) {
            throw new RuntimeException("该工装已存在同类触发方式的保养计划(" + bo.getTriggerType()
                + "),请直接修改原计划");
        }
        TgMaintenancePlan plan = new TgMaintenancePlan();
        plan.setToolingId(bo.getToolingId());
        plan.setPlanName(bo.getPlanName());
        plan.setTriggerType(bo.getTriggerType());
        plan.setTriggerValue(bo.getTriggerValue());
        plan.setEnabled(bo.getEnabled() == null ? "1" : bo.getEnabled());
        plan.setRemark(bo.getRemark());
        // 新建计划: 按 trigger_type 计算下次到期(相对当前时间,因无 last_maintain_time)
        calcNextDueOnCreate(plan, bo);
        int rows = baseMapper.insert(plan);
        log.info("新增工装保养计划: toolingId={}, triggerType={}", bo.getToolingId(), bo.getTriggerType());
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(TgMaintenancePlanBo bo) {
        TgMaintenancePlan plan = baseMapper.selectById(bo.getPlanId());
        if (plan == null) {
            throw new RuntimeException("保养计划不存在: " + bo.getPlanId());
        }
        plan.setToolingId(bo.getToolingId());
        plan.setPlanName(bo.getPlanName());
        plan.setTriggerType(bo.getTriggerType());
        plan.setTriggerValue(bo.getTriggerValue());
        plan.setEnabled(bo.getEnabled());
        plan.setRemark(bo.getRemark());
        // MES-TG-06: next_maintain_count/hours/time 是「寿命双轨」的到期阈值, 只能由
        // TgToolingServiceImpl.updatePlanAfterMaintain(保养完成) 维护。
        // 此前无条件采信入参, 有权限者可把 next_maintain_time 改到明年从而绕过到期拦截。
        // 本端点刻意不写这三列(客户端传值一律忽略); 新建时由 calcNextDueOnCreate 计算。
        int rows = baseMapper.updateById(plan);
        return rows > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        return baseMapper.deleteByIds(java.util.Arrays.asList(ids)) > 0;
    }

    @Override
    public Collection<TgMaintenancePlanVo> listEnabledByToolingId(Long toolingId) {
        return baseMapper.selectVoList(Wrappers.<TgMaintenancePlan>lambdaQuery()
            .eq(TgMaintenancePlan::getToolingId, toolingId)
            .eq(TgMaintenancePlan::getEnabled, "1"));
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 新建计划时按 trigger_type 计算下次到期阈值/时间
     */
    private void calcNextDueOnCreate(TgMaintenancePlan plan, TgMaintenancePlanBo bo) {
        String tt = bo.getTriggerType();
        Integer tv = bo.getTriggerValue();
        if (tv == null || tv <= 0) {
            return;
        }
        if (TRIGGER_BY_COUNT.equals(tt)) {
            plan.setNextMaintainCount(tv);
        } else if (TRIGGER_BY_HOURS.equals(tt)) {
            plan.setNextMaintainHours(BigDecimal.valueOf(tv));
        } else if (TRIGGER_PERIODIC.equals(tt)) {
            plan.setNextMaintainTime(new Date(System.currentTimeMillis() + (long) tv * 24 * 60 * 60 * 1000L));
        }
    }

    private LambdaQueryWrapper<TgMaintenancePlan> buildQueryWrapper(TgMaintenancePlanBo bo) {
        return Wrappers.<TgMaintenancePlan>lambdaQuery()
            .eq(bo.getToolingId() != null, TgMaintenancePlan::getToolingId, bo.getToolingId())
            .like(StringUtils.isNotBlank(bo.getPlanName()), TgMaintenancePlan::getPlanName, bo.getPlanName())
            .eq(StringUtils.isNotBlank(bo.getTriggerType()), TgMaintenancePlan::getTriggerType, bo.getTriggerType())
            .eq(StringUtils.isNotBlank(bo.getEnabled()), TgMaintenancePlan::getEnabled, bo.getEnabled())
            .orderByDesc(TgMaintenancePlan::getCreateTime);
    }
}
