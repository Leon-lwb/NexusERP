package com.nexus.erp.ai.adapter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对调用方认证的单租户、单物料全局池执行无副作用的净需求分配。
 * 此处不执行数据库读取、事务、锁定、单位换算、授权或预占。
 * 尤其要注意，核验标志是 ERP 调用方提供的声明，并非数据库证据。
 */
public final class ErpPurchaseDemandAllocator {
    public enum OrderStatus { DRAFT, APPROVED, PARTIALLY_RECEIVED }
    public enum CommitmentKind { PENDING, LEGACY_OPEN }
    public enum RejectionReason { INCOMPLETE_POOL, UNVERIFIED_SOURCE, INVALID_QUANTITY, DUPLICATE_SOURCE, OVERCOMMITTED }

    public static final class AllocationRejectedException extends IllegalArgumentException {
        private final RejectionReason reason;

        public AllocationRejectedException(RejectionReason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public RejectionReason reason() {
            return reason;
        }
    }

    /** 请求标识仅表示可见范围；其他所有需求仍参与供给竞争。 */
    public record Scope(String tenantId, String materialId, String baseUnitId, LocalDate snapshotDate,
                        LocalDate windowEndDate, boolean globalPoolComplete, Set<String> requestedDetailIds) {
        public Scope {
            requestedDetailIds = Set.copyOf(requestedDetailIds);
        }
    }

    /** 已入账的收货量必须已体现在数量中，不得再次作为订单供给传入。 */
    public record Stock(BigDecimal quantity, BigDecimal reserved, BigDecimal safety,
                        String baseUnitId, boolean verified) { }

    /** netDemandBase 已由调用方独立核验，等于 max(0, required - issued)。 */
    public record Demand(String detailId, LocalDate dueDate, BigDecimal netDemandBase,
                         String baseUnitId, boolean verified) { }

    /** sourceKey 是规范的来源键，并与任何尚未转单的申请意向共用。 */
    public record OrderLine(String orderLineId, String sourceKey, String detailId, OrderStatus status,
                            LocalDate expectedDate, BigDecimal orderedBase, BigDecimal receivedBase,
                            String baseUnitId, boolean verified) { }

    /** 此处仅接收仍然开放且尚未转单的申请意向。 */
    public record Commitment(String sourceKey, String detailId, CommitmentKind kind,
                             BigDecimal remainingBase, String baseUnitId, boolean verified) { }

    public record AllocationInput(Scope scope, Stock stock, List<Demand> demands,
                                  List<OrderLine> orders, List<Commitment> commitments) {
        public AllocationInput {
            demands = List.copyOf(demands);
            orders = List.copyOf(orders);
            commitments = List.copyOf(commitments);
        }
    }

    /** uncovered 和 requestable 仅表示当前尚未完成的采购，不表示历史上的准时履约情况。 */
    public record DetailAllocation(String detailId, LocalDate dueDate, boolean originallyOverdue,
                                   BigDecimal netDemandBase, BigDecimal stockAllocatedBase,
                                   BigDecimal timelyOrderAllocatedBase, BigDecimal uncoveredBase,
                                   BigDecimal committedBase, BigDecimal requestableBase) {
        public BigDecimal eligibleSupplyBase() {
            return stockAllocatedBase.add(timelyOrderAllocatedBase);
        }
    }

    /** 绝不返回范围外的明细标识，仅返回它们消耗的共享供给汇总量。 */
    public record AllocationResult(List<DetailAllocation> allocations, BigDecimal externalDemandBase,
                                   BigDecimal externalSupplyAllocatedBase) {
        public AllocationResult {
            allocations = List.copyOf(allocations);
        }
    }

    private static final Comparator<Demand> DEMAND_ORDER = Comparator.comparing(Demand::dueDate)
        .thenComparing(Demand::detailId, ErpPurchaseDemandAllocator::compareDecimalId);
    private static final Comparator<OrderLine> ORDER_PRIORITY = Comparator.comparing(OrderLine::expectedDate)
        .thenComparing(OrderLine::orderLineId, ErpPurchaseDemandAllocator::compareDecimalId);

