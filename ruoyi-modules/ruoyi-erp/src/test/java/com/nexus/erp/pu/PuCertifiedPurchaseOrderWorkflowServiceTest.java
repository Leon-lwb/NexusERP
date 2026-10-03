package com.nexus.erp.pu;

import cn.dev33.satoken.stp.StpUtil;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.*;
import com.nexus.erp.ai.adapter.ErpPurchaseCatalogSourceReader.CatalogSnapshot;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService.CertifiedPurchaseScope;
import com.nexus.erp.pu.domain.PuRequirementAllocationLedger;
import com.nexus.erp.pu.mapper.*;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.*;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderWorkflowMapper.*;
import com.nexus.erp.pu.service.*;
import com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.*;
import com.nexus.erp.pu.service.PuCertifiedPurchaseOrderWorkflowService.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 私有当前来源语义测试；模拟对象代表持久化数据，绝不代表外部 verified 标志。 */
class PuCertifiedPurchaseOrderWorkflowServiceTest {
    @Test void supplierTermsAreFullyPersistedAndConfirmedByADifferentAuthorizedActor() {
        try(var f=new Fixture()) {
            var recorded=f.service.recordTerms(801L,f.request("quote-1"));
            Quote q=recorded.quote();assertEquals(1,q.quoteVersion());assertEquals("SUP-2026-77",q.supplierQuoteReference());
            assertEquals("erp-attachment:777",q.evidenceReference());assertEquals("1".repeat(64),q.evidenceSha256());
            assertEquals("2026-10-12",q.promisedArrivalDate());assertEquals("2026-10-03",q.validFrom());assertEquals("2026-10-15",q.validUntil());
            assertEquals(new BigDecimal("12.3456"),q.unitPriceExcludingTax());assertEquals(new BigDecimal("0.13"),q.taxRate());
            assertEquals("CNY",q.currency());assertEquals(BigDecimal.ONE,q.exchangeRate());assertEquals("Net 30",q.paymentTerms());
            assertEquals("Supplier delivery",q.deliveryTerms());assertEquals("supplier sales",q.supplierContact());
            assertEquals(901L,q.enteredBy());assertNotNull(q.enteredAt());assertEquals(64,q.quoteHash().length());
            assertEquals(f.frozenFingerprint,q.catalogFingerprint());assertEquals(new BigDecimal("20"),q.quantity());
            f.actor=902L;var confirmed=f.service.confirmTerms(801L,q.quoteId(),"confirm-1",q.quoteHash(),"已核对供应商完整条款和附件");
            assertEquals(902L,confirmed.confirmation().actorId());assertEquals(q.quoteHash(),confirmed.confirmation().quoteHash());
            assertEquals("pu:order:terms:confirm",confirmed.confirmation().permissionCode());
            verify(f.mapper,times(1)).insertQuote(any());verify(f.mapper,times(1)).insertConfirmation(any());
            assertEquals("DRAFT",f.state);assertEquals(0,f.version);assertEquals(2,f.executions);
        }
    }
    @Test void actualApprovalPricesAllFrozenRootUnitLinesAndAppendsAllEventsAfterOneHeadCas() {
        try(var f=new Fixture()) {
            Quote q=f.confirmed();f.actor=903L;
            var result=f.service.approve(801L,0,q.quoteId(),"approve-1");
            assertEquals("APPROVED",result.status());assertEquals(1,result.version());assertEquals(2,result.allocations().size());
            assertEquals("2026-10-12",result.expectedArrivalDate());assertEquals(new BigDecimal("246.92"),result.amountExcludingTax());
            assertEquals(new BigDecimal("32.10"),result.taxAmount());assertEquals(new BigDecimal("279.02"),result.totalAmount());
            assertEquals(q.quoteId(),result.quoteId());assertEquals(q.quoteHash(),result.quoteHash());
            verify(f.mapper,times(1)).approveHead(eq(f.t),any(),eq(q),any(),eq(903L));
            verify(f.mapper,times(2)).priceLine(eq(f.t),any(),argThat(x->x.net().equals(new BigDecimal("123.46"))&&x.tax().equals(new BigDecimal("16.05"))&&x.gross().equals(new BigDecimal("139.51"))),eq(903L));
            verify(f.lifecycle,times(3)).validateOrderForWorkflowLocked(argThat(c->c.orderId()==801L&&c.operation()==Operation.APPROVE_ORDER&&c.expectedAllocationVersions().size()==2));
            verify(f.lifecycle).applyOrderForWorkflowLocked(argThat(c->c.operation()==Operation.APPROVE_ORDER));
            verify(f.certification,times(3)).prepareLocked(eq(f.t),eq(201L),eq(1501L),eq(f.end),eq(Set.of(301L,302L)));
            assertTrue(f.rows.stream().allMatch(r->r.getCoveredDemandBaseQty().compareTo(BigDecimal.TEN)==0&&r.getOccupiedBaseQty().compareTo(BigDecimal.TEN)==0));
        }
    }
    @Test void committedApprovalReplaysOriginalResultAfterCloseWithoutNewPricingOrGlobalRecertification() {
        try(var f=new Fixture()) {
            Quote q=f.confirmed();f.actor=903L;var first=f.service.approve(801L,0,q.quoteId(),"approve-1");
            f.service.close(801L,1,"close-1");clearInvocations(f.mapper,f.certification,f.lifecycle);
            when(f.certification.prepareLocked(any(),any(),any(),any(),any())).thenThrow(new ServiceException("scope changed"));
            var replay=f.service.approve(801L,0,q.quoteId(),"approve-1");
            assertTrue(replay.replayed());assertEquals(first.status(),replay.status());assertEquals(first.version(),replay.version());
            assertEquals(first.totalAmount(),replay.totalAmount());assertEquals(first.allocations().get(0).eventId(),replay.allocations().get(0).eventId());
            assertEquals("CLOSED",f.state);verifyNoInteractions(f.certification);
            verify(f.mapper,never()).approveHead(any(),any(),any(),any(),any());verify(f.mapper,never()).priceLine(any(),any(),any(),any());
            verify(f.mapper,never()).insertOperation(any());
        }
    }
    @Test void cancellationOfRealUnpricedDraftReleasesInheritedLedgerWithoutInventingMoneyOrEta() {
        try(var f=new Fixture()) {
            var result=f.service.cancel(801L,0,"cancel-1");assertEquals("CANCELLED",result.status());
            assertNull(result.quoteId());assertNull(result.expectedArrivalDate());assertNull(result.totalAmount());
            assertEquals(2,result.allocations().size());verify(f.mapper,times(1)).closeHead(eq(f.t),any(),eq("CANCELLED"),eq(901L));
            verify(f.mapper,never()).priceLine(any(),any(),any(),any());
        }
    }
    @Test void sameQuoteRequestReplaysAndChangedPayloadCannotReuseIt() {
        try(var f=new Fixture()) {
            var first=f.service.recordTerms(801L,f.request("quote-1"));var second=f.service.recordTerms(801L,f.request("quote-1"));
            assertTrue(second.replayed());assertEquals(first.quote().quoteId(),second.quote().quoteId());verify(f.mapper,times(1)).insertQuote(any());
            var r=f.request("quote-1");var changed=new SupplierTermsRequest(r.requestKey(),r.expectedOrderVersion(),new BigDecimal("15"),r.taxRate(),r.currency(),r.exchangeRate(),
                r.promisedArrivalDate(),r.validFrom(),r.validUntil(),r.supplierQuoteReference(),r.supplierContact(),r.evidenceReference(),r.evidenceSha256(),r.paymentTerms(),r.deliveryTerms());
            assertThrows(ServiceException.class,()->f.service.recordTerms(801L,changed));
        }
    }
    @Test void quoteConfirmationCannotBeSelfApprovedAndNeverOverwritesAnAudit() {
        try(var f=new Fixture()) {
            Quote q=f.service.recordTerms(801L,f.request("q")).quote();
            assertThrows(ServiceException.class,()->f.service.confirmTerms(801L,q.quoteId(),"c",q.quoteHash(),"self"));verify(f.mapper,never()).insertConfirmation(any());
            f.actor=902L;f.service.confirmTerms(801L,q.quoteId(),"c",q.quoteHash(),"independent");
            assertTrue(f.service.confirmTerms(801L,q.quoteId(),"c",q.quoteHash(),"independent").replayed());
            assertThrows(ServiceException.class,()->f.service.confirmTerms(801L,q.quoteId(),"c2",q.quoteHash(),"replace"));
            verify(f.mapper,times(1)).insertConfirmation(any());
        }
    }
    @Test void globalOvercommitAndChangedFrozenCatalogRejectBeforeAnyHeadOrPriceMutation() {
        for(boolean overcommit:List.of(true,false))try(var f=new Fixture()) {
            Quote q=f.confirmed();f.actor=903L;
            if(overcommit)when(f.certification.prepareLocked(any(),any(),any(),any(),any())).thenThrow(new ServiceException("OVERCOMMITTED"));
            else f.currentCatalog=f.catalog("changed");
            assertThrows(ServiceException.class,()->f.service.approve(801L,0,q.quoteId(),"approve"));f.noOrderWrites();
        }
    }
    @Test void secondLineGroupedPreflightFailurePreventsAnyHeadAndLineWrites() {
        try(var f=new Fixture()) {
            Quote q=f.confirmed();f.actor=903L;doThrow(new ServiceException("second line source broken")).when(f.lifecycle).validateOrderForWorkflowLocked(any());
            assertThrows(ServiceException.class,()->f.service.approve(801L,0,q.quoteId(),"approve"));f.noOrderWrites();
        }
    }
    @Test void expiredQuoteOrMissingIndependentConfirmationIsNotAnApprovalSource() {
        for(boolean expired:List.of(true,false))try(var f=new Fixture()) {
            Quote q=f.confirmed();f.actor=903L;
            if(expired)f.today="2026-10-16";else f.confirmations.clear();
            assertThrows(ServiceException.class,()->f.service.approve(801L,0,q.quoteId(),"approve"));f.noOrderWrites();
        }
    }
    @Test void oldHistoricalOrderCannotUpgradeWithoutAPermanentCommittedSource() {
        try(var f=new Fixture()) {
            when(f.mapper.findMaterial(f.t,801L)).thenReturn(null);
            assertThrows(ServiceException.class,()->f.service.recordTerms(801L,f.request("quote")));assertThrows(ServiceException.class,()->f.service.cancel(801L,0,"cancel"));
            f.noOrderWrites();verify(f.mapper,never()).insertQuote(any());
        }
    }
    @Test void missingEtaAndZeroDefaultPriceAndForeignCurrencyAreRejectedNotFilledIn() {
        for(int mode=0;mode<4;mode++)try(var f=new Fixture()) {
            var r=f.request("quote");var changed=new SupplierTermsRequest(r.requestKey(),r.expectedOrderVersion(),mode==0?BigDecimal.ZERO:r.unitPriceExcludingTax(),
                mode==3?null:r.taxRate(),mode==1?"USD":r.currency(),r.exchangeRate(),mode==2?null:r.promisedArrivalDate(),r.validFrom(),r.validUntil(),
                r.supplierQuoteReference(),r.supplierContact(),r.evidenceReference(),r.evidenceSha256(),r.paymentTerms(),r.deliveryTerms());
            assertThrows(ServiceException.class,()->f.service.recordTerms(801L,changed));verify(f.mapper,never()).insertQuote(any());
        }
    }
    @Test void missingQuoteAndStaleVersionAndSameRequestOtherActorCannotWrite() {
        try(var f=new Fixture()) {
            assertThrows(ServiceException.class,()->f.service.approve(801L,0,999L,"approve"));f.noOrderWrites();
            Quote q=f.confirmed();f.actor=903L;assertThrows(ServiceException.class,()->f.service.approve(801L,1,q.quoteId(),"approve"));
            f.service.approve(801L,0,q.quoteId(),"approve");f.actor=904L;
            assertThrows(ServiceException.class,()->f.service.approve(801L,0,q.quoteId(),"approve"));
        }
    }
    @Test void failedLineCasDoesNotPublishLifecycleSuccessOrStoreACommittedOperation() {
        try(var f=new Fixture()) {
            Quote q=f.confirmed();f.actor=903L;when(f.mapper.priceLine(any(),any(),any(),any())).thenReturn(0);
            assertThrows(ServiceException.class,()->f.service.approve(801L,0,q.quoteId(),"approve"));
            verify(f.lifecycle,never()).applyOrderForWorkflowLocked(any());verify(f.mapper,never()).insertOperation(any());
        }
    }
    @Test void evenAReplayedCommandNeedsCurrentPermissionAndTenantActorAuthentication() {
        try(var f=new Fixture()) {
            f.service.recordTerms(801L,f.request("q"));f.auth.when(()->StpUtil.checkPermission("pu:order:terms:add")).thenThrow(new ServiceException("denied"));
            assertThrows(ServiceException.class,()->f.service.recordTerms(801L,f.request("q")));
            verify(f.mapper,times(1)).insertQuote(any());
        }
    }
    @Test void extraOrderLineOrLedgerWithDifferentRootCannotBePartiallyApproved() {
        for(boolean extra:List.of(true,false))try(var f=new Fixture()) {
            if(extra)f.lines.add(new OrderLine(f.t,799L,801L,499L,201L,"501",BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ZERO,"PENDING","0"));
            else f.rows.get(1).setBaseUomId(999L);
            assertThrows(ServiceException.class,()->f.service.recordTerms(801L,f.request("quote")));f.noOrderWrites();
        }
    }

