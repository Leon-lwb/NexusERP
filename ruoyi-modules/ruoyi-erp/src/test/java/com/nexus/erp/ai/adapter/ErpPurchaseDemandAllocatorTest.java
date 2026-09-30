package com.nexus.erp.ai.adapter;

import com.nexus.erp.ai.adapter.ErpPurchaseDemandAllocator.AllocationInput;
import com.nexus.erp.ai.adapter.ErpPurchaseDemandAllocator.Commitment;
import com.nexus.erp.ai.adapter.ErpPurchaseDemandAllocator.CommitmentKind;
import com.nexus.erp.ai.adapter.ErpPurchaseDemandAllocator.Demand;
import com.nexus.erp.ai.adapter.ErpPurchaseDemandAllocator.DetailAllocation;
import com.nexus.erp.ai.adapter.ErpPurchaseDemandAllocator.OrderLine;
import com.nexus.erp.ai.adapter.ErpPurchaseDemandAllocator.OrderStatus;
import com.nexus.erp.ai.adapter.ErpPurchaseDemandAllocator.RejectionReason;
import com.nexus.erp.ai.adapter.ErpPurchaseDemandAllocator.Scope;
import com.nexus.erp.ai.adapter.ErpPurchaseDemandAllocator.Stock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ErpPurchaseDemandAllocatorTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    private static final LocalDate DUE = TODAY.plusDays(5);
    private static final String UNIT = "1001";
    private final ErpPurchaseDemandAllocator allocator = new ErpPurchaseDemandAllocator();

    @Test
    void twoWorkOrdersShareTenStockOnlyOnce() {
        var result = allocate("10", List.of(demand("1", DUE, "8"), demand("2", DUE, "8")),
            List.of(), List.of(), Set.of("1", "2"));
        assertEquals(List.of("1", "2"), result.allocations().stream().map(DetailAllocation::detailId).toList());
        qty("8", result.allocations().get(0).stockAllocatedBase());
        qty("2", result.allocations().get(1).stockAllocatedBase());
        qty("6", result.allocations().get(1).requestableBase());
    }

    @Test
    void sortsByOriginalDueDateBeforeIdRegardlessOfInputOrder() {
        var result = allocate("7", List.of(demand("2", DUE, "7"), demand("10", TODAY, "7")),
            List.of(), List.of(), Set.of("2", "10"));
        assertEquals(List.of("10", "2"), result.allocations().stream().map(DetailAllocation::detailId).toList());
        qty("7", result.allocations().get(0).stockAllocatedBase());
        qty("7", result.allocations().get(1).requestableBase());
    }

    @Test
    void sortsTiedDatesByNumericDetailIdNotInputOrderOrLexicalString() {
        var result = allocate("5", List.of(demand("10", DUE, "5"), demand("2", DUE, "5")),
            List.of(), List.of(), Set.of("2", "10"));
        assertEquals(List.of("2", "10"), result.allocations().stream().map(DetailAllocation::detailId).toList());
        qty("5", result.allocations().get(0).stockAllocatedBase());
    }

    @Test
    void onlyApprovedOrdersWithTimelyUnreceivedRemainderAreSharedOnce() {
        var result = allocate("0", List.of(demand("1", DUE, "5"), demand("2", DUE.plusDays(1), "5")),
            List.of(order("40", "1", OrderStatus.APPROVED, DUE, "8", "0")), List.of(), Set.of("1", "2"));
        qty("5", result.allocations().get(0).timelyOrderAllocatedBase());
        qty("3", result.allocations().get(1).timelyOrderAllocatedBase());
        qty("2", result.allocations().get(1).requestableBase());
    }

    @Test
    void orderTooLateForItsOriginIsBlockingNotTimelySupplyForLaterDemand() {
        var result = allocate("0", List.of(demand("1", DUE, "5"), demand("2", DUE.plusDays(2), "5")),
            List.of(order("40", "1", OrderStatus.APPROVED, DUE.plusDays(1), "3", "0")),
            List.of(), Set.of("1", "2"));
        qty("0", result.allocations().get(0).timelyOrderAllocatedBase());
        qty("3", result.allocations().get(0).committedBase());
        qty("2", result.allocations().get(0).requestableBase());
        qty("0", result.allocations().get(1).timelyOrderAllocatedBase());
    }

    @Test
    void expiredUnreceivedApprovedOrderCannotRetroactivelyCoverOverdueDemand() {
        var result = allocate("2", List.of(demand("1", TODAY.minusDays(2), "5")),
            List.of(order("40", "1", OrderStatus.APPROVED, TODAY.minusDays(1), "3", "0")),
            List.of(), Set.of("1"));
        var allocation = result.allocations().get(0);
        assertTrue(allocation.originallyOverdue());
        assertEquals(TODAY.minusDays(2), allocation.dueDate());
        qty("2", allocation.stockAllocatedBase());
        qty("0", allocation.timelyOrderAllocatedBase());
        qty("3", allocation.committedBase());
        qty("0", allocation.requestableBase());
    }

    @Test
    void currentStockReducesOverdueUnclosedDemandWithoutChangingOriginalDueDate() {
        var allocation = allocate("3", List.of(demand("1", TODAY.minusDays(2), "5")),
            List.of(), List.of(), Set.of("1")).allocations().get(0);
        qty("5", allocation.netDemandBase());
        qty("3", allocation.eligibleSupplyBase());
        qty("2", allocation.uncoveredBase());
        qty("2", allocation.requestableBase());
        assertTrue(allocation.originallyOverdue());
        assertEquals(TODAY.minusDays(2), allocation.dueDate());
    }

    @Test
    void draftOrderBlocksOnlyItsOriginAndIsNeverInventoried() {
        var result = allocate("0", List.of(demand("1", DUE, "6"), demand("2", DUE, "6")),
            List.of(order("40", "1", OrderStatus.DRAFT, DUE, "4", "0")), List.of(), Set.of("1", "2"));
        qty("0", result.allocations().get(0).eligibleSupplyBase());
        qty("4", result.allocations().get(0).committedBase());
        qty("2", result.allocations().get(0).requestableBase());
        qty("6", result.allocations().get(1).requestableBase());
    }

    @Test
    void legacyDraftFiveHundredTenOverFivePointFourTwoNineTwoIsFailClosed() {
        var error = assertThrows(ErpPurchaseDemandAllocator.AllocationRejectedException.class,
            () -> allocate("0", List.of(demand("1", DUE, "5.4292")), List.of(),
                List.of(commitment("req:510", "1", CommitmentKind.LEGACY_OPEN, "510")), Set.of("1")));
        assertEquals(RejectionReason.OVERCOMMITTED, error.reason());
    }

    @Test
    void partialReceiptOnlyCountsBookedStockAndRemainingOrderOnce() {
        var allocation = allocate("4", List.of(demand("1", DUE, "10")),
            List.of(order("40", "1", OrderStatus.PARTIALLY_RECEIVED, DUE, "10", "4")),
            List.of(), Set.of("1")).allocations().get(0);
        qty("4", allocation.stockAllocatedBase());
        qty("6", allocation.timelyOrderAllocatedBase());
        qty("0", allocation.committedBase());
        qty("0", allocation.requestableBase());
    }

    @Test
    void partialReceiptOfLateOrderBlocksOnlyUnreceivedRemainder() {
        var allocation = allocate("4", List.of(demand("1", DUE, "10")),
            List.of(order("40", "1", OrderStatus.PARTIALLY_RECEIVED, DUE.plusDays(2), "10", "4")),
            List.of(), Set.of("1")).allocations().get(0);
        qty("4", allocation.stockAllocatedBase());
        qty("0", allocation.timelyOrderAllocatedBase());
        qty("6", allocation.committedBase());
        qty("0", allocation.requestableBase());
    }

    @Test
    void pendingAndLegacyDistinctSourcesCombineAfterUncoveredNotAsSupply() {
        var allocation = allocate("0", List.of(demand("1", DUE, "10")), List.of(),
            List.of(commitment("req:1", "1", CommitmentKind.PENDING, "4"),
                commitment("req:2", "1", CommitmentKind.LEGACY_OPEN, "3")), Set.of("1")).allocations().get(0);
        qty("10", allocation.uncoveredBase());
        qty("7", allocation.committedBase());
        qty("3", allocation.requestableBase());
    }

    @Test
    void rejectsDuplicateCommitmentSourceAcrossPendingAndLegacy() {
        rejects(RejectionReason.DUPLICATE_SOURCE, "0", List.of(demand("1", DUE, "10")), List.of(),
            List.of(commitment("req:1", "1", CommitmentKind.PENDING, "2"),
                commitment("req:1", "1", CommitmentKind.LEGACY_OPEN, "2")), Set.of("1"));
    }

    @Test
    void rejectsOrderThatAlsoAppearsAsPendingOrLegacySource() {
        rejects(RejectionReason.DUPLICATE_SOURCE, "0", List.of(demand("1", DUE, "10")),
            List.of(order("40", "1", OrderStatus.APPROVED, DUE, "4", "0", "req:1")),
            List.of(commitment("req:1", "1", CommitmentKind.PENDING, "4")), Set.of("1"));
    }

    @Test
    void refusesMissingDemandFromGlobalPoolEvenWhenVisibleSliceLooksComplete() {
        var scope = new Scope("000001", "100", UNIT, TODAY, DUE.plusDays(10), false, Set.of("1"));
        var error = assertThrows(ErpPurchaseDemandAllocator.AllocationRejectedException.class,
            () -> allocator.allocate(new AllocationInput(scope, stock("10"),
                List.of(demand("1", DUE, "5")), List.of(), List.of())));
        assertEquals(RejectionReason.INCOMPLETE_POOL, error.reason());
    }

    @Test
    void externalEarlierDemandConsumesSharedStockWithoutReturningUnauthorizedRows() {
        var result = allocate("10", List.of(demand("1", DUE.minusDays(1), "8"), demand("2", DUE, "8")),
            List.of(), List.of(), Set.of("2"));
        assertEquals(List.of("2"), result.allocations().stream().map(DetailAllocation::detailId).toList());
        qty("2", result.allocations().get(0).stockAllocatedBase());
        qty("6", result.allocations().get(0).requestableBase());
        qty("8", result.externalDemandBase());
        qty("8", result.externalSupplyAllocatedBase());
    }

    @Test
    void missingRequestedDetailDoesNotTurnIntoZeroDemand() {
        rejects(RejectionReason.INCOMPLETE_POOL, "0", List.of(demand("1", DUE, "5")),
            List.of(), List.of(), Set.of("2"));
    }

    @Test
    void rejectsUnverifiedDemandEvenIfItIsOutsideVisibleSlice() {
        rejects(RejectionReason.UNVERIFIED_SOURCE, "0",
            List.of(demand("1", DUE, "5"), new Demand("2", DUE, bd("5"), UNIT, false)),
            List.of(), List.of(), Set.of("1"));
    }

    @Test
    void rejectsUnknownOrderStatusOrUnitInsteadOfAssumingNoSupply() {
        rejects(RejectionReason.UNVERIFIED_SOURCE, "0", List.of(demand("1", DUE, "5")),
            List.of(new OrderLine("40", "req:40", "1", null, DUE, bd("3"), bd("0"), UNIT, true)),
            List.of(), Set.of("1"));
        rejects(RejectionReason.UNVERIFIED_SOURCE, "0", List.of(demand("1", DUE, "5")),
            List.of(new OrderLine("40", "req:40", "1", OrderStatus.APPROVED, DUE,
                bd("3"), bd("0"), "unknown", true)), List.of(), Set.of("1"));
    }

    @Test
    void rejectsUnknownStockQualificationAndInvalidPartialReceipt() {
        var scope = scope(Set.of("1"));
        var error = assertThrows(ErpPurchaseDemandAllocator.AllocationRejectedException.class,
            () -> allocator.allocate(new AllocationInput(scope,
                new Stock(bd("10"), bd("1"), bd("1"), UNIT, false),
                List.of(demand("1", DUE, "5")), List.of(), List.of())));
        assertEquals(RejectionReason.UNVERIFIED_SOURCE, error.reason());
        rejects(RejectionReason.INVALID_QUANTITY, "0", List.of(demand("1", DUE, "5")),
            List.of(order("40", "1", OrderStatus.PARTIALLY_RECEIVED, DUE, "3", "4")),
            List.of(), Set.of("1"));
    }

    @Test
    void availableStockIsClampedAfterReservationsAndSafety() {
        var allocation = allocator.allocate(new AllocationInput(scope(Set.of("1")),
            new Stock(bd("4"), bd("3"), bd("2"), UNIT, true),
            List.of(demand("1", DUE, "6")), List.of(), List.of())).allocations().get(0);
        qty("0", allocation.stockAllocatedBase());
        qty("6", allocation.requestableBase());
    }

    @Test
    void rejectsDuplicateDemandDetailAndUnattributedOrder() {
        rejects(RejectionReason.DUPLICATE_SOURCE, "0", List.of(demand("1", DUE, "5"), demand("1", DUE, "5")),
            List.of(), List.of(), Set.of("1"));
        rejects(RejectionReason.INCOMPLETE_POOL, "0", List.of(demand("1", DUE, "5")),
            List.of(order("40", "2", OrderStatus.APPROVED, DUE, "1", "0")), List.of(), Set.of("1"));
    }

    @Test
    void reassignedTimelyOrderCannotAuthorizeDuplicatePurchaseForItsOrigin() {
        rejects(RejectionReason.UNVERIFIED_SOURCE, "0",
            List.of(demand("1", DUE, "3"), demand("2", DUE.plusDays(1), "5")),
            List.of(order("40", "2", OrderStatus.APPROVED, DUE, "3", "0")),
            List.of(), Set.of("1", "2"));
    }
    @Test
    void snapshotsAndResultListsCannotBeMutatedThroughCaller() {
        var demands = new ArrayList<>(List.of(demand("1", DUE, "5")));
        var input = new AllocationInput(scope(Set.of("1")), stock("0"), demands, List.of(), List.of());
        demands.add(demand("2", DUE, "3"));
        var result = allocator.allocate(input);
        assertEquals(1, result.allocations().size());
        assertThrows(UnsupportedOperationException.class, () -> result.allocations().clear());
    }

    private ErpPurchaseDemandAllocator.AllocationResult allocate(String availableStock, List<Demand> demands,
                                                                   List<OrderLine> orders, List<Commitment> commitments,
                                                                   Set<String> requested) {
        return allocator.allocate(new AllocationInput(scope(requested), stock(availableStock),
            demands, orders, commitments));
    }

    private void rejects(RejectionReason reason, String availableStock, List<Demand> demands,
                         List<OrderLine> orders, List<Commitment> commitments, Set<String> requested) {
        var error = assertThrows(ErpPurchaseDemandAllocator.AllocationRejectedException.class,
            () -> allocate(availableStock, demands, orders, commitments, requested));
        assertEquals(reason, error.reason());
    }

    private static Scope scope(Set<String> requested) {
        return new Scope("000001", "100", UNIT, TODAY, DUE.plusDays(10), true, requested);
    }

    private static Stock stock(String quantity) {
        return new Stock(bd(quantity), bd("0"), bd("0"), UNIT, true);
    }

    private static Demand demand(String detailId, LocalDate dueDate, String amount) {
        return new Demand(detailId, dueDate, bd(amount), UNIT, true);
    }

    private static OrderLine order(String orderLineId, String detailId, OrderStatus status,
                                   LocalDate expected, String quantity, String received) {
        return order(orderLineId, detailId, status, expected, quantity, received, "req:" + orderLineId);
    }

    private static OrderLine order(String orderLineId, String detailId, OrderStatus status,
                                   LocalDate expected, String quantity, String received, String sourceKey) {
        return new OrderLine(orderLineId, sourceKey, detailId, status, expected, bd(quantity), bd(received), UNIT, true);
    }

    private static Commitment commitment(String sourceKey, String detailId, CommitmentKind kind, String amount) {
        return new Commitment(sourceKey, detailId, kind, bd(amount), UNIT, true);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static void qty(String expected, BigDecimal actual) {
        assertEquals(0, bd(expected).compareTo(actual), "expected " + expected + " but got " + actual);
    }
}