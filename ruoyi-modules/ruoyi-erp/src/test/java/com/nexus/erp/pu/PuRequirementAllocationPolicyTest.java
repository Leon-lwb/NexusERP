package com.nexus.erp.pu;

import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纯策略契约：不调用 Spring 上下文、数据库、文件、网络或业务服务。
 * 刻意通过反射，让策略或 API 缺失成为普通 RED 断言失败，而非编译失败。
 * 生产实现必须等待实际观察到 RED 后再编写。
 */
@Tag("dev")
class PuRequirementAllocationPolicyTest {
    private static final String POLICY = "com.nexus.erp.pu.service.PuRequirementAllocationPolicy";
    private static final String REQUEST_INTENT = "REQUEST_INTENT";
    private static final String ORDER_COMMITTED = "ORDER_COMMITTED_NOT_TIMELY_SUPPLY";
    private static final String RELEASED = "RELEASED";
    private static final BigDecimal ZERO = new BigDecimal("0.0000");
    private static final Class<?>[] CREATE_ARGS = {PuRequirementAllocationLedger.class, Date.class};
    private static final Class<?>[] COMMIT_ARGS = {PuRequirementAllocationLedger.class, Long.class, Date.class};
    private static final Class<?>[] RELEASE_ARGS = {PuRequirementAllocationLedger.class, Date.class, String.class};

