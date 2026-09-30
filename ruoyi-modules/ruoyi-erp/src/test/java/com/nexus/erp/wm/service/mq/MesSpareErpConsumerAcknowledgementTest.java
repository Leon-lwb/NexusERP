package com.nexus.erp.wm.service.mq;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.service.IPuPurchaseRequisitionService;
import com.nexus.erp.wm.service.IWmSpareStockPostingService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

/** 共享容器默认使用 MANUAL；仅接收 Map 的监听器必须显式覆盖该确认模式。 */
@Tag("dev")
class MesSpareErpConsumerAcknowledgementTest {
    @Test
    void mapOnlyListenerMustUseAutomaticAcknowledgementInsteadOfLeavingManualDeliveriesUnacked() throws Exception {
        RabbitListener listener = MesSpareErpConsumer.class.getMethod("onMessage", Map.class)
            .getAnnotation(RabbitListener.class);
        assertEquals("AUTO", listener.ackMode());
    }

    @Test
    void auditOutageMustUseDedicatedBackoffFactoryInsteadOfGlobalImmediateRequeue() throws Exception {
        RabbitListener listener = MesSpareErpConsumer.class.getMethod("onMessage", Map.class)
            .getAnnotation(RabbitListener.class);
        assertEquals("mesSpareRabbitListenerContainerFactory", listener.containerFactory());
    }
    @Test
    void pausedPurchaseRequiresDurableAuditDependencyRatherThanAnUnboundedPermanentFailure() {
        assertTrue(Arrays.stream(MesSpareErpConsumer.class.getDeclaredFields())
            .anyMatch(field -> field.getName().equals("purchasePauseAuditService")));
    }
    @Test
    void pausedProcurementReturnsNormallyOnlyAfterCommittedAuditReceipt() {
        var stock = mock(IWmSpareStockPostingService.class);
        var purchase = mock(IPuPurchaseRequisitionService.class);
        var audit = mock(MesSparePurchasePauseAuditService.class);
        var payload = pausedPayload();
        when(audit.persist(payload)).thenReturn(123L);
        var consumer = new MesSpareErpConsumer(stock, purchase, audit);
        assertDoesNotThrow(() -> consumer.onMessage(payload));
        verify(audit).persist(payload);
        verifyNoInteractions(stock, purchase);
    }

    @Test
    void auditFailureCannotBecomeSuccessfulAcknowledgementOnRepeatedDelivery() {
        var stock = mock(IWmSpareStockPostingService.class);
        var purchase = mock(IPuPurchaseRequisitionService.class);
        var audit = mock(MesSparePurchasePauseAuditService.class);
        var payload = pausedPayload();
        RuntimeException failure = new IllegalStateException("audit temporarily unavailable");
        when(audit.persist(payload)).thenThrow(failure);
        var consumer = new MesSpareErpConsumer(stock, purchase, audit);
        for (int delivery = 0; delivery < 2; delivery++) {
            assertSame(failure, assertThrows(RuntimeException.class, () -> consumer.onMessage(payload)));
        }
        verify(audit, times(2)).persist(payload);
        verifyNoInteractions(stock, purchase);
    }

    @Test
    void invalidAuditReceiptCannotAuthorizeConsumerSuccess() {
        var stock = mock(IWmSpareStockPostingService.class);
        var purchase = mock(IPuPurchaseRequisitionService.class);
        var audit = mock(MesSparePurchasePauseAuditService.class);
        var payload = pausedPayload();
        when(audit.persist(payload)).thenReturn(null);
        var consumer = new MesSpareErpConsumer(stock, purchase, audit);
        assertThrows(ServiceException.class, () -> consumer.onMessage(payload));
        verifyNoInteractions(stock, purchase);
    }

    private Map<String, Object> pausedPayload() {
        return Map.of("bizType", "SPARE_PURCHASE", "tenantId", "000123", "bizKey", "EQSP-PR:000123:42");
    }}