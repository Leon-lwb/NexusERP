package com.nexus.erp.prd.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.PrdSchedulePlan;
import com.nexus.erp.prd.domain.PrdScheduleTask;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo;
import com.nexus.erp.prd.domain.vo.PrdSchedulePlanVo;
import com.nexus.erp.prd.domain.vo.PrdScheduleTaskVo;
import com.nexus.erp.prd.domain.vo.ScheduleGanttVo;
import com.nexus.erp.prd.mapper.PrdSchedulePlanMapper;
import com.nexus.erp.prd.mapper.PrdScheduleTaskMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.schedule.PrdScheduleEngine;
import com.nexus.erp.prd.schedule.PrdScheduleInputAssembler;
import com.nexus.erp.prd.schedule.ScheduleModel.ResourceLoad;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduleInput;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduleResult;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduledTask;
import com.nexus.erp.prd.schedule.ScheduleModel.Strategy;
import com.nexus.erp.prd.schedule.ScheduleModel.WoInfo;
import com.nexus.erp.prd.service.IPrdScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * APS 智能排产 Service 实现。
 * <p>
 * 排程输入统一由 {@link PrdScheduleInputAssembler} 装配；本服务负责调用排程引擎以及方案/任务持久化、
 * 发布和删除。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("erp")
@Service
public class PrdScheduleServiceImpl implements IPrdScheduleService {

    private final PrdSchedulePlanMapper planMapper;
    private final PrdScheduleTaskMapper taskMapper;
    private final PrdWorkOrderMapper workOrderMapper;
    private final PrdScheduleInputAssembler scheduleInputAssembler;

    private static final String PLAN_STATUS_COMPLETED = "COMPLETED";
    private static final String PLAN_STATUS_PUBLISHED = "PUBLISHED";

    @Override
    public TableDataInfo<PrdSchedulePlanVo> selectPageList(PrdSchedulePlanBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<PrdSchedulePlan> lqw = buildQueryWrapper(bo);
        IPage<PrdSchedulePlanVo> page = planMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(page);
    }

    @Override
    public List<PrdScheduleTaskVo> selectTasksByPlanId(Long planId) {
        List<PrdScheduleTask> tasks = taskMapper.selectList(
            Wrappers.lambdaQuery(PrdScheduleTask.class).eq(PrdScheduleTask::getPlanId, planId));
        return tasks.stream()
            .map(t -> MapstructUtils.convert(t, PrdScheduleTaskVo.class))
            .sorted(Comparator.comparing(PrdScheduleTaskVo::getWoNo)
                .thenComparing(t -> t.getSeqNum() == null ? Integer.MAX_VALUE : t.getSeqNum()))
            .toList();
    }

    @Override
    public ScheduleGanttVo selectGantt(Long planId) {
        PrdSchedulePlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new ServiceException("排程方案不存在");
        }
        ScheduleGanttVo gantt = new ScheduleGanttVo();
        gantt.setPlanId(plan.getPlanId());
        gantt.setPlanNo(plan.getPlanNo());
        gantt.setStrategy(plan.getStrategy());
        gantt.setStartDate(plan.getStartDate());
        gantt.setHorizonDays(plan.getHorizonDays());
        gantt.setWoCount(plan.getWoCount());
        gantt.setDelayedCount(plan.getDelayedCount());
        gantt.setSkippedCount(plan.getSkippedCount());
        gantt.setTotalHours(plan.getTotalHours());

