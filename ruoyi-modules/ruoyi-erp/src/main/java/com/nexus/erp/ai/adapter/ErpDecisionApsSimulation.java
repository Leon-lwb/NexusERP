package com.nexus.erp.ai.adapter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.ai.decision.dto.ApsScenarioCompareResult;
import com.nexus.common.ai.decision.dto.DecisionKpiSet;
import com.nexus.common.ai.decision.dto.DecisionScenario;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.ArrivalFact;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.WorkOrderFact;
import com.nexus.common.ai.decision.dto.ShortageSimulationResult;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.schedule.PrdScheduleEngine;
import com.nexus.erp.prd.schedule.PrdScheduleInputAssembler;
import com.nexus.erp.prd.schedule.ScheduleModel.ResourceLoad;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduleInput;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduleResult;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduledTask;
import com.nexus.erp.prd.schedule.ScheduleModel.StepInfo;
import com.nexus.erp.prd.schedule.ScheduleModel.Strategy;
import com.nexus.erp.prd.schedule.ScheduleModel.WoInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** 只读快照与显式工单范围的内存 APS 比较；租户、数据源和授权由调用方 Port 保证。 */
@Component
@RequiredArgsConstructor
public class ErpDecisionApsSimulation {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<String> DEFAULT_CODES =
        List.of("BASELINE_CURRENT", "APS_EDD", "APS_CR", "APS_CR_EXPEDITE");
    private static final Set<String> SCHEDULABLE = Set.of("CREATED", "APPROVED", "RELEASED", "IN_PROGRESS");
    private final PrdScheduleInputAssembler scheduleInputAssembler;
    private final PrdWorkOrderMapper workOrderMapper;
    private final ErpDecisionShortageSimulation shortageSimulation;

    public ApsScenarioCompareResult compare(DeliveryContextSnapshot snapshot, List<String> scenarioCodes,
                                            int horizonDays) {
        validate(snapshot, horizonDays);
        List<String> codes = scenarioCodes == null || scenarioCodes.isEmpty() ? DEFAULT_CODES : scenarioCodes;
        if (codes.size() > 4 || codes.stream().anyMatch(s -> s == null || s.isBlank())
            || new HashSet<>(codes).size() != codes.size()) {
            throw new IllegalArgumentException("场景编码必须非空、唯一且最多四个");
        }
        TreeMap<Long, WorkOrderFact> facts = snapshotWorkOrders(snapshot.workOrders());
        SavedSchedule saved = readSavedSchedule(snapshot.currentSchedule(), facts);
        Map<Long, LocalDate> savedEnds = parseId(snapshot.currentSchedule().get("planId")) == null
            || saved.invalid() ? Map.of() : saved.ends();
        List<DecisionScenario> results = new ArrayList<>();
        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        if (snapshot.warnings() != null) warnings.addAll(snapshot.warnings());
        DecisionScenario cr = null;
        Map<Strategy, ScheduleInput> usedInputs = new HashMap<>();
        for (String code : codes) {
            switch (code) {
                case "BASELINE_CURRENT" -> results.add(baseline(snapshot, facts, saved, horizonDays, warnings));
                case "APS_EDD" -> results.add(candidate(snapshot, facts, savedEnds, "APS_EDD", Strategy.EDD,
                    horizonDays, warnings, usedInputs));
                case "APS_CR" -> {
                    if (cr == null) cr = candidate(snapshot, facts, savedEnds, "APS_CR", Strategy.CR,
                        horizonDays, warnings, usedInputs);
                    results.add(cr);
                }
                case "APS_CR_EXPEDITE" -> {
                    if (cr == null) cr = candidate(snapshot, facts, savedEnds, "APS_CR", Strategy.CR,
                        horizonDays, warnings, usedInputs);
                    results.add(expedite(snapshot, cr, horizonDays, warnings, usedInputs.get(Strategy.CR)));
                }
                default -> results.add(unavailable(snapshot, facts, horizonDays, code, code,
                    "UNSUPPORTED_SCENARIO"));
            }
        }
        if (results.stream().noneMatch(DecisionScenario::feasible)) warnings.add("ALL_SCENARIOS_INFEASIBLE");
        return new ApsScenarioCompareResult(List.copyOf(results), List.copyOf(warnings));
    }

    private static void validate(DeliveryContextSnapshot snapshot, int horizonDays) {
        if (snapshot == null || snapshot.rootBusinessRef() == null || snapshot.rootBusinessRef().type() == null
            || snapshot.rootBusinessRef().type() != DecisionBusinessType.SHORTAGE_HORIZON
                && !positive(snapshot.rootBusinessRef().id())
            || snapshot.snapshotAt() == null || snapshot.snapshotHash() == null
            || snapshot.snapshotHash().isBlank() || snapshot.workOrders() == null
            || snapshot.currentSchedule() == null) {
            throw new IllegalArgumentException("缺少完整的交付上下文快照");
        }
        if (horizonDays < 1 || horizonDays > 90) {
            throw new IllegalArgumentException("推演窗口必须在 1..90 天内");
        }
    }

    private static TreeMap<Long, WorkOrderFact> snapshotWorkOrders(List<WorkOrderFact> orders) {
        TreeMap<Long, WorkOrderFact> facts = new TreeMap<>();
        for (WorkOrderFact fact : orders) {
            if (fact == null || !positive(fact.id()) || facts.putIfAbsent(fact.id(), fact) != null) {
                throw new IllegalArgumentException("范围工单 ID 无效或重复");
            }
        }
        return facts;
    }

