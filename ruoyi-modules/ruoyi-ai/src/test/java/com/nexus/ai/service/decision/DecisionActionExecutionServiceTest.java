package com.nexus.ai.service.decision;

import com.nexus.common.ai.decision.enums.DecisionExecutionStatus;
import com.nexus.common.ai.domain.entity.AiDecisionAction;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.ai.decision.enums.DecisionRunStatus;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.nexus.ai.service.decision.DecisionTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DecisionActionExecutionServiceTest {
    private final DecisionPersistenceService store = mock(DecisionPersistenceService.class);
    private final IndustrialDecisionDataPort port = mock(IndustrialDecisionDataPort.class);

    @Test void transientResultTwiceThenSuccessYieldsSingleVerifiedBusinessId() {
        var executor = mock(com.nexus.ai.service.decision.executor.DecisionActionExecutor.class);
        when(executor.type()).thenReturn(com.nexus.common.ai.decision.enums.DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT);
        var service = new DecisionActionExecutionService(store, List.of(executor),
            new com.fasterxml.jackson.databind.ObjectMapper());
        AiDecisionRun run = run(10L, DecisionRunStatus.APPROVED);
        when(store.requireRun(10L, "000000")).thenReturn(run);
        AiDecisionAction action = new AiDecisionAction();
        action.setId(20L); action.setRunId(10L); action.setActionType("CREATE_SCHEDULE_PLAN_DRAFT");
        action.setTargetBizType("SCHEDULE_PLAN"); action.setTargetBizId(9001L);
        action.setRiskLevel("HIGH"); action.setApprovalRequired("Y"); action.setApprovalStatus("APPROVED");
        action.setExecutionStatus("NOT_STARTED"); action.setPermissionCode("prd:schedule:run");
        action.setExecutorCode("schedulePlanDraft"); action.setSequenceNo(1);
        action.setPayloadJson("{\"runId\":\"10\"}"); action.setDependencyIdsJson("[]");
        when(store.listActions(10L)).thenReturn(List.of(action));
        var failure = new com.nexus.common.ai.decision.dto.DecisionActionExecutionResult(false, true,
            null, null, null, java.util.Map.of(), "TEMP", "请重试");
        var success = new com.nexus.common.ai.decision.dto.DecisionActionExecutionResult(true, false,
            "PRD_SCHEDULE_PLAN", 99L, "PLAN-99", java.util.Map.of("status", "COMPLETED"), null, null);
        when(executor.execute(any(), any())).thenReturn(failure, failure, success);
        when(executor.verify(any(), eq(success), any())).thenReturn(success);
        service.executeApproved(10L, actor());
        verify(executor, times(3)).execute(any(), any());
        verify(store).saveActionResult(20L, success, 3);
        verify(store).transition(10L, DecisionRunStatus.EXECUTING, DecisionRunStatus.SUCCEEDED);
    }

    @Test void revokedLivePermissionBlocksManualActionRetryBeforeMapperAccess() {
        var rights = mock(com.nexus.common.core.service.PermissionService.class);
        when(rights.getMenuPermission(1L)).thenReturn(java.util.Set.of("ai:decision:query"));
        var service = new DecisionActionExecutionService(store, List.of(),
            new com.fasterxml.jackson.databind.ObjectMapper(), null, rights);
        assertThrows(com.nexus.common.core.exception.ServiceException.class, () -> service.retry(20L, actor()));
        verifyNoInteractions(store);
    }

    @Test void verificationFailureCompensatesDraftBeforeRolledBackTerminalState() {
        var executor = mock(com.nexus.ai.service.decision.executor.DecisionActionExecutor.class);
        when(executor.type()).thenReturn(com.nexus.common.ai.decision.enums.DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT);
        var service = new DecisionActionExecutionService(store, List.of(executor), new com.fasterxml.jackson.databind.ObjectMapper());
        when(store.requireRun(10L, "000000")).thenReturn(run(10L, DecisionRunStatus.APPROVED));
        AiDecisionAction row = new AiDecisionAction();
        row.setId(20L); row.setRunId(10L); row.setActionType("CREATE_SCHEDULE_PLAN_DRAFT");
        row.setTargetBizType("SCHEDULE_PLAN"); row.setTargetBizId(9001L);
        row.setRiskLevel("HIGH"); row.setApprovalRequired("Y");
        row.setApprovalStatus("APPROVED"); row.setExecutionStatus("NOT_STARTED");
        row.setPermissionCode("prd:schedule:run"); row.setExecutorCode("schedulePlanDraft");
        row.setSequenceNo(1); row.setPayloadJson("{\"runId\":\"10\"}"); row.setDependencyIdsJson("[]");
        when(store.listActions(10L)).thenReturn(List.of(row));
        var created = new com.nexus.common.ai.decision.dto.DecisionActionExecutionResult(true, false,
            "PRD_SCHEDULE_PLAN", 99L, "PLAN-99", java.util.Map.of("status", "COMPLETED"), null, null);
        var unverified = new com.nexus.common.ai.decision.dto.DecisionActionExecutionResult(false, false,
            "PRD_SCHEDULE_PLAN", 99L, "PLAN-99", java.util.Map.of(), "VERIFY_FAILED", "来源变化");
        var compensated = new com.nexus.common.ai.decision.dto.DecisionActionExecutionResult(true, false,
            "PRD_SCHEDULE_PLAN", 99L, "PLAN-99", java.util.Map.of("compensated", true), null, null);
        when(executor.execute(any(), any())).thenReturn(created);
        when(executor.verify(any(), eq(created), any())).thenReturn(unverified);
        when(executor.compensate(any(), eq(created), any())).thenReturn(compensated);
        service.executeApproved(10L, actor());
        var ordered = inOrder(store, executor);
        ordered.verify(store).saveActionResult(20L, unverified, 1);
        ordered.verify(store).transition(10L, DecisionRunStatus.EXECUTING, DecisionRunStatus.ROLLING_BACK);
        ordered.verify(executor).compensate(any(), eq(created), any());
        ordered.verify(store).saveCompensationResult(20L, compensated);
        ordered.verify(store).transition(10L, DecisionRunStatus.ROLLING_BACK, DecisionRunStatus.ROLLED_BACK);
    }

    @Test void failedTerminalRunCannotBeMutatedByManualActionRetry() {
        var executor = mock(com.nexus.ai.service.decision.executor.DecisionActionExecutor.class);
        when(executor.type()).thenReturn(com.nexus.common.ai.decision.enums.DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT);
        var service = new DecisionActionExecutionService(store, List.of(executor), new com.fasterxml.jackson.databind.ObjectMapper());
        AiDecisionAction action = new AiDecisionAction();
        action.setId(20L); action.setRunId(10L); action.setApprovalStatus("APPROVED");
        action.setExecutionStatus("FAILED"); action.setActionType("CREATE_SCHEDULE_PLAN_DRAFT");
        action.setExecutorCode("schedulePlanDraft"); action.setPermissionCode("prd:schedule:run");
        action.setTargetBizType("SCHEDULE_PLAN"); action.setTargetBizId(9001L);
        action.setPayloadJson("{\"runId\":\"10\"}"); action.setRiskLevel("HIGH");
        action.setDependencyIdsJson("[]"); action.setSequenceNo(1);
        when(store.requireAction(20L, "000000")).thenReturn(action);
        when(store.requireRun(10L, "000000")).thenReturn(run(10L, DecisionRunStatus.FAILED));
        assertThrows(com.nexus.common.core.exception.ServiceException.class, () -> service.retry(20L, actor()));
        verify(store, never()).transitionAction(anyLong(), anyString(), anyString(), anyString());
        verify(executor, never()).execute(any(), any());
    }

    @Test void dependencyCycleDoesNotClaimRunIsExecuting() {
        var service = new DecisionActionExecutionService(store, List.of(), new com.fasterxml.jackson.databind.ObjectMapper());
        when(store.requireRun(10L, "000000")).thenReturn(run(10L, DecisionRunStatus.APPROVED));
        AiDecisionAction action = new AiDecisionAction();
        action.setId(20L); action.setRunId(10L); action.setApprovalStatus("APPROVED");
        action.setDependencyIdsJson("[20]"); action.setSequenceNo(1);
        when(store.listActions(10L)).thenReturn(List.of(action));
        assertThrows(com.nexus.common.core.exception.ServiceException.class, () -> service.executeApproved(10L, actor()));
        verify(store, never()).transition(10L, DecisionRunStatus.APPROVED, DecisionRunStatus.EXECUTING);
    }

    @Test void successfulActionEmitsStatusOnlyAfterResultIsPersisted() {
        var events = mock(com.nexus.ai.service.sse.AiDecisionSseMessageHelper.class);
        var executor = mock(com.nexus.ai.service.decision.executor.DecisionActionExecutor.class);
        when(executor.type()).thenReturn(com.nexus.common.ai.decision.enums.DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT);
        var service = new DecisionActionExecutionService(store, List.of(executor),
            new com.fasterxml.jackson.databind.ObjectMapper(), events);
        when(store.requireRun(10L, "000000")).thenReturn(run(10L, DecisionRunStatus.APPROVED));
        AiDecisionAction action = new AiDecisionAction();
        action.setId(20L); action.setRunId(10L); action.setActionType("CREATE_SCHEDULE_PLAN_DRAFT");
        action.setTargetBizType("SCHEDULE_PLAN"); action.setTargetBizId(9001L);
        action.setRiskLevel("HIGH"); action.setApprovalRequired("Y");
        action.setApprovalStatus("APPROVED"); action.setExecutionStatus("NOT_STARTED");
        action.setPermissionCode("prd:schedule:run"); action.setExecutorCode("schedulePlanDraft");
        action.setSequenceNo(1); action.setPayloadJson("{\"runId\":\"10\"}");
        action.setDependencyIdsJson("[]");
        when(store.listActions(10L)).thenReturn(List.of(action));
        var success = new com.nexus.common.ai.decision.dto.DecisionActionExecutionResult(true, false,
            "PRD_SCHEDULE_PLAN", 99L, "PLAN-99", java.util.Map.of("status", "COMPLETED"), null, null);
        when(executor.execute(any(), any())).thenReturn(success);
        when(executor.verify(any(), eq(success), any())).thenReturn(success);
        service.executeApproved(10L, actor());
        var ordered = inOrder(store, events);
        ordered.verify(store).saveActionResult(20L, success, 1);
        ordered.verify(events).sendActionStatus(1L, 10L, 20L, "SUCCEEDED");
    }

    @Test void legacyPurchaseApprovedRunIsRejectedBeforeAnyStateChange() {
        var executor = mock(com.nexus.ai.service.decision.executor.DecisionActionExecutor.class);
        when(executor.type()).thenReturn(com.nexus.common.ai.decision.enums.DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT);
        var service = new DecisionActionExecutionService(store, List.of(executor),
            new com.fasterxml.jackson.databind.ObjectMapper());
        when(store.requireRun(10L, "000000")).thenReturn(run(10L, DecisionRunStatus.APPROVED));
        when(store.listActions(10L)).thenReturn(List.of(legacyPurchase("NOT_STARTED")));
        var failure = assertThrows(com.nexus.common.core.exception.ServiceException.class,
            () -> service.executeApproved(10L, actor()));
        assertTrue(failure.getMessage().contains("采购"));
        verify(store, never()).transition(10L, DecisionRunStatus.APPROVED, DecisionRunStatus.EXECUTING);
        verify(store, never()).transitionAction(anyLong(), anyString(), anyString(), anyString());
        verify(executor, never()).execute(any(), any());
    }

    @Test void legacyPurchaseFailedActionCannotRetryOrIncrementCounter() {
        var executor = mock(com.nexus.ai.service.decision.executor.DecisionActionExecutor.class);
        when(executor.type()).thenReturn(com.nexus.common.ai.decision.enums.DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT);
        var service = new DecisionActionExecutionService(store, List.of(executor),
            new com.fasterxml.jackson.databind.ObjectMapper());
        when(store.requireAction(20L, "000000")).thenReturn(legacyPurchase("FAILED"));
        when(store.requireRun(10L, "000000")).thenReturn(run(10L, DecisionRunStatus.EXECUTING));
        var failure = assertThrows(com.nexus.common.core.exception.ServiceException.class,
            () -> service.retry(20L, actor()));
        assertTrue(failure.getMessage().contains("采购"));
        verify(store, never()).transitionAction(anyLong(), anyString(), anyString(), anyString());
        verify(store, never()).saveActionResult(anyLong(), any(), anyInt());
        verify(executor, never()).execute(any(), any());
    }

    private AiDecisionAction legacyPurchase(String executionStatus) {
        AiDecisionAction action = new AiDecisionAction();
        action.setId(20L); action.setRunId(10L); action.setTenantId("000000");
        action.setActionType("CREATE_PURCHASE_REQUISITION_DRAFT");
        action.setApprovalRequired("Y"); action.setApprovalStatus("APPROVED");
        action.setExecutionStatus(executionStatus);
        action.setPermissionCode("pu:requisition:add");
        action.setExecutorCode("purchaseRequisitionDraft");
        action.setTargetBizType("SCHEDULE_PLAN"); action.setTargetBizId(9001L);
        action.setRiskLevel("MEDIUM"); action.setSequenceNo(1);
        action.setPayloadJson("{\"runId\":\"10\",\"requiredDate\":\"2026-10-09\",\"materialLines\":[{\"materialId\":\"140\",\"gap\":\"5.4292\",\"requiredDate\":\"2026-10-09\"}]}");
        action.setDependencyIdsJson("[]");
        return action;
    }
    @Test void alreadySucceededActionIsNeverExecutedTwice() {
        var service = new DecisionActionExecutionService(store, List.of(), new com.fasterxml.jackson.databind.ObjectMapper());
        AiDecisionAction action = succeededAction(20L);
        when(store.requireAction(20L, "000000")).thenReturn(action);
        service.retry(20L, actor());
        verifyNoInteractions(port);
    }
}
