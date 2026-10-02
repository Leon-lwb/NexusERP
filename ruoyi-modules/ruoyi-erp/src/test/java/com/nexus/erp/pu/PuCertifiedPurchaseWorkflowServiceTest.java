package com.nexus.erp.pu;
import cn.dev33.satoken.stp.StpUtil;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService.CertifiedPurchaseScope;
import com.nexus.erp.ai.adapter.ErpPurchaseCatalogSourceReader.CatalogSnapshot;
import com.nexus.erp.ai.adapter.ErpPurchaseQuantityPolicy;
import com.nexus.erp.ai.adapter.ErpPurchaseDemandAllocator;
import java.time.*;
import com.nexus.erp.pu.mapper.*;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseWorkflowMapper.*;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.*;
import com.nexus.erp.pu.service.*;
import com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Grouped writer-boundary tests; no external DB, server, SQL runner or shared Maven target. */
class PuCertifiedPurchaseWorkflowServiceTest {
    @Test void conversionCreatesOneActualOrderAndAllSourceLinesBeforeAtomicLedgerTransfers() {
        try(var f=new Fixture("APPROVED")) {
            var result=f.service.convert(601L,0);
            assertEquals(2,result.allocations().size());assertNotNull(result.orderId());assertEquals("PO-1",result.orderNo());
            assertEquals(2,f.orderLines.size());
            verify(f.mapper,times(1)).insertOrder(eq(f.t),anyLong(),eq("PO-1"),any(),any(),eq(901L),eq("operator"),eq(7L));
            verify(f.mapper,times(2)).insertOrderLine(eq(f.t),anyLong(),anyLong(),any(),eq(901L),eq(7L));
            verify(f.mapper,times(1)).casHead(eq(f.t),eq(601L),eq(f.key),eq("APPROVED"),eq("ORDERED"),eq(0),eq(901L));
            verify(f.lifecycle,times(2)).applyLocked(argThat(c->c.operation()==Operation.TRANSFER_TO_ORDER&&c.businessLineId()!=null));
            assertEquals(2,f.executions);assertEquals(1,f.numberCalls);
        }
    }
    @Test void repeatedConversionFindsSameOrderBeforeReservingAnyNewNumber() {
        try(var f=new Fixture("APPROVED")) {
            var first=f.service.convert(601L,0);clearInvocations(f.mapper,f.lifecycle,f.numbers);
            var second=f.service.convert(601L,0);
            assertTrue(second.replayed());assertEquals(first.orderId(),second.orderId());assertEquals(first.orderNo(),second.orderNo());
            verifyNoInteractions(f.numbers);verify(f.mapper,never()).insertOrder(any(),any(),any(),any(),any(),any(),any(),any());
            verify(f.mapper,never()).insertOrderLine(any(),any(),any(),any(),any(),any());
        }
    }
    @Test void cancellationChangesHeadExactlyOnceAndReleasesEveryUntransferredLine() {
        try(var f=new Fixture("DRAFT")) {
            var result=f.service.cancel(601L,0);
            assertEquals("CANCELLED",result.status());assertEquals(2,result.allocations().size());
            verify(f.mapper,times(1)).casHead(eq(f.t),eq(601L),eq(f.key),eq("DRAFT"),eq("CANCELLED"),eq(0),eq(901L));
            verify(f.lifecycle,times(2)).applyLocked(argThat(c->c.operation()==Operation.CANCEL_REQUISITION));
        }
    }
    @Test void secondSourcePreflightFailurePreventsAnyPartialDocumentMutation() {
        try(var f=new Fixture("APPROVED")) {
            doAnswer(a->{Command c=a.getArgument(0);if("allocation-102".equals(c.allocationKey()))throw new ServiceException("broken source");return null;})
                .when(f.lifecycle).validateForWorkflowLocked(any());
            assertThrows(ServiceException.class,()->f.service.convert(601L,0));
            verifyNoInteractions(f.numbers);verify(f.mapper,never()).casHead(any(),any(),any(),any(),any(),any(),any());
            verify(f.mapper,never()).insertOrder(any(),any(),any(),any(),any(),any(),any(),any());
        }
    }
    @Test void headerCasFailureDoesNotCreateAnOrderOrEmitLedgerSuccess() {
        try(var f=new Fixture("APPROVED")) {
            when(f.mapper.casHead(any(),any(),any(),any(),any(),any(),any())).thenReturn(0);
            assertThrows(ServiceException.class,()->f.service.convert(601L,0));
            verify(f.mapper,never()).insertOrder(any(),any(),any(),any(),any(),any(),any(),any());
            verify(f.lifecycle,never()).applyLocked(any());
        }
    }
    @Test void cancellationRejectsAnyActualOrderEvenIfLedgerPointerWasLost() {
        try(var f=new Fixture("DRAFT")) {
            f.orders.add(new PurchaseOrder(f.t,801L,601L,"PO-OLD","DRAFT","0",1501L));
            assertThrows(ServiceException.class,()->f.service.cancel(601L,0));
            verify(f.mapper,never()).casHead(any(),any(),any(),any(),any(),any(),any());
            verify(f.lifecycle,never()).applyLocked(any());
        }
    }
    @Test void approvalUsesAllFrozenQuantitiesAndOneHeaderCas() {
        try(var f=new Fixture("DRAFT")) {
            var result=f.service.approve(601L,0);
            assertEquals("APPROVED",result.status());assertEquals(2,result.allocations().size());
            verify(f.mapper,times(2)).approveLine(eq(f.t),any(),eq(901L));
            verify(f.mapper,times(1)).casHead(eq(f.t),eq(601L),eq(f.key),eq("DRAFT"),eq("APPROVED"),eq(0),eq(901L));
            verify(f.lifecycle,times(2)).applyLocked(argThat(c->c.operation()==Operation.APPROVE));
        }
    }
    @Test void configuredFlowableApprovalIsRejectedRatherThanClaimedCrossMasterAtomic() {
        try(var f=new Fixture("DRAFT")) {
            f.flowId=99L;
            assertThrows(ServiceException.class,()->f.service.approve(601L,0));
            verify(f.mapper,never()).casHead(any(),any(),any(),any(),any(),any(),any());
            verify(f.mapper,never()).approveLine(any(),any(),any());
        }
    }
    @Test void finalLedgerFailureRejectsWholeConversionNotAPartialSuccessResponse() {
        try(var f=new Fixture("APPROVED")) {
            doAnswer(a->{Command c=a.getArgument(0);if("allocation-102".equals(c.allocationKey()))throw new ServiceException("CAS failed");return f.receipt(c);})
                .when(f.lifecycle).applyLocked(any());
            assertThrows(ServiceException.class,()->f.service.convert(601L,0));
            verify(f.executor,times(2)).executeRepeatableRead(eq(f.t),any());
        }
    }
    @Test void closureReleasesAllLinesButConvertedRequestsCannotReleaseOrderCommitment() {
        try(var f=new Fixture("DRAFT")) {
            var result=f.service.close(601L,0);assertEquals("CLOSED",result.status());assertEquals(2,result.allocations().size());
        }
        try(var f=new Fixture("ORDERED")) {
            assertThrows(ServiceException.class,()->f.service.close(601L,0));
            verify(f.lifecycle,never()).applyLocked(any());
        }
    }
    @Test void permissionIsCheckedBeforeSourcesOrNumberReservation() {
        try(var f=new Fixture("APPROVED")) {
            f.auth.when(()->StpUtil.checkPermission("pu:requisition:convert")).thenThrow(new ServiceException("denied"));
            assertThrows(ServiceException.class,()->f.service.convert(601L,0));
            verifyNoInteractions(f.executor,f.mapper,f.sources,f.numbers);
        }
    }
    @Test void reducedMrpRequirementRejectsApprovalBeforeAnyDocumentOrOrderWrite() {
        try(var f=new Fixture("DRAFT")) {
            f.quantitySix();f.realAllocation(new BigDecimal("2"),BigDecimal.ZERO);
            assertThrows(ServiceException.class,()->f.service.approve(601L,0));
            assertNoBusinessWrite(f);verify(f.certification).prepareLocked(f.t,201L,1501L,f.end,Set.of(301L,302L));
        }
    }
    @Test void reducedMrpRequirementRejectsConversionWithZeroPurchaseOrderWrites() {
        try(var f=new Fixture("APPROVED")) {
            f.quantitySix();f.realAllocation(new BigDecimal("2"),BigDecimal.ZERO);
            assertThrows(ServiceException.class,()->f.service.convert(601L,0));
            assertNoBusinessWrite(f);verifyNoInteractions(f.numbers);
        }
    }
    @Test void additionalStockReducingTrueGapToTwoRejectsConversionWithZeroPurchaseOrderWrites() {
        try(var f=new Fixture("APPROVED")) {
            f.quantitySix();f.realAllocation(new BigDecimal("6"),new BigDecimal("4"));
            assertThrows(ServiceException.class,()->f.service.convert(601L,0));
            assertNoBusinessWrite(f);verifyNoInteractions(f.numbers);
        }
    }
    @Test void validCurrentCommitmentMayProduceEmptyNewLinesWithoutReoccupation() {
        try(var f=new Fixture("APPROVED")) {
            f.quantitySix();f.realAllocation(new BigDecimal("6"),BigDecimal.ZERO);
            var result=f.service.convert(601L,0);
            assertNotNull(result.orderId());assertEquals(2,result.allocations().size());
            verify(f.certification,times(2)).prepareLocked(f.t,201L,1501L,f.end,Set.of(301L,302L));
            verify(f.sources,never()).markCommandCompensated(any(),any(),any());
        }
    }
    @Test void sameTermsVersionButChangedEffectiveDateIsAChangedFrozenCatalog() {
        try(var f=new Fixture("APPROVED")) {
            f.currentCatalog=f.catalog(f.frozenCatalog.snapshotAt(),f.frozenCatalog.terms().effectiveAt().plusSeconds(1));
            assertEquals(f.frozenCatalog.terms().termsVersion(),f.currentCatalog.terms().termsVersion());
            assertThrows(ServiceException.class,()->f.service.convert(601L,0));
            assertNoBusinessWrite(f);verifyNoInteractions(f.numbers);
        }
    }
    @Test void onlyCatalogSnapshotClockMovingDoesNotChangeFrozenContract() {
        try(var f=new Fixture("APPROVED")) {
            f.currentCatalog=f.catalog(f.frozenCatalog.snapshotAt().plusSeconds(1),f.frozenCatalog.terms().effectiveAt());
            assertNotNull(f.service.convert(601L,0).orderId());
            verify(f.certification,times(2)).prepareLocked(f.t,201L,1501L,f.end,Set.of(301L,302L));
        }
    }
    @Test void missingCatalogFingerprintOrOriginalWindowCannotInheritAnOldContract() {
        try(var f=new Fixture("APPROVED")) {
            var c=f.commandSource;
            f.commandSource=new CommandSource(c.tenantId(),c.requestKey(),c.requestHash(),c.actorId(),c.entryType(),c.materialId(),c.vendorId(),
                c.requisitionId(),c.requisitionNo(),c.sourceFingerprint(),c.resultFingerprint(),c.commandStatus(),null,c.windowEnd());
            assertThrows(ServiceException.class,()->f.service.convert(601L,0));assertNoBusinessWrite(f);
        }
        try(var f=new Fixture("APPROVED")) {
            var c=f.commandSource;
            f.commandSource=new CommandSource(c.tenantId(),c.requestKey(),c.requestHash(),c.actorId(),c.entryType(),c.materialId(),c.vendorId(),
                c.requisitionId(),c.requisitionNo(),c.sourceFingerprint(),c.resultFingerprint(),c.commandStatus(),c.catalogFingerprint(),null);
            assertThrows(ServiceException.class,()->f.service.convert(601L,0));assertNoBusinessWrite(f);
        }
    }
    @Test void capDriftBetweenNumberPreflightAndActualCreationIsRejectedBySecondCurrentRead() {
        try(var f=new Fixture("APPROVED")) {
            when(f.certification.prepareLocked(eq(f.t),eq(201L),eq(1501L),eq(f.end),eq(Set.of(301L,302L))))
                .thenReturn(new CertifiedPurchaseScope(f.t,201L,1501L,LocalDate.of(2026,10,2),f.end,f.currentCatalog,null,List.of(),Map.of(),"e".repeat(64)))
                .thenThrow(new ServiceException("OVERCOMMITTED"));
            assertThrows(ServiceException.class,()->f.service.convert(601L,0));assertNoBusinessWrite(f);
            verify(f.numbers,times(1)).reserveOrderNumber();
        }
    }
    private static void assertNoBusinessWrite(Fixture f) {
        verify(f.mapper,never()).casHead(any(),any(),any(),any(),any(),any(),any());
        verify(f.mapper,never()).approveLine(any(),any(),any());
        verify(f.mapper,never()).insertOrder(any(),any(),any(),any(),any(),any(),any(),any());
        verify(f.mapper,never()).insertOrderLine(any(),any(),any(),any(),any(),any());
        verify(f.lifecycle,never()).applyLocked(any());
    }