    private DecisionScenario baseline(DeliveryContextSnapshot snapshot, Map<Long, WorkOrderFact> facts,
                                      SavedSchedule schedule, int horizonDays, Set<String> warnings) {
        Map<String, Object> saved = snapshot.currentSchedule();
        if (parseId(saved.get("planId")) == null || schedule.ends().isEmpty() && !schedule.invalid()) {
            return unavailable(snapshot, facts, horizonDays, "BASELINE_CURRENT", "CURRENT", "NO_SAVED_SCHEDULE");
        }
        String strategy = saved.get("strategy") instanceof String s && !s.isBlank() ? s : "CURRENT";
        LocalDate makespan = parseDay(saved.get("makespanDate"));
        LocalDate terminalMax = schedule.ends().values().stream().max(LocalDate::compareTo).orElse(null);
        if (schedule.invalid() || makespan != null && terminalMax != null && makespan.isBefore(terminalMax)) {
            return unavailable(snapshot, facts, horizonDays, "BASELINE_CURRENT", strategy,
                "INVALID_SAVED_SCHEDULE");
        }
        // The saved plan's makespan may include work orders outside this decision's scope.
        makespan = terminalMax;
        List<Map<String, Object>> preview = new ArrayList<>();
        for (Object task : (List<?>) saved.get("tasks")) {
            if (!(task instanceof Map<?, ?> row) || !schedule.ends().containsKey(parseId(row.get("woId")))) continue;
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            row.forEach((key, value) -> {
                if (key instanceof String name) copy.put(name, name.endsWith("Id") && value != null
                    ? String.valueOf(value) : value);
            });
            preview.add(copy);
        }
        preview.sort(PREVIEW_ORDER);
        warnings.add("NO_BASELINE_UTILIZATION");
        ShortageSimulationResult shortage = simulateShortage(snapshot, horizonDays, Map.of(), warnings);
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        addMaterialReasons(shortage, reasons);
        DecisionKpiSet kpis = materialKnown(shortage)
            ? kpis(schedule.ends(), facts, makespan, null, 0, 0, shortage) : null;
        return new DecisionScenario("BASELINE_CURRENT", "saved schedule", strategy, reasons.isEmpty(),
            List.copyOf(reasons), kpis, null, 0, Map.of(), evidence(snapshot, shortage),
            limitPreview(preview), hashBaseline(snapshot, facts, saved, horizonDays, "BASELINE_CURRENT", strategy),
            delayedIds(schedule.ends(), facts), List.of());
    }

