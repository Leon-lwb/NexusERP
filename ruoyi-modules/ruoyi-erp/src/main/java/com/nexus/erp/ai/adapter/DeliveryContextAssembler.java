package com.nexus.erp.ai.adapter;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.ArrivalFact;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.OrderFact;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.RequirementFact;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.RoutingStepFact;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.StockFact;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.SupplierMetricFact;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.WorkOrderFact;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot.WorkOrderSalesLinkFact;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.domain.BdRouting;
import com.nexus.erp.bd.domain.BdRoutingStep;
import com.nexus.erp.bd.domain.BdWorkstation;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.bd.mapper.BdRoutingMapper;
import com.nexus.erp.bd.mapper.BdRoutingStepMapper;
import com.nexus.erp.bd.mapper.BdWorkstationMapper;
import com.nexus.erp.prd.domain.PrdCapacityCalendar;
import com.nexus.erp.prd.domain.PrdMaterialRequirement;
import com.nexus.erp.prd.domain.PrdMaterialRequirementDetail;
import com.nexus.erp.prd.domain.PrdProductionPlan;
import com.nexus.erp.prd.domain.PrdSchedulePlan;
import com.nexus.erp.prd.domain.PrdScheduleTask;
import com.nexus.erp.prd.domain.PrdScheduleTaskDayLoad;
import com.nexus.erp.prd.domain.PrdScheduleCapacityBooking;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo;
import com.nexus.erp.prd.mapper.PrdCapacityCalendarMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.mapper.PrdProductionPlanMapper;
import com.nexus.erp.prd.mapper.PrdSchedulePlanMapper;
import com.nexus.erp.prd.mapper.PrdScheduleTaskMapper;
import com.nexus.erp.prd.mapper.PrdScheduleTaskDayLoadMapper;
import com.nexus.erp.prd.mapper.PrdScheduleCapacityBookingMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.prd.schedule.PrdScheduleInputAssembler;
import com.nexus.erp.prd.schedule.ScheduleModel.ScheduleInput;
import com.nexus.erp.prd.schedule.ScheduleModel.StepInfo;
import com.nexus.erp.prd.schedule.ScheduleModel.Strategy;
import com.nexus.erp.prd.schedule.ScheduleModel.WoInfo;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;
import com.nexus.erp.pu.domain.PuVendorEvaluation;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseOrderMapper;
import com.nexus.erp.pu.mapper.PuVendorEvaluationMapper;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.mapper.SoSalesOrderHeaderMapper;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
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
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import java.util.stream.Stream;

/**
 * 装配 ERP 工业交付决策上下文，并生成可在审批前重算的关键业务指纹。
 */
@Component
@RequiredArgsConstructor
@DS("erp")
public class DeliveryContextAssembler {

    static final int FRESHNESS_HORIZON_DAYS = 90;
    private static final int IN_BATCH_SIZE = 500;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final String ADMIN_PERMISSION = "*:*:*";
    private static final String RUN_PERMISSION = "ai:decision:run";
    private static final Set<String> TERMINAL_WORK_ORDER_STATUSES = Set.of("COMPLETED", "CLOSED");
    private static final Set<String> SCHEDULABLE_WORK_ORDER_STATUSES =
        Set.of("CREATED", "APPROVED", "RELEASED", "IN_PROGRESS");
    private static final Set<String> OPEN_PURCHASE_ORDER_STATUSES =
        Set.of("APPROVED", "PARTIALLY_RECEIVED");

    private final SoSalesOrderHeaderMapper salesOrderHeaderMapper;
    private final SoSalesOrderLineMapper salesOrderLineMapper;
    private final PrdWorkOrderMapper workOrderMapper;
    private final PrdSchedulePlanMapper schedulePlanMapper;
    private final PrdScheduleTaskMapper scheduleTaskMapper;
    private final PrdScheduleTaskDayLoadMapper taskDayLoadMapper;
    private final PrdScheduleCapacityBookingMapper capacityBookingMapper;
    private final PrdMaterialRequirementMapper requirementMapper;
    private final PrdMaterialRequirementDetailMapper requirementDetailMapper;
    private final PrdProductionPlanMapper productionPlanMapper;
    private final WmMaterialStockMapper stockMapper;
    private final BdMaterialMapper materialMapper;
    private final com.nexus.erp.bd.mapper.BdUnitOfMeasureMapper evidenceUnitMapper;
    private final BdRoutingMapper routingMapper;
    private final BdRoutingStepMapper routingStepMapper;
    private final PrdCapacityCalendarMapper capacityCalendarMapper;
    private final SchHolidayMapper holidayMapper;
    private final SchEmployeeScheduleMapper employeeScheduleMapper;
    private final SchShiftMapper shiftMapper;
    private final SchTeamMapper teamMapper;
    private final SchLeaveApplyMapper leaveApplyMapper;
    private final BdWorkstationMapper workstationMapper;
    private final PuPurchaseOrderMapper purchaseOrderMapper;
    private final PuPurchaseOrderLineMapper purchaseOrderLineMapper;
    private final PuVendorEvaluationMapper vendorEvaluationMapper;
    private final PrdScheduleInputAssembler scheduleInputAssembler;

    /** 仅判断当前 actor 是否可读取该精确根对象；非法请求或无权限请求均拒绝。 */
    public boolean exists(DecisionBusinessRef ref, DecisionActor actor) {
        try {
            validateAccess(ref, actor, true);
            return switch (ref.type()) {
                case SALES_ORDER -> {
                    SoSalesOrderHeader order = salesOrderHeaderMapper.selectById(ref.id());
                    yield order != null && Objects.equals(ref.id(), order.getSoId());
                }
                case WORK_ORDER -> {
                    PrdWorkOrder workOrder = workOrderMapper.selectById(ref.id());
                    yield workOrder != null && Objects.equals(ref.id(), workOrder.getWoId())
                        && !isTerminalWorkOrder(workOrder);
                }
                case SCHEDULE_PLAN -> {
                    PrdSchedulePlan plan = schedulePlanMapper.selectById(ref.id());
                    yield plan != null && Objects.equals(ref.id(), plan.getPlanId());
                }
                case SHORTAGE_HORIZON -> true;
                default -> throw unsupportedType();
            };
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /**
     * 采购永久恢复专用的对象可见性：只允许本租户未删除的明确工单，可认领其后来的终态。
     * 不授予新分析、新建采购或来源额度，普通exists和核心快照算法保持原终态拒绝。
     */
    public boolean existsForPurchaseRecovery(DecisionBusinessRef ref, DecisionActor actor) {
        try {
            if (!TenantHelper.isEnable() || actor == null || actor.tenantId() == null
                || !actor.tenantId().equals(TenantHelper.getTenantId()) || ref == null
                || ref.type() != DecisionBusinessType.WORK_ORDER || ref.id() == null || ref.id() <= 0) return false;
            validateAccess(ref, actor, true);
            PrdWorkOrder workOrder = workOrderMapper.selectById(ref.id());
            return workOrder != null && Objects.equals(ref.id(), workOrder.getWoId())
                && actor.tenantId().equals(workOrder.getTenantId()) && "0".equals(workOrder.getDelFlag())
                && workOrder.getWoStatus() != null
                && Set.of("CREATED", "APPROVING", "APPROVED", "RELEASED", "IN_PROGRESS", "COMPLETED", "CLOSED")
                    .contains(workOrder.getWoStatus().trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    /**
     * 按调用方 horizon 装配展示快照；snapshotHash 始终使用固定 90 天关键指纹重算。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ, readOnly = true)
    public DeliveryContextSnapshot assemble(DecisionBusinessRef ref, int horizonDays, DecisionActor actor) {
        validateHorizon(horizonDays);
        validateAccess(ref, actor);
        LocalDate businessDate = businessDate();
        DecisionScope scope = loadScope(ref);
        LoadedContext context = loadContext(scope, FRESHNESS_HORIZON_DAYS, businessDate, actor.tenantId());
        String freshnessHash = hash(buildFingerprint(scope, context));
        SnapshotProjection projection = projectSnapshot(scope, context, horizonDays, businessDate);

        List<OrderFact> orderFacts = scope.salesOrders().stream()
            .sorted(orderComparator())
            .map(order -> new OrderFact(order.getSoId(), order.getSoNo(),
                toLocalDate(order.getPromiseDate()), order.getOrderStatus()))
            .toList();
        List<WorkOrderFact> workOrderFacts = scope.workOrders().stream()
            .sorted(workOrderComparator())
            .map(workOrder -> new WorkOrderFact(
                workOrder.getWoId(), workOrder.getWoNo(), workOrder.getMaterialId(),
                remaining(workOrder.getPlannedQty(), workOrder.getCompletedQty()),
                toLocalDate(workOrder.getPlanEndDate()), workOrder.getPriority(), workOrder.getWorkshopId()))
            .toList();

        return new DeliveryContextSnapshot(
            scope.normalizedRef(),
            orderFacts,
            workOrderFacts,
            projection.routingByMaterial(),
            projection.requirementFacts(),
            projection.stockFacts(),
            projection.arrivalFacts(),
            projection.capacityByDate(),
            projection.holidays(),
            projection.currentSchedule(),
            projection.supplierMetrics(),
            freshnessHash,
            LocalDateTime.now(BUSINESS_ZONE),
            projection.warnings(),
            scope.workOrderSalesOrderIds().entrySet().stream()
                .map(entry -> new WorkOrderSalesLinkFact(entry.getKey(), entry.getValue()))
                .toList());
    }

    /** 固定 90 天重算关键业务指纹；保持包可见供 checker 与同包测试使用。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ, readOnly = true)
    public String computeFreshnessHash(DecisionBusinessRef ref, DecisionActor actor) {
        validateAccess(ref, actor);
        LocalDate businessDate = businessDate();
        DecisionScope scope = loadScope(ref);
        LoadedContext context = loadContext(scope, FRESHNESS_HORIZON_DAYS, businessDate, actor.tenantId());
        return hash(buildFingerprint(scope, context));
    }

    LocalDate businessDate() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    private String hash(CriticalFreshnessFingerprint fingerprint) {
        return SecureUtil.sha256(JsonUtils.toJsonString(fingerprint));
    }

    private void validateHorizon(int horizonDays) {
        if (horizonDays < 1 || horizonDays > FRESHNESS_HORIZON_DAYS) {
            throw new ServiceException("决策 horizonDays 必须在 1 到 90 之间");
        }
    }

    private void validateAccess(DecisionBusinessRef ref, DecisionActor actor) {
        validateAccess(ref, actor, false);
    }

    private void validateAccess(DecisionBusinessRef ref, DecisionActor actor, boolean existenceOnly) {
        if (actor == null) {
            throw new ServiceException("决策执行人不能为空");
        }
        if (!hasText(actor.tenantId())) {
            throw new ServiceException("决策执行人 tenantId 不能为空");
        }
        if (actor.userId() == null || actor.userId() <= 0) {
            throw new ServiceException("决策执行人 userId 非法");
        }
        if (actor.permissions() == null) {
            throw new ServiceException("决策执行人 permissions 不能为空");
        }
        if (ref == null || ref.type() == null) {
            throw unsupportedType();
        }
        switch (ref.type()) {
            case SHORTAGE_HORIZON -> {
                if (ref.id() != null) {
                    throw new ServiceException("SHORTAGE_HORIZON 不允许指定业务 ID");
                }
            }
            case SALES_ORDER, WORK_ORDER, SCHEDULE_PLAN -> {
                if (ref.id() == null || ref.id() <= 0) {
                    throw new ServiceException("决策业务 ID 必须为正数");
                }
            }
            default -> throw unsupportedType();
        }
        /**
         * 仅有查询权限时只允许存在性检查，不执行仿真、快照装配或新鲜度计算。
         * 已授权执行人应能复核来源事实，不必额外授予发起新分析的权限。
         */
        boolean decisionRight = hasPermission(actor.permissions(), RUN_PERMISSION)
            || hasPermission(actor.permissions(), "ai:decision:execute")
            || existenceOnly && hasPermission(actor.permissions(), "ai:decision:query");
        if (!decisionRight || !hasPermission(actor.permissions(), permissionFor(ref.type()))) {
            throw new ServiceException("无权读取该决策业务上下文");
        }
    }

    private String permissionFor(DecisionBusinessType type) {
        return switch (type) {
            case SALES_ORDER -> "so:orderheader:query";
            case WORK_ORDER -> "prd:work:query";
            case SCHEDULE_PLAN -> "prd:schedule:query";
            case SHORTAGE_HORIZON -> "prd:shortage:query";
            default -> throw unsupportedType();
        };
    }

    private boolean hasPermission(Set<String> permissions, String required) {
        return permissions.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .anyMatch(permission -> ADMIN_PERMISSION.equals(permission) || required.equals(permission));
    }

    private ServiceException unsupportedType() {
        return new ServiceException("不支持的决策业务类型");
    }

    private DecisionScope loadScope(DecisionBusinessRef ref) {
        return switch (ref.type()) {
            case SALES_ORDER -> loadSalesOrderScope(ref.id());
            case WORK_ORDER -> loadWorkOrderScope(ref.id());
            case SCHEDULE_PLAN -> loadSchedulePlanScope(ref.id());
            case SHORTAGE_HORIZON -> loadShortageScope();
            default -> throw unsupportedType();
        };
    }

    private DecisionScope loadSalesOrderScope(Long salesOrderId) {
        SoSalesOrderHeader order = salesOrderHeaderMapper.selectById(salesOrderId);
        if (order == null || !Objects.equals(salesOrderId, order.getSoId())) {
            throw new ServiceException("销售订单不存在或不可访问");
        }
        List<SoSalesOrderLine> lines = safeList(salesOrderLineMapper.selectList(
            Wrappers.lambdaQuery(SoSalesOrderLine.class)
                .eq(SoSalesOrderLine::getSoId, salesOrderId))).stream()
            .filter(line -> Objects.equals(salesOrderId, line.getSoId()))
            .sorted(orderLineComparator())
            .toList();
        LinkedHashSet<Long> lineIds = lines.stream()
            .map(SoSalesOrderLine::getLineId)
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        TreeMap<Long, PrdProductionPlan> productionPlanById = new TreeMap<>();
        for (List<Long> batch : batches(lineIds)) {
            for (PrdProductionPlan plan : safeList(productionPlanMapper.selectList(
                Wrappers.lambdaQuery(PrdProductionPlan.class)
                    .in(PrdProductionPlan::getOrderLineId, batch)))) {
                if (plan.getPlanId() != null && lineIds.contains(plan.getOrderLineId())) {
                    productionPlanById.put(plan.getPlanId(), plan);
                }
            }
        }
        List<PrdProductionPlan> productionPlans = List.copyOf(productionPlanById.values());
        LinkedHashSet<Long> productionPlanIds = productionPlans.stream()
            .map(PrdProductionPlan::getPlanId)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        TreeMap<Long, PrdWorkOrder> candidates = new TreeMap<>();
        for (List<Long> batch : batches(productionPlanIds)) {
            for (PrdWorkOrder workOrder : safeList(workOrderMapper.selectList(
                Wrappers.lambdaQuery(PrdWorkOrder.class)
                    .in(PrdWorkOrder::getPlanId, batch)))) {
                if (workOrder.getWoId() != null) {
                    candidates.put(workOrder.getWoId(), workOrder);
                }
            }
        }
        for (List<Long> batch : batches(lineIds)) {
            for (PrdWorkOrder workOrder : safeList(workOrderMapper.selectList(
                Wrappers.lambdaQuery(PrdWorkOrder.class)
                    .in(PrdWorkOrder::getSourceLineId, batch)))) {
                if (workOrder.getWoId() != null) {
                    candidates.put(workOrder.getWoId(), workOrder);
                }
            }
        }
        for (PrdWorkOrder workOrder : safeList(workOrderMapper.selectList(
            Wrappers.lambdaQuery(PrdWorkOrder.class)
                .and(direct -> {
                    direct.eq(PrdWorkOrder::getSourceId, salesOrderId);
                    if (hasText(order.getSoNo())) {
                        direct.or().eq(PrdWorkOrder::getSourceNo, order.getSoNo());
                    }
                })))) {
            if (workOrder.getWoId() != null) {
                candidates.put(workOrder.getWoId(), workOrder);
            }
        }
        TreeMap<Long, PrdWorkOrder> scoped = new TreeMap<>();
        for (PrdWorkOrder workOrder : candidates.values()) {
            boolean productionChain = workOrder.getPlanId() != null
                && productionPlanIds.contains(workOrder.getPlanId());
            boolean legacyDirect = !isMaterialRequirementSource(workOrder)
                && (Objects.equals(salesOrderId, workOrder.getSourceId())
                    || (workOrder.getSourceLineId() != null
                        && lineIds.contains(workOrder.getSourceLineId()))
                    || (hasText(order.getSoNo())
                        && Objects.equals(order.getSoNo(), workOrder.getSourceNo())));
            if (!isTerminalWorkOrder(workOrder) && (productionChain || legacyDirect)) {
                scoped.put(workOrder.getWoId(), workOrder);
            }
        }
        List<PrdWorkOrder> workOrders = List.copyOf(scoped.values());
        LinkedHashMap<Long, Long> mapping = new LinkedHashMap<>();
        workOrders.forEach(workOrder -> mapping.put(workOrder.getWoId(), salesOrderId));
        return new DecisionScope(
            new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, salesOrderId, order.getSoNo()),
            List.of(order), lines, workOrders, null, List.of(), immutableLinkedMap(mapping));
    }

    private DecisionScope loadWorkOrderScope(Long workOrderId) {
        PrdWorkOrder workOrder = workOrderMapper.selectById(workOrderId);
        if (workOrder == null || !Objects.equals(workOrderId, workOrder.getWoId())) {
            throw new ServiceException("生产工单不存在或不可访问");
        }
        if (isTerminalWorkOrder(workOrder)) {
            throw new ServiceException("已完工或已关闭的生产工单不能发起交付决策");
        }
        List<PrdWorkOrder> workOrders = List.of(workOrder);
        ResolvedSalesContext sales = resolveSalesContext(workOrders);
        return new DecisionScope(
            new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, workOrderId, workOrder.getWoNo()),
            sales.orders(), sales.lines(), workOrders, null, List.of(), sales.workOrderSalesOrderIds());
    }

    private DecisionScope loadSchedulePlanScope(Long schedulePlanId) {
        PrdSchedulePlan plan = schedulePlanMapper.selectById(schedulePlanId);
        if (plan == null || !Objects.equals(schedulePlanId, plan.getPlanId())) {
            throw new ServiceException("排产方案不存在或不可访问");
        }
        List<PrdScheduleTask> tasks = safeList(scheduleTaskMapper.selectList(
            Wrappers.lambdaQuery(PrdScheduleTask.class)
                .eq(PrdScheduleTask::getPlanId, schedulePlanId))).stream()
            .filter(task -> Objects.equals(schedulePlanId, task.getPlanId()))
            .filter(task -> task.getWoId() != null && task.getWoId() > 0)
            .sorted(scheduleTaskComparator())
            .toList();
        List<Long> workOrderIds = tasks.stream()
            .map(PrdScheduleTask::getWoId)
            .distinct()
            .sorted()
            .toList();
        if (workOrderIds.isEmpty()) {
            throw new ServiceException("排产方案未包含生产工单");
        }
        List<PrdWorkOrder> workOrders = loadExactWorkOrders(workOrderIds).stream()
            .filter(workOrder -> !isTerminalWorkOrder(workOrder))
            .toList();
        ResolvedSalesContext sales = resolveSalesContext(workOrders);
        return new DecisionScope(
            new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, schedulePlanId, plan.getPlanNo()),
            sales.orders(), sales.lines(), workOrders, plan, tasks, sales.workOrderSalesOrderIds());
    }

    private DecisionScope loadShortageScope() {
        List<PrdWorkOrder> workOrders = safeList(workOrderMapper.selectList(
            Wrappers.lambdaQuery(PrdWorkOrder.class)
                .notIn(PrdWorkOrder::getWoStatus, TERMINAL_WORK_ORDER_STATUSES))).stream()
            .filter(workOrder -> !isTerminalWorkOrder(workOrder))
            .sorted(workOrderComparator())
            .toList();
        ResolvedSalesContext sales = resolveSalesContext(workOrders);
        return new DecisionScope(
            new DecisionBusinessRef(DecisionBusinessType.SHORTAGE_HORIZON, null, "SHORTAGE_HORIZON"),
            sales.orders(), sales.lines(), workOrders, null, List.of(), sales.workOrderSalesOrderIds());
    }

    private List<PrdWorkOrder> loadExactWorkOrders(Collection<Long> requestedIds) {
        LinkedHashSet<Long> ids = requestedIds.stream()
            .filter(Objects::nonNull)
            .filter(id -> id > 0)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            throw new ServiceException("指定工单 ID 不能为空");
        }
        TreeMap<Long, PrdWorkOrder> byId = new TreeMap<>();
        for (List<Long> batch : batches(ids)) {
            for (PrdWorkOrder row : safeList(workOrderMapper.selectList(
                Wrappers.lambdaQuery(PrdWorkOrder.class)
                    .in(PrdWorkOrder::getWoId, batch)))) {
                if (row.getWoId() == null || !ids.contains(row.getWoId())) {
                    continue;
                }
                if (byId.put(row.getWoId(), row) != null) {
                    throw new ServiceException("工单查询返回重复记录: " + row.getWoId());
                }
            }
        }
        List<Long> missing = ids.stream().filter(id -> !byId.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new ServiceException("工单不存在或不在当前范围: " + missing);
        }
        return List.copyOf(byId.values());
    }

