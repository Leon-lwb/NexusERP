package com.nexus.erp.pu;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
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
    @Test void orderTermsRequireActualDefinitionGateAndInstallationSchema() throws Exception {
        assertDoesNotThrow(()->com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleSourceGate.class.getMethod("requireOrderDefinitions"),
            "Expected RED: approved quote actual definition gate missing");
        Path root=Path.of("").toAbsolutePath();
        while(root!=null && !Files.isRegularFile(root.resolve("AGENTS.md")))root=root.getParent();
        assertNotNull(root);
        String sql=Files.readString(root.resolve("script/sql/ry-erp.sql"));
        for(String[] contract:List.of(
            new String[]{"pu_certified_purchase_order_terms","quote_id","tenant_id,order_id,quote_version"},
            new String[]{"pu_certified_purchase_order_terms_confirmation","confirmation_id","tenant_id,quote_id"},
            new String[]{"pu_certified_purchase_order_operation","command_id","tenant_id,order_id,operation"})) {
            var ddl=Pattern.compile("(?is)CREATE\\s+TABLE\\s+`?"+Pattern.quote(contract[0])+"`?\\s*\\(.*?\\)\\s*ENGINE[^;]*;").matcher(sql);
            assertTrue(ddl.find(),"Missing installation DDL: "+contract[0]);
            String table=ddl.group().replace("`","");
            assertTrue(Pattern.compile("(?i)ENGINE\\s*=\\s*InnoDB").matcher(table).find());
            assertTrue(table.contains("tenant_id VARCHAR(20) COLLATE utf8mb4_bin NOT NULL"));
            assertTrue(table.contains("PRIMARY KEY ("+contract[1]+")"));
            assertTrue(Pattern.compile("UNIQUE KEY \\w+ \\(tenant_id,request_key\\)").matcher(table).find());
            assertTrue(Pattern.compile("UNIQUE KEY \\w+ \\("+Pattern.quote(contract[2])+"\\)").matcher(table).find());
        }
    }
}