    private DecisionScenario candidate(DeliveryContextSnapshot snapshot, Map<Long, WorkOrderFact> facts,
                                       Map<Long, LocalDate> savedEnds, String code, Strategy strategy,
                                       int horizonDays, Set<String> warnings,
                                       Map<Strategy, ScheduleInput> usedInputs) {
        // Never use the unscoped assembler overload. Validate every row before the second, scoped load.
        List<Long> ids = new ArrayList<>(facts.keySet());
        if (ids.isEmpty()) return unavailable(snapshot, facts, horizonDays, code, strategy.name(),
            "NO_SCHEDULABLE_WORK_ORDERS");
        Map<Long, PrdWorkOrder> loaded = new TreeMap<>();
        for (int offset = 0; offset < ids.size(); offset += 500) {
            List<Long> batch = ids.subList(offset, Math.min(offset + 500, ids.size()));
            List<PrdWorkOrder> rows = workOrderMapper.selectList(Wrappers.lambdaQuery(PrdWorkOrder.class)
                .in(PrdWorkOrder::getWoId, batch));
            if (rows == null) throw stale("scoped work orders not returned");
            for (PrdWorkOrder row : rows) {
                if (row == null || !batch.contains(row.getWoId())
                    || loaded.putIfAbsent(row.getWoId(), row) != null) {
                    throw stale("unexpected or duplicate scoped work order");
                }
            }
        }
        if (loaded.size() != facts.size()) throw stale("scoped work orders incomplete");
        List<Long> schedulable = new ArrayList<>();
        for (Map.Entry<Long, PrdWorkOrder> entry : loaded.entrySet()) {
            PrdWorkOrder row = entry.getValue();
            // Check every row (including currently unschedulable ones) against the observed snapshot.
            if (!matches(row, facts.get(entry.getKey()))) {
                throw stale("work order changed during scoped read");
            }
            if (SCHEDULABLE.contains(row.getWoStatus())) schedulable.add(entry.getKey());
        }
        if (schedulable.isEmpty()) return unavailable(snapshot, facts, horizonDays, code, strategy.name(),
            "NO_SCHEDULABLE_WORK_ORDERS");
        PrdSchedulePlanBo bo = new PrdSchedulePlanBo();
        bo.setStrategy(strategy.name());
        bo.setStartDate(Date.from(snapshot.snapshotAt().toLocalDate().atStartOfDay(BUSINESS_ZONE).toInstant()));
        bo.setHorizonDays(horizonDays);
        ScheduleInput input = scheduleInputAssembler.assemble(bo, List.copyOf(schedulable));
        // The assembler performs another database read; reject facts changed between the two reads.
        if (input == null || input.getWorkOrders() == null || input.getStartDate() == null
            || !input.getStartDate().equals(snapshot.snapshotAt().toLocalDate())
            || input.getHorizonDays() != horizonDays || input.getStrategy() != strategy
            || input.getWorkOrders().size() != schedulable.size()) {
            throw stale("scoped assembler input changed");
        }
        Set<Long> seen = new HashSet<>();
        for (WoInfo wo : input.getWorkOrders()) {
            if (wo == null || !schedulable.contains(wo.getWoId()) || !seen.add(wo.getWoId())
                || !matches(wo, facts.get(wo.getWoId()))) {
                throw stale("work order changed during scoped read");
            }
        }
        usedInputs.put(strategy, input);
        validateScopedFacts(snapshot, input, horizonDays);
        ScheduleResult scheduled = PrdScheduleEngine.schedule(input);
        List<ScheduledTask> tasks = scheduled.getTasks();
        TreeMap<Long, LocalDate> ends = new TreeMap<>();
        List<Map<String, Object>> preview = new ArrayList<>();
        if (tasks != null) {
            for (ScheduledTask task : tasks) {
                if (task.getWo() == null || task.getWo().getWoId() == null || !seen.contains(task.getWo().getWoId())) {
                    throw new IllegalArgumentException("排程结果含范围外工单");
                }
                ends.merge(task.getWo().getWoId(), task.getEndDay(),
                    (a, b) -> a.isAfter(b) ? a : b);
                preview.add(taskPreview(task));
            }
        }
        preview.sort(PREVIEW_ORDER);
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        if (tasks == null || tasks.isEmpty() || scheduled.getSkipped() == null
            || !scheduled.getSkipped().isEmpty() || ends.size() != schedulable.size()
            || ends.values().stream().anyMatch(d -> d.isAfter(input.getStartDate().plusDays(horizonDays - 1L)))) {
            reasons.add("SCHEDULE_INCOMPLETE");
        }
        if (savedEnds.isEmpty()) warnings.add("NO_BASELINE_FOR_CHANGE_COMPARISON");
        int changed = 0;
        for (Long id : schedulable) {
            if (savedEnds.isEmpty() || !Objects.equals(savedEnds.get(id), ends.get(id))) changed++;
        }
        ShortageSimulationResult shortage = simulateShortage(snapshot, horizonDays, Map.of(), warnings);
        addMaterialReasons(shortage, reasons);
        BigDecimal maxRate = scheduled.getLoads() == null ? null : scheduled.getLoads().stream()
            .map(ResourceLoad::getRate).filter(Objects::nonNull).max(BigDecimal::compareTo).orElse(null);
        if (maxRate == null) warnings.add("NO_RESOURCE_LOAD");
        int overloaded = scheduled.getLoads() == null ? 0 : (int) scheduled.getLoads().stream()
            .filter(load -> load.getRate() != null && load.getRate().compareTo(BigDecimal.valueOf(100)) > 0).count();
        List<String> bottlenecks = scheduled.getLoads() == null ? List.of() : scheduled.getLoads().stream()
            .filter(load -> load.getResourceKey() != null && maxRate != null && maxRate.compareTo(load.getRate()) == 0)
            .map(ResourceLoad::getResourceKey).sorted().toList();
        DecisionKpiSet kpis = materialKnown(shortage)
            ? kpis(ends, facts, scheduled.getMakespan(), maxRate, overloaded, changed, shortage) : null;
        return new DecisionScenario(code, code, strategy.name(), reasons.isEmpty(), List.copyOf(reasons), kpis,
            null, 0, Map.of(), evidence(snapshot, shortage), limitPreview(preview), hashInput(snapshot, input, code, horizonDays, Map.of()),
            delayedIds(ends, facts), bottlenecks);
    }

