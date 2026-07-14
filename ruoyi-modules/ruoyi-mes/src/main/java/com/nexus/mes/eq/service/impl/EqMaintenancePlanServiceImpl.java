package com.nexus.mes.eq.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import java.util.Date;
import java.util.List;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.eq.domain.EqMaintenanceRecord;
import com.nexus.mes.eq.domain.bo.EqMaintenanceRecordBo;
import com.nexus.mes.eq.domain.vo.EqMaintenanceRecordVo;
import com.nexus.mes.eq.domain.vo.EqEquipmentVo;
import com.nexus.mes.eq.mapper.EqMaintenanceRecordMapper;
import com.nexus.mes.eq.service.IEqEquipmentService;
import com.nexus.mes.eq.service.IEqMaintenanceRecordService;

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
    private final EqMaintenanceRecordMapper maintenanceRecordMapper;
    private final IEqEquipmentService equipmentService;

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
    public List<EqMaintenancePlanVo> selectList(EqMaintenancePlanBo bo) {
        return baseMapper.selectVoList(buildQueryWrapper(bo));
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
            throw new ServiceException("计划ID不能为空");
        }
        EqMaintenancePlan plan = baseMapper.selectById(bo.getPlanId());
        if (plan == null) {
            throw new ServiceException("保养计划不存在: " + bo.getPlanId());
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EqMaintenanceRecordVo completePlan(Long planId, EqMaintenanceRecordBo bo) {
        EqMaintenancePlan plan = baseMapper.selectById(planId);
        if (plan == null) {
            throw new ServiceException("保养计划不存在: " + planId);
        }
        // 状态守卫: 仅 ACTIVE 计划可完成, 防止重复完成/已完成/停用计划产生重复记录并跳过保养周期
        if (!STATUS_ACTIVE.equals(plan.getStatus())) {
            throw new ServiceException("仅启用状态的保养计划可完成, 当前状态: " + plan.getStatus());
        }
        // 1. 写入保养执行记录(执行人=当前用户, 执行时间=当前时间)
        EqMaintenanceRecord record = new EqMaintenanceRecord();
        record.setPlanId(planId);
        record.setEquipmentId(plan.getEquipmentId());
        record.setMaintenanceType(plan.getMaintenanceType());
        record.setExecutorId(LoginHelper.getUserId());
        record.setExecutorName(LoginHelper.getUsername());
        record.setExecuteTime(new Date());
        record.setContent(bo == null ? null : bo.getContent());
        String result = (bo != null && StringUtils.isNotBlank(bo.getResult())) ? bo.getResult()
            : IEqMaintenanceRecordService.RESULT_NORMAL;
        record.setResult(result);
        record.setRemark(bo == null ? null : bo.getRemark());
        maintenanceRecordMapper.insert(record);
        log.info("保养计划 {} 完成执行, recordId={}", planId, record.getRecordId());

        // 2. 推进 nextDate = today + cycleDays
        Integer cycleDays = plan.getCycleDays();
        if (cycleDays == null || cycleDays <= 0) {
            cycleDays = defaultCycleDays(plan.getMaintenanceType());
        }
        if (cycleDays == null || cycleDays <= 0) {
            // 未知类型兜底 30 天
            cycleDays = 30;
        }
        java.util.Date newNextDate = java.sql.Date.valueOf(LocalDate.now().plusDays(cycleDays));
        LambdaUpdateWrapper<EqMaintenancePlan> wrapper = Wrappers.<EqMaintenancePlan>lambdaUpdate()
            .set(EqMaintenancePlan::getNextDate, newNextDate)
            .eq(EqMaintenancePlan::getPlanId, planId);
        baseMapper.update(null, wrapper);
        log.info("保养计划 {} 已完成, 推进 nextDate={}", planId, newNextDate);

        // 3. 保养结果异常: 置设备 MAINTENANCE(需后续处理), 触发设备状态变更事件(P0-5)
        if (IEqMaintenanceRecordService.RESULT_ABNORMAL.equals(result)) {
            equipmentService.updateStatus(plan.getEquipmentId(), IEqEquipmentService.STATUS_MAINTENANCE);
            log.info("保养结果异常, 设备置 MAINTENANCE: equipmentId={}", plan.getEquipmentId());
        }

        // 补填设备编码/名称, 与详情/列表接口响应结构一致(L2)
        EqMaintenanceRecordVo vo = maintenanceRecordMapper.selectVoById(record.getRecordId());
        if (vo != null && vo.getEquipmentId() != null) {
            EqEquipmentVo eq = equipmentService.selectById(vo.getEquipmentId());
            if (eq != null) {
                vo.setEquipmentCode(eq.getEquipmentCode());
                vo.setEquipmentName(eq.getEquipmentName());
            }
        }
        return vo;
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
