package com.nexus.erp.prd.schedule;

import com.nexus.erp.prd.schedule.ScheduleModel.ResourceLoad;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduleInput;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduleResult;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduledTask;
import com.nexus.erp.prd.schedule.ScheduleModel.StepInfo;
import com.nexus.erp.prd.schedule.ScheduleModel.Strategy;
import com.nexus.erp.prd.schedule.ScheduleModel.WoInfo;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * APS 排程引擎（纯算法，无 Spring/DB 依赖）
 * <p>
 * 天粒度前向有限产能排程：
 * <ol>
 *   <li>工单按策略排序(EDD/CR/PRIORITY)；</li>
 *   <li>逐工单按工艺路线步骤序号展开工序，前序完成日后一序才能开始(同日可衔接)；</li>
 *   <li>内部工序占用资源(工作站优先，退化为车间)的每日产能，不足时跨日拆分；
 *       委外工序不占产能，按等待+转移时间折算日历天数流逝；</li>
 *   <li>无产能日历的日期回退默认 8h/日，假期产能为 0；</li>
 *   <li>末序完工日晚于交期即拖期，拖期工单的全部任务打拖期标记。</li>
 * </ol>
 * 瓶颈视角：结果附资源负荷统计( booked/available )，瓶颈即利用率最高资源。
 *
 * @author nexus
 */
public class PrdScheduleEngine {

    /**
     * 无产能日历时的默认日产能(小时)
     */
    private static final BigDecimal DEFAULT_DAILY_HOURS = BigDecimal.valueOf(8);

    /**
     * 委外工序日历耗时折算：每日流逝小时数(自然日 24h)
     */
    private static final BigDecimal OUTSOURCE_HOURS_PER_DAY = BigDecimal.valueOf(24);

    private PrdScheduleEngine() {
    }

    /**
     * 执行排程
     *
     * @param input 排程输入(工单/工艺路线提供者/产能视图/假期/起始日/跨度/策略)
     * @return 排程结果(任务/跳过工单/资源负荷/汇总)
     */
    public static ScheduleResult schedule(ScheduleInput input) {
        LocalDate startDate = input.getStartDate();
        int horizonDays = Math.max(1, input.getHorizonDays());
        LocalDate horizonEnd = startDate.plusDays(horizonDays - 1L);

        // 产能记账: 剩余产能(可变副本) 与 可用产能(利用率分母, 固定快照)
        Map<LocalDate, Map<String, BigDecimal>> remaining = copyCapacity(input.getCapacityByDate());
        Map<LocalDate, Map<String, BigDecimal>> availableSnap = copyCapacity(input.getCapacityByDate());
        Set<LocalDate> holidays = input.getHolidays() == null ? Set.of() : input.getHolidays();

        List<WoInfo> ordered = sortWorkOrders(input.getWorkOrders(), input.getStrategy(), startDate);

        List<ScheduledTask> tasks = new ArrayList<>();
        List<WoInfo> skipped = new ArrayList<>();
        BigDecimal totalHours = BigDecimal.ZERO;
        int delayedCount = 0;
        LocalDate makespan = null;
        Set<String> touchedResources = new HashSet<>();
        Map<String, String> resourceNames = new HashMap<>();

        for (WoInfo wo : ordered) {
            if (wo.getRemainingQty() == null || wo.getRemainingQty().compareTo(BigDecimal.ZERO) <= 0) {
                skipped.add(wo);
                continue;
            }
            List<StepInfo> steps = input.getRoutingProvider() == null ? null : input.getRoutingProvider().apply(wo.getMaterialId());
            if (steps == null || steps.isEmpty()) {
                skipped.add(wo);
                continue;
            }
            List<StepInfo> sortedSteps = new ArrayList<>(steps);
            sortedSteps.sort(Comparator.comparing(s -> s.getSeqNum() == null ? Integer.MAX_VALUE : s.getSeqNum()));

            LocalDate cursor = startDate;
            List<ScheduledTask> woTasks = new ArrayList<>();
            for (StepInfo step : sortedSteps) {
                ScheduledTask task = scheduleStep(wo, step, cursor, remaining, availableSnap, holidays, horizonEnd, resourceNames);
                woTasks.add(task);
                if (!"OUTSOURCE".equals(task.getResourceType())) {
                    touchedResources.add(ScheduleModel.resourceKey(task.getResourceType(), task.getResourceId()));
                }
                cursor = task.getEndDay();
            }

            ScheduledTask last = woTasks.get(woTasks.size() - 1);
            boolean delay = wo.getDueDate() != null && last.getEndDay().isAfter(wo.getDueDate());
            if (delay) {
                delayedCount++;
            }
            for (ScheduledTask task : woTasks) {
                task.setDelay(delay);
                totalHours = totalHours.add(nvl(task.getProcessHours()));
                if (makespan == null || task.getEndDay().isAfter(makespan)) {
                    makespan = task.getEndDay();
                }
            }
            tasks.addAll(woTasks);
        }

        ScheduleResult result = new ScheduleResult();
        result.setTasks(tasks);
        result.setSkipped(skipped);
        result.setLoads(buildLoads(touchedResources, resourceNames, remaining, availableSnap, startDate, horizonEnd, holidays));
        result.setMakespan(makespan);
        result.setDelayedWoCount(delayedCount);
        result.setTotalHours(totalHours);
        return result;
    }