    private DecisionScenario expedite(DeliveryContextSnapshot snapshot, DecisionScenario cr,
                                      int horizonDays, Set<String> warnings, ScheduleInput crInput) {
        String code = "APS_CR_EXPEDITE";
        if (cr.kpis() == null) {
            return copyExpedite(cr, List.of("SHORTAGE_UNKNOWN"), Map.of(),
                expediteHash(snapshot, crInput, horizonDays, Map.of()), cr.evidenceKeys());
        }
        ShortageSimulationResult baseline = simulateShortage(snapshot, horizonDays, Map.of(), warnings);
        if (baseline == null || baseline.kpis() == null) return copyExpedite(cr,
            List.of("SHORTAGE_UNKNOWN"), Map.of(), expediteHash(snapshot, crInput, horizonDays, Map.of()), cr.evidenceKeys());
        LocalDate today = snapshot.snapshotAt().toLocalDate();
        TreeMap<Long, String> overridesByLine = new TreeMap<>();
        for (ShortageSimulationResult.Item item : baseline.items()) {
            if (item.firstShortageDate() == null) continue;
            if (snapshot.arrivals() == null) return copyExpedite(cr,
                List.of("SHORTAGE_UNKNOWN"), Map.of(), expediteHash(snapshot, crInput, horizonDays, Map.of()), cr.evidenceKeys());
            ArrivalFact line = snapshot.arrivals().stream()
                .filter(a -> Objects.equals(a.materialId(), item.materialId()) && a.purchaseLineId() != null
                    && a.expectedDate() != null && a.expectedDate().isAfter(item.firstShortageDate())
                    && a.remainingQty() != null && a.remainingQty().signum() > 0)
                .min(Comparator.comparing(ArrivalFact::purchaseLineId)).orElse(null);
            if (line == null) return copyExpedite(cr,
                List.of("NO_EXPEDITABLE_PURCHASE_LINE"), Map.of(), expediteHash(snapshot, crInput, horizonDays, Map.of()), cr.evidenceKeys());
            LocalDate proposed = item.firstShortageDate().minusDays(1);
            if (proposed.isBefore(today)) proposed = today;
            overridesByLine.put(line.purchaseLineId(), proposed.toString());
        }
        if (overridesByLine.isEmpty()) return copyExpedite(cr,
            List.of("NO_EXPEDITABLE_PURCHASE_LINE"), Map.of(), expediteHash(snapshot, crInput, horizonDays, Map.of()), cr.evidenceKeys());
        Map<String, String> datesByLine = new LinkedHashMap<>();
        overridesByLine.forEach((id, date) -> datesByLine.put(String.valueOf(id), date));
        Map<String, Object> overrides = Map.of("purchaseArrivalOverrides", datesByLine);
        warnings.add("EXPEDITE_IS_ASSUMPTION_NOT_SUPPLIER_COMMITMENT");
        ShortageSimulationResult changed = simulateShortage(snapshot, horizonDays, overrides, warnings);
        LinkedHashSet<String> reasons = new LinkedHashSet<>(cr.infeasibleReasons());
        reasons.remove("MATERIAL_SHORTAGE");
        reasons.remove("SHORTAGE_UNKNOWN");
        addMaterialReasons(changed, reasons);
        DecisionKpiSet old = cr.kpis();
        DecisionKpiSet changedKpis = materialKnown(changed) ? new DecisionKpiSet(
            changed.kpis().impactedOrderCount(),
            old.delayedWorkOrderCount(), old.totalTardinessDays(), old.maxTardinessDays(), old.makespanDate(),
            changed.riskMaterialCount(), changed.totalGap(),
            changed.earliestShortageDate() == null ? null : changed.earliestShortageDate().toString(),
            old.maxResourceUtilization(), old.overloadedResourceCount(), old.changedWorkOrderCount(), old.actionCost())
            : null;
        // Derive the CR input hash from exactly the same input, with only the override changing.
        String hash = expediteHash(snapshot, crInput, horizonDays, overrides);
        return new DecisionScenario(code, code, "CR", reasons.isEmpty(), List.copyOf(reasons), changedKpis,
            null, 0, overrides, evidence(snapshot, changed), cr.schedulePreview(), hash,
            cr.delayedOrders(), cr.bottleneckResources());
    }

    private static String expediteHash(DeliveryContextSnapshot snapshot, ScheduleInput input,
                                       int horizonDays, Map<String, Object> overrides) {
        if (input == null) {
            Map<Long, WorkOrderFact> facts = snapshotWorkOrders(snapshot.workOrders());
            return hashBaseline(snapshot, facts, snapshot.currentSchedule(), horizonDays,
                "APS_CR_EXPEDITE", "CR");
        }
        return hashInput(snapshot, input, "APS_CR_EXPEDITE", horizonDays, overrides);
    }

    private static DecisionScenario copyExpedite(DecisionScenario cr, List<String> reasons,
                                                 Map<String, Object> overrides,
                                                  String hash, List<String> evidence) {
        LinkedHashSet<String> combined = new LinkedHashSet<>(cr.infeasibleReasons());
        combined.addAll(reasons);
        return new DecisionScenario("APS_CR_EXPEDITE", "APS_CR_EXPEDITE", "CR", false,
            List.copyOf(combined), cr.kpis(),
            null, 0, overrides, evidence, cr.schedulePreview(), hash, cr.delayedOrders(), cr.bottleneckResources());
    }

    private ShortageSimulationResult simulateShortage(DeliveryContextSnapshot snapshot, int days,
                                                     Map<String, Object> overrides, Set<String> warnings) {
        try {
            return shortageSimulation.simulate(snapshot, days, overrides);
        } catch (ServiceException incomplete) {
            if (incomplete.getMessage() == null
                || !incomplete.getMessage().startsWith("SHORTAGE_SIMULATION_INCOMPLETE")) throw incomplete;
            warnings.add("SHORTAGE_SIMULATION_INCOMPLETE");
            return null;
        }
    }
    private static ServiceException stale(String detail) {
        return new ServiceException("STALE_SNAPSHOT: " + detail);
    }

    private record RouteStep(Long id, Integer sequence, Long station, String hours,
                             String setup, int outsource, String waitTime, String transferTime) { }

    private static String engineHours(BigDecimal value) {
        return decimal(value == null ? BigDecimal.ZERO : value);
    }

    private static int engineOutsource(Integer value) {
        return Objects.equals(value, 1) ? 1 : 0;
    }

