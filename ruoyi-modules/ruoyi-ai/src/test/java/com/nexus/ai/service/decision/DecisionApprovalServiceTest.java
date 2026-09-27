package com.nexus.ai.service.decision;

import com.nexus.common.ai.decision.dto.DecisionFreshnessResult;
import com.nexus.common.ai.decision.port.DecisionWorkflowPort;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.ai.decision.enums.DecisionRunStatus;
import com.nexus.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.nexus.ai.service.decision.DecisionTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.InOrder;

class DecisionApprovalServiceTest {
    private final DecisionPersistenceService store = mock(DecisionPersistenceService.class);
    private final IndustrialDecisionDataPort data = mock(IndustrialDecisionDataPort.class);
    private final DecisionWorkflowPort flows = mock(DecisionWorkflowPort.class);
    private final DecisionApprovalService service = new DecisionApprovalService(store, data, flows);

    @Test void revokedReviewPermissionCannotApproveDespiteOldSessionSnapshot() {
        var rights = mock(com.nexus.common.core.service.PermissionService.class);
        var events = mock(com.nexus.ai.service.sse.AiDecisionSseMessageHelper.class);
        var reviewer = new DecisionApprovalService(store, data, flows, events,
            mock(org.springframework.transaction.support.TransactionTemplate.class), rights);
        when(rights.getMenuPermission(1L)).thenReturn(java.util.Set.of("ai:decision:run"));
        assertThrows(ServiceException.class, () -> reviewer.approve(10L, approvalRequest(), actor()));
        verifyNoInteractions(store, data, flows);
    }

    @Test void approvalBindsReviewerBeforeDispatchAndEmitsStatus() {
        var events = mock(com.nexus.ai.service.sse.AiDecisionSseMessageHelper.class);
        var tx = mock(org.springframework.transaction.support.TransactionTemplate.class);
        doAnswer(call -> { ((java.util.function.Consumer<org.springframework.transaction.TransactionStatus>) call.getArgument(0))
            .accept(mock(org.springframework.transaction.TransactionStatus.class)); return null; })
            .when(tx).executeWithoutResult(any());
        var withEvents = new DecisionApprovalService(store, data, flows, events, tx);
        var run = waitingRun("hash-old");
        run.setBizType("SCHEDULE_PLAN"); run.setBizId(9001L); run.setVersion(0);
        when(store.requireRun(10L, "000000")).thenReturn(run);
        var action = new com.nexus.common.ai.domain.entity.AiDecisionAction();
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
        var reviewer = new com.nexus.common.ai.decision.dto.DecisionActor("000000", 1L, "reviewer",
            java.util.Set.of("ai:decision:run", "ai:decision:approve"));
        assertThrows(ServiceException.class, () -> service.approve(10L, approvalRequest(), reviewer));
        verifyNoInteractions(store, data, flows);
    }

    @Test void failedExecutionFlowAttachmentStopsOrphanBeforeInvalidatingApprovedRun() {
        var run = waitingRun("hash-old");
        run.setBizType("SCHEDULE_PLAN"); run.setBizId(9001L); run.setVersion(0);
        var approved = waitingRun("hash-old");
        approved.setStatus("APPROVED");
        when(store.requireRun(10L, "000000")).thenReturn(run, approved);
        var action = new com.nexus.common.ai.domain.entity.AiDecisionAction();
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
        var events = mock(com.nexus.ai.service.sse.AiDecisionSseMessageHelper.class);
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
        var action = new com.nexus.common.ai.domain.entity.AiDecisionAction();
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
        for (String type : java.util.Arrays.asList("PUBLISH_SCHEDULE_PLAN", "AUTO_APPROVE", null)) {
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

    @Test void riskPolicyMayRequireExtraApprovalForAnOtherwiseOptionalNotification() {
        readyForApproval();
        store.listActions(10L).get(0).setApprovalRequired("Y");
        when(flows.startExecution(any())).thenReturn(77L);
        assertDoesNotThrow(() -> service.approve(10L, approvalRequest(), actor()));
        verify(flows).startExecution(any());
    }

}
