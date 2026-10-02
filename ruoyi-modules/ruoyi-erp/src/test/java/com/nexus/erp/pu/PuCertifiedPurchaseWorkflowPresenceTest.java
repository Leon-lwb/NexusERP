package com.nexus.erp.pu;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PuCertifiedPurchaseWorkflowPresenceTest {
    @Test void documentWorkflowMustCreateRealOrdersAndCloseWholeDocuments() {
        assertDoesNotThrow(()->Class.forName("com.nexus.erp.pu.service.PuCertifiedPurchaseWorkflowService"),"Expected RED: grouped document workflow is not implemented");
    }
    @Test void certifiedDocumentApiCannotBeSubstitutedByOldTransactionalController() {
        assertDoesNotThrow(()->Class.forName("com.nexus.erp.pu.controller.PuCertifiedPurchaseWorkflowController"),"Expected RED: certified document controller is not implemented");
    }
}