    /**
     * 工单排序
     * EDD: 交期升序(交期空排最后), 次序优先级升序;
     * CR: 临界比升序(临界比 = 剩余工期天数/剩余工时折算天数, 负值即已临期, 优先);
     * PRIORITY: 优先级升序, 次序交期升序。
     */
    private static List<WoInfo> sortWorkOrders(List<WoInfo> workOrders, Strategy strategy, LocalDate startDate) {
        List<WoInfo> list = new ArrayList<>(workOrders == null ? List.of() : workOrders);
        list.sort(switch (strategy) {
            case CR -> Comparator.comparing((WoInfo w) -> criticalRatio(w, startDate));
            case PRIORITY -> Comparator.comparing((WoInfo w) -> nvl(w.getPriority()))
                .thenComparing(w -> w.getDueDate() == null ? LocalDate.MAX : w.getDueDate());
            default -> Comparator.comparing((WoInfo w) -> w.getDueDate() == null ? LocalDate.MAX : w.getDueDate())
                .thenComparing(w -> nvl(w.getPriority()));
        });
        return list;
    }

    /**
     * 临界比 = 剩余工期(天) / 剩余工时折算天数(8h/天)。工时为 0 时返回极大值(排最后)。
     */
    private static BigDecimal criticalRatio(WoInfo wo, LocalDate startDate) {
        if (wo.getDueDate() == null) {
            return BigDecimal.valueOf(Long.MAX_VALUE);
        }
        long daysToDue = ChronoUnit.DAYS.between(startDate, wo.getDueDate());
        // 工时在排序阶段未逐单展开工艺路线, 用剩余数量近似工时量级(同一物料间可比; 跨物料 EDD 为默认策略)
        BigDecimal qty = wo.getRemainingQty() == null ? BigDecimal.ONE : wo.getRemainingQty();
        BigDecimal workDays = qty.divide(DEFAULT_DAILY_HOURS, 2, RoundingMode.HALF_UP);
        if (workDays.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.valueOf(Long.MAX_VALUE);
        }
        return BigDecimal.valueOf(daysToDue).divide(workDays, 4, RoundingMode.HALF_UP);
    }

    /**
     * 排单个工序: 计算工时 → 内部工序按日产能分配(可跨日), 委外按日历天数流逝
     */
    private static ScheduledTask scheduleStep(WoInfo wo, StepInfo step, LocalDate earliest,
                                              Map<LocalDate, Map<String, BigDecimal>> remaining,
                                              Map<LocalDate, Map<String, BigDecimal>> availableSnap,
                                              Set<LocalDate> holidays,
                                              LocalDate horizonEnd,
                                              Map<String, String> resourceNames) {
        BigDecimal qty = wo.getRemainingQty();
        BigDecimal standard = step.getStandardHours() == null ? BigDecimal.ZERO : step.getStandardHours();
        BigDecimal setup = step.getSetupHours() == null ? BigDecimal.ZERO : step.getSetupHours();
        BigDecimal processHours = standard.multiply(qty, MathContext.DECIMAL64).setScale(2, RoundingMode.HALF_UP);

        ScheduledTask task = new ScheduledTask();
        task.setWo(wo);
        task.setStep(step);
        task.setSetupHours(setup);
        task.setProcessHours(processHours.add(setup).setScale(2, RoundingMode.HALF_UP));

        boolean outsource = step.getIsOutsource() != null && step.getIsOutsource() == 1;
        if (outsource) {
            BigDecimal elapsed = nvl(step.getWaitTime()).add(nvl(step.getTransferTime()));
            int days = Math.max(1, elapsed.divide(OUTSOURCE_HOURS_PER_DAY, 0, RoundingMode.CEILING).intValue());
            task.setResourceType("OUTSOURCE");
            task.setResourceId(null);
            task.setResourceName("委外加工");
            task.setStartDay(earliest);
            task.setEndDay(earliest.plusDays(days - 1L));
            task.setDurationDays(days);
            return task;
        }

        String resourceType;
        Long resourceId;
        String resourceName;
        if (step.getWorkstationId() != null) {
            resourceType = "WORKSTATION";
            resourceId = step.getWorkstationId();
            // 名称空串视为缺失(基础数据存在空串), 回退: 名称 → 编码 → ID
            resourceName = !isBlank(step.getWorkstationName()) ? step.getWorkstationName()
                : (!isBlank(step.getWorkstationCode()) ? step.getWorkstationCode() : "工作站" + step.getWorkstationId());
        } else if (wo.getWorkshopId() != null) {
            resourceType = "WORKSHOP";
            resourceId = wo.getWorkshopId();
            resourceName = !isBlank(wo.getWorkshopName()) ? wo.getWorkshopName() : "车间" + wo.getWorkshopId();
        } else {
            // 无任何资源归属: 仅按默认产能挂空资源, 保证任务仍有时序
            resourceType = "WORKSHOP";
            resourceId = null;
            resourceName = "未指定车间";
        }
        task.setResourceType(resourceType);
        task.setResourceId(resourceId);
        task.setResourceName(resourceName);

        String key = ScheduleModel.resourceKey(resourceType, resourceId);
        resourceNames.putIfAbsent(key, resourceName);

        LocalDate day = earliest;
        LocalDate startDay = null;
        LocalDate endDay = earliest;
        BigDecimal need = task.getProcessHours();
        while (need.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal avail = dailyCapacity(availableSnap, remaining, holidays, day, key);
            if (avail.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal take = avail.min(need);
                Map<String, BigDecimal> dayMap = remaining.computeIfAbsent(day, k -> new HashMap<>());
                dayMap.put(key, avail.subtract(take));
                if (startDay == null) {
                    startDay = day;
                }
                endDay = day;
                need = need.subtract(take);
            }
            if (need.compareTo(BigDecimal.ZERO) > 0) {
                day = day.plusDays(1);
            }
        }
        // 工时为 0 的工序(纯准备或未维护标准工时)至少占 1 天
        if (startDay == null) {
            startDay = earliest;
            endDay = earliest;
        }
        task.setStartDay(startDay);
        task.setEndDay(endDay);
        task.setDurationDays((int) ChronoUnit.DAYS.between(startDay, endDay) + 1);
        return task;
    }

