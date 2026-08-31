package com.nexus.mes.pda.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.domain.EqMaintenancePlan;
import com.nexus.mes.eq.domain.EqMaintenanceRecord;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.mapper.EqMaintenancePlanMapper;
import com.nexus.mes.eq.mapper.EqMaintenanceRecordMapper;
import com.nexus.mes.eq.service.IEqMaintenancePlanService;
import com.nexus.mes.eq.service.IEqMaintenanceRecordService;
import com.nexus.mes.pda.domain.bo.PdaMaintExecuteBo;
import com.nexus.mes.pda.domain.vo.MesActionResultVo;
import com.nexus.mes.pda.domain.vo.PdaEqMaintDetailVo;
import com.nexus.mes.pda.domain.vo.PdaEqMaintItemVo;
import com.nexus.mes.pda.service.IPdaEqMaintService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PDA 保养维保作业实现
 * <p>
 * 到期计划查询(ACTIVE 且 nextDate<=今天) + 执行落 {@code eq_maintenance_record}
 * + 到期执行时推进 {@code eq_maintenance_plan.next_date}(今天+周期, 逾期不顺延叠加)。
 * 计划维护(建计划/停用/自动算下次日期)仍在 PC 端完成。
 */
@DS("mes")
@Service
@RequiredArgsConstructor
public class PdaEqMaintServiceImpl implements IPdaEqMaintService {

    private static final String RESULT_ABNORMAL = "ABNORMAL";

    private final EqMaintenancePlanMapper planMapper;
    private final EqMaintenanceRecordMapper recordMapper;
    private final EqEquipmentMapper equipmentMapper;

    @Override
    public TableDataInfo<PdaEqMaintItemVo> todo(String keyword, PageQuery pageQuery) {
        Date today = DateUtil.beginOfDay(new Date());

        // keyword 命中的设备 (扫设备码/名称), 未命中设备时仍按计划名匹配
        final List<Long> equipmentIds;
        if (StringUtils.isNotBlank(keyword)) {
            equipmentIds = equipmentMapper.selectList(Wrappers.<EqEquipment>lambdaQuery()
                    .like(EqEquipment::getEquipmentCode, keyword)
                    .or()
                    .like(EqEquipment::getEquipmentName, keyword))
                .stream().map(EqEquipment::getEquipmentId).collect(Collectors.toList());
        } else {
            equipmentIds = null;
        }

        Page<EqMaintenancePlan> page = planMapper.selectPage(pageQuery.build(),
            Wrappers.<EqMaintenancePlan>lambdaQuery()
                .eq(EqMaintenancePlan::getStatus, IEqMaintenancePlanService.STATUS_ACTIVE)
                .le(EqMaintenancePlan::getNextDate, today)
                .and(StringUtils.isNotBlank(keyword), w -> {
                    if (equipmentIds != null && !equipmentIds.isEmpty()) {
                        w.in(EqMaintenancePlan::getEquipmentId, equipmentIds);
                        w.or();
                    }
                    w.like(EqMaintenancePlan::getPlanName, keyword);
                })
                .orderByAsc(EqMaintenancePlan::getNextDate));

        List<Long> planEquipmentIds = page.getRecords().stream()
            .map(EqMaintenancePlan::getEquipmentId).distinct().collect(Collectors.toList());
        Map<Long, EqEquipment> equipmentMap = planEquipmentIds.isEmpty() ? Map.of()
            : equipmentMapper.selectByIds(planEquipmentIds).stream()
                .collect(Collectors.toMap(EqEquipment::getEquipmentId, Function.identity()));

        List<PdaEqMaintItemVo> items = new ArrayList<>(page.getRecords().size());
        for (EqMaintenancePlan plan : page.getRecords()) {
            PdaEqMaintItemVo item = new PdaEqMaintItemVo();
            item.setId(plan.getPlanId());
            item.setPlanName(plan.getPlanName());
            item.setMaintenanceType(plan.getMaintenanceType());
            item.setNextDate(plan.getNextDate());
            item.setOverdueDays(overdueDays(plan.getNextDate(), today));
            item.setContent(plan.getContent());
            item.setResponsibleName(plan.getResponsibleName());
            EqEquipment eq = equipmentMap.get(plan.getEquipmentId());
            if (eq != null) {
                item.setEquipmentCode(eq.getEquipmentCode());
                item.setEquipmentName(eq.getEquipmentName());
                item.setEquipmentStatus(eq.getStatus());
            }
            items.add(item);
        }
        return new TableDataInfo<>(items, page.getTotal());
    }

