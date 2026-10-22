package com.nexus.ai.service.decision;

import static com.nexus.ai.service.decision.DecisionTestFixtures.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nexus.ai.service.sse.AiDecisionSseMessageHelper;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionFreshnessResult;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.enums.DecisionRunStatus;
import com.nexus.common.ai.decision.port.DecisionWorkflowPort;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;
import com.nexus.common.ai.domain.entity.AiDecisionAction;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.service.PermissionService;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

class DecisionApprovalServiceTest {
    private final DecisionPersistenceService store = mock(DecisionPersistenceService.class);
    private final IndustrialDecisionDataPort data = mock(IndustrialDecisionDataPort.class);
    private final DecisionWorkflowPort flows = mock(DecisionWorkflowPort.class);
    private final DecisionApprovalService service = new DecisionApprovalService(store, data, flows);

    @Test void revokedReviewPermissionCannotApproveDespiteOldSessionSnapshot() {
        var rights = mock(PermissionService.class);
        var events = mock(AiDecisionSseMessageHelper.class);
        var reviewer =
                new DecisionApprovalService(
                        store, data, flows, events, mock(TransactionTemplate.class), rights);
        when(rights.getMenuPermission(1L)).thenReturn(Set.of("ai:decision:run"));
        assertThrows(ServiceException.class, () -> reviewer.approve(10L, approvalRequest(), actor()));
        verifyNoInteractions(store, data, flows);
    }

    @Test void approvalBindsReviewerBeforeDispatchAndEmitsStatus() {
        var events = mock(AiDecisionSseMessageHelper.class);
        var tx = mock(TransactionTemplate.class);
        doAnswer(
                        call -> {
                            ((Consumer<TransactionStatus>) call.getArgument(0))
                                    .accept(mock(TransactionStatus.class));
                            return null;
                        })
                .when(tx)
                .executeWithoutResult(any());
        var withEvents = new DecisionApprovalService(store, data, flows, events, tx);
        var run = waitingRun("hash-old");
        run.setBizType("SCHEDULE_PLAN"); run.setBizId(9001L); run.setVersion(0);
        when(store.requireRun(10L, "000000")).thenReturn(run);
        var action = new AiDecisionAction();
        action.setId(20L); action.setRunId(10L); action.setApprovalStatus("PROPOSED");
        action.setExecutionStatus("NOT_STARTED"); action.setPermissionCode("ai:decision:execute");
        action.setActionType("SEND_MANAGER_NOTIFICATION"); action.setExecutorCode("managerNotification");
        action.setApprovalRequired("N");
        when(store.listActions(10L)).thenReturn(List.of(action));
        when(data.canAccess(any(), any())).thenReturn(true);
        when(data.checkFreshness(any(), eq("hash-old"), any()))
            .thenReturn(new DecisionFreshnessResult(true, "hash-old", List.of()));
        when(flows.startExecution(any())).thenReturn(77L);
        withEvents.approve(10L, approvalRequest(), actor());
        InOrder order = inOrder(store, flows, events);
        verify(tx).executeWithoutResult(any());
        order.verify(store).bindExecutionActor(10L, actor());
        order.verify(store).setActionApproval(20L, "PROPOSED", "APPROVED", actor(), approvalRequest().getComment());
        order.verify(flows).startExecution(any());
        order.verify(events).sendApprovalStatus(1L, 10L, "APPROVED");
    }

    @Test void approverWithoutExecutionPermissionIsRejectedBeforeAnySideEffect() {
        var reviewer =
                new DecisionActor(
                        "000000", 1L, "reviewer", Set.of("ai:decision:run", "ai:decision:approve"));
        assertThrows(ServiceException.class, () -> service.approve(10L, approvalRequest(), reviewer));
        verifyNoInteractions(store, data, flows);
    }