        // 交期映射(工单维度)
        Map<Long, Date> dueMap = new HashMap<>();
        List<PrdScheduleTaskVo> tasks = selectTasksByPlanId(planId);
        Map<Long, ScheduleGanttVo.GanttRow> rowMap = new HashMap<>();
        for (PrdScheduleTaskVo task : tasks) {
            ScheduleGanttVo.GanttRow row = rowMap.computeIfAbsent(task.getWoId(), k -> {
                ScheduleGanttVo.GanttRow r = new ScheduleGanttVo.GanttRow();
                r.setWoId(task.getWoId());
                r.setWoNo(task.getWoNo());
                r.setMaterialCode(task.getMaterialCode());
                r.setMaterialName(task.getMaterialName());
                return r;
            });
            ScheduleGanttVo.GanttBar bar = new ScheduleGanttVo.GanttBar();
            bar.setTaskId(task.getTaskId());
            bar.setSeqNum(task.getSeqNum());
            bar.setProcessName(task.getProcessName());
            bar.setResourceType(task.getResourceType());
            bar.setResourceId(task.getResourceId());
            bar.setResourceName(task.getResourceName());
            bar.setHours(task.getProcessHours());
            bar.setStartTime(task.getStartTime());
            bar.setEndTime(task.getEndTime());
            bar.setDurationDays(task.getDurationDays());
            row.getBars().add(bar);
            if (task.getDelayFlag() != null && task.getDelayFlag() == 1) {
                row.setDelay(true);
            }
        }
        // 工单交期: 从工单表补齐
        if (!rowMap.isEmpty()) {
            List<PrdWorkOrder> wos = workOrderMapper.selectBatchIds(rowMap.keySet());
            for (PrdWorkOrder wo : wos) {
                dueMap.put(wo.getWoId(), wo.getPlanEndDate());
            }
        }
        List<ScheduleGanttVo.GanttRow> rows = new ArrayList<>(rowMap.values());
        rows.forEach(r -> {
            r.getBars().sort(Comparator.comparing(b -> b.getSeqNum() == null ? Integer.MAX_VALUE : b.getSeqNum()));
            r.setDueDate(dueMap.get(r.getWoId()));
            if (r.getDelay() == null) {
                r.setDelay(false);
            }
        });
        // 拖期行置顶展示
        rows.sort(Comparator.comparing(ScheduleGanttVo.GanttRow::getDelay).reversed()
            .thenComparing(ScheduleGanttVo.GanttRow::getWoNo));
        gantt.setRows(rows);

        if (plan.getUtilizationJson() != null) {
            List<ResourceLoad> loads = JsonUtils.parseArray(plan.getUtilizationJson(), ResourceLoad.class);
            gantt.setLoads(loads.stream().map(l -> {
                ScheduleGanttVo.ResourceRateVo vo = new ScheduleGanttVo.ResourceRateVo();
                vo.setResourceKey(l.getResourceKey());
                vo.setResourceName(l.getResourceName());
                vo.setBookedHours(l.getBookedHours());
                vo.setAvailableHours(l.getAvailableHours());
                vo.setRate(l.getRate());
                return vo;
            }).toList());
        }
        return gantt;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrdSchedulePlanVo runSchedule(PrdSchedulePlanBo bo) {
        ScheduleInput input = scheduleInputAssembler.assemble(bo);
        ScheduleResult result = PrdScheduleEngine.schedule(input);
        LocalDate startDate = input.getStartDate();
        int horizonDays = input.getHorizonDays();
        Strategy strategy = input.getStrategy();
        List<WoInfo> woInfos = input.getWorkOrders();

        PrdSchedulePlan plan = new PrdSchedulePlan();
        plan.setPlanNo(generatePlanNo(startDate));
        plan.setPlanName(bo.getPlanName() != null && !bo.getPlanName().isBlank() ? bo.getPlanName()
            : "智能排产 " + plan.getPlanNo());
        plan.setWorkshopId(bo.getWorkshopId());
        plan.setWorkshopName(bo.getWorkshopName());
        plan.setStartDate(bo.getStartDate());
        plan.setHorizonDays(horizonDays);
        plan.setStrategy(strategy.name());
        plan.setWoCount(woInfos.size() - result.getSkipped().size());
        plan.setTaskCount(result.getTasks().size());
        plan.setSkippedCount(result.getSkipped().size());
        plan.setTotalHours(result.getTotalHours());
        plan.setDelayedCount(result.getDelayedWoCount());
        plan.setMakespanDate(result.getMakespan() == null ? null : toDate(result.getMakespan()));
        plan.setUtilizationJson(JsonUtils.toJsonString(result.getLoads()));
        plan.setStatus(PLAN_STATUS_COMPLETED);
        planMapper.insert(plan);

        for (ScheduledTask task : result.getTasks()) {
            PrdScheduleTask entity = toTaskEntity(plan.getPlanId(), task);
            taskMapper.insert(entity);
        }
        log.info("APS排程完成: planNo={}, 工单数={}, 任务数={}, 拖期={}, 跳过={}",
            plan.getPlanNo(), plan.getWoCount(), plan.getTaskCount(), plan.getDelayedCount(), plan.getSkippedCount());
        return MapstructUtils.convert(plan, PrdSchedulePlanVo.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean publish(Long planId) {
        PrdSchedulePlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new ServiceException("排程方案不存在");
        }
        if (PLAN_STATUS_PUBLISHED.equals(plan.getStatus())) {
            throw new ServiceException("方案已发布, 请勿重复操作");
        }
        // 末序任务日期回写工单计划开始/结束
        List<PrdScheduleTask> tasks = taskMapper.selectList(
            Wrappers.lambdaQuery(PrdScheduleTask.class).eq(PrdScheduleTask::getPlanId, planId));
        Map<Long, List<PrdScheduleTask>> byWo = tasks.stream().collect(Collectors.groupingBy(PrdScheduleTask::getWoId));
        for (Map.Entry<Long, List<PrdScheduleTask>> entry : byWo.entrySet()) {
            List<PrdScheduleTask> woTasks = entry.getValue().stream()
                .sorted(Comparator.comparing(t -> t.getSeqNum() == null ? Integer.MAX_VALUE : t.getSeqNum()))
                .toList();
            Date start = woTasks.get(0).getStartTime();
            Date end = woTasks.get(woTasks.size() - 1).getEndTime();
            PrdWorkOrder wo = workOrderMapper.selectById(entry.getKey());
            if (wo != null && !isTerminalStatus(wo.getWoStatus())) {
                wo.setPlanStartDate(start);
                wo.setPlanEndDate(end);
                workOrderMapper.updateById(wo);
            }
        }
        plan.setStatus(PLAN_STATUS_PUBLISHED);
        return planMapper.updateById(plan) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] planIds) {
        for (Long planId : planIds) {
            taskMapper.delete(Wrappers.lambdaQuery(PrdScheduleTask.class).eq(PrdScheduleTask::getPlanId, planId));
        }
        return planMapper.deleteByIds(List.of(planIds)) > 0;
    }

