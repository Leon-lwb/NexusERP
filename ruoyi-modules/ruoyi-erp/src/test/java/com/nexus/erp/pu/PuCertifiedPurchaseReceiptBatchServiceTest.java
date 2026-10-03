package com.nexus.erp.pu;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.domain.*;
import com.nexus.erp.pu.mapper.*;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.*;
import com.nexus.erp.pu.service.*;
import com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.nexus.erp.pu.service.support.PuCertifiedPurchaseLifecycleSupport.*;

/** 边界测试替身不代表已部署数据库，也不代表真实元数据验收。 */
class PuCertifiedPurchaseReceiptBatchServiceTest {
    static final String T="000001",KEY="request-0000000001",ORDER_STATUS="ORDER_COMMITTED_NOT_TIMELY_SUPPLY";
    static final BigDecimal TEN=new BigDecimal("10.0000"),THREE=new BigDecimal("3.0000");
    @Test void twoRealLinesUseOneOwnedRepeatableReadAndReturnPermanentLineReceipts() {
        try(var f=new Fixture()) {
            var result=f.service.consumeReceipt(T,901L);
            assertTrue(result.certified());assertFalse(result.replayed());assertEquals(801L,result.orderId());
            assertEquals(List.of(9001L,9002L),result.lines().stream().map(r->r.receiveLineId()).toList());
            verify(f.executor,times(1)).executeRepeatableRead(eq(T),any());
            verify(f.locks).lock(T,List.of(201L));
            var calls=mockingDetails(f.lifecycle).getInvocations().stream().toList();
            int firstWrite=-1,preflight=0;
            for(int i=0;i<calls.size();i++) {
                if(calls.get(i).getMethod().getName().equals("validateReceiptLocked"))preflight++;
                if(calls.get(i).getMethod().getName().equals("consumeReceiptLocked")&&firstWrite<0)firstWrite=i;
            }
            assertEquals(2,preflight);assertEquals(2,firstWrite,"ALL source preflights before ANY lifecycle mutation");
        }
    }
    @Test void suppliedOrderIdCannotOverrideTheLockedOriginalOrder() {
        try(var f=new Fixture()) {
            assertThrows(ServiceException.class,()->f.service.consumeReceipt(T,901L,802L));
            verify(f.lifecycle,never()).consumeReceiptLocked(any());
        }
    }
    @Test void sourceClassificationIncludesPermanentCommandAndLedgerWhenHeadMarkerWasLost() {
        try(var f=new Fixture()) {
            f.heads=List.of(new RequisitionHead(T,601L,"PR601","MANUAL","ORDERED",null,2,"0"));f.refresh();
            assertThrows(ServiceException.class,()->f.service.consumeReceipt(T,901L));
            verify(f.mapper).lockCommandsForOrder(T,801L);verify(f.mapper).lockAllocationsForOrder(T,801L);
            verify(f.lifecycle,never()).consumeReceiptLocked(any());
        }
    }
    @Test void compensatedKeysArePermanentEvidenceNotLegacyOrFakeSuccess() {
        try(var f=new Fixture()) {
            f.commands=List.of(f.command("COMPENSATED"));f.refresh();
            assertThrows(ServiceException.class,()->f.service.consumeReceipt(T,901L));
            verify(f.mapper).lockCommandsForOrder(T,801L);verify(f.lifecycle,never()).consumeReceiptLocked(any());
        }
    }
    @Test void trulyHistoricalOrderReturnsLegacyRouteOnlyAfterNativeIdentityChecks() {
        try(var f=new Fixture()) {
            f.order=new OrderHead(T,801L,null,"APPROVED","0");f.heads=List.of();f.reqLines=List.of();f.commands=List.of();f.allocations=List.of();
            f.orders=List.of(new OrderLine(T,701L,801L,null,201L,"公斤",TEN,BigDecimal.ZERO,BigDecimal.ZERO,"PENDING","0"));
            f.receives=List.of(new ReceiveLine(T,9001L,901L,701L,201L,null,THREE,"0"));f.refresh();
            var result=f.service.consumeReceipt(T,901L);
            assertFalse(result.certified());assertTrue(result.lines().isEmpty());assertFalse(result.replayed());
            verifyNoInteractions(f.lifecycle);
        }
    }
    @ParameterizedTest @ValueSource(strings={"receipt-head","receipt-line","order-head","order-line","requisition-head","requisition-line","command","ledger"})
    void crossTenantRowsFailBeforeAnyLifecycleWrite(String boundary) {
        try(var f=new Fixture()) {
            switch(boundary) {
                case "receipt-head" -> f.receiveHead=new ReceiveHead("000002",901L,801L,"FINISHED","0");
                case "receipt-line" -> f.receives=List.of(new ReceiveLine("000002",9001L,901L,701L,201L,"501",THREE,"0"));
                case "order-head" -> f.order=new OrderHead("000002",801L,601L,"APPROVED","0");
                case "order-line" -> f.orders=List.of(new OrderLine("000002",701L,801L,401L,201L,"501",TEN,BigDecimal.ZERO,BigDecimal.ZERO,"PENDING","0"));
                case "requisition-head" -> f.heads=List.of(new RequisitionHead("000002",601L,"PR601","MRP","ORDERED",KEY,2,"0"));
                case "requisition-line" -> f.reqLines=List.of(new RequisitionLine("000002",401L,601L,201L,"501",TEN,TEN,"MRP",301L,"2026-10-10","0"));
                case "command" -> {var c=f.command("COMMITTED");f.commands=List.of(new CommandSource("000002",c.requestKey(),c.requestHash(),c.actorId(),c.entryType(),c.materialId(),c.vendorId(),c.requisitionId(),c.requisitionNo(),c.sourceFingerprint(),c.resultFingerprint(),c.commandStatus(),c.catalogFingerprint(),c.windowEnd()));}
                case "ledger" -> f.allocations.get(0).setTenantId("000002");
            }
            f.refresh();assertThrows(ServiceException.class,()->f.service.consumeReceipt(T,901L));
            verify(f.lifecycle,never()).consumeReceiptLocked(any());
        }
    }
    @Test void everyGuardIsAcquiredInAscendingOrderBeforeCertificationAndMixedMaterialIsRejected() {
        try(var f=new Fixture()) {
            when(f.mapper.readMaterialHints(T,901L)).thenReturn(List.of(202L,201L));
            when(f.locks.lock(eq(T),anyCollection())).thenReturn(Map.of(201L,5,202L,9));
            f.receives=List.of(f.receives.get(0),new ReceiveLine(T,9002L,901L,702L,202L,"501",THREE,"0"));f.refresh();
            assertThrows(ServiceException.class,()->f.service.consumeReceipt(T,901L));
            verify(f.locks).lock(T,List.of(201L,202L));verify(f.lifecycle,never()).consumeReceiptLocked(any());
        }
    }
    @ParameterizedTest @ValueSource(strings={"missing-source-line","unknown-source-line","wrong-unit","wrong-material","bad-delta","legacy-virtual","duplicate-tx","wrong-actor"})
    void entireNativePhysicalSetIsCertifiedBeforeFirstMutation(String corruption) {
        try(var f=new Fixture()) {
            StockEffect old=f.effects.get(1);
            StockEffect bad=new StockEffect(old.tenantId(),old.txId(),"legacy-virtual".equals(corruption)?"VIRTUAL_OUT":old.txType(),
                "wrong-material".equals(corruption)?202L:old.materialId(),"wrong-unit".equals(corruption)?"502":old.baseUomId(),old.sourceDocType(),old.sourceDocId(),
                "missing-source-line".equals(corruption)?null:"unknown-source-line".equals(corruption)?9999L:old.sourceDocLineId(),old.txFlag(),old.quantity(),old.beforeQuantity(),
                "bad-delta".equals(corruption)?TEN:old.afterQuantity(),old.stockId(),"wrong-actor".equals(corruption)?902L:old.actorId(),old.delFlag());
            f.effects="duplicate-tx".equals(corruption)?List.of(f.effects.get(0),f.effects.get(0),old):List.of(f.effects.get(0),bad);f.refresh();
            assertThrows(ServiceException.class,()->f.service.consumeReceipt(T,901L));
            verify(f.physical).lockStockEffects(T,"WM_MATERIAL_RECEIVE",901L);
            verify(f.lifecycle,never()).consumeReceiptLocked(any());
        }
    }
    @Test void duplicateReceiveLabelsDoNotOverwriteAMapEntry() {
        try(var f=new Fixture()) {
            f.receives=List.of(f.receives.get(0),f.receives.get(0));f.refresh();
            assertThrows(ServiceException.class,()->f.service.consumeReceipt(T,901L));
            verify(f.mapper).lockReceiptLines(T,901L);verify(f.lifecycle,never()).consumeReceiptLocked(any());
        }
    }
    @Test void missingPurchaseLineCannotFallBackToMaterialMatchingOnCertifiedOrder() {
        try(var f=new Fixture()) {
            f.receives=List.of(new ReceiveLine(T,9001L,901L,null,201L,"501",THREE,"0"));f.refresh();
            assertThrows(ServiceException.class,()->f.service.consumeReceipt(T,901L));verify(f.lifecycle,never()).consumeReceiptLocked(any());
        }
    }
    @Test void paddedRequestKeyCannotReplacePersistedIdentity() {
        try(var f=new Fixture()) {
            f.allocations.get(1).setDecisionIdempotencyKey(KEY+" ");
            assertThrows(ServiceException.class,()->f.service.consumeReceipt(T,901L));
            verify(f.mapper).lockAllocationsForOrder(T,801L);verify(f.lifecycle,never()).consumeReceiptLocked(any());
        }
    }
    @Test void nativeDefinitionFailureCannotBeProvedByInstallationSql() {
        try(var f=new Fixture()) {
            when(f.physical.lifecycleQuantityDefinitions()).thenReturn(List.of(new NativeQuantityDefinition("wm_material_receive_line","quantity_received","double",null,null)));
            assertThrows(ServiceException.class,()->f.service.consumeReceipt(T,901L));
            verify(f.physical).lifecycleQuantityDefinitions();verify(f.lifecycle,never()).consumeReceiptLocked(any());
        }
    }
    @Test void mapperDiagnosticsAreFixedAndCauseFreeButJavaErrorRemainsExact() {
        try(var f=new Fixture()) {
            when(f.mapper.lockReceiptLines(T,901L)).thenThrow(new IllegalStateException("jdbc fixture-password SELECT secret"));
            ServiceException e=assertThrows(ServiceException.class,()->f.service.consumeReceipt(T,901L));
            assertFalse(e.getMessage().contains("fixture-password"));assertNull(e.getCause());assertEquals(0,e.getSuppressed().length);
            AssertionError fatal=new AssertionError("fatal");doThrow(fatal).when(f.mapper).lockReceiptLines(T,901L);
            assertSame(fatal,assertThrows(AssertionError.class,()->f.service.consumeReceipt(T,901L)));
        }
    }
    @Test void aPartialPermanentBatchMustNotInventSuccessfulRecovery() {
        try(var f=new Fixture()) {
            f.allocations.get(0).setVersion(3);
            when(f.mapper.lockReceiptEvent(T,eventKey(f.allocations.get(0),9001L))).thenReturn(f.event(f.allocations.get(0),9001L));
            assertThrows(ServiceException.class,()->f.service.consumeReceipt(T,901L));
            verify(f.mapper).lockReceiptEvent(T,eventKey(f.allocations.get(1),9002L));verify(f.lifecycle,never()).consumeReceiptLocked(any());
        }
    }
    @Test void everyDeliveryReplaysFullPermanentEventsEvenAfterLedgerVersionAdvanced() {
        try(var f=new Fixture()) {
            for(var row:f.allocations) {row.setVersion(7);when(f.mapper.lockReceiptEvent(T,eventKey(row,row.getOrderLineId()+8300))).thenReturn(f.event(row,row.getOrderLineId()+8300));}
            doAnswer(call->{ReceiptCommand c=call.getArgument(0);assertEquals(7,c.expectedVersion());var row=f.allocations.stream().filter(r->r.getAllocationKey().equals(c.allocationKey())).findFirst().orElseThrow();return f.receipt(row,c.receiveLineId(),true);}).when(f.lifecycle).consumeReceiptLocked(any());
            var result=f.service.consumeReceipt(T,901L);assertTrue(result.replayed());assertEquals(2,result.lines().size());
            verify(f.lifecycle,times(2)).consumeReceiptLocked(any());
        }
    }
    static String eventKey(PuRequirementAllocationLedger a,Long id){return "LIFECYCLE:"+hash(T,a.getAllocationKey(),"RECEIPT_POSTED",id.toString());}
    static class Fixture implements AutoCloseable {
        final PuRequirementErpTransactionExecutor executor=mock(PuRequirementErpTransactionExecutor.class);
        final PuRequirementErpTransactionQualification qualification=mock(PuRequirementErpTransactionQualification.class);
        final PuRequirementMaterialLockService locks=mock(PuRequirementMaterialLockService.class);
        final PuCertifiedPurchaseReceiptBatchMapper mapper=mock(PuCertifiedPurchaseReceiptBatchMapper.class);
        final PuCertifiedPurchaseLifecycleMapper physical=mock(PuCertifiedPurchaseLifecycleMapper.class);
        final PuCertifiedPurchaseLifecycleService lifecycle=mock(PuCertifiedPurchaseLifecycleService.class);
        final com.nexus.erp.wm.service.WmCertifiedReceiptPostingService posting=mock(com.nexus.erp.wm.service.WmCertifiedReceiptPostingService.class);
        final MockedStatic<TenantHelper> tenant=mockStatic(TenantHelper.class);
        final PuCertifiedPurchaseReceiptBatchService service=new PuCertifiedPurchaseReceiptBatchService(executor,qualification,locks,mapper,physical,lifecycle,posting);
        ReceiveHead receiveHead=new ReceiveHead(T,901L,801L,"FINISHED","0");OrderHead order=new OrderHead(T,801L,601L,"APPROVED","0");
        List<ReceiveLine> receives=new ArrayList<>();List<OrderLine> orders=new ArrayList<>();List<RequisitionHead> heads;
        List<RequisitionLine> reqLines=new ArrayList<>();List<CommandSource> commands;List<PuRequirementAllocationLedger> allocations=new ArrayList<>();List<StockEffect> effects=new ArrayList<>();
        Fixture() {
            tenant.when(TenantHelper::isEnable).thenReturn(true);tenant.when(TenantHelper::getTenantId).thenReturn(T);
            when(executor.executeRepeatableRead(eq(T),any())).thenAnswer(c->((Supplier<?>)c.getArgument(1)).get());
            when(locks.lock(eq(T),anyCollection())).thenReturn(Map.of(201L,5));when(mapper.readMaterialHints(T,901L)).thenReturn(List.of(201L));
            heads=List.of(new RequisitionHead(T,601L,"PR601","MRP","ORDERED",KEY,2,"0"));
            for(long i=1;i<=2;i++) {
                orders.add(new OrderLine(T,700+i,801L,400+i,201L,"501",TEN,BigDecimal.ZERO,BigDecimal.ZERO,"PENDING","0"));
                receives.add(new ReceiveLine(T,9000+i,901L,700+i,201L,"501",THREE,"0"));
                reqLines.add(new RequisitionLine(T,400+i,601L,201L,"501",TEN,TEN,"MRP",300+i,"2026-10-10","0"));
                var a=new PuRequirementAllocationLedger();a.setAllocationId(500+i);a.setTenantId(T);a.setMaterialId(201L);a.setSourceType("MRP_DETAIL");a.setAllocationNo(1);a.setDetailId(300+i);a.setRequisitionLineId(400+i);a.setOrderLineId(700+i);a.setBaseUomId(501L);a.setCoveredDemandBaseQty(TEN);a.setOccupiedBaseQty(TEN);a.setFulfilledBaseQty(BigDecimal.ZERO);a.setReleasedBaseQty(BigDecimal.ZERO);a.setAllocationStatus(ORDER_STATUS);a.setAllocationKey("allocation-"+(500+i));a.setDecisionIdempotencyKey(KEY);a.setVersion(2);a.setTransferTime(new Date());allocations.add(a);
                effects.add(new StockEffect(T,10000+i,"RECEIVE_IN",201L,"501","WM_MATERIAL_RECEIVE",901L,9000+i,1,THREE,BigDecimal.ZERO,THREE,11000+i,901L,"0"));
            }
            commands=List.of(command("COMMITTED"));definitions();refresh();
            doAnswer(call->{ReceiptCommand c=call.getArgument(0);var row=allocations.stream().filter(r->r.getAllocationKey().equals(c.allocationKey())).findFirst().orElseThrow();return receipt(row,c.receiveLineId(),false);}).when(lifecycle).consumeReceiptLocked(any());
        }
        CommandSource command(String status) {
            String frozen=hash(reqLines.stream().sorted(Comparator.comparing(RequisitionLine::sourceId)).map(l->hash(l.lineId().toString(),l.sourceId().toString(),l.materialId().toString(),l.unit(),l.applyQuantity().stripTrailingZeros().toPlainString(),l.requiredDate())).toArray(String[]::new));
            return new CommandSource(T,KEY,"a".repeat(64),901L,"MRP",201L,1001L,601L,"PR601","b".repeat(64),frozen,status,"c".repeat(64),"2026-10-10");
        }
        void refresh() {
            when(mapper.readReceiptHead(T,901L)).thenReturn(receiveHead);when(mapper.lockReceiptHead(T,901L)).thenReturn(receiveHead);
            when(mapper.lockReceiptLines(T,901L)).thenReturn(receives);when(mapper.lockOrderHead(T,801L)).thenReturn(order);
            when(mapper.lockOrderLines(T,801L)).thenReturn(orders);when(mapper.lockRequisitionHeadsForOrder(T,801L)).thenReturn(heads);
            when(mapper.lockRequisitionLinesForOrder(T,801L)).thenReturn(reqLines);when(mapper.lockCommandsForOrder(T,801L)).thenReturn(commands);
            when(mapper.lockAllocationsForOrder(T,801L)).thenReturn(allocations);when(physical.lockStockEffects(T,"WM_MATERIAL_RECEIVE",901L)).thenReturn(effects);
        }
        void definitions() {
            when(physical.pinAllocationDefinitions()).thenReturn(List.of());when(physical.pinLifecycleDefinitions()).thenReturn(List.of());
            when(physical.lifecycleTableDefinitions()).thenReturn(List.of("pu_certified_purchase_command","pu_requirement_allocation_ledger","pu_requirement_allocation_event","wm_material_receive","wm_material_receive_line","wm_material_stock_tx","pu_purchase_return","pu_purchase_return_line").stream().map(t->new TableDefinition(t,"InnoDB")).toList());
            List<IndexDefinition> keys=new ArrayList<>();
            Map<String,List<List<String>>> needed=Map.of("pu_certified_purchase_command",List.of(List.of("tenant_id","request_key")),"pu_requirement_allocation_ledger",List.of(List.of("tenant_id","allocation_key")),"pu_requirement_allocation_event",List.of(List.of("tenant_id","event_key"),List.of("tenant_id","allocation_id","event_sequence")));
            needed.forEach((table,sets)->{for(int j=0;j<sets.size();j++){var parts=sets.get(j);for(int i=0;i<parts.size();i++)keys.add(new IndexDefinition(table,"uk"+j,0,i+1,parts.get(i),null));}});
            when(physical.lifecycleUniqueDefinitions()).thenReturn(keys);
            when(physical.lifecycleQuantityDefinitions()).thenReturn(List.of("wm_material_receive_line.quantity_received","wm_material_stock_tx.tx_quantity","wm_material_stock_tx.before_quantity","wm_material_stock_tx.after_quantity","pu_purchase_return_line.return_quantity").stream().map(c->{String[] parts=c.split("\\.");return new NativeQuantityDefinition(parts[0],parts[1],"decimal",18,4);}).toList());
        }
        EventReceipt receipt(PuRequirementAllocationLedger a,Long id,boolean replay) {return new EventReceipt(id+20000,a.getAllocationId(),Operation.RECEIPT_POSTED,ORDER_STATUS,ORDER_STATUS,TEN,TEN.subtract(THREE),a.getRequisitionLineId(),a.getOrderLineId(),901L,replay);}
        PuRequirementAllocationEvent event(PuRequirementAllocationLedger a,Long id) {
            var e=new PuRequirementAllocationEvent();e.setTenantId(T);e.setEventId(id+20000);e.setAllocationId(a.getAllocationId());e.setEventKey(eventKey(a,id));e.setEventType("RECEIPT_POSTED");e.setEventSequence(3);e.setRequisitionLineId(a.getRequisitionLineId());e.setOrderLineId(a.getOrderLineId());e.setActorId(901L);e.setFromStatus(ORDER_STATUS);e.setToStatus(ORDER_STATUS);e.setOccupiedBaseBefore(TEN);e.setOccupiedBaseAfter(TEN.subtract(THREE));return e;
        }
        public void close(){tenant.close();}
    }
}
