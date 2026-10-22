package com.nexus.erp.pu;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import com.nexus.erp.pu.service.PuRequirementAllocationPolicy;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;

/** Pure arithmetic only: the service must obtain every quantity from locked persisted facts. */
class PuCertifiedPurchaseLifecyclePolicyTest {
    private static final Date NOW = new Date(1790900000000L);

    @Test void approvalRetainsTheSameSourceOccupancyAndCasVersion() {
        var row = intent();
        var approved = change("approveRequestIntent", row, NOW).ledger();
        assertEquals("REQUEST_INTENT", approved.getAllocationStatus());
        assertEquals(new BigDecimal("10.0000"), approved.getOccupiedBaseQty());
        assertEquals(0, approved.getVersion());
        assertEquals(row, intent(), "Pure approval must not mutate its input");
    }

    @Test void partialReceiptDoesNotTreatTheWholeSourceAsFulfilled() {
        var current = ordered();
        var row = change("receiveOrder", current, new BigDecimal("4.0000"), NOW).ledger();
        assertEquals(new BigDecimal("6.0000"), row.getOccupiedBaseQty());
        assertEquals(new BigDecimal("4.0000"), row.getFulfilledBaseQty());
        assertEquals(new BigDecimal("0.0000"), row.getReleasedBaseQty());
        assertEquals(current.getCoveredDemandBaseQty(), sum(row));
        assertEquals(701L, row.getOrderLineId());
        assertEquals(0, row.getVersion());
    }

    @Test void closingAnOrderReleasesOnlyUnfulfilledRemainder() {
        var partial = change("receiveOrder", ordered(), new BigDecimal("4.0000"), NOW).ledger();
        var row = change("releaseOrder", partial, NOW, "ORDER_CLOSED").ledger();
        assertEquals(new BigDecimal("0.0000"), row.getOccupiedBaseQty());
        assertEquals(new BigDecimal("4.0000"), row.getFulfilledBaseQty());
        assertEquals(new BigDecimal("6.0000"), row.getReleasedBaseQty());
        assertEquals("RELEASED", row.getAllocationStatus());
        assertEquals(701L, row.getOrderLineId());
        assertNotNull(row.getTransferTime());
    }

    @Test void activeOrderReturnReopensSameSourceWithoutNewAllocation() {
        var partial = change("receiveOrder", ordered(), new BigDecimal("4.0000"), NOW).ledger();
        var row = change("returnOrder", partial, new BigDecimal("2.0000"), NOW).ledger();
        assertEquals(new BigDecimal("8.0000"), row.getOccupiedBaseQty());
        assertEquals(new BigDecimal("2.0000"), row.getFulfilledBaseQty());
        assertEquals(new BigDecimal("0.0000"), row.getReleasedBaseQty());
        assertEquals(partial.getAllocationKey(), row.getAllocationKey());
        assertEquals(partial.getCoveredDemandBaseQty(), sum(row));
    }

    @Test void closedOrderReturnIncreasesReleasedRatherThanRevivingOrder() {
        var partial = change("receiveOrder", ordered(), new BigDecimal("4.0000"), NOW).ledger();
        var closed = change("releaseOrder", partial, NOW, "ORDER_CANCELLED").ledger();
        var row = change("returnOrder", closed, new BigDecimal("2.0000"), NOW).ledger();
        assertEquals(new BigDecimal("0.0000"), row.getOccupiedBaseQty());
        assertEquals(new BigDecimal("2.0000"), row.getFulfilledBaseQty());
        assertEquals(new BigDecimal("8.0000"), row.getReleasedBaseQty());
        assertEquals("RELEASED", row.getAllocationStatus());
        assertEquals(closed.getReleasedAt(), row.getReleasedAt());
    }

    @Test void fullReceiptConservesAllCoveredDemand() {
        var row = change("receiveOrder", ordered(), new BigDecimal("10.0000"), NOW).ledger();
        assertEquals(0, row.getOccupiedBaseQty().signum());
        assertEquals(row.getCoveredDemandBaseQty(), row.getFulfilledBaseQty());
        assertEquals(row.getCoveredDemandBaseQty(), sum(row));
    }

