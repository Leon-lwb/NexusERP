package com.nexus.mes.eq.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.eq.domain.EqMaintenancePlan;
import com.nexus.mes.eq.domain.bo.EqMaintenancePlanBo;
import com.nexus.mes.eq.domain.vo.EqMaintenancePlanVo;
import com.nexus.mes.eq.mapper.EqMaintenancePlanMapper;
import com.nexus.mes.eq.service.IEqMaintenancePlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;

/**
 * MES设备保养计划 Service 业务层实现
 * <p>
 * 下次保养日期自动计算: 用户未显式指定 nextDate 时, nextDate = today + cycleDays;
 * cycleDays 为空时按 maintenanceType 默认周期(DAILY=1/WEEKLY=7/MONTHLY=30/QUARTERLY=90/YEARLY=365)。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("mes")
@Service("eqMaintenancePlanService")
public class EqMaintenancePlanServiceImpl implements IEqMaintenancePlanService {

    private final EqMaintenancePlanMapper baseMapper;

    @Override
    public TableDataInfo<EqMaintenancePlanVo> selectPageList(EqMaintenancePlanBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<EqMaintenancePlan> lqw = buildQueryWrapper(bo);
        Page<EqMaintenancePlanVo> page = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public EqMaintenancePlanVo selectById(Long planId) {
        return baseMapper.selectVoById(planId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqMaintenancePlanVo insertByBo(EqMaintenancePlanBo bo) {
        EqMaintenancePlan plan = new EqMaintenancePlan();
        plan.setPlanName(bo.getPlanName());
        plan.setEquipmentId(bo.getEquipmentId());
        plan.setMaintenanceType(bo.getMaintenanceType());
        // cycleDays 为空时按保养类型取默认周期
        Integer cycleDays = bo.getCycleDays() != null ? bo.getCycleDays() : defaultCycleDays(bo.getMaintenanceType());
        plan.setCycleDays(cycleDays);
        // nextDate 为空时自动计算 today + cycleDays
        if (bo.getNextDate() != null) {
            plan.setNextDate(bo.getNextDate());
        } else if (cycleDays != null) {
            plan.setNextDate(java.sql.Date.valueOf(LocalDate.now().plusDays(cycleDays)));
        }
        plan.setContent(bo.getContent());
        plan.setResponsibleId(bo.getResponsibleId());
        plan.setResponsibleName(bo.getResponsibleName());
        plan.setStatus(StringUtils.isNotBlank(bo.getStatus()) ? bo.getStatus() : STATUS_ACTIVE);
        plan.setRemark(bo.getRemark());
        baseMapper.insert(plan);
        log.info("新增保养计划: planId={}, equipmentId={}, nextDate={}", plan.getPlanId(), plan.getEquipmentId(), plan.getNextDate());
        return baseMapper.selectVoById(plan.getPlanId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqMaintenancePlanVo updateByBo(EqMaintenancePlanBo bo) {
        if (bo.getPlanId() == null) {
            throw new RuntimeException("计划ID不能为空");
        }
        EqMaintenancePlan plan = baseMapper.selectById(bo.getPlanId());
        if (plan == null) {
            throw new RuntimeException("保养计划不存在: " + bo.getPlanId());
        }
        plan.setPlanName(bo.getPlanName());
        plan.setEquipmentId(bo.getEquipmentId());
        if (StringUtils.isNotBlank(bo.getMaintenanceType())) {
            plan.setMaintenanceType(bo.getMaintenanceType());
        }
        // cycleDays 为空时按保养类型取默认周期
        Integer cycleDays = bo.getCycleDays() != null ? bo.getCycleDays() : defaultCycleDays(plan.getMaintenanceType());
        plan.setCycleDays(cycleDays);
        // 编辑时若未显式指定 nextDate, 按新 cycleDays 重算
        if (bo.getNextDate() != null) {
            plan.setNextDate(bo.getNextDate());
        } else if (cycleDays != null) {
            plan.setNextDate(java.sql.Date.valueOf(LocalDate.now().plusDays(cycleDays)));
        }
        plan.setContent(bo.getContent());
        plan.setResponsibleId(bo.getResponsibleId());
        plan.setResponsibleName(bo.getResponsibleName());
        if (StringUtils.isNotBlank(bo.getStatus())) {
            plan.setStatus(bo.getStatus());
        }
        plan.setRemark(bo.getRemark());
        baseMapper.updateById(plan);
        log.info("编辑保养计划: planId={}, nextDate={}", plan.getPlanId(), plan.getNextDate());
        return baseMapper.selectVoById(plan.getPlanId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] ids) {
        if (ids == null || ids.length == 0) {
            return false;
        }
        return baseMapper.deleteByIds(Arrays.asList(ids)) > 0;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 保养类型默认周期(天): DAILY=1/WEEKLY=7/MONTHLY=30/QUARTERLY=90/YEARLY=365
     */
    private Integer defaultCycleDays(String maintenanceType) {
        if (StringUtils.isBlank(maintenanceType)) {
            return null;
        }
        return switch (maintenanceType) {
            case TYPE_DAILY -> 1;
            case TYPE_WEEKLY -> 7;
            case TYPE_MONTHLY -> 30;
            case TYPE_QUARTERLY -> 90;
            case TYPE_YEARLY -> 365;
            default -> null;
        };
    }

    private LambdaQueryWrapper<EqMaintenancePlan> buildQueryWrapper(EqMaintenancePlanBo bo) {
        return Wrappers.<EqMaintenancePlan>lambdaQuery()
            .like(StringUtils.isNotBlank(bo.getPlanName()), EqMaintenancePlan::getPlanName, bo.getPlanName())
            .eq(bo.getEquipmentId() != null, EqMaintenancePlan::getEquipmentId, bo.getEquipmentId())
            .eq(StringUtils.isNotBlank(bo.getMaintenanceType()), EqMaintenancePlan::getMaintenanceType, bo.getMaintenanceType())
            .eq(StringUtils.isNotBlank(bo.getStatus()), EqMaintenancePlan::getStatus, bo.getStatus())
            .orderByAsc(EqMaintenancePlan::getNextDate)
            .orderByDesc(EqMaintenancePlan::getCreateTime);
    }
}