    @Override
    public PdaEqMaintDetailVo detail(Long planId) {
        EqMaintenancePlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new ServiceException("保养计划不存在");
        }
        PdaEqMaintDetailVo vo = new PdaEqMaintDetailVo();
        vo.setPlanId(plan.getPlanId());
        vo.setPlanName(plan.getPlanName());
        vo.setMaintenanceType(plan.getMaintenanceType());
        vo.setCycleDays(plan.getCycleDays());
        vo.setNextDate(plan.getNextDate());
        vo.setContent(plan.getContent());
        vo.setResponsibleName(plan.getResponsibleName());

        EqEquipment eq = plan.getEquipmentId() == null ? null : equipmentMapper.selectById(plan.getEquipmentId());
        if (eq != null) {
            vo.setEquipmentId(eq.getEquipmentId());
            vo.setEquipmentCode(eq.getEquipmentCode());
            vo.setEquipmentName(eq.getEquipmentName());
            vo.setEquipmentStatus(eq.getStatus());
            vo.setWorkcenterCode(eq.getWorkcenterCode());
        }

        List<PdaEqMaintDetailVo.PdaMaintRecordVo> records = recordMapper.selectList(
                Wrappers.<EqMaintenanceRecord>lambdaQuery()
                    .eq(EqMaintenanceRecord::getPlanId, planId)
                    .orderByDesc(EqMaintenanceRecord::getExecuteTime)
                    .last("LIMIT 5"))
            .stream().map(r -> {
                PdaEqMaintDetailVo.PdaMaintRecordVo rv = new PdaEqMaintDetailVo.PdaMaintRecordVo();
                rv.setExecuteTime(r.getExecuteTime());
                rv.setExecutorName(r.getExecutorName());
                rv.setResult(r.getResult());
                rv.setContent(r.getContent());
                rv.setRemark(r.getRemark());
                return rv;
            })
            .collect(Collectors.toList());
        vo.setLastRecords(records);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesActionResultVo execute(Long planId, PdaMaintExecuteBo bo) {
        EqMaintenancePlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new ServiceException("保养计划不存在");
        }
        if (!IEqMaintenancePlanService.STATUS_ACTIVE.equals(plan.getStatus())) {
            return MesActionResultVo.fail("保养计划已停用");
        }
        String result = StringUtils.isNotBlank(bo.getResult()) ? bo.getResult()
            : IEqMaintenanceRecordService.RESULT_NORMAL;
        if (!IEqMaintenanceRecordService.RESULT_NORMAL.equals(result) && !RESULT_ABNORMAL.equals(result)) {
            return MesActionResultVo.fail("保养结果仅支持 NORMAL/ABNORMAL");
        }

        // 1. 落保养记录
        EqMaintenanceRecord record = new EqMaintenanceRecord();
        record.setPlanId(plan.getPlanId());
        record.setEquipmentId(plan.getEquipmentId());
        record.setMaintenanceType(plan.getMaintenanceType());
        record.setExecutorId(LoginHelper.getUserId());
        record.setExecutorName(LoginHelper.getUsername());
        record.setExecuteTime(new Date());
        record.setContent(StringUtils.isNotBlank(bo.getContent()) ? bo.getContent() : plan.getContent());
        record.setResult(result);
        record.setRemark(bo.getRemark());
        recordMapper.insert(record);

        // 2. 到期执行推进下次保养日期 (提前保养不推进, 防同周期重复执行重复顺延)
        Date today = DateUtil.beginOfDay(new Date());
        if (plan.getNextDate() == null || !plan.getNextDate().after(today)) {
            int cycle = plan.getCycleDays() == null ? 0 : plan.getCycleDays();
            plan.setNextDate(DateUtil.offsetDay(today, cycle));
            planMapper.updateById(plan);
        }

        return MesActionResultVo.ok(RESULT_ABNORMAL.equals(result)
            ? "保养已记录：存在异常，请及时报修处理"
            : "保养已记录");
    }

    private Integer overdueDays(Date nextDate, Date today) {
        if (nextDate == null) {
            return null;
        }
        return (int) DateUtil.betweenDay(nextDate, today, true);
    }
}