    @Test void sourceRequisitionCannotReleaseTransferredOccupancy() {
        assertThrows(IllegalArgumentException.class,
            () -> PuRequirementAllocationPolicy.releaseRequestIntent(ordered(), NOW, "CLOSED"));
    }

    @Test void corruptBalancesAndUnknownLifecycleStatesCannotBeRepairedByAReceipt() {
        var corrupt = ordered();
        corrupt.setFulfilledBaseQty(BigDecimal.ONE);
        assertThrows(IllegalArgumentException.class,
            () -> change("receiveOrder", corrupt, BigDecimal.ONE, NOW));
        var unknown = ordered();
        unknown.setAllocationStatus("ORDER_TIMELY_SUPPLY");
        assertThrows(IllegalArgumentException.class,
            () -> change("receiveOrder", unknown, BigDecimal.ONE, NOW));
    }

    @Test void overReceiptNegativeZeroAndUnrepresentableQuantitiesAreRejected() {
        for (String value : new String[]{"11", "-1", "0", "0.00001", "100000000000000"}) {
            assertThrows(IllegalArgumentException.class,
                () -> change("receiveOrder", ordered(), new BigDecimal(value), NOW), value);
        }
    }

    @Test void returnCannotExceedActualFulfillment() {
        var partial = change("receiveOrder", ordered(), new BigDecimal("4.0000"), NOW).ledger();
        assertThrows(IllegalArgumentException.class,
            () -> change("returnOrder", partial, new BigDecimal("4.0001"), NOW));
    }

    @Test void receiptCannotResurrectClosedOrUntransferredSource() {
        var closed = change("releaseOrder", ordered(), NOW, "CLOSED").ledger();
        assertThrows(IllegalArgumentException.class,
            () -> change("receiveOrder", closed, BigDecimal.ONE, NOW));
        assertThrows(IllegalArgumentException.class,
            () -> change("receiveOrder", intent(), BigDecimal.ONE, NOW));
    }

    @Test void integerMaxVersionAndIncompleteOrderIdentityAreRejected() {
        var current = ordered();
        current.setVersion(Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class,
            () -> change("receiveOrder", current, BigDecimal.ONE, NOW));
        var absent = ordered();
        absent.setTransferTime(null);
        assertThrows(IllegalArgumentException.class,
            () -> change("receiveOrder", absent, BigDecimal.ONE, NOW));
    }

    private static BigDecimal sum(PuRequirementAllocationLedger row) {
        return row.getOccupiedBaseQty().add(row.getFulfilledBaseQty()).add(row.getReleasedBaseQty());
    }
    static PuRequirementAllocationLedger intent() {
        var row = new PuRequirementAllocationLedger();
        row.setAllocationId(101L); row.setTenantId("000001"); row.setMaterialId(201L);
        row.setSourceType("MRP_DETAIL"); row.setDetailId(301L); row.setRequisitionLineId(401L);
        row.setAllocationNo(1); row.setBaseUomId(501L);
        row.setCoveredDemandBaseQty(new BigDecimal("10.0000"));
        row.setAllocationKey("allocation-101"); row.setDecisionIdempotencyKey("request-1");
        return PuRequirementAllocationPolicy.createRequestIntent(row, NOW).ledger();
    }
    static PuRequirementAllocationLedger ordered() {
        return PuRequirementAllocationPolicy.commitToOrder(intent(), 701L, NOW).ledger();
    }
    private static PuRequirementAllocationPolicy.Change change(String name, Object... args) {
        Class<?>[] types = Arrays.stream(args).map(Object::getClass).toArray(Class<?>[]::new);
        var method = assertDoesNotThrow(() -> PuRequirementAllocationPolicy.class.getMethod(name, types),
            "Expected lifecycle RED: approved API not implemented: " + name);
        try {
            return (PuRequirementAllocationPolicy.Change) method.invoke(null, args);
        } catch (InvocationTargetException wrapper) {
            if (wrapper.getCause() instanceof RuntimeException failure) throw failure;
            throw new AssertionError(wrapper.getCause());
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