    private ResolvedSalesContext resolveSalesContext(List<PrdWorkOrder> workOrders) {
        LinkedHashSet<Long> planIds = workOrders.stream()
            .map(PrdWorkOrder::getPlanId)
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        TreeMap<Long, PrdProductionPlan> resolvedPlanById = new TreeMap<>();
        for (List<Long> batch : batches(planIds)) {
            for (PrdProductionPlan plan : safeList(productionPlanMapper.selectBatchIds(batch))) {
                if (plan.getPlanId() != null && planIds.contains(plan.getPlanId())) {
                    resolvedPlanById.put(plan.getPlanId(), plan);
                }
            }
        }
        List<PrdProductionPlan> productionPlans = List.copyOf(resolvedPlanById.values());
        Map<Long, PrdProductionPlan> planById = productionPlans.stream()
            .collect(Collectors.toMap(PrdProductionPlan::getPlanId, plan -> plan, (left, right) -> left));
        LinkedHashSet<Long> orderLineIds = productionPlans.stream()
            .map(PrdProductionPlan::getOrderLineId)
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        TreeMap<Long, SoSalesOrderLine> resolvedLineById = new TreeMap<>();
        for (List<Long> batch : batches(orderLineIds)) {
            for (SoSalesOrderLine line : safeList(salesOrderLineMapper.selectBatchIds(batch))) {
                if (line.getLineId() != null && orderLineIds.contains(line.getLineId())) {
                    resolvedLineById.put(line.getLineId(), line);
                }
            }
        }
        List<SoSalesOrderLine> lines = List.copyOf(resolvedLineById.values());
        Map<Long, SoSalesOrderLine> lineById = lines.stream()
            .collect(Collectors.toMap(SoSalesOrderLine::getLineId, line -> line, (left, right) -> left));

        LinkedHashMap<Long, Long> mapping = new LinkedHashMap<>();
        for (PrdWorkOrder workOrder : workOrders.stream().sorted(workOrderComparator()).toList()) {
            PrdProductionPlan plan = planById.get(workOrder.getPlanId());
            SoSalesOrderLine line = plan == null ? null : lineById.get(plan.getOrderLineId());
            if (line != null && line.getSoId() != null && workOrder.getWoId() != null) {
                mapping.put(workOrder.getWoId(), line.getSoId());
            }
        }

        LinkedHashSet<Long> orderIds = new LinkedHashSet<>(mapping.values());
        LinkedHashSet<Long> legacySourceIds = workOrders.stream()
            .filter(workOrder -> !isMaterialRequirementSource(workOrder))
            .filter(workOrder -> !mapping.containsKey(workOrder.getWoId()))
            .map(PrdWorkOrder::getSourceId)
            .filter(Objects::nonNull)
            .filter(id -> id > 0)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        orderIds.addAll(legacySourceIds);
        TreeMap<Long, SoSalesOrderHeader> orderById = new TreeMap<>();
        if (orderIds.size() == 1) {
            Long id = orderIds.iterator().next();
            SoSalesOrderHeader order = salesOrderHeaderMapper.selectById(id);
            if (order != null && Objects.equals(id, order.getSoId())) {
                orderById.put(id, order);
            }
        } else if (!orderIds.isEmpty()) {
            for (List<Long> batch : batches(orderIds)) {
                for (SoSalesOrderHeader order : safeList(salesOrderHeaderMapper.selectBatchIds(batch))) {
                    if (order.getSoId() != null && orderIds.contains(order.getSoId())) {
                        orderById.put(order.getSoId(), order);
                    }
                }
            }
        }
        LinkedHashSet<String> sourceNumbers = workOrders.stream()
            .filter(workOrder -> !isMaterialRequirementSource(workOrder))
            .filter(workOrder -> !mapping.containsKey(workOrder.getWoId()))
            .map(PrdWorkOrder::getSourceNo)
            .filter(DeliveryContextAssembler::hasText)
            .map(String::trim)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!sourceNumbers.isEmpty()) {
            for (SoSalesOrderHeader order : safeList(salesOrderHeaderMapper.selectList(
                Wrappers.lambdaQuery(SoSalesOrderHeader.class)
                    .in(SoSalesOrderHeader::getSoNo, sourceNumbers)))) {
                if (order.getSoId() != null && sourceNumbers.contains(order.getSoNo())) {
                    orderById.putIfAbsent(order.getSoId(), order);
                }
            }
        }
        Map<String, SoSalesOrderHeader> orderByNumber = orderById.values().stream()
            .filter(order -> hasText(order.getSoNo()))
            .collect(Collectors.toMap(SoSalesOrderHeader::getSoNo, order -> order, (left, right) -> left));
        for (PrdWorkOrder workOrder : workOrders.stream().sorted(workOrderComparator()).toList()) {
            if (workOrder.getWoId() == null || mapping.containsKey(workOrder.getWoId())) {
                continue;
            }
            SoSalesOrderHeader order = isMaterialRequirementSource(workOrder)
                || workOrder.getSourceId() == null ? null : orderById.get(workOrder.getSourceId());
            if (order == null && !isMaterialRequirementSource(workOrder)
                && hasText(workOrder.getSourceNo())) {
                order = orderByNumber.get(workOrder.getSourceNo());
            }
            if (order != null) {
                mapping.put(workOrder.getWoId(), order.getSoId());
            }
        }
        mapping.entrySet().removeIf(entry -> !orderById.containsKey(entry.getValue()));
        return new ResolvedSalesContext(
            List.copyOf(orderById.values()), lines, immutableLinkedMap(mapping));
    }

    private boolean isMaterialRequirementSource(PrdWorkOrder workOrder) {
        return workOrder != null && "MATERIAL_REQUIREMENT".equalsIgnoreCase(workOrder.getSourceType());
    }

    private LoadedContext loadContext(DecisionScope scope, int horizonDays, LocalDate businessDate, String tenantId) {
        LocalDate horizonEnd = businessDate.plusDays(horizonDays - 1L);
        ScheduleInput apsInput = assembleScheduleInput(scope.workOrders(), horizonDays, businessDate);
        SourceFacts sourceFacts = loadSourceFacts(scope, apsInput, businessDate, horizonEnd);
        List<String> warnings = new ArrayList<>();
        if (scope.workOrders().isEmpty()) {
            warnings.add("NO_WORK_ORDER: 当前决策范围没有生产工单");
        } else if (safeList(apsInput.getWorkOrders()).isEmpty()) {
            warnings.add("NO_SCHEDULABLE_WORK_ORDER: 当前决策范围没有可排产工单");
        }

        List<RequirementLink> requirementRows = loadRequirementRows(
            scope, businessDate, horizonEnd, warnings);
        LinkedHashSet<Long> materialIds = collectRelevantMaterialIds(scope, requirementRows);
        StockBundle stocks = loadStocks(materialIds);
        var unitIds = stocks.materials().stream().map(BdMaterial::getBaseUomId)
            .filter(id -> id != null && id > 0).distinct().sorted().toList();
        var evidenceUnits = unitIds.isEmpty() ? List.<com.nexus.erp.bd.domain.BdUnitOfMeasure>of()
            : safeList(evidenceUnitMapper.selectBatchIds(unitIds));
        PurchaseBundle purchases = loadPurchases(materialIds, horizonEnd, warnings);
        List<SupplierMetricFact> supplierMetrics = loadSupplierMetrics(purchases.vendorIds(), warnings);
        ScheduleSelection schedule = loadCurrentSchedule(scope, warnings);

        Map<Long, List<RoutingStepFact>> routingByMaterial = buildRoutingFacts(scope, apsInput, sourceFacts);
        Map<LocalDate, Map<String, BigDecimal>> capacityByDate = sortedCapacity(apsInput.getCapacityByDate());
        List<LocalDate> holidays = safeCollection(apsInput.getHolidays()).stream().sorted().toList();
        Map<String, Object> currentSchedule = buildCurrentSchedule(scope, schedule, sourceFacts.capacity().calendars());

        return new LoadedContext(
            apsInput,
            requirementRows,
            stocks.rows(),
            stocks.materials(),
            List.copyOf(evidenceUnits),
            purchases.orders(),
            purchases.lines(),
            schedule,
            sourceFacts,
            routingByMaterial,
            capacityByDate,
            holidays,
            currentSchedule,
            supplierMetrics,
            List.copyOf(warnings), tenantId, new Date());
    }

    private SnapshotProjection projectSnapshot(DecisionScope scope,
                                               LoadedContext context,
                                               int horizonDays,
                                               LocalDate businessDate) {
        LocalDate horizonEnd = businessDate.plusDays(horizonDays - 1L);
        List<RequirementLink> requirementRows = context.requirementRows();
        if (scope.normalizedRef().type() == DecisionBusinessType.SHORTAGE_HORIZON) {
            requirementRows = requirementRows.stream()
                .filter(row -> {
                    LocalDate requiredDate = toLocalDate(row.detail().getRequiredDate());
                    return requiredDate != null && !requiredDate.isBefore(businessDate)
                        && !requiredDate.isAfter(horizonEnd);
                })
                .toList();
        }
        LinkedHashSet<Long> materialIds = collectRelevantMaterialIds(scope, requirementRows);
        StockBundle stocks = new StockBundle(context.stockRows(), context.materials());
        PurchaseBundle purchases = new PurchaseBundle(
            context.purchaseOrders(), context.purchaseLines(), List.of());
        List<String> warnings = new ArrayList<>(context.warnings());
        List<ArrivalFact> arrivals = buildArrivalFacts(purchases, materialIds, horizonEnd, context.materials(), context.evidenceUnits(), context.tenantId(), warnings, context.observedAt());

        LinkedHashMap<LocalDate, Map<String, BigDecimal>> capacity = new LinkedHashMap<>();
        context.capacityByDate().forEach((date, resources) -> {
            if (!date.isBefore(businessDate) && !date.isAfter(horizonEnd)) {
                capacity.put(date, resources);
            }
        });
        List<LocalDate> holidays = context.holidays().stream()
            .filter(date -> !date.isBefore(businessDate) && !date.isAfter(horizonEnd))
            .toList();
        List<StockFact> stockFacts = buildStockFacts(materialIds, stocks, warnings);
        var requirementFacts = buildRequirementFacts(requirementRows, context.materials(), context.evidenceUnits(), warnings, context.tenantId(), context.observedAt());
        List<String> stableWarnings = warnings.stream()
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();
        return new SnapshotProjection(
            context.routingByMaterial(),
            requirementFacts,
            stockFacts,
            arrivals,
            Collections.unmodifiableMap(capacity),
            holidays,
            context.currentSchedule(),
            context.supplierMetrics(),
            stableWarnings);
    }

    private ScheduleInput assembleScheduleInput(List<PrdWorkOrder> workOrders, int horizonDays,
                                                LocalDate businessDate) {
        List<Long> workOrderIds = workOrders.stream()
            .filter(this::isSchedulableWorkOrder)
            .map(PrdWorkOrder::getWoId)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();
        if (workOrderIds.isEmpty()) {
            ScheduleInput empty = new ScheduleInput();
            empty.setWorkOrders(List.of());
            empty.setRoutingProvider(materialId -> List.of());
            empty.setCapacityByDate(new LinkedHashMap<>());
            empty.setHolidays(new LinkedHashSet<>());
            empty.setStartDate(businessDate);
            empty.setHorizonDays(horizonDays);
            empty.setStrategy(Strategy.EDD);
            return empty;
        }
        PrdSchedulePlanBo bo = new PrdSchedulePlanBo();
        bo.setStartDate(java.sql.Date.valueOf(businessDate));
        bo.setHorizonDays(horizonDays);
        bo.setStrategy("EDD");
        return scheduleInputAssembler.assemble(bo, workOrderIds);
    }

    private List<RequirementLink> loadRequirementRows(DecisionScope scope,
                                                       LocalDate startDate,
                                                       LocalDate endDate,
                                                       List<String> warnings) {
        LinkedHashSet<Long> planIds = scope.workOrders().stream()
            .map(PrdWorkOrder::getPlanId)
            .filter(Objects::nonNull)
            .filter(id -> id > 0)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (planIds.isEmpty()) {
            return List.of();
        }
        TreeMap<Long, PrdMaterialRequirement> requirementRows = new TreeMap<>();
        for (List<Long> batch : batches(planIds)) {
            for (PrdMaterialRequirement requirement : safeList(requirementMapper.selectList(
                Wrappers.lambdaQuery(PrdMaterialRequirement.class)
                    .in(PrdMaterialRequirement::getPlanId, batch)))) {
                if (requirement.getRequirementId() != null && planIds.contains(requirement.getPlanId())) {
                    requirementRows.put(requirement.getRequirementId(), requirement);
                }
            }
        }
        List<PrdMaterialRequirement> requirements = List.copyOf(requirementRows.values());
        LinkedHashSet<Long> requirementIds = requirements.stream()
            .map(PrdMaterialRequirement::getRequirementId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requirementIds.isEmpty()) {
            return List.of();
        }
        Map<Long, PrdMaterialRequirement> requirementById = requirements.stream()
            .collect(Collectors.toMap(PrdMaterialRequirement::getRequirementId,
                requirement -> requirement, (left, right) -> left));
        Map<Long, List<PrdWorkOrder>> workOrdersByPlan = scope.workOrders().stream()
            .filter(workOrder -> workOrder.getPlanId() != null)
            .collect(Collectors.groupingBy(PrdWorkOrder::getPlanId));

        TreeMap<Long, PrdMaterialRequirementDetail> details = new TreeMap<>();
        for (List<Long> batch : batches(requirementIds)) {
            for (PrdMaterialRequirementDetail detail : safeList(requirementDetailMapper.selectList(
                Wrappers.lambdaQuery(PrdMaterialRequirementDetail.class)
                    .in(PrdMaterialRequirementDetail::getRequirementId, batch)
                    .isNotNull(PrdMaterialRequirementDetail::getRequiredDate)
                    .ge(PrdMaterialRequirementDetail::getRequiredDate, startOfDay(startDate))
                    .lt(PrdMaterialRequirementDetail::getRequiredDate, startOfDay(endDate.plusDays(1)))))) {
                if (detail.getDetailId() != null && requirementIds.contains(detail.getRequirementId())
                    && within(detail.getRequiredDate(), startDate, endDate)) {
                    if (details.putIfAbsent(detail.getDetailId(), detail) != null) {
                        throw new ServiceException("MRP 需求明细 ID 重复，来源不可核验");
                    }
                }
            }
        }
        List<RequirementLink> links = new ArrayList<>();
        for (PrdMaterialRequirementDetail detail : details.values()) {
            PrdMaterialRequirement requirement = requirementById.get(detail.getRequirementId());
            if (requirement == null) {
                continue;
            }
            List<PrdWorkOrder> directCandidates = scope.workOrders().stream()
                .filter(this::isMaterialRequirementSource)
                .filter(workOrder -> Objects.equals(requirement.getRequirementId(), workOrder.getSourceId()))
                .sorted(workOrderComparator())
                .toList();
            List<PrdWorkOrder> planCandidates = safeList(workOrdersByPlan.get(requirement.getPlanId())).stream()
                .sorted(workOrderComparator())
                .toList();
            PrdWorkOrder linkedWorkOrder = directCandidates.size() == 1
                ? directCandidates.get(0)
                : directCandidates.isEmpty() && planCandidates.size() == 1 ? planCandidates.get(0) : null;
            BigDecimal net = nonNegative(subtract(detail.getRequiredQuantity(), detail.getIssuedQuantity()));
            if (net.signum() > 0 && linkedWorkOrder == null) {
                warnings.add("物料需求明细 " + detail.getDetailId() + " 无法唯一关联生产工单");
            }
            links.add(new RequirementLink(requirement, detail, linkedWorkOrder, net));
        }
        links.sort(requirementLinkComparator());
        return List.copyOf(links);
    }

    private LinkedHashSet<Long> collectRelevantMaterialIds(DecisionScope scope,
                                                            List<RequirementLink> requirements) {
        LinkedHashSet<Long> materialIds = new LinkedHashSet<>();
        requirements.stream()
            .filter(row -> row.netQuantity().signum() > 0)
            .map(row -> row.detail().getMaterialId())
            .filter(Objects::nonNull)
            .filter(id -> id > 0)
            .sorted()
            .forEach(materialIds::add);
        if (scope.normalizedRef().type() != DecisionBusinessType.SHORTAGE_HORIZON) {
            scope.salesOrderLines().stream()
                .map(SoSalesOrderLine::getMaterialId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .sorted()
                .forEach(materialIds::add);
            scope.workOrders().stream()
                .map(PrdWorkOrder::getMaterialId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .sorted()
                .forEach(materialIds::add);
        }
        return materialIds.stream().sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private StockBundle loadStocks(LinkedHashSet<Long> materialIds) {
        if (materialIds.isEmpty()) {
            return new StockBundle(List.of(), List.of());
        }
        TreeMap<Long, WmMaterialStock> stockRows = new TreeMap<>();
        TreeMap<Long, BdMaterial> materialsById = new TreeMap<>();
        for (List<Long> batch : batches(materialIds)) {
            for (WmMaterialStock row : safeList(stockMapper.selectList(
                Wrappers.lambdaQuery(WmMaterialStock.class)
                    .in(WmMaterialStock::getMaterialId, batch)))) {
                if (row.getStockId() != null && row.getMaterialId() != null
                    && materialIds.contains(row.getMaterialId())) {
                    stockRows.put(row.getStockId(), row);
                }
            }
            for (BdMaterial material : safeList(materialMapper.selectBatchIds(batch))) {
                if (material.getMaterialId() != null && materialIds.contains(material.getMaterialId())) {
                    materialsById.put(material.getMaterialId(), material);
                }
            }
        }
        List<WmMaterialStock> rows = stockRows.values().stream().sorted(stockRowComparator()).toList();
        List<BdMaterial> materials = materialsById.values().stream().sorted(materialComparator()).toList();
        return new StockBundle(rows, materials);
    }

    private PurchaseBundle loadPurchases(LinkedHashSet<Long> materialIds,
                                          LocalDate horizonEnd,
                                          List<String> warnings) {
        if (materialIds.isEmpty()) {
            return new PurchaseBundle(List.of(), List.of(), List.of());
        }
        TreeMap<Long, PuPurchaseOrderLine> candidateById = new TreeMap<>();
        for (List<Long> batch : batches(materialIds)) {
            for (PuPurchaseOrderLine line : safeList(purchaseOrderLineMapper.selectList(
                Wrappers.lambdaQuery(PuPurchaseOrderLine.class)
                    .in(PuPurchaseOrderLine::getMaterialId, batch)))) {
                if (line.getLineId() != null && line.getMaterialId() != null
                    && materialIds.contains(line.getMaterialId())
                    && remaining(line.getOrderQuantity(), line.getReceivedQuantity()).signum() > 0) {
                    candidateById.put(line.getLineId(), line);
                }
            }
        }
        List<PuPurchaseOrderLine> candidateLines = candidateById.values().stream()
            .sorted(purchaseLineComparator())
            .toList();
        LinkedHashSet<Long> orderIds = candidateLines.stream()
            .map(PuPurchaseOrderLine::getOrderId)
            .filter(Objects::nonNull)
            .filter(id -> id > 0)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (orderIds.isEmpty()) {
            return new PurchaseBundle(List.of(), List.of(), List.of());
        }
        TreeMap<Long, PuPurchaseOrder> orderById = new TreeMap<>();
        for (List<Long> batch : batches(orderIds)) {
            for (PuPurchaseOrder order : safeList(purchaseOrderMapper.selectList(
                Wrappers.lambdaQuery(PuPurchaseOrder.class)
                    .in(PuPurchaseOrder::getOrderId, batch)
                    .in(PuPurchaseOrder::getStatus, OPEN_PURCHASE_ORDER_STATUSES)
                    .and(date -> date.isNull(PuPurchaseOrder::getExpectedArrivalDate)
                        .or().lt(PuPurchaseOrder::getExpectedArrivalDate,
                            startOfDay(horizonEnd.plusDays(1))))))) {
                LocalDate expectedDate = toLocalDate(order.getExpectedArrivalDate());
                if (order.getOrderId() != null && orderIds.contains(order.getOrderId())
                    && OPEN_PURCHASE_ORDER_STATUSES.contains(order.getStatus())
                    && (expectedDate == null || !expectedDate.isAfter(horizonEnd))) {
                    orderById.put(order.getOrderId(), order);
                }
            }
        }
        Set<Long> validOrderIds = orderById.keySet();
        List<PuPurchaseOrderLine> lines = candidateLines.stream()
            .filter(line -> validOrderIds.contains(line.getOrderId()))
            .toList();
        Set<Long> orderIdsWithLines = lines.stream()
            .map(PuPurchaseOrderLine::getOrderId)
            .collect(Collectors.toSet());
        List<PuPurchaseOrder> relatedOrders = orderById.values().stream()
            .filter(order -> orderIdsWithLines.contains(order.getOrderId()))
            .sorted(purchaseOrderComparator())
            .toList();
        LinkedHashSet<Long> vendors = new LinkedHashSet<>();
        for (PuPurchaseOrderLine line : lines) {
            PuPurchaseOrder order = orderById.get(line.getOrderId());
            if (order == null) {
                continue;
            }
            if (order.getVendorId() != null && order.getVendorId() > 0) {
                vendors.add(order.getVendorId());
            }
            if (order.getExpectedArrivalDate() == null) {
                warnings.add("采购订单 " + order.getOrderId() + " 缺少预计到货日期，未计入可用到货");
            }
        }
        return new PurchaseBundle(relatedOrders, lines, vendors.stream().sorted().toList());
    }

    private List<SupplierMetricFact> loadSupplierMetrics(List<Long> vendorIds, List<String> warnings) {
        if (vendorIds.isEmpty()) {
            return List.of();
        }
        List<PuVendorEvaluation> evaluations = safeList(vendorEvaluationMapper.selectList(
            Wrappers.lambdaQuery(PuVendorEvaluation.class)
                .in(PuVendorEvaluation::getVendorId, vendorIds))).stream()
            .filter(evaluation -> evaluation.getVendorId() != null && vendorIds.contains(evaluation.getVendorId()))
            .sorted(vendorEvaluationComparator())
            .toList();
        Map<Long, PuVendorEvaluation> latestByVendor = new TreeMap<>();
        for (PuVendorEvaluation evaluation : evaluations) {
            PuVendorEvaluation current = latestByVendor.get(evaluation.getVendorId());
            if (current == null || vendorEvaluationComparator().compare(current, evaluation) < 0) {
                latestByVendor.put(evaluation.getVendorId(), evaluation);
            }
        }
        List<SupplierMetricFact> metrics = new ArrayList<>();
        for (Long vendorId : vendorIds.stream().sorted().toList()) {
            PuVendorEvaluation evaluation = latestByVendor.get(vendorId);
            if (evaluation == null) {
                warnings.add("供应商 " + vendorId + " 缺少评价指标");
                continue;
            }
            Date observed = evaluation.getUpdateTime() != null
                ? evaluation.getUpdateTime() : evaluation.getCreateTime();
            metrics.add(new SupplierMetricFact(
                vendorId,
                evaluation.getDeliveryRate(),
                evaluation.getTotalOrders() == null ? 0 : evaluation.getTotalOrders(),
                toLocalDateTime(observed)));
        }
        return List.copyOf(metrics);
    }

    private static boolean verifiablePublishedSchedule(PrdSchedulePlan plan) {
        return plan != null && "PUBLISHED".equals(plan.getStatus())
            && Integer.valueOf(2).equals(plan.getCapacityCalcVersion());
    }
    private ScheduleSelection loadCurrentSchedule(DecisionScope scope, List<String> warnings) {
        if (scope.specifiedSchedulePlan() != null) {
            if (!verifiablePublishedSchedule(scope.specifiedSchedulePlan())) {
                warnings.add("排程尚未按可信口径发布，不能视为已保存的产能基线");
                return new ScheduleSelection(null, List.of(), List.of());
            }
            List<PrdScheduleTask> fingerprintTasks = scope.specifiedScheduleTasks().stream()
                .filter(task -> Objects.equals(scope.specifiedSchedulePlan().getPlanId(), task.getPlanId()))
                .sorted(scheduleTaskComparator())
                .toList();
            List<PrdScheduleTask> displayTasks = fingerprintTasks.stream()
                .filter(task -> scope.workOrderIds().contains(task.getWoId()))
                .toList();
            return new ScheduleSelection(
                scope.specifiedSchedulePlan(), displayTasks, fingerprintTasks);
        }
        if (scope.workOrders().isEmpty()) {
            return new ScheduleSelection(null, List.of(), List.of());
        }
        List<Long> workOrderIds = scope.workOrderIds();
        List<PrdScheduleTask> tasks = safeList(scheduleTaskMapper.selectList(
            Wrappers.lambdaQuery(PrdScheduleTask.class)
                .in(PrdScheduleTask::getWoId, workOrderIds))).stream()
            .filter(task -> task.getWoId() != null && workOrderIds.contains(task.getWoId()))
            .filter(task -> task.getPlanId() != null && task.getPlanId() > 0)
            .sorted(scheduleTaskComparator())
            .toList();
        LinkedHashSet<Long> planIds = tasks.stream()
            .map(PrdScheduleTask::getPlanId)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (planIds.isEmpty()) {
            warnings.add("当前决策范围尚无排产方案");
            return new ScheduleSelection(null, List.of(), List.of());
        }
        /**
         * 自动选择的基准计划必须涵盖范围内的每个工单。
         * 不能用更新但仅含一个工单的计划，悄然替换完整的销售订单基准计划。
         */
        List<PrdSchedulePlan> plans = safeList(schedulePlanMapper.selectBatchIds(planIds)).stream()
            .filter(plan -> plan.getPlanId() != null && planIds.contains(plan.getPlanId()))
            .filter(DeliveryContextAssembler::verifiablePublishedSchedule)
            .filter(plan -> tasks.stream()
                .filter(task -> Objects.equals(plan.getPlanId(), task.getPlanId()))
                .map(PrdScheduleTask::getWoId).collect(Collectors.toSet())
                .containsAll(workOrderIds))
            .toList();
        PrdSchedulePlan latest = plans.stream().max(schedulePlanLatestComparator()).orElse(null);
        if (latest == null) {
            warnings.add("当前决策范围的排产任务缺少对应排产方案");
            return new ScheduleSelection(null, List.of(), List.of());
        }
        List<PrdScheduleTask> selectedTasks = tasks.stream()
            .filter(task -> Objects.equals(latest.getPlanId(), task.getPlanId()))
            .sorted(scheduleTaskComparator())
            .toList();
        return new ScheduleSelection(latest, selectedTasks, selectedTasks);
    }

    private SourceFacts loadSourceFacts(DecisionScope scope,
                                        ScheduleInput apsInput,
                                        LocalDate startDate,
                                        LocalDate endDate) {
        if (scope.workOrders().isEmpty()) {
            return SourceFacts.empty();
        }
        RoutingSourceBundle routingSources = loadRoutingSources(scope, apsInput);
        CapacitySourceBundle capacitySources = loadCapacitySources(
            scope, routingSources, startDate, endDate);
        return new SourceFacts(routingSources, capacitySources);
    }

    private RoutingSourceBundle loadRoutingSources(DecisionScope scope, ScheduleInput apsInput) {
        LinkedHashSet<Long> materialIds = new LinkedHashSet<>();
        scope.workOrders().stream()
            .map(PrdWorkOrder::getMaterialId)
            .filter(Objects::nonNull)
            .sorted()
            .forEach(materialIds::add);
        safeList(apsInput.getWorkOrders()).stream()
            .map(WoInfo::getMaterialId)
            .filter(Objects::nonNull)
            .sorted()
            .forEach(materialIds::add);
        if (materialIds.isEmpty()) {
            return new RoutingSourceBundle(List.of());
        }
        List<BdRouting> candidates = safeList(routingMapper.selectList(
            Wrappers.lambdaQuery(BdRouting.class)
                .eq(BdRouting::getIsCurrent, 1L)
                .eq(BdRouting::getStatus, 1L)
                .and(query -> query.in(BdRouting::getMaterialId, materialIds)
                    .or().isNull(BdRouting::getMaterialId)))).stream()
            .filter(routing -> Objects.equals(1L, routing.getIsCurrent()))
            .filter(routing -> Objects.equals(1L, routing.getStatus()))
            .filter(routing -> routing.getMaterialId() == null || materialIds.contains(routing.getMaterialId()))
            .sorted(routingComparator())
            .toList();
        List<RoutingAssignment> selected = new ArrayList<>();
        for (Long materialId : materialIds) {
            BdRouting routing = candidates.stream()
                .filter(candidate -> Objects.equals(materialId, candidate.getMaterialId()))
                .max(routingComparator())
                .orElseGet(() -> candidates.stream()
                    .filter(candidate -> candidate.getMaterialId() == null)
                    .max(routingComparator())
                    .orElse(null));
            if (routing != null && routing.getRoutingId() != null) {
                selected.add(new RoutingAssignment(materialId, routing, List.of()));
            }
        }
        LinkedHashSet<Long> routingIds = selected.stream()
            .map(assignment -> assignment.routing().getRoutingId())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, List<BdRoutingStep>> stepsByRouting = new TreeMap<>();
        if (!routingIds.isEmpty()) {
            for (BdRoutingStep step : safeList(routingStepMapper.selectList(
                Wrappers.lambdaQuery(BdRoutingStep.class)
                    .in(BdRoutingStep::getRoutingId, routingIds)))) {
                if (step.getRoutingId() != null && routingIds.contains(step.getRoutingId())) {
                    stepsByRouting.computeIfAbsent(step.getRoutingId(), ignored -> new ArrayList<>()).add(step);
                }
            }
        }
        List<RoutingAssignment> assignments = selected.stream()
            .map(assignment -> new RoutingAssignment(
                assignment.materialId(),
                assignment.routing(),
                safeList(stepsByRouting.get(assignment.routing().getRoutingId())).stream()
                    .sorted(routingStepSourceComparator())
                    .toList()))
            .sorted(Comparator.comparing(RoutingAssignment::materialId))
            .toList();
        return new RoutingSourceBundle(assignments);
    }

    private CapacitySourceBundle loadCapacitySources(DecisionScope scope,
                                                      RoutingSourceBundle routingSources,
                                                      LocalDate startDate,
                                                      LocalDate endDate) {
        LinkedHashSet<Long> workshopIds = scope.workOrders().stream()
            .map(PrdWorkOrder::getWorkshopId)
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<Long> workstationIds = scope.workOrders().stream()
            .map(PrdWorkOrder::getWorkstationId)
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        routingSources.assignments().stream()
            .flatMap(assignment -> assignment.steps().stream())
            .map(BdRoutingStep::getWorkstationId)
            .filter(Objects::nonNull)
            .sorted()
            .forEach(workstationIds::add);
        LinkedHashSet<Long> productionLineIds = scope.workOrders().stream()
            .map(PrdWorkOrder::getProductionLineId)
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));

        List<BdWorkstation> workstations = loadSourceWorkstations(workstationIds);
        workstations.stream()
            .map(BdWorkstation::getWorkshopId)
            .filter(Objects::nonNull)
            .forEach(workshopIds::add);

        LinkedHashSet<Long> capacityObjectIds = new LinkedHashSet<>();
        capacityObjectIds.addAll(workshopIds);
        capacityObjectIds.addAll(workstationIds);
        capacityObjectIds.addAll(productionLineIds);
        List<PrdCapacityCalendar> calendars = capacityObjectIds.isEmpty() ? List.of()
            : safeList(capacityCalendarMapper.selectList(
                Wrappers.lambdaQuery(PrdCapacityCalendar.class)
                    .eq(PrdCapacityCalendar::getStatus, "ACTIVE")
                    .ge(PrdCapacityCalendar::getCalendarDate, startOfDay(startDate))
                    .lt(PrdCapacityCalendar::getCalendarDate, startOfDay(endDate.plusDays(1)))
                    .in(PrdCapacityCalendar::getObjectId, capacityObjectIds))).stream()
                .filter(calendar -> isRelatedCapacity(
                    calendar, workshopIds, workstationIds, productionLineIds, startDate, endDate))
                .sorted(capacityCalendarComparator())
                .toList();

        List<SchHoliday> holidays = safeList(holidayMapper.selectList(
            Wrappers.lambdaQuery(SchHoliday.class)
                .ge(SchHoliday::getHolidayDate, startOfDay(startDate))
                .lt(SchHoliday::getHolidayDate, startOfDay(endDate.plusDays(1))))).stream()
            .filter(holiday -> within(holiday.getHolidayDate(), startDate, endDate))
            .sorted(holidayComparator())
            .toList();

        List<SchTeam> teams = workshopIds.isEmpty() ? List.of()
            : safeList(teamMapper.selectList(
                Wrappers.lambdaQuery(SchTeam.class)
                    .in(SchTeam::getWorkshopId, workshopIds))).stream()
                .filter(team -> team.getTeamId() != null)
                .filter(team -> team.getWorkshopId() != null && workshopIds.contains(team.getWorkshopId()))
                .sorted(teamComparator())
                .toList();
        LinkedHashSet<Long> relatedTeamIds = teams.stream()
            .map(SchTeam::getTeamId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<SchEmployeeSchedule> schedules = relatedTeamIds.isEmpty() ? List.of()
            : safeList(employeeScheduleMapper.selectList(
                Wrappers.lambdaQuery(SchEmployeeSchedule.class)
                    .in(SchEmployeeSchedule::getTeamId, relatedTeamIds)
                    .ge(SchEmployeeSchedule::getScheduledDate, startOfDay(startDate))
                    .lt(SchEmployeeSchedule::getScheduledDate, startOfDay(endDate.plusDays(1))))).stream()
                .filter(schedule -> schedule.getScheduleId() != null)
                .filter(schedule -> schedule.getTeamId() != null
                    && relatedTeamIds.contains(schedule.getTeamId()))
                .filter(schedule -> within(schedule.getScheduledDate(), startDate, endDate))
                .sorted(employeeScheduleComparator())
                .toList();

        LinkedHashSet<Long> shiftIds = schedules.stream()
            .flatMap(schedule -> Stream.of(schedule.getShiftId(), schedule.getActualShiftId()))
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        calendars.stream().map(PrdCapacityCalendar::getShiftId)
            .filter(Objects::nonNull).sorted().forEach(shiftIds::add);
        List<SchShift> shifts = shiftIds.isEmpty() ? List.of()
            : safeList(shiftMapper.selectBatchIds(shiftIds)).stream()
                .filter(shift -> shift.getShiftId() != null && shiftIds.contains(shift.getShiftId()))
                .sorted(shiftComparator())
                .toList();

        LinkedHashSet<Long> workerIds = schedules.stream()
            .map(SchEmployeeSchedule::getWorkerId)
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        List<SchLeaveApply> leaves = workerIds.isEmpty() ? List.of()
            : safeList(leaveApplyMapper.selectList(
                Wrappers.lambdaQuery(SchLeaveApply.class)
                    .eq(SchLeaveApply::getApplyStatus, "approved")
                    .in(SchLeaveApply::getWorkerId, workerIds)
                    .lt(SchLeaveApply::getStartDate, startOfDay(endDate.plusDays(1)))
                    .ge(SchLeaveApply::getEndDate, startOfDay(startDate)))).stream()
                .filter(leave -> workerIds.contains(leave.getWorkerId()))
                .filter(leave -> "approved".equalsIgnoreCase(leave.getApplyStatus()))
                .filter(leave -> overlaps(leave.getStartDate(), leave.getEndDate(), startDate, endDate))
                .sorted(leaveComparator())
                .toList();
        return new CapacitySourceBundle(
            calendars, holidays, schedules, shifts, teams, leaves, workstations);
    }

    private List<BdWorkstation> loadSourceWorkstations(Set<Long> workstationIds) {
        if (workstationIds.isEmpty()) {
            return List.of();
        }
        return safeList(workstationMapper.selectList(
            Wrappers.lambdaQuery(BdWorkstation.class)
                .in(BdWorkstation::getWorkstationId, workstationIds))).stream()
            .filter(workstation -> workstation.getWorkstationId() != null
                && workstationIds.contains(workstation.getWorkstationId()))
            .sorted(workstationComparator())
            .toList();
    }

    private boolean isRelatedCapacity(PrdCapacityCalendar calendar,
                                      Set<Long> workshopIds,
                                      Set<Long> workstationIds,
                                      Set<Long> productionLineIds,
                                      LocalDate startDate,
                                      LocalDate endDate) {
        if (calendar.getObjectId() == null || !within(calendar.getCalendarDate(), startDate, endDate)
            || !"ACTIVE".equalsIgnoreCase(calendar.getStatus())) {
            return false;
        }
        String objectType = calendar.getObjectType() == null
            ? "" : calendar.getObjectType().toUpperCase(Locale.ROOT);
        return switch (objectType) {
            case "WORKSHOP" -> workshopIds.contains(calendar.getObjectId());
            case "WORKSTATION" -> workstationIds.contains(calendar.getObjectId());
            case "LINE" -> productionLineIds.contains(calendar.getObjectId());
            default -> false;
        };
    }

    private static boolean within(Date date, LocalDate startDate, LocalDate endDate) {
        LocalDate value = toLocalDate(date);
        return value != null && !value.isBefore(startDate) && !value.isAfter(endDate);
    }

    private static boolean overlaps(Date from, Date to, LocalDate startDate, LocalDate endDate) {
        LocalDate first = toLocalDate(from);
        LocalDate last = toLocalDate(to);
        return first != null && last != null && !first.isAfter(endDate) && !last.isBefore(startDate);
    }

    private Map<Long, List<RoutingStepFact>> buildRoutingFacts(DecisionScope scope,
                                                                        ScheduleInput input,
                                                                        SourceFacts sourceFacts) {
        LinkedHashMap<Long, List<RoutingStepFact>> routes = new LinkedHashMap<>();
        List<Long> materialIds = safeList(input.getWorkOrders()).stream()
            .map(WoInfo::getMaterialId)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();
        Map<Long, RoutingAssignment> assignments = sourceFacts.routing().assignments().stream()
            .collect(Collectors.toMap(RoutingAssignment::materialId, assignment -> assignment));
        Map<Long, BdWorkstation> workstationById = sourceFacts.capacity().workstations().stream()
            .filter(workstation -> workstation.getWorkstationId() != null)
            .collect(Collectors.toMap(BdWorkstation::getWorkstationId,
                workstation -> workstation, (left, right) -> left));
        for (Long materialId : materialIds) {
            RoutingAssignment assignment = assignments.get(materialId);
            List<RoutingStepFact> facts;
            if (assignment != null) {
                facts = assignment.steps().stream()
                    .sorted(routingStepSourceComparator())
                    .map(step -> {
                        BdWorkstation workstation = workstationById.get(step.getWorkstationId());
                        Long workshopId = workstation == null
                            ? uniqueWorkshopId(scope.workOrders(), materialId)
                            : workstation.getWorkshopId();
                        return new RoutingStepFact(
                            assignment.routing().getRoutingId(),
                            step.getStepId(),
                            step.getSequenceNum(),
                            step.getWorkstationId(),
                            workshopId,
                            step.getStandardHours(), step.getSetupHours(), step.getIsOutsource(),
                            step.getWaitTime(), step.getTransferTime());
                    })
                    .toList();
            } else {
                Long workshopId = uniqueWorkshopId(scope.workOrders(), materialId);
                facts = routingSteps(input, materialId).stream()
                    .map(step -> new RoutingStepFact(
                        null,
                        step.getStepId(),
                        step.getSeqNum(),
                        step.getWorkstationId(),
                        workshopId,
                        step.getStandardHours(), step.getSetupHours(), step.getIsOutsource(),
                        step.getWaitTime(), step.getTransferTime()))
                    .toList();
            }
            routes.put(materialId, facts);
        }
        return Collections.unmodifiableMap(routes);
    }

    private Long uniqueWorkshopId(List<PrdWorkOrder> workOrders, Long materialId) {
        List<Long> workshops = workOrders.stream()
            .filter(workOrder -> Objects.equals(materialId, workOrder.getMaterialId()))
            .map(PrdWorkOrder::getWorkshopId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        return workshops.size() == 1 ? workshops.get(0) : null;
    }

    /** 数量不换算；只有原MRP单位与已确认根基本单位一致时才保留排名单位身份。 */
    private List<RequirementFact> buildRequirementFacts(List<RequirementLink> rows, List<BdMaterial> materials,
            List<com.nexus.erp.bd.domain.BdUnitOfMeasure> units, List<String> warnings, String tenantId, Date observedAt) {
        var masters = materials.stream().collect(Collectors.toMap(BdMaterial::getMaterialId, m -> m, (a, b) -> a));
        var byId = units.stream().collect(Collectors.toMap(com.nexus.erp.bd.domain.BdUnitOfMeasure::getUomId, u -> u, (a, b) -> a));
        return rows.stream()
            .filter(row -> row.netQuantity().signum() > 0)
            .map(row -> new RequirementFact(
                row.detail().getDetailId(),
                row.detail().getMaterialId(),
                row.detail().getMaterialCode(),
                row.detail().getMaterialName(),
                toLocalDate(row.detail().getRequiredDate()),
                row.netQuantity(),
                row.workOrder() == null ? null : row.workOrder().getWoId(),
                row.workOrder() == null ? null : row.workOrder().getWoNo(),
                verifiedRequirementUnit(row, masters, byId, warnings, tenantId, observedAt)))
            .toList();
    }

    private Long verifiedRequirementUnit(RequirementLink row, Map<Long, BdMaterial> materials,
            Map<Long, com.nexus.erp.bd.domain.BdUnitOfMeasure> units, List<String> warnings, String tenant, Date observedAt) {
        Long unit = verifiedBaseQuantityUnit(row.detail().getMaterialId(), row.detail().getUnit(), row.netQuantity(), materials, units, tenant, observedAt);
        if (unit == null) warnings.add("UNKNOWN_REQUIREMENT_UNIT:" + row.detail().getDetailId());
        return unit;
    }

    /** 原单位和数量都需匹配已确认根单位；不从名称猜ID、不为采购供给擅自换算。 */
    private Long verifiedBaseQuantityUnit(Long materialId, String originalUnit, BigDecimal quantity, Map<Long, BdMaterial> materials,
            Map<Long, com.nexus.erp.bd.domain.BdUnitOfMeasure> units, String tenant, Date observedAt) {
        var material = materials.get(materialId);
        var unit = material == null ? null : units.get(material.getBaseUomId());
        if (material != null && unit != null && Objects.equals(tenant, material.getTenantId())
            && Objects.equals(tenant, unit.getTenantId()) && Objects.equals(1L, material.getStatus())
            && "0".equals(material.getDelFlag()) && Objects.equals(1L, unit.getStatus()) && "0".equals(unit.getDelFlag())
            && Objects.equals(1L, unit.getIsBase()) && Objects.equals(unit.getUomId(), unit.getPrimaryId()) && unit.getConversionRate() != null
            && unit.getConversionRate().compareTo(BigDecimal.ONE) == 0 && unit.getQuantityScale() != null
            && unit.getQuantityScale() >= 0 && unit.getQuantityScale() <= 4
            && unit.getConversionVersion() != null && !unit.getConversionVersion().isBlank()
            && unit.getConversionVerifiedAt() != null && observedAt != null && !unit.getConversionVerifiedAt().after(observedAt)
            && unit.getUomId().toString().equals(originalUnit)
            && rawQuantityWithinUnit(quantity, unit.getQuantityScale())) return unit.getUomId();
        return null;
    }

    /** 原始数量独立核精度/边界，不让净量相减或NULL归零掩盖原值非法。 */
    private static boolean rawQuantityWithinUnit(BigDecimal value, Integer scale) {
        if (value == null || value.signum() < 0 || scale == null) return false;
        var normalized = value.stripTrailingZeros();
        return normalized.scale() <= scale && (long) normalized.precision() - normalized.scale() <= 14;
    }

    private List<StockFact> buildStockFacts(LinkedHashSet<Long> materialIds, StockBundle stocks,
                                            List<String> warnings) {
        Map<Long, BdMaterial> materialById = stocks.materials().stream()
            .collect(Collectors.toMap(BdMaterial::getMaterialId, material -> material, (left, right) -> left));
        Map<Long, List<WmMaterialStock>> rowsByMaterial = stocks.rows().stream()
            .collect(Collectors.groupingBy(WmMaterialStock::getMaterialId));
        List<StockFact> facts = new ArrayList<>();
        for (Long materialId : materialIds) {
            BdMaterial material = materialById.get(materialId);
            if (material == null) warnings.add("MISSING_MATERIAL_MASTER:" + materialId);
            List<WmMaterialStock> rows = rowsByMaterial.getOrDefault(materialId, List.of());
            boolean known = material != null && material.getBaseUomId() != null && material.getBaseUomId() > 0
                && !rows.isEmpty();
            BigDecimal supply = BigDecimal.ZERO;
            BigDecimal reserved = BigDecimal.ZERO;
            for (WmMaterialStock row : rows) {
                boolean rowKnown = knownStockQuantity(row.getStockQuantity()) && knownStockQuantity(row.getReservedQuantity())
                    && row.getReservedQuantity().compareTo(row.getStockQuantity()) <= 0
                    && (Long.valueOf(0).equals(row.getFrozenFlag()) || Long.valueOf(1).equals(row.getFrozenFlag()))
                    && material != null && material.getBaseUomId() != null
                    && material.getBaseUomId().toString().equals(row.getBaseUomId());
                known = known && rowKnown;
                if (!rowKnown) continue; /** 任一未知行使整个物料供给未知，不能提供残缺合计。 */
                if (Long.valueOf(1).equals(row.getFrozenFlag())) {
                    warnings.add("FROZEN_STOCK_EXCLUDED:" + materialId);
                    continue; /** 冻结来源不是可用供给，其预留也不再次扣减。 */
                }
                supply = supply.add(row.getStockQuantity());
                reserved = reserved.add(row.getReservedQuantity());
            }
            if (!known) warnings.add("UNKNOWN_STOCK_SOURCE:" + materialId);
            BigDecimal safety = material == null ? null : material.getSafetyStock();
            if (!knownStockQuantity(safety)) {
                safety = null;
                if (material != null) warnings.add("UNKNOWN_SAFETY_STOCK_SOURCE:" + materialId);
            }
            facts.add(new StockFact(materialId, known ? supply : null, known ? reserved : null, safety));
        }
        return List.copyOf(facts);
    }

    /** 未知、负数或超出既有数量存储边界不得被默认值或取整认证。 */
    private static boolean knownStockQuantity(BigDecimal quantity) {
        return quantity != null && quantity.signum() >= 0
            && quantity.stripTrailingZeros().scale() <= 4
            && quantity.precision() - quantity.scale() <= 14;
    }

    private List<ArrivalFact> buildArrivalFacts(PurchaseBundle purchases,
                                                 Set<Long> materialIds,
                                                 LocalDate horizonEnd, List<BdMaterial> materials,
                                                 List<com.nexus.erp.bd.domain.BdUnitOfMeasure> units, String tenant, List<String> warnings, Date observedAt) {
        var masters = materials.stream().collect(Collectors.toMap(BdMaterial::getMaterialId, m -> m, (a, b) -> a));
        var byId = units.stream().collect(Collectors.toMap(com.nexus.erp.bd.domain.BdUnitOfMeasure::getUomId, u -> u, (a, b) -> a));
        Map<Long, PuPurchaseOrder> orderById = purchases.orders().stream()
            .collect(Collectors.toMap(PuPurchaseOrder::getOrderId, order -> order));
        List<ArrivalFact> arrivals = new ArrayList<>();
        for (PuPurchaseOrderLine line : purchases.lines()) {
            if (materialIds != null && !materialIds.contains(line.getMaterialId())) {
                continue;
            }
            PuPurchaseOrder order = orderById.get(line.getOrderId());
            if (order == null || order.getExpectedArrivalDate() == null) {
                continue;
            }
            LocalDate expectedDate = toLocalDate(order.getExpectedArrivalDate());
            if (expectedDate == null || expectedDate.isAfter(horizonEnd)) {
                continue;
            }
            BigDecimal remaining = remaining(line.getOrderQuantity(), line.getReceivedQuantity());
            if (remaining.signum() <= 0) {
                continue;
            }
            Long baseUnit = verifiedBaseQuantityUnit(line.getMaterialId(), line.getUnit(), remaining, masters, byId, tenant, observedAt);
            var sourceUnit = baseUnit == null ? null : byId.get(baseUnit);
            if (sourceUnit == null || !rawQuantityWithinUnit(line.getOrderQuantity(), sourceUnit.getQuantityScale())
                || !rawQuantityWithinUnit(line.getReceivedQuantity(), sourceUnit.getQuantityScale())
                || line.getReceivedQuantity().compareTo(line.getOrderQuantity()) > 0) baseUnit = null;
            if (baseUnit == null) warnings.add("UNKNOWN_ARRIVAL_UNIT:" + line.getLineId());
            arrivals.add(new ArrivalFact(
                line.getMaterialId(), line.getLineId(), expectedDate, remaining, order.getVendorId(), baseUnit));
        }
        arrivals.sort(Comparator
            .comparing(ArrivalFact::expectedDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ArrivalFact::materialId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ArrivalFact::purchaseLineId, Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(arrivals);
    }

    private Map<String, Object> buildCurrentSchedule(DecisionScope scope, ScheduleSelection selection,
                                                     List<PrdCapacityCalendar> calendars) {
        PrdSchedulePlan plan = selection.plan();
        if (plan == null) {
            return new LinkedHashMap<>();
        }
        LinkedHashMap<String, Object> schedule = new LinkedHashMap<>();
        schedule.put("planId", idString(plan.getPlanId()));
        schedule.put("tenantId", plan.getTenantId());
        schedule.put("capacityCalcVersion", plan.getCapacityCalcVersion());
        schedule.put("planNo", plan.getPlanNo());
        schedule.put("status", plan.getStatus());
        schedule.put("strategy", plan.getStrategy());
        schedule.put("startDate", toIsoDate(plan.getStartDate()));
        schedule.put("horizonDays", plan.getHorizonDays());
        schedule.put("makespanDate", toIsoDate(plan.getMakespanDate()));
        schedule.put("totalHours", decimal(plan.getTotalHours()));

        List<String> workOrderIds = selection.displayTasks().stream()
            .map(PrdScheduleTask::getWoId)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .map(String::valueOf)
            .toList();
        schedule.put("workOrderIds", workOrderIds);

        List<Map<String, Object>> taskPreview = selection.displayTasks().stream()
            .sorted(scheduleTaskComparator())
            .map(this::scheduleTaskMap)
            .toList();
        schedule.put("tasks", taskPreview);
        ScheduleProvenance provenance = attachScheduleProvenance(selection, calendars, schedule);
        Map<Long, BigDecimal> ledgerHours = provenance.ledgerHours();
        /**
         * 来源日历版本已包含在关键新鲜度指纹中。
         * 原始计划与有效工时须和 APS 剩余工时分开保存，以便独立重读并重算
         * 已保存资源负荷，不能从候选方案推断。
         */
        List<Map<String, Object>> capacityCalendars = provenance.calendars().stream()
            .sorted(capacityCalendarComparator())
            .map(calendar -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("calendarId", idString(calendar.getCalendarId()));
                row.put("tenantId", calendar.getTenantId());
                row.put("status", calendar.getStatus());
                row.put("calendarDate", toIsoDate(calendar.getCalendarDate()));
                row.put("resourceType", calendar.getObjectType());
                row.put("resourceId", idString(calendar.getObjectId()));
                row.put("shiftId", idString(calendar.getShiftId()));
                row.put("plannedHours", decimal(calendar.getPlannedHours()));
                row.put("ledgerHours", decimal(ledgerHours.getOrDefault(calendar.getCalendarId(), BigDecimal.ZERO)));
                row.put("effectiveHours", decimal(calendar.getEffectiveHours()));
                return Collections.unmodifiableMap(row);
            }).toList();
        schedule.put("capacityCalendars", capacityCalendars);

        List<String> salesOrderIds = scope.workOrderSalesOrderIds().values().stream()
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .map(String::valueOf)
            .toList();
        schedule.put("workOrderSalesOrderIds", salesOrderIds);
        List<Map<String, Object>> mappings = scope.workOrderSalesOrderIds().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> {
                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                row.put("workOrderId", idString(entry.getKey()));
                row.put("salesOrderId", idString(entry.getValue()));
                return Collections.unmodifiableMap(row);
            })
            .toList();
        schedule.put("workOrderSalesOrders", mappings);
        return schedule;
    }

    /** 同一快照内读本方案逐日行及相关产能日历的租户全账册，避免借用他计划的工时。 */
    private ScheduleProvenance attachScheduleProvenance(ScheduleSelection selection, List<PrdCapacityCalendar> calendars,
                                           LinkedHashMap<String, Object> schedule) {
        PrdSchedulePlan plan = selection.plan();
        String tenant = plan.getTenantId();
        if (tenant == null || tenant.isBlank() || plan.getPlanId() == null)
            throw new ServiceException("排程缺少可信租户或方案标识");
        if (TenantHelper.isEnable() && !tenant.equals(TenantHelper.getTenantId()))
            throw new ServiceException("排程与当前租户不匹配");
        List<PrdScheduleTaskDayLoad> allLoads = safeList(taskDayLoadMapper.selectList(
            Wrappers.lambdaQuery(PrdScheduleTaskDayLoad.class)
                .eq(PrdScheduleTaskDayLoad::getTenantId, tenant)
                .eq(PrdScheduleTaskDayLoad::getPlanId, plan.getPlanId())
                .last("LIMIT 100001")));
        if (allLoads.size() > 100000 || allLoads.stream().anyMatch(row -> row == null
            || !tenant.equals(row.getTenantId()) || !plan.getPlanId().equals(row.getPlanId())))
            throw new ServiceException("排程逐日来源超界或归属不一致");
        Set<Long> displayedTasks = selection.displayTasks().stream().map(PrdScheduleTask::getTaskId)
            .filter(Objects::nonNull).collect(Collectors.toSet());
        List<Map<String, Object>> dayLoads = allLoads.stream()
            .filter(row -> displayedTasks.contains(row.getTaskId()))
            .sorted(Comparator.comparing(PrdScheduleTaskDayLoad::getLoadId, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(row -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("loadId", idString(row.getLoadId()));
                value.put("tenantId", row.getTenantId());
                value.put("planId", idString(row.getPlanId()));
                value.put("taskId", idString(row.getTaskId()));
                value.put("resourceType", row.getResourceType());
                value.put("resourceId", idString(row.getResourceId()));
                value.put("loadDate", toIsoDate(row.getLoadDate()));
                value.put("plannedHours", decimal(row.getPlannedHours()));
                return Collections.unmodifiableMap(value);
            }).toList();
        schedule.put("taskDayLoads", dayLoads);

        /**
         * 计划加载器已限定所选任务的范围；计划逐日负荷引用的其他任务标识需独立查询，
         * 避免把跨工单计划误判为伪造任务。
         */
        List<PrdScheduleTask> planTasks = selection.fingerprintTasks();
        if (planTasks.size() > 1000) throw new ServiceException("排程任务来源超界");
        Map<Long, PrdScheduleTask> taskById = new HashMap<>();
        for (PrdScheduleTask row : planTasks) {
            if (row == null || row.getTaskId() == null || !tenant.equals(row.getTenantId())
                || !plan.getPlanId().equals(row.getPlanId())
                || taskById.putIfAbsent(row.getTaskId(), row) != null)
                throw new ServiceException("排程任务归属不一致");
        }
        if (!taskById.keySet().containsAll(displayedTasks))
            throw new ServiceException("排程展示任务不属于当前方案");
        Map<String, PrdScheduleTaskDayLoad> loadByTaskDate = new HashMap<>();
        for (PrdScheduleTaskDayLoad row : allLoads) {
            PrdScheduleTask task = row == null ? null : taskById.get(row.getTaskId());
            if (row != null && task == null && row.getTaskId() != null) {
                task = scheduleTaskMapper.selectById(row.getTaskId());
                if (task == null || !tenant.equals(task.getTenantId())
                    || !plan.getPlanId().equals(task.getPlanId())
                    || taskById.putIfAbsent(row.getTaskId(), task) != null)
                    throw new ServiceException("排程逐日来源的任务归属不一致");
            }
            LocalDate date = row == null ? null : toLocalDate(row.getLoadDate());
            if (task == null || date == null || row.getPlannedHours() == null
                || row.getPlannedHours().signum() <= 0 || !Objects.equals(row.getResourceType(), task.getResourceType())
                || !Objects.equals(row.getResourceId(), task.getResourceId())
                || loadByTaskDate.putIfAbsent(row.getTaskId() + ":" + date, row) != null)
                throw new ServiceException("排程逐日来源与方案任务不一致");
        }
        /** 按计划查询，而非仅按来源日历或展示的任务标识查询，以发现隐藏的计划行。 */
        List<PrdScheduleCapacityBooking> planBookings = safeList(capacityBookingMapper.selectList(
            Wrappers.lambdaQuery(PrdScheduleCapacityBooking.class)
                .eq(PrdScheduleCapacityBooking::getTenantId, tenant)
                .eq(PrdScheduleCapacityBooking::getPlanId, plan.getPlanId())
                .last("LIMIT 100001")));
        if (planBookings.size() > 100000) throw new ServiceException("本方案产能占用超界");
        if (!planBookings.isEmpty()) {
            /** 仅按已观测逐日负荷加载，会漏掉证据已全部删除但仍然有效的计划任务。 */
            List<PrdScheduleTask> fullPlanTasks = safeList(scheduleTaskMapper.selectList(
                Wrappers.lambdaQuery(PrdScheduleTask.class)
                    .eq(PrdScheduleTask::getTenantId, tenant)
                    .eq(PrdScheduleTask::getPlanId, plan.getPlanId())
                    .last("LIMIT 1001")));
            if (fullPlanTasks.isEmpty() || fullPlanTasks.size() > 1000)
                throw new ServiceException("已发布排程任务缺失或超界");
            Set<Long> fullIds = new HashSet<>();
            for (PrdScheduleTask task : fullPlanTasks) {
                if (task == null || task.getTaskId() == null || !tenant.equals(task.getTenantId())
                    || !plan.getPlanId().equals(task.getPlanId()) || !fullIds.add(task.getTaskId()))
                    throw new ServiceException("已发布排程任务归属不一致");
                PrdScheduleTask seen = taskById.putIfAbsent(task.getTaskId(), task);
                if (seen != null && !seen.equals(task))
                    throw new ServiceException("排程任务在同一来源快照内发生变化");
            }
            if (!fullIds.containsAll(taskById.keySet()))
                throw new ServiceException("排程逐日来源的任务不在完整方案中");
            schedule.put("capacityPlanTasks", fullPlanTasks.stream()
                .sorted(scheduleTaskComparator())
                .map(task -> {
                    Map<String, Object> value = new LinkedHashMap<>(scheduleTaskMap(task));
                    value.put("updateTime", toIsoDateTime(task.getUpdateTime()));
                    return Collections.unmodifiableMap(value);
                }).toList());
            schedule.put("capacityPlanDayLoads", allLoads.stream()
                .sorted(Comparator.comparing(PrdScheduleTaskDayLoad::getLoadId))
                .map(this::dayLoadMap).toList());
        }
        Map<Long, PrdCapacityCalendar> calendarById = new HashMap<>();
        for (PrdCapacityCalendar calendar : safeList(calendars)) {
            if (calendar == null || calendar.getCalendarId() == null || !tenant.equals(calendar.getTenantId())
                || calendarById.putIfAbsent(calendar.getCalendarId(), calendar) != null)
                throw new ServiceException("产能日历来源异常");
        }
        Set<Long> missingCalendarIds = new HashSet<>();
        for (PrdScheduleCapacityBooking row : planBookings) {
            if (row == null || row.getBookingId() == null || row.getCalendarId() == null
                || !tenant.equals(row.getTenantId()) || !plan.getPlanId().equals(row.getPlanId())
                || !taskById.containsKey(row.getTaskId()))
                throw new ServiceException("本方案产能占用归属不一致");
            if (!calendarById.containsKey(row.getCalendarId())) missingCalendarIds.add(row.getCalendarId());
        }
        if (calendarById.size() + missingCalendarIds.size() > 10000)
            throw new ServiceException("排程产能日历来源超界");
        /** APS 输入窗口不是账册边界：仍处于已发布状态的计划可能跨越更早的日期。 */
        for (List<Long> batch : batches(missingCalendarIds)) {
            for (PrdCapacityCalendar calendar : safeList(capacityCalendarMapper.selectBatchIds(batch))) {
                if (calendar == null || calendar.getCalendarId() == null
                    || !batch.contains(calendar.getCalendarId()) || !tenant.equals(calendar.getTenantId())
                    || calendarById.putIfAbsent(calendar.getCalendarId(), calendar) != null)
                    throw new ServiceException("排程关联日历重复或租户归属无效");
            }
        }
        if (!calendarById.keySet().containsAll(missingCalendarIds))
            throw new ServiceException("排程关联的历史产能日历不存在");
        List<Long> calendarIds = calendarById.keySet().stream().sorted().toList();
        Set<Long> calendarIdSet = calendarById.keySet();
        List<PrdScheduleCapacityBooking> allBookings = calendarIds.isEmpty() ? List.of() :
            safeList(capacityBookingMapper.selectList(Wrappers.lambdaQuery(PrdScheduleCapacityBooking.class)
                .eq(PrdScheduleCapacityBooking::getTenantId, tenant)
                .in(PrdScheduleCapacityBooking::getCalendarId, calendarIds)
                .last("LIMIT 100001")));
        if (allBookings.size() > 100000) throw new ServiceException("产能账册来源超界");
        Map<Long, PrdScheduleCapacityBooking> ledgerById = new HashMap<>();
        Map<Long, BigDecimal> ledgerHours = new HashMap<>();
        Set<Long> otherPlanIds = new HashSet<>();
        for (PrdScheduleCapacityBooking row : allBookings) {
            PrdCapacityCalendar calendar = row == null ? null : calendarById.get(row.getCalendarId());
            if (row == null || row.getBookingId() == null || row.getPlanId() == null
                || row.getTaskId() == null || row.getCalendarId() == null
                || !calendarIdSet.contains(row.getCalendarId()) || !tenant.equals(row.getTenantId())
                || calendar == null || !"ACTIVE".equals(calendar.getStatus())
                || !Objects.equals(toLocalDate(calendar.getCalendarDate()), toLocalDate(row.getLoadDate()))
                || row.getBookedHours() == null || row.getBookedHours().signum() <= 0
                || ledgerById.putIfAbsent(row.getBookingId(), row) != null)
                throw new ServiceException("产能账册日期、负荷或租户归属无效");
            if (!plan.getPlanId().equals(row.getPlanId())) otherPlanIds.add(row.getPlanId());
            ledgerHours.merge(row.getCalendarId(), row.getBookedHours(), BigDecimal::add);
        }
        Map<Long, PrdSchedulePlan> otherPlans = new HashMap<>();
        for (List<Long> batch : batches(otherPlanIds)) {
            for (PrdSchedulePlan other : safeList(schedulePlanMapper.selectBatchIds(batch))) {
                if (other == null || other.getPlanId() == null || !batch.contains(other.getPlanId())
                    || !tenant.equals(other.getTenantId()) || !verifiablePublishedSchedule(other)
                    || otherPlans.putIfAbsent(other.getPlanId(), other) != null)
                    throw new ServiceException("共享产能占用的方案来源不可信");
            }
        }
        if (!otherPlans.keySet().containsAll(otherPlanIds))
            throw new ServiceException("共享产能占用缺少已发布方案");
        Set<Long> otherTaskIds = allBookings.stream()
            .filter(row -> !plan.getPlanId().equals(row.getPlanId()))
            .map(PrdScheduleCapacityBooking::getTaskId).collect(Collectors.toSet());
        Map<Long, PrdScheduleTask> otherTasks = new HashMap<>();
        for (List<Long> batch : batches(otherTaskIds)) {
            for (PrdScheduleTask task : safeList(scheduleTaskMapper.selectBatchIds(batch))) {
                if (task == null || task.getTaskId() == null || !batch.contains(task.getTaskId())
                    || !tenant.equals(task.getTenantId()) || !otherPlanIds.contains(task.getPlanId())
                    || otherTasks.putIfAbsent(task.getTaskId(), task) != null)
                    throw new ServiceException("共享产能预约的任务来源无效");
            }
        }
        if (!otherTasks.keySet().containsAll(otherTaskIds))
            throw new ServiceException("共享产能预约缺少真实任务");
        verifyOtherPlanProvenance(tenant, otherTasks, allBookings, calendarById, ledgerById, schedule);
        Map<Long, PrdScheduleCapacityBooking> planById = new HashMap<>();
        Map<String, BigDecimal> bookedByTaskDate = new HashMap<>();
        for (PrdScheduleCapacityBooking row : planBookings) {
            PrdCapacityCalendar calendar = calendarById.get(row.getCalendarId());
            PrdScheduleTaskDayLoad load = loadByTaskDate.get(row.getTaskId() + ":" + toLocalDate(row.getLoadDate()));
            if (calendar == null || load == null || !"ACTIVE".equals(calendar.getStatus())
                || !Objects.equals(toLocalDate(calendar.getCalendarDate()), toLocalDate(row.getLoadDate()))
                || !Objects.equals(calendar.getObjectType(), load.getResourceType())
                || !Objects.equals(calendar.getObjectId(), load.getResourceId())
                || row.getBookedHours() == null || row.getBookedHours().signum() <= 0
                || planById.putIfAbsent(row.getBookingId(), row) != null
                || !row.equals(ledgerById.get(row.getBookingId())))
                throw new ServiceException("本方案产能占用与日历总账或逐日来源不一致");
            bookedByTaskDate.merge(row.getTaskId() + ":" + toLocalDate(row.getLoadDate()),
                row.getBookedHours(), BigDecimal::add);
        }
        for (var entry : loadByTaskDate.entrySet()) {
            if (entry.getValue().getPlannedHours().compareTo(
                bookedByTaskDate.getOrDefault(entry.getKey(), BigDecimal.ZERO)) != 0)
                throw new ServiceException("排程逐日负荷与本方案班次预约不一致");
        }
        if (!planBookings.isEmpty()) {
            Map<Long, BigDecimal> totalByTask = new HashMap<>();
            Map<Long, LocalDate> firstByTask = new HashMap<>();
            Map<Long, LocalDate> lastByTask = new HashMap<>();
            for (PrdScheduleTaskDayLoad row : allLoads) {
                LocalDate date = toLocalDate(row.getLoadDate());
                totalByTask.merge(row.getTaskId(), row.getPlannedHours(), BigDecimal::add);
                firstByTask.merge(row.getTaskId(), date, (a, b) -> a.isBefore(b) ? a : b);
                lastByTask.merge(row.getTaskId(), date, (a, b) -> a.isAfter(b) ? a : b);
            }
            for (PrdScheduleTask task : taskById.values()) {
                BigDecimal total = totalByTask.getOrDefault(task.getTaskId(), BigDecimal.ZERO);
                if (task.getProcessHours() == null || task.getSetupHours() == null
                    || task.getProcessHours().signum() < 0 || task.getSetupHours().signum() < 0)
                    throw new ServiceException("本方案工序工时来源无效");
                if ("OUTSOURCE".equals(task.getResourceType())) {
                    if (total.signum() != 0) throw new ServiceException("委外工序不应占用内部班次");
                    continue;
                }
                if (task.getProcessHours().add(task.getSetupHours()).compareTo(total) != 0
                    || !Objects.equals(toLocalDate(task.getStartTime()), firstByTask.get(task.getTaskId()))
                    || !Objects.equals(toLocalDate(task.getEndTime()), lastByTask.get(task.getTaskId())))
                    throw new ServiceException("本方案工序总工时与全方案逐日及班次预约不一致");
            }
        }
        for (PrdScheduleCapacityBooking row : allBookings) {
            if (plan.getPlanId().equals(row.getPlanId()) && !planById.containsKey(row.getBookingId()))
                throw new ServiceException("日历总账包含未归属本方案的占用");
        }
        List<Map<String, Object>> ownBookings = planBookings.stream()
            .filter(row -> displayedTasks.contains(row.getTaskId()))
            .sorted(Comparator.comparing(PrdScheduleCapacityBooking::getBookingId))
            .map(this::bookingMap).toList();
        schedule.put("capacityBookings", ownBookings);
        schedule.put("capacityLedgerBookings", allBookings.stream()
            .sorted(Comparator.comparing(PrdScheduleCapacityBooking::getBookingId))
            .map(this::bookingMap).toList());
        schedule.put("capacityLedgerPlans", otherPlans.values().stream()
            .sorted(Comparator.comparing(PrdSchedulePlan::getPlanId))
            .map(other -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("planId", idString(other.getPlanId()));
                value.put("tenantId", other.getTenantId());
                value.put("status", other.getStatus());
                value.put("capacityCalcVersion", other.getCapacityCalcVersion());
                return Collections.unmodifiableMap(value);
            }).toList());
        return new ScheduleProvenance(ledgerHours, List.copyOf(calendarById.values()));
    }

    /** 重新读取共享同一日历的其他计划的全部事实；其局部预占记录不足以证明负荷。 */
    private void verifyOtherPlanProvenance(String tenant, Map<Long, PrdScheduleTask> tasks,
                                            List<PrdScheduleCapacityBooking> sharedLedger,
                                            Map<Long, PrdCapacityCalendar> sharedCalendars,
                                            Map<Long, PrdScheduleCapacityBooking> sharedById,
                                            LinkedHashMap<String, Object> schedule) {
        if (tasks.isEmpty()) return;
        List<PrdScheduleTaskDayLoad> sourceLoads = new ArrayList<>();
        List<PrdScheduleCapacityBooking> sourceBookings = new ArrayList<>();
        for (List<Long> batch : batches(tasks.keySet())) {
            List<PrdScheduleTaskDayLoad> queriedLoads = safeList(taskDayLoadMapper.selectList(
                Wrappers.lambdaQuery(PrdScheduleTaskDayLoad.class)
                    .eq(PrdScheduleTaskDayLoad::getTenantId, tenant)
                    .in(PrdScheduleTaskDayLoad::getTaskId, batch)
                    .last("LIMIT 100001")));
            List<PrdScheduleCapacityBooking> queriedBookings = safeList(capacityBookingMapper.selectList(
                Wrappers.lambdaQuery(PrdScheduleCapacityBooking.class)
                    .eq(PrdScheduleCapacityBooking::getTenantId, tenant)
                    .in(PrdScheduleCapacityBooking::getTaskId, batch)
                    .last("LIMIT 100001")));
            if (queriedLoads.size() > 100000 || queriedBookings.size() > 100000
                || sourceLoads.size() + queriedLoads.size() > 100000
                || sourceBookings.size() + queriedBookings.size() > 100000)
                throw new ServiceException("共享产能来源超界");
            sourceLoads.addAll(queriedLoads);
            sourceBookings.addAll(queriedBookings);
        }
        Map<String, PrdScheduleTaskDayLoad> byTaskDate = new HashMap<>();
        Map<Long, BigDecimal> sourceTotals = new HashMap<>();
        Map<Long, LocalDate> firstDays = new HashMap<>();
        Map<Long, LocalDate> lastDays = new HashMap<>();
        Set<Long> loadIds = new HashSet<>();
        for (PrdScheduleTaskDayLoad load : sourceLoads) {
            PrdScheduleTask task = load == null ? null : tasks.get(load.getTaskId());
            LocalDate day = load == null ? null : toLocalDate(load.getLoadDate());
            LocalDate start = task == null ? null : toLocalDate(task.getStartTime());
            LocalDate end = task == null ? null : toLocalDate(task.getEndTime());
            if (task == null || load.getLoadId() == null || !loadIds.add(load.getLoadId())
                || !tenant.equals(load.getTenantId()) || !Objects.equals(task.getPlanId(), load.getPlanId())
                || !Objects.equals(task.getResourceType(), load.getResourceType())
                || !Objects.equals(task.getResourceId(), load.getResourceId())
                || "OUTSOURCE".equals(task.getResourceType()) || day == null || start == null || end == null
                || day.isBefore(start) || day.isAfter(end) || load.getPlannedHours() == null
                || load.getPlannedHours().signum() <= 0 || load.getPlannedHours().scale() > 2
                || byTaskDate.putIfAbsent(load.getTaskId() + ":" + day, load) != null)
                throw new ServiceException("共享产能来源逐日负荷无效");
            sourceTotals.merge(load.getTaskId(), load.getPlannedHours(), BigDecimal::add);
            firstDays.merge(load.getTaskId(), day, (a, b) -> a.isBefore(b) ? a : b);
            lastDays.merge(load.getTaskId(), day, (a, b) -> a.isAfter(b) ? a : b);
        }
        for (PrdScheduleTask task : tasks.values()) {
            if (task.getProcessHours() == null || task.getSetupHours() == null
                || task.getProcessHours().signum() < 0 || task.getSetupHours().signum() < 0
                || task.getProcessHours().add(task.getSetupHours()).compareTo(
                    sourceTotals.getOrDefault(task.getTaskId(), BigDecimal.ZERO)) != 0
                || !Objects.equals(toLocalDate(task.getStartTime()), firstDays.get(task.getTaskId()))
                || !Objects.equals(toLocalDate(task.getEndTime()), lastDays.get(task.getTaskId())))
                throw new ServiceException("共享产能的工序总工时与逐日负荷不一致");
        }
        Map<Long, PrdCapacityCalendar> calendars = new HashMap<>(sharedCalendars);
        Set<Long> missingIds = new HashSet<>();
        for (PrdScheduleCapacityBooking row : sourceBookings) {
            if (row == null || row.getCalendarId() == null) throw new ServiceException("共享产能预约缺少日历");
            if (!calendars.containsKey(row.getCalendarId())) missingIds.add(row.getCalendarId());
        }
        if (calendars.size() + missingIds.size() > 10000)
            throw new ServiceException("共享产能预约关联日历超界");
        for (List<Long> batch : batches(missingIds)) {
            for (PrdCapacityCalendar calendar : safeList(capacityCalendarMapper.selectBatchIds(batch))) {
                if (calendar == null || calendar.getCalendarId() == null || !batch.contains(calendar.getCalendarId())
                    || !tenant.equals(calendar.getTenantId())
                    || calendars.putIfAbsent(calendar.getCalendarId(), calendar) != null)
                    throw new ServiceException("共享产能预约日历归属不一致");
            }
        }
        if (!calendars.keySet().containsAll(missingIds))
            throw new ServiceException("共享产能预约关联日历不存在");
        Map<String, BigDecimal> bookedByTaskDate = new HashMap<>();
        Map<Long, PrdScheduleCapacityBooking> bookingById = new HashMap<>();
        for (PrdScheduleCapacityBooking row : sourceBookings) {
            PrdScheduleTask task = tasks.get(row.getTaskId());
            PrdCapacityCalendar calendar = calendars.get(row.getCalendarId());
            LocalDate day = toLocalDate(row.getLoadDate());
            if (task == null || row.getBookingId() == null || calendar == null || day == null
                || !tenant.equals(row.getTenantId()) || !"ACTIVE".equals(calendar.getStatus())
                || !Objects.equals(row.getPlanId(), task.getPlanId())
                || !Objects.equals(day, toLocalDate(calendar.getCalendarDate()))
                || !Objects.equals(task.getResourceType(), calendar.getObjectType())
                || !Objects.equals(task.getResourceId(), calendar.getObjectId())
                || !byTaskDate.containsKey(row.getTaskId() + ":" + day)
                || row.getBookedHours() == null || row.getBookedHours().signum() <= 0
                || bookingById.putIfAbsent(row.getBookingId(), row) != null
                || sharedById.containsKey(row.getBookingId())
                    && !row.equals(sharedById.get(row.getBookingId())))
                throw new ServiceException("共享产能预约与原始工序/日历不一致");
            bookedByTaskDate.merge(row.getTaskId() + ":" + day, row.getBookedHours(), BigDecimal::add);
        }
        for (PrdScheduleCapacityBooking row : sharedLedger) {
            if (tasks.containsKey(row.getTaskId()) && !bookingById.containsKey(row.getBookingId()))
                throw new ServiceException("共享产能账册缺少跨方案预约来源");
        }
        for (var entry : byTaskDate.entrySet()) {
            if (entry.getValue().getPlannedHours().compareTo(
                bookedByTaskDate.getOrDefault(entry.getKey(), BigDecimal.ZERO)) != 0)
                throw new ServiceException("共享产能逐日负荷与完整班次预约不一致");
        }
        schedule.put("capacityDependencyTasks", tasks.values().stream()
            .sorted(scheduleTaskComparator()).map(this::scheduleTaskMap).toList());
        schedule.put("capacityDependencyDayLoads", sourceLoads.stream()
            .sorted(Comparator.comparing(PrdScheduleTaskDayLoad::getLoadId)).map(this::dayLoadMap).toList());
        schedule.put("capacityDependencyBookings", sourceBookings.stream()
            .sorted(Comparator.comparing(PrdScheduleCapacityBooking::getBookingId)).map(this::bookingMap).toList());
        schedule.put("capacityDependencyCalendars", missingIds.stream().sorted()
            .map(id -> {
                PrdCapacityCalendar row = calendars.get(id);
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("calendarId", idString(id));
                value.put("tenantId", row.getTenantId());
                value.put("status", row.getStatus());
                value.put("calendarDate", toIsoDate(row.getCalendarDate()));
                value.put("resourceType", row.getObjectType());
                value.put("resourceId", idString(row.getObjectId()));
                value.put("shiftId", idString(row.getShiftId()));
                value.put("plannedHours", decimal(row.getPlannedHours()));
                value.put("effectiveHours", decimal(row.getEffectiveHours()));
                return Collections.unmodifiableMap(value);
            }).toList());
    }

    private Map<String, Object> dayLoadMap(PrdScheduleTaskDayLoad row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("loadId", idString(row.getLoadId()));
        value.put("tenantId", row.getTenantId());
        value.put("planId", idString(row.getPlanId()));
        value.put("taskId", idString(row.getTaskId()));
        value.put("resourceType", row.getResourceType());
        value.put("resourceId", idString(row.getResourceId()));
        value.put("loadDate", toIsoDate(row.getLoadDate()));
        value.put("plannedHours", decimal(row.getPlannedHours()));
        return Collections.unmodifiableMap(value);
    }

    private Map<String, Object> bookingMap(PrdScheduleCapacityBooking row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("bookingId", idString(row.getBookingId()));
        value.put("tenantId", row.getTenantId());
        value.put("planId", idString(row.getPlanId()));
        value.put("taskId", idString(row.getTaskId()));
        value.put("calendarId", idString(row.getCalendarId()));
        value.put("loadDate", toIsoDate(row.getLoadDate()));
        value.put("bookedHours", decimal(row.getBookedHours()));
        return Collections.unmodifiableMap(value);
    }
    private Map<String, Object> scheduleTaskMap(PrdScheduleTask task) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("taskId", idString(task.getTaskId()));
        row.put("woId", idString(task.getWoId()));
        row.put("woNo", task.getWoNo());
        row.put("seqNum", task.getSeqNum());
        row.put("stepId", idString(task.getStepId()));
        row.put("processCode", task.getProcessCode());
        row.put("processName", task.getProcessName());
        row.put("resourceType", task.getResourceType());
        row.put("resourceId", idString(task.getResourceId()));
        row.put("resourceName", task.getResourceName());
        row.put("setupHours", decimal(task.getSetupHours()));
        row.put("processHours", decimal(task.getProcessHours()));
        row.put("startTime", toScheduleTime(task.getStartTime()));
        row.put("endTime", toScheduleTime(task.getEndTime()));
        row.put("durationDays", task.getDurationDays());
        row.put("delayFlag", task.getDelayFlag());
        return Collections.unmodifiableMap(row);
    }

    private CriticalFreshnessFingerprint buildFingerprint(DecisionScope scope, LoadedContext context) {
        CriticalRootRef root = new CriticalRootRef(
            scope.normalizedRef().type().name(), scope.normalizedRef().id(), scope.normalizedRef().code());
        List<CriticalOrder> orders = scope.salesOrders().stream()
            .sorted(orderComparator())
            .map(order -> new CriticalOrder(
                order.getSoId(), order.getOrderStatus(), toIsoDate(order.getPromiseDate()),
                toIsoDateTime(order.getUpdateTime())))
            .toList();
        List<CriticalOrderLine> orderLines = scope.salesOrderLines().stream()
            .sorted(orderLineComparator())
            .map(line -> new CriticalOrderLine(
                line.getLineId(), line.getSoId(), line.getMaterialId(), line.getLineStatus(),
                canonicalDecimal(line.getOrderedQty()), canonicalDecimal(line.getDeliveredQty()),
                canonicalDecimal(line.getFulfilledQty()), canonicalDecimal(line.getProductionQty()),
                canonicalDecimal(line.getPurchaseQty()), canonicalDecimal(line.getReservedQty()),
                toIsoDate(line.getPromiseDate()), toIsoDateTime(line.getUpdateTime())))
            .toList();
        List<CriticalWorkOrder> workOrders = scope.workOrders().stream()
            .sorted(workOrderComparator())
            .map(workOrder -> new CriticalWorkOrder(
                workOrder.getWoId(), workOrder.getWoStatus(), toIsoDate(workOrder.getPlanEndDate()),
                canonicalDecimal(remaining(workOrder.getPlannedQty(), workOrder.getCompletedQty())),
                toIsoDateTime(workOrder.getUpdateTime()), workOrder.getPlanId(),
                workOrder.getSourceType(), workOrder.getSourceId(),
                workOrder.getSourceLineId(), workOrder.getSourceNo()))
            .toList();
        List<CriticalWorkOrderSalesLink> workOrderSalesLinks = scope.workOrderSalesOrderIds().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> new CriticalWorkOrderSalesLink(entry.getKey(), entry.getValue()))
            .toList();

        List<CriticalRequirementHeader> requirementHeaders = context.requirementRows().stream()
            .map(RequirementLink::requirement)
            .collect(Collectors.toMap(PrdMaterialRequirement::getRequirementId,
                requirement -> requirement, (left, right) -> left, TreeMap::new))
            .values().stream()
            .map(requirement -> new CriticalRequirementHeader(
                requirement.getRequirementId(), requirement.getPlanId(), requirement.getStatus(),
                toIsoDateTime(requirement.getUpdateTime())))
            .toList();
        List<CriticalRequirementDetail> requirements = context.requirementRows().stream()
            .sorted(requirementLinkComparator())
            .map(row -> new CriticalRequirementDetail(
                row.detail().getDetailId(), row.detail().getRequirementId(), row.detail().getMaterialId(),
                toIsoDate(row.detail().getRequiredDate()), canonicalDecimal(row.netQuantity()),
                toIsoDateTime(row.detail().getUpdateTime()), row.detail().getUnit()))
            .toList();
        List<CriticalStockRow> stocks = context.stockRows().stream()
            .sorted(stockRowComparator())
            .map(row -> new CriticalStockRow(
                row.getStockId(), row.getMaterialId(), row.getTenantId(), row.getBaseUomId(), row.getFrozenFlag(),
                row.getWarehouseId(), row.getAreaId(), row.getLocationId(), row.getVersion(), row.getDelFlag(),
                canonicalDecimal(row.getStockQuantity()), canonicalDecimal(row.getReservedQuantity()), toIsoDateTime(row.getUpdateTime())))
            .toList();
        List<CriticalMaterial> materials = context.materials().stream()
            .sorted(materialComparator())
            .map(material -> new CriticalMaterial(
                material.getMaterialId(), material.getBaseUomId(), material.getStatus(), material.getDelFlag(),
                canonicalDecimal(material.getSafetyStock()), toIsoDateTime(material.getUpdateTime())))
            .toList();
        List<CriticalPurchaseOrder> purchaseOrders = context.purchaseOrders().stream()
            .sorted(purchaseOrderComparator())
            .map(order -> new CriticalPurchaseOrder(
                order.getOrderId(), order.getStatus(), toIsoDate(order.getExpectedArrivalDate()),
                toIsoDateTime(order.getUpdateTime())))
            .toList();
        List<CriticalPurchaseLine> purchaseLines = context.purchaseLines().stream()
            .sorted(purchaseLineComparator())
            .map(line -> new CriticalPurchaseLine(
                line.getLineId(), line.getOrderId(), line.getMaterialId(),
                canonicalDecimal(remaining(line.getOrderQuantity(), line.getReceivedQuantity())),
                toIsoDateTime(line.getUpdateTime()), line.getUnit(), canonicalDecimal(line.getOrderQuantity()), canonicalDecimal(line.getReceivedQuantity())))
            .toList();

        CriticalApsInput apsInput = buildCriticalApsInput(scope, context.apsInput());
        CriticalSourceVersions sourceVersions = buildCriticalSourceVersions(context.sourceFacts());
        CriticalSchedule currentSchedule = buildCriticalSchedule(context.schedule(), context.currentSchedule());
        return new CriticalFreshnessFingerprint(
            root, orders, orderLines, workOrders, workOrderSalesLinks, requirementHeaders, requirements,
            stocks, materials, context.evidenceUnits().stream().sorted(Comparator.comparing(com.nexus.erp.bd.domain.BdUnitOfMeasure::getUomId))
                .map(u -> new CriticalQuantityUnit(u.getUomId(), u.getTenantId(), u.getPrimaryId(), u.getIsBase(), u.getStatus(), u.getDelFlag(),
                    canonicalDecimal(u.getConversionRate()), u.getQuantityScale(), u.getConversionVersion(), toIsoDateTime(u.getConversionVerifiedAt()))).toList(),
            purchaseOrders, purchaseLines, apsInput, sourceVersions, currentSchedule);
    }

    private CriticalApsInput buildCriticalApsInput(DecisionScope scope, ScheduleInput input) {
        List<CriticalApsWorkOrder> workOrders = safeList(input.getWorkOrders()).stream()
            .sorted(Comparator.comparing(WoInfo::getWoId, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(workOrder -> new CriticalApsWorkOrder(
                workOrder.getWoId(), workOrder.getMaterialId(), canonicalDecimal(workOrder.getRemainingQty()),
                iso(workOrder.getDueDate()), workOrder.getPriority(), workOrder.getWorkshopId()))
            .toList();
        List<CriticalRoutingStep> routingSteps = new ArrayList<>();
        List<Long> materialIds = workOrders.stream()
            .map(CriticalApsWorkOrder::materialId)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();
        for (Long materialId : materialIds) {
            Long workshopId = uniqueWorkshopId(scope.workOrders(), materialId);
            for (StepInfo step : routingSteps(input, materialId)) {
                routingSteps.add(new CriticalRoutingStep(
                    materialId, step.getStepId(), step.getSeqNum(), step.getWorkstationId(), workshopId,
                    canonicalDecimal(step.getStandardHours()), canonicalDecimal(step.getSetupHours()), step.getIsOutsource(),
                    canonicalDecimal(step.getWaitTime()), canonicalDecimal(step.getTransferTime())));
            }
        }
        routingSteps.sort(criticalRoutingComparator());

        List<CriticalCapacity> capacity = new ArrayList<>();
        sortedCapacity(input.getCapacityByDate()).forEach((date, resources) ->
            resources.forEach((resource, hours) ->
                capacity.add(new CriticalCapacity(date.toString(), resource, canonicalDecimal(hours)))));
        List<String> holidays = safeCollection(input.getHolidays()).stream()
            .sorted()
            .map(LocalDate::toString)
            .toList();
        return new CriticalApsInput(
            iso(input.getStartDate()), input.getHorizonDays(),
            input.getStrategy() == null ? null : input.getStrategy().name(),
            workOrders, List.copyOf(routingSteps), List.copyOf(capacity), holidays);
    }

    private CriticalSourceVersions buildCriticalSourceVersions(SourceFacts sourceFacts) {
        List<CriticalRoutingSource> routings = sourceFacts.routing().assignments().stream()
            .sorted(Comparator.comparing(RoutingAssignment::materialId))
            .map(assignment -> {
                BdRouting routing = assignment.routing();
                return new CriticalRoutingSource(
                    assignment.materialId(), routing.getRoutingId(), routing.getMaterialId(),
                    routing.getRoutingVersion(), routing.getIsCurrent(), routing.getStatus(),
                    toIsoDate(routing.getEffectiveDate()), toIsoDate(routing.getExpiryDate()),
                    canonicalDecimal(routing.getTotalHours()), toIsoDateTime(routing.getUpdateTime()));
            })
            .toList();
        List<CriticalRoutingStepSource> routingSteps = sourceFacts.routing().assignments().stream()
            .flatMap(assignment -> assignment.steps().stream()
                .map(step -> new CriticalRoutingStepSource(
                    assignment.materialId(), step.getRoutingId(), step.getStepId(),
                    step.getSequenceNum(), step.getWorkstationId(), canonicalDecimal(step.getStandardHours()),
                    canonicalDecimal(step.getSetupHours()), step.getIsOutsource(), canonicalDecimal(step.getWaitTime()),
                    canonicalDecimal(step.getTransferTime()), toIsoDateTime(step.getUpdateTime()))))
            .sorted(Comparator
                .comparing(CriticalRoutingStepSource::materialId,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CriticalRoutingStepSource::sequenceNum,
                    Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CriticalRoutingStepSource::stepId,
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        CapacitySourceBundle capacity = sourceFacts.capacity();
        List<CriticalCapacityCalendarSource> calendars = capacity.calendars().stream()
            .sorted(capacityCalendarComparator())
            .map(row -> new CriticalCapacityCalendarSource(
                row.getCalendarId(), row.getObjectType(), row.getObjectId(),
                toIsoDate(row.getCalendarDate()), row.getShiftId(), canonicalDecimal(row.getAvailableHours()),
                canonicalDecimal(row.getPlannedHours()), canonicalDecimal(row.getActualHours()),
                canonicalDecimal(row.getEfficiency()), canonicalDecimal(row.getEffectiveHours()),
                canonicalDecimal(row.getRemainingHours()), row.getStatus(), toIsoDateTime(row.getUpdateTime())))
            .toList();
        List<CriticalHolidaySource> holidays = capacity.holidays().stream()
            .sorted(holidayComparator())
            .map(row -> new CriticalHolidaySource(
                row.getHolidayId(), toIsoDate(row.getHolidayDate()), row.getIsWorkingDay(),
                toIsoDateTime(row.getUpdateTime())))
            .toList();
        List<CriticalEmployeeScheduleSource> employeeSchedules = capacity.employeeSchedules().stream()
            .sorted(employeeScheduleComparator())
            .map(row -> new CriticalEmployeeScheduleSource(
                row.getScheduleId(), row.getWorkerId(), row.getTeamId(), row.getShiftId(),
                row.getActualShiftId(), toIsoDate(row.getScheduledDate()), row.getIsWorkingDay(),
                row.getScheduleStatus(), toScheduleTime(row.getStartTime()),
                toScheduleTime(row.getEndTime()), toIsoDateTime(row.getUpdateTime())))
            .toList();
        List<CriticalShiftSource> shifts = capacity.shifts().stream()
            .sorted(shiftComparator())
            .map(row -> new CriticalShiftSource(
                row.getShiftId(), toScheduleTime(row.getStartTime()), toScheduleTime(row.getEndTime()),
                canonicalDecimal(row.getWorkHours()), row.getIsCrossDay(),
                toScheduleTime(row.getRestStartTime()), toScheduleTime(row.getRestEndTime()),
                toIsoDateTime(row.getUpdateTime())))
            .toList();
        List<CriticalTeamSource> teams = capacity.teams().stream()
            .sorted(teamComparator())
            .map(row -> new CriticalTeamSource(
                row.getTeamId(), row.getWorkshopId(), row.getStatus(), row.getMemberCount(),
                toIsoDateTime(row.getUpdateTime())))
            .toList();
        List<CriticalLeaveSource> leaves = capacity.leaves().stream()
            .sorted(leaveComparator())
            .map(row -> new CriticalLeaveSource(
                row.getLeaveId(), row.getWorkerId(), toIsoDate(row.getStartDate()),
                toIsoDate(row.getEndDate()), row.getApplyStatus(), canonicalDecimal(row.getDurationDays()),
                toIsoDateTime(row.getUpdateTime())))
            .toList();
        List<CriticalWorkstationSource> workstations = capacity.workstations().stream()
            .sorted(workstationComparator())
            .map(row -> new CriticalWorkstationSource(
                row.getWorkstationId(), row.getWorkshopId(), row.getStatus(),
                row.getOperatorCount(), canonicalDecimal(row.getStandardCapacity()),
                toIsoDateTime(row.getUpdateTime())))
            .toList();
        return new CriticalSourceVersions(
            routings, routingSteps, calendars, holidays, employeeSchedules,
            shifts, teams, leaves, workstations);
    }

    private CriticalSchedule buildCriticalSchedule(ScheduleSelection selection, Map<String, Object> observedSchedule) {
        if (selection.plan() == null) {
            return null;
        }
        PrdSchedulePlan plan = selection.plan();
        List<CriticalScheduleTask> tasks = selection.fingerprintTasks().stream()
            .sorted(scheduleTaskComparator())
            .map(task -> new CriticalScheduleTask(
                task.getTaskId(), task.getWoId(), task.getSeqNum(), task.getStepId(),
                task.getResourceType(), task.getResourceId(), canonicalDecimal(task.getSetupHours()),
                canonicalDecimal(task.getProcessHours()), toScheduleTime(task.getStartTime()),
                toScheduleTime(task.getEndTime()), task.getDurationDays(), task.getDelayFlag(),
                toIsoDateTime(task.getUpdateTime())))
            .toList();
        return new CriticalSchedule(
            plan.getPlanId(), plan.getPlanNo(), plan.getStatus(), plan.getStrategy(),
            toIsoDate(plan.getStartDate()), plan.getHorizonDays(), toIsoDate(plan.getMakespanDate()),
            canonicalDecimal(plan.getTotalHours()), toIsoDateTime(plan.getUpdateTime()),
            plan.getCapacityCalcVersion(), SecureUtil.sha256(JsonUtils.toJsonString(observedSchedule)), tasks);
    }

    private List<StepInfo> routingSteps(ScheduleInput input, Long materialId) {
        if (input.getRoutingProvider() == null) {
            return List.of();
        }
        List<StepInfo> steps = input.getRoutingProvider().apply(materialId);
        return safeList(steps).stream().sorted(stepComparator()).toList();
    }

    private Map<LocalDate, Map<String, BigDecimal>> sortedCapacity(
        Map<LocalDate, Map<String, BigDecimal>> source) {
        LinkedHashMap<LocalDate, Map<String, BigDecimal>> sorted = new LinkedHashMap<>();
        if (source == null) {
            return sorted;
        }
        source.entrySet().stream()
            .filter(entry -> entry.getKey() != null)
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                LinkedHashMap<String, BigDecimal> resources = new LinkedHashMap<>();
                if (entry.getValue() != null) {
                    entry.getValue().entrySet().stream()
                        .filter(resource -> resource.getKey() != null)
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(resource -> resources.put(resource.getKey(), zero(resource.getValue())));
                }
                sorted.put(entry.getKey(), Collections.unmodifiableMap(resources));
            });
        return Collections.unmodifiableMap(sorted);
    }

    private boolean isTerminalWorkOrder(PrdWorkOrder workOrder) {
        return workOrder != null && hasText(workOrder.getWoStatus())
            && TERMINAL_WORK_ORDER_STATUSES.contains(workOrder.getWoStatus().trim().toUpperCase(Locale.ROOT));
    }

    private boolean isSchedulableWorkOrder(PrdWorkOrder workOrder) {
        return workOrder != null && hasText(workOrder.getWoStatus())
            && SCHEDULABLE_WORK_ORDER_STATUSES.contains(
                workOrder.getWoStatus().trim().toUpperCase(Locale.ROOT));
    }

    private static BigDecimal remaining(BigDecimal planned, BigDecimal completed) {
        return nonNegative(subtract(planned, completed));
    }

    private static BigDecimal subtract(BigDecimal minuend, BigDecimal subtrahend) {
        return zero(minuend).subtract(zero(subtrahend));
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String canonicalDecimal(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.signum() == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private static String idString(Long value) {
        return value == null ? null : value.toString();
    }

    private static Date startOfDay(LocalDate value) {
        return Date.from(value.atStartOfDay(BUSINESS_ZONE).toInstant());
    }

    private static LocalDate toLocalDate(Date value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return java.time.Instant.ofEpochMilli(value.getTime())
            .atZone(BUSINESS_ZONE).toLocalDate();
    }

    private static LocalDateTime toLocalDateTime(Date value) {
        if (value == null) {
            return null;
        }
        return java.time.Instant.ofEpochMilli(value.getTime())
            .atZone(BUSINESS_ZONE).toLocalDateTime();
    }

    private static String toIsoDate(Date value) {
        LocalDate date = toLocalDate(value);
        return date == null ? null : date.toString();
    }

    private static String toIsoDateTime(Date value) {
        LocalDateTime dateTime = toLocalDateTime(value);
        return dateTime == null ? null : dateTime.toString();
    }

    private static String toScheduleTime(Date value) {
        if (value instanceof java.sql.Date) {
            return toIsoDate(value);
        }
        return toIsoDateTime(value);
    }

    private static String iso(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static <T> List<List<T>> batches(Collection<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<T> ordered = new ArrayList<>(values);
        List<List<T>> result = new ArrayList<>();
        for (int start = 0; start < ordered.size(); start += IN_BATCH_SIZE) {
            result.add(List.copyOf(ordered.subList(start,
                Math.min(start + IN_BATCH_SIZE, ordered.size()))));
        }
        return List.copyOf(result);
    }

    private static <T> List<T> safeList(List<T> source) {
        return source == null ? List.of() : source;
    }

    private static <T> Collection<T> safeCollection(Collection<T> source) {
        return source == null ? List.of() : source;
    }

    private static <K, V> Map<K, V> immutableLinkedMap(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static Comparator<SoSalesOrderHeader> orderComparator() {
        return Comparator.comparing(SoSalesOrderHeader::getSoId,
            Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<SoSalesOrderLine> orderLineComparator() {
        return Comparator.comparing(SoSalesOrderLine::getLineId,
            Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<PrdWorkOrder> workOrderComparator() {
        return Comparator.comparing(PrdWorkOrder::getWoId,
            Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<PrdProductionPlan> productionPlanComparator() {
        return Comparator.comparing(PrdProductionPlan::getPlanId,
            Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<PrdMaterialRequirement> requirementComparator() {
        return Comparator.comparing(PrdMaterialRequirement::getRequirementId,
            Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<RequirementLink> requirementLinkComparator() {
        return Comparator
            .comparing((RequirementLink row) -> toLocalDate(row.detail().getRequiredDate()),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(row -> row.detail().getMaterialId(),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(row -> row.detail().getDetailId(),
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<WmMaterialStock> stockRowComparator() {
        return Comparator
            .comparing(WmMaterialStock::getMaterialId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(WmMaterialStock::getStockId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<BdMaterial> materialComparator() {
        return Comparator.comparing(BdMaterial::getMaterialId,
            Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<PuPurchaseOrder> purchaseOrderComparator() {
        return Comparator.comparing(PuPurchaseOrder::getOrderId,
            Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<PuPurchaseOrderLine> purchaseLineComparator() {
        return Comparator
            .comparing(PuPurchaseOrderLine::getOrderId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PuPurchaseOrderLine::getLineId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<PuVendorEvaluation> vendorEvaluationComparator() {
        return Comparator
            .comparing(PuVendorEvaluation::getVendorId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PuVendorEvaluation::getEvalPeriod, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(PuVendorEvaluation::getEvaluationId,
                Comparator.nullsFirst(Comparator.naturalOrder()));
    }

    private static Comparator<PrdScheduleTask> scheduleTaskComparator() {
        return Comparator
            .comparing(PrdScheduleTask::getWoId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PrdScheduleTask::getSeqNum, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PrdScheduleTask::getTaskId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<PrdSchedulePlan> schedulePlanLatestComparator() {
        return Comparator
            .comparingLong(DeliveryContextAssembler::planVersionMillis)
            .thenComparing(PrdSchedulePlan::getPlanId, Comparator.nullsFirst(Comparator.naturalOrder()));
    }

    private static long planVersionMillis(PrdSchedulePlan plan) {
        Date version = plan.getUpdateTime() != null ? plan.getUpdateTime() : plan.getCreateTime();
        return version == null ? Long.MIN_VALUE : version.getTime();
    }

    private static Comparator<StepInfo> stepComparator() {
        return Comparator
            .comparing(StepInfo::getSeqNum, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StepInfo::getStepId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StepInfo::getWorkstationId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<BdRouting> routingComparator() {
        return Comparator
            .comparing(BdRouting::getEffectiveDate, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(BdRouting::getUpdateTime, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(BdRouting::getRoutingId, Comparator.nullsFirst(Comparator.naturalOrder()));
    }

    private static Comparator<BdRoutingStep> routingStepSourceComparator() {
        return Comparator
            .comparing(BdRoutingStep::getSequenceNum, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(BdRoutingStep::getStepId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<PrdCapacityCalendar> capacityCalendarComparator() {
        return Comparator
            .comparing((PrdCapacityCalendar row) -> toLocalDate(row.getCalendarDate()),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PrdCapacityCalendar::getObjectType,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PrdCapacityCalendar::getObjectId,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PrdCapacityCalendar::getCalendarId,
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<SchHoliday> holidayComparator() {
        return Comparator
            .comparing((SchHoliday row) -> toLocalDate(row.getHolidayDate()),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SchHoliday::getHolidayId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<SchEmployeeSchedule> employeeScheduleComparator() {
        return Comparator
            .comparing((SchEmployeeSchedule row) -> toLocalDate(row.getScheduledDate()),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SchEmployeeSchedule::getWorkerId,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SchEmployeeSchedule::getScheduleId,
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<SchShift> shiftComparator() {
        return Comparator.comparing(SchShift::getShiftId,
            Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<SchTeam> teamComparator() {
        return Comparator.comparing(SchTeam::getTeamId,
            Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<SchLeaveApply> leaveComparator() {
        return Comparator
            .comparing((SchLeaveApply row) -> toLocalDate(row.getStartDate()),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SchLeaveApply::getWorkerId,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(SchLeaveApply::getLeaveId,
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<BdWorkstation> workstationComparator() {
        return Comparator.comparing(BdWorkstation::getWorkstationId,
            Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static Comparator<CriticalRoutingStep> criticalRoutingComparator() {
        return Comparator
            .comparing(CriticalRoutingStep::materialId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(CriticalRoutingStep::seqNum, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(CriticalRoutingStep::stepId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(CriticalRoutingStep::workstationId,
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private record DecisionScope(
        DecisionBusinessRef normalizedRef,
        List<SoSalesOrderHeader> salesOrders,
        List<SoSalesOrderLine> salesOrderLines,
        List<PrdWorkOrder> workOrders,
        PrdSchedulePlan specifiedSchedulePlan,
        List<PrdScheduleTask> specifiedScheduleTasks,
        Map<Long, Long> workOrderSalesOrderIds
    ) {
        private List<Long> workOrderIds() {
            return workOrders.stream()
                .map(PrdWorkOrder::getWoId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        }
    }

    private record ResolvedSalesContext(
        List<SoSalesOrderHeader> orders,
        List<SoSalesOrderLine> lines,
        Map<Long, Long> workOrderSalesOrderIds
    ) {
    }

    private record RequirementLink(
        PrdMaterialRequirement requirement,
        PrdMaterialRequirementDetail detail,
        PrdWorkOrder workOrder,
        BigDecimal netQuantity
    ) {
    }

    private record StockBundle(List<WmMaterialStock> rows, List<BdMaterial> materials) {
    }

    private record PurchaseBundle(
        List<PuPurchaseOrder> orders,
        List<PuPurchaseOrderLine> lines,
        List<Long> vendorIds
    ) {
    }

    private record ScheduleProvenance(Map<Long, BigDecimal> ledgerHours, List<PrdCapacityCalendar> calendars) { }

    private record ScheduleSelection(
        PrdSchedulePlan plan,
        List<PrdScheduleTask> displayTasks,
        List<PrdScheduleTask> fingerprintTasks
    ) {
    }

    private record SourceFacts(RoutingSourceBundle routing, CapacitySourceBundle capacity) {
        private static SourceFacts empty() {
            return new SourceFacts(new RoutingSourceBundle(List.of()), CapacitySourceBundle.empty());
        }
    }

    private record RoutingSourceBundle(List<RoutingAssignment> assignments) {
    }

    private record RoutingAssignment(Long materialId, BdRouting routing, List<BdRoutingStep> steps) {
    }

    private record CapacitySourceBundle(
        List<PrdCapacityCalendar> calendars,
        List<SchHoliday> holidays,
        List<SchEmployeeSchedule> employeeSchedules,
        List<SchShift> shifts,
        List<SchTeam> teams,
        List<SchLeaveApply> leaves,
        List<BdWorkstation> workstations
    ) {
        private static CapacitySourceBundle empty() {
            return new CapacitySourceBundle(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    private record SnapshotProjection(
        Map<Long, List<RoutingStepFact>> routingByMaterial,
        List<RequirementFact> requirementFacts,
        List<StockFact> stockFacts,
        List<ArrivalFact> arrivalFacts,
        Map<LocalDate, Map<String, BigDecimal>> capacityByDate,
        List<LocalDate> holidays,
        Map<String, Object> currentSchedule,
        List<SupplierMetricFact> supplierMetrics,
        List<String> warnings
    ) {
    }

    private record LoadedContext(
        ScheduleInput apsInput,
        List<RequirementLink> requirementRows,
        List<WmMaterialStock> stockRows,
        List<BdMaterial> materials,
        List<com.nexus.erp.bd.domain.BdUnitOfMeasure> evidenceUnits,
        List<PuPurchaseOrder> purchaseOrders,
        List<PuPurchaseOrderLine> purchaseLines,
        ScheduleSelection schedule,
        SourceFacts sourceFacts,
        Map<Long, List<RoutingStepFact>> routingByMaterial,
        Map<LocalDate, Map<String, BigDecimal>> capacityByDate,
        List<LocalDate> holidays,
        Map<String, Object> currentSchedule,
        List<SupplierMetricFact> supplierMetrics,
        List<String> warnings,
        String tenantId, Date observedAt
    ) {
    }

    private record CriticalFreshnessFingerprint(
        CriticalRootRef root,
        List<CriticalOrder> orders,
        List<CriticalOrderLine> orderLines,
        List<CriticalWorkOrder> workOrders,
        List<CriticalWorkOrderSalesLink> workOrderSalesLinks,
        List<CriticalRequirementHeader> requirementHeaders,
        List<CriticalRequirementDetail> requirements,
        List<CriticalStockRow> stocks,
        List<CriticalMaterial> materials,
        List<CriticalQuantityUnit> quantityUnits,
        List<CriticalPurchaseOrder> purchaseOrders,
        List<CriticalPurchaseLine> purchaseLines,
        CriticalApsInput apsInput,
        CriticalSourceVersions sourceVersions,
        CriticalSchedule currentSchedule
    ) {
    }

    private record CriticalRootRef(String type, Long id, String code) {
    }

    private record CriticalOrder(Long id, String status, String promiseDate, String updateTime) {
    }

    private record CriticalOrderLine(
        Long id,
        Long orderId,
        Long materialId,
        String status,
        String orderedQty,
        String deliveredQty,
        String fulfilledQty,
        String productionQty,
        String purchaseQty,
        String reservedQty,
        String promiseDate,
        String updateTime
    ) {
    }

    private record CriticalWorkOrder(
        Long id,
        String status,
        String planEndDate,
        String remainingQty,
        String updateTime,
        Long planId,
        String sourceType,
        Long sourceId,
        Long sourceLineId,
        String sourceNo
    ) {
    }

    private record CriticalWorkOrderSalesLink(Long workOrderId, Long salesOrderId) {
    }

    private record CriticalRequirementHeader(Long id, Long planId, String status, String updateTime) {
    }

    private record CriticalRequirementDetail(
        Long id,
        Long requirementId,
        Long materialId,
        String requiredDate,
        String netQty,
        String updateTime, String originalUnit
    ) {
    }

    private record CriticalStockRow(
        Long id,
        Long materialId,
        String tenantId,
        String baseUomId,
        Long frozenFlag,
        Long warehouseId,
        Long areaId,
        Long locationId,
        Integer version,
        String delFlag,
        String stockQty,
        String reservedQty,
        String updateTime
    ) {
    }

    /** 根单位数量合同进入同一快照指纹；失效或版本变化使来源不再相同。 */
    private record CriticalQuantityUnit(Long id, String tenant, Long primaryId, Long isBase, Long status, String delFlag,
            String rate, Integer scale, String version, String verifiedAt) { }

    private record CriticalMaterial(Long id, Long baseUomId, Long status, String delFlag, String safetyStock, String updateTime) {
    }

    private record CriticalPurchaseOrder(Long id, String status, String expectedDate, String updateTime) {
    }

    private record CriticalPurchaseLine(
        Long id,
        Long orderId,
        Long materialId,
        String remainingQty,
        String updateTime, String originalUnit, String rawOrdered, String rawReceived
    ) {
    }

    private record CriticalApsInput(
        String startDate,
        int horizonDays,
        String strategy,
        List<CriticalApsWorkOrder> workOrders,
        List<CriticalRoutingStep> routingSteps,
        List<CriticalCapacity> capacityByDate,
        List<String> holidays
    ) {
    }

    private record CriticalApsWorkOrder(
        Long id,
        Long materialId,
        String remainingQty,
        String dueDate,
        Long priority,
        Long workshopId
    ) {
    }

    private record CriticalRoutingStep(
        Long materialId,
        Long stepId,
        Integer seqNum,
        Long workstationId,
        Long workshopId,
        String standardHours,
        String setupHours,
        Integer outsource,
        String waitTime,
        String transferTime
    ) {
    }

    private record CriticalCapacity(String date, String resource, String remainingHours) {
    }

    private record CriticalSourceVersions(
        List<CriticalRoutingSource> routings,
        List<CriticalRoutingStepSource> routingSteps,
        List<CriticalCapacityCalendarSource> capacityCalendars,
        List<CriticalHolidaySource> holidays,
        List<CriticalEmployeeScheduleSource> employeeSchedules,
        List<CriticalShiftSource> shifts,
        List<CriticalTeamSource> teams,
        List<CriticalLeaveSource> leaves,
        List<CriticalWorkstationSource> workstations
    ) {
    }

    private record CriticalRoutingSource(
        Long materialId,
        Long routingId,
        Long routingMaterialId,
        String version,
        Long current,
        Long status,
        String effectiveDate,
        String expiryDate,
        String totalHours,
        String updateTime
    ) {
    }

    private record CriticalRoutingStepSource(
        Long materialId,
        Long routingId,
        Long stepId,
        Integer sequenceNum,
        Long workstationId,
        String standardHours,
        String setupHours,
        Integer outsource,
        String waitTime,
        String transferTime,
        String updateTime
    ) {
    }

    private record CriticalCapacityCalendarSource(
        Long id,
        String objectType,
        Long objectId,
        String date,
        Long shiftId,
        String availableHours,
        String plannedHours,
        String actualHours,
        String efficiency,
        String effectiveHours,
        String remainingHours,
        String status,
        String updateTime
    ) {
    }

    private record CriticalHolidaySource(Long id, String date, Integer workingDay, String updateTime) {
    }

    private record CriticalEmployeeScheduleSource(
        Long id,
        Long workerId,
        Long teamId,
        Long shiftId,
        Long actualShiftId,
        String date,
        Integer workingDay,
        String status,
        String startTime,
        String endTime,
        String updateTime
    ) {
    }

    private record CriticalShiftSource(
        Long id,
        String startTime,
        String endTime,
        String workHours,
        Integer crossDay,
        String restStartTime,
        String restEndTime,
        String updateTime
    ) {
    }

    private record CriticalTeamSource(
        Long id,
        Long workshopId,
        Long status,
        Long memberCount,
        String updateTime
    ) {
    }

    private record CriticalLeaveSource(
        Long id,
        Long workerId,
        String startDate,
        String endDate,
        String status,
        String durationDays,
        String updateTime
    ) {
    }

    private record CriticalWorkstationSource(
        Long id,
        Long workshopId,
        Long status,
        Long operatorCount,
        String standardCapacity,
        String updateTime
    ) {
    }

    private record CriticalSchedule(
        Long planId,
        String planNo,
        String status,
        String strategy,
        String startDate,
        Integer horizonDays,
        String makespanDate,
        String totalHours,
        String updateTime,
        Integer capacityCalcVersion,
        String provenanceHash,
        List<CriticalScheduleTask> tasks
    ) {
    }

    private record CriticalScheduleTask(
        Long taskId,
        Long workOrderId,
        Integer seqNum,
        Long stepId,
        String resourceType,
        Long resourceId,
        String setupHours,
        String processHours,
        String startTime,
        String endTime,
        Integer durationDays,
        Integer delayFlag,
        String updateTime
    ) {
    }
}