    @Test void persistedApprovedQuoteProofIsRequiredInsideExistingOwnedRrForPhysicalPostingAndReplay() {
        try(var f=new Fixture()) {
            Quote q=f.confirmed();f.actor=903L;f.service.approve(801L,0,q.quoteId(),"approve-1");int before=f.executions;
            f.callback=true;
            assertDoesNotThrow(()->f.service.requireApprovedLocked(f.t,801L));
            f.state="PARTIALLY_RECEIVED";assertDoesNotThrow(()->f.service.requireApprovedLocked(f.t,801L));
            f.state="RECEIVED";assertDoesNotThrow(()->f.service.requireApprovedLocked(f.t,801L));
            assertEquals(before,f.executions,"physical posting verifier must join existing RR, never start one");
            f.confirmations.clear();assertThrows(ServiceException.class,()->f.service.requireApprovedLocked(f.t,801L));
            f.callback=false;
        }
    }
    @Test void positivePricesAndApprovedStatusWithoutPermanentQuoteWorkflowNeverAuthorizeStockPosting() {
        try(var f=new Fixture()) {
            f.state="APPROVED";f.totals=new Totals(BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.TEN);f.callback=true;
            assertThrows(ServiceException.class,()->f.service.requireApprovedLocked(f.t,801L));
            f.callback=false;verify(f.mapper,never()).insertOperation(any());
        }
    }
    @Test void terminalPostingReplayHasASeparateReadOnlyGateButNeverAuthorizesFreshPosting() {
        try(var f=new Fixture()) {
            Quote q=f.confirmed();f.actor=903L;f.service.approve(801L,0,q.quoteId(),"approve");f.service.close(801L,1,"close");
            int before=f.executions;f.callback=true;
            assertThrows(ServiceException.class,()->f.service.requireApprovedLocked(f.t,801L));
            assertDoesNotThrow(()->f.service.requireApprovedReplayLocked(f.t,801L));assertEquals(before,f.executions);
            doThrow(new ServiceException("unprocessed physical source")).when(f.lifecycle).settledOrderEventsLocked(f.t,201L,f.sourceKey,801L);
            assertThrows(ServiceException.class,()->f.service.requireApprovedReplayLocked(f.t,801L));
            f.callback=false;
        }
    }
    @Test void terminalPostingReplayCannotUseStatusAndPricesWithoutAPermanentCloseReceipt() {
        try(var f=new Fixture()) {
            Quote q=f.confirmed();f.actor=903L;f.service.approve(801L,0,q.quoteId(),"approve");f.state="CLOSED";f.version=2;f.callback=true;
            assertThrows(ServiceException.class,()->f.service.requireApprovedReplayLocked(f.t,801L));f.callback=false;
        }
    }
    static final class Fixture implements AutoCloseable {
        final String t="000000",sourceKey="manual-source",frozenFingerprint;
        final LocalDate end=LocalDate.of(2026,10,16);String today="2026-10-03",state="DRAFT";int version=0,executions=0;Long actor=901L,attachedQuote=null;
        boolean callback=false;Totals totals=null;String eta=null;
        final MockedStatic<StpUtil> auth=mockStatic(StpUtil.class);final MockedStatic<LoginHelper> login=mockStatic(LoginHelper.class);final MockedStatic<TenantHelper> tenant=mockStatic(TenantHelper.class);
        final PuRequirementErpTransactionExecutor executor=mock(PuRequirementErpTransactionExecutor.class);final PuRequirementErpTransactionQualification qualification=mock(PuRequirementErpTransactionQualification.class);
        final PuRequirementMaterialLockService locks=mock(PuRequirementMaterialLockService.class);final PuCertifiedPurchaseOrderWorkflowMapper mapper=mock(PuCertifiedPurchaseOrderWorkflowMapper.class);
        final PuCertifiedPurchaseLifecycleMapper sources=mock(PuCertifiedPurchaseLifecycleMapper.class);final PuCertifiedPurchaseLifecycleService lifecycle=mock(PuCertifiedPurchaseLifecycleService.class);
        final ErpPurchaseSourceCertificationService certification=mock(ErpPurchaseSourceCertificationService.class);
        final PuCertifiedPurchaseOrderWorkflowService service=new PuCertifiedPurchaseOrderWorkflowService(executor,qualification,locks,mapper,sources,lifecycle,certification);
        final List<OrderLine> lines=new ArrayList<>();final List<RequisitionLine> reqLines=new ArrayList<>();final List<PuRequirementAllocationLedger> rows=new ArrayList<>();
        final Map<Long,Quote> quotes=new LinkedHashMap<>();final Map<Long,Confirmation> confirmations=new LinkedHashMap<>();final Map<String,OperationRecord> operations=new HashMap<>();
        final Map<String,List<EventReceipt>> permanentEvents=new HashMap<>();final Map<Long,LinePrice> prices=new HashMap<>();CatalogSnapshot currentCatalog;
        Fixture() {
            currentCatalog=catalog("original");frozenFingerprint=ErpPurchaseSourceCertificationService.catalogFingerprint(currentCatalog);
            tenant.when(TenantHelper::isEnable).thenReturn(true);tenant.when(TenantHelper::getTenantId).thenReturn(t);
            login.when(LoginHelper::getTenantId).thenReturn(t);login.when(LoginHelper::getUserId).thenAnswer(a->actor);
            login.when(LoginHelper::getUsername).thenReturn("operator");login.when(LoginHelper::getDeptId).thenReturn(7L);
            when(executor.executeRepeatableRead(eq(t),any())).thenAnswer(a->{executions++;callback=true;try{return ((Supplier<?>)a.getArgument(1)).get();}finally{callback=false;}});
            doAnswer(a->{assertTrue(callback,"ownership must be rechecked inside RR");return null;}).when(qualification).requireSourceChainTransaction();
            when(locks.lock(t,List.of(201L))).thenReturn(Map.of(201L,0));when(mapper.findMaterial(t,801L)).thenReturn(201L);
            when(mapper.databaseDate()).thenAnswer(a->today);when(sources.databaseNow()).thenReturn(Date.from(Instant.parse("2026-10-03T04:00:00Z")));
            PuCertifiedPurchaseOrderWorkflowMapperTest.stubOrderDefinitions(sources);
            when(mapper.lockHead(t,801L)).thenAnswer(a->head());
            when(sources.lockRequisitionHead(t,601L)).thenReturn(new RequisitionHead(t,601L,"REQ-1","MANUAL","ORDERED",sourceKey,2,"0"));
            when(sources.lockCommand(t,sourceKey)).thenReturn(new CommandSource(t,sourceKey,"a".repeat(64),901L,"MANUAL",201L,1501L,601L,"REQ-1","b".repeat(64),"c".repeat(64),"COMMITTED",frozenFingerprint,end.toString()));
            for(int i=0;i<2;i++) {
                var row=new PuRequirementAllocationLedger();row.setAllocationId(101L+i);row.setTenantId(t);row.setMaterialId(201L);row.setBaseUomId(501L);row.setDetailId(301L+i);
                row.setRequisitionLineId(401L+i);row.setOrderLineId(701L+i);row.setDecisionIdempotencyKey(sourceKey);row.setAllocationKey("allocation-"+(101+i));
                row.setSourceType("MRP_DETAIL");row.setAllocationNo(1);row.setVersion(2);row.setAllocationStatus("ORDER_COMMITTED_NOT_TIMELY_SUPPLY");
                row.setCoveredDemandBaseQty(BigDecimal.TEN);row.setOccupiedBaseQty(BigDecimal.TEN);row.setFulfilledBaseQty(BigDecimal.ZERO);row.setReleasedBaseQty(BigDecimal.ZERO);rows.add(row);
                lines.add(new OrderLine(t,701L+i,801L,401L+i,201L,"501",BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO,"PENDING","0"));
                reqLines.add(new RequisitionLine(t,401L+i,601L,201L,"501",BigDecimal.TEN,BigDecimal.TEN,"MRP",301L+i,"2026-10-12","0"));
                when(sources.lockOrdersForRequisitionLine(t,401L+i)).thenReturn(List.of(lines.get(i)));
            }
            when(sources.lockOrderLines(t,801L)).thenAnswer(a->List.copyOf(lines));when(sources.lockCommandAllocations(t,sourceKey)).thenAnswer(a->List.copyOf(rows));
            when(sources.lockOrderAllocations(t,801L)).thenAnswer(a->List.copyOf(rows));when(sources.lockRequisitionLines(t,601L)).thenAnswer(a->List.copyOf(reqLines));
            when(mapper.lockMoneyLines(t,801L)).thenAnswer(a->lines.stream().map(l->{LinePrice p=prices.get(l.lineId());return new LineMoney(t,l.lineId(),801L,p==null?null:p.grossUnit(),p==null?BigDecimal.ZERO:p.netUnit(),p==null?null:p.rate(),p==null?null:p.gross(),p==null?BigDecimal.ZERO:p.net(),p==null?BigDecimal.ZERO:p.tax(),BigDecimal.ZERO);}).toList());
            when(certification.prepareLocked(eq(t),eq(201L),eq(1501L),eq(end),eq(Set.of(301L,302L)))).thenAnswer(a->{assertTrue(callback);return new CertifiedPurchaseScope(t,201L,1501L,LocalDate.of(2026,10,3),end,currentCatalog,null,List.of(),Map.of(),"e".repeat(64));});
            when(mapper.lockQuoteVersions(t,801L)).thenAnswer(a->quotes.values().stream().map(Quote::quoteVersion).sorted().toList());
            when(mapper.lockQuoteRequest(eq(t),anyString())).thenAnswer(a->quotes.values().stream().filter(q->q.requestKey().equals(a.getArgument(1))).findFirst().orElse(null));
            when(mapper.lockQuote(eq(t),anyLong())).thenAnswer(a->quotes.get(a.getArgument(1)));when(mapper.insertQuote(any())).thenAnswer(a->{assertTrue(callback);Quote q=a.getArgument(0);quotes.put(q.quoteId(),q);return 1;});
            when(mapper.lockConfirmation(eq(t),anyLong())).thenAnswer(a->confirmations.get(a.getArgument(1)));
            when(mapper.lockConfirmationRequest(eq(t),anyString())).thenAnswer(a->confirmations.values().stream().filter(c->c.requestKey().equals(a.getArgument(1))).findFirst().orElse(null));
            when(mapper.insertConfirmation(any())).thenAnswer(a->{assertTrue(callback);Confirmation c=a.getArgument(0);confirmations.put(c.quoteId(),c);return 1;});
            when(mapper.approveHead(eq(t),any(),any(),any(),any())).thenAnswer(a->{assertTrue(callback);state="APPROVED";version++;Quote q=a.getArgument(2);attachedQuote=q.quoteId();eta=q.promisedArrivalDate();totals=a.getArgument(3);return 1;});
            when(mapper.priceLine(eq(t),any(),any(),any())).thenAnswer(a->{OrderLine l=a.getArgument(1);prices.put(l.lineId(),a.getArgument(2));return 1;});
            when(mapper.closeHead(eq(t),any(),anyString(),any())).thenAnswer(a->{assertTrue(callback);state=a.getArgument(2);version++;return 1;});
            when(mapper.lockOperation(eq(t),anyString())).thenAnswer(a->operations.get(a.getArgument(1)));
            when(mapper.lockTerminalOperation(t,801L)).thenAnswer(a->operations.values().stream().filter(r->Set.of("CLOSE_ORDER","CANCEL_ORDER").contains(r.operation())).findFirst().orElse(null));
            when(mapper.lockApprovalOperation(t,801L)).thenAnswer(a->operations.values().stream().filter(r->r.operation().equals("APPROVE_ORDER")).findFirst().orElse(null));
            when(mapper.insertOperation(any())).thenAnswer(a->{assertTrue(callback);OperationRecord r=a.getArgument(0);operations.put(r.requestKey(),r);return 1;});
            when(lifecycle.settledOrderEventsLocked(t,201L,sourceKey,801L)).thenAnswer(a->permanentEvents.getOrDefault(state.equals("CLOSED")?"CLOSE_ORDER":"CANCEL_ORDER",List.of()));
            when(lifecycle.approvedOrderEventsLocked(t,201L,sourceKey,801L)).thenAnswer(a->permanentEvents.getOrDefault("APPROVE_ORDER",List.of()));
            when(lifecycle.applyOrderForWorkflowLocked(any())).thenAnswer(a->{OrderCommand c=a.getArgument(0);String key=c.operation().name();boolean replay=permanentEvents.containsKey(key);
                List<EventReceipt> events=permanentEvents.computeIfAbsent(key,k->rows.stream().map(r->new EventReceipt(1000L+10*c.operation().ordinal()+r.getAllocationId(),r.getAllocationId(),c.operation(),r.getAllocationStatus(),c.operation()==Operation.APPROVE_ORDER?r.getAllocationStatus():"RELEASED",r.getOccupiedBaseQty(),c.operation()==Operation.APPROVE_ORDER?r.getOccupiedBaseQty():BigDecimal.ZERO,r.getRequisitionLineId(),r.getOrderLineId(),actor,false)).toList());
                return new OrderReceipt(801L,c.operation(),events.stream().map(e->new EventReceipt(e.eventId(),e.allocationId(),e.operation(),e.fromStatus(),e.toStatus(),e.occupiedBefore(),e.occupiedAfter(),e.requisitionLineId(),e.orderLineId(),e.actorId(),replay)).toList(),replay);});
        }
        Head head(){return new Head(t,801L,"PO-1",601L,1501L,state,version,attachedQuote,eta,"0",null,"CNY",BigDecimal.ONE,totals==null?null:totals.gross(),totals==null?BigDecimal.ZERO:totals.net(),totals==null?null:totals.tax(),attachedQuote==null?null:BigDecimal.ZERO,attachedQuote==null?null:BigDecimal.ZERO,attachedQuote==null?null:BigDecimal.ZERO,attachedQuote==null?null:"Net 30",attachedQuote==null?null:"Supplier delivery");}
        SupplierTermsRequest request(String key){return new SupplierTermsRequest(key,0,new BigDecimal("12.3456"),new BigDecimal("0.13"),"CNY",BigDecimal.ONE,LocalDate.of(2026,10,12),LocalDate.of(2026,10,3),LocalDate.of(2026,10,15),"SUP-2026-77","supplier sales","erp-attachment:777","1".repeat(64),"Net 30","Supplier delivery");}
        Quote confirmed(){Quote q=service.recordTerms(801L,request("quote-1")).quote();actor=902L;service.confirmTerms(801L,q.quoteId(),"confirm-1",q.quoteHash(),"reviewed independently");return q;}
        CatalogSnapshot catalog(String version){Instant now=Instant.parse("2026-10-03T04:00:00Z");var unit=new ErpPurchaseQuantityPolicy.TrustedUnit(t,"501","501","quantity",BigDecimal.ONE,"unit-1",4,now,true);
            var material=new ErpPurchaseQuantityPolicy.Material(t,"201","501",true);
            var terms=new ErpPurchaseQuantityPolicy.SupplierTerms(t,"201","1601","501","501",BigDecimal.ZERO,BigDecimal.ONE,true,BigDecimal.ONE,"terms-1",Instant.parse("2026-10-01T04:00:00Z"),Instant.parse("2026-11-01T04:00:00Z"),now,true);
            return new CatalogSnapshot(t,"1501","1601",now,"+08:00",material,terms,unit,unit,unit,Map.of("vendor",version));}
        void noOrderWrites(){verify(mapper,never()).approveHead(any(),any(),any(),any(),any());verify(mapper,never()).priceLine(any(),any(),any(),any());verify(mapper,never()).closeHead(any(),any(),any(),any());verify(mapper,never()).insertOperation(any());}
        public void close(){auth.close();login.close();tenant.close();}
    }
}
