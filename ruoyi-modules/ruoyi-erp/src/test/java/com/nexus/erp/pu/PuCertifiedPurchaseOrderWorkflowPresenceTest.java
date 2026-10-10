package com.nexus.erp.pu;

import com.nexus.erp.support.ErpDdlTestSupport;

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
            String table=ddl.group();
            assertTrue(Pattern.compile("(?i)ENGINE\\s*=\\s*InnoDB").matcher(table).find());
            ErpDdlTestSupport.assertColumn(table,"tenant_id","varchar(20) NOT NULL");
            ErpDdlTestSupport.assertColumnCollation(table,"tenant_id","utf8mb4_bin");
            ErpDdlTestSupport.assertPrimaryKey(table,contract[1]);
            ErpDdlTestSupport.assertUniqueIndexColumns(table,"tenant_id","request_key");
            ErpDdlTestSupport.assertUniqueIndexColumns(table,contract[2].split(","));
        }
    }
}