    static final class Fixture implements AutoCloseable {
        final String t="000001",key="request-0000000001";
        final MockedStatic<TenantHelper> tenant=mockStatic(TenantHelper.class);
        final MockedStatic<LoginHelper> login=mockStatic(LoginHelper.class);
        final MockedStatic<StpUtil> auth=mockStatic(StpUtil.class);
        final PuRequirementErpTransactionExecutor executor=mock(PuRequirementErpTransactionExecutor.class);
        final PuRequirementErpTransactionQualification qualification=mock(PuRequirementErpTransactionQualification.class);
        final PuRequirementMaterialLockService locks=mock(PuRequirementMaterialLockService.class);
        final PuCertifiedPurchaseWorkflowMapper mapper=mock(PuCertifiedPurchaseWorkflowMapper.class);
        final PuCertifiedPurchaseLifecycleMapper sources=mock(PuCertifiedPurchaseLifecycleMapper.class);
        final PuCertifiedPurchaseLifecycleService lifecycle=mock(PuCertifiedPurchaseLifecycleService.class);
        final PuCertifiedPurchaseWorkflowNumberReservation numbers=mock(PuCertifiedPurchaseWorkflowNumberReservation.class);
        final ErpPurchaseSourceCertificationService certification=mock(ErpPurchaseSourceCertificationService.class);
        final PuCertifiedPurchaseWorkflowService service=new PuCertifiedPurchaseWorkflowService(executor,qualification,locks,mapper,sources,lifecycle,numbers,certification);
        final LocalDate end=LocalDate.of(2026,10,16);
        final CatalogSnapshot frozenCatalog=catalog(Instant.parse("2026-10-02T04:00:00Z"),Instant.parse("2026-10-01T04:00:00Z"));
        CatalogSnapshot currentCatalog=frozenCatalog;
        CommandSource commandSource;
        final List<PuRequirementAllocationLedger> rows=new ArrayList<>();
        final List<RequisitionLine> lines=new ArrayList<>();final List<PurchaseOrder> orders=new ArrayList<>();final List<OrderLine> orderLines=new ArrayList<>();
        String state;int version=0,executions=0,numberCalls=0;boolean callback=false;Long flowId=null;
        Fixture(String initial) {
            state=initial;
            tenant.when(TenantHelper::isEnable).thenReturn(true);tenant.when(TenantHelper::getTenantId).thenReturn(t);
            login.when(LoginHelper::getTenantId).thenReturn(t);login.when(LoginHelper::getUserId).thenReturn(901L);
            login.when(LoginHelper::getUsername).thenReturn("operator");login.when(LoginHelper::getDeptId).thenReturn(7L);
            when(executor.executeRepeatableRead(eq(t),any())).thenAnswer(a->{executions++;callback=true;try{return ((Supplier<?>)a.getArgument(1)).get();}finally{callback=false;}});
            when(numbers.reserveOrderNumber()).thenAnswer(a->{assertFalse(callback,"AutoCode may not run in owned ERP callback");numberCalls++;return "PO-1";});
            when(locks.lock(t,List.of(201L))).thenReturn(Map.of(201L,0));
            when(mapper.findMaterial(t,601L)).thenReturn(201L);
            when(mapper.lockHead(t,601L)).thenAnswer(a->new Head(t,601L,"REQ-1","MANUAL",state,key,version,"0",flowId));
            commandSource=new CommandSource(t,key,"a".repeat(64),901L,"MANUAL",201L,1501L,601L,"REQ-1","b".repeat(64),"c".repeat(64),"COMMITTED",
                ErpPurchaseSourceCertificationService.catalogFingerprint(frozenCatalog),end.toString());
            when(sources.lockCommand(t,key)).thenAnswer(a->commandSource);
            when(certification.prepareLocked(eq(t),eq(201L),eq(1501L),eq(end),eq(Set.of(301L,302L)))).thenAnswer(a->{
                assertTrue(callback,"All global source checks must run inside the owned RR callback");
                return new CertifiedPurchaseScope(t,201L,1501L,LocalDate.of(2026,10,2),end,currentCatalog,null,List.of(),Map.of(),"e".repeat(64));
            });
            for(int n=0;n<2;n++) {
                var row=PuCertifiedPurchaseLifecyclePolicyTest.intent();row.setAllocationId(101L+n);row.setRequisitionLineId(401L+n);row.setDetailId(301L+n);
                row.setAllocationKey("allocation-"+(101+n));row.setDecisionIdempotencyKey(key);rows.add(row);
                lines.add(new RequisitionLine(t,401L+n,601L,201L,"501",BigDecimal.TEN,"DRAFT".equals(initial)?BigDecimal.ZERO:BigDecimal.TEN,"MRP",301L+n,"2026-10-08","0"));
            }
            when(sources.lockCommandAllocations(t,key)).thenAnswer(a->List.copyOf(rows));
            when(sources.lockRequisitionLines(t,601L)).thenAnswer(a->List.copyOf(lines));
            when(mapper.lockOrders(t,601L)).thenAnswer(a->List.copyOf(orders));
            when(sources.lockOrderLines(eq(t),anyLong())).thenAnswer(a->List.copyOf(orderLines));
            when(mapper.lockVendor(t,1501L)).thenReturn(new Vendor(t,1501L,"V-1","Supplier",1L,"0"));
            when(mapper.casHead(eq(t),eq(601L),eq(key),anyString(),anyString(),anyInt(),eq(901L))).thenAnswer(a->{assertTrue(callback);if(!state.equals(a.getArgument(3))||version!=(Integer)a.getArgument(5))return 0;state=a.getArgument(4);version++;return 1;});
            when(mapper.approveLine(eq(t),any(),eq(901L))).thenAnswer(a->{RequisitionLine line=a.getArgument(1);int at=lines.indexOf(line);lines.set(at,new RequisitionLine(t,line.lineId(),601L,201L,"501",BigDecimal.TEN,BigDecimal.TEN,"MRP",line.sourceId(),line.requiredDate(),"0"));return 1;});
            when(mapper.insertOrder(eq(t),anyLong(),anyString(),any(),any(),eq(901L),eq("operator"),eq(7L))).thenAnswer(a->{assertTrue(callback);orders.add(new PurchaseOrder(t,a.getArgument(1),601L,a.getArgument(2),"DRAFT","0",1501L));return 1;});
            when(mapper.insertOrderLine(eq(t),anyLong(),anyLong(),any(),eq(901L),eq(7L))).thenAnswer(a->{RequisitionLine line=a.getArgument(3);orderLines.add(new OrderLine(t,a.getArgument(1),a.getArgument(2),line.lineId(),201L,"501",line.applyQuantity(),BigDecimal.ZERO,BigDecimal.ZERO,"PENDING","0"));return 1;});
            when(lifecycle.applyLocked(any())).thenAnswer(a->receipt(a.getArgument(0)));
        }
        CatalogSnapshot catalog(Instant at,Instant effective) {
            var unit=new ErpPurchaseQuantityPolicy.TrustedUnit(t,"501","501","quantity",BigDecimal.ONE,"u1",4,Instant.parse("2026-10-01T03:00:00Z"),true);
            var terms=new ErpPurchaseQuantityPolicy.SupplierTerms(t,"201","1601","501","501",BigDecimal.ONE,BigDecimal.ONE,false,
                BigDecimal.ONE,"terms-v1",effective,Instant.parse("2026-11-01T04:00:00Z"),Instant.parse("2026-10-01T03:00:00Z"),true);
            return new CatalogSnapshot(t,"1501","1601",at,"+08:00",new ErpPurchaseQuantityPolicy.Material(t,"201","501",true),terms,unit,unit,unit,Map.of("terms:1601","terms-v1","unit:501","u1"));
        }
        void quantitySix() {
            rows.get(0).setCoveredDemandBaseQty(new BigDecimal("6"));rows.get(0).setOccupiedBaseQty(new BigDecimal("6"));
            var l=lines.get(0);lines.set(0,new RequisitionLine(t,l.lineId(),l.requisitionId(),l.materialId(),l.unit(),new BigDecimal("6"),
                "DRAFT".equals(state)?BigDecimal.ZERO:new BigDecimal("6"),l.sourceType(),l.sourceId(),l.requiredDate(),l.delFlag()));
        }
        void realAllocation(BigDecimal required,BigDecimal stock) {
            when(certification.prepareLocked(eq(t),eq(201L),eq(1501L),eq(end),eq(Set.of(301L,302L)))).thenAnswer(a->{
                assertTrue(callback);var allocator=new ErpPurchaseDemandAllocator();
                var scope=new ErpPurchaseDemandAllocator.Scope(t,"201","501",LocalDate.of(2026,10,2),end,true,Set.of("301","302"));
                var demands=List.of(new ErpPurchaseDemandAllocator.Demand("301",LocalDate.of(2026,10,8),required,"501",true),
                    new ErpPurchaseDemandAllocator.Demand("302",LocalDate.of(2026,10,9),BigDecimal.TEN,"501",true));
                var commitments=rows.stream().map(r->new ErpPurchaseDemandAllocator.Commitment(r.getAllocationKey(),r.getDetailId().toString(),
                    ErpPurchaseDemandAllocator.CommitmentKind.PENDING,r.getOccupiedBaseQty(),"501",true)).toList();
                ErpPurchaseDemandAllocator.AllocationResult allocated;
                try {allocated=allocator.allocate(new ErpPurchaseDemandAllocator.AllocationInput(scope,
                    new ErpPurchaseDemandAllocator.Stock(stock,BigDecimal.ZERO,BigDecimal.ZERO,"501",true),demands,List.of(),commitments));}
                catch(ErpPurchaseDemandAllocator.AllocationRejectedException conflict) {
                    assertEquals(ErpPurchaseDemandAllocator.RejectionReason.OVERCOMMITTED,conflict.reason());throw new ServiceException(conflict.reason().name());
                }
                assertTrue(allocated.allocations().stream().allMatch(x->x.requestableBase().signum()==0));
                return new CertifiedPurchaseScope(t,201L,1501L,LocalDate.of(2026,10,2),end,currentCatalog,allocated,List.of(),Map.of(),"e".repeat(64));
            });
        }
        EventReceipt receipt(Command c) {
            var row=rows.stream().filter(r->r.getAllocationKey().equals(c.allocationKey())).findFirst().orElseThrow();
            boolean replay=state.equals("ORDERED")&&row.getOrderLineId()!=null;
            if(!replay){row.setVersion(row.getVersion()+1);if(c.operation()==Operation.TRANSFER_TO_ORDER){row.setOrderLineId(c.businessLineId());row.setAllocationStatus("ORDER_COMMITTED_NOT_TIMELY_SUPPLY");}
                if(c.operation()==Operation.CANCEL_REQUISITION||c.operation()==Operation.CLOSE_REQUISITION){row.setAllocationStatus("RELEASED");row.setOccupiedBaseQty(BigDecimal.ZERO);row.setReleasedBaseQty(BigDecimal.TEN);}}
            return new EventReceipt(row.getAllocationId()+1000,row.getAllocationId(),c.operation(),"REQUEST_INTENT",row.getAllocationStatus(),BigDecimal.TEN,row.getOccupiedBaseQty(),row.getRequisitionLineId(),row.getOrderLineId(),901L,replay);
        }
        public void close(){auth.close();login.close();tenant.close();}
    }
}
