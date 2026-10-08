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
        assertEquals(4,endpoints);
        assertEquals(3,java.util.Arrays.stream(PuCertifiedPurchaseController.class.getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)).count());
        assertEquals(1,java.util.Arrays.stream(PuCertifiedPurchaseController.class.getDeclaredMethods())
            .filter(m -> m.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class)).count());
    }
    @Test void installationSchemaKeepsPermanentTenantKeysAndCommittedDefault() throws Exception {
        Path root=Path.of(".").toAbsolutePath();while(root!=null&&!Files.exists(root.resolve("script/sql/ry-erp.sql")))root=root.getParent();
        assertNotNull(root,"需要仓库脚本上下文");
        var ddl=java.util.regex.Pattern.compile("(?is)CREATE\\s+TABLE\\s+`pu_certified_purchase_command`\\s*\\(.*?\\)\\s*ENGINE[^;]*;")
            .matcher(Files.readString(root.resolve("script/sql/ry-erp.sql")));
        assertTrue(ddl.find(),"Missing installation DDL: pu_certified_purchase_command");
        String sql=ddl.group().toLowerCase(Locale.ROOT);
        assertTrue(sql.contains("create table `pu_certified_purchase_command`"));
        assertTrue(sql.contains("primary key (`tenant_id`,`request_key`)"));
        assertTrue(sql.contains("unique key `uk_certified_purchase_requisition` (`tenant_id`,`requisition_id`)"));
        assertTrue(sql.contains("engine=innodb"));
        assertTrue(sql.contains("`command_status` varchar(20) character set ascii collate ascii_bin not null default 'committed'"));
        assertTrue(sql.contains("`catalog_fingerprint` char(64) character set ascii collate ascii_bin not null"));
        assertTrue(sql.contains("`window_end` date not null"));
    }
}
