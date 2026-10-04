package com.nexus.erp.pu;

import com.nexus.erp.ai.adapter.ErpPurchaseSourceSchemaVerifier;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.OrderApprovalSource;
import com.nexus.erp.pu.service.*;
import com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 执行真实生命周期与守卫服务，事务及持久来源替身不代表数据库部署验收。 */
class PuCertifiedPurchaseLifecycleReadOnlyValidationTest {
    static final String T = "000001", KEY = "request-0000000001", ORDER = "ORDER_COMMITTED_NOT_TIMELY_SUPPLY";
    static final Date NOW = new Date(1790900000000L);
    @Test void receiptReadOnlyValidationMustNeverEnsureAnExistingGuard() {
        try (var f = fixture()) {
            f.partiallyReceived("APPROVED");
            var service = actualService(f);
            inTransaction(() -> validateReadOnly(service, f.receiptCommand()));
            assertNoWrites(f);
            verify(f.guards).lockVersion(T, 201L);
        }
    }
    @Test void approvedOrderReadbackMustNeverEnsureAnExistingGuard() {
        try (var f = fixture()) {
            f.transferred("ORDERED", "APPROVED", BigDecimal.ZERO, BigDecimal.ZERO); orderProofs(f);
            f.history.add(f.event(Operation.APPROVE_ORDER, 801L, BigDecimal.TEN, BigDecimal.TEN, ORDER, ORDER, 701L)); f.row.setVersion(2);
            var service = actualService(f);
            inTransaction(() -> {
                var events = service.approvedOrderEventsLocked(T, 201L, KEY, 801L);
                assertEquals(1, events.size()); assertTrue(events.get(0).replayed());
            });
            assertNoWrites(f); verify(f.guards).lockVersion(T, 201L);
        }
    }
    @Test void settledOrderReadbackMustNeverEnsureAnExistingGuard() {
        try (var f = fixture()) {
            f.partiallyReceived("CLOSED"); orderProofs(f);
            f.row = PuRequirementAllocationPolicy.releaseOrder(f.row, NOW, "CLOSE_ORDER").ledger(); f.row.setVersion(3);
            f.history.add(f.event(Operation.CLOSE_ORDER, 801L, new BigDecimal("6.0000"), BigDecimal.ZERO, ORDER, "RELEASED", 701L));
            var service = actualService(f);
            inTransaction(() -> {
                var events = service.settledOrderEventsLocked(T, 201L, KEY, 801L);
                assertEquals(1, events.size()); assertEquals(Operation.CLOSE_ORDER, events.get(0).operation()); assertTrue(events.get(0).replayed());
            });
            assertNoWrites(f); verify(f.guards).lockVersion(T, 201L);
        }
    }
    @Test void mutableReceiptPreflightKeepsItsOriginalGuardEnsureSemantics() {
        try (var f = fixture()) {
            f.partiallyReceived("APPROVED"); var service = actualService(f);
            inTransaction(() -> service.validateReceiptLocked(f.receiptCommand()));
            verify(f.guards).ensureGuard(T, 201L); verify(f.guards).lockVersion(T, 201L);
            verify(f.events, never()).insert(any()); verify(f.ledgers, never()).updateWithVersion(any(), any(), any());
        }
    }
    @Test void mutableReceiptReplayStillUsesOriginalGuardPathAndOriginalPermanentEvent() {
        try (var f = fixture()) {
            f.partiallyReceived("APPROVED"); var service = actualService(f);
            when(f.events.lockByKey(T, f.history.get(2).getEventKey())).thenReturn(f.history.get(2));
            inTransaction(() -> {
                var event = service.consumeReceiptLocked(f.receiptCommand());
                assertTrue(event.replayed()); assertEquals(f.history.get(2).getEventId(), event.eventId());
            });
            verify(f.guards).ensureGuard(T, 201L); verify(f.guards).lockVersion(T, 201L);
            verify(f.events, never()).insert(any()); verify(f.ledgers, never()).updateWithVersion(any(), any(), any());
            verify(f.guards, never()).advanceVersion(any(), any(), any());
        }
    }
    static PuCertifiedPurchaseLifecycleServiceTest.Fixture fixture() {
        var f = new PuCertifiedPurchaseLifecycleServiceTest.Fixture();
        when(f.guards.lockVersion(T, 201L)).thenReturn(5);
        return f;
    }
    static PuCertifiedPurchaseLifecycleService actualService(PuCertifiedPurchaseLifecycleServiceTest.Fixture f) {
        var schema = mock(ErpPurchaseSourceSchemaVerifier.class);
        var locks = new PuRequirementMaterialLockService(f.guards, schema);
        return new PuCertifiedPurchaseLifecycleService(f.executor, f.qualification, locks, f.guards, f.ledgers, f.events, f.mapper);
    }
    static void orderProofs(PuCertifiedPurchaseLifecycleServiceTest.Fixture f) {
        PuCertifiedPurchaseOrderWorkflowMapperTest.stubOrderDefinitions(f.mapper);
        when(f.mapper.lockOrderAllocations(T, 801L)).thenAnswer(a -> List.of(f.row));
        when(f.mapper.lockOrderApprovalSource(T, 801L)).thenReturn(new OrderApprovalSource(T, 801L, 1501L, 1801L, 1,
            new BigDecimal("113"), new BigDecimal("100"), new BigDecimal("13"), "2026-10-12", 201L, 501L, KEY,
            "d".repeat(64), "e".repeat(64), 902L, 903L, "e".repeat(64), "pu:order:terms:confirm", NOW));
    }
    static void inTransaction(Runnable work) {
        boolean previous = TransactionSynchronizationManager.isActualTransactionActive();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { work.run(); } finally { TransactionSynchronizationManager.setActualTransactionActive(previous); }
    }
    static void validateReadOnly(PuCertifiedPurchaseLifecycleService service, ReceiptCommand command) {
        service.validateReceiptReadOnlyLocked(command);
    }
    static void assertNoWrites(PuCertifiedPurchaseLifecycleServiceTest.Fixture f) {
        verifyNoInteractions(f.executor);
        verify(f.guards, never()).ensureGuard(anyString(), anyLong());
        verify(f.guards, never()).advanceVersion(any(), any(), any());
        verify(f.ledgers, never()).updateWithVersion(any(), any(), any());
        verify(f.events, never()).insert(any());
        verify(f.mapper, never()).updateOrderCounters(any(), any(), any(), any(), any(), any());
        verify(f.mapper, never()).updateOrderStatus(any(), any(), any(), any(), any());
    }
}
