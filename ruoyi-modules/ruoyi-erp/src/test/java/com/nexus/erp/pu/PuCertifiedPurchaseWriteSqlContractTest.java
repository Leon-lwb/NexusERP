package com.nexus.erp.pu;

import com.nexus.erp.pu.mapper.PuCertifiedPurchaseWriteMapper;
import com.nexus.erp.pu.service.PuCertifiedPurchaseWriteService;
import com.nexus.erp.pu.controller.PuCertifiedPurchaseController;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.apache.ibatis.annotations.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PuCertifiedPurchaseWriteSqlContractTest {
    @Test void allCurrentReadsAreExplicitTenantBoundAndDoNotReuseMyBatisCaches() {
        for(var method:PuCertifiedPurchaseWriteMapper.class.getMethods()) {
            var select=method.getAnnotation(Select.class);if(select==null)continue;
            String sql=String.join(" ",select.value()).toLowerCase(Locale.ROOT);
            assertFalse(sql.contains("coalesce("));assertFalse(sql.contains("cast("));
            var options=method.getAnnotation(Options.class);assertNotNull(options,method.getName());
            assertFalse(options.useCache());assertEquals(Options.FlushCachePolicy.TRUE,options.flushCache());
            if(!sql.contains("information_schema")&&!sql.contains("where 1=0")) {
                assertTrue(sql.contains("tenant_id=#{tenantid}"),method.getName());assertTrue(sql.contains("for update"),method.getName());
            }
        }
    }
    @Test void writerCannotBeWrappedByAnOrdinarySpringTransaction() {
        assertNull(PuCertifiedPurchaseWriteService.class.getAnnotation(Transactional.class));
        for(var m:PuCertifiedPurchaseWriteService.class.getMethods())assertNull(m.getAnnotation(Transactional.class),m.getName());
    }
    @Test void everyActualEndpointRequiresPurchaseWritePermission() {
        int endpoints=0;
        for(var method:PuCertifiedPurchaseController.class.getDeclaredMethods()) {
            var permission=method.getAnnotation(SaCheckPermission.class);assertNotNull(permission);
            assertArrayEquals(new String[]{"pu:requisition:add"},permission.value());endpoints++;
        }
        assertEquals(3,endpoints);
    }
    @Test void migrationNeverUpdatesLegacyBusinessRowsOrRemovesPermanentKeys() throws Exception {
        Path root=Path.of(".").toAbsolutePath();while(root!=null&&!Files.exists(root.resolve("script/sql/ry-erp.sql")))root=root.getParent();
        assertNotNull(root,"需要仓库脚本上下文");
        String sql=Files.readString(root.resolve("script/sql/update/2026-10-02-certified-purchase-command-ry-erp.sql")).toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("create table if not exists `pu_certified_purchase_command`"));
        assertTrue(sql.contains("primary key (`tenant_id`,`request_key`)"));
        assertTrue(sql.contains("unique key `uk_certified_purchase_requisition` (`tenant_id`,`requisition_id`)"));
        assertTrue(sql.contains("engine=innodb"));assertFalse(java.util.regex.Pattern.compile("(?im)^\\s*(update|delete|drop|truncate|replace|insert)\\b").matcher(sql).find());
    }
}