    private static void validateScopedFacts(DeliveryContextSnapshot snapshot, ScheduleInput input, int horizonDays) {
        LocalDate start = snapshot.snapshotAt().toLocalDate();
        LocalDate end = start.plusDays(horizonDays - 1L);
        if (snapshot.routingByMaterial() == null || snapshot.capacityByDate() == null
            || snapshot.holidays() == null || input.getCapacityByDate() == null || input.getHolidays() == null) {
            throw stale("missing route, capacity or holiday facts");
        }
        Set<Long> materials = new HashSet<>();
        for (WoInfo wo : input.getWorkOrders()) {
            Long materialId = wo.getMaterialId();
            if (materialId == null || !materials.add(materialId)) continue;
            List<DeliveryContextSnapshot.RoutingStepFact> snapshotSteps = snapshot.routingByMaterial().get(materialId);
            List<StepInfo> liveSteps = input.getRoutingProvider() == null ? null
                : input.getRoutingProvider().apply(materialId);
            if (snapshotSteps == null || liveSteps == null || snapshotSteps.stream().anyMatch(Objects::isNull)
                || liveSteps.stream().anyMatch(Objects::isNull)) {
                throw stale("missing routing for material " + materialId);
            }
            List<RouteStep> expected = snapshotSteps.stream()
                .map(step -> new RouteStep(step.stepId(), step.seqNum(), step.workstationId(),
                    engineHours(step.standardHours()), engineHours(step.setupHours()),
                    engineOutsource(step.isOutsource()), engineHours(step.waitTime()),
                    engineHours(step.transferTime())))
                .sorted(ROUTE_ORDER).toList();
            List<RouteStep> actual = liveSteps.stream()
                .map(step -> new RouteStep(step.getStepId(), step.getSeqNum(), step.getWorkstationId(),
                    engineHours(step.getStandardHours()), engineHours(step.getSetupHours()),
                    engineOutsource(step.getIsOutsource()), engineHours(step.getWaitTime()),
                    engineHours(step.getTransferTime())))
                .sorted(ROUTE_ORDER).toList();
            if (!expected.equals(actual)) throw stale("routing changed for material " + materialId);
        }
        if (!capacityInWindow(snapshot.capacityByDate(), start, end)
                .equals(capacityInWindow(input.getCapacityByDate(), start, end))) {
            throw stale("capacity changed");
        }
        List<LocalDate> savedHolidays = snapshot.holidays().stream()
            .filter(day -> !day.isBefore(start) && !day.isAfter(end)).sorted().toList();
        List<LocalDate> liveHolidays = input.getHolidays().stream()
            .filter(day -> !day.isBefore(start) && !day.isAfter(end)).sorted().toList();
        if (!savedHolidays.equals(liveHolidays)) throw stale("holidays changed");
    }