    private PrdScheduleTask toTaskEntity(Long planId, ScheduledTask task) {
        PrdScheduleTask entity = new PrdScheduleTask();
        entity.setPlanId(planId);
        entity.setWoId(task.getWo().getWoId());
        entity.setWoNo(task.getWo().getWoNo());
        entity.setMaterialCode(task.getWo().getMaterialCode());
        entity.setMaterialName(task.getWo().getMaterialName());
        entity.setSeqNum(task.getStep().getSeqNum());
        entity.setStepId(task.getStep().getStepId());
        entity.setProcessCode(task.getStep().getProcessCode());
        entity.setProcessName(task.getStep().getProcessName());
        entity.setResourceType(task.getResourceType());
        entity.setResourceId(task.getResourceId());
        entity.setResourceName(task.getResourceName());
        entity.setSetupHours(task.getSetupHours());
        entity.setProcessHours(task.getProcessHours());
        entity.setStartTime(toDate(task.getStartDay()));
        entity.setEndTime(endOfDay(task.getEndDay()));
        entity.setDurationDays(task.getDurationDays());
        entity.setDelayFlag(task.isDelay() ? 1 : 0);
        return entity;
    }

    private LambdaQueryWrapper<PrdSchedulePlan> buildQueryWrapper(PrdSchedulePlanBo bo) {
        LambdaQueryWrapper<PrdSchedulePlan> lqw = Wrappers.lambdaQuery(PrdSchedulePlan.class);
        if (bo != null) {
            lqw.like(bo.getPlanNo() != null && !bo.getPlanNo().isBlank(), PrdSchedulePlan::getPlanNo, bo.getPlanNo());
            lqw.like(bo.getPlanName() != null && !bo.getPlanName().isBlank(), PrdSchedulePlan::getPlanName, bo.getPlanName());
            lqw.eq(bo.getStrategy() != null && !bo.getStrategy().isBlank(), PrdSchedulePlan::getStrategy, bo.getStrategy());
            lqw.eq(bo.getStatus() != null && !bo.getStatus().isBlank(), PrdSchedulePlan::getStatus, bo.getStatus());
            lqw.eq(bo.getWorkshopId() != null, PrdSchedulePlan::getWorkshopId, bo.getWorkshopId());
        }
        lqw.orderByDesc(PrdSchedulePlan::getCreateTime);
        return lqw;
    }

    private boolean isTerminalStatus(String woStatus) {
        return "COMPLETED".equals(woStatus) || "CLOSED".equals(woStatus);
    }

    /**
     * 方案编号: APS+yyyyMMdd+4位日内流水(按当日已有方案数)
     */
    private String generatePlanNo(LocalDate startDate) {
        long todayCount = planMapper.selectCount(
            Wrappers.lambdaQuery(PrdSchedulePlan.class)
                .likeRight(PrdSchedulePlan::getPlanNo, "APS" + startDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)));
        return String.format("APS%s%04d", startDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
            todayCount + 1);
    }

    private Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private Date endOfDay(LocalDate date) {
        return Date.from(date.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant());
    }
}
