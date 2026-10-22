package com.nexus.flow.node.industrial;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.hutool.extra.spring.SpringUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.service.decision.*;
import com.nexus.ai.service.decision.executor.CreatePurchaseRequisitionDraftExecutor;
import com.nexus.ai.service.decision.executor.DecisionActionExecutor;
import com.nexus.ai.service.sse.AiDecisionSseMessageHelper;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.*;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;
import com.nexus.common.ai.domain.entity.*;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.service.PermissionService;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.state.FlowState;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

/** 实际执行节点、异常包装和执行服务整链反例；内存状态不代表真实 JDBC 或 ERP 回滚。 */
class RecoveryFlowTest {
    @Test void executingUnknownMissSurvivesActualFlowWrapperAndCanRetryOriginalAction() {
        var h = new Harness("EXECUTING", "EXECUTING");
        h.run.setStatus("APPROVED"); h.action.setExecutionStatus("NOT_STARTED");
        when(h.executor.execute(any(), any())).thenThrow(new ServiceException("ERP 响应未知"));
        assertThrows(ServiceException.class, () -> h.node(), "节点允许失败，但业务运行必须保留可恢复状态");
        assertEquals("EXECUTING", h.run.getStatus()); assertEquals("EXECUTING", h.action.getExecutionStatus());
        h.noTerminalOrCompensation(); verify(h.store, never()).saveOutcome(any(), any());
        when(h.port.recoverPurchaseAction(any(), any())).thenReturn(h.receipt(true));
        assertDoesNotThrow(() -> h.inTenant(() -> { h.execution.retry(20L, h.actor); return null; }),
            "同绑定执行人仍能用既有 retry 恢复原单");
        assertEquals("SUCCEEDED", h.run.getStatus()); assertEquals(99L, h.action.getResultBizId());
        verify(h.executor, times(1)).execute(any(), any());
    }
    @Test void finalCasFalseSurvivesWrapperWithoutOutcomeOrFalseTerminal() {
        var h = new Harness("EXECUTING", "SUCCEEDED"); h.save(h.receipt(false)); h.finishCas = false;
        assertThrows(ServiceException.class, () -> h.node(), "最终 CAS 未收敛不是执行节点成功完成");
        assertEquals("EXECUTING", h.run.getStatus()); assertEquals("SUCCEEDED", h.action.getExecutionStatus());
        assertEquals(99L, h.action.getResultBizId()); h.noTerminalOrCompensation();
        verifyNoInteractions(h.outcomes); verify(h.store, never()).saveOutcome(any(), any());
    }
    @Test void masterWriteFailureAfterCommitSurvivesActualWrapperWithOriginalReceipt() {
        var h = new Harness("APPROVED", "NOT_STARTED"); h.masterResultUnavailable = true;
        when(h.executor.execute(any(), any())).thenReturn(h.receipt(false));
        assertThrows(ServiceException.class, () -> h.node());
        assertEquals("EXECUTING", h.run.getStatus()); assertEquals("EXECUTING", h.action.getExecutionStatus());
        h.noTerminalOrCompensation(); verify(h.store, never()).saveOutcome(any(), any());
        h.masterResultUnavailable = false; when(h.port.recoverPurchaseAction(any(), any())).thenReturn(h.receipt(true));
        h.inTenant(() -> { h.execution.retry(20L, h.actor); return null; });
        assertEquals(99L, h.action.getResultBizId()); verify(h.executor, times(1)).execute(any(), any());
    }
    @Test void successfulExecutionFinalizesExactlyOnceAcrossServiceAndNode() {
        var h = new Harness("APPROVED", "NOT_STARTED"); when(h.executor.execute(any(), any())).thenReturn(h.receipt(false));
        var output = h.node(); assertEquals("SUCCEEDED", output.path("status").asText());
        verify(h.outcomes, times(1)).measure(10L, h.actor);
        verify(h.store, times(1)).saveOutcome(eq(10L), any());
        verify(h.executor, times(1)).execute(any(), any()); verify(h.store, never()).fail(any(), any(), any());
    }
    @Test void directFinalizerDoesNotMeasureOrWriteNonterminalRun() {
        var h = new Harness("EXECUTING", "EXECUTING"); h.finalizer.finalizeRun(10L, h.actor);
        verifyNoInteractions(h.outcomes); verify(h.store, never()).saveOutcome(any(), any());
    }
    @Test void ordinaryAnalysisErrorKeepsOriginalFailedSemantics() {
        var h = new Harness("RUNNING", "NOT_STARTED"); h.ctx.setNodeType("industrial.deliveryContext");
        assertThrows(ServiceException.class, () -> h.inTenant(() -> h.support.output(h.ctx,
            (Supplier<Object>) () -> { throw new ServiceException("普通分析失败"); })));
        assertEquals("FAILED", h.run.getStatus()); verify(h.store).fail(eq(10L), anyString(), anyString());
    }
    @Test void ordinaryNonPurchaseExecutionErrorIsNotProtectedAsPurchasePending() {
        var h = new Harness("EXECUTING", "EXECUTING"); h.nonPurchase();
        assertThrows(ServiceException.class, () -> h.node());
        assertEquals("FAILED", h.run.getStatus()); verify(h.store).fail(eq(10L), anyString(), anyString());
    }
    @Test void freshGenericSnapshotIsCheckedBeforeFirstTransitionAndPurchaseSourceRead() {
        var h = new Harness("APPROVED", "NOT_STARTED"); when(h.executor.execute(any(), any())).thenReturn(h.receipt(false));
        h.node(); var order = inOrder(h.port, h.store, h.executor);
        order.verify(h.port).checkFreshness(any(), eq("hash-old"), eq(h.actor));
        order.verify(h.store).transition(10L, DecisionRunStatus.APPROVED, DecisionRunStatus.EXECUTING);
        order.verify(h.port).requireFreshPurchaseContract(any(), any(), eq(30), eq(h.actor));
        order.verify(h.executor).execute(any(), any());
    }
    @Test void changedOrdinaryHashWithUnchangedPurchaseContractStopsApprovedRunAsStale() {
        var h = new Harness("APPROVED", "NOT_STARTED");
        when(h.port.checkFreshness(any(), anyString(), any())).thenReturn(new DecisionFreshnessResult(false, "hash-new", List.of("schedule")));
        assertThrows(ServiceException.class, () -> h.node());
        assertEquals("STALE", h.run.getStatus()); assertEquals("NOT_STARTED", h.action.getExecutionStatus());
        verify(h.store).transition(10L, DecisionRunStatus.APPROVED, DecisionRunStatus.STALE);
        verify(h.executor, never()).execute(any(), any()); verify(h.port, never()).requireFreshPurchaseContract(any(), any(), anyInt(), any());
    }
    @Test void unknownHashDoesNotBecomeStaleOrFailedThroughFlowWrapper() {
        var h = new Harness("APPROVED", "NOT_STARTED"); when(h.port.checkFreshness(any(), anyString(), any())).thenReturn(null);
        assertThrows(ServiceException.class, () -> h.node());
        assertEquals("APPROVED", h.run.getStatus()); assertEquals("NOT_STARTED", h.action.getExecutionStatus());
        verify(h.store, never()).transition(any(), any(), any()); verify(h.store, never()).fail(any(), any(), any());
        verify(h.executor, never()).execute(any(), any());
    }
    @Test void unavailablePermissionDoesNotWriteApprovedRunStaleAtActorBoundary() {
        var h = new Harness("APPROVED", "NOT_STARTED"); when(h.rights.getMenuPermission(1L)).thenThrow(new ServiceException("读取权限未知"));
        assertThrows(ServiceException.class, () -> h.node());
        assertEquals("APPROVED", h.run.getStatus()); verify(h.store, never()).transition(any(), any(), any());
        verify(h.store, never()).fail(any(), any(), any()); verify(h.executor, never()).execute(any(), any());
    }
    @Test void existingExecutingReceiptIgnoresChangedGlobalSnapshotAndRecoversFirst() {
        var h = new Harness("EXECUTING", "EXECUTING"); when(h.port.recoverPurchaseAction(any(), any())).thenReturn(h.receipt(true));
        when(h.port.checkFreshness(any(), anyString(), any())).thenThrow(new ServiceException("落账后全局快照必然变化"));
        h.node(); assertEquals("SUCCEEDED", h.run.getStatus()); assertEquals(99L, h.action.getResultBizId());
        verify(h.port, never()).checkFreshness(any(), anyString(), any()); verify(h.executor, never()).execute(any(), any());
        verify(h.port, never()).requireFreshPurchaseContract(any(), any(), anyInt(), any());
        verify(h.outcomes, times(1)).measure(10L, h.actor);
    }
    @Test void permissionReadFailsAfterActorValidationStillPreservesInitialApprovedState() {
        var h = new Harness("APPROVED", "NOT_STARTED");
        when(h.rights.getMenuPermission(1L)).thenReturn(h.actor.permissions()).thenThrow(new ServiceException("执行前实时权限读取未知"));
        assertThrows(ServiceException.class, () -> h.node());
        assertEquals("APPROVED", h.run.getStatus()); verify(h.store, never()).transition(any(), any(), any());
        verify(h.store, never()).fail(any(), any(), any()); verify(h.executor, never()).execute(any(), any());
    }
    @Test void purchasePermissionReadFailsAtActionValidationDoesNotCloseApprovedRun() {
        var h = new Harness("APPROVED", "NOT_STARTED");
        when(h.rights.getMenuPermission(1L)).thenReturn(h.actor.permissions(), h.actor.permissions())
            .thenThrow(new ServiceException("动作采购权限读取未知"));
        assertThrows(ServiceException.class, () -> h.node());
        assertEquals("APPROVED", h.run.getStatus()); verify(h.store, never()).fail(any(), any(), any());
        verify(h.store, never()).transition(any(), any(), any());
    }
    @Test void livePermissionReadFailsDuringExecutingRecoveryDoesNotCloseKnownPurchaseBreakpoint() {
        var h = new Harness("EXECUTING", "EXECUTING");
        when(h.rights.getMenuPermission(1L)).thenReturn(h.actor.permissions(), h.actor.permissions())
            .thenThrow(new ServiceException("恢复动作权限读取未知"));
        assertThrows(ServiceException.class, () -> h.node());
        assertEquals("EXECUTING", h.run.getStatus()); assertEquals("EXECUTING", h.action.getExecutionStatus());
        h.noTerminalOrCompensation(); verify(h.port, never()).recoverPurchaseAction(any(), any());
    }
    @Test void deniedExecutionIdentityDoesNotReadMasterBeforeTrustBoundary() {
        var h = new Harness("APPROVED", "NOT_STARTED");
        var denied = new DecisionActor("000000", 1L, "无执行权限", Set.of("pu:requisition:add"));
        assertThrows(RuntimeException.class, () -> h.execution.executeApproved(10L, denied));
        verifyNoInteractions(h.store, h.port);
    }
    @Test void diagnosticActionReadFailureCannotConvertTypedPurchasePendingIntoTerminalFailure() {
        var h = new Harness("EXECUTING", "EXECUTING");
        when(h.store.listActions(10L)).thenReturn(List.of(h.action), List.of(h.action))
            .thenThrow(new ServiceException("包装层重读动作未知"));
        assertThrows(ServiceException.class, () -> h.node());
        assertEquals("EXECUTING", h.run.getStatus()); assertEquals("EXECUTING", h.action.getExecutionStatus());
        h.noTerminalOrCompensation();
    }
    @Test void succeededPurchaseOrdinaryServiceReadErrorDoesNotCloseOrOverwriteOriginalThroughRealPort() {
        var h = new Harness("EXECUTING", "SUCCEEDED"); h.save(h.receipt(false));
        assertStoredPurchaseReadPending(h, new ServiceException("原稿独立读取暂时不可确认"), null);
    }
    @Test void succeededPurchaseOrdinaryRuntimeReadErrorDoesNotCloseOrOverwriteOriginalThroughRealPort() {
        var h = new Harness("EXECUTING", "SUCCEEDED"); h.save(h.receipt(false));
        assertStoredPurchaseReadPending(h, new IllegalStateException("原单读取未知"), null);
    }
    @Test void succeededPurchaseUnknownVerifyResultDoesNotBecomeNewDraftSuccess() {
        var h = new Harness("EXECUTING", "SUCCEEDED"); h.save(h.receipt(false));
        assertStoredPurchaseReadPending(h, null, null);
    }
    @Test void succeededPurchaseChangedDocumentKeepsHistoryAndDoesNotFinalize() {
        var h = new Harness("EXECUTING", "SUCCEEDED"); h.save(h.receipt(false));
        var original = h.receipt(true); var details = new HashMap<String,Object>(original.result());
        details.put("status", "CLOSED"); details.put("afterState", "CLOSED");
        var changed = new DecisionActionExecutionResult(false, false, original.resultBizType(), original.resultBizId(),
            original.resultBizCode(), details, "VERIFY_STATE_MISMATCH", "原单已关闭而非原稿");
        assertStoredPurchaseReadPending(h, null, changed);
    }
    @Test void succeededPurchaseActionReaderUnknownBeforePortVerifyKeepsOriginalState() throws Exception {
        var h = new Harness("EXECUTING", "SUCCEEDED"); h.save(h.receipt(false));
        String oldJson=h.action.getResultJson(); int version=h.action.getVersion();
        var reader=spy(h.mapper);
        doThrow(new ServiceException("冻结采购请求暂时读取不可确认"))
                .when(reader)
                .readValue(eq(h.action.getPayloadJson()), any(TypeReference.class));
        h.execution =
                new DecisionActionExecutionService(
                        h.store,
                        List.of(new CreatePurchaseRequisitionDraftExecutor(h.port)),
                        reader,
                        h.events,
                        h.rights,
                        h.port,
                        h.finalizer);
        assertThrows(ServiceException.class,()->h.node());
        assertEquals("EXECUTING",h.run.getStatus()); assertEquals("SUCCEEDED",h.action.getExecutionStatus());
        assertEquals(oldJson,h.action.getResultJson()); assertEquals(version,h.action.getVersion());
        verify(h.store,never()).fail(any(),any(),any()); verifyNoInteractions(h.outcomes);
        verify(h.port,never()).executeBusinessAction(any(),any()); verify(h.port,never()).verifyBusinessAction(any(),any(),any());
        assertNoResultWrite(h);
    }
    @Test void nonPurchaseSucceededReferenceReadErrorKeepsOriginalFailureSemantics() throws Exception {
        var h = new Harness("EXECUTING", "SUCCEEDED"); h.nonPurchase();
        h.action.setResultJson(h.mapper.writeValueAsString(new DecisionActionExecutionResult(true,false,"PRD_SCHEDULE_PLAN",101L,"PLAN-101",Map.of(),null,null)));
        when(h.executor.verify(any(),any(),any())).thenThrow(new ServiceException("非采购核验失败"));
        assertThrows(ServiceException.class,()->h.node());
        assertEquals("FAILED",h.run.getStatus()); verify(h.store).fail(eq(10L),anyString(),anyString());
        verify(h.executor,never()).execute(any(),any());
    }
    private void assertStoredPurchaseReadPending(Harness h, RuntimeException error, DecisionActionExecutionResult result) {
        String json=h.action.getResultJson(); int version=h.action.getVersion(); Long id=h.action.getResultBizId();
        String type=h.action.getResultBizType(), code=h.action.getResultBizCode();
        h.execution =
                new DecisionActionExecutionService(
                        h.store,
                        List.of(new CreatePurchaseRequisitionDraftExecutor(h.port)),
                        h.mapper,
                        h.events,
                        h.rights,
                        h.port,
                        h.finalizer);
        if(error!=null) when(h.port.verifyBusinessAction(any(),any(),any())).thenThrow(error);
        else when(h.port.verifyBusinessAction(any(),any(),any())).thenReturn(result);
        assertThrows(ServiceException.class,()->h.node(),"真实采购执行器的未知核验只能失败节点，不能关闭业务运行");
        verify(h.port).verifyBusinessAction(any(),any(),eq(h.actor));
        assertEquals("EXECUTING",h.run.getStatus()); assertEquals("SUCCEEDED",h.action.getExecutionStatus());
        assertEquals(id,h.action.getResultBizId()); assertEquals(type,h.action.getResultBizType()); assertEquals(code,h.action.getResultBizCode());
        assertEquals(json,h.action.getResultJson()); assertEquals(version,h.action.getVersion());
        verify(h.store,never()).fail(any(),any(),any()); verifyNoInteractions(h.outcomes); verify(h.store,never()).saveOutcome(any(),any());
        verify(h.port,never()).executeBusinessAction(any(),any()); verify(h.port,never()).compensateBusinessAction(any(),any(),any());
        assertNoResultWrite(h);
    }
    private static void assertNoResultWrite(Harness h) {
        assertTrue(mockingDetails(h.store).getInvocations().stream().noneMatch(i -> Set.of("saveActionResult","saveRecoveredPurchaseResult","saveCompensationResult","finishRecoveredExecution").contains(i.getMethod().getName())),
            "未确认的已成功采购不能覆写结果、补偿或收敛运行");
    }
    @Test void normalRecoveryFixtureMatchesCompleteErpEnvelopeAndEveryFrozenLine() {
        var h = new Harness("EXECUTING", "EXECUTING"); var receipt = h.receipt(true);
        assertEquals(Set.of("purchase_payload_version", "runId", "actionId", "tenantId", "requestKey", "materialId", "vendorId",
            "sourceType", "sourceFingerprint", "payloadFingerprint", "status", "versionMarker", "beforeState", "afterState", "replayed", "lines"), receipt.result().keySet());
        var line = (Map<?, ?>) ((List<?>) receipt.result().get("lines")).get(0);
        assertEquals(Set.of("lineId", "requirementDetailId", "materialId", "baseUomId", "orderUomId", "coveredDemandBase", "requestedOrderQty", "requiredDate"), line.keySet());
        assertEquals(DecisionPurchaseContractCodec.payloadFingerprint(h.proposal(), h.actor), receipt.result().get("payloadFingerprint"));
    }
    /** master 替身模拟允许的状态迁移和实际回读；真实服务、节点与包装均不 stub。 */
    private static final class Harness {
        final ObjectMapper mapper = new ObjectMapper();
        final DecisionActor actor = new DecisionActor("000000", 1L, "绑定执行人", Set.of("ai:decision:run", "ai:decision:execute", "prd:work:query", "pu:requisition:add", "prd:schedule:run"));
        final AiDecisionRun run = new AiDecisionRun(); final AiDecisionAction action = new AiDecisionAction();
        final DecisionRunStateMachine states = new DecisionRunStateMachine();
        final IndustrialDecisionDataPort port = mock(IndustrialDecisionDataPort.class);
        final PermissionService rights = mock(PermissionService.class);
        final DecisionActionExecutor executor = mock(DecisionActionExecutor.class);
        final AiDecisionSseMessageHelper events = mock(AiDecisionSseMessageHelper.class);
        final DecisionOutcomeService outcomes = mock(DecisionOutcomeService.class);
        final DeliveryContextSnapshot snapshot;
        final DecisionPersistenceService store;
        final DecisionFinalizeService finalizer;
        DecisionActionExecutionService execution; final DecisionNodeSupport support;
        final FlowExecutionContext ctx = new FlowExecutionContext();
        boolean finishCas = true; boolean masterResultUnavailable;
        Harness(String runStatus, String actionStatus) {
            run.setId(10L); run.setTenantId("000000"); run.setBizType("WORK_ORDER"); run.setBizId(9001L); run.setBizCode("WO-9001");
            run.setStatus(runStatus); run.setVersion(0); run.setDecisionContractVersion(2); run.setInputSnapshotHash("hash-old");
            run.setScoreConfigJson("{\"horizonDays\":30}");
            action.setId(20L); action.setRunId(10L); action.setTenantId("000000"); action.setActionType("CREATE_PURCHASE_REQUISITION_DRAFT");
            action.setExecutorCode("purchaseRequisitionDraft"); action.setPermissionCode("pu:requisition:add"); action.setApprovalRequired("Y");
            action.setApprovalStatus("APPROVED"); action.setExecutionStatus(actionStatus); action.setPurchasePayloadVersion(2); action.setVersion(0);
            action.setTargetBizType("WORK_ORDER"); action.setTargetBizId(9001L); action.setTargetBizCode("WO-9001");
            action.setRiskLevel("MEDIUM"); action.setSequenceNo(1); action.setDependencyIdsJson("[]");
            var contract = new DecisionPurchaseContractV2(139L, 17L, LocalDate.of(2026,11,1), "material-v1", "vendor-v1", 23L, "terms-v1",
                3L, "uom-v1", 3L, "uom-v1", "uom-v1", List.of(500L), List.of(new DecisionPurchaseContractV2.Line(500L,3L,new BigDecimal("5"),3L,new BigDecimal("5"),LocalDate.of(2026,10,9))), "a".repeat(64));
            action.setIdempotencyKey(DecisionPurchaseContractCodec.idempotencyKey(actor.tenantId(),10L,new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER,9001L,"WO-9001"),contract));
            try { action.setPayloadJson(mapper.writeValueAsString(DecisionPurchaseContractCodec.toPayload(10L,contract))); }
            catch (Exception invalid) { throw new AssertionError("合成合同编码失败",invalid); }
            snapshot = new DeliveryContextSnapshot(new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER,9001L,"WO-9001"),List.of(),List.of(),Map.of(),List.of(),List.of(),List.of(),Map.of(),List.of(),Map.of(),List.of(),"hash-old",LocalDateTime.of(2026,10,6,10,0),List.of());
            store =
                    mock(
                            DecisionPersistenceService.class,
                            invocation -> {
                                var args = invocation.getArguments();
                                switch (invocation.getMethod().getName()) {
                                    case "requireRun":
                                        return run;
                                    case "requireAction":
                                        return action;
                                    case "listActions":
                                        return List.of(action);
                                    case "trustedExecutionActor", "trustedActor":
                                        return actor;
                                    case "requireSnapshot":
                                        return snapshot;
                                    case "transition":
                                        states.requireTransition(
                                                (DecisionRunStatus) args[1],
                                                (DecisionRunStatus) args[2]);
                                        if (!run.getStatus()
                                                .equals(((DecisionRunStatus) args[1]).name()))
                                            throw new ServiceException("模拟 master 状态 CAS 冲突");
                                        run.setStatus(((DecisionRunStatus) args[2]).name());
                                        run.setVersion(run.getVersion() + 1);
                                        return null;
                                    case "transitionAction":
                                        if (!Objects.equals(action.getExecutionStatus(), args[2]))
                                            throw new ServiceException("模拟动作状态 CAS 冲突");
                                        action.setExecutionStatus((String) args[3]);
                                        action.setVersion(action.getVersion() + 1);
                                        return null;
                                    case "saveActionResult":
                                        if (masterResultUnavailable)
                                            throw new ServiceException("ERP 已提交而 master 回写未知");
                                        save((DecisionActionExecutionResult) args[1]);
                                        return null;
                                    case "saveRecoveredPurchaseResult":
                                        save((DecisionActionExecutionResult) args[2]);
                                        return null;
                                    case "finishRecoveredExecution":
                                        if (!finishCas
                                                || !"SUCCEEDED".equals(action.getExecutionStatus()))
                                            return false;
                                        run.setStatus("SUCCEEDED");
                                        run.setVersion(run.getVersion() + 1);
                                        return true;
                                    case "fail":
                                        states.requireTransition(
                                                DecisionRunStatus.valueOf(run.getStatus()),
                                                DecisionRunStatus.FAILED);
                                        run.setStatus("FAILED");
                                        return null;
                                    case "saveOutcome":
                                        run.setOutcomeJson("已持久化复盘");
                                        return null;
                                    default:
                                        return Answers.RETURNS_DEFAULTS.answer(invocation);
                                }
                            });
            when(rights.getMenuPermission(1L)).thenReturn(actor.permissions());when(port.canAccess(any(),any())).thenReturn(true);
            when(port.checkFreshness(any(),anyString(),any())).thenReturn(new DecisionFreshnessResult(true,"hash-old",List.of()));
            when(executor.type()).thenReturn(DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT);
            when(executor.verify(any(),any(),any())).thenAnswer(c->c.getArgument(1));
            when(outcomes.measure(any(),any())).thenReturn(new DecisionOutcome(null,null,null,Map.of(),List.of(),List.of(),LocalDateTime.of(2026,10,6,10,0),"PROJECTED_ONLY"));
            finalizer=new DecisionFinalizeService(store,outcomes,events);
            execution=new DecisionActionExecutionService(store,List.of(executor),mapper,events,rights,port,finalizer);
            support=new DecisionNodeSupport(store,port,mapper,events,rights);
            ctx.setTenantId("000000");ctx.setNodeType("industrial.executeApprovedActions");ctx.setNodeId("execute");
            ctx.setState(new FlowState(mapper.createObjectNode().put("runId","10")));
        }
        void nonPurchase() {
            action.setActionType("CREATE_SCHEDULE_PLAN_DRAFT");action.setExecutorCode("schedulePlanDraft");action.setPermissionCode("prd:schedule:run");
            when(executor.type()).thenReturn(DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT);
            execution=new DecisionActionExecutionService(store,List.of(executor),mapper,events,rights,port,finalizer);
        }

        JsonNode node() {
            return inTenant(
                    () ->
                            new DecisionExecuteApprovedActionsNodeProvider(
                                            support, execution, store, finalizer)
                                    .execute(ctx));
        }

        <T> T inTenant(Supplier<T> body) {
            try (var spring = mockStatic(SpringUtil.class);
                    var login = mockStatic(LoginHelper.class)) {
                spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
                login.when(LoginHelper::isLogin).thenReturn(false);
                return body.get();
            }
        }

        DecisionActionProposal proposal(){try {
                return new DecisionActionProposal(
                        20L,
                        DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
                        "合成采购",
                        "冻结来源",
                        snapshot.rootBusinessRef(),
                        mapper.readValue(
                                action.getPayloadJson(),
                                new TypeReference<Map<String, Object>>() {}),
                        DecisionRiskLevel.MEDIUM,
                        true,
                        "pu:requisition:add",
                        action.getIdempotencyKey(),
                        "purchaseRequisitionDraft",
                        1,
                        List.of());
            } catch (Exception invalid) {
                throw new AssertionError("合成动作解析失败",invalid);}}
        DecisionActionExecutionResult receipt(boolean replayed){var a=proposal();var c=DecisionPurchaseContractCodec.parse(a.payload(),10L);var l=c.frozenLines().get(0);
            var fields=Map.<String,Object>ofEntries(Map.entry("purchase_payload_version",2),Map.entry("runId","10"),Map.entry("actionId","20"),Map.entry("tenantId",actor.tenantId()),
                Map.entry("requestKey",a.idempotencyKey()),Map.entry("materialId","139"),Map.entry("vendorId","17"),Map.entry("sourceType","AI_DECISION"),
                Map.entry("sourceFingerprint",c.expectedSourceFingerprint()),Map.entry("payloadFingerprint",DecisionPurchaseContractCodec.payloadFingerprint(a,actor)),
                Map.entry("status","DRAFT"),Map.entry("versionMarker","v0"),Map.entry("beforeState",replayed?"COMMITTED":"ABSENT"),Map.entry("afterState","DRAFT"),Map.entry("replayed",replayed),
                Map.entry("lines",List.of(Map.of("lineId","801","requirementDetailId",l.detailId().toString(),"materialId","139","baseUomId","3","orderUomId","3","coveredDemandBase","5","requestedOrderQty","5","requiredDate",l.requiredDate().toString()))));
            return new DecisionActionExecutionResult(true,false,"PU_PURCHASE_REQUISITION",99L,"REQ-99",fields,null,null);}
        void save(DecisionActionExecutionResult r){action.setExecutionStatus(r.success()?"SUCCEEDED":"FAILED");action.setResultBizType(r.resultBizType());action.setResultBizId(r.resultBizId());action.setResultBizCode(r.resultBizCode());
            try{action.setResultJson(mapper.writeValueAsString(r));}catch(Exception invalid){throw new AssertionError("合成结果编码失败",invalid);}action.setVersion(action.getVersion()+1);}
        void noTerminalOrCompensation(){verify(store,never()).fail(any(),any(),any());verify(executor,never()).compensate(any(),any(),any());}
    }
}