    @Test void failedExecutionFlowAttachmentStopsOrphanBeforeInvalidatingApprovedRun() {
        var run = waitingRun("hash-old");
        run.setBizType("SCHEDULE_PLAN"); run.setBizId(9001L); run.setVersion(0);
        var approved = waitingRun("hash-old");
        approved.setStatus("APPROVED");
        when(store.requireRun(10L, "000000")).thenReturn(run, approved);
        var action = new AiDecisionAction();
        action.setId(20L); action.setRunId(10L); action.setApprovalStatus("PROPOSED");
        action.setExecutionStatus("NOT_STARTED"); action.setPermissionCode("ai:decision:execute");
        action.setActionType("SEND_MANAGER_NOTIFICATION"); action.setExecutorCode("managerNotification");
        action.setApprovalRequired("N");
        when(store.listActions(10L)).thenReturn(List.of(action));
        when(data.canAccess(any(), any())).thenReturn(true);
        when(data.checkFreshness(any(), eq("hash-old"), any()))
            .thenReturn(new DecisionFreshnessResult(true, "hash-old", List.of()));
        when(flows.startExecution(any())).thenReturn(77L);
        doThrow(new ServiceException("db failed")).when(store).attachExecutionFlow(10L, 77L);
        when(flows.stop(77L, actor())).thenReturn(true);

        assertThrows(ServiceException.class, () -> service.approve(10L, approvalRequest(), actor()));
        InOrder order = inOrder(flows, store);
        order.verify(flows).startExecution(any());
        order.verify(store).attachExecutionFlow(10L, 77L);
        order.verify(flows).stop(77L, actor());
        order.verify(store).transition(10L, DecisionRunStatus.APPROVED, DecisionRunStatus.STALE);
    }
    @Test void unconfirmedExecutionFlowStopRecordsItsIdForTenantAudit() {
        readyForApproval();
        when(flows.startExecution(any())).thenReturn(77L);
        doThrow(new ServiceException("db failed")).when(store).attachExecutionFlow(10L, 77L);
        when(flows.stop(77L, actor())).thenReturn(false);

        assertThrows(ServiceException.class, () -> service.approve(10L, approvalRequest(), actor()));
        verify(flows).stop(77L, actor());
        verify(store).recordUnlinkedExecutionFlow(10L, 77L);
        verify(store).transition(10L, DecisionRunStatus.APPROVED, DecisionRunStatus.STALE);
    }

    @Test void ssePublishFailureAfterExecutionStartedDoesNotInvalidateApproval() {
        readyForApproval();
        var events = mock(AiDecisionSseMessageHelper.class);
        var withEvents = new DecisionApprovalService(store, data, flows, events);
        when(flows.startExecution(any())).thenReturn(77L);
        doThrow(new IllegalStateException("redis temporarily down")).when(events)
            .sendApprovalStatus(1L, 10L, "APPROVED");

        assertDoesNotThrow(() -> withEvents.approve(10L, approvalRequest(), actor()));
        verify(flows, never()).stop(anyLong(), any());
        verify(store, never()).transition(10L, DecisionRunStatus.APPROVED, DecisionRunStatus.STALE);
    }

    private void readyForApproval() {
        var run = waitingRun("hash-old");
        run.setBizType("SCHEDULE_PLAN"); run.setBizId(9001L); run.setVersion(0);
        when(store.requireRun(10L, "000000")).thenReturn(run);
        var action = new AiDecisionAction();
        action.setId(20L); action.setRunId(10L); action.setApprovalStatus("PROPOSED");
        action.setExecutionStatus("NOT_STARTED"); action.setPermissionCode("ai:decision:execute");
        action.setActionType("SEND_MANAGER_NOTIFICATION"); action.setExecutorCode("managerNotification");
        action.setApprovalRequired("N");
        when(store.listActions(10L)).thenReturn(List.of(action));
        when(data.canAccess(any(), any())).thenReturn(true);
        when(data.checkFreshness(any(), eq("hash-old"), any()))
            .thenReturn(new DecisionFreshnessResult(true, "hash-old", List.of()));
    }

