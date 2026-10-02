package com.nexus.erp.pu;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PuCertifiedPurchaseLifecyclePresenceTest {
    @Test void lifecycleIsAnImplementedServiceNotAnExternalVerifiedFlag() {
        assertDoesNotThrow(() -> Class.forName("com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService"),
            "Expected RED: certified lifecycle service has not been implemented");
    }
}
