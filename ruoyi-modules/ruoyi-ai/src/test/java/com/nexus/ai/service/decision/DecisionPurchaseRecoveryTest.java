package com.nexus.ai.service.decision;

import static com.nexus.ai.service.decision.DecisionTestFixtures.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.service.decision.executor.DecisionActionExecutor;
import com.nexus.ai.service.sse.AiDecisionSseMessageHelper;
import com.nexus.common.ai.decision.dto.DecisionActionExecutionResult;
import com.nexus.common.ai.decision.dto.DecisionActionProposal;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DecisionFreshnessResult;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.enums.DecisionRiskLevel;
import com.nexus.common.ai.decision.enums.DecisionRunStatus;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;
import com.nexus.common.ai.domain.entity.AiDecisionAction;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.service.PermissionService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 独立验证执行中断的调度边界，不伪装为跨物理库事务验证。 */
class DecisionPurchaseRecoveryTest {
    @Test void productionConstructorExplicitlyRequiresPurchasePortAndFinalizer() {
        assertDoesNotThrow(
                () -> {
                    var constructor =
                            DecisionActionExecutionService.class.getConstructor(
                                    DecisionPersistenceService.class,
                                    List.class,
                                    ObjectMapper.class,
                                    AiDecisionSseMessageHelper.class,
                                    PermissionService.class,
                                    IndustrialDecisionDataPort.class,
                                    DecisionFinalizeService.class);
                    assertNotNull(
                            constructor.getAnnotation(Autowired.class),
                            "生产构造器必须明确注入采购恢复端口、实时权限和复盘服务");
                    assertEquals(
                            1L,
                            Arrays.stream(DecisionActionExecutionService.class.getConstructors())
                                    .filter(c -> c.isAnnotationPresent(Autowired.class))
                                    .count(),
                            "只能有一个 Spring 自动注入构造器");
                },
                "旧隔离重载不能成为新版采购的默认授权依赖");
    }
    @Test void resumedRunCountsVerifiedSucceededActionsWithoutExecutingAgain() throws Exception {
        var store = mock(DecisionPersistenceService.class);
        var executor = mock(DecisionActionExecutor.class);
        when(executor.type()).thenReturn(DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT);
        var mapper = new ObjectMapper();
        var rights = mock(PermissionService.class);
        when(rights.getMenuPermission(1L)).thenReturn(actor().permissions());
        var finalizer = mock(DecisionFinalizeService.class);
        var service =
                new DecisionActionExecutionService(
                        store,
                        List.of(executor),
                        mapper,
                        null,
                        rights,
                        mock(IndustrialDecisionDataPort.class),
                        finalizer);
        when(store.finishRecoveredExecution(eq(10L), anyInt())).thenReturn(true);
        var versionedRun = run(10L, DecisionRunStatus.EXECUTING); versionedRun.setDecisionContractVersion(2);
        when(store.requireRun(10L, "000000")).thenReturn(versionedRun);
        var a = scheduleAction("SUCCEEDED");
        var result = new DecisionActionExecutionResult(true, false, "PRD_SCHEDULE_PLAN", 99L, "PLAN-99",
            Map.of("status", "COMPLETED"), null, null);
        a.setResultJson(mapper.writeValueAsString(result));
        when(store.listActions(10L)).thenReturn(List.of(a));
        when(executor.verify(any(), any(), any())).thenReturn(result);
        assertDoesNotThrow(() -> service.executeApproved(10L, actor()), "执行中断续跑不应拒绝 EXECUTING 运行");
        verify(executor).verify(any(), any(), any());
        verify(executor, never()).execute(any(), any());
        verify(store, never()).saveActionResult(anyLong(), any(), anyInt());
    }
    @Test void unknownExecutingMissCannotCreateOrIncrementCounter() {
        var h = new RecoveryHarness("EXECUTING", "EXECUTING");
        var error = assertThrows(ServiceException.class, () -> h.service.retry(20L, actor()));
        assertTrue(error.getMessage().contains("尚不能确定"), "永久记录 MISS 不等于执行事务已经失败");
        verify(h.port).recoverPurchaseAction(any(), eq(actor()));
        h.assertNoPurchaseWrite();
        verify(h.port, never()).requireFreshPurchaseContract(any(), any(), anyInt(), any());
        assertEquals("EXECUTING", h.purchase.getExecutionStatus());
        assertEquals(2, h.purchase.getRetryCount(), "未知原态不能改动既有重试计数");
    }
    @Test void sameBoundActorRecoversCommittedDraftBeforeFreshnessAndFinalizes() {
        var h = new RecoveryHarness("EXECUTING", "EXECUTING");
        when(h.port.recoverPurchaseAction(any(), any())).thenReturn(h.receipt(true));
        h.service.retry(20L, actor());
        var order = inOrder(h.port, h.purchaseExecutor, h.store, h.finalizer);
        order.verify(h.port).recoverPurchaseAction(any(), eq(actor()));
        order.verify(h.purchaseExecutor).verify(any(), any(), eq(actor()));
        order.verify(h.store).saveRecoveredPurchaseResult(eq(20L), eq(0), any());
        order.verify(h.store).finishRecoveredExecution(eq(10L), anyInt());
        order.verify(h.finalizer).finalizeRun(10L, actor());
        verify(h.port, never()).requireFreshPurchaseContract(any(), any(), anyInt(), any());
        verify(h.purchaseExecutor, never()).execute(any(), any());
        verify(h.purchaseExecutor, never()).compensate(any(), any(), any());
        assertEquals("SUCCEEDED", h.run.getStatus());
    }
    @Test void differentAdministratorCannotReplaceBoundExecutionActor() {
        var h = new RecoveryHarness("EXECUTING", "EXECUTING");
        var other = new DecisionActor("000000", 2L, "其他管理员", Set.of("*:*:*"));
        when(h.rights.getMenuPermission(2L)).thenReturn(Set.of("*:*:*"));
        assertThrows(ServiceException.class, () -> h.service.retry(20L, other));
        verifyNoInteractions(h.port); h.assertNoPurchaseWrite();
    }
    @Test void revokedLivePurchasePermissionBlocksRecoveryWithoutStateChange() {
        var h = new RecoveryHarness("EXECUTING", "EXECUTING");
        when(h.rights.getMenuPermission(1L)).thenReturn(Set.of("ai:decision:execute"));
        assertThrows(DecisionActionExecutionService.InitialExecutionCheckUnavailableException.class, () -> h.service.retry(20L, actor()));
        verifyNoInteractions(h.port); h.assertNoPurchaseWrite();
    }
    @Test void failedDefiniteMissRechecksSourcesAndRetriesOriginalKeyOnly() {
        var h = new RecoveryHarness("EXECUTING", "FAILED");
        when(h.purchaseExecutor.execute(any(), any())).thenReturn(h.receipt(false));
        h.service.retry(20L, actor());
        var order = inOrder(h.port, h.purchaseExecutor, h.store);
        order.verify(h.port).recoverPurchaseAction(any(), any());
        order.verify(h.port).requireFreshPurchaseContract(any(), eq(h.snapshot), eq(30), any());
        order.verify(h.store).transitionAction(20L, "APPROVED", "FAILED", "EXECUTING");
        order.verify(h.purchaseExecutor).execute(argThat(a -> a.idempotencyKey().equals(h.purchase.getIdempotencyKey())), eq(actor()));
        order.verify(h.purchaseExecutor).verify(any(), any(), any());
        order.verify(h.store).saveActionResult(eq(20L), any(), eq(1));
        verify(h.purchaseExecutor, never()).compensate(any(), any(), any());
    }
    @Test void failedActionWithCommittedReceiptDoesNotRecheckChangedOwnAllocation() {
        var h = new RecoveryHarness("EXECUTING", "FAILED");
        when(h.port.recoverPurchaseAction(any(), any())).thenReturn(h.receipt(true));
        doThrow(new IllegalStateException("自身落账已改变可申请量")).when(h.port)
            .requireFreshPurchaseContract(any(), any(), anyInt(), any());
        h.service.retry(20L, actor());
        verify(h.store).saveRecoveredPurchaseResult(eq(20L), eq(0), any());
        verify(h.purchaseExecutor, never()).execute(any(), any());
    }
    @Test void lostErpResponseRecoversBeforeAnyTerminalFailure() {
        var h = new RecoveryHarness("APPROVED", "NOT_STARTED");
        when(h.port.recoverPurchaseAction(any(), any())).thenReturn(null, h.receipt(true));
        when(h.purchaseExecutor.execute(any(), any())).thenThrow(new IllegalStateException("提交后响应丢失"));
        h.service.executeApproved(10L, actor());
        verify(h.purchaseExecutor, times(1)).execute(any(), any());
        verify(h.port, times(2)).recoverPurchaseAction(any(), any());
        verify(h.store).saveRecoveredPurchaseResult(eq(20L), eq(1), any());
        verify(h.store, never()).saveActionResult(eq(20L), argThat(r -> !r.success()), anyInt());
        verify(h.purchaseExecutor, never()).compensate(any(), any(), any());
        assertEquals("SUCCEEDED", h.run.getStatus());
    }
    @Test void masterWriteFailureLeavesRecoverableExecutingState() {
        var h = new RecoveryHarness("APPROVED", "NOT_STARTED");
        when(h.purchaseExecutor.execute(any(), any())).thenReturn(h.receipt(false));
        doThrow(new IllegalStateException("ERP 已提交，AI 回写失败")).when(h.store).saveActionResult(eq(20L), any(), anyInt());
        assertThrows(RuntimeException.class, () -> h.service.executeApproved(10L, actor()));
        assertEquals("EXECUTING", h.purchase.getExecutionStatus()); assertEquals("EXECUTING", h.run.getStatus());
        verify(h.purchaseExecutor, never()).compensate(any(), any(), any());
        verify(h.store, never()).saveActionResult(eq(20L), argThat(r -> !r.success()), anyInt());
        doAnswer(call -> { h.save((DecisionActionExecutionResult) call.getArgument(1)); return null; })
            .when(h.store).saveActionResult(eq(20L), any(), anyInt());
        when(h.port.recoverPurchaseAction(any(), any())).thenReturn(h.receipt(true));
        h.service.retry(20L, actor());
        verify(h.purchaseExecutor, times(1)).execute(any(), any());
        assertEquals(99L, h.purchase.getResultBizId(), "后续补回同一真实引用，不分配新申请");
    }
    @Test void unknownExecuteFailureAndMissRemainInFlightWithoutRecreation() {
        var h = new RecoveryHarness("APPROVED", "NOT_STARTED");
        when(h.purchaseExecutor.execute(any(), any())).thenThrow(new IllegalStateException("未知执行异常"));
        assertThrows(DecisionActionExecutionService.PurchaseExecutionPendingException.class, () -> h.service.executeApproved(10L, actor()));
        assertEquals("EXECUTING", h.purchase.getExecutionStatus()); assertEquals("EXECUTING", h.run.getStatus());
        verify(h.store, never()).saveActionResult(anyLong(), any(), anyInt());
        verify(h.purchaseExecutor, times(1)).execute(any(), any());
        verify(h.purchaseExecutor, never()).compensate(any(), any(), any());
    }
    @Test void changedDraftOrUnknownReceiptDoesNotRecreateCompensateOrEraseReference() {
        var h = new RecoveryHarness("EXECUTING", "EXECUTING");
        h.purchase.setResultBizType("PU_PURCHASE_REQUISITION"); h.purchase.setResultBizId(99L);
        var changed = new DecisionActionExecutionResult(false, false, "PU_PURCHASE_REQUISITION", 99L, "REQ-99",
            Map.of("status", "CLOSED"), "VERIFY_STATE_MISMATCH", "原单已关闭");
        when(h.port.recoverPurchaseAction(any(), any())).thenReturn(changed);
        assertThrows(ServiceException.class, () -> h.service.retry(20L, actor()));
        h.assertNoPurchaseWrite(); assertEquals(99L, h.purchase.getResultBizId(), "历史引用不能因恢复冲突被清空");
        assertEquals("EXECUTING", h.purchase.getExecutionStatus());
    }
    @Test void recoveryConflictIsNotAConfirmedMiss() {
        var h = new RecoveryHarness("EXECUTING", "FAILED");
        when(h.port.recoverPurchaseAction(any(), any()))
                .thenThrow(new ServiceException("载荷或永久记录冲突"));
        assertThrows(ServiceException.class, () -> h.service.retry(20L, actor()));
        h.assertNoPurchaseWrite(); verify(h.port, never()).requireFreshPurchaseContract(any(), any(), anyInt(), any());
    }
    @Test void committedButUnverifiedDraftCannotBecomeFailedOrCompensated() {
        var h = new RecoveryHarness("APPROVED", "NOT_STARTED");
        when(h.purchaseExecutor.execute(any(), any())).thenReturn(h.receipt(false));
        when(h.purchaseExecutor.verify(any(), any(), any())).thenReturn(new DecisionActionExecutionResult(false, false,
            "PU_PURCHASE_REQUISITION", 99L, "REQ-99", Map.of(), "VERIFY_STATE_MISMATCH", "已转单或引用不匹配"));
        when(h.port.recoverPurchaseAction(any(), any())).thenReturn(null, h.receipt(true));
        assertThrows(DecisionActionExecutionService.PurchaseExecutionPendingException.class, () -> h.service.executeApproved(10L, actor()));
        assertEquals("EXECUTING", h.purchase.getExecutionStatus());
        verify(h.store, never()).saveActionResult(anyLong(), any(), anyInt());
        verify(h.store, never()).saveRecoveredPurchaseResult(anyLong(), anyInt(), any());
        verify(h.purchaseExecutor, never()).compensate(any(), any(), any());
    }
    @Test void succeededPurchaseCanFinishInterruptedRunWithoutOverwritingEnvelope() {
        var h = new RecoveryHarness("EXECUTING", "SUCCEEDED"); h.save(h.receipt(false));
        String original = h.purchase.getResultJson();
        when(h.purchaseExecutor.verify(any(), any(), any())).thenReturn(h.receipt(true));
        h.service.retry(20L, actor());
        verify(h.purchaseExecutor).verify(any(), any(), any());
        verify(h.store).finishRecoveredExecution(eq(10L), anyInt());
        verify(h.finalizer).finalizeRun(10L, actor());
        verify(h.store, never()).saveRecoveredPurchaseResult(anyLong(), anyInt(), any());
        verify(h.store, never()).saveActionResult(anyLong(), any(), anyInt());
        assertEquals(original, h.purchase.getResultJson(), "重放观察字段不同不能覆写成功的历史信封");
    }
    @Test void succeededReferenceMismatchCannotConvergeRun() {
        var h = new RecoveryHarness("EXECUTING", "SUCCEEDED"); h.save(h.receipt(false));
        h.purchase.setResultBizId(100L);
        assertThrows(DecisionActionExecutionService.PurchaseExecutionPendingException.class, () -> h.service.retry(20L, actor()));
        verify(h.store, never()).finishRecoveredExecution(anyLong(), anyInt());
        verifyNoInteractions(h.finalizer); verify(h.purchaseExecutor, never()).execute(any(), any());
    }
    @Test void concurrentSameStableRecoveredResultIsVerifiedWithoutOverwriting() {
        var h = new RecoveryHarness("EXECUTING", "EXECUTING");
        when(h.port.recoverPurchaseAction(any(), any())).thenReturn(h.receipt(true));
        doAnswer(
                        call -> {
                            h.save(h.receipt(false));
                            throw new ServiceException("动作 CAS 已被另一个执行者完成");
                        })
                .when(h.store)
                .saveRecoveredPurchaseResult(eq(20L), eq(0), any());
        h.service.retry(20L, actor());
        verify(h.purchaseExecutor, times(2)).verify(any(), any(), any());
        verify(h.store, times(1)).saveRecoveredPurchaseResult(eq(20L), eq(0), any());
        verify(h.store).finishRecoveredExecution(eq(10L), anyInt());
    }
    @Test void concurrentDifferentBusinessReferenceStopsWithoutOverwriting() {
        var h = new RecoveryHarness("EXECUTING", "EXECUTING");
        when(h.port.recoverPurchaseAction(any(), any())).thenReturn(h.receipt(true));
        doAnswer(
                        call -> {
                            h.save(
                                    new DecisionActionExecutionResult(
                                            true,
                                            false,
                                            "PU_PURCHASE_REQUISITION",
                                            100L,
                                            "REQ-100",
                                            h.receipt(false).result(),
                                            null,
                                            null));
                            throw new ServiceException("并发结果冲突");
                        })
                .when(h.store)
                .saveRecoveredPurchaseResult(eq(20L), eq(0), any());
        assertThrows(ServiceException.class, () -> h.service.retry(20L, actor()));
        verify(h.store, never()).finishRecoveredExecution(anyLong(), anyInt());
        verify(h.store, never()).saveActionResult(anyLong(), any(), anyInt());
    }
    @Test void resumedRunVerifiesSucceededThenRecoversBeforeContinuingDependencies() throws Exception {
        var h = new RecoveryHarness("EXECUTING", "EXECUTING");
        var done = scheduleAction("SUCCEEDED");
        var result = new DecisionActionExecutionResult(true, false, "PRD_SCHEDULE_PLAN", 101L, "PLAN-101", Map.of(), null, null);
        done.setResultJson(h.mapper.writeValueAsString(result));
        var later = scheduleAction("NOT_STARTED"); later.setId(40L); later.setDependencyIdsJson("[20,30]");
        h.actions.add(done); h.actions.add(later);
        var schedule = mock(DecisionActionExecutor.class); when(schedule.type()).thenReturn(DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT);
        when(schedule.verify(any(), any(), any())).thenAnswer(c -> c.getArgument(1)); when(schedule.execute(any(), any())).thenReturn(result);
        h.service = h.withExecutors(List.of(h.purchaseExecutor, schedule));
        when(h.port.recoverPurchaseAction(any(), any())).thenReturn(h.receipt(true));
        doAnswer(c -> { later.setExecutionStatus("SUCCEEDED"); later.setResultJson(h.mapper.writeValueAsString(c.getArgument(1))); return null; })
            .when(h.store).saveActionResult(eq(40L), any(), anyInt());
        h.service.executeApproved(10L, actor());
        var order = inOrder(schedule, h.port, h.store);
        order.verify(schedule).verify(argThat(a -> a.actionId().equals(30L)), any(), any());
        order.verify(h.port).recoverPurchaseAction(any(), any());
        order.verify(h.store).saveRecoveredPurchaseResult(eq(20L), eq(0), any());
        order.verify(schedule).execute(argThat(a -> a.actionId().equals(40L)), any());
        verify(h.store, never()).saveActionResult(eq(30L), any(), anyInt());
        assertEquals("SUCCEEDED", h.run.getStatus());
    }
    @Test void unresolvedInFlightActionDoesNotBecomeSucceededOrStartIndependentAction() {
        var h = new RecoveryHarness("EXECUTING", "EXECUTING"); h.actions.add(scheduleAction("NOT_STARTED"));
        var schedule = mock(DecisionActionExecutor.class); when(schedule.type()).thenReturn(DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT);
        h.service = h.withExecutors(List.of(h.purchaseExecutor, schedule));
        assertThrows(DecisionActionExecutionService.PurchaseExecutionPendingException.class, () -> h.service.executeApproved(10L, actor()));
        verify(schedule, never()).execute(any(), any()); verify(h.store, never()).finishRecoveredExecution(anyLong(), anyInt());
        assertEquals("EXECUTING", h.run.getStatus());
    }
    @Test void nonPurchaseInFlightCannotBeGuessedAsCompleted() {
        var h = new RecoveryHarness("EXECUTING", "SUCCEEDED"); h.save(h.receipt(false));
        h.actions.add(scheduleAction("EXECUTING"));
        var schedule = mock(DecisionActionExecutor.class); when(schedule.type()).thenReturn(DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT);
        h.service = h.withExecutors(List.of(h.purchaseExecutor, schedule));
        assertThrows(ServiceException.class, () -> h.service.executeApproved(10L, actor()));
        verify(schedule, never()).execute(any(), any()); verify(h.store, never()).finishRecoveredExecution(anyLong(), anyInt());
    }
    @Test void purchaseRetryCanContinueReadyNotStartedDependency() {
        var h = new RecoveryHarness("EXECUTING", "EXECUTING");
        var later = scheduleAction("NOT_STARTED"); later.setDependencyIdsJson("[20]"); h.actions.add(later);
        var schedule = mock(DecisionActionExecutor.class); when(schedule.type()).thenReturn(DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT);
        var done = new DecisionActionExecutionResult(true, false, "PRD_SCHEDULE_PLAN", 101L, "PLAN-101", Map.of(), null, null);
        when(schedule.execute(any(), any())).thenReturn(done); when(schedule.verify(any(), any(), any())).thenAnswer(c -> c.getArgument(1));
        doAnswer(c -> { later.setExecutionStatus("SUCCEEDED"); later.setResultJson(h.mapper.writeValueAsString(c.getArgument(1))); return null; })
            .when(h.store).saveActionResult(eq(30L), any(), anyInt());
        h.service = h.withExecutors(List.of(h.purchaseExecutor, schedule));
        when(h.port.recoverPurchaseAction(any(), any())).thenReturn(h.receipt(true));
        h.service.retry(20L, actor());
        verify(schedule).execute(argThat(a -> a.actionId().equals(30L)), any());
        verify(h.finalizer).finalizeRun(10L, actor());
    }
    @Test void terminalPurchaseRunIsNeverMutatedOrRecovered() {
        var h = new RecoveryHarness("FAILED", "FAILED");
        assertThrows(ServiceException.class, () -> h.service.retry(20L, actor()));
        verifyNoInteractions(h.port); h.assertNoPurchaseWrite();
    }
    @Test void missingProductionDependenciesCannotAllowV2Purchase() {
        var h = new RecoveryHarness("APPROVED", "NOT_STARTED");
        for (var service : List.of(
            new DecisionActionExecutionService(h.store, List.of(h.purchaseExecutor), h.mapper),
            new DecisionActionExecutionService(h.store, List.of(h.purchaseExecutor), h.mapper, h.events, h.rights, null, h.finalizer),
            new DecisionActionExecutionService(h.store, List.of(h.purchaseExecutor), h.mapper, h.events, h.rights, h.port, null),
            new DecisionActionExecutionService(h.store, List.of(h.purchaseExecutor), h.mapper, h.events, null, h.port, h.finalizer))) {
            assertThrows(
                    ServiceException.class,
                    () -> service.executeApproved(10L, actor()),
                    "新版采购端口、实时权限或复盘依赖缺失必须失败关闭");
        }
        h.assertNoPurchaseWrite(); verify(h.store, never()).transition(anyLong(), any(), any());
    }
    @Test void purchaseSseFailureDoesNotDenyAlreadyCommittedResult() {
        var h = new RecoveryHarness("EXECUTING", "EXECUTING"); when(h.port.recoverPurchaseAction(any(), any())).thenReturn(h.receipt(true));
        doThrow(new IllegalStateException("仅 SSE 暂时不可用")).when(h.events).sendActionStatus(anyLong(), anyLong(), anyLong(), anyString());
        assertDoesNotThrow(() -> h.service.retry(20L, actor()));
        assertEquals("SUCCEEDED", h.purchase.getExecutionStatus()); verify(h.purchaseExecutor, never()).compensate(any(), any(), any());
    }
    @Test void resumedNonPurchaseCompensationKeepsKnownTerminalAuditWithoutCompensatingPurchase() {
        var h = new RecoveryHarness("EXECUTING", "SUCCEEDED"); h.save(h.receipt(false));
        var later = scheduleAction("NOT_STARTED"); h.actions.add(later);
        var schedule = mock(DecisionActionExecutor.class); when(schedule.type()).thenReturn(DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT);
        var created = new DecisionActionExecutionResult(true, false, "PRD_SCHEDULE_PLAN", 101L, "PLAN-101", Map.of(), null, null);
        var failed = new DecisionActionExecutionResult(false, false, "PRD_SCHEDULE_PLAN", 101L, "PLAN-101", Map.of(), "VERIFY_FAILED", "排程草稿未核验");
        when(schedule.execute(any(), any())).thenReturn(created); when(schedule.verify(any(), any(), any())).thenReturn(failed);
        when(schedule.compensate(any(), any(), any())).thenReturn(created);
        doAnswer(c -> { later.setExecutionStatus("FAILED"); return null; }).when(h.store).saveActionResult(eq(30L), any(), anyInt());
        doAnswer(c -> { later.setExecutionStatus("COMPENSATED"); return null; }).when(h.store).saveCompensationResult(eq(30L), any());
        h.service = h.withExecutors(List.of(h.purchaseExecutor, schedule));
        assertDoesNotThrow(() -> h.service.executeApproved(10L, actor()), "非采购原补偿流程不能停在 ROLLING_BACK 丢失终态审计");
        verify(h.store).transition(10L, DecisionRunStatus.ROLLING_BACK, DecisionRunStatus.FAILED);
        verify(h.finalizer).finalizeRun(10L, actor());
        verify(h.purchaseExecutor, never()).compensate(any(), any(), any());
        assertEquals("SUCCEEDED", h.purchase.getExecutionStatus(), "已提交采购不能被非采购补偿虚构为已撤销");
    }
    @Test void oldNonPurchaseInterruptedRunUsesExistingVersionCasTransition() throws Exception {
        var store = mock(DecisionPersistenceService.class);
        var rights = mock(PermissionService.class);
        when(rights.getMenuPermission(1L)).thenReturn(actor().permissions());
        var finalizer = mock(DecisionFinalizeService.class); var executor = mock(DecisionActionExecutor.class);
        when(executor.type()).thenReturn(DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT);
        var r = run(10L, DecisionRunStatus.EXECUTING); var a = scheduleAction("SUCCEEDED");
        var result = new DecisionActionExecutionResult(true, false, "PRD_SCHEDULE_PLAN", 101L, "PLAN-101", Map.of(), null, null);
        var mapper = new ObjectMapper(); a.setResultJson(mapper.writeValueAsString(result));
        when(store.requireRun(10L, "000000")).thenReturn(r); when(store.listActions(10L)).thenReturn(List.of(a));
        when(executor.verify(any(), any(), any())).thenReturn(result);
        var service =
                new DecisionActionExecutionService(
                        store,
                        List.of(executor),
                        mapper,
                        null,
                        rights,
                        mock(IndustrialDecisionDataPort.class),
                        finalizer);
        service.executeApproved(10L, actor());
        verify(store).transition(10L, DecisionRunStatus.EXECUTING, DecisionRunStatus.SUCCEEDED);
        verify(store, never()).finishRecoveredExecution(anyLong(), anyInt());
        verify(finalizer).finalizeRun(10L, actor());
    }
    @Test void unknownExecutionStateIsSafelyRejectedBeforeAnyRecoveryWrite() {
        var h = new RecoveryHarness("EXECUTING", "EXECUTING"); h.purchase.setExecutionStatus(null);
        assertThrows(
                ServiceException.class,
                () -> h.service.retry(20L, actor()),
                "损坏或未知动作状态不能变成 NPE，更不能取得恢复或创建授权");
        h.assertNoPurchaseWrite(); verifyNoInteractions(h.port);
    }
    @Test void finishCasRefusalKeepsRunUnresolvedAndDoesNotFinalize() {
        var h = new RecoveryHarness("EXECUTING", "EXECUTING");
        when(h.port.recoverPurchaseAction(any(), any())).thenReturn(h.receipt(true));
        when(h.store.finishRecoveredExecution(eq(10L), anyInt())).thenReturn(false);
        h.service.retry(20L, actor());
        assertEquals("EXECUTING", h.run.getStatus(), "最终 CAS 未收敛时不能凭本地成功集合宣布整运行成功");
        verifyNoInteractions(h.finalizer);
    }
    @Test void forgedPayloadV2FailsBeforeExecutionStatusOrCounters() {
        var h = new RecoveryHarness("APPROVED", "NOT_STARTED");
        h.purchase.setPayloadJson("{\"runId\":\"10\",\"purchase_payload_version\":2}");
        assertThrows(ServiceException.class, () -> h.service.executeApproved(10L, actor()));
        h.assertNoPurchaseWrite(); verify(h.store, never()).transition(anyLong(), any(), any());
        verifyNoInteractions(h.port); assertEquals(2, h.purchase.getRetryCount());
    }
    @Test void nullActionListRowCannotBeIgnoredToFinalizeRun() {
        var h = new RecoveryHarness("EXECUTING", "SUCCEEDED"); h.save(h.receipt(false));
        when(h.store.listActions(10L)).thenReturn(Arrays.asList(h.purchase, null));
        assertThrows(
                ServiceException.class,
                () -> h.service.retry(20L, actor()),
                "读取不到某个动作不是该动作已成功的证据，不允许过滤后收敛运行");
        verify(h.store, never()).finishRecoveredExecution(anyLong(), anyInt());
        verifyNoInteractions(h.finalizer);
    }
    @Test void anotherTenantCannotRecoverEvenWithSameNumericUserId() {
        var h = new RecoveryHarness("EXECUTING", "EXECUTING");
        var foreign = new DecisionActor("000001", 1L, "同 ID 不同租户", actor().permissions());
        when(h.store.requireAction(20L, "000001")).thenReturn(h.purchase);
        when(h.store.requireRun(10L, "000001")).thenReturn(h.run);
        when(h.store.trustedExecutionActor(10L, "000001")).thenReturn(actor());
        assertThrows(ServiceException.class, () -> h.service.retry(20L, foreign));
        h.assertNoPurchaseWrite(); verifyNoInteractions(h.port);
    }
    @Test void revokedLivePermissionIsRecheckedOnEveryRecoveryAttempt() {
        var h = new RecoveryHarness("EXECUTING", "EXECUTING");
        assertThrows(ServiceException.class, () -> h.service.retry(20L, actor()));
        when(h.rights.getMenuPermission(1L)).thenReturn(Set.of("ai:decision:execute"));
        assertThrows(DecisionActionExecutionService.InitialExecutionCheckUnavailableException.class, () -> h.service.retry(20L, actor()));
        verify(h.port, times(1)).recoverPurchaseAction(any(), any()); h.assertNoPurchaseWrite();
    }
    @Test void failedRetryUnknownSourceReadDoesNotIncrementCounterOrCreate() {
        var h = new RecoveryHarness("EXECUTING", "FAILED");
        doThrow(new ServiceException("来源链读取不可证明"))
                .when(h.port)
                .requireFreshPurchaseContract(any(), any(), anyInt(), any());
        assertThrows(ServiceException.class, () -> h.service.retry(20L, actor()));
        h.assertNoPurchaseWrite(); assertEquals("FAILED", h.purchase.getExecutionStatus()); assertEquals(2, h.purchase.getRetryCount());
    }
    @Test void recoveredReceiptWithChangedDraftVersionCannotConvergeOrCompensate() {
        var h = new RecoveryHarness("EXECUTING", "EXECUTING");
        var draft = h.receipt(true);
        var details = new HashMap<String, Object>(draft.result());
        details.put("versionMarker", "v1");
        when(h.port.recoverPurchaseAction(any(), any())).thenReturn(new DecisionActionExecutionResult(true, false,
            draft.resultBizType(), draft.resultBizId(), draft.resultBizCode(), details, null, null));
        assertThrows(ServiceException.class, () -> h.service.retry(20L, actor()));
        h.assertNoPurchaseWrite(); verifyNoInteractions(h.finalizer);
    }
    @Test void retrySucceededTerminalPurchaseOnlyVerifiesNeverWrites() {
        var h = new RecoveryHarness("SUCCEEDED", "SUCCEEDED"); h.save(h.receipt(false));
        h.service.retry(20L, actor()); verify(h.purchaseExecutor).verify(any(), any(), any());
        h.assertNoPurchaseWrite(); verify(h.store, never()).finishRecoveredExecution(anyLong(), anyInt());
        verify(h.store, never()).transition(anyLong(), any(), any()); verifyNoInteractions(h.finalizer);
    }
    @Test void resumedCompensationSkipsOnlyLaterActionsNotDetachedPreviouslySucceededRows() {
        var h = new RecoveryHarness("APPROVED", "NOT_STARTED");
        var later = scheduleAction("NOT_STARTED"); h.actions.add(later);
        var schedule = mock(DecisionActionExecutor.class); when(schedule.type()).thenReturn(DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT);
        var created = new DecisionActionExecutionResult(true, false, "PRD_SCHEDULE_PLAN", 101L, "PLAN-101", Map.of(), null, null);
        when(h.purchaseExecutor.execute(any(), any())).thenReturn(h.receipt(false));
        when(schedule.execute(any(), any())).thenReturn(created);
        when(schedule.verify(any(), any(), any())).thenReturn(new DecisionActionExecutionResult(false, false,
            "PRD_SCHEDULE_PLAN", 101L, "PLAN-101", Map.of(), "VERIFY_FAILED", "排程核验失败"));
        when(schedule.compensate(any(), any(), any())).thenReturn(created);
        // 实体读取不是数据库活对象：master 更新只替换持久化读回行，不改先前读取的快照。
        doAnswer(
                        c -> {
                            Long id = c.getArgument(0);
                            var current =
                                    h.actions.stream()
                                            .filter(a -> a.getId().equals(id))
                                            .findFirst()
                                            .orElseThrow();
                            if (!Objects.equals(current.getExecutionStatus(), c.getArgument(2)))
                                throw new ServiceException("模拟状态 CAS 拒绝覆写先前已成功动作");
                            if (id.equals(20L)) {
                                var fresh = DecisionPurchaseContractGuardTest.v2Action();
                                fresh.setExecutionStatus(c.getArgument(3));
                                fresh.setVersion(1);
                                h.actions.set(0, fresh);
                            } else current.setExecutionStatus(c.getArgument(3));
                            return null;
                        })
                .when(h.store)
                .transitionAction(anyLong(), anyString(), anyString(), anyString());
        doAnswer(c -> {
            var current = h.actions.get(0); DecisionActionExecutionResult r = c.getArgument(1);
            current.setExecutionStatus("SUCCEEDED"); current.setResultBizType(r.resultBizType()); current.setResultBizId(r.resultBizId());
            current.setResultBizCode(r.resultBizCode()); current.setResultJson(h.mapper.writeValueAsString(r)); return null;
        }).when(h.store).saveActionResult(eq(20L), any(), anyInt());
        doAnswer(c -> { later.setExecutionStatus("FAILED"); return null; }).when(h.store).saveActionResult(eq(30L), any(), anyInt());
        doAnswer(c -> { later.setExecutionStatus("COMPENSATED"); return null; }).when(h.store).saveCompensationResult(eq(30L), any());
        h.service = h.withExecutors(List.of(h.purchaseExecutor, schedule));
        assertDoesNotThrow(() -> h.service.executeApproved(10L, actor()), "先前动作的陈旧 NOT_STARTED 快照不能导致成功采购被跳过或阻断终态审计");
        verify(h.store, never()).transitionAction(20L, "APPROVED", "NOT_STARTED", "SKIPPED");
        verify(h.store).transition(10L, DecisionRunStatus.ROLLING_BACK, DecisionRunStatus.FAILED);
        assertEquals("SUCCEEDED", h.actions.get(0).getExecutionStatus(), "原采购实际成功结果须完整保留");
    }
    /** 内存替身只模拟 master 返回的状态，验证编排调用顺序，不证明 SQL CAS 或跨库物理回滚。 */
    private static final class RecoveryHarness {
        final DecisionPersistenceService store = mock(DecisionPersistenceService.class);
        final IndustrialDecisionDataPort port = mock(IndustrialDecisionDataPort.class);
        final PermissionService rights = mock(PermissionService.class);
        final DecisionFinalizeService finalizer = mock(DecisionFinalizeService.class);
        final AiDecisionSseMessageHelper events = mock(AiDecisionSseMessageHelper.class);
        final DecisionActionExecutor purchaseExecutor = mock(DecisionActionExecutor.class);
        final ObjectMapper mapper = new ObjectMapper();
        final AiDecisionRun run = DecisionPurchaseContractGuardTest.v2Run();
        final AiDecisionAction purchase = DecisionPurchaseContractGuardTest.v2Action();
        final List<AiDecisionAction> actions = new ArrayList<>();
        final DeliveryContextSnapshot snapshot =
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
        DecisionActionExecutionService service;
        RecoveryHarness(String runState, String actionState) {
            run.setStatus(runState); run.setInputSnapshotHash("hash-old"); purchase.setExecutionStatus(actionState); purchase.setRetryCount(2); actions.add(purchase);
            when(rights.getMenuPermission(1L)).thenReturn(actor().permissions());
            when(port.canAccess(any(), any())).thenReturn(true);
            when(port.checkFreshness(any(), anyString(), any()))
                    .thenReturn(new DecisionFreshnessResult(true, "hash-old", List.of()));
            when(purchaseExecutor.type()).thenReturn(DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT);
            when(purchaseExecutor.verify(any(), any(), any())).thenAnswer(c -> c.getArgument(1));
            when(store.requireRun(10L, "000000")).thenReturn(run);
            when(store.trustedExecutionActor(10L, "000000")).thenReturn(actor());
            when(store.requireSnapshot(10L, "000000")).thenReturn(snapshot);
            when(store.requireAction(anyLong(), eq("000000"))).thenAnswer(c -> actions.stream().filter(a -> a.getId().equals(c.getArgument(0))).findFirst().orElseThrow());
            when(store.listActions(10L)).thenAnswer(c -> List.copyOf(actions));
            doAnswer(c -> { run.setStatus(((DecisionRunStatus) c.getArgument(2)).name()); run.setVersion(run.getVersion()+1); return null; })
                .when(store).transition(eq(10L), any(), any());
            doAnswer(c -> { var a = actions.stream().filter(x -> x.getId().equals(c.getArgument(0))).findFirst().orElseThrow();
                a.setExecutionStatus(c.getArgument(3)); a.setVersion(a.getVersion()+1); return null; })
                .when(store).transitionAction(anyLong(), anyString(), anyString(), anyString());
            doAnswer(c -> { save(c.getArgument(1)); return null; }).when(store).saveActionResult(eq(20L), any(), anyInt());
            doAnswer(c -> { save(c.getArgument(2)); return null; }).when(store).saveRecoveredPurchaseResult(eq(20L), anyInt(), any());
            when(store.finishRecoveredExecution(eq(10L), anyInt())).thenAnswer(c -> {
                boolean all = actions.stream().allMatch(a -> "SUCCEEDED".equals(a.getExecutionStatus()));
                if (all) { run.setStatus("SUCCEEDED"); run.setVersion(run.getVersion()+1); } return all;
            });
            service = withExecutors(List.of(purchaseExecutor));
        }
        DecisionActionExecutionService withExecutors(List<DecisionActionExecutor> executors) {
            return new DecisionActionExecutionService(store, executors, mapper, events, rights, port, finalizer);
        }
        DecisionActionExecutionResult receipt(boolean replayed) {
            try {
                var payload =
                        mapper.readValue(
                                purchase.getPayloadJson(),
                                new TypeReference<Map<String, Object>>() {});
                var contract = DecisionPurchaseContractCodec.parse(payload, run.getId());
                var proposal =
                        new DecisionActionProposal(
                                purchase.getId(),
                                DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
                                "合成采购",
                                "来源已冻结",
                                snapshot.rootBusinessRef(),
                                payload,
                                DecisionRiskLevel.MEDIUM,
                                true,
                                "pu:requisition:add",
                                purchase.getIdempotencyKey(),
                                "purchaseRequisitionDraft",
                                1,
                                List.of());
                var line = contract.frozenLines().get(0);
                var details =
                        Map.<String, Object>ofEntries(
                                Map.entry("purchase_payload_version", 2),
                                Map.entry("runId", "10"),
                                Map.entry("actionId", "20"),
                                Map.entry("tenantId", actor().tenantId()),
                                Map.entry("requestKey", purchase.getIdempotencyKey()),
                                Map.entry("materialId", contract.materialId().toString()),
                                Map.entry("vendorId", contract.vendorId().toString()),
                                Map.entry("sourceType", "AI_DECISION"),
                                Map.entry(
                                        "sourceFingerprint", contract.expectedSourceFingerprint()),
                                Map.entry(
                                        "payloadFingerprint",
                                        DecisionPurchaseContractCodec.payloadFingerprint(
                                                proposal, actor())),
                                Map.entry("status", "DRAFT"),
                                Map.entry("versionMarker", "v0"),
                                Map.entry("afterState", "DRAFT"),
                                Map.entry("beforeState", replayed ? "COMMITTED" : "ABSENT"),
                                Map.entry("replayed", replayed),
                                Map.entry(
                                        "lines",
                                        List.of(
                                                Map.of(
                                                        "lineId",
                                                        "801",
                                                        "requirementDetailId",
                                                        line.detailId().toString(),
                                                        "materialId",
                                                        contract.materialId().toString(),
                                                        "baseUomId",
                                                        line.baseUomId().toString(),
                                                        "orderUomId",
                                                        line.orderUomId().toString(),
                                                        "coveredDemandBase",
                                                        line.coveredDemandBase()
                                                                .stripTrailingZeros()
                                                                .toPlainString(),
                                                        "requestedOrderQty",
                                                        line.requestedOrderQty()
                                                                .stripTrailingZeros()
                                                                .toPlainString(),
                                                        "requiredDate",
                                                        line.requiredDate().toString()))));
                return new DecisionActionExecutionResult(true, false, "PU_PURCHASE_REQUISITION", 99L, "REQ-99", details, null, null);
            } catch (Exception invalid) { throw new AssertionError("恢复成功 fixture 必须具备真实完整 ERP 结果形状", invalid); }
        }
        void save(DecisionActionExecutionResult result) {
            purchase.setExecutionStatus(result.success() ? "SUCCEEDED" : "FAILED");
            purchase.setResultBizType(result.resultBizType()); purchase.setResultBizId(result.resultBizId()); purchase.setResultBizCode(result.resultBizCode());
            try { purchase.setResultJson(mapper.writeValueAsString(result)); } catch (Exception invalid) { throw new AssertionError("测试结果编码失败", invalid); } purchase.setVersion(purchase.getVersion()+1);
        }
        void assertNoPurchaseWrite() {
            verify(store, never()).transitionAction(anyLong(), anyString(), anyString(), anyString());
            verify(store, never()).saveActionResult(anyLong(), any(), anyInt());
            verify(store, never()).saveRecoveredPurchaseResult(anyLong(), anyInt(), any());
            verify(purchaseExecutor, never()).execute(any(), any()); verify(purchaseExecutor, never()).compensate(any(), any(), any());
        }
    }
    static AiDecisionAction scheduleAction(String state) {
        var a = new AiDecisionAction(); a.setId(30L); a.setRunId(10L); a.setTenantId("000000");
        a.setActionType("CREATE_SCHEDULE_PLAN_DRAFT"); a.setExecutorCode("schedulePlanDraft");
        a.setPermissionCode("prd:schedule:run"); a.setApprovalRequired("Y"); a.setApprovalStatus("APPROVED");
        a.setExecutionStatus(state); a.setTargetBizType("SCHEDULE_PLAN"); a.setTargetBizId(9001L);
        a.setRiskLevel("HIGH"); a.setSequenceNo(2); a.setVersion(0); a.setDependencyIdsJson("[]");
        a.setPayloadJson("{\"runId\":\"10\"}"); return a;
    }
}
