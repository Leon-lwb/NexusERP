package com.nexus.erp.pu;

import com.nexus.erp.support.ErpDdlTestSupport;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.*;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderWorkflowMapper;
import com.nexus.erp.pu.service.*;
import org.apache.ibatis.annotations.*;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PuCertifiedPurchaseOrderWorkflowMapperTest {
    static final List<String> TABLES=List.of("pu_purchase_order","pu_purchase_order_line","pu_certified_purchase_order_terms","pu_certified_purchase_order_terms_confirmation","pu_certified_purchase_order_operation");
    @Test void correctActualOrderQuoteAuditAndReceiptDefinitionsGrantOnlyInsideOwnedTransaction() {
        var m=mock(PuCertifiedPurchaseLifecycleMapper.class);stubOrderDefinitions(m);
        var q=mock(PuRequirementErpTransactionQualification.class);assertDoesNotThrow(()->new PuCertifiedPurchaseLifecycleSourceGate(m,q).requireOrderDefinitions());
        var ordered=inOrder(q,m);ordered.verify(q).requireSourceChainTransaction();ordered.verify(m).pinOrderDefinitions();
    }
    @Test void missingOrNonTransactionalQuoteAuditAndReceiptTablesFailClosed() {
        for(String table:TABLES)for(boolean missing:List.of(true,false)) {
            var m=mock(PuCertifiedPurchaseLifecycleMapper.class);stubOrderDefinitions(m);
            when(m.orderTableDefinitions()).thenReturn(TABLES.stream().filter(t->!missing||!t.equals(table)).map(t->new TableDefinition(t,t.equals(table)?"MyISAM":"InnoDB")).toList());
            assertThrows(ServiceException.class,()->gate(m));
        }
    }
    @Test void realMoneyPrecisionAndEveryPersistedConfirmationFieldAreRequired() {
        for(String key:List.of("pu_purchase_order_line.unit_price","pu_purchase_order_line.tax_rate","pu_purchase_order.total_amount","pu_certified_purchase_order_terms.promised_arrival_date",
            "pu_certified_purchase_order_terms.evidence_sha256","pu_certified_purchase_order_terms_confirmation.actor_id","pu_certified_purchase_order_operation.result_fingerprint")) {
            var m=mock(PuCertifiedPurchaseLifecycleMapper.class);stubOrderDefinitions(m);var columns=new ArrayList<>(columns());
            columns.removeIf(c->(c.tableName()+"."+c.columnName()).equals(key));when(m.orderColumnDefinitions()).thenReturn(columns);assertThrows(ServiceException.class,()->gate(m),key);
        }
        var m=mock(PuCertifiedPurchaseLifecycleMapper.class);stubOrderDefinitions(m);var columns=new ArrayList<>(columns());
        for(int i=0;i<columns.size();i++)if(columns.get(i).columnName().equals("total_amount")) {var c=columns.get(i);columns.set(i,new OrderColumnDefinition(c.tableName(),c.columnName(),"decimal",18,4,null,c.nullable()));}
        when(m.orderColumnDefinitions()).thenReturn(columns);assertThrows(ServiceException.class,()->gate(m));
    }
    @Test void nonUniqueOrPrefixQuoteRevisionAndRequestKeyIndicesCannotAuthorizePermanentReplay() {
        for(int mode=0;mode<2;mode++) {
            var m=mock(PuCertifiedPurchaseLifecycleMapper.class);stubOrderDefinitions(m);var indices=new ArrayList<>(indices());
            for(int i=0;i<indices.size();i++){var c=indices.get(i);if(c.indexName().equals("revision"))indices.set(i,new IndexDefinition(c.tableName(),c.indexName(),mode==0?1:0,c.sequence(),c.columnName(),mode==1?1:null));}
            when(m.orderUniqueDefinitions()).thenReturn(indices);assertThrows(ServiceException.class,()->gate(m));
        }
    }
    @Test void immutableQuoteAuditAndReceiptMapperExposeNoUpdatesDeletesGenericCrudOrPriceFromLegacyVendor() {
        for(var method:PuCertifiedPurchaseOrderWorkflowMapper.class.getDeclaredMethods()) {
            if(method.getName().startsWith("insert"))assertNotNull(method.getAnnotation(Insert.class));
            if(method.getAnnotation(Update.class)!=null)assertTrue(Set.of("approveHead","priceLine","closeHead").contains(method.getName()));
            assertNull(method.getAnnotation(Delete.class));
            Select select=method.getAnnotation(Select.class);if(select!=null){String sql=String.join("",select.value());assertFalse(sql.contains("bd_material_vendor"));
                if(!method.getName().equals("databaseDate"))assertTrue(sql.contains("tenant_id"),method.getName());
                if(method.getName().startsWith("lock"))assertTrue(sql.contains("FOR UPDATE"),method.getName());
                assertFalse(method.getAnnotation(Options.class).useCache());}
        }
    }
    @Test void pricingSqlRetainsQuantitiesAndCountersAndHeaderCarriesSourceEtaAndVersionCas() throws Exception {
        var methods=PuCertifiedPurchaseOrderWorkflowMapper.class.getDeclaredMethods();
        String price=Arrays.stream(methods).filter(m->m.getName().equals("priceLine")).map(m->String.join("",m.getAnnotation(Update.class).value())).findFirst().orElseThrow();
        String set=price.substring(0,price.indexOf("WHERE"));assertFalse(set.contains("order_quantity="));assertFalse(set.contains("received_quantity="));assertFalse(set.contains("invoiced_qty="));
        assertTrue(price.contains("order_quantity=#{old.orderQuantity}"));assertTrue(price.contains("tenant_id=#{tenantId}"));
        String head=Arrays.stream(methods).filter(m->m.getName().equals("approveHead")).map(m->String.join("",m.getAnnotation(Update.class).value())).findFirst().orElseThrow();
        assertTrue(head.contains("expected_arrival_date=#{q.promisedArrivalDate}"));assertTrue(head.contains("certified_version=#{old.version}"));
        for(String methodName:List.of("approveHead","closeHead")) {
            String sql=Arrays.stream(methods).filter(m->m.getName().equals(methodName)).map(m->String.join("",m.getAnnotation(Update.class).value())).findFirst().orElseThrow();
            assertTrue(sql.contains("certified_version=certified_version+1"));assertTrue(sql.contains("certified_version=#{old.version}"));
            assertFalse(sql.contains("IFNULL(certified_version"),"未知原生版本不得补0参与CAS");
        }
        String locked=String.join("",PuCertifiedPurchaseOrderWorkflowMapper.class.getMethod("lockHead",String.class,Long.class).getAnnotation(Select.class).value());
        assertTrue(locked.contains("certified_version AS version"));assertFalse(locked.contains("IFNULL(certified_version"));
        assertFalse(head.contains("CURRENT_DATE"));assertTrue(head.contains("certified_terms_id IS NULL"));
        String headDdl=tableDefinition("pu_purchase_order");
        ErpDdlTestSupport.assertColumn(headDdl, "certified_version", "int DEFAULT NULL");
        ErpDdlTestSupport.assertColumn(headDdl, "certified_terms_id", "bigint DEFAULT NULL");
    }
    @Test void implicitNullableDumpFieldsAreNotOmittedFromSharedActualDefinitionFixture() {
        var actual=columns();
        for(String key:List.of("pu_purchase_order.tenant_id", "pu_purchase_order_line.invoiced_qty",
            "pu_purchase_order_line.returned_quantity", "pu_purchase_order_line.receive_status")) {
            var matching=actual.stream().filter(c->(c.tableName()+"."+c.columnName()).equals(key)).toList();
            assertEquals(1,matching.size(),key);assertEquals("YES",matching.get(0).nullable(),key);
        }
    }
    @Test void invalidNativeTypesLengthsNullabilityAndUnownedTransactionStillFailClosed() {
        for(int mode=0;mode<3;mode++) {
            var m=mock(PuCertifiedPurchaseLifecycleMapper.class);stubOrderDefinitions(m);
            var changed=new ArrayList<>(columns());
            for(int i=0;i<changed.size();i++) {
                var c=changed.get(i);
                if(c.tableName().equals("pu_certified_purchase_order_terms")&&c.columnName().equals("request_key"))
                    changed.set(i,new OrderColumnDefinition(c.tableName(),c.columnName(),mode==0?"char":c.dataType(),
                        c.precision(),c.scale(),mode==1?127L:c.maxLength(),mode==2?"YES":c.nullable()));
            }
            assertNotEquals(columns(),changed,"Native definition mutation must take effect");
            when(m.orderColumnDefinitions()).thenReturn(changed);assertThrows(ServiceException.class,()->gate(m));
        }
        var m=mock(PuCertifiedPurchaseLifecycleMapper.class);var q=mock(PuRequirementErpTransactionQualification.class);
        doThrow(new ServiceException("transaction required")).when(q).requireSourceChainTransaction();
        assertThrows(ServiceException.class,()->new PuCertifiedPurchaseLifecycleSourceGate(m,q).requireOrderDefinitions());
        verifyNoInteractions(m);
    }
    static void stubOrderDefinitions(PuCertifiedPurchaseLifecycleMapper m) {
        PuCertifiedPurchaseLifecycleSourceGateTest.stub(m);when(m.pinAllocationDefinitions()).thenReturn(List.of());
        when(m.pinOrderDefinitions()).thenReturn(List.of());when(m.orderTableDefinitions()).thenReturn(TABLES.stream().map(t->new TableDefinition(t,"InnoDB")).toList());
        var nativeColumns=columns();
        when(m.orderColumnDefinitions()).thenReturn(nativeColumns);when(m.orderUniqueDefinitions()).thenReturn(indices());
    }
    static List<OrderColumnDefinition> columns() {
        try {
            var result=new ArrayList<OrderColumnDefinition>();
            // 测试模拟实际观测到的原生元数据；生产环境绝不从 SQL 文件获取权威依据。
            for(String table:TABLES) {
                for (var c : ErpDdlTestSupport.columns(tableDefinition(table))) {
                    result.add(new OrderColumnDefinition(table, c.name(), c.type(), c.precision(), c.scale(),
                        c.maxLength(), c.nullable() ? "YES" : "NO"));
                }
            }
            return result;
        }catch(java.io.IOException e){throw new AssertionError(e);}
    }
    private static String tableDefinition(String table) throws java.io.IOException {
        String sql=Files.readString(repoFile("script/sql/ry-erp.sql"));
        var ddl=Pattern.compile("(?is)CREATE\\s+TABLE\\s+`?"+Pattern.quote(table)+"`?\\s*\\(.*?\\)\\s*ENGINE[^;]*;").matcher(sql);
        assertTrue(ddl.find(),"Missing installation DDL: "+table);
        return ddl.group();
    }
    private static Path repoFile(String file) {
        Path root=Path.of("").toAbsolutePath();
        while(root!=null && !Files.isRegularFile(root.resolve("AGENTS.md"))) root=root.getParent();
        if(root==null)throw new AssertionError("repository fixture root unavailable");
        return root.resolve(file);
    }
    static List<IndexDefinition> indices() {
        var r=new ArrayList<IndexDefinition>();
        add(r,"pu_certified_purchase_order_terms","request","tenant_id","request_key");add(r,"pu_certified_purchase_order_terms","revision","tenant_id","order_id","quote_version");
        add(r,"pu_certified_purchase_order_terms_confirmation","request","tenant_id","request_key");add(r,"pu_certified_purchase_order_terms_confirmation","quote","tenant_id","quote_id");
        add(r,"pu_certified_purchase_order_operation","request","tenant_id","request_key");add(r,"pu_certified_purchase_order_operation","operation","tenant_id","order_id","operation");
        add(r,"pu_certified_purchase_order_terms","PRIMARY","quote_id");add(r,"pu_certified_purchase_order_terms_confirmation","PRIMARY","confirmation_id");add(r,"pu_certified_purchase_order_operation","PRIMARY","command_id");
        return r;
    }
    private static void add(List<IndexDefinition> r,String table,String name,String...parts){for(int i=0;i<parts.length;i++)r.add(new IndexDefinition(table,name,0,i+1,parts[i],null));}
    private static void gate(PuCertifiedPurchaseLifecycleMapper m){new PuCertifiedPurchaseLifecycleSourceGate(m,mock(PuRequirementErpTransactionQualification.class)).requireOrderDefinitions();}
}
