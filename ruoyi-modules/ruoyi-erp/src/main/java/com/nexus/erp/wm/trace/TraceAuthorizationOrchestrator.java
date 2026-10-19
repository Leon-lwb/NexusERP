package com.nexus.erp.wm.trace;

import com.nexus.common.trace.port.ProductionWorkOrderReferencePort;
import com.nexus.erp.wm.domain.bo.WmTraceAuthorizationBo;
import com.nexus.erp.wm.service.WmTraceAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;

/**
 * 来源额度授权的跨模块编排；先核验外部事实，再进入所属库事务。
 */
@Service
@RequiredArgsConstructor
public class TraceAuthorizationOrchestrator {
    private final ProductionWorkOrderReferencePort references;
    private final WmTraceAuthorizationService source;
    private final ErpTraceContext context;

    public WmTraceAuthorizationService.Authorization productionLot(WmTraceAuthorizationBo command) {
        preflight(command);
        return source.productionLot(command);
    }

    public List<WmTraceAuthorizationService.Authorization> reserveBackflush(
            WmTraceAuthorizationBo command) {
        preflight(command);
        return source.reserveBackflush(command);
    }

    private void preflight(WmTraceAuthorizationBo command) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("生产来源核验必须在ERP写事务之前完成");
        }
        if (command == null) {
            throw new IllegalArgumentException("生产来源授权请求为空");
        }
        references.verify(context.tenant(), command.getMesWoId(), command.getErpWoId());
    }
}
