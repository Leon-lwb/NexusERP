package com.nexus.erp.pu;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PuCertifiedPurchaseOrderWorkflowPresenceTest {
    @Test void realOrderPricingApprovalAndClosureHaveADedicatedWriterNotAnotherReader() {
        for(String name:List.of("service.PuCertifiedPurchaseOrderWorkflowService","mapper.PuCertifiedPurchaseOrderWorkflowMapper","controller.PuCertifiedPurchaseOrderWorkflowController"))
            assertDoesNotThrow(()->Class.forName("com.nexus.erp.pu."+name),"Expected RED: actual order writer entry missing: "+name);
    }
    @Test void orderApprovalHasAPermanentLifecycleOperation() {
        assertTrue(Arrays.stream(com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.Operation.values())
            .anyMatch(v->v.name().equals("APPROVE_ORDER")),"Expected RED: order approval sequence event missing");
    }
    @Test void orderTermsRequireActualDefinitionGateAndAnUnexecutedSeparateMigration() {
        assertDoesNotThrow(()->com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleSourceGate.class.getMethod("requireOrderDefinitions"),
            "Expected RED: approved quote actual definition gate missing");
        Path root=Path.of("").toAbsolutePath();
        while(root!=null && !Files.isRegularFile(root.resolve("AGENTS.md")))root=root.getParent();
        assertNotNull(root);
        Path sql=root.resolve("script/sql/update/2026-10-03-certified-purchase-order-terms-ry-erp.sql");
        assertTrue(Files.exists(sql),"Expected RED: supplier confirmation storage contract missing");
    }
}
