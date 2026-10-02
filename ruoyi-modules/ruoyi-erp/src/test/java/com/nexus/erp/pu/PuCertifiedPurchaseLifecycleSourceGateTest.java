package com.nexus.erp.pu;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.*;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PuCertifiedPurchaseLifecycleSourceGateTest {
    static final List<String> TABLES=List.of("pu_certified_purchase_command","pu_requirement_allocation_ledger","pu_requirement_allocation_event",
        "wm_material_receive","wm_material_receive_line","wm_material_stock_tx","pu_purchase_return","pu_purchase_return_line");
    static final Map<String,String> QUANTITIES=Map.of("wm_material_receive_line.quantity_received","quantity_received",
        "wm_material_stock_tx.tx_quantity","tx_quantity","wm_material_stock_tx.before_quantity","before_quantity",
        "wm_material_stock_tx.after_quantity","after_quantity","pu_purchase_return_line.return_quantity","return_quantity");
    @Test void allPersistedEffectSourcesMustBePinnedAndProvenNativeTransactional() {
        var mapper=valid();invoke(mapper);
        verify(mapper).pinLifecycleDefinitions();verify(mapper).lifecycleTableDefinitions();
        verify(mapper).lifecycleQuantityDefinitions();verify(mapper).lifecycleUniqueDefinitions();
    }
    @Test void anyMyIsamOrMissingTableRefusesTheWholeLifecycle() {
        var mapper=valid();var definitions=new ArrayList<>(TABLES.stream().map(t->new TableDefinition(t,"InnoDB")).toList());
        definitions.set(5,new TableDefinition("wm_material_stock_tx","MyISAM"));when(mapper.lifecycleTableDefinitions()).thenReturn(definitions);
        assertThrows(ServiceException.class,()->invoke(mapper));
        when(mapper.lifecycleTableDefinitions()).thenReturn(definitions.subList(0,7));
        assertThrows(ServiceException.class,()->invoke(mapper));
    }
    @Test void doubleOrScaleTwoIsNotUpgradedByJavaBigDecimalOrCast() {
        var mapper=valid();var definitions=new ArrayList<>(quantities());definitions.set(0,new NativeQuantityDefinition("wm_material_receive_line","quantity_received","double",null,null));
        when(mapper.lifecycleQuantityDefinitions()).thenReturn(definitions);assertThrows(ServiceException.class,()->invoke(mapper));
        definitions.set(0,new NativeQuantityDefinition("wm_material_receive_line","quantity_received","decimal",18,2));
        assertThrows(ServiceException.class,()->invoke(mapper));
    }
    @Test void truncatedOrNonUniquePermanentKeysAreRejected() {
        var mapper=valid();var definitions=new ArrayList<>(indices());definitions.set(0,new IndexDefinition("pu_certified_purchase_command","PRIMARY",0,1,"tenant_id",3));
        when(mapper.lifecycleUniqueDefinitions()).thenReturn(definitions);assertThrows(ServiceException.class,()->invoke(mapper));
    }
    @Test void unownedTransactionRejectsBeforeAnySchemaMapperRead() {
        var mapper=valid();var qualification=mock(PuRequirementErpTransactionQualification.class);
        doThrow(new ServiceException("not owned")).when(qualification).requireSourceChainTransaction();
        assertThrows(ServiceException.class,()->invoke(mapper,qualification));verifyNoInteractions(mapper);
    }
    @Test void missingActualAllocationSequenceUniqueKeyRefusesCoreAndEffectOperations() {
        var mapper=valid();when(mapper.lifecycleUniqueDefinitions()).thenReturn(indices().stream().filter(i->!"uk_allocation_sequence".equals(i.indexName())).toList());
        assertThrows(ServiceException.class,()->invoke(mapper));
    }
    @Test void nonUniqueOrTruncatedAllocationSequenceIndexDoesNotGrantCausalOrdering() {
        for(int mode=0;mode<2;mode++) {
            var mapper=valid();var rows=new ArrayList<>(indices());
            for(int i=0;i<rows.size();i++)if("uk_allocation_sequence".equals(rows.get(i).indexName())) {
                var old=rows.get(i);rows.set(i,new IndexDefinition(old.tableName(),old.indexName(),mode==0?1:0,old.sequence(),old.columnName(),mode==1?1:null));
            }
            when(mapper.lifecycleUniqueDefinitions()).thenReturn(rows);assertThrows(ServiceException.class,()->invoke(mapper));
        }
    }
    static PuCertifiedPurchaseLifecycleMapper valid() {
        var m=mock(PuCertifiedPurchaseLifecycleMapper.class);stub(m);return m;
    }
    static void stub(PuCertifiedPurchaseLifecycleMapper m) {
        when(m.pinLifecycleDefinitions()).thenReturn(List.of());
        when(m.lifecycleTableDefinitions()).thenReturn(TABLES.stream().map(t->new TableDefinition(t,"InnoDB")).toList());
        when(m.lifecycleQuantityDefinitions()).thenReturn(quantities());when(m.lifecycleUniqueDefinitions()).thenReturn(indices());
    }
    static List<NativeQuantityDefinition> quantities() {
        return QUANTITIES.keySet().stream().sorted().map(k->new NativeQuantityDefinition(k.substring(0,k.indexOf('.')),QUANTITIES.get(k),"decimal",18,4)).toList();
    }
    static List<IndexDefinition> indices() {
        var rows=new ArrayList<IndexDefinition>();
        for(var item:Map.of("pu_certified_purchase_command","request_key","pu_requirement_allocation_ledger","allocation_key","pu_requirement_allocation_event","event_key").entrySet()) {
            rows.add(new IndexDefinition(item.getKey(),"PRIMARY",0,1,"tenant_id",null));rows.add(new IndexDefinition(item.getKey(),"PRIMARY",0,2,item.getValue(),null));
        }
        rows.add(new IndexDefinition("pu_requirement_allocation_event","uk_allocation_sequence",0,1,"tenant_id",null));
        rows.add(new IndexDefinition("pu_requirement_allocation_event","uk_allocation_sequence",0,2,"allocation_id",null));
        rows.add(new IndexDefinition("pu_requirement_allocation_event","uk_allocation_sequence",0,3,"event_sequence",null));
        return rows;
    }
    private static void invoke(PuCertifiedPurchaseLifecycleMapper m) {invoke(m,mock(PuRequirementErpTransactionQualification.class));}
    private static void invoke(PuCertifiedPurchaseLifecycleMapper m,PuRequirementErpTransactionQualification q) {
        var type=assertDoesNotThrow(()->Class.forName("com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleSourceGate"),"Expected RED: actual source schema gate is missing");
        var gate=assertDoesNotThrow(()->type.getConstructor(PuCertifiedPurchaseLifecycleMapper.class,PuRequirementErpTransactionQualification.class).newInstance(m,q));
        try {type.getMethod("requireDefinitions").invoke(gate);}catch(InvocationTargetException e){if(e.getCause()instanceof RuntimeException r)throw r;throw new AssertionError(e.getCause());}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
}
