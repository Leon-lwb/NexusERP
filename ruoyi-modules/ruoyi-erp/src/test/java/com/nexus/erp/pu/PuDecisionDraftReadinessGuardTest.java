package com.nexus.erp.pu;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.domain.PuPurchaseRequisitionLine;
import com.nexus.erp.pu.domain.bo.PuPurchaseRequisitionBo;
import com.nexus.erp.pu.service.PuDecisionDraftReadinessGuard;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 生产默认边界；这里的 Mock 不会开放 AI 草稿创建。 */
@Tag("dev")
class PuDecisionDraftReadinessGuardTest {
    private final PuDecisionDraftReadinessGuard guard = new PuDecisionDraftReadinessGuard();

    @Test
    void ordinaryManualDraftDoesNotAcquireAnAiIdentity() {
        PuPurchaseRequisitionBo bo = new PuPurchaseRequisitionBo();
        bo.setSourceType("MANUAL");
        assertDoesNotThrow(() -> guard.require(bo));
    }

    @Test
    void oldAndCaseNormalizedAiSourcesRemainBlocked() {
        for (String source : List.of("AI_DECISION", "ai_decision", " AI_DECISION ")) {
            PuPurchaseRequisitionBo bo = new PuPurchaseRequisitionBo();
            bo.setSourceType(source);
            assertThrows(ServiceException.class, () -> guard.require(bo));
        }
    }

    @Test
    void suppliedRunOrActionOrIdempotencyKeyNeverProvesReadiness() {
        for (int claim = 0; claim < 3; claim++) {
            PuPurchaseRequisitionBo bo = new PuPurchaseRequisitionBo();
            if (claim == 0) bo.setDecisionRunId(99L);
            if (claim == 1) bo.setDecisionActionId(501L);
            if (claim == 2) bo.setDecisionIdempotencyKey("decision-idem-501");
            assertThrows(ServiceException.class, () -> guard.require(bo));
        }
    }

    @Test
    void manualHeaderCannotConcealAiLineProvenance() {
        PuPurchaseRequisitionBo bo = new PuPurchaseRequisitionBo();
        bo.setSourceType("MANUAL");
        PuPurchaseRequisitionLine line = new PuPurchaseRequisitionLine();
        line.setSourceType(" AI_DECISION ");
        line.setSourceId(99L);
        bo.setPuPurchaseRequisitionLineList(List.of(line));
        assertThrows(ServiceException.class, () -> guard.require(bo));
    }

    @Test
    void nullInputRemainsTheServicesOrdinaryValidationResponsibility() {
        assertDoesNotThrow(() -> guard.require(null));
    }
}