    @Test void staleBusinessSnapshotBlocksAllApprovalAndExecution() {
        var run = waitingRun("hash-old");
        run.setBizType("SCHEDULE_PLAN"); run.setBizId(9001L); run.setVersion(0);
        when(store.requireRun(10L, "000000")).thenReturn(run);
        when(data.canAccess(any(), any())).thenReturn(true);
        when(data.checkFreshness(any(), eq("hash-old"), any()))
            .thenReturn(new DecisionFreshnessResult(false, "hash-new", List.of("stock")));
        assertThrows(ServiceException.class, () -> service.approve(10L, approvalRequest(), actor()));
        verify(store).transition(10L, DecisionRunStatus.WAITING_APPROVAL, DecisionRunStatus.STALE);
        verifyNoInteractions(flows);
    }
    @Test void unsupportedOrTamperedActionCannotBindApproverOrDispatchExecution() {
        readyForApproval();
        var action = store.listActions(10L).get(0);
        for (String type : Arrays.asList("PUBLISH_SCHEDULE_PLAN", "AUTO_APPROVE", null)) {
            action.setActionType(type);
            assertThrows(ServiceException.class, () -> service.approve(10L, approvalRequest(), actor()));
        }
        action.setActionType("SEND_MANAGER_NOTIFICATION"); action.setExecutorCode("untrusted");
        assertThrows(ServiceException.class, () -> service.approve(10L, approvalRequest(), actor()));
        action.setActionType("CREATE_PURCHASE_REQUISITION_DRAFT");
        action.setExecutorCode("purchaseRequisitionDraft"); action.setPermissionCode("pu:requisition:add");
        action.setApprovalRequired("N");
        assertThrows(ServiceException.class, () -> service.approve(10L, approvalRequest(), actor()));
        verify(store, never()).bindExecutionActor(anyLong(), any());
        verify(store, never()).setActionApproval(anyLong(), anyString(), anyString(), any(), anyString());
        verifyNoInteractions(flows);
    }

    @Test void historicalPurchaseCannotApproveOrMarkStaleBeforeContractGate() {
        readyForApproval();
        var run = store.requireRun(10L, "000000");
        var action = historicalPurchaseAction();
        when(store.listActions(10L)).thenReturn(List.of(action));
        when(data.checkFreshness(any(), anyString(), any()))
            .thenReturn(new DecisionFreshnessResult(false, "changed", List.of("stock")));

        var request = approvalRequest();
        request.setComment("{\"purchase_payload_version\":2}"); /** 客户端文本不能修改冻结动作。 */
        var error = assertThrows(ServiceException.class, () -> service.approve(10L, request, actor()));
        assertTrue(error.getMessage().contains("采购动作"));
        assertEquals("WAITING_APPROVAL", run.getStatus());
        assertEquals("PROPOSED", action.getApprovalStatus());
        assertEquals("NOT_STARTED", action.getExecutionStatus());
        assertEquals(0, run.getVersion());
        verify(data, never()).checkFreshness(any(), anyString(), any());
        verify(store, never()).transition(anyLong(), any(), any());
        verify(store, never()).bindExecutionActor(anyLong(), any());
        verify(store, never()).setActionApproval(anyLong(), anyString(), anyString(), any(), anyString());
        verifyNoInteractions(flows);
    }

