package com.nexus.erp.pu;

import static org.junit.jupiter.api.Assertions.*;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.erp.pu.controller.PuCertifiedPurchaseWorkflowController;
import com.nexus.erp.pu.domain.PuRequirementAllocationEvent;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import com.nexus.erp.pu.mapper.*;
import com.nexus.erp.pu.service.PuCertifiedPurchaseWorkflowService;
import com.nexus.erp.support.ErpDdlTestSupport;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

class PuCertifiedPurchaseLifecycleSqlContractTest {
    @Test void metadataRecordsHaveExplicitAliasesInsteadOfDependingOnPositionalConstructorDefaults() throws Exception {
        String columns=sql(PuCertifiedPurchaseLifecycleMapper.class,"lifecycleQuantityDefinitions");
        assertTrue(columns.contains("numeric_precision AS `precision`"));assertTrue(columns.contains("numeric_scale AS `scale`"));
        assertTrue(sql(PuCertifiedPurchaseLifecycleMapper.class,"lifecycleUniqueDefinitions").replace("`","").contains("seq_in_index AS sequence"));
    }
    /** 元数据别名在 MySQL 5.7 中也必须合法，不能只验证 MyBatis 能解析注解。 */
    @Test void orderMetadataAliasesRemainMysqlCompatibleAndKeepRecordNames() throws Exception {
        String columns = sql(PuCertifiedPurchaseLifecycleMapper.class, "orderColumnDefinitions");
        assertTrue(columns.contains("numeric_precision AS `precision`"));
        assertTrue(columns.contains("numeric_scale AS `scale`"));
        assertTrue(columns.contains("is_nullable AS `nullable`"));
        assertFalse(columns.matches("(?is).*\\bAS\\s+precision\\b.*"));
        // 仅显式本地诊断时导出已编译注解 SQL；默认回归不连接任何数据库。
        String export = System.getProperty("nexus.metadataSqlExport");
        if (export != null) {
            Files.writeString(
                    Path.of(export),
                    columns
                            + "\n---\n"
                            + sql(
                                    PuCertifiedPurchaseLifecycleMapper.class,
                                    "lifecycleQuantityDefinitions"));
        }
    }
    @Test void currentReadsFlushBothMybatisCachesAndDoNotReuseSessionSnapshot() {
        for(Class<?> type:new Class<?>[]{PuCertifiedPurchaseLifecycleMapper.class,PuCertifiedPurchaseWorkflowMapper.class,PuRequirementAllocationLedgerMapper.class,PuRequirementAllocationEventMapper.class})
            for(var m:type.getDeclaredMethods()) if(m.isAnnotationPresent(Select.class)&&!m.getName().equals("findMaterial")) {
                var options=m.getAnnotation(Options.class);assertNotNull(options,m.toString());
                assertEquals(Options.FlushCachePolicy.TRUE,options.flushCache(),m.toString());assertFalse(options.useCache(),m.toString());
                if(m.getName().startsWith("lock"))assertTrue(String.join(" ",m.getAnnotation(Select.class).value()).contains("FOR UPDATE"),m.toString());
            }
    }
    @Test void protectedApiPermissionsAndNoOuterTransactionAreFixed() throws Exception {
        assertNull(PuCertifiedPurchaseWorkflowController.class.getAnnotation(Transactional.class));
        assertNull(PuCertifiedPurchaseWorkflowService.class.getAnnotation(Transactional.class));
        for(String operation:new String[]{"approve","convert","close","cancel"}) {
            var service=PuCertifiedPurchaseWorkflowService.class.getMethod(operation,Long.class,Integer.class);
            assertNull(service.getAnnotation(Transactional.class));
            var method=PuCertifiedPurchaseWorkflowController.class.getMethod(operation,Long.class,PuCertifiedPurchaseWorkflowController.VersionRequest.class);
            assertArrayEquals(new String[]{"pu:requisition:"+operation},method.getAnnotation(SaCheckPermission.class).value());
        }
    }
    @Test void realMybatisParsesEveryFixedMapperStatementWithoutAConnectionOrDatabaseWrite() {
        Configuration configuration=new Configuration();configuration.setMapUnderscoreToCamelCase(true);
        configuration.setArgNameBasedConstructorAutoMapping(true);
        for(Class<?> type:new Class<?>[]{PuCertifiedPurchaseLifecycleMapper.class,PuCertifiedPurchaseWorkflowMapper.class,PuRequirementAllocationLedgerMapper.class,PuRequirementAllocationEventMapper.class})
            assertDoesNotThrow(()->configuration.addMapper(type));
        assertTrue(configuration.getMappedStatementNames().size()>50);
    }
    @Test void permanentCommandCompensationAndLedgerWritesAreTenantAndVersionBound() throws Exception {
        var method=PuCertifiedPurchaseLifecycleMapper.class.getMethod("markCommandCompensated",String.class,String.class,Long.class);
        String update=String.join(" ",method.getAnnotation(Update.class).value());
        assertTrue(update.contains("tenant_id=#{tenantId}"));assertTrue(update.contains("command_status='COMMITTED'"));
        assertFalse(update.toUpperCase().contains("DELETE"));
        var cas =
                PuRequirementAllocationLedgerMapper.class.getMethod(
                        "updateWithVersion",
                        String.class,
                        PuRequirementAllocationLedger.class,
                        Integer.class);
        String ledger=String.join(" ",cas.getAnnotation(Update.class).value());assertTrue(ledger.contains("version = #{expectedVersion}"));
        assertTrue(ledger.contains("allocation_key = #{row.allocationKey}"));assertTrue(ledger.contains("base_uom_id = #{row.baseUomId}"));
    }
    @Test void commandCurrentReadIncludesFrozenCatalogAndOriginalDayWindow() throws Exception {
        var method=PuCertifiedPurchaseLifecycleMapper.class.getMethod("lockCommand",String.class,String.class);
        String select=String.join(" ",method.getAnnotation(Select.class).value());
        assertTrue(select.contains("catalog_fingerprint"));
        assertTrue(select.contains("DATE_FORMAT(window_end,'%Y-%m-%d') AS window_end"));
        var fields=PuCertifiedPurchaseLifecycleMapper.CommandSource.class.getRecordComponents();
        assertEquals("catalogFingerprint",fields[fields.length-2].getName());assertEquals("windowEnd",fields[fields.length-1].getName());
    }
    @Test void joinedReturnAndMovementReadsExposeHeaderAndLineDeletionWithoutHidingCorruption() throws Exception {
        for(String name:new String[]{"lockReturnLine","lockReceiptSources","lockReturnSources"}) {
            var method=PuCertifiedPurchaseLifecycleMapper.class.getMethod(name,String.class,Long.class);
            String select=String.join(" ",method.getAnnotation(Select.class).value());
            assertTrue(select.contains("h.del_flag AS del_flag"),name);
            assertTrue(select.contains("l.del_flag AS line_del_flag"),name);
            String predicates=select.substring(select.indexOf(" FROM ")).toLowerCase();
            assertFalse(predicates.contains("del_flag"),"Deleted or NULL rows must not be hidden in JOIN/WHERE: "+name);
            assertTrue(select.contains("FOR UPDATE"));
        }
    }
    @Test void eventCausalityRequiresPersistedSequenceNotOccurrenceTimeOrSnowflakeOrder() throws Exception {
        assertDoesNotThrow(() -> PuRequirementAllocationEvent.class.getMethod("getEventSequence"));
        assertDoesNotThrow(
                () ->
                        PuRequirementAllocationEvent.class.getMethod(
                                "setEventSequence", Integer.class));
        for(String name:new String[]{"listByAllocation","lockByAllocation"}) {
            var method=PuRequirementAllocationEventMapper.class.getMethod(name,String.class,Long.class);
            String select=String.join(" ",method.getAnnotation(Select.class).value());
            assertTrue(select.contains("ORDER BY event_sequence"));assertFalse(select.contains("ORDER BY occurred_at"));
            assertFalse(select.contains("ORDER BY event_id"));
        }
        var method =
                PuRequirementAllocationEventMapper.class.getMethod(
                        "insert", PuRequirementAllocationEvent.class);
        String insert = String.join(" ", method.getAnnotation(Insert.class).value());
        assertTrue(insert.contains("event_sequence"));assertTrue(insert.contains("#{row.eventSequence}"));
    }
    @Test void installationSchemaRequiresExplicitSequencePerTenantAllocation() throws Exception {
        var root = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        while (root != null
                && !Files.isRegularFile(root.resolve("ruoyi-modules/ruoyi-erp/pom.xml")))
            root = root.getParent();
        assertNotNull(root,"Test must locate the actual repository schema");
        String install = Files.readString(root.resolve("script/sql/ry-erp.sql"));
        int start=install.indexOf("CREATE TABLE `pu_requirement_allocation_event`");
        assertTrue(start>=0);String event=install.substring(start,install.indexOf(";",start));
        ErpDdlTestSupport.assertColumn(event, "event_sequence", "int NOT NULL");
        ErpDdlTestSupport.assertIndex(event, true, "uk_prae_tenant_allocation_sequence", "tenant_id", "allocation_id", "event_sequence");
        assertFalse(
                Pattern.compile("(?im)^\\s*`event_sequence`[^\\r\\n]*\\bDEFAULT\\b")
                        .matcher(event)
                        .find(),
                "Event order must be supplied by the writer, not inferred by a default");
        ErpDdlTestSupport.assertIndex(event, true, "uk_prae_tenant_event_key", "tenant_id", "event_key");
    }
    private static String sql(Class<?> type,String name)throws Exception{return String.join(" ",type.getMethod(name).getAnnotation(Select.class).value());}
}
