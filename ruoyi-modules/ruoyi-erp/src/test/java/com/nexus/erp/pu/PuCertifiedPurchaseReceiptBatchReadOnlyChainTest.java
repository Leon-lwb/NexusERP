package com.nexus.erp.pu;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceSchemaVerifier;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.*;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseReceiptBatchMapper;
import com.nexus.erp.pu.service.*;
import com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.Operation;
import com.nexus.erp.wm.service.WmCertifiedReceiptPostingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.nexus.erp.pu.PuCertifiedPurchaseLifecycleReadOnlyValidationTest.*;

/** 运行真实批次、生命周期及守卫调用链；物理过账与数据库读数仍是离线替身。 */
class PuCertifiedPurchaseReceiptBatchReadOnlyChainTest {
    @Test void realReadbackChainReconstructsCommittedReceiptWithoutEnsuringOrWriting() {
        try (var f = fixture()) {
            f.partiallyReceived("APPROVED"); nativeReceipt(f);
            var service = batch(f);
            inTransaction(() -> {
                var receipt = service.readCommittedReceiptLocked(T, 1001L, 801L).orElseThrow();
                assertTrue(receipt.replayed()); assertEquals(1, receipt.lines().size());
                assertEquals(f.history.get(2).getEventId(), receipt.lines().get(0).event().eventId());
                assertEquals(new BigDecimal("6.0000"), receipt.lines().get(0).event().occupiedAfter());
            });
            verify(f.guards, times(2)).lockVersion(T, 201L); assertNoWrites(f);
        }
    }
    @Test void noEventsInRealChainReturnEmptyOnlyAfterReadOnlyContextValidation() {
        try (var f = fixture()) {
            f.transferred("ORDERED", "APPROVED", BigDecimal.ZERO, BigDecimal.ZERO); f.receipt(); nativeReceipt(f);
            var service = batch(f);
            inTransaction(() -> assertTrue(service.readCommittedReceiptLocked(T, 1001L, 801L).isEmpty()));
            verify(f.events).lockByAllocation(T, 101L); verify(f.guards, times(2)).lockVersion(T, 201L); assertNoWrites(f);
        }
    }
    @Test void currentReleasedBalanceCannotReplaceOriginalReceiptEventAmounts() {
        try (var f = fixture()) {
            f.partiallyReceived("CLOSED"); nativeReceipt(f);
            f.row = PuRequirementAllocationPolicy.releaseOrder(f.row, NOW, "CLOSE_ORDER").ledger(); f.row.setVersion(3);
            f.history.add(f.event(Operation.CLOSE_ORDER, 801L, new BigDecimal("6.0000"), BigDecimal.ZERO, ORDER, "RELEASED", 701L));
            var service = batch(f);
            inTransaction(() -> {
                var event = service.readCommittedReceiptLocked(T, 1001L, 801L).orElseThrow().lines().get(0).event();
                assertEquals(0, f.row.getOccupiedBaseQty().signum());
                assertEquals(BigDecimal.TEN, event.occupiedBefore()); assertEquals(new BigDecimal("6.0000"), event.occupiedAfter());
                assertEquals(ORDER, event.fromStatus()); assertEquals(ORDER, event.toStatus());
            });
            assertNoWrites(f);
        }
    }
    @ParameterizedTest @ValueSource(strings = {"missing-guard", "counter-net-equal", "history-gap", "demand-tenant", "root-unit", "prior-state"})
    void realReadOnlyContextMustRejectChangedFactsWithoutAnyWrite(String corruption) {
        try (var f = fixture()) {
            f.partiallyReceived("APPROVED"); nativeReceipt(f);
            switch (corruption) {
                case "missing-guard" -> when(f.guards.lockVersion(T, 201L)).thenReturn(null);
                case "counter-net-equal" -> f.order("APPROVED", new BigDecimal("5.0000"), new BigDecimal("1.0000"));
                case "history-gap" -> f.history.get(1).setEventSequence(0);
                case "demand-tenant" -> when(f.mapper.lockDemandDetail(T, 301L)).thenReturn(new DemandDetail("000002", 301L, 1401L, 201L, 1L, "0"));
                case "root-unit" -> when(f.mapper.lockRootUnit(T, 501L)).thenReturn(new RootUnit(T, 501L, "件", 1L, 501L, new BigDecimal("2"), 4, "uom-v1", NOW, 1L, "0"));
                case "prior-state" -> f.history.get(2).setToStatus("RELEASED");
            }
            var service = batch(f);
            inTransaction(() -> assertThrows(ServiceException.class, () -> service.readCommittedReceiptLocked(T, 1001L, 801L)));
            assertNoWrites(f);
        }
    }
    static void nativeReceipt(PuCertifiedPurchaseLifecycleServiceTest.Fixture f) {
        when(f.mapper.lockStockEffects(T, "WM_MATERIAL_RECEIVE", 1001L)).thenReturn(List.of(new StockEffect(T, 2102L,
            "RECEIVE_IN", 201L, "501", "WM_MATERIAL_RECEIVE", 1001L, 1101L, 1,
            new BigDecimal("4.0000"), BigDecimal.ZERO, new BigDecimal("4.0000"), 3002L, 901L, "0")));
    }
    static PuCertifiedPurchaseReceiptBatchService batch(PuCertifiedPurchaseLifecycleServiceTest.Fixture f) {
        var mapper = mock(PuCertifiedPurchaseReceiptBatchMapper.class);
        var head = new ReceiveHead(T, 1001L, 801L, "FINISHED", "0");
        when(mapper.readReceiptHead(T, 1001L)).thenReturn(head); when(mapper.lockReceiptHead(T, 1001L)).thenReturn(head);
        when(mapper.readMaterialHints(T, 1001L)).thenReturn(List.of(201L));
        when(mapper.lockReceiptLines(T, 1001L)).thenReturn(List.of(f.receiveLine));
        var order = f.mapper.lockOrderHead(T, 801L); var req = f.mapper.lockRequisitionHead(T, 601L); var command = f.mapper.lockCommand(T, KEY);
        when(mapper.lockOrderHead(T, 801L)).thenReturn(order); when(mapper.lockOrderLines(T, 801L)).thenReturn(List.of(f.orderLine));
        when(mapper.lockRequisitionHeadsForOrder(T, 801L)).thenReturn(List.of(req));
        when(mapper.lockRequisitionLinesForOrder(T, 801L)).thenReturn(List.of(f.reqLine));
        when(mapper.lockCommandsForOrder(T, 801L)).thenReturn(List.of(command));
        when(mapper.lockAllocationsForOrder(T, 801L)).thenReturn(List.of(f.row));
        String key = "LIFECYCLE:" + PuCertifiedPurchaseLifecycleServiceTest.hash(T, "allocation-101", "RECEIPT_POSTED", "1101");
        when(mapper.lockReceiptEvent(T, key)).thenReturn(f.history.stream().filter(e -> key.equals(e.getEventKey())).findFirst().orElse(null));
        var locks = new PuRequirementMaterialLockService(f.guards, mock(ErpPurchaseSourceSchemaVerifier.class));
        var lifecycle = new PuCertifiedPurchaseLifecycleService(f.executor, f.qualification, locks, f.guards, f.ledgers, f.events, f.mapper);
        return new PuCertifiedPurchaseReceiptBatchService(f.executor, f.qualification, locks, mapper, f.mapper, lifecycle, mock(WmCertifiedReceiptPostingService.class));
    }
}