    @Test void claimedV2InFrozenPurchasePayloadDoesNotEnableApprovalBeforeP1() {
        readyForApproval();
        var action = historicalPurchaseAction();
        when(store.listActions(10L)).thenReturn(List.of(action));
        for (String payload : List.of(
            "{\"runId\":\"10\",\"purchase_payload_version\":\"2\",\"materialLines\":[{\"materialId\":\"139\"}]}",
            "{\"runId\":\"10\",\"purchase_payload_version\":2.0,\"materialLines\":[{\"materialId\":\"139\"}]}",
            "{\"runId\":\"10\",\"purchase_payload_version\":2}",
            "{\"runId\":\"10\",\"purchase_payload_version\":2,\"materialLines\":[{\"materialId\":\"139\",\"base_uom_id\":\"1\",\"requirement_detail_ids\":[\"500\"]}]}")) {
            action.setPayloadJson(payload);
            var error = assertThrows(ServiceException.class, () -> service.approve(10L, approvalRequest(), actor()));
            assertTrue(error.getMessage().contains("采购动作"));
        }
        verify(data, never()).checkFreshness(any(), anyString(), any());
        verify(store, never()).transition(anyLong(), any(), any());
        verify(store, never()).setActionApproval(anyLong(), anyString(), anyString(), any(), anyString());
        verifyNoInteractions(flows);
    }

    @Test void mismatchedPurchaseRunAndTenantAreRejectedBeforeFreshness() {
        readyForApproval();
        var action = historicalPurchaseAction();
        when(store.listActions(10L)).thenReturn(List.of(action));
        action.setRunId(11L);
        assertThrows(ServiceException.class, () -> service.approve(10L, approvalRequest(), actor()));
        action.setRunId(10L);
        action.setTenantId("another-tenant");
        assertTrue(assertThrows(ServiceException.class, () -> service.approve(10L, approvalRequest(), actor()))
            .getMessage().contains("归属"));
        verify(data, never()).checkFreshness(any(), anyString(), any());
        verify(store, never()).transition(anyLong(), any(), any());
        verifyNoInteractions(flows);
    }

    @Test void purchasePayloadCannotClaimAnotherRunEvenWhenItClaimsV2() {
        readyForApproval();
        var action = historicalPurchaseAction();
        action.setPayloadJson("{\"runId\":\"11\",\"purchase_payload_version\":2,\"materialLines\":[{\"materialId\":\"139\",\"base_uom_id\":\"1\",\"requirement_detail_ids\":[\"500\"]}]}");
        when(store.listActions(10L)).thenReturn(List.of(action));

        assertTrue(assertThrows(ServiceException.class, () -> service.approve(10L, approvalRequest(), actor()))
            .getMessage().contains("归属"));
        verify(data, never()).checkFreshness(any(), anyString(), any());
        verify(store, never()).transition(anyLong(), any(), any());
        verifyNoInteractions(flows);
    }
    @Test void explicitlyRejectedPurchaseDoesNotBlockApprovedNonPurchaseAction() {
        readyForApproval();
        var purchase = historicalPurchaseAction();
        purchase.setId(21L);
        var notification = store.listActions(10L).get(0);
        when(store.listActions(10L)).thenReturn(List.of(notification, purchase));
        var request = approvalRequest();
        request.setRejectedActionIds(List.of(21L));
        when(flows.startExecution(any())).thenReturn(77L);

        assertDoesNotThrow(() -> service.approve(10L, request, actor()));
        verify(data).checkFreshness(any(), eq("hash-old"), any());
        verify(store).setActionApproval(20L, "PROPOSED", "APPROVED", actor(), request.getComment());
        verify(store).setActionApproval(21L, "PROPOSED", "REJECTED", actor(), request.getComment());
        verify(flows).startExecution(any());
    }

    @Test void manualRejectionOfHistoricalPurchaseIsStillAvailable() {
        readyForApproval();
        var action = historicalPurchaseAction();
        when(store.listActions(10L)).thenReturn(List.of(action));

        var reviewer = new DecisionActor("000000", 1L, "reviewer", Set.of("ai:decision:approve"));
        assertDoesNotThrow(() -> service.reject(10L, "旧采购来源未证实", reviewer));
        verify(store).setActionApproval(20L, "PROPOSED", "REJECTED", reviewer, "旧采购来源未证实");
        verify(store).transition(10L, DecisionRunStatus.WAITING_APPROVAL, DecisionRunStatus.REJECTED);
        verify(data, never()).checkFreshness(any(), anyString(), any());
        verifyNoInteractions(flows);
    }