    @Test
    void publicApiIsLimitedToSevenPureTransitionsWithoutBooleanBypasses() {
        Class<?> policy = policyClass();
        assertTrue(Modifier.isFinal(policy.getModifiers()));
        assertEquals(0, policy.getConstructors().length, "Pure utility must not be a service instance");
        Set<String> publicMethods = new TreeSet<>();
        int publicMethodCount = 0;
        for (Method method : policy.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                publicMethodCount++;
                publicMethods.add(method.getName());
                assertTrue(Modifier.isStatic(method.getModifiers()));
                assertFalse(Arrays.asList(method.getParameterTypes()).contains(boolean.class));
                assertFalse(Arrays.asList(method.getParameterTypes()).contains(Boolean.class));
            }
        }
        assertEquals(7, publicMethodCount, "Overloaded public methods must not expand the bounded API");
        assertEquals(Set.of("createRequestIntent", "commitToOrder", "releaseRequestIntent",
            "approveRequestIntent", "receiveOrder", "releaseOrder", "returnOrder"), publicMethods);
        Class<?> changeType = policyMethod("createRequestIntent", CREATE_ARGS).getReturnType();
        assertEquals(changeType, policyMethod("commitToOrder", COMMIT_ARGS).getReturnType());
        assertEquals(changeType, policyMethod("releaseRequestIntent", RELEASE_ARGS).getReturnType());
        assertTrue(Modifier.isFinal(changeType.getModifiers()));
    }

    @Test
    void creationInitializesOnlyRequestIntentAndRetainsExplicitSourceIdentity() {
        PuRequirementAllocationLedger input = seed();
        PuRequirementAllocationLedger unchanged = snapshot(input);
        Date now = now();
        Object change = create(input, now);
        PuRequirementAllocationLedger row = ledger(change);

        assertEquals(unchanged, input, "Input must never be changed in place");
        assertNotSame(input, row);
        assertEquals(identity(input), identity(row));
        assertEquals(input.getCoveredDemandBaseQty(), row.getCoveredDemandBaseQty());
        assertEquals(input.getCoveredDemandBaseQty(), row.getOccupiedBaseQty());
        assertEquals(ZERO, row.getFulfilledBaseQty());
        assertEquals(ZERO, row.getReleasedBaseQty());
        assertEquals(REQUEST_INTENT, row.getAllocationStatus());
        assertNull(row.getOrderLineId());
        assertNull(row.getTransferTime());
        assertNull(row.getReleasedAt());
        assertNull(row.getReleaseReason());
        assertEquals(Integer.valueOf(0), row.getVersion());
        assertEquals(now, row.getCreateTime());
        assertEquals(now, row.getUpdateTime());
        assertNull(before(change), "Creation has no prior persisted ledger");
        assertEquals(now, occurredAt(change));
    }

    @Test
    void manualMrpIntentMayHaveNoDecisionIdempotencyKey() {
        PuRequirementAllocationLedger input = seed();
        input.setDecisionIdempotencyKey(null);
        PuRequirementAllocationLedger row = ledger(create(input, now()));
        assertNull(row.getDecisionIdempotencyKey());
        assertEquals(input.getAllocationKey(), row.getAllocationKey());
        assertEquals("MRP_DETAIL", row.getSourceType());
    }

    @Test
    void exactDecimalBoundariesAreAcceptedWithoutRounding() {
        for (String quantity : List.of("0.0001", "99999999999999.9999")) {
            PuRequirementAllocationLedger input = seed();
            input.setCoveredDemandBaseQty(new BigDecimal(quantity));
            PuRequirementAllocationLedger row = ledger(create(input, now()));
            assertEquals(new BigDecimal(quantity), row.getCoveredDemandBaseQty());
            assertEquals(new BigDecimal(quantity), row.getOccupiedBaseQty());
        }
    }

    @Test
    void extraTrailingZeroesDoNotMakeAnExactlyRepresentableDecimalInvalid() {
        PuRequirementAllocationLedger input = seed();
        input.setCoveredDemandBaseQty(new BigDecimal("12.34000"));
        PuRequirementAllocationLedger unchanged = snapshot(input);
        PuRequirementAllocationLedger row = ledger(create(input, now()));
        assertEquals(0, new BigDecimal("12.3400").compareTo(row.getCoveredDemandBaseQty()));
        assertEquals(0, row.getCoveredDemandBaseQty().compareTo(row.getOccupiedBaseQty()));
        assertEquals(unchanged, input);
    }

    @Test
    void creationAcceptsOnlyCoherentOptionalInitialBalancesAndVersion() {
        PuRequirementAllocationLedger input = seed();
        input.setAllocationStatus(REQUEST_INTENT);
        input.setOccupiedBaseQty(input.getCoveredDemandBaseQty());
        input.setFulfilledBaseQty(BigDecimal.ZERO);
        input.setReleasedBaseQty(BigDecimal.ZERO);
        input.setVersion(0);
        PuRequirementAllocationLedger row = ledger(create(input, now()));
        assertEquals(REQUEST_INTENT, row.getAllocationStatus());
        assertEquals(input.getCoveredDemandBaseQty(), row.getOccupiedBaseQty());
        assertEquals(ZERO, row.getFulfilledBaseQty());
        assertEquals(ZERO, row.getReleasedBaseQty());
        assertEquals(Integer.valueOf(0), row.getVersion());
    }

    @Test
    void missingZeroNegativeInexactAndOverflowingCoverageIsRejectedEverywhere() {
        for (BigDecimal quantity : Arrays.asList(null, BigDecimal.ZERO, new BigDecimal("-0.0001"),
            new BigDecimal("0.00001"), new BigDecimal("12.34561"),
            new BigDecimal("100000000000000.0000"), new BigDecimal("1E+15"))) {
            rejectEveryTransition(row -> row.setCoveredDemandBaseQty(quantity));
        }
    }

    @Test
    void allOwnershipIdentifiersMustBePresentAndPositiveForEveryTransition() {
        for (String field : List.of("AllocationId", "MaterialId", "DetailId", "RequisitionLineId", "BaseUomId")) {
            for (Long value : Arrays.asList(null, 0L, -1L)) {
                rejectEveryTransition(row -> set(row, field, Long.class, value));
            }
        }
    }

    @Test
    void splitAllocationNumberMustBePresentAndPositiveForEveryTransition() {
        for (Integer value : Arrays.asList(null, 0, -1)) {
            rejectEveryTransition(row -> row.setAllocationNo(value));
        }
    }

    @Test
    void tenantIsRequiredBoundedAndNeverTrimmedIntoADifferentIdentity() {
        for (String value : Arrays.asList(null, "", " ", "\t", " tenant-a ", "t".repeat(21))) {
            rejectEveryTransition(row -> row.setTenantId(value));
        }
    }

    @Test
    void allocationKeyIsRequiredBoundedAndNeverRewritten() {
        for (String value : Arrays.asList(null, "", " ", " allocation-key ", "k".repeat(129))) {
            rejectEveryTransition(row -> row.setAllocationKey(value));
        }
    }

    @Test
    void decisionKeyIfSuppliedMustBeNonblankBoundedAndUnaltered() {
        for (String value : List.of("", " ", " decision-key ", "k".repeat(129))) {
            rejectEveryTransition(row -> row.setDecisionIdempotencyKey(value));
        }
    }

    @Test
    void sourceMustBeExplicitMrpDetailNeverInferredOrCaseNormalized() {
        for (String value : Arrays.asList(null, "", "AI_DECISION", "LOW_STOCK", "MANUAL", "mrp_detail", " MRP_DETAIL ")) {
            rejectEveryTransition(row -> row.setSourceType(value));
        }
    }

    @Test
    void creationCannotResetPersistedTransferredReleasedOrOtherLifecycleFacts() {
        List<Consumer<PuRequirementAllocationLedger>> corruptions = List.of(
            row -> row.setOrderLineId(77L),
            row -> row.setOrderLineId(0L),
            row -> row.setAllocationStatus(ORDER_COMMITTED),
            row -> row.setAllocationStatus("ORDER_TIMELY_SUPPLY"),
            row -> row.setAllocationStatus(RELEASED),
            row -> row.setVersion(-1),
            row -> row.setVersion(1),
            row -> row.setCreateTime(new Date(1000)),
            row -> row.setUpdateTime(new Date(2000)),
            row -> row.setTransferTime(new Date(3000)),
            row -> row.setReleasedAt(new Date(4000)),
            row -> row.setReleaseReason("previously released")
        );
        for (Consumer<PuRequirementAllocationLedger> corruption : corruptions) {
            PuRequirementAllocationLedger input = seed();
            corruption.accept(input);
            rejectUnchanged(input, () -> create(input, now()));
        }
    }

    @Test
    void creationDoesNotSilentlyRepairContradictorySuppliedBalances() {
        List<Consumer<PuRequirementAllocationLedger>> corruptions = List.of(
            row -> row.setOccupiedBaseQty(BigDecimal.ZERO),
            row -> row.setOccupiedBaseQty(new BigDecimal("12.3457")),
            row -> row.setOccupiedBaseQty(new BigDecimal("12.34561")),
            row -> row.setOccupiedBaseQty(new BigDecimal("100000000000000")),
            row -> row.setFulfilledBaseQty(BigDecimal.ONE),
            row -> row.setFulfilledBaseQty(new BigDecimal("-1")),
            row -> row.setReleasedBaseQty(BigDecimal.ONE),
            row -> row.setReleasedBaseQty(new BigDecimal("-1"))
        );
        for (Consumer<PuRequirementAllocationLedger> corruption : corruptions) {
            PuRequirementAllocationLedger input = seed();
            corruption.accept(input);
            rejectUnchanged(input, () -> create(input, now()));
        }
    }

    @Test
    void orderCommitRetainsOccupancyAndCannotBecomeTimelySupplyOrInventory() {
        PuRequirementAllocationLedger input = intent();
        PuRequirementAllocationLedger unchanged = snapshot(input);
        Date now = now();
        Object change = commit(input, 77L, now);
        PuRequirementAllocationLedger row = ledger(change);

        assertEquals(unchanged, input);
        assertEquals(identity(input), identity(row));
        assertEquals(Long.valueOf(77), row.getOrderLineId());
        assertEquals(ORDER_COMMITTED, row.getAllocationStatus());
        assertEquals(input.getCoveredDemandBaseQty(), row.getCoveredDemandBaseQty());
        assertEquals(input.getOccupiedBaseQty(), row.getOccupiedBaseQty());
        assertEquals(ZERO, row.getFulfilledBaseQty());
        assertEquals(ZERO, row.getReleasedBaseQty());
        assertEquals(input.getCreateTime(), row.getCreateTime());
        assertEquals(now, row.getTransferTime());
        assertEquals(now, row.getUpdateTime());
        assertNull(row.getReleasedAt());
        assertNull(row.getReleaseReason());
        assertEquals(input.getVersion(), row.getVersion(), "Mapper, not policy, increments CAS version");
        assertEquals(unchanged, before(change));
        assertEquals(now, occurredAt(change));
    }

    @Test
    void orderLineIdMustBeProvidedAndPositive() {
        for (Long orderLineId : Arrays.asList(null, 0L, -1L)) {
            PuRequirementAllocationLedger input = intent();
            rejectUnchanged(input, () -> commit(input, orderLineId, now()));
        }
    }

    @Test
    void orderPresenceBlocksBothTransitionsEvenIfStatusStillClaimsRequestIntent() {
        for (Long orderLineId : List.of(77L, 0L, -1L)) {
            PuRequirementAllocationLedger input = intent();
            input.setOrderLineId(orderLineId);
            rejectUnchanged(input, () -> commit(input, 88L, now()));
            rejectUnchanged(input, () -> release(input, now(), "requisition closed"));
        }
    }

    @Test
    void committedOrderCannotBeReleasedByClosingItsSourceRequisitionOrReplayedAsAnotherOrder() {
        PuRequirementAllocationLedger committed = ledger(commit(intent(), 77L, now()));
        rejectUnchanged(committed, () -> release(committed, new Date(6000), "source requisition closed"));
        rejectUnchanged(committed, () -> commit(committed, 77L, new Date(6000)));
        rejectUnchanged(committed, () -> commit(committed, 88L, new Date(6000)));
    }

    @Test
    void releaseRemovesOnlyUncommittedIntentAndPreservesItsCoverageAndOwnership() {
        PuRequirementAllocationLedger input = intent();
        PuRequirementAllocationLedger unchanged = snapshot(input);
        Date now = now();
        Object change = release(input, now, "unchanged draft compensation");
        PuRequirementAllocationLedger row = ledger(change);

        assertEquals(unchanged, input);
        assertEquals(identity(input), identity(row));
        assertEquals(input.getCoveredDemandBaseQty(), row.getCoveredDemandBaseQty());
        assertEquals(ZERO, row.getOccupiedBaseQty());
        assertEquals(ZERO, row.getFulfilledBaseQty());
        assertEquals(input.getCoveredDemandBaseQty(), row.getReleasedBaseQty());
        assertEquals(RELEASED, row.getAllocationStatus());
        assertNull(row.getOrderLineId());
        assertNull(row.getTransferTime());
        assertEquals(input.getCreateTime(), row.getCreateTime());
        assertEquals(now, row.getReleasedAt());
        assertEquals(now, row.getUpdateTime());
        assertEquals("unchanged draft compensation", row.getReleaseReason());
        assertEquals(input.getVersion(), row.getVersion());
        assertEquals(unchanged, before(change));
        assertEquals(now, occurredAt(change));
    }

    @Test
    void releaseReasonMustBePresentNonblankAndFitTheExistingColumn() {
        for (String reason : Arrays.asList(null, "", " ", "\t", "r".repeat(256))) {
            PuRequirementAllocationLedger input = intent();
            rejectUnchanged(input, () -> release(input, now(), reason));
        }
        String boundary = "r".repeat(255);
        assertEquals(boundary, ledger(release(intent(), now(), boundary)).getReleaseReason());
    }

    @Test
    void onlyRequestIntentCanBeTransferredOrReleasedAndReleasedKeysCannotBeReopened() {
        for (String status : Arrays.asList(null, "", "UNKNOWN", ORDER_COMMITTED, "ORDER_TIMELY_SUPPLY", RELEASED)) {
            PuRequirementAllocationLedger input = intent();
            input.setAllocationStatus(status);
            rejectUnchanged(input, () -> commit(input, 77L, now()));
            rejectUnchanged(input, () -> release(input, now(), "request cancelled"));
        }
        PuRequirementAllocationLedger released = ledger(release(intent(), now(), "cancelled"));
        rejectUnchanged(released, () -> create(released, new Date(6000)));
        rejectUnchanged(released, () -> commit(released, 77L, new Date(6000)));
        rejectUnchanged(released, () -> release(released, new Date(6000), "cancelled again"));
    }

    @Test
    void incompleteOrPartialBalancesAreRejectedRatherThanInventingFulfillmentAlgorithms() {
        List<Consumer<PuRequirementAllocationLedger>> corruptions = List.of(
            row -> row.setOccupiedBaseQty(null),
            row -> row.setOccupiedBaseQty(BigDecimal.ZERO),
            row -> row.setOccupiedBaseQty(new BigDecimal("-1")),
            row -> row.setOccupiedBaseQty(new BigDecimal("12.3457")),
            row -> row.setOccupiedBaseQty(new BigDecimal("12.34561")),
            row -> row.setOccupiedBaseQty(new BigDecimal("100000000000000")),
            row -> row.setFulfilledBaseQty(null),
            row -> row.setFulfilledBaseQty(BigDecimal.ONE),
            row -> row.setFulfilledBaseQty(new BigDecimal("-1")),
            row -> row.setFulfilledBaseQty(new BigDecimal("0.00001")),
            row -> row.setReleasedBaseQty(null),
            row -> row.setReleasedBaseQty(BigDecimal.ONE),
            row -> row.setReleasedBaseQty(new BigDecimal("-1")),
            row -> row.setReleasedBaseQty(new BigDecimal("0.00001"))
        );
        for (Consumer<PuRequirementAllocationLedger> corruption : corruptions) {
            PuRequirementAllocationLedger input = intent();
            corruption.accept(input);
            rejectUnchanged(input, () -> commit(input, 77L, now()));
            rejectUnchanged(input, () -> release(input, now(), "cancelled"));
        }
    }

    @Test
    void currentIntentMustHaveACasVersionAndCannotOverflowTheMapperIncrement() {
        for (Integer version : Arrays.asList(null, -1, Integer.MAX_VALUE)) {
            PuRequirementAllocationLedger input = intent();
            input.setVersion(version);
            rejectUnchanged(input, () -> commit(input, 77L, now()));
            rejectUnchanged(input, () -> release(input, now(), "cancelled"));
        }
    }

    @Test
    void currentIntentCannotConcealPriorTransferOrReleaseAuditFacts() {
        List<Consumer<PuRequirementAllocationLedger>> corruptions = List.of(
            row -> row.setCreateTime(null),
            row -> row.setTransferTime(new Date(3000)),
            row -> row.setReleasedAt(new Date(4000)),
            row -> row.setReleaseReason("previously released")
        );
        for (Consumer<PuRequirementAllocationLedger> corruption : corruptions) {
            PuRequirementAllocationLedger input = intent();
            corruption.accept(input);
            rejectUnchanged(input, () -> commit(input, 77L, now()));
            rejectUnchanged(input, () -> release(input, now(), "cancelled"));
        }
    }

    @Test
    void nullLedgerAndNullCallerTimeAreRejectedByAllEntrypoints() {
        assertThrows(IllegalArgumentException.class, () -> create(null, now()));
        assertThrows(IllegalArgumentException.class, () -> commit(null, 77L, now()));
        assertThrows(IllegalArgumentException.class, () -> release(null, now(), "cancelled"));
        PuRequirementAllocationLedger seed = seed();
        PuRequirementAllocationLedger current = intent();
        rejectUnchanged(seed, () -> create(seed, null));
        rejectUnchanged(current, () -> commit(current, 77L, null));
        rejectUnchanged(current, () -> release(current, null, "cancelled"));
    }

    @Test
    void creationChangeOwnsItsSnapshotsAndCallerDateWithoutExposingMutableAliases() {
        PuRequirementAllocationLedger input = seed();
        Date now = now();
        Object change = create(input, now);
        PuRequirementAllocationLedger expected = snapshot(ledger(change));
        input.setTenantId("other-tenant");
        input.setAllocationKey("other-key");
        input.setCoveredDemandBaseQty(BigDecimal.ONE);
        now.setTime(-1);

        assertEquals(expected, ledger(change));
        assertEquals(new Date(5000), occurredAt(change));
        assertSnapshotAccessorIsDefensive(change, "ledger");
        Date exposed = occurredAt(change);
        exposed.setTime(-2);
        assertEquals(new Date(5000), occurredAt(change));
        assertNotSame(occurredAt(change), occurredAt(change));
    }

    @Test
    void bothTransitionChangesDeepCopyPriorAndResultLedgerIncludingAllAuditDates() {
        for (boolean transfer : List.of(true, false)) {
            PuRequirementAllocationLedger input = intent();
            Date now = now();
            Object change = transfer ? commit(input, 77L, now) : release(input, now, "cancelled");
            PuRequirementAllocationLedger expectedBefore = snapshot(before(change));
            PuRequirementAllocationLedger expectedAfter = snapshot(ledger(change));
            input.setTenantId("other-tenant");
            input.setDecisionIdempotencyKey("other-decision-key");
            mutateDates(input);
            now.setTime(-1);

            assertEquals(expectedBefore, before(change));
            assertEquals(expectedAfter, ledger(change));
            assertEquals(new Date(5000), occurredAt(change));
            assertSnapshotAccessorIsDefensive(change, "before");
            assertSnapshotAccessorIsDefensive(change, "ledger");
            Date exposed = occurredAt(change);
            exposed.setTime(-2);
            assertEquals(new Date(5000), occurredAt(change));
        }
    }

    @Test
    void changeHasOnlyPrivateFinalStateAndNoMutatingApiForEventConsumers() {
        Object change = release(intent(), now(), "cancelled");
        Class<?> type = change.getClass();
        assertTrue(Modifier.isFinal(type.getModifiers()));
        for (Field field : type.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                assertTrue(Modifier.isPrivate(field.getModifiers()), field.getName());
                assertTrue(Modifier.isFinal(field.getModifiers()), field.getName());
            }
        }
        for (Method method : type.getMethods()) {
            assertFalse(method.getName().startsWith("set"), method.getName());
        }
        assertNotSame(before(change), before(change));
        assertNotSame(ledger(change), ledger(change));
    }

    @Test
    void eventSnapshotsDistinguishIntentTransferAndReleaseWithoutClaimingSupply() {
        Object creation = create(seed(), now());
        PuRequirementAllocationLedger created = ledger(creation);
        Object transfer = commit(created, 77L, new Date(6000));
        Object release = release(created, new Date(6000), "cancelled without order");

        assertNull(before(creation));
        assertEquals(REQUEST_INTENT, before(transfer).getAllocationStatus());
        assertEquals(ORDER_COMMITTED, ledger(transfer).getAllocationStatus());
        assertEquals(before(transfer).getOccupiedBaseQty(), ledger(transfer).getOccupiedBaseQty());
        assertEquals(REQUEST_INTENT, before(release).getAllocationStatus());
        assertEquals(RELEASED, ledger(release).getAllocationStatus());
        assertEquals(created.getOccupiedBaseQty(), before(release).getOccupiedBaseQty());
        assertEquals(ZERO, ledger(release).getOccupiedBaseQty());
        assertEquals(identity(created), identity(ledger(transfer)));
        assertEquals(identity(created), identity(ledger(release)));
    }

    private static PuRequirementAllocationLedger seed() {
        PuRequirementAllocationLedger row = new PuRequirementAllocationLedger();
        row.setAllocationId(101L);
        row.setTenantId("tenant-a");
        row.setMaterialId(102L);
        row.setSourceType("MRP_DETAIL");
        row.setDetailId(103L);
        row.setRequisitionLineId(104L);
        row.setAllocationNo(1);
        row.setBaseUomId(105L);
        row.setCoveredDemandBaseQty(new BigDecimal("12.3456"));
        row.setAllocationKey("allocation-Case-A");
        row.setDecisionIdempotencyKey("decision-Case-A");
        return row;
    }

    private static PuRequirementAllocationLedger intent() {
        PuRequirementAllocationLedger row = seed();
        row.setAllocationStatus(REQUEST_INTENT);
        row.setOccupiedBaseQty(row.getCoveredDemandBaseQty());
        row.setFulfilledBaseQty(ZERO);
        row.setReleasedBaseQty(ZERO);
        row.setVersion(7);
        row.setCreateTime(new Date(1000));
        row.setUpdateTime(new Date(2000));
        return row;
    }

    private static Date now() {
        return new Date(5000);
    }

    private static List<Object> identity(PuRequirementAllocationLedger row) {
        return Arrays.asList(row.getAllocationId(), row.getTenantId(), row.getMaterialId(), row.getSourceType(),
            row.getDetailId(), row.getRequisitionLineId(), row.getAllocationNo(), row.getBaseUomId(),
            row.getAllocationKey(), row.getDecisionIdempotencyKey());
    }

    private static void rejectEveryTransition(Consumer<PuRequirementAllocationLedger> corruption) {
        PuRequirementAllocationLedger seed = seed();
        corruption.accept(seed);
        rejectUnchanged(seed, () -> create(seed, now()));
        PuRequirementAllocationLedger current = intent();
        corruption.accept(current);
        rejectUnchanged(current, () -> commit(current, 77L, now()));
        rejectUnchanged(current, () -> release(current, now(), "cancelled"));
    }

    private static void rejectUnchanged(PuRequirementAllocationLedger row, Executable action) {
        PuRequirementAllocationLedger unchanged = snapshot(row);
        assertThrows(IllegalArgumentException.class, action);
        assertEquals(unchanged, row, "Rejected operation must not partially mutate its input");
    }

    private static void assertSnapshotAccessorIsDefensive(Object change, String accessor) {
        PuRequirementAllocationLedger first = (PuRequirementAllocationLedger) read(change, accessor);
        PuRequirementAllocationLedger expected = snapshot(first);
        first.setTenantId("tampered-tenant");
        first.setAllocationKey("tampered-key");
        first.setSourceType("LOW_STOCK");
        first.setCoveredDemandBaseQty(BigDecimal.ONE);
        first.setOrderLineId(999L);
        mutateDates(first);
        assertEquals(expected, read(change, accessor));
        assertNotSame(read(change, accessor), read(change, accessor));
    }

    private static void mutateDates(PuRequirementAllocationLedger row) {
        for (Date date : Arrays.asList(row.getCreateTime(), row.getUpdateTime(), row.getTransferTime(), row.getReleasedAt())) {
            if (date != null) date.setTime(-100);
        }
    }

    /** 独立的测试快照，不复用策略的复制实现。 */
    private static PuRequirementAllocationLedger snapshot(PuRequirementAllocationLedger row) {
        PuRequirementAllocationLedger copy = new PuRequirementAllocationLedger();
        try {
            for (Field field : PuRequirementAllocationLedger.class.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    Object value = field.get(row);
                    field.set(copy, value instanceof Date date ? new Date(date.getTime()) : value);
                }
            }
            return copy;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot snapshot existing ledger fixture", failure);
        }
    }

    private static void set(PuRequirementAllocationLedger row, String property, Class<?> type, Object value) {
        assertDoesNotThrow(() -> PuRequirementAllocationLedger.class.getMethod("set" + property, type).invoke(row, value));
    }

    private static Class<?> policyClass() {
        return assertDoesNotThrow(() -> Class.forName(POLICY),
            "Expected RED until PuRequirementAllocationPolicy is implemented; missing class is not a compile failure");
    }

    private static Method policyMethod(String name, Class<?>[] types) {
        Class<?> policy = policyClass();
        return assertDoesNotThrow(() -> policy.getMethod(name, types), "Missing approved policy API: " + name);
    }

    private static Object create(PuRequirementAllocationLedger seed, Date now) {
        return invoke(policyMethod("createRequestIntent", CREATE_ARGS), null, seed, now);
    }

    private static Object commit(PuRequirementAllocationLedger current, Long orderLineId, Date now) {
        return invoke(policyMethod("commitToOrder", COMMIT_ARGS), null, current, orderLineId, now);
    }

    private static Object release(PuRequirementAllocationLedger current, Date now, String reason) {
        return invoke(policyMethod("releaseRequestIntent", RELEASE_ARGS), null, current, now, reason);
    }

    private static PuRequirementAllocationLedger ledger(Object change) {
        Object row = read(change, "ledger");
        assertTrue(row instanceof PuRequirementAllocationLedger, "Change.ledger() must return a defensive ledger copy");
        return (PuRequirementAllocationLedger) row;
    }

    private static PuRequirementAllocationLedger before(Object change) {
        Object row = read(change, "before");
        assertTrue(row == null || row instanceof PuRequirementAllocationLedger);
        return (PuRequirementAllocationLedger) row;
    }

    private static Date occurredAt(Object change) {
        Object date = read(change, "occurredAt");
        assertTrue(date instanceof Date);
        return (Date) date;
    }

    private static Object read(Object change, String accessor) {
        assertNotNull(change, "Policy must return an immutable change result");
        Method method = assertDoesNotThrow(() -> change.getClass().getMethod(accessor), "Missing Change accessor: " + accessor);
        return invoke(method, change);
    }

    /** 解包校验失败异常，使测试断言策略异常，而非反射内部异常。 */
    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapper) {
            Throwable cause = wrapper.getCause();
            if (cause instanceof RuntimeException failure) throw failure;
            if (cause instanceof Error failure) throw failure;
            throw new AssertionError("Unexpected checked policy exception", cause);
        } catch (IllegalAccessException failure) {
            throw new AssertionError("Approved policy API must be public", failure);
        }
    }
}
