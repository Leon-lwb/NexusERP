package com.nexus.erp.prd.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.prd.domain.PrdCapacityCalendar;
import com.nexus.erp.prd.domain.PrdSchedulePlan;
import com.nexus.erp.prd.domain.PrdScheduleCapacityBooking;
import com.nexus.erp.prd.domain.PrdScheduleTask;
import com.nexus.erp.prd.domain.PrdScheduleTaskDayLoad;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo;
import com.nexus.erp.prd.domain.vo.PrdSchedulePlanVo;
import com.nexus.erp.prd.domain.vo.PrdScheduleTaskVo;
import com.nexus.erp.prd.domain.vo.ScheduleGanttVo;
import com.nexus.erp.prd.mapper.PrdCapacityCalendarMapper;
import com.nexus.erp.prd.mapper.PrdScheduleCapacityBookingMapper;
import com.nexus.erp.prd.mapper.PrdSchedulePlanMapper;
import com.nexus.erp.prd.mapper.PrdScheduleTaskMapper;
import com.nexus.erp.prd.mapper.PrdScheduleTaskDayLoadMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.schedule.PrdScheduleEngine;
import com.nexus.erp.prd.schedule.PrdScheduleInputAssembler;
import com.nexus.erp.prd.schedule.PrdScheduleNumberAllocator;
import com.nexus.erp.prd.schedule.ScheduleModel.ResourceLoad;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduleInput;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduleResult;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduledTask;
import com.nexus.erp.prd.schedule.ScheduleModel.Strategy;
import com.nexus.erp.prd.schedule.ScheduleModel.WoInfo;
import com.nexus.erp.prd.service.IPrdCapacityCalendarService;
import com.nexus.erp.prd.service.IPrdScheduleService;
import com.nexus.erp.sch.domain.SchHoliday;
import com.nexus.erp.sch.domain.SchShift;
import com.nexus.erp.sch.mapper.SchHolidayMapper;
import com.nexus.erp.sch.mapper.SchShiftMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * APS 智能排产服务实现。
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
    private final PrdScheduleTaskDayLoadMapper taskDayLoadMapper;
    private final PrdScheduleCapacityBookingMapper bookingMapper;
    private final PrdWorkOrderMapper workOrderMapper;
    private final PrdScheduleInputAssembler scheduleInputAssembler;
    private final PrdScheduleNumberAllocator numberAllocator;
    private final PrdCapacityCalendarMapper capacityCalendarMapper;
    private final IPrdCapacityCalendarService capacityCalendarService;
    private final SchShiftMapper shiftMapper;
    private final SchHolidayMapper holidayMapper;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
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

        /** 交期映射(工单维度) */
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
        /** 工单交期: 从工单表补齐 */
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
        /** 拖期行置顶展示 */
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
        if (bo == null) {
            throw new ServiceException("排程参数不能为空");
        }
        /**
         * 普通界面/接口入口绝不能接收调用方提供的 AI 来源信息。
         * 只有限定作用域的内部端口可以持久化 decisionRunId/actionId/scenario/scope。
         */
        if (bo.getDecisionIdempotencyKey() != null || bo.getDecisionRunId() != null
            || bo.getDecisionActionId() != null || bo.getDecisionScenarioCode() != null
            || bo.getDecisionWorkOrderIdsJson() != null) {
            throw new ServiceException("手工排程不能携带 AI 决策来源，请使用受保护的决策入口");
        }
        Long userId = LoginHelper.getUserId();
        return scheduleAndPersist(bo, scheduleInputAssembler.assemble(bo), userId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrdSchedulePlanVo runSchedule(PrdSchedulePlanBo bo, Collection<Long> workOrderIds) {
        if (bo == null) {
            throw new ServiceException("排程参数不能为空");
        }
        List<Long> normalizedIds = normalizeWorkOrderIds(workOrderIds);
        String scopeJson = workOrderIdsJson(normalizedIds);
        if (StringUtils.isBlank(bo.getDecisionWorkOrderIdsJson())) {
            bo.setDecisionWorkOrderIdsJson(scopeJson);
        } else if (!scopeJson.equals(bo.getDecisionWorkOrderIdsJson())) {
            throw new ServiceException("排程工单范围与结构化来源不一致");
        }
        String decisionKey = normalizeDecisionKey(bo.getDecisionIdempotencyKey());
        if (decisionKey != null) {
            validateDecisionIdentity(bo);
            PrdSchedulePlan existing = findByDecisionKey(decisionKey);
            if (existing != null) {
                validateExistingDecisionPlan(bo, existing);
                return MapstructUtils.convert(existing, PrdSchedulePlanVo.class);
            }
        }
        return scheduleAndPersist(bo, scheduleInputAssembler.assemble(bo, normalizedIds),
            bo.getCreateBy(), bo.getUpdateBy());
    }

    private PrdSchedulePlanVo scheduleAndPersist(PrdSchedulePlanBo bo, ScheduleInput input,
                                                  Long createBy, Long updateBy) {
        ScheduleResult result;
        try {
            result = PrdScheduleEngine.schedule(input);
        } catch (IllegalArgumentException invalidInput) {
            /** 这些是可确定的业务输入/产能错误，而不是未知的服务端异常。 */
            throw new ServiceException(invalidInput.getMessage());
        }
        if (result.getTasks() == null || result.getTasks().isEmpty()) {
            throw new ServiceException("排程没有可排的工序任务，请核对工单剩余数量和工艺路线");
        }
        LocalDate startDate = input.getStartDate();
        int horizonDays = input.getHorizonDays();
        Strategy strategy = input.getStrategy();
        List<WoInfo> woInfos = input.getWorkOrders();

        PrdSchedulePlan plan = new PrdSchedulePlan();
        String tenantId = scheduleTenantId();
        plan.setTenantId(tenantId);
        plan.setPlanNo(numberAllocator.nextNumber(tenantId, startDate));
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
        plan.setRemark(bo.getRemark());
        plan.setDecisionRunId(bo.getDecisionRunId());
        plan.setDecisionActionId(bo.getDecisionActionId());
        plan.setDecisionIdempotencyKey(normalizeDecisionKey(bo.getDecisionIdempotencyKey()));
        plan.setDecisionScenarioCode(bo.getDecisionScenarioCode());
        plan.setDecisionWorkOrderIdsJson(bo.getDecisionWorkOrderIdsJson());
        plan.setCapacityCalcVersion(2);
        plan.setVersion(0);
        plan.setCreateBy(createBy);
        plan.setUpdateBy(updateBy);

        try {
            int rows = planMapper.insert(plan);
            if (rows != 1) {
                throw new ServiceException("排程方案主表写入失败");
            }
        } catch (DuplicateKeyException duplicate) {
            if (plan.getDecisionIdempotencyKey() == null) {
                throw duplicate;
            }
            PrdSchedulePlan existing = findByDecisionKeyForUpdate(plan.getDecisionIdempotencyKey());
            if (existing == null) {
                if (isCompensatedDecisionKey(plan.getDecisionIdempotencyKey())) {
                    throw new ServiceException("排程方案已补偿，幂等键永久占用，不可重试");
                }
                throw duplicate;
            }
            validateExistingDecisionPlan(bo, existing);
            return MapstructUtils.convert(existing, PrdSchedulePlanVo.class);
        }
        if (plan.getPlanId() == null || plan.getPlanId() <= 0 || StringUtils.isBlank(plan.getPlanNo())
            || plan.getCreateTime() == null || plan.getUpdateTime() == null) {
            throw new ServiceException("排程方案主表未生成完整标识或版本时间");
        }

        for (ScheduledTask task : result.getTasks()) {
            PrdScheduleTask entity = toTaskEntity(plan.getPlanId(), task);
            if (taskMapper.insert(entity) != 1) {
                throw new ServiceException("排程任务写入失败");
            }
            persistTaskDayLoads(plan.getPlanId(), entity, task);
        }
        log.info("APS排程完成: planNo={}, 工单数={}, 任务数={}, 拖期={}, 跳过={}",
            plan.getPlanNo(), plan.getWoCount(), plan.getTaskCount(), plan.getDelayedCount(), plan.getSkippedCount());
        return MapstructUtils.convert(plan, PrdSchedulePlanVo.class);
    }

    /** 仅保存引擎已扣减过的真实日负荷；绝不从任务跨度均匀推算或把假期补零。 */
    private void persistTaskDayLoads(Long planId, PrdScheduleTask entity, ScheduledTask source) {
        if ("OUTSOURCE".equals(entity.getResourceType())) {
            if (!source.getDayLoads().isEmpty()) throw new ServiceException("委外任务不能占用内部日历");
            return;
        }
        if (planId == null || entity.getTaskId() == null || entity.getTaskId() <= 0
            || entity.getResourceId() == null || entity.getResourceId() <= 0
            || !("WORKSTATION".equals(entity.getResourceType()) || "WORKSHOP".equals(entity.getResourceType()))
            || source.getDayLoads() == null || source.getDayLoads().isEmpty() || source.getDayLoads().size() > 90) {
            throw new ServiceException("内部排程缺少可信逐日负荷或资源");
        }
        String tenant = TenantHelper.isEnable() ? TenantHelper.getTenantId() : "000000";
        if (StringUtils.isBlank(tenant)) throw new ServiceException("排程缺少租户身份");
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, BigDecimal> day : source.getDayLoads().entrySet()) {
            if (day.getKey() == null || day.getValue() == null || day.getValue().signum() <= 0
                || day.getValue().scale() > 2 || day.getKey().isBefore(source.getStartDay())
                || day.getKey().isAfter(source.getEndDay())) {
                throw new ServiceException("排程逐日工时、精度或日期无效");
            }
            PrdScheduleTaskDayLoad load = new PrdScheduleTaskDayLoad();
            load.setLoadId(IdWorker.getId());
            load.setTenantId(tenant);
            load.setPlanId(planId);
            load.setTaskId(entity.getTaskId());
            load.setResourceType(entity.getResourceType());
            load.setResourceId(entity.getResourceId());
            load.setLoadDate(toDate(day.getKey()));
            load.setPlannedHours(day.getValue());
            load.setCreateTime(new Date());
            if (taskDayLoadMapper.insert(load) != 1) throw new ServiceException("排程逐日负荷写入失败");
            sum = sum.add(day.getValue());
        }
        BigDecimal setup = source.getSetupHours() == null ? BigDecimal.ZERO : source.getSetupHours();
        if (source.getProcessHours() == null || sum.compareTo(source.getProcessHours().add(setup)) != 0)
            throw new ServiceException("排程逐日工时与工序加工/准备工时不一致");
    }
    private List<Long> normalizeWorkOrderIds(Collection<Long> workOrderIds) {
        if (workOrderIds == null || workOrderIds.isEmpty()) {
            throw new ServiceException("排程工单ID不能为空");
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : workOrderIds) {
            if (id == null || id <= 0 || !unique.add(id)) {
                throw new ServiceException("排程工单ID必须为不重复的正数");
            }
        }
        return unique.stream().sorted().toList();
    }

    private String workOrderIdsJson(List<Long> workOrderIds) {
        return JsonUtils.toJsonString(workOrderIds.stream().map(String::valueOf).toList());
    }

    private String normalizeDecisionKey(String key) {
        return StringUtils.isBlank(key) ? null : key;
    }

    private void validateDecisionIdentity(PrdSchedulePlanBo bo) {
        if (bo.getDecisionRunId() == null || bo.getDecisionRunId() <= 0
            || bo.getDecisionActionId() == null || bo.getDecisionActionId() <= 0
            || StringUtils.isBlank(bo.getDecisionScenarioCode())) {
            throw new ServiceException("AI决策排程缺少有效runId/actionId/scenario");
        }
        if (bo.getDecisionIdempotencyKey().length() > 128) {
            throw new ServiceException("AI决策幂等键长度不能超过128");
        }
    }

    private PrdSchedulePlan findByDecisionKey(String key) {
        return planMapper.selectOne(Wrappers.lambdaQuery(PrdSchedulePlan.class)
            .eq(PrdSchedulePlan::getDecisionIdempotencyKey, key));
    }

    private PrdSchedulePlan findByDecisionKeyForUpdate(String key) {
        return planMapper.selectOne(Wrappers.lambdaQuery(PrdSchedulePlan.class)
            .eq(PrdSchedulePlan::getDecisionIdempotencyKey, key)
            .last("FOR UPDATE"));
    }

    private boolean isCompensatedDecisionKey(String key) {
        String tenantId = TenantHelper.getTenantId();
        if (StringUtils.isBlank(tenantId)) {
            throw new ServiceException("租户上下文缺失，无法确认排程方案幂等冲突");
        }
        return planMapper.findCompensatedDecisionId(tenantId, key) != null;
    }

    private void validateExistingDecisionPlan(PrdSchedulePlanBo requested, PrdSchedulePlan existing) {
        String requestedStrategy = requested.getStrategy() == null ? null : requested.getStrategy().trim();
        if ("CURRENT".equalsIgnoreCase(requestedStrategy)
            || "BASELINE_CURRENT".equalsIgnoreCase(requestedStrategy)) {
            throw new ServiceException("当前排程基线不能作为重排策略");
        }
        boolean matches = Objects.equals(requested.getDecisionRunId(), existing.getDecisionRunId())
            && Objects.equals(requested.getDecisionActionId(), existing.getDecisionActionId())
            && Objects.equals(requested.getDecisionIdempotencyKey(), existing.getDecisionIdempotencyKey())
            && Objects.equals(requested.getDecisionScenarioCode(), existing.getDecisionScenarioCode())
            && Objects.equals(requested.getDecisionWorkOrderIdsJson(), existing.getDecisionWorkOrderIdsJson())
            && Objects.equals(StringUtils.isNotBlank(requested.getPlanName()) ? requested.getPlanName()
                : "智能排产 " + existing.getPlanNo(), existing.getPlanName())
            && Objects.equals(requested.getWorkshopId(), existing.getWorkshopId())
            && Objects.equals(toLocalDate(requested.getStartDate()), toLocalDate(existing.getStartDate()))
            && Objects.equals(requested.getHorizonDays(), existing.getHorizonDays())
            && Objects.equals(Strategy.parse(requestedStrategy).name(), existing.getStrategy());
        if (!matches) {
            throw new ServiceException("幂等键对应的排程方案来源或载荷不一致");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean publish(Long planId) {
        PrdSchedulePlan plan = planMapper.selectById(planId);
        if (plan == null) throw new ServiceException("排程方案不存在");
        if (!PLAN_STATUS_COMPLETED.equals(plan.getStatus())) {
            throw new ServiceException(PLAN_STATUS_PUBLISHED.equals(plan.getStatus())
                ? "方案已发布, 请勿重复操作" : "当前方案状态不允许发布");
        }
        if (!Integer.valueOf(2).equals(plan.getCapacityCalcVersion())) {
            throw new ServiceException("排程方案产能计算版本无效，旧方案不能发布");
        }
        String tenant = scheduleTenantId();
        if (!tenant.equals(plan.getTenantId())) throw new ServiceException("排程方案租户与当前上下文不一致");
        List<PrdScheduleTask> tasks = taskMapper.selectList(Wrappers.lambdaQuery(PrdScheduleTask.class)
            .eq(PrdScheduleTask::getPlanId, planId));
        if (tasks == null || tasks.isEmpty()) throw new ServiceException("排程方案无任务，不能发布");
        /** 所有发布先锁工单（按ID固定顺序），再锁方案行；不能先锁各自方案再互相等对方方案。 */
        Map<Long, PrdWorkOrder> workOrders = lockUnpublishedWorkOrders(planId, tasks, tenant);
        int version = plan.getVersion() == null ? 0 : plan.getVersion();
        int claimed = planMapper.update(null, Wrappers.lambdaUpdate(PrdSchedulePlan.class)
            .eq(PrdSchedulePlan::getPlanId, planId)
            .eq(PrdSchedulePlan::getStatus, PLAN_STATUS_COMPLETED)
            .eq(PrdSchedulePlan::getCapacityCalcVersion, 2)
            .eq(PrdSchedulePlan::getVersion, version)
            .set(PrdSchedulePlan::getStatus, PLAN_STATUS_PUBLISHED)
            .set(PrdSchedulePlan::getVersion, version + 1)
            .set(PrdSchedulePlan::getUpdateTime, new Date()));
        if (claimed != 1) throw new ServiceException("排程方案状态或版本已变化，发布失败");

        List<PrdScheduleTaskDayLoad> loads = verifiedDayLoads(planId, tasks, tenant);
        occupyPublishedCapacity(planId, loads, tenant);
        Map<Long, List<PrdScheduleTask>> byWo = tasks.stream()
            .collect(Collectors.groupingBy(PrdScheduleTask::getWoId));
        for (Long woId : workOrders.keySet().stream().sorted().toList()) {
            List<PrdScheduleTask> woTasks = byWo.get(woId).stream()
                .sorted(Comparator.comparing(t -> t.getSeqNum() == null ? Integer.MAX_VALUE : t.getSeqNum()))
                .toList();
            PrdWorkOrder wo = workOrders.get(woId);
            wo.setPlanStartDate(woTasks.get(0).getStartTime());
            wo.setPlanEndDate(woTasks.get(woTasks.size() - 1).getEndTime());
            if (workOrderMapper.updateById(wo) != 1) throw new ServiceException("工单计划日期回写失败: " + woId);
        }
        return true;
    }

    /** 锁同一工单，防止两个不同的方案各自成功 CAS 后重复占用。 */
    private Map<Long, PrdWorkOrder> lockUnpublishedWorkOrders(Long planId, List<PrdScheduleTask> tasks, String tenant) {
        Set<Long> woIds = new HashSet<>();
        for (PrdScheduleTask task : tasks) {
            if (task == null || !Objects.equals(planId, task.getPlanId())
                || task.getTaskId() == null || task.getTaskId() <= 0
                || task.getWoId() == null || task.getWoId() <= 0 || !tenant.equals(task.getTenantId())) {
                throw new ServiceException("排程任务来源或租户无效");
            }
            woIds.add(task.getWoId());
        }
        Map<Long, PrdWorkOrder> workOrders = new HashMap<>();
        for (Long woId : woIds.stream().sorted().toList()) {
            PrdWorkOrder wo = workOrderMapper.selectOne(Wrappers.lambdaQuery(PrdWorkOrder.class)
                .eq(PrdWorkOrder::getWoId, woId).last("FOR UPDATE"));
            if (wo == null || !woId.equals(wo.getWoId()) || !tenant.equals(wo.getTenantId()))
                throw new ServiceException("排程工单来源或租户无效");
            if (isTerminalStatus(wo.getWoStatus()))
                throw new ServiceException("工单已完工或关闭，不能发布排程方案: " + woId);
            workOrders.put(woId, wo);
        }
        /**
         * 工单锁拿到后再作锁定当前读：RR 下普通读可能沿用发布前的旧快照。
         * idx_pst_wo(wo_id) 的范围按工单 ID 顺序扫描；新方案发布也先按同序锁工单。
         */
        List<PrdScheduleTask> related = taskMapper.selectList(Wrappers.lambdaQuery(PrdScheduleTask.class)
            .in(PrdScheduleTask::getWoId, woIds).ne(PrdScheduleTask::getPlanId, planId)
            .orderByAsc(PrdScheduleTask::getWoId).orderByAsc(PrdScheduleTask::getTaskId).last("FOR UPDATE"));
        Set<Long> otherPlanIds = new HashSet<>();
        if (related != null) {
            for (PrdScheduleTask other : related) {
                if (other == null || other.getPlanId() == null || other.getWoId() == null
                    || !woIds.contains(other.getWoId()) || !tenant.equals(other.getTenantId()))
                    throw new ServiceException("排程工单存在异常的其他方案来源");
                if (!Objects.equals(planId, other.getPlanId())) otherPlanIds.add(other.getPlanId());
            }
        }
        /** 普通 selectBatchIds 在 MySQL RR 下会重用旧快照；逐 ID 当前读才能看见刚提交的发布。 */
        for (Long otherId : otherPlanIds.stream().sorted().toList()) {
            PrdSchedulePlan other = planMapper.selectOne(Wrappers.lambdaQuery(PrdSchedulePlan.class)
                .eq(PrdSchedulePlan::getPlanId, otherId).last("FOR UPDATE"));
            if (other == null || !otherId.equals(other.getPlanId())
                || !tenant.equals(other.getTenantId()) || PLAN_STATUS_PUBLISHED.equals(other.getStatus()))
                throw new ServiceException("工单已有发布方案或方案来源不可信，不得重复发布");
        }
        return workOrders;
    }

    /** 逐任务复核引擎真实按日结果，不按任务起止时间重新猜测负荷。 */
    private List<PrdScheduleTaskDayLoad> verifiedDayLoads(Long planId, List<PrdScheduleTask> tasks, String tenant) {
        List<PrdScheduleTaskDayLoad> rows = taskDayLoadMapper.selectList(Wrappers.lambdaQuery(PrdScheduleTaskDayLoad.class)
            .eq(PrdScheduleTaskDayLoad::getTenantId, tenant)
            .eq(PrdScheduleTaskDayLoad::getPlanId, planId).last("FOR UPDATE"));
        Map<Long, PrdScheduleTask> byId = new HashMap<>();
        for (PrdScheduleTask task : tasks) {
            if (byId.putIfAbsent(task.getTaskId(), task) != null) throw new ServiceException("排程任务ID重复");
        }
        Map<Long, BigDecimal> sums = new HashMap<>();
        Set<String> days = new HashSet<>();
        List<PrdScheduleTaskDayLoad> safe = rows == null ? List.of() : rows;
        for (PrdScheduleTaskDayLoad row : safe) {
            PrdScheduleTask task = row == null ? null : byId.get(row.getTaskId());
            LocalDate day = row == null ? null : toLocalDate(row.getLoadDate());
            if (task == null || row.getLoadId() == null || row.getLoadId() <= 0
                || !tenant.equals(row.getTenantId()) || !Objects.equals(planId, row.getPlanId())
                || !Objects.equals(task.getResourceType(), row.getResourceType())
                || !Objects.equals(task.getResourceId(), row.getResourceId())
                || "OUTSOURCE".equals(task.getResourceType()) || day == null
                || toLocalDate(task.getStartTime()) == null || toLocalDate(task.getEndTime()) == null
                || day.isBefore(toLocalDate(task.getStartTime())) || day.isAfter(toLocalDate(task.getEndTime()))
                || row.getPlannedHours() == null || row.getPlannedHours().signum() <= 0
                || row.getPlannedHours().scale() > 2 || !days.add(row.getTaskId() + ":" + day))
                throw new ServiceException("排程逐日负荷或其来源无效");
            sums.merge(task.getTaskId(), row.getPlannedHours(), BigDecimal::add);
        }
        for (PrdScheduleTask task : tasks) {
            if ("OUTSOURCE".equals(task.getResourceType())) continue;
            if (!("WORKSTATION".equals(task.getResourceType()) || "WORKSHOP".equals(task.getResourceType()))
                || task.getResourceId() == null || task.getResourceId() <= 0
                || task.getProcessHours() == null || task.getProcessHours().signum() < 0
                || task.getSetupHours() == null || task.getSetupHours().signum() < 0
                || sums.get(task.getTaskId()) == null
                || sums.get(task.getTaskId()).compareTo(task.getProcessHours().add(task.getSetupHours())) != 0)
                throw new ServiceException("排程任务与逐日工时或加工/准备负荷不一致");
        }
        return safe.stream().sorted(Comparator
            .comparing(PrdScheduleTaskDayLoad::getResourceType)
            .thenComparing(PrdScheduleTaskDayLoad::getResourceId)
            .thenComparing(PrdScheduleTaskDayLoad::getLoadDate)
            .thenComparing(PrdScheduleTaskDayLoad::getTaskId)).toList();
    }

    /** 仅将有正负荷的日期按当时可用班次分摊；不声称这些班次是生成方案时已选定的班次。 */
    private void occupyPublishedCapacity(Long planId, List<PrdScheduleTaskDayLoad> loads, String tenant) {
        if (loads.isEmpty()) return; /** 纯委外方案无内部日历占用 */
        Set<LocalDate> loadedDays = new HashSet<>();
        Map<ResourceDay, List<PrdCapacityCalendar>> calendarsByDay = new HashMap<>();
        Map<Long, CapacityBefore> before = new HashMap<>();
        for (PrdScheduleTaskDayLoad load : loads) loadedDays.add(toLocalDate(load.getLoadDate()));
        /**
         * uk_tenant_holiday_date(tenant_id, holiday_date) 上的当前范围读同时覆盖缺行的日期间隙。
         * 节假日写入口也先按日期锁该范围，再锁日历；不可改成普通 RR 快照读。
         */
        List<SchHoliday> holidays = holidayMapper.selectList(Wrappers.lambdaQuery(SchHoliday.class)
            .eq(SchHoliday::getTenantId, tenant)
            .ge(SchHoliday::getHolidayDate, toDate(loadedDays.stream().min(LocalDate::compareTo).orElseThrow()))
            .lt(SchHoliday::getHolidayDate, toDate(loadedDays.stream().max(LocalDate::compareTo).orElseThrow().plusDays(1)))
            .orderByAsc(SchHoliday::getHolidayDate).last("FOR UPDATE"));
        if (holidays != null) {
            for (SchHoliday holiday : holidays) {
                if (holiday != null && holiday.getHolidayDate() != null
                    && loadedDays.contains(toLocalDate(holiday.getHolidayDate()))
                    && (holiday.getIsWorkingDay() == null || holiday.getIsWorkingDay() == 0))
                    throw new ServiceException("排程逐日负荷落在非工作假日，不允许发布");
            }
        }
        List<ResourceDay> resourceDays = loads.stream()
            .map(l -> new ResourceDay(l.getResourceType(), l.getResourceId(), toLocalDate(l.getLoadDate())))
            .distinct().sorted(Comparator.comparing(ResourceDay::type)
                .thenComparing(ResourceDay::id).thenComparing(ResourceDay::date)).toList();
        /**
         * 先发现候选 ID（此普通读不能用于余额决策），随后全局按 PK 升序逐行锁定并复核现值。
         * 不能按资源日各自 SELECT ... FOR UPDATE：两个方案资源顺序可能与 calendarId 相反。
         */
        Map<Long, ResourceDay> candidateIds = new HashMap<>();
        for (ResourceDay key : resourceDays) {
            List<PrdCapacityCalendar> candidates = capacityCalendarMapper.selectList(Wrappers.lambdaQuery(PrdCapacityCalendar.class)
                .eq(PrdCapacityCalendar::getTenantId, tenant)
                .eq(PrdCapacityCalendar::getObjectType, key.type())
                .eq(PrdCapacityCalendar::getObjectId, key.id())
                .eq(PrdCapacityCalendar::getCalendarDate, toDate(key.date()))
                .eq(PrdCapacityCalendar::getStatus, "ACTIVE"));
            if (candidates != null) {
                for (PrdCapacityCalendar row : candidates) {
                    if (row == null || !key.type().equals(row.getObjectType())
                        || !key.id().equals(row.getObjectId()) || !key.date().equals(toLocalDate(row.getCalendarDate())))
                        continue;
                    if (row.getCalendarId() == null || row.getCalendarId() <= 0
                        || candidateIds.putIfAbsent(row.getCalendarId(), key) != null)
                        throw new ServiceException("产能日历候选ID无效或重复");
                }
            }
            calendarsByDay.put(key, new ArrayList<>());
        }
        Set<Long> shiftIds = new HashSet<>();
        for (Long id : candidateIds.keySet().stream().sorted().toList()) {
            PrdCapacityCalendar row = capacityCalendarMapper.selectOne(Wrappers.lambdaQuery(PrdCapacityCalendar.class)
                .eq(PrdCapacityCalendar::getCalendarId, id).last("FOR UPDATE"));
            ResourceDay key = candidateIds.get(id);
            if (row == null || !id.equals(row.getCalendarId())
                || !key.type().equals(row.getObjectType()) || !key.id().equals(row.getObjectId())
                || !key.date().equals(toLocalDate(row.getCalendarDate()))
                || !"ACTIVE".equals(row.getStatus()) || !tenant.equals(row.getTenantId())
                || row.getShiftId() == null || row.getShiftId() <= 0
                || row.getEffectiveHours() == null || row.getEffectiveHours().signum() <= 0
                || row.getRemainingHours() == null || row.getRemainingHours().signum() < 0
                || row.getPlannedHours() == null || row.getPlannedHours().signum() < 0
                || before.putIfAbsent(id, new CapacityBefore(row.getPlannedHours(), row.getRemainingHours())) != null)
                throw new ServiceException("产能日历租户、班次或产能无效");
            shiftIds.add(row.getShiftId());
            calendarsByDay.get(key).add(row);
        }
        for (ResourceDay key : resourceDays) {
            /**
             * 候选 ID 来自可能已建立的 RR 旧快照，逐 PK 当前读只能复核已知 ID。
             * 锁定 idx_date / uk_date_object_shift 的日期资源范围（包括缺行间隙），复核新增班次。
             * 若发现幻行或候选变化则整体回滚，不基于不完整班次集合继续发布。
             */
            List<PrdCapacityCalendar> current = capacityCalendarMapper.selectList(Wrappers.lambdaQuery(PrdCapacityCalendar.class)
                .eq(PrdCapacityCalendar::getTenantId, tenant)
                .eq(PrdCapacityCalendar::getObjectType, key.type())
                .eq(PrdCapacityCalendar::getObjectId, key.id())
                .eq(PrdCapacityCalendar::getCalendarDate, toDate(key.date()))
                .eq(PrdCapacityCalendar::getStatus, "ACTIVE")
                .orderByAsc(PrdCapacityCalendar::getCalendarId).last("FOR UPDATE"));
            if (current == null) throw new ServiceException("排程产能日历范围复核失败");
            Set<Long> currentIds = new HashSet<>();
            for (PrdCapacityCalendar row : current) {
                if (row == null || row.getCalendarId() == null || !tenant.equals(row.getTenantId())
                    || !key.type().equals(row.getObjectType()) || !key.id().equals(row.getObjectId())
                    || !key.date().equals(toLocalDate(row.getCalendarDate())) || !"ACTIVE".equals(row.getStatus())
                    || !currentIds.add(row.getCalendarId()))
                    throw new ServiceException("排程产能日历范围来源不可信");
            }
            Set<Long> lockedIds = calendarsByDay.get(key).stream()
                .map(PrdCapacityCalendar::getCalendarId).collect(Collectors.toSet());
            if (!currentIds.equals(lockedIds)) throw new ServiceException("排程产能日历候选已变化，请重新生成方案");
            if (lockedIds.isEmpty()) throw new ServiceException("排程逐日负荷缺少对应产能日历");
        }
        List<SchShift> shifts = shiftMapper.selectList(Wrappers.lambdaQuery(SchShift.class)
            .in(SchShift::getShiftId, shiftIds));
        Map<Long, LocalTime> startTimes = new HashMap<>();
        if (shifts != null) {
            for (SchShift shift : shifts) {
                if (shift == null || shift.getShiftId() == null || !shiftIds.contains(shift.getShiftId())
                    || !tenant.equals(shift.getTenantId()) || shift.getStartTime() == null
                    || shift.getEndTime() == null || shift.getIsCrossDay() == null
                    || (shift.getIsCrossDay() != 0 && shift.getIsCrossDay() != 1))
                    throw new ServiceException("产能日历班次起止时间或跨天标记不可信");
                LocalTime startTime = shiftTime(shift.getStartTime());
                LocalTime endTime = shiftTime(shift.getEndTime());
                if ((shift.getIsCrossDay() == 0 && !endTime.isAfter(startTime))
                    || (shift.getIsCrossDay() == 1 && !endTime.isBefore(startTime))
                    || startTimes.putIfAbsent(shift.getShiftId(), startTime) != null)
                    throw new ServiceException("产能日历班次起止时间或跨天标记不可信");
            }
        }
        if (startTimes.size() != shiftIds.size()) throw new ServiceException("产能日历关联班次不存在");
        for (List<PrdCapacityCalendar> rows : calendarsByDay.values()) {
            Set<LocalTime> unique = new HashSet<>();
            rows.sort(Comparator.comparing((PrdCapacityCalendar row) -> startTimes.get(row.getShiftId())));
            for (PrdCapacityCalendar row : rows) {
                if (!unique.add(startTimes.get(row.getShiftId())))
                    throw new ServiceException("同一资源日历的班次起始时间重复");
            }
        }
        Map<Long, BigDecimal> remaining = new HashMap<>();
        before.forEach((id, state) -> remaining.put(id, state.remaining()));
        Map<Long, BigDecimal> bookedByCalendar = new TreeMap<>();
        List<PrdScheduleCapacityBooking> bookings = new ArrayList<>();
        for (PrdScheduleTaskDayLoad load : loads) {
            LocalDate date = toLocalDate(load.getLoadDate());
            ResourceDay key = new ResourceDay(load.getResourceType(), load.getResourceId(), date);
            BigDecimal need = load.getPlannedHours();
            for (PrdCapacityCalendar row : calendarsByDay.get(key)) {
                if (need.signum() <= 0) break;
                Long id = row.getCalendarId();
                BigDecimal take = need.min(remaining.get(id));
                if (take.signum() <= 0) continue;
                PrdScheduleCapacityBooking booking = new PrdScheduleCapacityBooking();
                booking.setBookingId(IdWorker.getId());
                booking.setTenantId(tenant);
                booking.setPlanId(planId);
                booking.setTaskId(load.getTaskId());
                booking.setCalendarId(id);
                booking.setLoadDate(toDate(date));
                booking.setBookedHours(take);
                booking.setCreateTime(new Date());
                bookings.add(booking);
                remaining.put(id, remaining.get(id).subtract(take));
                bookedByCalendar.merge(id, take, BigDecimal::add);
                need = need.subtract(take);
            }
            if (need.signum() > 0) throw new ServiceException("排程逐日负荷剩余产能不足");
        }
        for (Map.Entry<Long, BigDecimal> booking : bookedByCalendar.entrySet()) {
            capacityCalendarService.occupyCapacity(booking.getKey(), booking.getValue());
        }
        for (PrdScheduleCapacityBooking booking : bookings) {
            if (bookingMapper.insert(booking) != 1) throw new ServiceException("产能占用凭据写入失败");
        }
        for (Map.Entry<Long, BigDecimal> booked : bookedByCalendar.entrySet()) {
            CapacityBefore initial = before.get(booked.getKey());
            PrdCapacityCalendar stored = capacityCalendarMapper.selectById(booked.getKey());
            if (stored == null || !tenant.equals(stored.getTenantId())
                || stored.getPlannedHours() == null || stored.getRemainingHours() == null
                || stored.getPlannedHours().compareTo(initial.planned().add(booked.getValue())) != 0
                || stored.getRemainingHours().compareTo(initial.remaining().subtract(booked.getValue())) != 0)
                throw new ServiceException("排程产能日历占用未成功持久化");
        }
    }

    private LocalTime shiftTime(Date value) {
        return value instanceof java.sql.Time time ? time.toLocalTime()
            : Instant.ofEpochMilli(value.getTime()).atZone(BUSINESS_ZONE).toLocalTime();
    }

    private record ResourceDay(String type, Long id, LocalDate date) { }

    private record CapacityBefore(BigDecimal planned, BigDecimal remaining) { }

    private String scheduleTenantId() {
        String tenantId = TenantHelper.isEnable() ? TenantHelper.getTenantId() : "000000";
        if (StringUtils.isBlank(tenantId)) {
            throw new ServiceException("租户上下文缺失，不能操作排程产能");
        }
        return tenantId;
    }

    private boolean hasBookings(Long planId, String tenantId) {
        return bookingMapper.selectCount(Wrappers.lambdaQuery(PrdScheduleCapacityBooking.class)
            .eq(PrdScheduleCapacityBooking::getTenantId, tenantId)
            .eq(PrdScheduleCapacityBooking::getPlanId, planId)) > 0;
    }

    private void deleteDayLoads(Long planId, String tenantId) {
        taskDayLoadMapper.delete(Wrappers.lambdaQuery(PrdScheduleTaskDayLoad.class)
            .eq(PrdScheduleTaskDayLoad::getTenantId, tenantId)
            .eq(PrdScheduleTaskDayLoad::getPlanId, planId));
    }

    /** 删除同样按工单 -> 任务 -> 方案顺序；先发现工单，锁后通过当前读防止旧快照漏任务。 */
    private void lockDeletionWorkOrdersAndTasks(List<Long> planIds, String tenantId) {
        List<PrdScheduleTask> discovered = taskMapper.selectList(Wrappers.lambdaQuery(PrdScheduleTask.class)
            .eq(PrdScheduleTask::getTenantId, tenantId).in(PrdScheduleTask::getPlanId, planIds));
        Map<Long, ResourceTaskOwner> expected = deletionTaskOwners(discovered, planIds, tenantId);
        for (Long woId : expected.values().stream().map(ResourceTaskOwner::woId).distinct().sorted().toList()) {
            PrdWorkOrder wo = workOrderMapper.selectOne(Wrappers.lambdaQuery(PrdWorkOrder.class)
                .eq(PrdWorkOrder::getWoId, woId).last("FOR UPDATE"));
            if (wo == null || !woId.equals(wo.getWoId()) || !tenantId.equals(wo.getTenantId()))
                throw new ServiceException("待删除排程工单来源或租户已变化");
        }
        List<PrdScheduleTask> current = taskMapper.selectList(Wrappers.lambdaQuery(PrdScheduleTask.class)
            .eq(PrdScheduleTask::getTenantId, tenantId).in(PrdScheduleTask::getPlanId, planIds)
            .orderByAsc(PrdScheduleTask::getTaskId).last("FOR UPDATE"));
        if (!expected.equals(deletionTaskOwners(current, planIds, tenantId)))
            throw new ServiceException("排程任务或工单关联已变化，请重试删除");
    }

    private Map<Long, ResourceTaskOwner> deletionTaskOwners(List<PrdScheduleTask> tasks,
                                                               List<Long> planIds, String tenantId) {
        Map<Long, ResourceTaskOwner> owners = new HashMap<>();
        for (PrdScheduleTask task : tasks == null ? List.<PrdScheduleTask>of() : tasks) {
            if (task == null || task.getTaskId() == null || task.getTaskId() <= 0
                || task.getWoId() == null || task.getWoId() <= 0 || !planIds.contains(task.getPlanId())
                || !tenantId.equals(task.getTenantId())
                || owners.putIfAbsent(task.getTaskId(), new ResourceTaskOwner(task.getPlanId(), task.getWoId())) != null)
                throw new ServiceException("待删除排程任务来源或租户无效");
        }
        return owners;
    }

    private record ResourceTaskOwner(Long planId, Long woId) { }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteByIds(Long[] planIds) {
        if (planIds == null || planIds.length == 0 || java.util.Arrays.stream(planIds).anyMatch(id -> id == null || id <= 0))
            throw new ServiceException("排程方案ID无效");
        List<Long> orderedIds = java.util.Arrays.stream(planIds).distinct().sorted().toList();
        String tenantId = scheduleTenantId();
        lockDeletionWorkOrdersAndTasks(orderedIds, tenantId);
        /** 已发布方案、或任何仍有占用凭据的候选方案都不能直接删除。 */
        for (Long planId : orderedIds) {
            PrdSchedulePlan plan = planMapper.selectOne(Wrappers.lambdaQuery(PrdSchedulePlan.class)
                .eq(PrdSchedulePlan::getPlanId, planId).last("FOR UPDATE"));
            if (plan == null || !tenantId.equals(plan.getTenantId()))
                throw new ServiceException("排程方案不存在或租户不一致，不能删除");
            if (PLAN_STATUS_PUBLISHED.equals(plan.getStatus()) || hasBookings(planId, tenantId))
                throw new ServiceException("排程方案仍有产能预约，不能直接删除");
        }
        for (Long planId : orderedIds) {
            deleteDayLoads(planId, tenantId);
            taskMapper.delete(Wrappers.lambdaQuery(PrdScheduleTask.class).eq(PrdScheduleTask::getPlanId, planId));
        }
        if (planMapper.deleteByIds(orderedIds) != orderedIds.size())
            throw new ServiceException("排程方案批量删除未全部成功，已回滚任务和逐日行");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteCompletedIfUnchanged(Long planId, String versionMarker,
                                              Long decisionRunId, Long decisionActionId,
                                              String decisionIdempotencyKey, String scenarioCode) {
        Integer version = parseVersionMarker(planId, versionMarker);
        if (version == null || decisionRunId == null || decisionRunId <= 0
            || decisionActionId == null || decisionActionId <= 0
            || StringUtils.isBlank(decisionIdempotencyKey) || StringUtils.isBlank(scenarioCode)) {
            return false;
        }
        LambdaQueryWrapper<PrdSchedulePlan> wrapper = Wrappers.lambdaQuery(PrdSchedulePlan.class)
            .eq(PrdSchedulePlan::getPlanId, planId)
            .eq(PrdSchedulePlan::getStatus, PLAN_STATUS_COMPLETED)
            .eq(PrdSchedulePlan::getVersion, version)
            .eq(PrdSchedulePlan::getDecisionRunId, decisionRunId)
            .eq(PrdSchedulePlan::getDecisionActionId, decisionActionId)
            .eq(PrdSchedulePlan::getDecisionIdempotencyKey, decisionIdempotencyKey)
            .eq(PrdSchedulePlan::getDecisionScenarioCode, scenarioCode);
        String tenantId = scheduleTenantId();
        lockDeletionWorkOrdersAndTasks(List.of(planId), tenantId);
        if (hasBookings(planId, tenantId)) {
            throw new ServiceException("排程方案存在产能预约，不允许补偿删除");
        }
        if (planMapper.delete(wrapper) != 1) {
            return false;
        }
        deleteDayLoads(planId, tenantId);
        taskMapper.delete(Wrappers.lambdaQuery(PrdScheduleTask.class)
            .eq(PrdScheduleTask::getPlanId, planId));
        return true;
    }

    private Integer parseVersionMarker(Long id, String marker) {
        if (id == null || id <= 0 || marker == null || !marker.startsWith("V:")) {
            return null;
        }
        String versionText = marker.substring(2);
        if (versionText.isEmpty() || !versionText.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            int version = Integer.parseInt(versionText);
            return version < 0 ? null : version;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private PrdScheduleTask toTaskEntity(Long planId, ScheduledTask task) {
        PrdScheduleTask entity = new PrdScheduleTask();
        entity.setTaskId(IdWorker.getId());
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

    private LocalDate toLocalDate(Date value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return Instant.ofEpochMilli(value.getTime()).atZone(BUSINESS_ZONE).toLocalDate();
    }

    private Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(BUSINESS_ZONE).toInstant());
    }

    private Date endOfDay(LocalDate date) {
        return Date.from(date.atTime(23, 59, 59).atZone(BUSINESS_ZONE).toInstant());
    }
}
