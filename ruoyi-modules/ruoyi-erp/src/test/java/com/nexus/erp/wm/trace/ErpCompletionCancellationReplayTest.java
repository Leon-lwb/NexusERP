package com.nexus.erp.wm.trace;

import com.nexus.common.trace.*;
import com.nexus.common.trace.port.TraceEventHandler;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 实际 Spring 事务代理下，取消后的重放只读查询与原结果 ACK。
 */
class ErpCompletionCancellationReplayTest extends ErpCompletionCancellationTestSupport {
    ErpTraceJournalService journalProxy() {
        var advice = new TransactionInterceptor();
        advice.setTransactionManager(et.getTransactionManager());
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        advice.afterPropertiesSet();
        var factory = new ProxyFactory(new ErpTraceJournalService(erp, json, context));
        factory.setProxyTargetClass(true);
        factory.addAdvice(advice);
        return (ErpTraceJournalService) factory.getProxy();
    }

    @Test
    void replayLookupUsesReadonlyNewTransactionAndLeavesAcknowledgementsUntouched() {
        deliver();
        int outbox = erp.queryForObject("SELECT COUNT(*) FROM wm_trace_outbox", Integer.class);
        var journal = journalProxy();
        var checks = new AtomicInteger();
        et.executeWithoutResult(
                s -> {
                    Object parent =
                            TransactionSynchronizationManager.getResource(erp.getDataSource());
                    doAnswer(
                                    call -> {
                                        assertTrue(
                                                TransactionSynchronizationManager
                                                        .isActualTransactionActive());
                                        assertTrue(
                                                TransactionSynchronizationManager
                                                        .isCurrentTransactionReadOnly());
                                        assertNotSame(
                                                parent,
                                                TransactionSynchronizationManager.getResource(
                                                        erp.getDataSource()));
                                        checks.incrementAndGet();
                                        return null;
                                    })
                            .when(context)
                            .requireTenant(TENANT);
                    assertEquals(originalResult(), journal.findApplied(original).resultPayload());
                    assertSame(
                            parent,
                            TransactionSynchronizationManager.getResource(erp.getDataSource()));
                    assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                });
        assertEquals(1, checks.get());
        assertEquals(
                outbox, erp.queryForObject("SELECT COUNT(*) FROM wm_trace_outbox", Integer.class));
        verify(context, times(1)).requireWrite(TENANT); // 只有前面的首次取消写入。
    }

    @Test
    void appliedOriginalCanReplayWithNullPreparationWithoutCallingBusiness() {
        deliver();
        var journal = journalProxy();
        var posting = mock(TraceEventHandler.class);
        when(posting.targetModule()).thenReturn("ERP");
        when(posting.supports(original.eventType())).thenReturn(true);
        var found = journal.findApplied(original);
        assertNotNull(found);
        assertEquals(found, journal.accept(original, posting, null));
        verify(posting, never()).prepare(any());
        verify(posting, never()).apply(any(), any());
    }

    @Test
    void lookupRejectsTenantBeforeReadingTargetState() {
        deliver();
        doThrow(new IllegalStateException("tenant rejected")).when(context).requireTenant(TENANT);
        assertThrows(IllegalStateException.class, () -> journalProxy().findApplied(original));
        assertEquals("3", scalar("SELECT COUNT(*) FROM wm_trace_outbox"));
    }
}