    /**
     * 某资源某日可用产能: 假期 0; 有快照记录用剩余值; 无记录回退默认 8h。
     */
    private static BigDecimal dailyCapacity(Map<LocalDate, Map<String, BigDecimal>> availableSnap,
                                            Map<LocalDate, Map<String, BigDecimal>> remaining,
                                            Set<LocalDate> holidays, LocalDate day, String key) {
        if (holidays.contains(day)) {
            return BigDecimal.ZERO;
        }
        Map<String, BigDecimal> dayRemaining = remaining.get(day);
        if (dayRemaining != null && dayRemaining.containsKey(key)) {
            return dayRemaining.get(key).max(BigDecimal.ZERO);
        }
        Map<String, BigDecimal> daySnap = availableSnap.get(day);
        if (daySnap != null && daySnap.containsKey(key)) {
            // 快照已耗尽(被本引擎占用) → 0
            return BigDecimal.ZERO;
        }
        return DEFAULT_DAILY_HOURS;
    }

    /**
     * 资源负荷统计: horizon 内 booked(剩余扣减量) / available(初始快照+无日历日默认 8h)
     */
    private static List<ResourceLoad> buildLoads(Set<String> touchedKeys, Map<String, String> resourceNames,
                                                 Map<LocalDate, Map<String, BigDecimal>> remaining,
                                                 Map<LocalDate, Map<String, BigDecimal>> availableSnap,
                                                 LocalDate start, LocalDate end, Set<LocalDate> holidays) {
        List<ResourceLoad> loads = new ArrayList<>();
        for (String key : touchedKeys) {
            BigDecimal booked = BigDecimal.ZERO;
            BigDecimal available = BigDecimal.ZERO;
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                if (holidays.contains(d)) {
                    continue;
                }
                Map<String, BigDecimal> dayRemaining = remaining.get(d);
                Map<String, BigDecimal> daySnap = availableSnap.get(d);
                BigDecimal snapValue = daySnap == null ? null : daySnap.get(key);
                // 产能基准: 有日历行用快照值, 否则默认 8h
                BigDecimal capValue = snapValue != null ? snapValue.max(BigDecimal.ZERO) : DEFAULT_DAILY_HOURS;
                available = available.add(capValue);
                if (dayRemaining != null && dayRemaining.containsKey(key)) {
                    booked = booked.add(capValue.subtract(dayRemaining.get(key)).max(BigDecimal.ZERO));
                }
            }
            ResourceLoad load = new ResourceLoad();
            load.setResourceKey(key);
            load.setResourceName(resourceNames.getOrDefault(key, key));
            load.setBookedHours(booked.setScale(2, RoundingMode.HALF_UP));
            load.setAvailableHours(available.setScale(2, RoundingMode.HALF_UP));
            load.setRate(available.compareTo(BigDecimal.ZERO) > 0
                ? booked.divide(available, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
            loads.add(load);
        }
        loads.sort(Comparator.comparing(ResourceLoad::getRate).reversed());
        return loads;
    }

    private static Map<LocalDate, Map<String, BigDecimal>> copyCapacity(Map<LocalDate, Map<String, BigDecimal>> source) {
        Map<LocalDate, Map<String, BigDecimal>> copy = new HashMap<>();
        if (source != null) {
            source.forEach((d, m) -> copy.put(d, new HashMap<>(m == null ? Map.of() : m)));
        }
        return copy;
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static Long nvl(Long v) {
        return v == null ? Long.MAX_VALUE : v;
    }

    /**
     * 空白判断(null 或全空白字符), 引擎零依赖故不引 StrUtil
     */
    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
