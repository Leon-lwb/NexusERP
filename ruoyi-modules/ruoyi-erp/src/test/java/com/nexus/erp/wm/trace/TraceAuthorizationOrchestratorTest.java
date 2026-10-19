package com.nexus.erp.wm.trace;

import com.nexus.common.trace.port.ProductionWorkOrderReferencePort;
import com.nexus.erp.wm.domain.bo.WmTraceAuthorizationBo;
import com.nexus.erp.wm.service.WmTraceAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class TraceAuthorizationOrchestratorTest {
    @Test
    void nonexistentMesWorkOrderCannotReserveWarehouseStock() {
        var references = mock(ProductionWorkOrderReferencePort.class);
        var source = mock(WmTraceAuthorizationService.class);
        var context = mock(ErpTraceContext.class);
        when(context.tenant()).thenReturn("000001");
        doThrow(new IllegalStateException("missing MES source"))
                .when(references)
                .verify("000001", 2L, 1L);
        var command = new WmTraceAuthorizationBo();
        command.setMesWoId(2L);
        command.setErpWoId(1L);
        var orchestrator = new TraceAuthorizationOrchestrator(references, source, context);
        assertThrows(IllegalStateException.class, () -> orchestrator.reserveBackflush(command));
        verifyNoInteractions(source);
    }

    @Test
    void crossDatabasePreflightCannotRunWhileHoldingABusinessTransaction() {
        var references = mock(ProductionWorkOrderReferencePort.class);
        var source = mock(WmTraceAuthorizationService.class);
        var orchestrator =
                new TraceAuthorizationOrchestrator(references, source, mock(ErpTraceContext.class));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(
                    IllegalStateException.class,
                    () -> orchestrator.productionLot(new WmTraceAuthorizationBo()));
            verifyNoInteractions(references, source);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }
}
