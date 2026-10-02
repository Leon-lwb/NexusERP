package com.nexus.erp.pu;
import com.nexus.erp.pu.mapper.*;
import com.nexus.erp.pu.controller.PuCertifiedPurchaseWorkflowController;
import com.nexus.erp.pu.service.PuCertifiedPurchaseWorkflowService;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import cn.dev33.satoken.annotation.SaCheckPermission;
import static org.junit.jupiter.api.Assertions.*;
class PuCertifiedPurchaseLifecycleSqlContractTest {
    @Test void metadataRecordsHaveExplicitAliasesInsteadOfDependingOnPositionalConstructorDefaults() throws Exception {
        String columns=sql(PuCertifiedPurchaseLifecycleMapper.class,"lifecycleQuantityDefinitions");
        assertTrue(columns.contains("numeric_precision AS precision"));assertTrue(columns.contains("numeric_scale AS scale"));
        assertTrue(sql(PuCertifiedPurchaseLifecycleMapper.class,"lifecycleUniqueDefinitions").replace("`","").contains("seq_in_index AS sequence"));
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
        var cas=PuRequirementAllocationLedgerMapper.class.getMethod("updateWithVersion",String.class,com.nexus.erp.pu.domain.PuRequirementAllocationLedger.class,Integer.class);
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
        assertDoesNotThrow(()->com.nexus.erp.pu.domain.PuRequirementAllocationEvent.class.getMethod("getEventSequence"));
        assertDoesNotThrow(()->com.nexus.erp.pu.domain.PuRequirementAllocationEvent.class.getMethod("setEventSequence",Integer.class));
        for(String name:new String[]{"listByAllocation","lockByAllocation"}) {
            var method=PuRequirementAllocationEventMapper.class.getMethod(name,String.class,Long.class);
            String select=String.join(" ",method.getAnnotation(Select.class).value());
            assertTrue(select.contains("ORDER BY event_sequence"));assertFalse(select.contains("ORDER BY occurred_at"));
            assertFalse(select.contains("ORDER BY event_id"));
        }
        var method=PuRequirementAllocationEventMapper.class.getMethod("insert",com.nexus.erp.pu.domain.PuRequirementAllocationEvent.class);
        String insert=String.join(" ",method.getAnnotation(org.apache.ibatis.annotations.Insert.class).value());
        assertTrue(insert.contains("event_sequence"));assertTrue(insert.contains("#{row.eventSequence}"));
    }
    @Test void installationAndIncrementalSchemaPersistSequenceWithoutInferringLegacyHistory() throws Exception {
        var root=java.nio.file.Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        while(root!=null&&!java.nio.file.Files.isRegularFile(root.resolve("ruoyi-modules/ruoyi-erp/pom.xml")))root=root.getParent();
        assertNotNull(root,"Test must locate the actual repository schema");
        String install=java.nio.file.Files.readString(root.resolve("script/sql/ry-erp.sql"));
        int start=install.indexOf("CREATE TABLE `pu_requirement_allocation_event`");
        assertTrue(start>=0);String event=install.substring(start,install.indexOf(";",start));
        assertTrue(event.contains("`event_sequence` int"));
        assertTrue(event.contains("UNIQUE INDEX `uk_prae_tenant_allocation_sequence`(`tenant_id`, `allocation_id`, `event_sequence`)"));
        String migration=java.nio.file.Files.readString(root.resolve("script/sql/update/2026-10-02-certified-allocation-event-sequence-ry-erp.sql"));
        assertTrue(migration.contains("`event_sequence` int"));assertTrue(migration.contains("NULL DEFAULT NULL"));
        assertFalse(migration.toUpperCase(java.util.Locale.ROOT).contains("UPDATE `PU_REQUIREMENT_ALLOCATION_EVENT`"));
        assertFalse(migration.toUpperCase(java.util.Locale.ROOT).contains("DROP TABLE"));
    }
    private static String sql(Class<?> type,String name)throws Exception{return String.join(" ",type.getMethod(name).getAnnotation(Select.class).value());}
}
