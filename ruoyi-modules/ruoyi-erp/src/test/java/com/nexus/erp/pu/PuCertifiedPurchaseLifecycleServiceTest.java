package com.nexus.erp.pu;

import cn.dev33.satoken.stp.StpUtil;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.domain.*;
import com.nexus.erp.pu.mapper.*;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.*;
import com.nexus.erp.pu.service.*;
import com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 不连接数据库或运行中的服务：持久化行由明确的边界测试替身表示，算术运算使用真实实现。 */
class PuCertifiedPurchaseLifecycleServiceTest {
    private static final String T = "000001", KEY = "request-0000000001", ALLOCATION = "allocation-101";
    private static final Date NOW = new Date(1790900000000L);
    private static final Long ACTOR = 901L;

    @Test void publicEntryUsesTheDedicatedRepeatableReadExecutor() {
        try (var f = new Fixture()) {
            assertNotNull(f.service.apply(f.command(Operation.APPROVE, null)));
            verify(f.executor).executeRepeatableRead(eq(T), any());
        }
    }
    @Test void lockedEntryCannotStartOrPretendToOwnATransaction() throws Exception {
        var annotation = PuCertifiedPurchaseLifecycleService.class.getMethod("applyLocked", Command.class)
            .getAnnotation(Transactional.class);
        assertNotNull(annotation);
        assertEquals(Propagation.MANDATORY, annotation.propagation());
        try (var f = new Fixture()) {
            doThrow(new ServiceException("owned RR missing")).when(f.qualification).requireSourceChainTransaction();
            assertThrows(ServiceException.class, () -> f.service.applyLocked(f.command(Operation.APPROVE, null)));
            verifyNoInteractions(f.mapper, f.ledgers, f.events, f.locks, f.guards);
        }
    }
    @Test void approvalPreservesSourceAndWritesExactlyOneCasAndAppendOnlyEvent() {
        try (var f = new Fixture()) {
            var result = f.service.applyLocked(f.command(Operation.APPROVE, null));
            assertEquals(new BigDecimal("10.0000"), result.occupiedAfter());
            assertFalse(result.replayed());
            verify(f.ledgers).updateWithVersion(eq(T), argThat(r -> r.getOrderLineId() == null
                && r.getOccupiedBaseQty().compareTo(BigDecimal.TEN) == 0), eq(0));
            verify(f.events).insert(argThat(e -> "APPROVE".equals(e.getEventType()) && ACTOR.equals(e.getActorId())));
            verify(f.guards).advanceVersion(T, 201L, 5);
        }
    }
    @Test void transferDoesNotInsertADuplicateLedger() {
        try (var f = new Fixture()) {
            f.requisition("ORDERED"); f.order("DRAFT", BigDecimal.ZERO, BigDecimal.ZERO);
            var result = f.service.applyLocked(f.command(Operation.TRANSFER_TO_ORDER, 701L));
            assertEquals("ORDER_COMMITTED_NOT_TIMELY_SUPPLY", result.toStatus());
            assertEquals(new BigDecimal("10.0000"), result.occupiedAfter());
            verify(f.ledgers, never()).insert(any());
            verify(f.ledgers).updateWithVersion(eq(T), argThat(r -> Long.valueOf(701).equals(r.getOrderLineId())), eq(0));
        }
    }
    @Test void transferRejectsDifferentQuantityUnitParentOrDuplicateDestination() {
        try (var f = new Fixture()) {
            f.requisition("ORDERED"); f.order("DRAFT", BigDecimal.ZERO, BigDecimal.ZERO);
            var o = f.orderLine;
            f.orderLine = new OrderLine(T,701L,801L,401L,201L,"501",new BigDecimal("11"),BigDecimal.ZERO,BigDecimal.ZERO,"PENDING","0");
            when(f.mapper.lockOrdersForRequisitionLine(T,401L)).thenReturn(List.of(f.orderLine));
            assertThrows(ServiceException.class, () -> f.service.applyLocked(f.command(Operation.TRANSFER_TO_ORDER,701L)));
            f.orderLine = new OrderLine(T,701L,801L,401L,201L,"公斤",BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO,"PENDING","0");
            when(f.mapper.lockOrdersForRequisitionLine(T,401L)).thenReturn(List.of(f.orderLine));
            assertThrows(ServiceException.class, () -> f.service.applyLocked(f.command(Operation.TRANSFER_TO_ORDER,701L)));
            when(f.mapper.lockOrdersForRequisitionLine(T,401L)).thenReturn(List.of(o,o));
            assertThrows(ServiceException.class, () -> f.service.applyLocked(f.command(Operation.TRANSFER_TO_ORDER,701L)));
            verify(f.ledgers,never()).updateWithVersion(any(),any(),any());
        }
    }
    @Test void closingRequisitionNeverReleasesAnOrderInheritedAllocation() {
        try (var f = new Fixture()) {
            f.transferred("CLOSED", "APPROVED", BigDecimal.ZERO, BigDecimal.ZERO);
            assertThrows(ServiceException.class, () -> f.service.applyLocked(f.command(Operation.CLOSE_REQUISITION,null)));
            verify(f.ledgers,never()).updateWithVersion(any(),any(),any());
        }
    }
    @Test void cancelledUntransferredRequisitionReleasesWithoutDeletingPermanentCommand() {
        try (var f = new Fixture()) {
            f.requisition("CANCELLED");
            var result=f.service.applyLocked(f.command(Operation.CANCEL_REQUISITION,null));
            assertEquals("RELEASED",result.toStatus());
            assertEquals(0,result.occupiedAfter().signum());
            verify(f.mapper,never()).markCommandCompensated(any(),any(),any());
        }
    }
    @Test void closesOnlyRemainderOfPartiallyFulfilledOrder() {
        try (var f = new Fixture()) {
            f.partiallyReceived("CLOSED");
            f.service.applyLocked(f.command(Operation.CLOSE_ORDER,null));
            verify(f.ledgers).updateWithVersion(eq(T),argThat(r -> r.getFulfilledBaseQty().compareTo(new BigDecimal("4"))==0
                && r.getReleasedBaseQty().compareTo(new BigDecimal("6"))==0 && r.getOccupiedBaseQty().signum()==0),eq(2));
        }
    }
    @Test void receiptReadsFinishedDocumentAndStockEffectsRatherThanCallerQuantity() {
        try (var f = new Fixture()) {
            f.transferred("ORDERED","APPROVED",BigDecimal.ZERO,BigDecimal.ZERO); f.receipt();
            var result=f.service.consumeReceiptLocked(f.receiptCommand());
            assertEquals(new BigDecimal("6.0000"),result.occupiedAfter());
            verify(f.mapper).updateOrderCounters(eq(T),eq(f.orderLine),eq(new BigDecimal("4.0000")),eq(BigDecimal.ZERO),eq("PARTIAL_RECEIVED"),eq(ACTOR));
            verify(f.ledgers).updateWithVersion(eq(T),argThat(r -> r.getFulfilledBaseQty().compareTo(new BigDecimal("4"))==0),eq(1));
        }
    }
    @Test void receiptRefusesUnfinishedMissingOrMismatchedPhysicalResult() {
        try (var f = new Fixture()) {
            f.transferred("ORDERED","APPROVED",BigDecimal.ZERO,BigDecimal.ZERO); f.receipt();
            when(f.mapper.lockStockEffects(T,"WM_MATERIAL_RECEIVE",1001L)).thenReturn(List.of());
            assertThrows(ServiceException.class,()->f.service.consumeReceiptLocked(f.receiptCommand()));
            verify(f.mapper,never()).updateOrderCounters(any(),any(),any(),any(),any(),any());
        }
    }
    @Test void multiLineReceiptWithUnboundStockResultIsExplicitlyUnsupported() {
        try(var f=new Fixture()) {
            f.transferred("ORDERED","APPROVED",BigDecimal.ZERO,BigDecimal.ZERO);f.receipt();
            when(f.mapper.lockReceiveLines(T,1001L)).thenReturn(List.of(f.receiveLine,f.receiveLine));
            assertThrows(ServiceException.class,()->f.service.consumeReceiptLocked(f.receiptCommand()));
            verify(f.ledgers,never()).updateWithVersion(any(),any(),any());
        }
    }
    @Test void durableDuplicateReceiptReturnsOriginalEventEvenAfterLaterVersion() {
        try(var f=new Fixture()) {
            f.partiallyReceived("PARTIALLY_RECEIVED");f.receipt();
            var event=f.history.get(2);
            when(f.events.lockByKey(eq(T),anyString())).thenReturn(event);
            var result=f.service.consumeReceiptLocked(f.receiptCommand());
            assertTrue(result.replayed());assertEquals(event.getEventId(),result.eventId());
            verify(f.mapper,never()).updateOrderCounters(any(),any(),any(),any(),any(),any());
            verify(f.events,never()).insert(any());
            verify(f.guards,never()).advanceVersion(any(),any(),any());
        }
    }
    @Test void unrelatedEventKeyCannotBeClaimedAsReplay() {
        try(var f=new Fixture()) {
            var event=f.event(Operation.APPROVE,0L,BigDecimal.TEN,BigDecimal.TEN,"REQUEST_INTENT","REQUEST_INTENT",null);
            event.setAllocationId(999L);
            when(f.events.lockByKey(eq(T),anyString())).thenReturn(event);
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.APPROVE,null)));
            verify(f.ledgers,never()).updateWithVersion(any(),any(),any());
        }
    }
    @Test void staleVersionOrFailedCasRollsBackInsteadOfAppendingSuccess() {
        try(var f=new Fixture()) {
            when(f.ledgers.updateWithVersion(any(),any(),any())).thenReturn(0);
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.APPROVE,null)));
            verify(f.events,never()).insert(any());
        }
    }
    @Test void missingSourceCreationEventOrUnknownCommandNeverAuthorizesTransition() {
        try(var f=new Fixture()) {
            when(f.events.lockByAllocation(T,101L)).thenReturn(List.of());
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.APPROVE,null)));
            verify(f.ledgers,never()).updateWithVersion(any(),any(),any());
        }
    }
    @Test void reqLineSourceIdentityAndFrozenResultFingerprintMustAgree() {
        try(var f=new Fixture()) {
            var bad=new RequisitionLine(T,401L,601L,201L,"501",BigDecimal.TEN,BigDecimal.TEN,"MRP",999L,"2026-10-08","0");
            when(f.mapper.lockRequisitionLine(T,401L)).thenReturn(bad);
            when(f.mapper.lockRequisitionLines(T,601L)).thenReturn(List.of(bad));
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.APPROVE,null)));
            verify(f.ledgers,never()).updateWithVersion(any(),any(),any());
        }
    }
    @Test void latePhysicalReceiptMustPreventOrderCloseBeforeItIsConsumed() {
        try(var f=new Fixture()) {
            f.transferred("ORDERED","CLOSED",BigDecimal.ZERO,BigDecimal.ZERO);
            when(f.mapper.lockReceiptSources(T,701L)).thenReturn(List.of(new MovementSource(T,1101L,1001L,"FINISHED","0","0")));
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.CLOSE_ORDER,null)));
            verify(f.ledgers,never()).updateWithVersion(any(),any(),any());
        }
    }
    @Test void executedReturnReopensOriginalSourceOnce() {
        try(var f=new Fixture()) {
            f.partiallyReceived("PARTIALLY_RECEIVED");f.returned();
            var result=f.service.applyLocked(f.command(Operation.RETURN_POSTED,1201L));
            assertEquals(new BigDecimal("8.0000"),result.occupiedAfter());
            verify(f.mapper).updateOrderCounters(eq(T),eq(f.orderLine),eq(new BigDecimal("4.0000")),eq(new BigDecimal("2.0000")),eq("PARTIAL_RECEIVED"),eq(ACTOR));
            verify(f.ledgers,never()).insert(any());
        }
    }
    @Test void returnRequiresExecutedPhysicalOutflowNotReturnDraftQuantity() {
        try(var f=new Fixture()) {
            f.partiallyReceived("PARTIALLY_RECEIVED");f.returned();
            when(f.mapper.lockStockEffects(T,"PU_PURCHASE_RETURN",1301L)).thenReturn(List.of());
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.RETURN_POSTED,1201L)));
            verify(f.mapper,never()).updateOrderCounters(any(),any(),any(),any(),any(),any());
        }
    }
    @Test void mapperFailureDoesNotLeakDatabaseDiagnostics() {
        try(var f=new Fixture()) {
            when(f.mapper.lockMaterial(T,201L)).thenThrow(new IllegalStateException("jdbc secret diagnostic"));
            var error=assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.APPROVE,null)));
            assertFalse(error.getMessage().contains("secret"));assertNull(error.getCause());
        }
    }
    @Test void crossTenantActorOrMissingPermissionIsRejectedBeforeAnyMapper() {
        try(var f=new Fixture()) {
            f.login.when(LoginHelper::getUserId).thenReturn(999L);
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.APPROVE,null)));
            verifyNoInteractions(f.mapper,f.ledgers,f.events,f.locks);
        }
    }
    @Test void permissionFailureIsNotAnExternallySuppliedVerifiedFlag() {
        try(var f=new Fixture()) {
            f.auth.when(()->StpUtil.checkPermission("pu:requisition:approve")).thenThrow(new ServiceException("denied"));
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.APPROVE,null)));
            verifyNoInteractions(f.mapper,f.ledgers,f.events,f.locks);
        }
    }

    @Test void wholeDraftCompensationKeepsPermanentKeyAndAtomicallyReleasesItsLedger() {
        try(var f=new Fixture()) {
            f.requisition("DRAFT");
            when(f.mapper.compensateDraft(T,601L,KEY,0,ACTOR)).thenReturn(1);
            when(f.mapper.compensateDraftLines(T,601L,ACTOR)).thenReturn(1);
            when(f.mapper.markCommandCompensated(T,KEY,601L)).thenReturn(1);
            var result=f.service.compensateDraftLocked(compensation());
            assertFalse(result.replayed()); assertEquals(1,result.allocations().size());
            assertEquals("RELEASED",result.allocations().get(0).toStatus());
            verify(f.mapper).markCommandCompensated(T,KEY,601L);
            verify(f.mapper).compensateDraft(T,601L,KEY,0,ACTOR);
            verify(f.ledgers).updateWithVersion(eq(T),argThat(r->r.getOccupiedBaseQty().signum()==0&&r.getReleasedBaseQty().compareTo(BigDecimal.TEN)==0),eq(0));
        }
    }
    @Test void compensatedKeyReturnsOriginalPermanentResultsWithoutAnotherRelease() {
        try(var f=new Fixture()) {
            f.row=PuRequirementAllocationPolicy.releaseRequestIntent(f.row,NOW,"DRAFT_COMPENSATED").ledger();f.row.setVersion(1);
            var event=f.event(Operation.COMPENSATE_DRAFT,601L,BigDecimal.TEN,BigDecimal.ZERO,"REQUEST_INTENT","RELEASED",null);
            f.history.add(event);
            when(f.events.lockByKey(eq(T),anyString())).thenReturn(event);
            when(f.mapper.lockRequisitionHead(T,601L)).thenReturn(new RequisitionHead(T,601L,"REQ-1","MANUAL","CANCELLED",KEY,1,"1"));
            var old=f.reqLine;
            when(f.mapper.lockRequisitionLines(T,601L)).thenReturn(List.of(new RequisitionLine(T,old.lineId(),old.requisitionId(),old.materialId(),old.unit(),old.applyQuantity(),old.approvedQuantity(),old.sourceType(),old.sourceId(),old.requiredDate(),"1")));
            when(f.mapper.lockCommand(T,KEY)).thenReturn(new CommandSource(T,KEY,"a".repeat(64),ACTOR,"MANUAL",201L,1501L,601L,"REQ-1","b".repeat(64),hash(hash("401","301","201","501","10","2026-10-08")),"COMPENSATED","d".repeat(64),"2026-10-16"));
            var result=f.service.compensateDraftLocked(compensation());
            assertTrue(result.replayed());assertEquals(event.getEventId(),result.allocations().get(0).eventId());
            verify(f.ledgers,never()).updateWithVersion(any(),any(),any());
            verify(f.mapper,never()).markCommandCompensated(any(),any(),any());
            verify(f.events,never()).insert(any());
        }
    }
    @Test void compensationRejectsAnyActualDownstreamOrderAndDoesNotTouchPermanentCommand() {
        try(var f=new Fixture()) {
            f.requisition("DRAFT");f.order("DRAFT",BigDecimal.ZERO,BigDecimal.ZERO);
            assertThrows(ServiceException.class,()->f.service.compensateDraftLocked(compensation()));
            verify(f.ledgers,never()).updateWithVersion(any(),any(),any());
            verify(f.mapper,never()).markCommandCompensated(any(),any(),any());
        }
    }
    @Test void compensationCommandCasFailureIsFailureNotSuccess() {
        try(var f=new Fixture()) {
            f.requisition("DRAFT");when(f.mapper.compensateDraft(T,601L,KEY,0,ACTOR)).thenReturn(1);
            when(f.mapper.compensateDraftLines(T,601L,ACTOR)).thenReturn(1);
            when(f.mapper.markCommandCompensated(T,KEY,601L)).thenReturn(0);
            assertThrows(ServiceException.class,()->f.service.compensateDraftLocked(compensation()));
            verify(f.guards,never()).advanceVersion(any(),any(),any());
        }
    }
    private static CompensationCommand compensation() {
        return new CompensationCommand(T,ACTOR,201L,KEY,601L,0,Map.of(ALLOCATION,0));
    }

    @Test void documentPreflightCannotNeedAFabricatedFutureOrderLineId() {
        try(var f=new Fixture()) {
            assertDoesNotThrow(()->f.service.validateForWorkflowLocked(f.command(Operation.TRANSFER_TO_ORDER,null)));
            verify(f.ledgers,never()).updateWithVersion(any(),any(),any());
        }
    }

    @Test void approvalRejectsExpiredActualTermsInsteadOfTrustingCreationFingerprint() {
        try(var f=new Fixture()) {
            when(f.mapper.lockPurchaseTerms(T,201L,1501L)).thenReturn(List.of(new PurchaseTerms(T,1601L,201L,1501L,501L,501L,
                BigDecimal.ZERO,BigDecimal.ONE,1,BigDecimal.ONE,"terms-v1",new Date(NOW.getTime()-1000),new Date(NOW.getTime()-1),NOW,1L,"0")));
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.APPROVE,null)));
            verify(f.ledgers,never()).updateWithVersion(any(),any(),any());
        }
    }
    @Test void terminalDatabaseClockRejectsTermsThatExpiredDuringTransition() {
        try(var f=new Fixture()) {
            when(f.mapper.lockPurchaseTerms(T,201L,1501L)).thenReturn(List.of(new PurchaseTerms(T,1601L,201L,1501L,501L,501L,
                BigDecimal.ZERO,BigDecimal.ONE,1,BigDecimal.ONE,"terms-v1",new Date(NOW.getTime()-1000),new Date(NOW.getTime()+1000),NOW,1L,"0")));
            when(f.mapper.databaseNow()).thenReturn(NOW,NOW,new Date(NOW.getTime()+2000));
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.APPROVE,null)));
        }
    }

    @Test void physicallyPostedReturnWithDeletedLineMustNotChangeCountersLedgerOrEvents() {
        try(var f=new Fixture()) {
            f.partiallyReceived("PARTIALLY_RECEIVED");f.returned();
            when(f.mapper.lockReturnLine(T,1201L)).thenReturn(returnFlags("0","1"));
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.RETURN_POSTED,1201L)));
            assertNoLifecycleWrites(f);
        }
    }
    @Test void physicallyPostedReturnWithDeletedHeaderMustNotChangeCountersLedgerOrEvents() {
        try(var f=new Fixture()) {
            f.partiallyReceived("PARTIALLY_RECEIVED");f.returned();
            when(f.mapper.lockReturnLine(T,1201L)).thenReturn(returnFlags("1","0"));
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.RETURN_POSTED,1201L)));
            assertNoLifecycleWrites(f);
        }
    }
    @Test void consumedReceiptWithDeletedLineMustStillBlockOrderClosure() {
        try(var f=new Fixture()) {
            f.partiallyReceived("CLOSED");
            when(f.mapper.lockReceiptSources(T,701L)).thenReturn(List.of(new MovementSource(T,1101L,1001L,"FINISHED","0","1")));
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.CLOSE_ORDER,null)));
            assertNoLifecycleWrites(f);
        }
    }
    @Test void consumedReceiptWithDeletedHeaderMustStillBlockOrderClosure() {
        try(var f=new Fixture()) {
            f.partiallyReceived("CLOSED");
            when(f.mapper.lockReceiptSources(T,701L)).thenReturn(List.of(new MovementSource(T,1101L,1001L,"FINISHED","1","0")));
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.CLOSE_ORDER,null)));
            assertNoLifecycleWrites(f);
        }
    }
    @Test void nonDeletedConsumedReceiptAllowsClosingOnlyUnfulfilledRemainder() {
        try(var f=new Fixture()) {
            f.partiallyReceived("CLOSED");
            when(f.mapper.lockReceiptSources(T,701L)).thenReturn(List.of(new MovementSource(T,1101L,1001L,"FINISHED","0","0")));
            assertEquals("RELEASED",f.service.applyLocked(f.command(Operation.CLOSE_ORDER,null)).toStatus());
            verify(f.ledgers).updateWithVersion(eq(T),argThat(r->r.getReleasedBaseQty().compareTo(new BigDecimal("6"))==0),eq(2));
        }
    }
    @Test void consumedReturnWithDeletedLineMustStillBlockOrderClosure() {
        try(var f=new Fixture()) {
            consumedReturn(f);
            when(f.mapper.lockReturnSources(T,701L)).thenReturn(List.of(new MovementSource(T,1201L,1301L,"EXECUTED","0","1")));
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.CLOSE_ORDER,null)));
            assertNoLifecycleWrites(f);
        }
    }
    @Test void consumedReturnWithDeletedHeaderMustStillBlockOrderClosure() {
        try(var f=new Fixture()) {
            consumedReturn(f);
            when(f.mapper.lockReturnSources(T,701L)).thenReturn(List.of(new MovementSource(T,1201L,1301L,"EXECUTED","1","0")));
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.CLOSE_ORDER,null)));
            assertNoLifecycleWrites(f);
        }
    }
    @Test void nonDeletedConsumedReturnAllowsClosingOnlyUnfulfilledRemainder() {
        try(var f=new Fixture()) {
            consumedReturn(f);f.returned();
            when(f.mapper.lockReturnSources(T,701L)).thenReturn(List.of(new MovementSource(T,1201L,1301L,"EXECUTED","0","0")));
            assertEquals("RELEASED",f.service.applyLocked(f.command(Operation.CLOSE_ORDER,null)).toStatus());
            verify(f.ledgers).updateWithVersion(eq(T),argThat(r->r.getReleasedBaseQty().compareTo(new BigDecimal("8"))==0),eq(3));
        }
    }
    private static ReturnLine returnFlags(String head,String line) {
        return new ReturnLine(T,1201L,1301L,701L,201L,"501",new BigDecimal("2.0000"),801L,"EXECUTED",head,line);
    }
    private static void consumedReturn(Fixture f) {
        f.partiallyReceived("CLOSED");
        f.row=PuRequirementAllocationPolicy.returnOrder(f.row,new BigDecimal("2.0000"),NOW).ledger();f.row.setVersion(3);
        f.history.add(f.event(Operation.RETURN_POSTED,1201L,new BigDecimal("6.0000"),new BigDecimal("8.0000"),
            "ORDER_COMMITTED_NOT_TIMELY_SUPPLY","ORDER_COMMITTED_NOT_TIMELY_SUPPLY",701L));
        f.order("CLOSED",new BigDecimal("4.0000"),new BigDecimal("2.0000"));
    }
    private static void assertNoLifecycleWrites(Fixture f) {
        verify(f.mapper,never()).updateOrderCounters(any(),any(),any(),any(),any(),any());
        verify(f.mapper,never()).updateOrderStatus(any(),any(),any(),any(),any());
        verify(f.ledgers,never()).updateWithVersion(any(),any(),any());
        verify(f.events,never()).insert(any());verify(f.guards,never()).advanceVersion(any(),any(),any());
    }

    @Test void sameSecondReverseJvmSnowflakeIdsRecoverByPersistentAllocationSequence() {
        try(var f=new Fixture()) {
            var approve=f.event(Operation.APPROVE,601L,BigDecimal.TEN,BigDecimal.TEN,"REQUEST_INTENT","REQUEST_INTENT",null);
            f.history.get(0).setEventId(900L);approve.setEventId(100L);
            f.history.add(approve);f.row.setVersion(1);
            when(f.events.lockByAllocation(T,101L)).thenAnswer(a->{
                String query=String.join(" ",PuRequirementAllocationEventMapper.class.getMethod("lockByAllocation",String.class,Long.class).getAnnotation(org.apache.ibatis.annotations.Select.class).value());
                var values=new ArrayList<>(f.history);
                if(query.contains("ORDER BY event_sequence")) values.sort(Comparator.comparing(PuRequirementAllocationEvent::getEventSequence));
                else values.sort(Comparator.comparing(PuRequirementAllocationEvent::getOccurredAt).thenComparing(PuRequirementAllocationEvent::getEventId));
                return values;
            });
            when(f.events.lockByKey(eq(T),anyString())).thenReturn(approve);
            assertTrue(f.service.applyLocked(f.command(Operation.APPROVE,null)).replayed());
            assertNoLifecycleWrites(f);
        }
    }
    @Test void legacyNullEventSequenceIsRejectedInsteadOfInferredFromTimeOrId() {
        try(var f=new Fixture()) {
            f.history.get(0).setEventSequence(null);
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.APPROVE,null)));
            assertNoLifecycleWrites(f);
        }
    }
    @Test void duplicateOrGappedSequenceCannotAuthorizeAnotherTransition() {
        for(Integer sequence:Arrays.asList(null,0,2))try(var f=new Fixture()) {
            f.transferred("ORDERED","CLOSED",BigDecimal.ZERO,BigDecimal.ZERO);
            f.history.get(1).setEventSequence(sequence);
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.CLOSE_ORDER,null)));
            assertNoLifecycleWrites(f);
        }
    }
    @Test void newlyAppendedSequenceMustEqualTheNextLedgerCasVersion() {
        try(var f=new Fixture()) {
            f.service.applyLocked(f.command(Operation.APPROVE,null));
            verify(f.events).insert(argThat(e->Integer.valueOf(1).equals(e.getEventSequence())));
            verify(f.ledgers).updateWithVersion(eq(T),any(),eq(0));
        }
    }
    @Test void receiptReplayRefusesChangedReceivedCounterEvenWithMatchingPhysicalEffectAndEventKey() {
        try(var f=new Fixture()) {
            f.partiallyReceived("PARTIALLY_RECEIVED");f.receipt();
            f.order("PARTIALLY_RECEIVED",BigDecimal.ZERO,BigDecimal.ZERO);
            when(f.events.lockByKey(eq(T),anyString())).thenReturn(f.history.get(2));
            assertThrows(ServiceException.class,()->f.service.consumeReceiptLocked(f.receiptCommand()));
            assertNoLifecycleWrites(f);
        }
    }
    @Test void returnReplayRefusesChangedSourceQuantityAndMatchingChangedStockEffect() {
        try(var f=new Fixture()) {
            consumedReturn(f);f.returned();
            when(f.mapper.lockReturnLine(T,1201L)).thenReturn(new ReturnLine(T,1201L,1301L,701L,201L,"501",new BigDecimal("3.0000"),801L,"EXECUTED","0","0"));
            when(f.mapper.lockStockEffects(T,"PU_PURCHASE_RETURN",1301L)).thenReturn(List.of(
                new StockEffect(T,2201L,"PUR_RETURN_OUT",201L,"501","PU_PURCHASE_RETURN",1301L,1201L,-1,
                    new BigDecimal("3.0000"),new BigDecimal("4.0000"),new BigDecimal("1.0000"),3002L,ACTOR,"0")));
            when(f.events.lockByKey(eq(T),anyString())).thenReturn(f.history.get(3));
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.RETURN_POSTED,1201L)));
            assertNoLifecycleWrites(f);
        }
    }
    @Test void validReturnReplayKeepsOriginalEventWithoutCounterOrLedgerWrites() {
        try(var f=new Fixture()) {
            consumedReturn(f);f.returned();
            when(f.events.lockByKey(eq(T),anyString())).thenReturn(f.history.get(3));
            var result=f.service.applyLocked(f.command(Operation.RETURN_POSTED,1201L));
            assertTrue(result.replayed());assertEquals(f.history.get(3).getEventId(),result.eventId());
            assertNoLifecycleWrites(f);
        }
    }
    @Test void releasedReturnWithoutFrozenPostedQuantityIsRejectedBeforeAnyWrite() {
        try(var f=new Fixture()) {
            closedPartial(f);f.returned();
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.RETURN_POSTED,1201L)));
            assertNoLifecycleWrites(f);
        }
    }
    @Test void releasedReturnZeroDeltaPriorKeyIsNotProofOfOriginalPostedQuantity() {
        try(var f=new Fixture()) {
            closedPartial(f);
            f.row=PuRequirementAllocationPolicy.returnOrder(f.row,new BigDecimal("2.0000"),NOW).ledger();f.row.setVersion(4);
            var prior=f.event(Operation.RETURN_POSTED,1201L,BigDecimal.ZERO,BigDecimal.ZERO,"RELEASED","RELEASED",701L);f.history.add(prior);
            f.order("CLOSED",new BigDecimal("4.0000"),new BigDecimal("2.0000"));f.returned();
            when(f.events.lockByKey(eq(T),anyString())).thenReturn(prior);
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.RETURN_POSTED,1201L)));
            assertNoLifecycleWrites(f);
        }
    }
    @Test void physicalReplayStillRejectsDeletedLineWithAnExistingPermanentKey() {
        try(var f=new Fixture()) {
            consumedReturn(f);f.returned();when(f.events.lockByKey(eq(T),anyString())).thenReturn(f.history.get(3));
            when(f.mapper.lockReturnLine(T,1201L)).thenReturn(returnFlags("0","1"));
            assertThrows(ServiceException.class,()->f.service.applyLocked(f.command(Operation.RETURN_POSTED,1201L)));
            assertNoLifecycleWrites(f);
        }
    }
    private static void closedPartial(Fixture f) {
        f.partiallyReceived("CLOSED");f.row=PuRequirementAllocationPolicy.releaseOrder(f.row,NOW,"CLOSE_ORDER").ledger();f.row.setVersion(3);
        f.history.add(f.event(Operation.CLOSE_ORDER,801L,new BigDecimal("6.0000"),BigDecimal.ZERO,"ORDER_COMMITTED_NOT_TIMELY_SUPPLY","RELEASED",701L));
    }

    @Test void physicalReplayRejectsGrossCounterDriftEvenWhenNetFulfillmentIsUnchanged() {
        try(var f=new Fixture()) {
            consumedReturn(f);f.receipt();
            f.order("PARTIALLY_RECEIVED",new BigDecimal("2.0000"),BigDecimal.ZERO);
            when(f.events.lockByKey(eq(T),anyString())).thenReturn(f.history.get(2));
            assertThrows(ServiceException.class,()->f.service.consumeReceiptLocked(f.receiptCommand()));
            assertNoLifecycleWrites(f);
        }
    }
    static final class Fixture implements AutoCloseable {
        final MockedStatic<TenantHelper> tenant=mockStatic(TenantHelper.class);
        final MockedStatic<LoginHelper> login=mockStatic(LoginHelper.class);
        final MockedStatic<StpUtil> auth=mockStatic(StpUtil.class);
        final PuRequirementErpTransactionExecutor executor=mock(PuRequirementErpTransactionExecutor.class);
        final PuRequirementErpTransactionQualification qualification=mock(PuRequirementErpTransactionQualification.class);
        final PuRequirementMaterialLockService locks=mock(PuRequirementMaterialLockService.class);
        final PuRequirementMaterialGuardMapper guards=mock(PuRequirementMaterialGuardMapper.class);
        final PuRequirementAllocationLedgerMapper ledgers=mock(PuRequirementAllocationLedgerMapper.class);
        final PuRequirementAllocationEventMapper events=mock(PuRequirementAllocationEventMapper.class);
        final PuCertifiedPurchaseLifecycleMapper mapper=mock(PuCertifiedPurchaseLifecycleMapper.class);
        final PuCertifiedPurchaseLifecycleService service=new PuCertifiedPurchaseLifecycleService(executor,qualification,locks,guards,ledgers,events,mapper);
        PuRequirementAllocationLedger row=PuCertifiedPurchaseLifecyclePolicyTest.intent();
        final List<PuRequirementAllocationEvent> history=new ArrayList<>();
        final RequisitionLine reqLine=new RequisitionLine(T,401L,601L,201L,"501",new BigDecimal("10.0000"),new BigDecimal("10.0000"),"MRP",301L,"2026-10-08","0");
        OrderLine orderLine; ReceiveLine receiveLine;
        Fixture() {
            tenant.when(TenantHelper::isEnable).thenReturn(true);tenant.when(TenantHelper::getTenantId).thenReturn(T);
            login.when(LoginHelper::getTenantId).thenReturn(T);login.when(LoginHelper::getUserId).thenReturn(ACTOR);
            when(executor.executeRepeatableRead(eq(T),any())).thenAnswer(a -> ((Supplier<?>)a.getArgument(1)).get());
            row.setDecisionIdempotencyKey(KEY);
            when(locks.lock(T,List.of(201L))).thenReturn(Map.of(201L,5));
            when(guards.advanceVersion(any(),any(),any())).thenReturn(1);
            when(ledgers.lockByKey(T,ALLOCATION)).thenAnswer(a->row);
            when(ledgers.updateWithVersion(any(),any(),any())).thenReturn(1);
            when(mapper.lockMaterial(T,201L)).thenReturn(new Material(T,201L,501L,1L,"0"));
            when(mapper.lockRootUnit(T,501L)).thenReturn(new RootUnit(T,501L,"件",1L,501L,BigDecimal.ONE,4,"uom-v1",NOW,1L,"0"));
            when(mapper.databaseNow()).thenReturn(NOW); when(mapper.commandTableEngine()).thenReturn("InnoDB");
            when(mapper.pinCommandDefinition()).thenReturn(List.of());
            PuCertifiedPurchaseLifecycleSourceGateTest.stub(mapper);
            when(mapper.pinAllocationDefinitions()).thenReturn(List.of());
            when(mapper.lockLifecycleVendor(T,1501L)).thenReturn(new VendorIdentity(T,1501L,"ACTIVE","0"));
            when(mapper.lockPurchaseTerms(T,201L,1501L)).thenReturn(List.of(new PurchaseTerms(T,1601L,201L,1501L,501L,501L,
                BigDecimal.ZERO,BigDecimal.ONE,1,BigDecimal.ONE,"terms-v1",new Date(NOW.getTime()-1000),new Date(NOW.getTime()+86400000),NOW,1L,"0")));
            when(mapper.lockDemandDetail(T,301L)).thenReturn(new DemandDetail(T,301L,1401L,201L,1L,"0"));
            when(mapper.lockDemandHead(T,1401L)).thenReturn(new DemandHead(T,1401L,"CONFIRMED","0"));
            when(mapper.lockRequisitionLine(T,401L)).thenReturn(reqLine);
            when(mapper.lockRequisitionLines(T,601L)).thenReturn(List.of(reqLine));
            when(mapper.lockRequisitionAllocations(T,601L)).thenAnswer(a->List.of(row));
            when(mapper.lockCommandAllocations(T,KEY)).thenAnswer(a->List.of(row));
            when(mapper.lockRelatedAllocations(eq(T),eq(401L),any())).thenAnswer(a->List.of(row));
            when(mapper.lockCommand(T,KEY)).thenReturn(new CommandSource(T,KEY,"a".repeat(64),ACTOR,"MANUAL",201L,1501L,601L,"REQ-1","b".repeat(64),hash(hash("401","301","201","501","10","2026-10-08")),"COMMITTED","d".repeat(64),"2026-10-16"));
            requisition("APPROVED");when(mapper.lockOrdersForRequisitionLine(T,401L)).thenReturn(List.of());
            when(mapper.lockReceiptSources(T,701L)).thenReturn(List.of());when(mapper.lockReturnSources(T,701L)).thenReturn(List.of());
            when(mapper.updateOrderCounters(any(),any(),any(),any(),any(),any())).thenReturn(1);
            when(mapper.updateOrderStatus(any(),any(),any(),any(),any())).thenReturn(1);
            when(events.insert(any())).thenReturn(1);
            var created=new PuRequirementAllocationEvent();created.setEventId(1L);created.setTenantId(T);created.setAllocationId(101L);
            created.setEventSequence(0);created.setEventKey("CREATE:"+ALLOCATION);created.setEventType("CREATE");created.setToStatus("REQUEST_INTENT");
            created.setOccupiedBaseBefore(BigDecimal.ZERO);created.setOccupiedBaseAfter(new BigDecimal("10.0000"));
            created.setRequisitionLineId(401L);created.setActorId(ACTOR);created.setOccurredAt(NOW);history.add(created);
            when(events.lockByAllocation(T,101L)).thenAnswer(a->List.copyOf(history));
        }
        Command command(Operation op,Long line) {return new Command(T,ACTOR,201L,ALLOCATION,KEY,row.getVersion(),op,line);}
        ReceiptCommand receiptCommand(){return new ReceiptCommand(T,201L,ALLOCATION,KEY,1,1101L);}
        void requisition(String state){when(mapper.lockRequisitionHead(T,601L)).thenReturn(new RequisitionHead(T,601L,"REQ-1","MANUAL",state,KEY,0,"0"));}
        void order(String state,BigDecimal received,BigDecimal returned) {
            orderLine=new OrderLine(T,701L,801L,401L,201L,"501",new BigDecimal("10.0000"),received,returned,"PENDING","0");
            when(mapper.lockOrdersForRequisitionLine(T,401L)).thenAnswer(a->List.of(orderLine));
            when(mapper.lockOrderLines(T,801L)).thenAnswer(a->List.of(orderLine));
            when(mapper.lockOrderHead(T,801L)).thenReturn(new OrderHead(T,801L,601L,state,"0"));
        }
        void transferred(String requisition,String order,BigDecimal received,BigDecimal returned) {
            row=PuRequirementAllocationPolicy.commitToOrder(row,701L,NOW).ledger();row.setVersion(1);
            history.add(event(Operation.TRANSFER_TO_ORDER,701L,BigDecimal.TEN,BigDecimal.TEN,"REQUEST_INTENT",row.getAllocationStatus(),701L));
            requisition(requisition);order(order,received,returned);
        }
        void partiallyReceived(String orderStatus) {
            transferred("ORDERED",orderStatus,new BigDecimal("4.0000"),BigDecimal.ZERO);
            row=PuRequirementAllocationPolicy.receiveOrder(row,new BigDecimal("4.0000"),NOW).ledger();row.setVersion(2);
            history.add(event(Operation.RECEIPT_POSTED,1101L,BigDecimal.TEN,new BigDecimal("6.0000"),row.getAllocationStatus(),row.getAllocationStatus(),701L));
            receipt();when(mapper.lockReceiptSources(T,701L)).thenReturn(List.of(new MovementSource(T,1101L,1001L,"FINISHED","0","0")));
        }
        void receipt() {
            receiveLine=new ReceiveLine(T,1101L,1001L,701L,201L,"501",new BigDecimal("4.0000"),"0");
            when(mapper.lockReceiveLine(T,1101L)).thenReturn(receiveLine);
            when(mapper.lockReceiveLines(T,1001L)).thenReturn(List.of(receiveLine));
            when(mapper.lockReceiveHead(T,1001L)).thenReturn(new ReceiveHead(T,1001L,801L,"FINISHED","0"));
            when(mapper.lockStockEffects(T,"WM_MATERIAL_RECEIVE",1001L)).thenReturn(List.of(
                new StockEffect(T,2101L,"VIRTUAL_OUT",201L,"501","WM_MATERIAL_RECEIVE",1001L,null,-1,new BigDecimal("4.0000"),new BigDecimal("4.0000"),BigDecimal.ZERO,3001L,ACTOR,"0"),
                new StockEffect(T,2102L,"TARGET_IN",201L,"501","WM_MATERIAL_RECEIVE",1001L,null,1,new BigDecimal("4.0000"),BigDecimal.ZERO,new BigDecimal("4.0000"),3002L,ACTOR,"0")));
        }
        void returned() {
            when(mapper.lockReturnLine(T,1201L)).thenReturn(new ReturnLine(T,1201L,1301L,701L,201L,"501",new BigDecimal("2.0000"),801L,"EXECUTED","0","0"));
            when(mapper.lockStockEffects(T,"PU_PURCHASE_RETURN",1301L)).thenReturn(List.of(
                new StockEffect(T,2201L,"PUR_RETURN_OUT",201L,"501","PU_PURCHASE_RETURN",1301L,1201L,-1,new BigDecimal("2.0000"),new BigDecimal("4.0000"),new BigDecimal("2.0000"),3002L,ACTOR,"0")));
        }
        PuRequirementAllocationEvent event(Operation op,Long source,BigDecimal before,BigDecimal after,String from,String to,Long orderLine) {
            var e=new PuRequirementAllocationEvent();e.setEventSequence(history.size());e.setEventId((long)history.size()+1);e.setTenantId(T);e.setAllocationId(101L);
            e.setEventKey("LIFECYCLE:"+hash(T,ALLOCATION,op.name(),source.toString()));e.setEventType(op.name());
            e.setFromStatus(from);e.setToStatus(to);e.setOccupiedBaseBefore(before);e.setOccupiedBaseAfter(after);
            e.setRequisitionLineId(401L);e.setOrderLineId(orderLine);e.setActorId(ACTOR);e.setOccurredAt(NOW);return e;
        }
        public void close(){auth.close();login.close();tenant.close();}
    }
    @Test void groupedActualOrderApprovalAppendsASequenceWithoutChangingAnyFrozenBalance() {
        try(var f=new Fixture()) {
            f.transferred("ORDERED","APPROVED",BigDecimal.ZERO,BigDecimal.ZERO);stubOrderGroup(f);
            var receipt=f.service.applyOrderForWorkflowLocked(orderGroup(f,Operation.APPROVE_ORDER));
            assertFalse(receipt.replayed());assertEquals(1,receipt.allocations().size());assertEquals(Operation.APPROVE_ORDER,receipt.allocations().get(0).operation());
            verify(f.ledgers).updateWithVersion(eq(T),argThat(r->r.getCoveredDemandBaseQty().compareTo(BigDecimal.TEN)==0&&r.getOccupiedBaseQty().compareTo(BigDecimal.TEN)==0&&r.getFulfilledBaseQty().signum()==0&&r.getReleasedBaseQty().signum()==0),eq(1));
            verify(f.events).insert(argThat(e->"APPROVE_ORDER".equals(e.getEventType())&&Integer.valueOf(2).equals(e.getEventSequence())));
        }
    }
    @Test void groupedClosureRetainsGrossReceivedAndFulfilledAndReleasesOnlyRemainingSix() {
        try(var f=new Fixture()) {
            f.partiallyReceived("CLOSED");stubOrderGroup(f);
            when(f.mapper.lockReceiptSources(T,701L)).thenReturn(List.of(new MovementSource(T,1101L,1001L,"FINISHED","0","0")));
            var receipt=f.service.applyOrderForWorkflowLocked(orderGroup(f,Operation.CLOSE_ORDER));
            assertEquals(0,receipt.allocations().get(0).occupiedAfter().signum());
            verify(f.ledgers).updateWithVersion(eq(T),argThat(r->r.getFulfilledBaseQty().compareTo(new BigDecimal("4"))==0&&r.getReleasedBaseQty().compareTo(new BigDecimal("6"))==0&&r.getCoveredDemandBaseQty().compareTo(BigDecimal.TEN)==0),eq(2));
            verify(f.mapper,never()).updateOrderCounters(any(),any(),any(),any(),any(),any());
        }
    }
    @Test void groupedOrderRejectsAnExtraActualLineBeforeWritingAnyLedger() {
        try(var f=new Fixture()) {
            f.transferred("ORDERED","CLOSED",BigDecimal.ZERO,BigDecimal.ZERO);stubOrderGroup(f);
            when(f.mapper.lockOrderLines(T,801L)).thenReturn(List.of(f.orderLine,new OrderLine(T,799L,801L,499L,201L,"501",BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.ZERO,"PENDING","0")));
            assertThrows(ServiceException.class,()->f.service.applyOrderForWorkflowLocked(orderGroup(f,Operation.CLOSE_ORDER)));assertNoLifecycleWrites(f);
        }
    }
    @Test void groupedApprovalCannotUseACallerCommandInsteadOfPersistedIndependentQuoteProof() {
        try(var f=new Fixture()) {
            f.transferred("ORDERED","APPROVED",BigDecimal.ZERO,BigDecimal.ZERO);stubOrderGroup(f);when(f.mapper.lockOrderApprovalSource(T,801L)).thenReturn(null);
            assertThrows(ServiceException.class,()->f.service.applyOrderForWorkflowLocked(orderGroup(f,Operation.APPROVE_ORDER)));assertNoLifecycleWrites(f);
        }
    }
    @Test void readOnlyQuotePreflightDoesNotRequireQuoteEntererToAlsoHaveOrderApprovalPermission() {
        try(var f=new Fixture()) {
            f.transferred("ORDERED","DRAFT",BigDecimal.ZERO,BigDecimal.ZERO);stubOrderGroup(f);
            f.auth.when(()->StpUtil.checkPermission("pu:order:approve")).thenThrow(new ServiceException("no approval role"));
            assertDoesNotThrow(()->f.service.validateOrderForWorkflowLocked(orderGroup(f,Operation.APPROVE_ORDER)));
            assertThrows(ServiceException.class,()->f.service.applyOrderForWorkflowLocked(orderGroup(f,Operation.APPROVE_ORDER)));
            assertNoLifecycleWrites(f);
        }
    }
    @Test void finishedReceiptWithMatchingEventKeyButChangedPhysicalQuantityCannotReleaseTheOrder() {
        try(var f=new Fixture()) {
            f.partiallyReceived("CLOSED");f.receipt();stubOrderGroup(f);
            when(f.mapper.lockReceiptSources(T,701L)).thenReturn(List.of(new MovementSource(T,1101L,1001L,"FINISHED","0","0")));
            ReceiveLine changed=new ReceiveLine(T,1101L,1001L,701L,201L,"501",new BigDecimal("3"),"0");
            when(f.mapper.lockReceiveLine(T,1101L)).thenReturn(changed);when(f.mapper.lockReceiveLines(T,1001L)).thenReturn(List.of(changed));
            when(f.mapper.lockStockEffects(T,"WM_MATERIAL_RECEIVE",1001L)).thenReturn(List.of(new StockEffect(T,2102L,"RECEIVE_IN",201L,"501","WM_MATERIAL_RECEIVE",1001L,1101L,1,new BigDecimal("3"),BigDecimal.ZERO,new BigDecimal("3"),3002L,ACTOR,"0")));
            assertThrows(ServiceException.class,()->f.service.applyOrderForWorkflowLocked(orderGroup(f,Operation.CLOSE_ORDER)));assertNoLifecycleWrites(f);
        }
    }
    @Test void nativeLineAttributedReceiveInIsARealPhysicalSourceWithoutInventingAVirtualOut() {
        try(var f=new Fixture()) {
            f.transferred("ORDERED","APPROVED",BigDecimal.ZERO,BigDecimal.ZERO);f.receipt();
            when(f.mapper.lockStockEffects(T,"WM_MATERIAL_RECEIVE",1001L)).thenReturn(List.of(new StockEffect(T,2102L,"RECEIVE_IN",201L,"501","WM_MATERIAL_RECEIVE",1001L,1101L,1,new BigDecimal("4"),BigDecimal.ZERO,new BigDecimal("4"),3002L,ACTOR,"0")));
            assertDoesNotThrow(()->f.service.consumeReceiptLocked(f.receiptCommand()));
        }
    }
    @Test void existingGuardOnlyPreflightTraversesTheRealLifecycleWithoutProvisioning() throws Exception {
        try (var f=new Fixture()) {
            f.transferred("ORDERED","DRAFT",BigDecimal.ZERO,BigDecimal.ZERO);stubOrderGroup(f);
            when(f.locks.lockExisting(T,List.of(201L))).thenReturn(Map.of(201L,5));
            var method=assertDoesNotThrow(()->PuCertifiedPurchaseLifecycleService.class.getMethod(
                "validateOrderForWorkflowExistingGuardLocked",PuCertifiedPurchaseLifecycleService.OrderCommand.class));
            assertDoesNotThrow(()->method.invoke(f.service,orderGroup(f,Operation.APPROVE_ORDER)));
            verify(f.locks).lockExisting(T,List.of(201L));verify(f.locks,never()).lock(any(),any());
            assertNoLifecycleWrites(f);
        }
    }
    @Test void existingGuardOnlyPreflightDoesNotTurnMissingGuardIntoBirth() throws Exception {
        try (var f=new Fixture()) {
            f.transferred("ORDERED","DRAFT",BigDecimal.ZERO,BigDecimal.ZERO);stubOrderGroup(f);
            when(f.locks.lockExisting(T,List.of(201L))).thenReturn(Map.of());
            var method=assertDoesNotThrow(()->PuCertifiedPurchaseLifecycleService.class.getMethod(
                "validateOrderForWorkflowExistingGuardLocked",PuCertifiedPurchaseLifecycleService.OrderCommand.class));
            var error=assertThrows(java.lang.reflect.InvocationTargetException.class,
                ()->method.invoke(f.service,orderGroup(f,Operation.APPROVE_ORDER)));
            assertInstanceOf(ServiceException.class,error.getCause());verify(f.locks,never()).lock(any(),any());
            assertNoLifecycleWrites(f);
        }
    }
    private static PuCertifiedPurchaseLifecycleService.OrderCommand orderGroup(Fixture f,Operation op) {
        return new PuCertifiedPurchaseLifecycleService.OrderCommand(T,ACTOR,201L,KEY,801L,op,Map.of(ALLOCATION,f.row.getVersion()));
    }
    private static void stubOrderGroup(Fixture f) {
        PuCertifiedPurchaseOrderWorkflowMapperTest.stubOrderDefinitions(f.mapper);
        when(f.mapper.lockOrderAllocations(T,801L)).thenAnswer(a->List.of(f.row));when(f.mapper.lockCommandAllocations(T,KEY)).thenAnswer(a->List.of(f.row));
        when(f.mapper.lockOrderApprovalSource(T,801L)).thenReturn(new OrderApprovalSource(T,801L,1501L,1801L,1,new BigDecimal("113"),new BigDecimal("100"),new BigDecimal("13"),
            "2026-10-12",201L,501L,KEY,"d".repeat(64),"e".repeat(64),902L,903L,"e".repeat(64),"pu:order:terms:confirm",NOW));
    }
    static String hash(String... values) {
        try {var d=MessageDigest.getInstance("SHA-256");for(String value:values){byte[] b=value.getBytes(StandardCharsets.UTF_8);d.update(java.nio.ByteBuffer.allocate(4).putInt(b.length).array());d.update(b);}return HexFormat.of().formatHex(d.digest());}
        catch(Exception impossible){throw new AssertionError(impossible);}
    }
}
