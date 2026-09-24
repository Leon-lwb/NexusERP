package com.nexus.erp.prd.schedule;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.bd.domain.BdRoutingStep;
import com.nexus.erp.bd.domain.BdWorkstation;
import com.nexus.erp.bd.domain.vo.BdRoutingVo;
import com.nexus.erp.bd.mapper.BdRoutingStepMapper;
import com.nexus.erp.bd.mapper.BdWorkstationMapper;
import com.nexus.erp.bd.service.IBdRoutingService;
import com.nexus.erp.prd.domain.PrdCapacityCalendar;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo;
import com.nexus.erp.prd.mapper.PrdCapacityCalendarMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduleInput;
import com.nexus.erp.prd.schedule.ScheduleModel.StepInfo;
import com.nexus.erp.prd.schedule.ScheduleModel.Strategy;
import com.nexus.erp.prd.schedule.ScheduleModel.WoInfo;
import com.nexus.erp.sch.domain.SchEmployeeSchedule;
import com.nexus.erp.sch.domain.SchHoliday;
import com.nexus.erp.sch.domain.SchLeaveApply;
import com.nexus.erp.sch.domain.SchShift;
import com.nexus.erp.sch.domain.SchTeam;
import com.nexus.erp.sch.mapper.SchEmployeeScheduleMapper;
import com.nexus.erp.sch.mapper.SchHolidayMapper;
import com.nexus.erp.sch.mapper.SchLeaveApplyMapper;
import com.nexus.erp.sch.mapper.SchShiftMapper;
import com.nexus.erp.sch.mapper.SchTeamMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * APS 排程输入装配器。
 * <p>
 * 统一装配正式排产与 What-if 所需的工单、工艺路线、设备产能、节假日及人力产能，
 * 不负责排程结果持久化。
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrdScheduleInputAssembler {

    /**
     * 参与排产的工单状态: 已创建/已批准/已下达/生产中(未完工都可重排)
     */
    private static final List<String> SCHEDULABLE_STATUS =
        List.of("CREATED", "APPROVED", "RELEASED", "IN_PROGRESS");

    private final PrdWorkOrderMapper workOrderMapper;
    private final PrdCapacityCalendarMapper capacityCalendarMapper;
    private final SchHolidayMapper schHolidayMapper;
    private final SchEmployeeScheduleMapper employeeScheduleMapper;
    private final SchShiftMapper schShiftMapper;
    private final SchTeamMapper schTeamMapper;
    private final SchLeaveApplyMapper schLeaveApplyMapper;
    private final BdWorkstationMapper workstationMapper;
    private final IBdRoutingService routingService;
    private final BdRoutingStepMapper routingStepMapper;

    /**
     * 按正式排产口径装配指定车间(空为全部车间)的全部可排工单。
     */
    public ScheduleInput assemble(PrdSchedulePlanBo bo) {
        Strategy strategy = resolveStrategy(bo.getStrategy());
        List<PrdWorkOrder> workOrders = workOrderMapper.selectList(
            Wrappers.lambdaQuery(PrdWorkOrder.class)
                .in(PrdWorkOrder::getWoStatus, SCHEDULABLE_STATUS)
                .eq(bo.getWorkshopId() != null, PrdWorkOrder::getWorkshopId, bo.getWorkshopId()));
        return assemble(bo, strategy, workOrders);
    }

    /**
     * 仅装配明确指定的工单；请求为空、工单缺失或状态不可排时 fail-closed。
     */
    public ScheduleInput assemble(PrdSchedulePlanBo bo, Collection<Long> workOrderIds) {
        Strategy strategy = resolveStrategy(bo.getStrategy());
        LinkedHashSet<Long> requestedIds = normalizeWorkOrderIds(workOrderIds);
        List<PrdWorkOrder> selected = workOrderMapper.selectList(
            Wrappers.lambdaQuery(PrdWorkOrder.class)
                .in(PrdWorkOrder::getWoId, requestedIds));
        List<PrdWorkOrder> ordered = validateAndOrderScopedWorkOrders(requestedIds, selected);
        return assemble(bo, strategy, ordered);
    }

    private ScheduleInput assemble(PrdSchedulePlanBo bo, Strategy strategy, List<PrdWorkOrder> workOrders) {
        if (workOrders == null || workOrders.isEmpty()) {
            throw new ServiceException("没有可排产的工单(需存在 CREATED/APPROVED/RELEASED/IN_PROGRESS 状态工单)");
        }

        LocalDate startDate = toLocalDate(bo.getStartDate());
        int horizonDays = Math.max(1, bo.getHorizonDays() == null ? 14 : bo.getHorizonDays());

        Map<Long, List<StepInfo>> routingMap = new HashMap<>();
        List<WoInfo> woInfos = new ArrayList<>();
        for (PrdWorkOrder wo : workOrders) {
            WoInfo info = new WoInfo();
            info.setWoId(wo.getWoId());
            info.setWoNo(wo.getWoNo());
            info.setMaterialId(wo.getMaterialId());
            info.setMaterialCode(wo.getMaterialCode());
            info.setMaterialName(wo.getMaterialName());
            BigDecimal completed = wo.getCompletedQty() == null ? BigDecimal.ZERO : wo.getCompletedQty();
            BigDecimal planned = wo.getPlannedQty() == null ? BigDecimal.ZERO : wo.getPlannedQty();
            info.setRemainingQty(planned.subtract(completed).max(BigDecimal.ZERO));
            info.setPriority(wo.getPriority());
            info.setDueDate(wo.getPlanEndDate() == null ? null : toLocalDate(wo.getPlanEndDate()));
            info.setWorkshopId(wo.getWorkshopId());
            info.setWorkshopName(wo.getWorkshopName());
            woInfos.add(info);

            if (wo.getMaterialId() != null && !routingMap.containsKey(wo.getMaterialId())) {
                routingMap.put(wo.getMaterialId(), loadRoutingSteps(wo.getMaterialId()));
            }
        }

        LocalDate horizonEnd = startDate.plusDays(horizonDays - 1L);
        Map<LocalDate, Map<String, BigDecimal>> capacityByDate = loadCapacity(startDate, horizonEnd);
        Set<LocalDate> holidays = loadHolidays(startDate, horizonEnd);
        applyLaborCapacity(capacityByDate, startDate, horizonEnd);

        ScheduleInput input = new ScheduleInput();
        input.setWorkOrders(woInfos);
        input.setRoutingProvider(routingMap::get);
        input.setCapacityByDate(capacityByDate);
        input.setHolidays(holidays);
        input.setStartDate(startDate);
        input.setHorizonDays(horizonDays);
        input.setStrategy(strategy);
        return input;
    }

    private Strategy resolveStrategy(String strategyCode) {
        if (strategyCode != null) {
            String normalized = strategyCode.trim().toUpperCase(Locale.ROOT);
            if ("BASELINE_CURRENT".equals(normalized) || "CURRENT".equals(normalized)) {
                throw new ServiceException(
                    "CURRENT/BASELINE_CURRENT 为已保存排程基线，不能作为重排策略；请从已保存排程任务读取基线");
            }
        }
        return Strategy.parse(strategyCode);
    }

    private LinkedHashSet<Long> normalizeWorkOrderIds(Collection<Long> workOrderIds) {
        if (workOrderIds == null || workOrderIds.isEmpty()) {
            throw new ServiceException("指定工单ID不能为空");
        }
        LinkedHashSet<Long> requestedIds = new LinkedHashSet<>();
        for (Long workOrderId : workOrderIds) {
            if (workOrderId == null) {
                throw new ServiceException("指定工单ID不能为空");
            }
            requestedIds.add(workOrderId);
        }
        if (requestedIds.isEmpty()) {
            throw new ServiceException("指定工单ID不能为空");
        }
        return requestedIds;
    }

    private List<PrdWorkOrder> validateAndOrderScopedWorkOrders(LinkedHashSet<Long> requestedIds,
                                                                 List<PrdWorkOrder> selected) {
        Map<Long, PrdWorkOrder> byId = new LinkedHashMap<>();
        Set<Long> duplicateIds = new LinkedHashSet<>();
        Set<Long> unexpectedIds = new LinkedHashSet<>();
        if (selected != null) {
            for (PrdWorkOrder workOrder : selected) {
                if (workOrder == null || workOrder.getWoId() == null) {
                    continue;
                }
                if (!requestedIds.contains(workOrder.getWoId())) {
                    unexpectedIds.add(workOrder.getWoId());
                    continue;
                }
                if (byId.putIfAbsent(workOrder.getWoId(), workOrder) != null) {
                    duplicateIds.add(workOrder.getWoId());
                }
            }
        }
        if (!unexpectedIds.isEmpty()) {
            throw new ServiceException("指定工单查询返回了未请求的工单: {}", unexpectedIds);
        }
        if (!duplicateIds.isEmpty()) {
            throw new ServiceException("指定工单查询结果重复: {}", duplicateIds);
        }

        List<Long> missingIds = requestedIds.stream()
            .filter(id -> !byId.containsKey(id))
            .toList();
        if (!missingIds.isEmpty()) {
            throw new ServiceException("指定工单不存在或未完整返回: {}", missingIds);
        }

        List<String> unschedulable = requestedIds.stream()
            .map(byId::get)
            .filter(wo -> !SCHEDULABLE_STATUS.contains(wo.getWoStatus()))
            .map(wo -> wo.getWoId() + "(" + wo.getWoStatus() + ")")
            .toList();
        if (!unschedulable.isEmpty()) {
            throw new ServiceException(
                "指定工单不可排产(仅支持 CREATED/APPROVED/RELEASED/IN_PROGRESS): {}", unschedulable);
        }

        return requestedIds.stream().map(byId::get).toList();
    }

    /**
     * 加载物料当前版本工艺路线步骤(无则空列表 → 引擎跳过该工单)
     */
    private List<StepInfo> loadRoutingSteps(Long materialId) {
        BdRoutingVo routing = routingService.getCurrentRouting(materialId);
        if (routing == null) {
            return List.of();
        }
        List<BdRoutingStep> steps = routingStepMapper.selectList(
            Wrappers.lambdaQuery(BdRoutingStep.class)
                .eq(BdRoutingStep::getRoutingId, routing.getRoutingId())
                .orderByAsc(BdRoutingStep::getSequenceNum));
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        return steps.stream().map(step -> {
            StepInfo info = new StepInfo();
            info.setStepId(step.getStepId());
            info.setSeqNum(step.getSequenceNum());
            info.setProcessCode(step.getProcessCode());
            info.setProcessName(step.getProcessName());
            info.setWorkstationId(step.getWorkstationId());
            info.setWorkstationCode(step.getWorkstationCode());
            info.setWorkstationName(step.getWorkstationName());
            info.setStandardHours(step.getStandardHours());
            info.setSetupHours(step.getSetupHours());
            info.setIsOutsource(step.getIsOutsource());
            info.setWaitTime(step.getWaitTime());
            info.setTransferTime(step.getTransferTime());
            return info;
        }).toList();
    }

    /**
     * 产能日历按日聚合: WORKSTATION 与 WORKSHOP 两种对象类型的剩余小时按资源键合并(多班次求和)
     */
    private Map<LocalDate, Map<String, BigDecimal>> loadCapacity(LocalDate start, LocalDate end) {
        List<PrdCapacityCalendar> calendars = capacityCalendarMapper.selectList(
            Wrappers.lambdaQuery(PrdCapacityCalendar.class)
                .eq(PrdCapacityCalendar::getStatus, "ACTIVE")
                .ge(PrdCapacityCalendar::getCalendarDate, toDate(start))
                .le(PrdCapacityCalendar::getCalendarDate, toDate(end)));
        Map<LocalDate, Map<String, BigDecimal>> capacity = new HashMap<>();
        if (calendars == null) {
            return capacity;
        }
        for (PrdCapacityCalendar calendar : calendars) {
            LocalDate day = toLocalDate(calendar.getCalendarDate());
            if (calendar.getObjectId() == null) {
                continue;
            }
            String key = ScheduleModel.resourceKey(calendar.getObjectType(), calendar.getObjectId());
            BigDecimal remaining = calendar.getRemainingHours() == null
                ? BigDecimal.ZERO : calendar.getRemainingHours();
            capacity.computeIfAbsent(day, ignored -> new HashMap<>()).merge(key, remaining, BigDecimal::add);
        }
        return capacity;
    }

    /**
     * 加载假期(排产起始日至跨度末日, 只取非工作日)
     */
    private Set<LocalDate> loadHolidays(LocalDate start, LocalDate end) {
        List<SchHoliday> holidays = schHolidayMapper.selectList(
            Wrappers.lambdaQuery(SchHoliday.class)
                .ge(SchHoliday::getHolidayDate, toDate(start))
                .le(SchHoliday::getHolidayDate, toDate(end)));
        Set<LocalDate> result = new HashSet<>();
        if (holidays == null) {
            return result;
        }
        for (SchHoliday holiday : holidays) {
            if (holiday.getIsWorkingDay() == null || holiday.getIsWorkingDay() == 0) {
                result.add(toLocalDate(holiday.getHolidayDate()));
            }
        }
        return result;
    }

    /**
     * 把排班/班次/请假折算的人力可用性并入产能视图。
     */
    private void applyLaborCapacity(Map<LocalDate, Map<String, BigDecimal>> capacityByDate,
                                    LocalDate start, LocalDate end) {
        List<SchEmployeeSchedule> schedules = employeeScheduleMapper.selectList(
            Wrappers.<SchEmployeeSchedule>lambdaQuery()
                .ge(SchEmployeeSchedule::getScheduledDate, toDate(start))
                .le(SchEmployeeSchedule::getScheduledDate, toDate(end)));
        if (schedules == null || schedules.isEmpty()) {
            log.info("APS人力产能: {} ~ {} 无排班数据, 沿用产能日历", start, end);
            return;
        }

        Set<Long> shiftIds = new HashSet<>();
        for (SchEmployeeSchedule schedule : schedules) {
            Long shiftId = schedule.getActualShiftId() != null
                ? schedule.getActualShiftId() : schedule.getShiftId();
            if (shiftId != null) {
                shiftIds.add(shiftId);
            }
        }
        Map<Long, BigDecimal> shiftHours = new HashMap<>();
        if (!shiftIds.isEmpty()) {
            for (SchShift shift : schShiftMapper.selectBatchIds(shiftIds)) {
                if (shift.getWorkHours() != null) {
                    shiftHours.put(shift.getShiftId(), shift.getWorkHours());
                }
            }
        }

        Set<Long> teamIds = schedules.stream()
            .map(SchEmployeeSchedule::getTeamId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<Long, Long> teamWorkshop = new HashMap<>();
        if (!teamIds.isEmpty()) {
            for (SchTeam team : schTeamMapper.selectBatchIds(teamIds)) {
                if (team.getWorkshopId() != null) {
                    teamWorkshop.put(team.getTeamId(), team.getWorkshopId());
                }
            }
        }

        Set<String> onLeave = loadApprovedLeaveKeys(start, end);
        List<PrdLaborCapacity.LaborRow> rows = new ArrayList<>();
        for (SchEmployeeSchedule schedule : schedules) {
            if (schedule.getIsWorkingDay() == null || schedule.getIsWorkingDay() != 1) {
                continue;
            }
            if ("rest".equalsIgnoreCase(schedule.getScheduleStatus())) {
                continue;
            }
            Long workshopId = schedule.getTeamId() == null ? null : teamWorkshop.get(schedule.getTeamId());
            if (workshopId == null || schedule.getScheduledDate() == null) {
                continue;
            }
            LocalDate day = toLocalDate(schedule.getScheduledDate());
            if (schedule.getWorkerId() != null
                && onLeave.contains(PrdLaborCapacity.leaveKey(schedule.getWorkerId(), day))) {
                continue;
            }
            PrdLaborCapacity.LaborRow row = new PrdLaborCapacity.LaborRow();
            row.setWorkshopId(workshopId);
            row.setScheduledDate(day);
            row.setWorkerId(schedule.getWorkerId());
            row.setHours(resolveShiftHours(schedule, shiftHours));
            rows.add(row);
        }

        Map<Long, Map<LocalDate, BigDecimal>> laborByWorkshop =
            PrdLaborCapacity.aggregate(rows, start, end);
        if (laborByWorkshop.isEmpty()) {
            log.info("APS人力产能: 排班未关联班组/车间, 无法折算人力, 沿用产能日历");
            return;
        }

        Map<Long, List<Long>> workstationsByShop = new HashMap<>();
        List<BdWorkstation> workstations = workstationMapper.selectList(
            Wrappers.<BdWorkstation>lambdaQuery()
                .in(BdWorkstation::getWorkshopId, laborByWorkshop.keySet()));
        for (BdWorkstation workstation : workstations) {
            workstationsByShop.computeIfAbsent(workstation.getWorkshopId(), ignored -> new ArrayList<>())
                .add(workstation.getWorkstationId());
        }
        PrdLaborCapacity.applyCap(capacityByDate, laborByWorkshop, workstationsByShop);
        log.info("APS人力产能: 已接入排班人力约束, 车间数={}, 有效排班行={}",
            laborByWorkshop.size(), rows.size());
    }

    /**
     * 单条排班工时：班次定义工时 → 排班起止时间折算 → 8h/人·日兜底
     */
    private BigDecimal resolveShiftHours(SchEmployeeSchedule schedule, Map<Long, BigDecimal> shiftHours) {
        Long shiftId = schedule.getActualShiftId() != null
            ? schedule.getActualShiftId() : schedule.getShiftId();
        BigDecimal hours = shiftId == null ? null : shiftHours.get(shiftId);
        if (hours != null && hours.signum() > 0) {
            return hours;
        }
        if (schedule.getStartTime() != null && schedule.getEndTime() != null
            && schedule.getEndTime().after(schedule.getStartTime())) {
            long minutes = (schedule.getEndTime().getTime() - schedule.getStartTime().getTime()) / 60000L;
            if (minutes > 0) {
                return BigDecimal.valueOf(minutes)
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
            }
        }
        return PrdLaborCapacity.DEFAULT_HOURS_PER_WORKER_DAY;
    }

    /**
     * 已通过的请假 → (工人ID|日期) 键集合，用于把请假人员从当日人力中剔除
     */
    private Set<String> loadApprovedLeaveKeys(LocalDate start, LocalDate end) {
        List<SchLeaveApply> leaves = schLeaveApplyMapper.selectList(
            Wrappers.<SchLeaveApply>lambdaQuery()
                .eq(SchLeaveApply::getApplyStatus, "approved")
                .le(SchLeaveApply::getStartDate, toDate(end))
                .ge(SchLeaveApply::getEndDate, toDate(start)));
        Set<String> keys = new HashSet<>();
        if (leaves == null) {
            return keys;
        }
        for (SchLeaveApply leave : leaves) {
            if (leave.getWorkerId() == null || leave.getStartDate() == null || leave.getEndDate() == null) {
                continue;
            }
            LocalDate from = toLocalDate(leave.getStartDate());
            LocalDate to = toLocalDate(leave.getEndDate());
            LocalDate day = from.isBefore(start) ? start : from;
            LocalDate last = to.isAfter(end) ? end : to;
            for (; !day.isAfter(last); day = day.plusDays(1)) {
                keys.add(PrdLaborCapacity.leaveKey(leave.getWorkerId(), day));
            }
        }
        return keys;
    }

    private LocalDate toLocalDate(Date date) {
        if (date instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}