    private static final Comparator<RouteStep> ROUTE_ORDER =
        Comparator.comparing(RouteStep::sequence, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(RouteStep::id, Comparator.nullsLast(Long::compareTo))
            .thenComparing(RouteStep::station, Comparator.nullsLast(Long::compareTo))
            .thenComparing(RouteStep::hours)
            .thenComparing(RouteStep::setup)
            .thenComparingInt(RouteStep::outsource)
            .thenComparing(RouteStep::waitTime)
            .thenComparing(RouteStep::transferTime);

    private static Map<String, Map<String, String>> capacityInWindow(
        Map<LocalDate, Map<String, BigDecimal>> byDay, LocalDate start, LocalDate end) {
        TreeMap<String, Map<String, String>> normalized = new TreeMap<>();
        byDay.forEach((day, resources) -> {
            if (day == null || resources == null) throw stale("invalid capacity facts");
            if (!day.isBefore(start) && !day.isAfter(end)) {
                TreeMap<String, String> sorted = new TreeMap<>();
                resources.forEach((key, hours) -> sorted.put(key, decimal(hours)));
                normalized.put(day.toString(), sorted);
            }
        });
        return normalized;
    }

    private static boolean matches(PrdWorkOrder row, WorkOrderFact fact) {
        BigDecimal planned = row.getPlannedQty() == null ? BigDecimal.ZERO : row.getPlannedQty();
        BigDecimal completed = row.getCompletedQty() == null ? BigDecimal.ZERO : row.getCompletedQty();
        return Objects.equals(row.getWoNo(), fact.code())
            && fact.remainingQty() != null
            && fact.remainingQty().compareTo(planned.subtract(completed).max(BigDecimal.ZERO)) == 0
            && Objects.equals(row.getMaterialId(), fact.materialId())
            && Objects.equals(row.getPriority(), fact.priority())
            && Objects.equals(row.getWorkshopId(), fact.workshopId())
            && Objects.equals(day(row.getPlanEndDate()), fact.planEndDate());
    }

    private static boolean matches(WoInfo wo, WorkOrderFact fact) {
        return fact != null && Objects.equals(wo.getWoNo(), fact.code())
            && wo.getRemainingQty() != null && fact.remainingQty() != null
            && wo.getRemainingQty().compareTo(fact.remainingQty()) == 0
            && Objects.equals(wo.getMaterialId(), fact.materialId())
            && Objects.equals(wo.getPriority(), fact.priority())
            && Objects.equals(wo.getWorkshopId(), fact.workshopId())
            && Objects.equals(wo.getDueDate(), fact.planEndDate());
    }

    private static LocalDate day(Date date) {
        if (date == null) return null;
        if (date instanceof java.sql.Date sql) return sql.toLocalDate();
        return Instant.ofEpochMilli(date.getTime()).atZone(BUSINESS_ZONE).toLocalDate();
    }

    private static boolean materialKnown(ShortageSimulationResult result) {
        return result != null && result.kpis() != null
            && (result.warnings() == null || !result.warnings().contains("NO_REQUIREMENT_DATA"));
    }

    private static void addMaterialReasons(ShortageSimulationResult result, Set<String> reasons) {
        if (!materialKnown(result)) reasons.add("SHORTAGE_UNKNOWN");
        else if (result.hasRisk()) reasons.add("MATERIAL_SHORTAGE");
    }

    private static DecisionKpiSet kpis(Map<Long, LocalDate> ends, Map<Long, WorkOrderFact> facts,
                                       LocalDate makespan, BigDecimal maxUtil, int overload,
                                       int changed, ShortageSimulationResult shortage) {
        int total = 0, max = 0;
        for (Map.Entry<Long, LocalDate> entry : ends.entrySet()) {
            LocalDate due = facts.get(entry.getKey()).planEndDate();
            if (due == null) continue;
            int days = (int) Math.max(0L, ChronoUnit.DAYS.between(due, entry.getValue()));
            total += days;
            max = Math.max(max, days);
        }
        return new DecisionKpiSet(shortage == null || shortage.kpis() == null ? 0
                : shortage.kpis().impactedOrderCount(), delayedIds(ends, facts).size(), total, max,
            makespan == null ? null : makespan.toString(), shortage == null ? 0 : shortage.riskMaterialCount(),
            shortage == null || shortage.kpis() == null ? null : shortage.totalGap(),
            shortage == null || shortage.earliestShortageDate() == null ? null : shortage.earliestShortageDate().toString(),
            maxUtil, overload, changed, null);
    }

    private static List<Long> delayedIds(Map<Long, LocalDate> ends, Map<Long, WorkOrderFact> facts) {
        return ends.entrySet().stream().filter(e -> facts.get(e.getKey()).planEndDate() != null
            && e.getValue().isAfter(facts.get(e.getKey()).planEndDate())).map(Map.Entry::getKey).sorted().toList();
    }

    private record SavedTask(long taskId, int sequence, LocalDate start, LocalDate end) { }

    private record SavedSchedule(TreeMap<Long, LocalDate> ends, boolean invalid) { }

    /** Dates are taken from the terminal operation (sequence, then task ID), never from a previous step. */
    private static SavedSchedule readSavedSchedule(Map<String, Object> saved, Map<Long, WorkOrderFact> facts) {
        TreeMap<Long, LocalDate> ends = new TreeMap<>();
        if (!(saved.get("tasks") instanceof List<?> tasks) || tasks.isEmpty()) {
            return new SavedSchedule(ends, false);
        }
        TreeMap<Long, List<SavedTask>> byWorkOrder = new TreeMap<>();
        boolean invalid = false;
        for (Object task : tasks) {
            if (!(task instanceof Map<?, ?> row)) { invalid = true; continue; }
            Long woId = parseId(row.get("woId"));
            if (!facts.containsKey(woId)) continue;
            Long taskId = parseId(row.get("taskId"));
            Object number = row.get("seqNum");
            LocalDate start = parseDay(row.get("startTime"));
            LocalDate end = parseDay(row.get("endTime"));
            if (taskId == null || !(number instanceof Number seq) || seq.longValue() < 0
                || seq.longValue() > Integer.MAX_VALUE || start == null || end == null || end.isBefore(start)) {
                invalid = true;
                continue;
            }
            byWorkOrder.computeIfAbsent(woId, ignored -> new ArrayList<>())
                .add(new SavedTask(taskId, seq.intValue(), start, end));
        }
        for (Map.Entry<Long, List<SavedTask>> entry : byWorkOrder.entrySet()) {
            List<SavedTask> ordered = entry.getValue();
            ordered.sort(Comparator.comparingInt(SavedTask::sequence).thenComparingLong(SavedTask::taskId));
            Set<Long> taskIds = new HashSet<>();
            SavedTask previous = null;
            for (SavedTask task : ordered) {
                if (!taskIds.add(task.taskId()) || previous != null && task.start().isBefore(previous.end())) {
                    invalid = true;
                }
                previous = task;
            }
            ends.put(entry.getKey(), ordered.get(ordered.size() - 1).end());
        }
        return new SavedSchedule(ends, invalid);
    }

    private static Map<String, Object> taskPreview(ScheduledTask task) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("woId", String.valueOf(task.getWo().getWoId()));
        row.put("woNo", task.getWo().getWoNo());
        row.put("stepId", task.getStep().getStepId() == null ? null : String.valueOf(task.getStep().getStepId()));
        row.put("seqNum", task.getStep().getSeqNum());
        row.put("resourceType", task.getResourceType());
        row.put("resourceId", task.getResourceId() == null ? null : String.valueOf(task.getResourceId()));
        row.put("resourceName", task.getResourceName());
        row.put("startTime", task.getStartDay() == null ? null : task.getStartDay().toString());
        row.put("endTime", task.getEndDay() == null ? null : task.getEndDay().toString());
        row.put("processHours", decimal(task.getProcessHours()));
        row.put("setupHours", decimal(task.getSetupHours()));
        row.put("delayFlag", task.isDelay() ? 1 : 0);
        return row;
    }