    public AllocationResult allocate(AllocationInput input) {
        if (input == null || input.scope() == null || input.stock() == null) {
            throw reject(RejectionReason.UNVERIFIED_SOURCE, "Missing allocation snapshot");
        }
        Scope scope = input.scope();
        if (!scope.globalPoolComplete() || blank(scope.tenantId()) || blank(scope.materialId())
            || blank(scope.baseUnitId()) || scope.snapshotDate() == null || scope.windowEndDate() == null
            || scope.windowEndDate().isBefore(scope.snapshotDate())) {
            throw reject(RejectionReason.INCOMPLETE_POOL, "Global tenant/material window not certified");
        }
        Stock stock = input.stock();
        requireVerified(stock.verified(), stock.baseUnitId(), scope.baseUnitId());
        requireNonnegative(stock.quantity(), stock.reserved(), stock.safety());

        List<Demand> demands = new ArrayList<>(input.demands());
        Map<String, Demand> byId = new HashMap<>();
        for (Demand demand : demands) {
            if (demand == null || !decimalId(demand.detailId()) || demand.dueDate() == null) {
                throw reject(RejectionReason.UNVERIFIED_SOURCE, "Unattributed demand or date");
            }
            requireVerified(demand.verified(), demand.baseUnitId(), scope.baseUnitId());
            requireNonnegative(demand.netDemandBase());
            if (demand.dueDate().isAfter(scope.windowEndDate())) {
                throw reject(RejectionReason.INCOMPLETE_POOL, "Demand falls outside certified window");
            }
            if (byId.putIfAbsent(demand.detailId(), demand) != null) {
                throw reject(RejectionReason.DUPLICATE_SOURCE, "Duplicate demand detail");
            }
        }
        if (!byId.keySet().containsAll(scope.requestedDetailIds())) {
            throw reject(RejectionReason.INCOMPLETE_POOL, "Requested detail missing from global pool");
        }
        demands.sort(DEMAND_ORDER);

        /** 每个实际申请或订单来源只能是未结意向或订单之一，绝不能同时计入两者。 */
        Set<String> sourceKeys = new HashSet<>();
        Set<String> orderLineIds = new HashSet<>();
        Map<String, BigDecimal> blockedByDetail = new HashMap<>();
        List<OrderLine> timelyOrders = new ArrayList<>();
        Map<String, BigDecimal> remainingByOrder = new HashMap<>();
        Map<String, BigDecimal> allocatedAwayFromOrigin = new HashMap<>();

        for (Commitment commitment : input.commitments()) {
            if (commitment == null || blank(commitment.sourceKey()) || commitment.kind() == null) {
                throw reject(RejectionReason.UNVERIFIED_SOURCE, "Unknown application provenance or status");
            }
            requireVerified(commitment.verified(), commitment.baseUnitId(), scope.baseUnitId());
            requireOrigin(byId, commitment.detailId());
            requirePositive(commitment.remainingBase());
            if (!sourceKeys.add(commitment.sourceKey())) {
                throw reject(RejectionReason.DUPLICATE_SOURCE, "Application counted more than once");
            }
            blockedByDetail.merge(commitment.detailId(), commitment.remainingBase(), BigDecimal::add);
        }
        for (OrderLine order : input.orders()) {
            if (order == null || !decimalId(order.orderLineId()) || blank(order.sourceKey())
                || order.status() == null || order.expectedDate() == null) {
                throw reject(RejectionReason.UNVERIFIED_SOURCE, "Unknown order provenance, status or ETA");
            }
            requireVerified(order.verified(), order.baseUnitId(), scope.baseUnitId());
            Demand origin = requireOrigin(byId, order.detailId());
            requirePositive(order.orderedBase());
            requireNonnegative(order.receivedBase());
            if (order.receivedBase().compareTo(order.orderedBase()) > 0
                || (order.status() == OrderStatus.PARTIALLY_RECEIVED
                    && (order.receivedBase().signum() == 0 || order.receivedBase().compareTo(order.orderedBase()) == 0))
                || (order.status() != OrderStatus.PARTIALLY_RECEIVED && order.receivedBase().signum() > 0)) {
                throw reject(RejectionReason.INVALID_QUANTITY, "Inconsistent order receipt and status");
            }
            if (!orderLineIds.add(order.orderLineId()) || !sourceKeys.add(order.sourceKey())) {
                throw reject(RejectionReason.DUPLICATE_SOURCE, "Order and/or application counted more than once");
            }
            BigDecimal remaining = order.orderedBase().subtract(order.receivedBase());
            /**
             * 不得向逾期需求追溯承诺供给。即使另一明细的需求日期更晚，迟到订单或草稿订单仍须
             * 保留原明细作为阻止重复采购的承诺。
             */
            if (order.status() != OrderStatus.DRAFT
                && !origin.dueDate().isBefore(scope.snapshotDate())
                && !order.expectedDate().isBefore(scope.snapshotDate())
                && !order.expectedDate().isAfter(origin.dueDate())) {
                timelyOrders.add(order);
                remainingByOrder.put(order.orderLineId(), remaining);
            } else {
                blockedByDetail.merge(order.detailId(), remaining, BigDecimal::add);
            }
        }
        timelyOrders.sort(ORDER_PRIORITY);

        BigDecimal availableStock = stock.quantity().subtract(stock.reserved()).subtract(stock.safety()).max(BigDecimal.ZERO);
        List<DetailAllocation> visible = new ArrayList<>();
        Map<String, DetailAllocation> all = new HashMap<>();
        BigDecimal externalDemand = BigDecimal.ZERO;
        BigDecimal externalSupply = BigDecimal.ZERO;
        for (Demand demand : demands) {
            BigDecimal outstanding = demand.netDemandBase();
            BigDecimal allocatedStock = availableStock.min(outstanding);
            availableStock = availableStock.subtract(allocatedStock);
            outstanding = outstanding.subtract(allocatedStock);
            BigDecimal allocatedOrder = BigDecimal.ZERO;
            for (OrderLine order : timelyOrders) {
                if (outstanding.signum() == 0) {
                    break;
                }
                if (order.expectedDate().isAfter(demand.dueDate())) {
                    break; /** 订单按 ETA 排序，绝不将迟到供给向更早日期挪用。 */
                }
                BigDecimal available = remainingByOrder.get(order.orderLineId());
                BigDecimal used = available.min(outstanding);
                if (used.signum() > 0) {
                    remainingByOrder.put(order.orderLineId(), available.subtract(used));
                    outstanding = outstanding.subtract(used);
                    allocatedOrder = allocatedOrder.add(used);
                    if (!order.detailId().equals(demand.detailId())) {
                        allocatedAwayFromOrigin.merge(order.detailId(), used, BigDecimal::add);
                    }
                }
            }
            BigDecimal blocked = blockedByDetail.getOrDefault(demand.detailId(), BigDecimal.ZERO);
            if (blocked.compareTo(outstanding) > 0) {
                throw reject(RejectionReason.OVERCOMMITTED, "Open source commitments exceed uncovered demand");
            }
            DetailAllocation allocation = new DetailAllocation(demand.detailId(), demand.dueDate(),
                demand.dueDate().isBefore(scope.snapshotDate()), demand.netDemandBase(),
                allocatedStock, allocatedOrder, outstanding, blocked, outstanding.subtract(blocked));
            all.put(demand.detailId(), allocation);
            if (scope.requestedDetailIds().contains(demand.detailId())) {
                visible.add(allocation);
            } else {
                externalDemand = externalDemand.add(demand.netDemandBase());
                externalSupply = externalSupply.add(allocatedStock).add(allocatedOrder);
            }
        }
        /**
         * 已转单订单重新分配给竞争明细，不能据此授权为原明细再次采购；
         * 除非 ERP 已证明来源转移。
         */
        for (Map.Entry<String, BigDecimal> entry : allocatedAwayFromOrigin.entrySet()) {
            if (entry.getValue().signum() > 0 && all.get(entry.getKey()).requestableBase().signum() > 0) {
                throw reject(RejectionReason.UNVERIFIED_SOURCE, "Order source transfer requires ERP reconciliation");
            }
        }
        return new AllocationResult(visible, externalDemand, externalSupply);
    }

