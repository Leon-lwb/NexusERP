package com.nexus.erp.prd.schedule;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.bd.domain.BdRouting;
import com.nexus.erp.bd.domain.BdRoutingStep;
import com.nexus.erp.bd.domain.BdWorkstation;
import com.nexus.erp.bd.mapper.BdRoutingMapper;
import com.nexus.erp.bd.mapper.BdRoutingStepMapper;
import com.nexus.erp.bd.mapper.BdWorkstationMapper;
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
import java.util.Comparator;
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
import java.util.TreeMap;
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
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

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
    private final BdRoutingMapper routingMapper;
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

        LinkedHashSet<Long> materialIds = workOrders.stream()
            .map(PrdWorkOrder::getMaterialId)
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, List<StepInfo>> routingMap = loadRoutingSteps(materialIds);
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
        }

        ResourceScope resourceScope = resourceScope(workOrders, routingMap);
        LocalDate horizonEnd = startDate.plusDays(horizonDays - 1L);
        Map<LocalDate, Map<String, BigDecimal>> capacityByDate =
            loadCapacity(startDate, horizonEnd, resourceScope);
        Set<LocalDate> holidays = loadHolidays(startDate, horizonEnd);
        applyLaborCapacity(capacityByDate, startDate, horizonEnd, resourceScope);

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
     * 批量加载物料当前有效路线及步骤。专属路线优先，通用路线兜底；重复 current active fail-closed。
     */
    private Map<Long, List<StepInfo>> loadRoutingSteps(LinkedHashSet<Long> materialIds) {
        LinkedHashMap<Long, List<StepInfo>> result = new LinkedHashMap<>();
        materialIds.forEach(materialId -> result.put(materialId, List.of()));
        if (materialIds.isEmpty()) {
            return result;
        }
        List<BdRouting> routings = routingMapper.selectList(
            Wrappers.lambdaQuery(BdRouting.class)
                .eq(BdRouting::getIsCurrent, 1L)
                .eq(BdRouting::getStatus, 1L)
                .and(query -> query.in(BdRouting::getMaterialId, materialIds)
                    .or().isNull(BdRouting::getMaterialId)));
        List<BdRouting> candidates = routings == null ? List.of() : routings.stream()
            .filter(routing -> Objects.equals(1L, routing.getIsCurrent()))
            .filter(routing -> Objects.equals(1L, routing.getStatus()))
            .filter(routing -> routing.getMaterialId() == null || materialIds.contains(routing.getMaterialId()))
            .sorted(Comparator.comparing(BdRouting::getRoutingId,
                Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        List<BdRouting> generic = candidates.stream()
            .filter(routing -> routing.getMaterialId() == null)
            .toList();
        if (generic.size() > 1) {
            throw new ServiceException("存在多条当前有效通用工艺路线: "
                + generic.stream().map(BdRouting::getRoutingId).toList());
        }
        LinkedHashMap<Long, BdRouting> selected = new LinkedHashMap<>();
        for (Long materialId : materialIds) {
            List<BdRouting> specific = candidates.stream()
                .filter(routing -> Objects.equals(materialId, routing.getMaterialId()))
                .toList();
            if (specific.size() > 1) {
                throw new ServiceException("物料 " + materialId + " 存在多条当前有效工艺路线: "
                    + specific.stream().map(BdRouting::getRoutingId).toList());
            }
            BdRouting routing = specific.isEmpty() ? (generic.isEmpty() ? null : generic.get(0)) : specific.get(0);
            if (routing != null && routing.getRoutingId() != null) {
                selected.put(materialId, routing);
            }
        }
        LinkedHashSet<Long> routingIds = selected.values().stream()
            .map(BdRouting::getRoutingId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, List<BdRoutingStep>> stepsByRouting = new TreeMap<>();
        if (!routingIds.isEmpty()) {
            List<BdRoutingStep> steps = routingStepMapper.selectList(
                Wrappers.lambdaQuery(BdRoutingStep.class)
                    .in(BdRoutingStep::getRoutingId, routingIds)
                    .orderByAsc(BdRoutingStep::getRoutingId, BdRoutingStep::getSequenceNum));
            if (steps != null) {
                for (BdRoutingStep step : steps) {
                    if (step.getRoutingId() != null && routingIds.contains(step.getRoutingId())) {
                        stepsByRouting.computeIfAbsent(step.getRoutingId(), ignored -> new ArrayList<>()).add(step);
                    }
                }
            }
        }
        for (Long materialId : materialIds) {
            BdRouting routing = selected.get(materialId);
            if (routing == null) {
                continue;
            }
            List<StepInfo> steps = stepsByRouting
                .getOrDefault(routing.getRoutingId(), List.of()).stream()
                .sorted(Comparator
                    .comparing(BdRoutingStep::getSequenceNum,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(BdRoutingStep::getStepId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toStepInfo)
                .toList();
            result.put(materialId, steps);
        }
        return result;
    }

    private StepInfo toStepInfo(BdRoutingStep step) {
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
    }

    private ResourceScope resourceScope(List<PrdWorkOrder> workOrders,
                                        Map<Long, List<StepInfo>> routingMap) {
        LinkedHashSet<Long> workshopIds = workOrders.stream()
            .map(PrdWorkOrder::getWorkshopId)
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<Long> workstationIds = workOrders.stream()
            .map(PrdWorkOrder::getWorkstationId)
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        routingMap.values().stream().flatMap(List::stream)
            .map(StepInfo::getWorkstationId)
            .filter(Objects::nonNull)
            .sorted()
            .forEach(workstationIds::add);
        List<BdWorkstation> scopedWorkstations = List.of();
        if (!workstationIds.isEmpty()) {
            List<BdWorkstation> returned = workstationMapper.selectList(
                Wrappers.lambdaQuery(BdWorkstation.class)
                    .in(BdWorkstation::getWorkstationId, workstationIds));
            if (returned != null) {
                scopedWorkstations = returned.stream()
                    .filter(workstation -> workstation.getWorkstationId() != null
                        && workstationIds.contains(workstation.getWorkstationId()))
                    .sorted(Comparator.comparing(BdWorkstation::getWorkstationId))
                    .toList();
                scopedWorkstations.stream()
                    .map(BdWorkstation::getWorkshopId)
                    .filter(Objects::nonNull)
                    .sorted()
                    .forEach(workshopIds::add);
            }
        }
        return new ResourceScope(workshopIds, workstationIds, scopedWorkstations);
    }

    /** 仅加载 scoped WORKSHOP/WORKSTATION 产能。 */
    private Map<LocalDate, Map<String, BigDecimal>> loadCapacity(LocalDate start,
                                                                 LocalDate end,
                                                                 ResourceScope scope) {
        Map<LocalDate, Map<String, BigDecimal>> capacity = new LinkedHashMap<>();
        if (scope.workshopIds().isEmpty() && scope.workstationIds().isEmpty()) {
            return capacity;
        }
        var query = Wrappers.lambdaQuery(PrdCapacityCalendar.class)
            .eq(PrdCapacityCalendar::getStatus, "ACTIVE")
            .ge(PrdCapacityCalendar::getCalendarDate, startOfDay(start))
            .lt(PrdCapacityCalendar::getCalendarDate, startOfDay(end.plusDays(1)));
        query.and(resources -> {
            boolean hasWorkshop = !scope.workshopIds().isEmpty();
            if (hasWorkshop) {
                resources.nested(resource -> resource
                    .eq(PrdCapacityCalendar::getObjectType, "WORKSHOP")
                    .in(PrdCapacityCalendar::getObjectId, scope.workshopIds()));
            }
            if (!scope.workstationIds().isEmpty()) {
                if (hasWorkshop) {
                    resources.or();
                }
                resources.nested(resource -> resource
                    .eq(PrdCapacityCalendar::getObjectType, "WORKSTATION")
                    .in(PrdCapacityCalendar::getObjectId, scope.workstationIds()));
            }
        });
        List<PrdCapacityCalendar> calendars = capacityCalendarMapper.selectList(query);
        if (calendars == null) {
            return capacity;
        }
        for (PrdCapacityCalendar calendar : calendars) {
            if (calendar.getObjectId() == null || calendar.getObjectType() == null) {
                continue;
            }
            String objectType = calendar.getObjectType().toUpperCase(Locale.ROOT);
            boolean related = "WORKSHOP".equals(objectType)
                ? scope.workshopIds().contains(calendar.getObjectId())
                : "WORKSTATION".equals(objectType)
                    && scope.workstationIds().contains(calendar.getObjectId());
            LocalDate day = toLocalDate(calendar.getCalendarDate());
            if (!related || day == null || day.isBefore(start) || day.isAfter(end)) {
                continue;
            }
            String key = ScheduleModel.resourceKey(objectType, calendar.getObjectId());
            BigDecimal remaining = calendar.getRemainingHours() == null
                ? BigDecimal.ZERO : calendar.getRemainingHours();
            capacity.computeIfAbsent(day, ignored -> new LinkedHashMap<>())
                .merge(key, remaining, BigDecimal::add);
        }
        return capacity;
    }

    /**
     * 加载假期(排产起始日至跨度末日, 只取非工作日)
     */
    private Set<LocalDate> loadHolidays(LocalDate start, LocalDate end) {
        List<SchHoliday> holidays = schHolidayMapper.selectList(
            Wrappers.lambdaQuery(SchHoliday.class)
                .ge(SchHoliday::getHolidayDate, startOfDay(start))
                .lt(SchHoliday::getHolidayDate, startOfDay(end.plusDays(1))));
        Set<LocalDate> result = new HashSet<>();
        if (holidays == null) {
            return result;
        }
        for (SchHoliday holiday : holidays) {
            if (holiday.getHolidayDate() == null) {
                continue;
            }
            LocalDate day = toLocalDate(holiday.getHolidayDate());
            if (!day.isBefore(start) && !day.isAfter(end)
                && (holiday.getIsWorkingDay() == null || holiday.getIsWorkingDay() == 0)) {
                result.add(day);
            }
        }
        return result;
    }

    /** 把 scoped 排班/班次/请假折算的人力可用性并入产能视图。 */
    private void applyLaborCapacity(Map<LocalDate, Map<String, BigDecimal>> capacityByDate,
                                    LocalDate start,
                                    LocalDate end,
                                    ResourceScope scope) {
        if (scope.workshopIds().isEmpty()) {
            return;
        }
        List<SchTeam> teams = schTeamMapper.selectList(
            Wrappers.lambdaQuery(SchTeam.class)
                .in(SchTeam::getWorkshopId, scope.workshopIds()));
        Map<Long, Long> teamWorkshop = new LinkedHashMap<>();
        if (teams != null) {
            teams.stream()
                .filter(team -> team.getTeamId() != null && team.getWorkshopId() != null)
                .filter(team -> scope.workshopIds().contains(team.getWorkshopId()))
                .sorted(Comparator.comparing(SchTeam::getTeamId))
                .forEach(team -> teamWorkshop.put(team.getTeamId(), team.getWorkshopId()));
        }
        if (teamWorkshop.isEmpty()) {
            log.info("APS人力产能: scoped 车间无班组, 沿用产能日历");
            return;
        }
        List<SchEmployeeSchedule> schedules = employeeScheduleMapper.selectList(
            Wrappers.lambdaQuery(SchEmployeeSchedule.class)
                .in(SchEmployeeSchedule::getTeamId, teamWorkshop.keySet())
                .ge(SchEmployeeSchedule::getScheduledDate, startOfDay(start))
                .lt(SchEmployeeSchedule::getScheduledDate, startOfDay(end.plusDays(1))));
        if (schedules == null || schedules.isEmpty()) {
            log.info("APS人力产能: {} ~ {} scoped 班组无排班数据, 沿用产能日历", start, end);
            return;
        }
        List<SchEmployeeSchedule> scopedSchedules = schedules.stream()
            .filter(schedule -> schedule.getTeamId() != null
                && teamWorkshop.containsKey(schedule.getTeamId()))
            .filter(schedule -> schedule.getScheduledDate() != null)
            .filter(schedule -> {
                LocalDate day = toLocalDate(schedule.getScheduledDate());
                return day != null && !day.isBefore(start) && !day.isAfter(end);
            })
            .toList();
        LinkedHashSet<Long> shiftIds = scopedSchedules.stream()
            .map(schedule -> schedule.getActualShiftId() != null
                ? schedule.getActualShiftId() : schedule.getShiftId())
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, BigDecimal> shiftHours = new LinkedHashMap<>();
        if (!shiftIds.isEmpty()) {
            for (SchShift shift : schShiftMapper.selectBatchIds(shiftIds)) {
                if (shift.getShiftId() != null && shiftIds.contains(shift.getShiftId())
                    && shift.getWorkHours() != null) {
                    shiftHours.put(shift.getShiftId(), shift.getWorkHours());
                }
            }
        }
        LinkedHashSet<Long> workerIds = scopedSchedules.stream()
            .map(SchEmployeeSchedule::getWorkerId)
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> onLeave = loadApprovedLeaveKeys(start, end, workerIds);
        List<PrdLaborCapacity.LaborRow> rows = new ArrayList<>();
        for (SchEmployeeSchedule schedule : scopedSchedules) {
            if (schedule.getIsWorkingDay() == null || schedule.getIsWorkingDay() != 1
                || "rest".equalsIgnoreCase(schedule.getScheduleStatus())) {
                continue;
            }
            Long workshopId = teamWorkshop.get(schedule.getTeamId());
            LocalDate day = toLocalDate(schedule.getScheduledDate());
            if (workshopId == null || day == null) {
                continue;
            }
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
            log.info("APS人力产能: scoped 排班无有效人力, 沿用产能日历");
            return;
        }
        Map<Long, List<Long>> workstationsByShop = new LinkedHashMap<>();
        for (BdWorkstation workstation : scope.workstations()) {
                if (workstation.getWorkstationId() == null || workstation.getWorkshopId() == null
                    || !laborByWorkshop.containsKey(workstation.getWorkshopId())) {
                    continue;
                }
                if (!scope.workstationIds().isEmpty()
                    && !scope.workstationIds().contains(workstation.getWorkstationId())
                    && !scope.workshopIds().contains(workstation.getWorkshopId())) {
                    continue;
                }
                workstationsByShop.computeIfAbsent(
                    workstation.getWorkshopId(), ignored -> new ArrayList<>())
                    .add(workstation.getWorkstationId());
            }
        PrdLaborCapacity.applyCap(capacityByDate, laborByWorkshop, workstationsByShop);
        log.info("APS人力产能: 已接入 scoped 排班约束, 车间数={}, 有效排班行={}",
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

    /** 已通过的 scoped 请假 → (工人ID|日期) 键集合。 */
    private Set<String> loadApprovedLeaveKeys(LocalDate start,
                                               LocalDate end,
                                               Set<Long> workerIds) {
        Set<String> keys = new HashSet<>();
        if (workerIds.isEmpty()) {
            return keys;
        }
        List<SchLeaveApply> leaves = schLeaveApplyMapper.selectList(
            Wrappers.lambdaQuery(SchLeaveApply.class)
                .eq(SchLeaveApply::getApplyStatus, "approved")
                .in(SchLeaveApply::getWorkerId, workerIds)
                .lt(SchLeaveApply::getStartDate, startOfDay(end.plusDays(1)))
                .ge(SchLeaveApply::getEndDate, startOfDay(start)));
        if (leaves == null) {
            return keys;
        }
        for (SchLeaveApply leave : leaves) {
            if (leave.getWorkerId() == null || !workerIds.contains(leave.getWorkerId())
                || leave.getStartDate() == null || leave.getEndDate() == null
                || !"approved".equalsIgnoreCase(leave.getApplyStatus())) {
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

    private record ResourceScope(
        Set<Long> workshopIds,
        Set<Long> workstationIds,
        List<BdWorkstation> workstations
    ) {
    }

    private LocalDate toLocalDate(Date date) {
        if (date instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return java.time.Instant.ofEpochMilli(date.getTime())
            .atZone(BUSINESS_ZONE).toLocalDate();
    }

    private Date startOfDay(LocalDate date) {
        return Date.from(date.atStartOfDay(BUSINESS_ZONE).toInstant());
    }
}