    private static final Comparator<Map<String, Object>> PREVIEW_ORDER =
        Comparator.comparing((Map<String, Object> row) -> parseId(row.get("woId")),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(row -> row.get("seqNum") instanceof Number n ? n.intValue() : Integer.MAX_VALUE)
            .thenComparing(row -> parseId(row.get("taskId")), Comparator.nullsLast(Long::compareTo))
            .thenComparing(row -> String.valueOf(row.get("startTime")))
            .thenComparing(row -> parseId(row.get("stepId")), Comparator.nullsLast(Long::compareTo))
            .thenComparing(row -> JsonUtils.toJsonString(new TreeMap<>(row)));

    private static List<Map<String, Object>> limitPreview(List<Map<String, Object>> rows) {
        return rows.stream().limit(50)
            .map(row -> java.util.Collections.unmodifiableMap(new LinkedHashMap<>(removeNulls(row))))
            .toList();
    }

    private static Map<String, Object> removeNulls(Map<String, Object> map) {
        LinkedHashMap<String, Object> safe = new LinkedHashMap<>();
        map.forEach((k, v) -> { if (v != null) safe.put(k, v); });
        return safe;
    }

    private static List<String> evidence(DeliveryContextSnapshot snapshot, ShortageSimulationResult shortage) {
        LinkedHashSet<String> refs = new LinkedHashSet<>();
        refs.add("snapshot:" + snapshot.snapshotHash());
        if (shortage != null && shortage.evidenceRefs() != null) refs.addAll(shortage.evidenceRefs());
        return List.copyOf(refs);
    }

    private static String hashBaseline(DeliveryContextSnapshot snapshot, Map<Long, WorkOrderFact> facts,
                                       Map<String, Object> saved, int horizonDays, String code, String strategy) {
        LinkedHashMap<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("snapshotHash", snapshot.snapshotHash());
        canonical.put("horizonDays", horizonDays);
        canonical.put("scenarioCode", code);
        canonical.put("strategy", strategy);
        canonical.put("overrides", Map.of());
        canonical.put("workOrders", facts.values().stream().map(ErpDecisionApsSimulation::workOrderFactMap).toList());
        TreeMap<String, Object> normalized = new TreeMap<>(saved);
        if (saved.get("tasks") instanceof List<?> tasks) {
            List<Map<String, Object>> sortedTasks = new ArrayList<>();
            for (Object task : tasks) {
                if (task instanceof Map<?, ?> row) {
                    TreeMap<String, Object> copy = new TreeMap<>();
                    row.forEach((k, v) -> { if (k instanceof String key) copy.put(key, v); });
                    sortedTasks.add(copy);
                }
            }
            sortedTasks.sort(PREVIEW_ORDER);
            normalized.put("tasks", sortedTasks);
        }
        if (saved.get("workOrderSalesOrders") instanceof List<?> links) {
            List<Map<String, Object>> sortedLinks = new ArrayList<>();
            for (Object item : links) {
                if (item instanceof Map<?, ?> link) {
                    TreeMap<String, Object> row = new TreeMap<>();
                    link.forEach((key, value) -> { if (key instanceof String name) row.put(name, value); });
                    sortedLinks.add(row);
                }
            }
            sortedLinks.sort(Comparator
                .comparing((Map<String, Object> row) -> parseId(row.get("workOrderId")),
                    Comparator.nullsLast(Long::compareTo))
                .thenComparing(row -> parseId(row.get("salesOrderId")),
                    Comparator.nullsLast(Long::compareTo)));
            normalized.put("workOrderSalesOrders", sortedLinks);
        }
        for (String idKey : List.of("workOrderIds", "workOrderSalesOrderIds")) {
            if (saved.get(idKey) instanceof List<?> ids) {
                normalized.put(idKey, ids.stream().map(String::valueOf)
                    .sorted(Comparator.comparing((String id) -> parseId(id),
                        Comparator.nullsLast(Long::compareTo)).thenComparing(Comparator.naturalOrder()))
                    .toList());
            }
        }
        canonical.put("savedSchedule", normalized);
        TreeMap<Long, List<Map<String, Object>>> routes = new TreeMap<>();
        if (snapshot.routingByMaterial() != null) {
            snapshot.routingByMaterial().forEach((material, steps) -> {
                if (steps == null) { routes.put(material, List.of()); return; }
                routes.put(material, steps.stream()
                    .sorted(Comparator.comparing(DeliveryContextSnapshot.RoutingStepFact::seqNum,
                            Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(DeliveryContextSnapshot.RoutingStepFact::stepId,
                            Comparator.nullsLast(Long::compareTo)))
                    .map(step -> {
                        Map<String, Object> row = new TreeMap<>();
                        row.put("id", step.stepId() == null ? null : String.valueOf(step.stepId()));
                        row.put("seq", step.seqNum());
                        row.put("station", step.workstationId() == null ? null : String.valueOf(step.workstationId()));
                        row.put("workshop", step.workshopId() == null ? null : String.valueOf(step.workshopId()));
                        row.put("hours", engineHours(step.standardHours()));
                        row.put("setup", engineHours(step.setupHours()));
                        row.put("outsource", engineOutsource(step.isOutsource()));
                        row.put("wait", engineHours(step.waitTime()));
                        row.put("transfer", engineHours(step.transferTime()));
                        return row;
                    }).toList());
            });
        }
        canonical.put("routing", routes);
        canonical.put("capacity", canonicalCapacity(snapshot.capacityByDate()));
        canonical.put("holidays", snapshot.holidays() == null ? List.of()
            : snapshot.holidays().stream().sorted().map(LocalDate::toString).toList());
        return hashCanonical(canonical);
    }

    private static Map<String, Object> workOrderFactMap(WorkOrderFact fact) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", String.valueOf(fact.id()));
        row.put("code", fact.code());
        row.put("materialId", fact.materialId() == null ? null : String.valueOf(fact.materialId()));
        row.put("qty", decimal(fact.remainingQty()));
        row.put("due", fact.planEndDate() == null ? null : fact.planEndDate().toString());
        row.put("priority", fact.priority() == null ? null : String.valueOf(fact.priority()));
        row.put("workshopId", fact.workshopId() == null ? null : String.valueOf(fact.workshopId()));
        return row;
    }

    private static String hashInput(DeliveryContextSnapshot snapshot, ScheduleInput input,
                                    String code, int horizonDays, Map<String, Object> overrides) {
        LinkedHashMap<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("snapshotHash", snapshot.snapshotHash());
        canonical.put("scenarioCode", code);
        canonical.put("strategy", input.getStrategy().name());
        canonical.put("startDate", input.getStartDate().toString());
        canonical.put("horizonDays", horizonDays);
        canonical.put("workOrders", input.getWorkOrders().stream().sorted(Comparator.comparing(WoInfo::getWoId))
            .map(wo -> {
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                row.put("id", String.valueOf(wo.getWoId()));
                row.put("code", wo.getWoNo());
                row.put("materialId", wo.getMaterialId() == null ? null : String.valueOf(wo.getMaterialId()));
                row.put("qty", decimal(wo.getRemainingQty()));
                row.put("due", wo.getDueDate() == null ? null : wo.getDueDate().toString());
                row.put("priority", wo.getPriority() == null ? null : String.valueOf(wo.getPriority()));
                row.put("workshopId", wo.getWorkshopId() == null ? null : String.valueOf(wo.getWorkshopId()));
                return row;
            }).toList());
        TreeMap<Long, List<Map<String, Object>>> routes = new TreeMap<>();
        for (WoInfo wo : input.getWorkOrders()) {
            if (wo.getMaterialId() == null || routes.containsKey(wo.getMaterialId())) continue;
            List<StepInfo> steps = input.getRoutingProvider() == null ? null : input.getRoutingProvider().apply(wo.getMaterialId());
            routes.put(wo.getMaterialId(), steps == null ? List.of() : steps.stream()
                .sorted(Comparator.comparing(StepInfo::getSeqNum, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(StepInfo::getStepId, Comparator.nullsLast(Long::compareTo)))
                .map(step -> {
                    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    row.put("stepId", step.getStepId() == null ? null : String.valueOf(step.getStepId()));
                    row.put("seq", step.getSeqNum());
                    row.put("workstationId", step.getWorkstationId() == null ? null : String.valueOf(step.getWorkstationId()));
                    row.put("hours", engineHours(step.getStandardHours()));
                    row.put("setup", engineHours(step.getSetupHours()));
                    row.put("outsource", engineOutsource(step.getIsOutsource()));
                    row.put("wait", engineHours(step.getWaitTime()));
                    row.put("transfer", engineHours(step.getTransferTime()));
                    return (Map<String, Object>) row;
                }).toList());
        }
        canonical.put("routing", routes);
        canonical.put("capacity", canonicalCapacity(input.getCapacityByDate()));
        canonical.put("holidays", input.getHolidays() == null ? List.of() : input.getHolidays().stream()
            .sorted().map(LocalDate::toString).toList());
        canonical.put("overrides", overrides);
        return hashCanonical(canonical);
    }

    private static Map<String, Map<String, String>> canonicalCapacity(
        Map<LocalDate, Map<String, BigDecimal>> byDate) {
        TreeMap<String, Map<String, String>> capacity = new TreeMap<>();
        if (byDate != null) byDate.forEach((date, resources) -> {
            TreeMap<String, String> sorted = new TreeMap<>();
            if (resources != null) resources.forEach((key, hours) -> sorted.put(key, decimal(hours)));
            capacity.put(date.toString(), sorted);
        });
        return capacity;
    }

    private static String hashCanonical(Object canonical) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(JsonUtils.toJsonString(canonical).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String decimal(BigDecimal number) {
        return number == null ? null : number.stripTrailingZeros().toPlainString();
    }

    private static boolean positive(Long id) { return id != null && id > 0; }

    private static Long parseId(Object value) {
        if (value == null) return null;
        try {
            long id = Long.parseLong(String.valueOf(value));
            return id > 0 ? id : null;
        } catch (NumberFormatException e) { return null; }
    }

    private static LocalDate parseDay(Object value) {
        if (!(value instanceof String text) || text.length() < 10) return null;
        try { return LocalDate.parse(text.substring(0, 10)); }
        catch (RuntimeException e) { return null; }
    }

    private static DecisionScenario unavailable(DeliveryContextSnapshot snapshot, Map<Long, WorkOrderFact> facts,
                                                 int horizonDays, String code, String strategy, String reason) {
        return new DecisionScenario(code, code, strategy, false, List.of(reason), null,
            null, 0, Map.of(), List.of("snapshot:" + snapshot.snapshotHash()), List.of(),
            hashBaseline(snapshot, facts, snapshot.currentSchedule(), horizonDays, code, strategy),
            List.of(), List.of());
    }
}