    private static Demand requireOrigin(Map<String, Demand> byId, String detailId) {
        Demand origin = byId.get(detailId);
        if (origin == null) {
            throw reject(RejectionReason.INCOMPLETE_POOL, "Order/application origin missing from global pool");
        }
        return origin;
    }

    private static void requireVerified(boolean verified, String unitId, String expectedUnitId) {
        if (!verified || blank(unitId) || !unitId.equals(expectedUnitId)) {
            throw reject(RejectionReason.UNVERIFIED_SOURCE, "Source/base unit not independently certified");
        }
    }

    private static void requireNonnegative(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value == null || value.signum() < 0) {
                throw reject(RejectionReason.INVALID_QUANTITY, "Missing or negative base-unit quantity");
            }
        }
    }

    private static void requirePositive(BigDecimal value) {
        requireNonnegative(value);
        if (value.signum() == 0) {
            throw reject(RejectionReason.INVALID_QUANTITY, "Open source has no remaining quantity");
        }
    }

    private static int compareDecimalId(String left, String right) {
        int length = Integer.compare(left.length(), right.length());
        return length == 0 ? left.compareTo(right) : length;
    }

    private static boolean decimalId(String id) {
        return id != null && id.matches("[1-9][0-9]*");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static AllocationRejectedException reject(RejectionReason reason, String message) {
        return new AllocationRejectedException(reason, message);
    }
}