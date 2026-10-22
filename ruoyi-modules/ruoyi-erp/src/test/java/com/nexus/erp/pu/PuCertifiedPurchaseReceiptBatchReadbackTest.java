package com.nexus.erp.pu;

import static com.nexus.erp.pu.PuCertifiedPurchaseReceiptBatchServiceTest.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.domain.PuRequirementAllocationEvent;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.*;
import com.nexus.erp.pu.service.PuCertifiedPurchaseReceiptBatchService.BatchReceipt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** 只读恢复边界使用持久来源替身，不表示真实数据库已迁移或生产已验收。 */
class PuCertifiedPurchaseReceiptBatchReadbackTest {
    @Test void fullCommittedBatchReturnsOriginalEventFieldsWithoutConsumption() {
        try (var f = fixture()) {
            List<PuRequirementAllocationEvent> events = committed(f);
            events.get(0).setOccupiedBaseBefore(TEN.subtract(BigDecimal.ONE));
            events.get(0).setOccupiedBaseAfter(TEN.subtract(BigDecimal.ONE).subtract(THREE));
            var result = read(f, T, 901L, 801L).orElseThrow();
            assertTrue(result.certified()); assertTrue(result.replayed());
            assertEquals(T, result.tenantId()); assertEquals(901L, result.receiveId()); assertEquals(801L, result.orderId());
            assertEquals(List.of(9001L, 9002L), result.lines().stream().map(r -> r.receiveLineId()).toList());
            for (int i = 0; i < events.size(); i++) {
                var original = events.get(i); var actual = result.lines().get(i).event();
                assertEquals(original.getEventId(), actual.eventId()); assertEquals(original.getAllocationId(), actual.allocationId());
                assertEquals(original.getEventType(), actual.operation().name());
                assertEquals(original.getFromStatus(), actual.fromStatus()); assertEquals(original.getToStatus(), actual.toStatus());
                assertEquals(original.getOccupiedBaseBefore(), actual.occupiedBefore()); assertEquals(original.getOccupiedBaseAfter(), actual.occupiedAfter());
                assertEquals(original.getRequisitionLineId(), actual.requisitionLineId()); assertEquals(original.getOrderLineId(), actual.orderLineId());
                assertEquals(original.getActorId(), actual.actorId()); assertTrue(actual.replayed());
            }
            verify(f.locks).lockExisting(T, List.of(201L));
            verify(f.lifecycle, times(2)).validateReceiptReadOnlyLocked(any());
            assertNoWrites(f);
        }
    }
    @Test void absentPermanentEventsOnlyReturnEmptyAfterFullSourceAndTerminalChecks() {
        try (var f = fixture()) {
            assertTrue(read(f, T, 901L, 801L).isEmpty());
            verify(f.posting).validatePostedLocked(T, 901L);
            verify(f.mapper, times(2)).lockReceiptHead(T, 901L);
            verify(f.mapper, times(2)).lockReceiptLines(T, 901L);
            verify(f.physical).lockStockEffects(T, "WM_MATERIAL_RECEIVE", 901L);
            verify(f.lifecycle, times(2)).validateReceiptReadOnlyLocked(any());
            assertNoWrites(f);
        }
    }
    @Test void partialPermanentBatchCannotBecomeSuccessfulOrEmpty() {
        try (var f = fixture()) {
            var row = f.allocations.get(0); row.setVersion(3);
            when(f.mapper.lockReceiptEvent(T, eventKey(row, 9001L))).thenReturn(f.event(row, 9001L));
            assertThrows(ServiceException.class, () -> read(f, T, 901L, 801L));
            assertNoWrites(f);
        }
    }
    @Test void qualificationFailureCannotStartAnotherTransactionOrReadAnySource() {
        try (var f = fixture()) {
            doThrow(new ServiceException("缺少已拥有的 ERP RR")).when(f.qualification).requireSourceChainTransaction();
            assertThrows(ServiceException.class, () -> read(f, T, 901L, 801L));
            verifyNoInteractions(f.executor, f.mapper, f.physical, f.locks, f.lifecycle, f.posting);
        }
    }
    @Test void failedEventReadIsUnknownInsteadOfNoConsumption() {
        try (var f = fixture()) {
            when(f.mapper.lockReceiptEvent(anyString(), anyString())).thenThrow(new IllegalStateException("测试连接秘密"));
            var failure = assertThrows(ServiceException.class, () -> read(f, T, 901L, 801L));
            assertFalse(failure.getMessage().contains("测试连接秘密")); assertNull(failure.getCause());
            assertNoWrites(f);
        }
    }
    @Test void receiptIdentityDriftAtEndCannotReturnCommittedEvidence() {
        try (var f = fixture()) {
            committed(f);
            when(f.mapper.lockReceiptHead(T, 901L)).thenReturn(f.receiveHead, new ReceiveHead(T, 901L, 802L, "FINISHED", "0"));
            assertThrows(ServiceException.class, () -> read(f, T, 901L, 801L));
            assertNoWrites(f);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"caller", "receipt-head", "receipt-line", "order-head", "order-line", "requisition-head", "requisition-line", "command", "ledger", "physical", "event"})
    void everyTenantBoundaryRefusesReadbackInsteadOfGuessingConsumption(String boundary) {
        try (var f = fixture()) {
            var events = committed(f);
            switch (boundary) {
                case "receipt-head" -> f.receiveHead = new ReceiveHead("000002", 901L, 801L, "FINISHED", "0");
                case "receipt-line" -> f.receives = List.of(new ReceiveLine("000002", 9001L, 901L, 701L, 201L, "501", THREE, "0"));
                case "order-head" -> f.order = new OrderHead("000002", 801L, 601L, "APPROVED", "0");
                case "order-line" ->
                        f.orders =
                                List.of(
                                        new OrderLine(
                                                "000002",
                                                701L,
                                                801L,
                                                401L,
                                                201L,
                                                "501",
                                                TEN,
                                                THREE,
                                                BigDecimal.ZERO,
                                                "PENDING",
                                                "0"));
                case "requisition-head" -> f.heads = List.of(new RequisitionHead("000002", 601L, "PR601", "MRP", "ORDERED", KEY, 2, "0"));
                case "requisition-line" -> f.reqLines = List.of(new RequisitionLine("000002", 401L, 601L, 201L, "501", TEN, TEN, "MRP", 301L, "2026-10-10", "0"));
                case "command" -> { var c = f.commands.get(0); f.commands = List.of(new CommandSource("000002", c.requestKey(), c.requestHash(), c.actorId(), c.entryType(), c.materialId(), c.vendorId(), c.requisitionId(), c.requisitionNo(), c.sourceFingerprint(), c.resultFingerprint(), c.commandStatus(), c.catalogFingerprint(), c.windowEnd())); }
                case "ledger" -> f.allocations.get(0).setTenantId("000002");
                case "physical" -> { var e = f.effects.get(0); f.effects = List.of(new StockEffect("000002", e.txId(), e.txType(), e.materialId(), e.baseUomId(), e.sourceDocType(), e.sourceDocId(), e.sourceDocLineId(), e.txFlag(), e.quantity(), e.beforeQuantity(), e.afterQuantity(), e.stockId(), e.actorId(), e.delFlag()), f.effects.get(1)); }
                case "event" -> events.get(0).setTenantId("000002");
            }
            f.refresh();
            assertThrows(ServiceException.class, () -> read(f, "caller".equals(boundary) ? "000002" : T, 901L, 801L));
            assertNoWrites(f);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"id", "allocation", "key", "type", "sequence-null", "sequence-zero", "sequence-future", "requisition", "order", "actor", "quantity-null", "quantity-changed"})
    void damagedPermanentEventCannotAuthorizeRecovery(String corruption) {
        try (var f = fixture()) {
            var event = committed(f).get(0);
            switch (corruption) {
                case "id" -> event.setEventId(null);
                case "allocation" -> event.setAllocationId(999L);
                case "key" -> event.setEventKey("其他永久键");
                case "type" -> event.setEventType("RETURN_POSTED");
                case "sequence-null" -> event.setEventSequence(null);
                case "sequence-zero" -> event.setEventSequence(0);
                case "sequence-future" -> event.setEventSequence(8);
                case "requisition" -> event.setRequisitionLineId(999L);
                case "order" -> event.setOrderLineId(999L);
                case "actor" -> event.setActorId(999L);
                case "quantity-null" -> event.setOccupiedBaseBefore(null);
                case "quantity-changed" -> event.setOccupiedBaseAfter(TEN);
            }
            assertThrows(ServiceException.class, () -> read(f, T, 901L, 801L));
            assertNoWrites(f);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"guard", "guard-key", "guard-version", "native-effect", "posting", "command", "frozen-source", "schema", "expected-order", "legacy"})
    void absentOrChangedSourceCannotProduceCommittedReceipt(String corruption) {
        try (var f = fixture()) {
            committed(f);
            switch (corruption) {
                case "guard" -> when(f.locks.lockExisting(T, List.of(201L))).thenReturn(Map.of());
                case "guard-key" -> when(f.locks.lockExisting(T, List.of(201L))).thenReturn(Map.of(202L, 5));
                case "guard-version" -> when(f.locks.lockExisting(T, List.of(201L))).thenReturn(Collections.singletonMap(201L, null));
                case "native-effect" -> f.effects = List.of();
                case "posting" -> doThrow(new ServiceException("原物理过账指纹变化")).when(f.posting).validatePostedLocked(T, 901L);
                case "command" -> f.commands = List.of(f.command("COMPENSATED"));
                case "frozen-source" -> f.reqLines = List.of(new RequisitionLine(T, 401L, 601L, 201L, "501", TEN, TEN, "MRP", 999L, "2026-10-10", "0"), f.reqLines.get(1));
                case "schema" -> when(f.physical.lifecycleQuantityDefinitions()).thenReturn(List.of());
                case "legacy" -> {
                    f.order = new OrderHead(T, 801L, null, "APPROVED", "0");
                    f.heads = List.of(); f.reqLines = List.of(); f.commands = List.of(); f.allocations = List.of();
                    f.orders =
                            List.of(
                                    new OrderLine(
                                            T,
                                            701L,
                                            801L,
                                            null,
                                            201L,
                                            "501",
                                            TEN,
                                            BigDecimal.ZERO,
                                            BigDecimal.ZERO,
                                            "PENDING",
                                            "0"));
                    f.receives = List.of(f.receives.get(0));
                }
            }
            f.refresh();
            assertThrows(ServiceException.class, () -> read(f, T, 901L, "expected-order".equals(corruption) ? 802L : 801L));
            assertNoWrites(f);
        }
    }
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void terminalQualificationFailureRefusesBothCommittedAndUnobservedReadback(boolean committed) {
        try (var f = fixture()) {
            if (committed) committed(f);
            var reads = new AtomicInteger(); var terminal = new AtomicBoolean();
            when(f.mapper.lockReceiptHead(T, 901L)).thenAnswer(i -> { if (reads.incrementAndGet() == 2) terminal.set(true); return f.receiveHead; });
            doAnswer(i -> { if (terminal.get()) throw new ServiceException("末端事务归属丢失"); return null; }).when(f.qualification).requireSourceChainTransaction();
            assertThrows(ServiceException.class, () -> read(f, T, 901L, 801L));
            assertTrue(terminal.get()); assertNoWrites(f);
        }
    }
    @Test void absentEventsStillCannotSkipTerminalReceiptLineIntegrity() {
        try (var f = fixture()) {
            when(f.mapper.lockReceiptLines(T, 901L)).thenReturn(f.receives).thenReturn(List.of(f.receives.get(0)));
            assertThrows(ServiceException.class, () -> read(f, T, 901L, 801L));
            assertNoWrites(f);
        }
    }
    @Test void sortedExistingGuardsPrecedeMixedMaterialRejectionWithoutProvisioning() {
        try (var f = fixture()) {
            when(f.mapper.readMaterialHints(T, 901L)).thenReturn(List.of(202L, 201L, 201L));
            when(f.locks.lockExisting(T, List.of(201L, 202L))).thenReturn(Map.of(201L, 5, 202L, 9));
            assertThrows(ServiceException.class, () -> read(f, T, 901L, 801L));
            verify(f.locks).lockExisting(T, List.of(201L, 202L)); assertNoWrites(f);
        }
    }
    static Fixture fixture() {
        var f = new Fixture();
        when(f.locks.lockExisting(T, List.of(201L))).thenReturn(Map.of(201L, 5));
        return f;
    }
    static List<PuRequirementAllocationEvent> committed(Fixture f) {
        List<PuRequirementAllocationEvent> events = new ArrayList<>();
        for (var row : f.allocations) {
            row.setVersion(7); long lineId = row.getOrderLineId() + 8300;
            var event = f.event(row, lineId); events.add(event);
            when(f.mapper.lockReceiptEvent(T, eventKey(row, lineId))).thenReturn(event);
        }
        return events;
    }
    static Optional<BatchReceipt> read(Fixture f, String tenant, Long receiveId, Long orderId) {
        return f.service.readCommittedReceiptLocked(tenant, receiveId, orderId);
    }
    static void assertNoWrites(Fixture f) {
        verifyNoInteractions(f.executor);
        verify(f.locks, never()).lock(anyString(), anyCollection());
        verify(f.lifecycle, never()).consumeReceiptLocked(any());
        verify(f.lifecycle, never()).validateReceiptLocked(any());
        assertTrue(mockingDetails(f.locks).getInvocations().stream().allMatch(i -> i.getMethod().getName().equals("lockExisting")));
        assertTrue(mockingDetails(f.lifecycle).getInvocations().stream().allMatch(i -> i.getMethod().getName().equals("validateReceiptReadOnlyLocked")));
        assertTrue(mockingDetails(f.mapper).getInvocations().stream().allMatch(i -> i.getMethod().getName().startsWith("read") || i.getMethod().getName().startsWith("lock")));
    }
}