    private AiDecisionAction historicalPurchaseAction() {
        var action = new AiDecisionAction();
        action.setId(20L); action.setRunId(10L); action.setTenantId("000000");
        action.setApprovalStatus("PROPOSED"); action.setExecutionStatus("NOT_STARTED");
        action.setActionType("CREATE_PURCHASE_REQUISITION_DRAFT");
        action.setPermissionCode("pu:requisition:add"); action.setExecutorCode("purchaseRequisitionDraft");
        action.setApprovalRequired("Y");
        action.setPayloadJson("{\"runId\":\"10\",\"requiredDate\":\"2026-10-01\",\"materialLines\":[{\"materialId\":\"139\",\"gap\":\"14.659\",\"requiredDate\":\"2026-10-01\"}]}");
        return action;
    }
    @Test void riskPolicyMayRequireExtraApprovalForAnOtherwiseOptionalNotification() {
        readyForApproval();
        store.listActions(10L).get(0).setApprovalRequired("Y");
        when(flows.startExecution(any())).thenReturn(77L);
        assertDoesNotThrow(() -> service.approve(10L, approvalRequest(), actor()));
        verify(flows).startExecution(any());
    }

    @Test void unavailableGenericFreshnessDoesNotWriteStaleOrBindActor() {
        readyForApproval();
        when(data.checkFreshness(any(), anyString(), any())).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.approve(10L, approvalRequest(), actor()),
            "读取不到快照不代表已经证明来源发生变化");
        verify(store, never()).transition(anyLong(), any(), any());
        verify(store, never()).bindExecutionActor(anyLong(), any());
        verifyNoInteractions(flows);
    }

    @Test void approvalRechecksPurchaseSourcesBeforeMasterAtomicBlock() {
        var reviewer = readyForPurchaseApproval();
        var request = approvalRequest(); request.setComment("{\"purchase_payload_version\":2,\"source\":\"client\"}");
        reviewer.approve(10L, request, actor());
        var order = inOrder(data, purchaseTx, store, flows);
        order.verify(data).checkFreshness(any(), eq("hash-old"), eq(actor()));
        order.verify(data).requireFreshPurchaseContract(argThat(a -> a.actionId().equals(20L)
            && a.target().id().equals(9001L) && a.payload().get("materialId").equals("139")
            && a.idempotencyKey().equals(DecisionPurchaseContractGuardTest.v2Action().getIdempotencyKey())),
            eq(purchaseSnapshot), eq(30), eq(actor()));
        order.verify(purchaseTx).executeWithoutResult(any());
        order.verify(store).bindExecutionActor(10L, actor());
        order.verify(store).setActionApproval(20L, "PROPOSED", "APPROVED", actor(), request.getComment());
        order.verify(store).transition(10L, DecisionRunStatus.WAITING_APPROVAL, DecisionRunStatus.APPROVED);
        order.verify(flows).startExecution(any());
        assertEquals(DecisionPurchaseContractGuardTest.v2Action().getPayloadJson(), store.listActions(10L).get(0).getPayloadJson(),
            "客户端注释仅为审批说明，不得成为合同事实或改写冻结载荷");
    }
    @Test void expiredTermsOrChangedAllocationCannotApprove() {
        var reviewer = readyForPurchaseApproval();
        doThrow(new DecisionPurchaseContractCodec.SourceChangedException())
                .when(data)
                .requireFreshPurchaseContract(any(), any(), anyInt(), any());
        var changed = assertThrows(ServiceException.class, () -> reviewer.approve(10L, approvalRequest(), actor()),
            "只有明确的来源变化异常才把采购审批标记失效");
        assertEquals("采购冻结来源已变化，须重新分析", changed.getMessage(), "公共差异信号须转换成固定安全的业务异常");
        verify(store).transition(10L, DecisionRunStatus.WAITING_APPROVAL, DecisionRunStatus.STALE);
        verifyNoInteractions(purchaseTx, flows);
        verify(store, never()).bindExecutionActor(anyLong(), any());
        verify(store, never()).setActionApproval(anyLong(), anyString(), anyString(), any(), any());
    }
    @Test void unknownOrUnauthorizedPurchaseReadKeepsApprovalState() {
        var reviewer = readyForPurchaseApproval();
        for (RuntimeException failure : List.of(new ServiceException("来源读取未知"),
            new ServiceException("当前权限不可证明"), new UnsupportedOperationException("端口未实现"))) {
            doThrow(failure).when(data).requireFreshPurchaseContract(any(), any(), anyInt(), any());
            assertThrows(RuntimeException.class, () -> reviewer.approve(10L, approvalRequest(), actor()),
                "未知读取和鉴权异常不是来源已经变化的证据");
        }
        verify(store, never()).transition(anyLong(), any(), any());
        verify(store, never()).bindExecutionActor(anyLong(), any());
        verifyNoInteractions(purchaseTx, flows);
    }
    @Test void livePurchasePermissionCannotBeSuppliedByRequestComment() {
        var reviewer = readyForPurchaseApproval();
        when(purchaseRights.getMenuPermission(1L))
                .thenReturn(Set.of("ai:decision:approve", "ai:decision:execute"));
        var request = approvalRequest(); request.setComment("pu:requisition:add 已核验 version=2");
        assertThrows(ServiceException.class, () -> reviewer.approve(10L, request, actor()));
        verify(data, never()).requireFreshPurchaseContract(any(), any(), anyInt(), any());
        verifyNoInteractions(purchaseTx, flows);
        verify(store, never()).bindExecutionActor(anyLong(), any());
    }

    private TransactionTemplate purchaseTx;
    private PermissionService purchaseRights;
    private DeliveryContextSnapshot purchaseSnapshot;
    private DecisionApprovalService readyForPurchaseApproval() {
        var run = DecisionPurchaseContractGuardTest.v2Run(); run.setStatus("WAITING_APPROVAL");
        run.setInputSnapshotHash("hash-old");
        var action = DecisionPurchaseContractGuardTest.v2Action();
        action.setApprovalStatus("PROPOSED"); action.setExecutionStatus("NOT_STARTED");
        when(store.requireRun(10L, "000000")).thenReturn(run);
        when(store.listActions(10L)).thenReturn(List.of(action));
        purchaseSnapshot =
                new DeliveryContextSnapshot(
                        new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 9001L, "WO-9001"),
                        List.of(),
                        List.of(),
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        Map.of(),
                        List.of(),
                        Map.of(),
                        List.of(),
                        "hash-old",
                        LocalDateTime.of(2026, 10, 6, 10, 0),
                        List.of());
        when(store.requireSnapshot(10L, "000000")).thenReturn(purchaseSnapshot);
        when(data.canAccess(any(), any())).thenReturn(true);
        when(data.checkFreshness(any(), anyString(), any())).thenReturn(new DecisionFreshnessResult(true, "hash-old", List.of()));
        when(flows.startExecution(any())).thenReturn(77L);
        purchaseRights = mock(PermissionService.class);
        when(purchaseRights.getMenuPermission(1L)).thenReturn(actor().permissions());
        purchaseTx = mock(TransactionTemplate.class);
        doAnswer(
                        call -> {
                            ((Consumer<TransactionStatus>) call.getArgument(0))
                                    .accept(mock(TransactionStatus.class));
                            return null;
                        })
                .when(purchaseTx)
                .executeWithoutResult(any());
        return new DecisionApprovalService(store, data, flows, null, purchaseTx, purchaseRights);
    }

}
