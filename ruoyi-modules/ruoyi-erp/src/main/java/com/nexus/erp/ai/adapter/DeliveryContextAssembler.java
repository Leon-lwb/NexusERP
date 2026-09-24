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
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.core.exception.ServiceException;
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
import com.nexus.erp.prd.domain.PrdSchedulePlan;
import com.nexus.erp.prd.domain.PrdScheduleTask;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo;
import com.nexus.erp.prd.mapper.PrdCapacityCalendarMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.mapper.PrdSchedulePlanMapper;
import com.nexus.erp.prd.mapper.PrdScheduleTaskMapper;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
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

    private static final String ADMIN_PERMISSION = "*:*:*";
    private static final String RUN_PERMISSION = "ai:decision:run";
    private static final Set<String> TERMINAL_WORK_ORDER_STATUSES = Set.of("COMPLETED", "CLOSED");
    private static final Set<String> OPEN_PURCHASE_ORDER_STATUSES =
        Set.of("APPROVED", "PARTIALLY_RECEIVED");

    private final SoSalesOrderHeaderMapper salesOrderHeaderMapper;
    private final SoSalesOrderLineMapper salesOrderLineMapper;
    private final PrdWorkOrderMapper workOrderMapper;
    private final PrdSchedulePlanMapper schedulePlanMapper;
    private final PrdScheduleTaskMapper scheduleTaskMapper;
    private final PrdMaterialRequirementMapper requirementMapper;
    private final PrdMaterialRequirementDetailMapper requirementDetailMapper;
    private final WmMaterialStockMapper stockMapper;
    private final BdMaterialMapper materialMapper;
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

    /**
     * 仅返回当前 actor 是否可读取该精确根对象；非法请求与无权限请求均 fail-closed。
     */
    public boolean exists(DecisionBusinessRef ref, DecisionActor actor) {
        try {
            validateAccess(ref, actor);
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
     * 按调用方 horizon 装配展示快照；snapshotHash 始终使用固定 90 天关键指纹重算。
     */
    public DeliveryContextSnapshot assemble(DecisionBusinessRef ref, int horizonDays, DecisionActor actor) {
        validateHorizon(horizonDays);
        validateAccess(ref, actor);
        LocalDate businessDate = businessDate();
        DecisionScope scope = loadScope(ref);
        LoadedContext context = loadContext(scope, FRESHNESS_HORIZON_DAYS, businessDate);
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
            LocalDateTime.now(),
            projection.warnings());
    }

    /** 固定 90 天重算关键业务指纹；保持包可见供 checker 与同包测试使用。 */
    String computeFreshnessHash(DecisionBusinessRef ref, DecisionActor actor) {
        validateAccess(ref, actor);
        LocalDate businessDate = businessDate();
        DecisionScope scope = loadScope(ref);
        LoadedContext context = loadContext(scope, FRESHNESS_HORIZON_DAYS, businessDate);
        return hash(buildFingerprint(scope, context));
    }

    LocalDate businessDate() {
        return LocalDate.now();
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
        if (!hasPermission(actor.permissions(), RUN_PERMISSION)
            || !hasPermission(actor.permissions(), permissionFor(ref.type()))) {
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
        Set<Long> lineIds = lines.stream()
            .map(SoSalesOrderLine::getLineId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        List<PrdWorkOrder> queried = safeList(workOrderMapper.selectList(
            Wrappers.lambdaQuery(PrdWorkOrder.class)
                .and(query -> {
                    query.eq(PrdWorkOrder::getSourceId, salesOrderId);
                    if (!lineIds.isEmpty()) {
                        query.or().in(PrdWorkOrder::getSourceLineId, lineIds);
                    }
                    if (hasText(order.getSoNo())) {
                        query.or().eq(PrdWorkOrder::getSourceNo, order.getSoNo());
                    }
                })));
        List<PrdWorkOrder> workOrders = queried.stream()
            .filter(workOrder -> Objects.equals(salesOrderId, workOrder.getSourceId())
                || (workOrder.getSourceLineId() != null && lineIds.contains(workOrder.getSourceLineId()))
                || (hasText(order.getSoNo()) && Objects.equals(order.getSoNo(), workOrder.getSourceNo())))
            .sorted(workOrderComparator())
            .toList();
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
        List<SoSalesOrderHeader> orders = resolveSalesOrders(workOrders);
        Map<Long, Long> mapping = mapWorkOrdersToSalesOrders(workOrders, orders);
        return new DecisionScope(
            new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, workOrderId, workOrder.getWoNo()),
            orders, List.of(), workOrders, null, List.of(), mapping);
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
        List<PrdWorkOrder> workOrders = loadExactWorkOrders(workOrderIds);
        List<SoSalesOrderHeader> orders = resolveSalesOrders(workOrders);
        Map<Long, Long> mapping = mapWorkOrdersToSalesOrders(workOrders, orders);
        return new DecisionScope(
            new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, schedulePlanId, plan.getPlanNo()),
            orders, List.of(), workOrders, plan, tasks, mapping);
    }

    private DecisionScope loadShortageScope() {
        List<PrdWorkOrder> workOrders = safeList(workOrderMapper.selectList(
            Wrappers.lambdaQuery(PrdWorkOrder.class)
                .notIn(PrdWorkOrder::getWoStatus, TERMINAL_WORK_ORDER_STATUSES))).stream()
            .filter(workOrder -> !isTerminalWorkOrder(workOrder))
            .sorted(workOrderComparator())
            .toList();
        List<SoSalesOrderHeader> orders = resolveSalesOrders(workOrders);
        Map<Long, Long> mapping = mapWorkOrdersToSalesOrders(workOrders, orders);
        return new DecisionScope(
            new DecisionBusinessRef(DecisionBusinessType.SHORTAGE_HORIZON, null, "SHORTAGE_HORIZON"),
            orders, List.of(), workOrders, null, List.of(), mapping);
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
        List<PrdWorkOrder> rows = safeList(workOrderMapper.selectList(
            Wrappers.lambdaQuery(PrdWorkOrder.class)
                .in(PrdWorkOrder::getWoId, ids)));
        TreeMap<Long, PrdWorkOrder> byId = new TreeMap<>();
        for (PrdWorkOrder row : rows) {
            if (row.getWoId() == null || !ids.contains(row.getWoId())) {
                continue;
            }
            if (byId.put(row.getWoId(), row) != null) {
                throw new ServiceException("工单查询返回重复记录: " + row.getWoId());
            }
        }
        List<Long> missing = ids.stream().filter(id -> !byId.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            throw new ServiceException("工单不存在或不在当前范围: " + missing);
        }
        return List.copyOf(byId.values());
    }

    private List<SoSalesOrderHeader> resolveSalesOrders(List<PrdWorkOrder> workOrders) {
        LinkedHashSet<Long> sourceIds = workOrders.stream()
            .map(PrdWorkOrder::getSourceId)
            .filter(Objects::nonNull)
            .filter(id -> id > 0)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> sourceNumbers = workOrders.stream()
            .map(PrdWorkOrder::getSourceNo)
            .filter(DeliveryContextAssembler::hasText)
            .map(String::trim)
            .sorted()
            .collect(Collectors.toCollection(LinkedHashSet::new));
        TreeMap<Long, SoSalesOrderHeader> resolved = new TreeMap<>();

        if (sourceIds.size() == 1) {
            SoSalesOrderHeader order = salesOrderHeaderMapper.selectById(sourceIds.iterator().next());
            if (order != null && order.getSoId() != null && sourceIds.contains(order.getSoId())) {
                resolved.put(order.getSoId(), order);
            }
        } else if (!sourceIds.isEmpty()) {
            for (SoSalesOrderHeader order : safeList(salesOrderHeaderMapper.selectBatchIds(sourceIds))) {
                if (order.getSoId() != null && sourceIds.contains(order.getSoId())) {
                    resolved.put(order.getSoId(), order);
                }
            }
        }
        if (!sourceNumbers.isEmpty()) {
            List<SoSalesOrderHeader> byNumber = safeList(salesOrderHeaderMapper.selectList(
                Wrappers.lambdaQuery(SoSalesOrderHeader.class)
                    .in(SoSalesOrderHeader::getSoNo, sourceNumbers)));
            for (SoSalesOrderHeader order : byNumber) {
                if (order.getSoId() != null && sourceNumbers.contains(order.getSoNo())) {
                    resolved.putIfAbsent(order.getSoId(), order);
                }
            }
        }
        return List.copyOf(resolved.values());
    }

    private Map<Long, Long> mapWorkOrdersToSalesOrders(List<PrdWorkOrder> workOrders,
                                                        List<SoSalesOrderHeader> orders) {
        Map<Long, SoSalesOrderHeader> byId = orders.stream()
            .filter(order -> order.getSoId() != null)
            .collect(Collectors.toMap(SoSalesOrderHeader::getSoId, order -> order, (left, right) -> left));
        Map<String, SoSalesOrderHeader> byNumber = orders.stream()
            .filter(order -> hasText(order.getSoNo()))
            .collect(Collectors.toMap(SoSalesOrderHeader::getSoNo, order -> order, (left, right) -> left));
        LinkedHashMap<Long, Long> mapping = new LinkedHashMap<>();
        workOrders.stream().sorted(workOrderComparator()).forEach(workOrder -> {
            SoSalesOrderHeader order = workOrder.getSourceId() == null ? null : byId.get(workOrder.getSourceId());
            if (order == null && hasText(workOrder.getSourceNo())) {
                order = byNumber.get(workOrder.getSourceNo());
            }
            if (order != null && workOrder.getWoId() != null) {
                mapping.put(workOrder.getWoId(), order.getSoId());
            }
        });
        return immutableLinkedMap(mapping);
    }

    private LoadedContext loadContext(DecisionScope scope, int horizonDays, LocalDate businessDate) {
        LocalDate horizonEnd = businessDate.plusDays(horizonDays - 1L);
        ScheduleInput apsInput = assembleScheduleInput(scope.workOrders(), horizonDays, businessDate);
        SourceFacts sourceFacts = loadSourceFacts(scope, apsInput, businessDate, horizonEnd);
        List<String> warnings = new ArrayList<>();
        if (scope.workOrders().isEmpty()) {
            warnings.add("NO_WORK_ORDER: 当前决策范围没有生产工单");
        }

        List<RequirementLink> requirementRows = loadRequirementRows(scope, warnings);
        LinkedHashSet<Long> materialIds = collectRelevantMaterialIds(scope, requirementRows);
        StockBundle stocks = loadStocks(materialIds);
        PurchaseBundle purchases = loadPurchases(materialIds, warnings);
        List<SupplierMetricFact> supplierMetrics = loadSupplierMetrics(purchases.vendorIds(), warnings);
        ScheduleSelection schedule = loadCurrentSchedule(scope, warnings);

        Map<Long, List<RoutingStepFact>> routingByMaterial = buildRoutingFacts(scope, apsInput, sourceFacts);
        Map<LocalDate, Map<String, BigDecimal>> capacityByDate = sortedCapacity(apsInput.getCapacityByDate());
        List<LocalDate> holidays = safeCollection(apsInput.getHolidays()).stream().sorted().toList();
        Map<String, Object> currentSchedule = buildCurrentSchedule(scope, schedule);

        return new LoadedContext(
            apsInput,
            requirementRows,
            stocks.rows(),
            stocks.materials(),
            purchases.orders(),
            purchases.lines(),
            schedule,
            sourceFacts,
            routingByMaterial,
            capacityByDate,
            holidays,
            currentSchedule,
            supplierMetrics,
            List.copyOf(warnings));
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
        List<ArrivalFact> arrivals = buildArrivalFacts(purchases, materialIds, horizonEnd);

        LinkedHashMap<LocalDate, Map<String, BigDecimal>> capacity = new LinkedHashMap<>();
        context.capacityByDate().forEach((date, resources) -> {
            if (!date.isBefore(businessDate) && !date.isAfter(horizonEnd)) {
                capacity.put(date, resources);
            }
        });
        List<LocalDate> holidays = context.holidays().stream()
            .filter(date -> !date.isBefore(businessDate) && !date.isAfter(horizonEnd))
            .toList();
        List<String> stableWarnings = warnings.stream()
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .toList();
        return new SnapshotProjection(
            context.routingByMaterial(),
            buildRequirementFacts(requirementRows),
            buildStockFacts(materialIds, stocks),
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
        List<PrdMaterialRequirement> requirements = safeList(requirementMapper.selectList(
            Wrappers.lambdaQuery(PrdMaterialRequirement.class)
                .in(PrdMaterialRequirement::getPlanId, planIds))).stream()
            .filter(requirement -> requirement.getRequirementId() != null)
            .filter(requirement -> planIds.contains(requirement.getPlanId()))
            .sorted(requirementComparator())
            .toList();
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

        List<RequirementLink> links = new ArrayList<>();
        for (PrdMaterialRequirementDetail detail : safeList(requirementDetailMapper.selectList(
            Wrappers.lambdaQuery(PrdMaterialRequirementDetail.class)
                .in(PrdMaterialRequirementDetail::getRequirementId, requirementIds)))) {
            PrdMaterialRequirement requirement = requirementById.get(detail.getRequirementId());
            if (requirement == null || detail.getDetailId() == null) {
                continue;
            }
            List<PrdWorkOrder> candidates = safeList(workOrdersByPlan.get(requirement.getPlanId())).stream()
                .sorted(workOrderComparator())
                .toList();
            PrdWorkOrder linkedWorkOrder = candidates.size() == 1 ? candidates.get(0) : null;
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
        List<WmMaterialStock> rows = safeList(stockMapper.selectList(
            Wrappers.lambdaQuery(WmMaterialStock.class)
                .in(WmMaterialStock::getMaterialId, materialIds))).stream()
            .filter(row -> row.getMaterialId() != null && materialIds.contains(row.getMaterialId()))
            .sorted(stockRowComparator())
            .toList();
        List<BdMaterial> materials = safeList(materialMapper.selectBatchIds(materialIds)).stream()
            .filter(material -> material.getMaterialId() != null && materialIds.contains(material.getMaterialId()))
            .sorted(materialComparator())
            .toList();
        return new StockBundle(rows, materials);
    }

    private PurchaseBundle loadPurchases(LinkedHashSet<Long> materialIds,
                                          List<String> warnings) {
        if (materialIds.isEmpty()) {
            return new PurchaseBundle(List.of(), List.of(), List.of());
        }
        List<PuPurchaseOrderLine> candidateLines = safeList(purchaseOrderLineMapper.selectList(
            Wrappers.lambdaQuery(PuPurchaseOrderLine.class)
                .in(PuPurchaseOrderLine::getMaterialId, materialIds))).stream()
            .filter(line -> line.getLineId() != null)
            .filter(line -> line.getMaterialId() != null && materialIds.contains(line.getMaterialId()))
            .filter(line -> remaining(line.getOrderQuantity(), line.getReceivedQuantity()).signum() > 0)
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
        List<PuPurchaseOrder> orders = safeList(purchaseOrderMapper.selectList(
            Wrappers.lambdaQuery(PuPurchaseOrder.class)
                .in(PuPurchaseOrder::getOrderId, orderIds)
                .in(PuPurchaseOrder::getStatus, OPEN_PURCHASE_ORDER_STATUSES))).stream()
            .filter(order -> order.getOrderId() != null && orderIds.contains(order.getOrderId()))
            .filter(order -> OPEN_PURCHASE_ORDER_STATUSES.contains(order.getStatus()))
            .sorted(purchaseOrderComparator())
            .toList();
        Set<Long> validOrderIds = orders.stream()
            .map(PuPurchaseOrder::getOrderId)
            .collect(Collectors.toSet());
        List<PuPurchaseOrderLine> lines = candidateLines.stream()
            .filter(line -> validOrderIds.contains(line.getOrderId()))
            .toList();
        Set<Long> orderIdsWithLines = lines.stream()
            .map(PuPurchaseOrderLine::getOrderId)
            .collect(Collectors.toSet());
        List<PuPurchaseOrder> relatedOrders = orders.stream()
            .filter(order -> orderIdsWithLines.contains(order.getOrderId()))
            .toList();
        Map<Long, PuPurchaseOrder> orderById = relatedOrders.stream()
            .collect(Collectors.toMap(PuPurchaseOrder::getOrderId, order -> order));
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

    private ScheduleSelection loadCurrentSchedule(DecisionScope scope, List<String> warnings) {
        if (scope.workOrders().isEmpty()) {
            return new ScheduleSelection(null, List.of());
        }
        if (scope.specifiedSchedulePlan() != null) {
            List<PrdScheduleTask> tasks = scope.specifiedScheduleTasks().stream()
                .filter(task -> Objects.equals(scope.specifiedSchedulePlan().getPlanId(), task.getPlanId()))
                .filter(task -> scope.workOrderIds().contains(task.getWoId()))
                .sorted(scheduleTaskComparator())
                .toList();
            return new ScheduleSelection(scope.specifiedSchedulePlan(), tasks);
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
            return new ScheduleSelection(null, List.of());
        }
        List<PrdSchedulePlan> plans = safeList(schedulePlanMapper.selectBatchIds(planIds)).stream()
            .filter(plan -> plan.getPlanId() != null && planIds.contains(plan.getPlanId()))
            .toList();
        PrdSchedulePlan latest = plans.stream().max(schedulePlanLatestComparator()).orElse(null);
        if (latest == null) {
            warnings.add("当前决策范围的排产任务缺少对应排产方案");
            return new ScheduleSelection(null, List.of());
        }
        List<PrdScheduleTask> selectedTasks = tasks.stream()
            .filter(task -> Objects.equals(latest.getPlanId(), task.getPlanId()))
            .sorted(scheduleTaskComparator())
            .toList();
        return new ScheduleSelection(latest, selectedTasks);
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

        List<BdWorkstation> workstations = loadSourceWorkstations(workstationIds, workshopIds);
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
                    .ge(PrdCapacityCalendar::getCalendarDate, java.sql.Date.valueOf(startDate))
                    .le(PrdCapacityCalendar::getCalendarDate, java.sql.Date.valueOf(endDate))
                    .in(PrdCapacityCalendar::getObjectId, capacityObjectIds))).stream()
                .filter(calendar -> isRelatedCapacity(
                    calendar, workshopIds, workstationIds, productionLineIds, startDate, endDate))
                .sorted(capacityCalendarComparator())
                .toList();

        List<SchHoliday> holidays = safeList(holidayMapper.selectList(
            Wrappers.lambdaQuery(SchHoliday.class)
                .ge(SchHoliday::getHolidayDate, java.sql.Date.valueOf(startDate))
                .le(SchHoliday::getHolidayDate, java.sql.Date.valueOf(endDate)))).stream()
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
                    .ge(SchEmployeeSchedule::getScheduledDate, java.sql.Date.valueOf(startDate))
                    .le(SchEmployeeSchedule::getScheduledDate, java.sql.Date.valueOf(endDate)))).stream()
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
                    .le(SchLeaveApply::getStartDate, java.sql.Date.valueOf(endDate))
                    .ge(SchLeaveApply::getEndDate, java.sql.Date.valueOf(startDate)))).stream()
                .filter(leave -> workerIds.contains(leave.getWorkerId()))
                .filter(leave -> "approved".equalsIgnoreCase(leave.getApplyStatus()))
                .filter(leave -> overlaps(leave.getStartDate(), leave.getEndDate(), startDate, endDate))
                .sorted(leaveComparator())
                .toList();
        return new CapacitySourceBundle(
            calendars, holidays, schedules, shifts, teams, leaves, workstations);
    }

    private List<BdWorkstation> loadSourceWorkstations(Set<Long> workstationIds, Set<Long> workshopIds) {
        if (workstationIds.isEmpty() && workshopIds.isEmpty()) {
            return List.of();
        }
        var query = Wrappers.lambdaQuery(BdWorkstation.class);
        if (!workstationIds.isEmpty() && !workshopIds.isEmpty()) {
            query.and(nested -> nested.in(BdWorkstation::getWorkstationId, workstationIds)
                .or().in(BdWorkstation::getWorkshopId, workshopIds));
        } else if (!workstationIds.isEmpty()) {
            query.in(BdWorkstation::getWorkstationId, workstationIds);
        } else {
            query.in(BdWorkstation::getWorkshopId, workshopIds);
        }
        return safeList(workstationMapper.selectList(query)).stream()
            .filter(workstation -> workstation.getWorkstationId() != null)
            .filter(workstation -> workstationIds.contains(workstation.getWorkstationId())
                || workshopIds.contains(workstation.getWorkshopId()))
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
                            step.getStandardHours());
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
                        step.getStandardHours()))
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

    private List<RequirementFact> buildRequirementFacts(List<RequirementLink> rows) {
        return rows.stream()
            .filter(row -> row.netQuantity().signum() > 0)
            .map(row -> new RequirementFact(
                row.detail().getMaterialId(),
                row.detail().getMaterialCode(),
                row.detail().getMaterialName(),
                toLocalDate(row.detail().getRequiredDate()),
                row.netQuantity(),
                row.workOrder() == null ? null : row.workOrder().getWoId(),
                row.workOrder() == null ? null : row.workOrder().getWoNo()))
            .toList();
    }

    private List<StockFact> buildStockFacts(LinkedHashSet<Long> materialIds, StockBundle stocks) {
        Map<Long, BdMaterial> materialById = stocks.materials().stream()
            .collect(Collectors.toMap(BdMaterial::getMaterialId, material -> material, (left, right) -> left));
        Map<Long, BigDecimal> stockByMaterial = new TreeMap<>();
        Map<Long, BigDecimal> reservedByMaterial = new TreeMap<>();
        materialIds.forEach(materialId -> {
            stockByMaterial.put(materialId, BigDecimal.ZERO);
            reservedByMaterial.put(materialId, BigDecimal.ZERO);
        });
        for (WmMaterialStock row : stocks.rows()) {
            stockByMaterial.merge(row.getMaterialId(), zero(row.getStockQuantity()), BigDecimal::add);
            reservedByMaterial.merge(row.getMaterialId(), zero(row.getReservedQuantity()), BigDecimal::add);
        }
        List<StockFact> facts = new ArrayList<>();
        for (Long materialId : materialIds) {
            BdMaterial material = materialById.get(materialId);
            facts.add(new StockFact(
                materialId,
                stockByMaterial.getOrDefault(materialId, BigDecimal.ZERO),
                reservedByMaterial.getOrDefault(materialId, BigDecimal.ZERO),
                material == null ? BigDecimal.ZERO : zero(material.getSafetyStock())));
        }
        return List.copyOf(facts);
    }

    private List<ArrivalFact> buildArrivalFacts(PurchaseBundle purchases,
                                                 Set<Long> materialIds,
                                                 LocalDate horizonEnd) {
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
            arrivals.add(new ArrivalFact(
                line.getMaterialId(), line.getLineId(), expectedDate, remaining, order.getVendorId()));
        }
        arrivals.sort(Comparator
            .comparing(ArrivalFact::expectedDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ArrivalFact::materialId, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(ArrivalFact::purchaseLineId, Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(arrivals);
    }

    private Map<String, Object> buildCurrentSchedule(DecisionScope scope, ScheduleSelection selection) {
        PrdSchedulePlan plan = selection.plan();
        if (plan == null) {
            return new LinkedHashMap<>();
        }
        LinkedHashMap<String, Object> schedule = new LinkedHashMap<>();
        schedule.put("planId", idString(plan.getPlanId()));
        schedule.put("planNo", plan.getPlanNo());
        schedule.put("status", plan.getStatus());
        schedule.put("strategy", plan.getStrategy());
        schedule.put("startDate", toIsoDate(plan.getStartDate()));
        schedule.put("horizonDays", plan.getHorizonDays());
        schedule.put("makespanDate", toIsoDate(plan.getMakespanDate()));
        schedule.put("totalHours", decimal(plan.getTotalHours()));

        List<String> workOrderIds = selection.tasks().stream()
            .map(PrdScheduleTask::getWoId)
            .filter(Objects::nonNull)
            .distinct()
            .sorted()
            .map(String::valueOf)
            .toList();
        schedule.put("workOrderIds", workOrderIds);

        List<Map<String, Object>> taskPreview = selection.tasks().stream()
            .sorted(scheduleTaskComparator())
            .map(this::scheduleTaskMap)
            .toList();
        schedule.put("tasks", taskPreview);

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
                decimal(line.getOrderedQty()), decimal(line.getDeliveredQty()),
                decimal(line.getFulfilledQty()), decimal(line.getProductionQty()),
                decimal(line.getPurchaseQty()), decimal(line.getReservedQty()),
                toIsoDate(line.getPromiseDate()), toIsoDateTime(line.getUpdateTime())))
            .toList();
        List<CriticalWorkOrder> workOrders = scope.workOrders().stream()
            .sorted(workOrderComparator())
            .map(workOrder -> new CriticalWorkOrder(
                workOrder.getWoId(), workOrder.getWoStatus(), toIsoDate(workOrder.getPlanEndDate()),
                decimal(remaining(workOrder.getPlannedQty(), workOrder.getCompletedQty())),
                toIsoDateTime(workOrder.getUpdateTime())))
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
                toIsoDate(row.detail().getRequiredDate()), decimal(row.netQuantity()),
                toIsoDateTime(row.detail().getUpdateTime())))
            .toList();
        List<CriticalStockRow> stocks = context.stockRows().stream()
            .sorted(stockRowComparator())
            .map(row -> new CriticalStockRow(
                row.getStockId(), row.getMaterialId(), decimal(zero(row.getStockQuantity())),
                decimal(zero(row.getReservedQuantity())), toIsoDateTime(row.getUpdateTime())))
            .toList();
        List<CriticalMaterial> materials = context.materials().stream()
            .sorted(materialComparator())
            .map(material -> new CriticalMaterial(
                material.getMaterialId(), decimal(zero(material.getSafetyStock())),
                toIsoDateTime(material.getUpdateTime())))
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
                decimal(remaining(line.getOrderQuantity(), line.getReceivedQuantity())),
                toIsoDateTime(line.getUpdateTime())))
            .toList();

        CriticalApsInput apsInput = buildCriticalApsInput(scope, context.apsInput());
        CriticalSourceVersions sourceVersions = buildCriticalSourceVersions(context.sourceFacts());
        CriticalSchedule currentSchedule = buildCriticalSchedule(context.schedule());
        return new CriticalFreshnessFingerprint(
            root, orders, orderLines, workOrders, requirementHeaders, requirements,
            stocks, materials, purchaseOrders, purchaseLines, apsInput, sourceVersions, currentSchedule);
    }

    private CriticalApsInput buildCriticalApsInput(DecisionScope scope, ScheduleInput input) {
        List<CriticalApsWorkOrder> workOrders = safeList(input.getWorkOrders()).stream()
            .sorted(Comparator.comparing(WoInfo::getWoId, Comparator.nullsLast(Comparator.naturalOrder())))
            .map(workOrder -> new CriticalApsWorkOrder(
                workOrder.getWoId(), workOrder.getMaterialId(), decimal(workOrder.getRemainingQty()),
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
                    decimal(step.getStandardHours()), decimal(step.getSetupHours()), step.getIsOutsource(),
                    decimal(step.getWaitTime()), decimal(step.getTransferTime())));
            }
        }
        routingSteps.sort(criticalRoutingComparator());

        List<CriticalCapacity> capacity = new ArrayList<>();
        sortedCapacity(input.getCapacityByDate()).forEach((date, resources) ->
            resources.forEach((resource, hours) ->
                capacity.add(new CriticalCapacity(date.toString(), resource, decimal(hours)))));
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
                    decimal(routing.getTotalHours()), toIsoDateTime(routing.getUpdateTime()));
            })
            .toList();
        List<CriticalRoutingStepSource> routingSteps = sourceFacts.routing().assignments().stream()
            .flatMap(assignment -> assignment.steps().stream()
                .map(step -> new CriticalRoutingStepSource(
                    assignment.materialId(), step.getRoutingId(), step.getStepId(),
                    step.getSequenceNum(), step.getWorkstationId(), decimal(step.getStandardHours()),
                    decimal(step.getSetupHours()), step.getIsOutsource(), decimal(step.getWaitTime()),
                    decimal(step.getTransferTime()), toIsoDateTime(step.getUpdateTime()))))
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
                toIsoDate(row.getCalendarDate()), row.getShiftId(), decimal(row.getAvailableHours()),
                decimal(row.getPlannedHours()), decimal(row.getActualHours()),
                decimal(row.getEfficiency()), decimal(row.getEffectiveHours()),
                decimal(row.getRemainingHours()), row.getStatus(), toIsoDateTime(row.getUpdateTime())))
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
                decimal(row.getWorkHours()), row.getIsCrossDay(),
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
                toIsoDate(row.getEndDate()), row.getApplyStatus(), decimal(row.getDurationDays()),
                toIsoDateTime(row.getUpdateTime())))
            .toList();
        List<CriticalWorkstationSource> workstations = capacity.workstations().stream()
            .sorted(workstationComparator())
            .map(row -> new CriticalWorkstationSource(
                row.getWorkstationId(), row.getWorkshopId(), row.getStatus(),
                row.getOperatorCount(), decimal(row.getStandardCapacity()),
                toIsoDateTime(row.getUpdateTime())))
            .toList();
        return new CriticalSourceVersions(
            routings, routingSteps, calendars, holidays, employeeSchedules,
            shifts, teams, leaves, workstations);
    }

    private CriticalSchedule buildCriticalSchedule(ScheduleSelection selection) {
        if (selection.plan() == null) {
            return null;
        }
        PrdSchedulePlan plan = selection.plan();
        List<CriticalScheduleTask> tasks = selection.tasks().stream()
            .sorted(scheduleTaskComparator())
            .map(task -> new CriticalScheduleTask(
                task.getTaskId(), task.getWoId(), task.getSeqNum(), task.getStepId(),
                task.getResourceType(), task.getResourceId(), decimal(task.getSetupHours()),
                decimal(task.getProcessHours()), toScheduleTime(task.getStartTime()),
                toScheduleTime(task.getEndTime()), task.getDurationDays(), task.getDelayFlag(),
                toIsoDateTime(task.getUpdateTime())))
            .toList();
        return new CriticalSchedule(
            plan.getPlanId(), plan.getPlanNo(), plan.getStatus(), plan.getStrategy(),
            toIsoDate(plan.getStartDate()), plan.getHorizonDays(), toIsoDate(plan.getMakespanDate()),
            decimal(plan.getTotalHours()), toIsoDateTime(plan.getUpdateTime()), tasks);
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

    private static String idString(Long value) {
        return value == null ? null : value.toString();
    }

    private static LocalDate toLocalDate(Date value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return value.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static LocalDateTime toLocalDateTime(Date value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate().atStartOfDay();
        }
        return value.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
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
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate().toString();
        }
        return toIsoDateTime(value);
    }

    private static String iso(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
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

    private record ScheduleSelection(PrdSchedulePlan plan, List<PrdScheduleTask> tasks) {
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
        List<PuPurchaseOrder> purchaseOrders,
        List<PuPurchaseOrderLine> purchaseLines,
        ScheduleSelection schedule,
        SourceFacts sourceFacts,
        Map<Long, List<RoutingStepFact>> routingByMaterial,
        Map<LocalDate, Map<String, BigDecimal>> capacityByDate,
        List<LocalDate> holidays,
        Map<String, Object> currentSchedule,
        List<SupplierMetricFact> supplierMetrics,
        List<String> warnings
    ) {
    }

    private record CriticalFreshnessFingerprint(
        CriticalRootRef root,
        List<CriticalOrder> orders,
        List<CriticalOrderLine> orderLines,
        List<CriticalWorkOrder> workOrders,
        List<CriticalRequirementHeader> requirementHeaders,
        List<CriticalRequirementDetail> requirements,
        List<CriticalStockRow> stocks,
        List<CriticalMaterial> materials,
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
        String updateTime
    ) {
    }

    private record CriticalRequirementHeader(Long id, Long planId, String status, String updateTime) {
    }

    private record CriticalRequirementDetail(
        Long id,
        Long requirementId,
        Long materialId,
        String requiredDate,
        String netQty,
        String updateTime
    ) {
    }

    private record CriticalStockRow(
        Long id,
        Long materialId,
        String stockQty,
        String reservedQty,
        String updateTime
    ) {
    }

    private record CriticalMaterial(Long id, String safetyStock, String updateTime) {
    }

    private record CriticalPurchaseOrder(Long id, String status, String expectedDate, String updateTime) {
    }

    private record CriticalPurchaseLine(
        Long id,
        Long orderId,
        Long materialId,
        String remainingQty,
        String updateTime
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
