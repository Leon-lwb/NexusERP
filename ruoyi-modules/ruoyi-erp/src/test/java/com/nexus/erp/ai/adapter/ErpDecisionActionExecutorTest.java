package com.nexus.erp.ai.adapter;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.nexus.common.ai.decision.dto.DecisionActionExecutionResult;
import com.nexus.common.ai.decision.dto.DecisionActionProposal;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.enums.DecisionRiskLevel;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.prd.domain.PrdSchedulePlan;
import com.nexus.erp.prd.domain.bo.PrdSchedulePlanBo;
import com.nexus.erp.prd.domain.vo.PrdSchedulePlanVo;
import com.nexus.erp.prd.mapper.PrdSchedulePlanMapper;
import com.nexus.erp.prd.service.IPrdScheduleService;
import com.nexus.erp.pu.domain.PuPurchaseRequisitionLine;
import com.nexus.erp.pu.domain.bo.PuPurchaseRequisitionBo;
import com.nexus.erp.pu.domain.vo.PuPurchaseRequisitionVo;
import com.nexus.erp.pu.service.IPuPurchaseRequisitionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** ERP 决策动作执行、验证与条件补偿测试。 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ErpDecisionActionExecutorTest {

    private static final long CREATE_MILLIS = 1_795_622_400_000L;
    private static final long UPDATE_MILLIS = 1_795_622_460_000L;
    private static final String PURCHASE_BIZ_TYPE = "PU_PURCHASE_REQUISITION";
    private static final String SCHEDULE_BIZ_TYPE = "PRD_SCHEDULE_PLAN";

    @Mock
    private IPuPurchaseRequisitionService purchaseRequisitionService;
    @Mock
    private IPrdScheduleService scheduleService;
    @Mock
    private PrdSchedulePlanMapper planMapper;

    @InjectMocks
    private ErpDecisionActionExecutor executor;

    @Test
    void executePurchaseDraftBuildsStrictBusinessBoAndReturnsRereadMarker() {
        DecisionActionProposal action = purchaseAction(purchasePayload());
        when(purchaseRequisitionService.createDraft(any(PuPurchaseRequisitionBo.class))).thenReturn(7001L);
        when(purchaseRequisitionService.selectById(7001L)).thenReturn(purchaseVo("DRAFT", UPDATE_MILLIS));

        DecisionActionExecutionResult result = executor.execute(action, purchaseActor());

        assertTrue(result.success());
        assertFalse(result.retryable());
        assertEquals(PURCHASE_BIZ_TYPE, result.resultBizType());
        assertEquals(7001L, result.resultBizId());
        assertEquals("PR-7001", result.resultBizCode());
        assertEquals("DRAFT", result.result().get("status"));
        assertEquals("U:" + UPDATE_MILLIS, result.result().get("versionMarker"));

        ArgumentCaptor<PuPurchaseRequisitionBo> bo = ArgumentCaptor.forClass(PuPurchaseRequisitionBo.class);
        verify(purchaseRequisitionService).createDraft(bo.capture());
        PuPurchaseRequisitionBo captured = bo.getValue();
        assertEquals("AI交付风险建议-SO-42", captured.getRequisitionName());
        assertEquals("AI_DECISION", captured.getSourceType());
        assertEquals(java.sql.Date.valueOf("2026-09-30"), captured.getRequiredDate());
        assertTrue(captured.getRemark().contains("runId=99"), captured.getRemark());
        assertTrue(captured.getRemark().contains("idempotencyKey=idem-purchase"), captured.getRemark());
        assertTrue(captured.getRequisitionDate() != null);
        assertEquals(1, captured.getPuPurchaseRequisitionLineList().size());
        PuPurchaseRequisitionLine line = captured.getPuPurchaseRequisitionLineList().get(0);
        assertEquals(101L, line.getMaterialId());
        assertEquals("M-101", line.getMaterialCode());
        assertEquals("物料101", line.getMaterialName());
        assertEquals(0, new BigDecimal("5.00").compareTo(line.getApplyQuantity()));
        assertEquals(0, new BigDecimal("5").compareTo(line.getShortageQuantity()));
        assertEquals(java.sql.Date.valueOf("2026-09-28"), line.getRequiredDate());
        assertEquals("AI_DECISION", line.getSourceType());
        assertEquals(99L, line.getSourceId());
        verifyNoInteractions(scheduleService, planMapper);
    }

    @Test
    void wildcardPermissionCanExecuteSupportedPurchaseAction() {
        DecisionActionProposal action = purchaseAction(purchasePayload());
        when(purchaseRequisitionService.createDraft(any(PuPurchaseRequisitionBo.class))).thenReturn(7001L);
        when(purchaseRequisitionService.selectById(7001L)).thenReturn(purchaseVo("DRAFT", UPDATE_MILLIS));

        DecisionActionExecutionResult result = executor.execute(action,
            new DecisionActor("000000", 7L, "admin", Set.of("*:*:*")));

        assertTrue(result.success());
    }

    @Test
    void executeScheduleDraftUsesExplicitIdsAndNeverPublishes() {
        DecisionActionProposal action = scheduleAction(schedulePayload("APS_CR_EXPEDITE", "CR"));
        PrdSchedulePlanVo created = new PrdSchedulePlanVo();
        created.setPlanId(8001L);
        created.setPlanNo("APS202609240001");
        created.setStatus("COMPLETED");
        when(scheduleService.runSchedule(any(PrdSchedulePlanBo.class), anyCollection())).thenReturn(created);
        when(planMapper.selectById(8001L)).thenReturn(schedulePlan("COMPLETED", UPDATE_MILLIS));

        DecisionActionExecutionResult result = executor.execute(action, scheduleActor());

        assertTrue(result.success());
        assertEquals(SCHEDULE_BIZ_TYPE, result.resultBizType());
        assertEquals(8001L, result.resultBizId());
        assertEquals("APS202609240001", result.resultBizCode());
        assertEquals("COMPLETED", result.result().get("status"));
        assertEquals("U:" + UPDATE_MILLIS, result.result().get("versionMarker"));
        assertEquals("APS_CR_EXPEDITE", result.result().get("scenarioCode"));

        ArgumentCaptor<PrdSchedulePlanBo> bo = ArgumentCaptor.forClass(PrdSchedulePlanBo.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Collection<Long>> ids = (ArgumentCaptor) ArgumentCaptor.forClass(Collection.class);
        verify(scheduleService).runSchedule(bo.capture(), ids.capture());
        assertEquals("AI交付风险方案-APS_CR_EXPEDITE", bo.getValue().getPlanName());
        assertEquals("CR", bo.getValue().getStrategy());
        assertEquals(java.sql.Date.valueOf("2026-09-24"), bo.getValue().getStartDate());
        assertEquals(30, bo.getValue().getHorizonDays());
        assertEquals(21L, bo.getValue().getWorkshopId());
        assertTrue(bo.getValue().getRemark().contains("runId=99"), bo.getValue().getRemark());
        assertTrue(bo.getValue().getRemark().contains("scenario=APS_CR_EXPEDITE"), bo.getValue().getRemark());
        assertTrue(bo.getValue().getRemark().contains("idempotencyKey=idem-schedule"), bo.getValue().getRemark());
        assertEquals(List.of(11L, 12L), List.copyOf(ids.getValue()));
        verify(scheduleService, never()).runSchedule(any(PrdSchedulePlanBo.class));
        verify(scheduleService, never()).publish(any());
        verify(scheduleService, never()).deleteByIds(any(Long[].class));
        verify(purchaseRequisitionService, never()).deleteByIds(any(Long[].class));
    }

    @Test
    void purchasePayloadRejectsUnknownFieldsBeforeDelegating() {
        Map<String, Object> unknownHeader = new LinkedHashMap<>(purchasePayload());
        unknownHeader.put("freeForm", "must-not-persist");
        Map<String, Object> unknownLine = new LinkedHashMap<>(purchaseLine());
        unknownLine.put("remark", "must-not-persist");
        Map<String, Object> payloadWithUnknownLine = purchasePayloadWithLine(unknownLine);

        assertThrows(ServiceException.class,
            () -> executor.execute(purchaseAction(unknownHeader), purchaseActor()));
        assertThrows(ServiceException.class,
            () -> executor.execute(purchaseAction(payloadWithUnknownLine), purchaseActor()));

        verifyNoInteractions(purchaseRequisitionService, scheduleService, planMapper);
    }

    @Test
    void purchasePayloadRejectsInvalidDecimalQuantityAndDatesBeforeDelegating() {
        Map<String, Object> zeroRun = new LinkedHashMap<>(purchasePayload());
        zeroRun.put("runId", "0");
        Map<String, Object> zeroMaterial = new LinkedHashMap<>(purchaseLine());
        zeroMaterial.put("materialId", "0");
        Map<String, Object> exponentQuantity = new LinkedHashMap<>(purchaseLine());
        exponentQuantity.put("quantity", "1e3");
        Map<String, Object> mismatchedGap = new LinkedHashMap<>(purchaseLine());
        mismatchedGap.put("quantity", "5");
        mismatchedGap.put("gap", "6");
        Map<String, Object> noQuantity = new LinkedHashMap<>(purchaseLine());
        noQuantity.remove("quantity");
        noQuantity.remove("gap");
        Map<String, Object> invalidHeaderDate = new LinkedHashMap<>(purchasePayload());
        invalidHeaderDate.put("requiredDate", "2026-02-30");
        Map<String, Object> invalidLineDate = new LinkedHashMap<>(purchaseLine());
        invalidLineDate.put("requiredDate", "2026-02-30");

        assertThrows(ServiceException.class, () -> executor.execute(purchaseAction(zeroRun), purchaseActor()));
        assertThrows(ServiceException.class,
            () -> executor.execute(purchaseAction(purchasePayloadWithLine(zeroMaterial)), purchaseActor()));
        assertThrows(ServiceException.class,
            () -> executor.execute(purchaseAction(purchasePayloadWithLine(exponentQuantity)), purchaseActor()));
        assertThrows(ServiceException.class,
            () -> executor.execute(purchaseAction(purchasePayloadWithLine(mismatchedGap)), purchaseActor()));
        assertThrows(ServiceException.class,
            () -> executor.execute(purchaseAction(purchasePayloadWithLine(noQuantity)), purchaseActor()));
        assertThrows(ServiceException.class,
            () -> executor.execute(purchaseAction(invalidHeaderDate), purchaseActor()));
        assertThrows(ServiceException.class,
            () -> executor.execute(purchaseAction(purchasePayloadWithLine(invalidLineDate)), purchaseActor()));

        verifyNoInteractions(purchaseRequisitionService, scheduleService, planMapper);
    }

    @Test
    void schedulePayloadRejectsDuplicatesStrategyConflictBaselineAndUnknownFields() {
        Map<String, Object> duplicates = new LinkedHashMap<>(schedulePayload("APS_EDD", "EDD"));
        duplicates.put("workOrderIds", List.of("11", "11"));
        Map<String, Object> conflict = schedulePayload("APS_EDD", "CR");
        Map<String, Object> baseline = schedulePayload("BASELINE_CURRENT", null);
        Map<String, Object> current = schedulePayload("CURRENT", null);
        Map<String, Object> unknown = new LinkedHashMap<>(schedulePayload("APS_EDD", "EDD"));
        unknown.put("freeForm", true);
        Map<String, Object> invalidHorizon = new LinkedHashMap<>(schedulePayload("APS_EDD", "EDD"));
        invalidHorizon.put("horizonDays", 91);
        Map<String, Object> invalidDate = new LinkedHashMap<>(schedulePayload("APS_EDD", "EDD"));
        invalidDate.put("startDate", "2026-02-30");

        assertThrows(ServiceException.class, () -> executor.execute(scheduleAction(duplicates), scheduleActor()));
        assertThrows(ServiceException.class, () -> executor.execute(scheduleAction(conflict), scheduleActor()));
        assertThrows(ServiceException.class, () -> executor.execute(scheduleAction(baseline), scheduleActor()));
        assertThrows(ServiceException.class, () -> executor.execute(scheduleAction(current), scheduleActor()));
        assertThrows(ServiceException.class, () -> executor.execute(scheduleAction(unknown), scheduleActor()));
        assertThrows(ServiceException.class, () -> executor.execute(scheduleAction(invalidHorizon), scheduleActor()));
        assertThrows(ServiceException.class, () -> executor.execute(scheduleAction(invalidDate), scheduleActor()));

        verifyNoInteractions(purchaseRequisitionService, scheduleService, planMapper);
    }

    @Test
    void actorAndActionMetadataTamperingFailsBeforeAnyDelegate() {
        DecisionActionProposal valid = purchaseAction(purchasePayload());
        DecisionActor blankTenant = new DecisionActor(" ", 7L, "tester", Set.of("pu:requisition:add"));
        DecisionActor invalidUser = new DecisionActor("000000", 0L, "tester", Set.of("pu:requisition:add"));
        DecisionActor nullPermissions = new DecisionActor("000000", 7L, "tester", null);
        DecisionActor emptyPermissions = new DecisionActor("000000", 7L, "tester", Set.of());
        DecisionActor missingPermission = new DecisionActor("000000", 7L, "tester", Set.of("prd:schedule:run"));
        DecisionActionProposal unsupported = action(DecisionActionType.SEND_EXPEDITE_NOTICE, purchasePayload(),
            "ai:decision:execute", "sendExpediteNotice", target(), "idem");
        DecisionActionProposal tamperedPermission = action(DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
            purchasePayload(), "prd:schedule:run", "purchaseRequisitionDraft", target(), "idem");
        DecisionActionProposal tamperedExecutor = action(DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
            purchasePayload(), "pu:requisition:add", "otherExecutor", target(), "idem");
        DecisionActionProposal nullPayload = action(DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
            null, "pu:requisition:add", "purchaseRequisitionDraft", target(), "idem");
        DecisionActionProposal blankIdempotency = action(DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
            purchasePayload(), "pu:requisition:add", "purchaseRequisitionDraft", target(), " ");
        DecisionActionProposal nullTarget = action(DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
            purchasePayload(), "pu:requisition:add", "purchaseRequisitionDraft", null, "idem");

        assertThrows(ServiceException.class, () -> executor.execute(null, purchaseActor()));
        assertThrows(ServiceException.class, () -> executor.execute(valid, null));
        assertThrows(ServiceException.class, () -> executor.execute(valid, blankTenant));
        assertThrows(ServiceException.class, () -> executor.execute(valid, invalidUser));
        assertThrows(ServiceException.class, () -> executor.execute(valid, nullPermissions));
        assertThrows(ServiceException.class, () -> executor.execute(valid, emptyPermissions));
        assertThrows(ServiceException.class, () -> executor.execute(valid, missingPermission));
        assertThrows(ServiceException.class, () -> executor.execute(unsupported,
            new DecisionActor("000000", 7L, "tester", Set.of("ai:decision:execute"))));
        assertThrows(ServiceException.class, () -> executor.execute(tamperedPermission, purchaseActor()));
        assertThrows(ServiceException.class, () -> executor.execute(tamperedExecutor, purchaseActor()));
        assertThrows(ServiceException.class, () -> executor.execute(nullPayload, purchaseActor()));
        assertThrows(ServiceException.class, () -> executor.execute(blankIdempotency, purchaseActor()));
        assertThrows(ServiceException.class, () -> executor.execute(nullTarget, purchaseActor()));

        verifyNoInteractions(purchaseRequisitionService, scheduleService, planMapper);
    }

    @Test
    void executePurchaseFailsWhenRereadIsMissingOrNotDraft() {
        DecisionActionProposal action = purchaseAction(purchasePayload());
        PuPurchaseRequisitionVo submitted = purchaseVo("SUBMITTED", UPDATE_MILLIS);
        when(purchaseRequisitionService.createDraft(any(PuPurchaseRequisitionBo.class))).thenReturn(7001L);
        when(purchaseRequisitionService.selectById(7001L)).thenReturn(null, submitted);

        assertThrows(ServiceException.class, () -> executor.execute(action, purchaseActor()));
        assertThrows(ServiceException.class, () -> executor.execute(action, purchaseActor()));

        verify(purchaseRequisitionService, never()).deleteByIds(any(Long[].class));
    }

    @Test
    void executeScheduleFailsWhenRereadIsMissingOrPublished() {
        DecisionActionProposal action = scheduleAction(schedulePayload("APS_EDD", "EDD"));
        PrdSchedulePlanVo created = new PrdSchedulePlanVo();
        created.setPlanId(8001L);
        when(scheduleService.runSchedule(any(PrdSchedulePlanBo.class), anyCollection())).thenReturn(created);
        when(planMapper.selectById(8001L)).thenReturn(null, schedulePlan("PUBLISHED", UPDATE_MILLIS));

        assertThrows(ServiceException.class, () -> executor.execute(action, scheduleActor()));
        assertThrows(ServiceException.class, () -> executor.execute(action, scheduleActor()));

        verify(scheduleService, never()).publish(any());
        verify(scheduleService, never()).deleteByIds(any(Long[].class));
    }

    @Test
    void verifyPurchaseReturnsOriginalResultWhenStateAndMarkerMatch() {
        DecisionActionProposal action = purchaseAction(purchasePayload());
        DecisionActionExecutionResult original = purchaseResult("U:" + UPDATE_MILLIS);
        when(purchaseRequisitionService.selectById(7001L)).thenReturn(purchaseVo("DRAFT", UPDATE_MILLIS));

        DecisionActionExecutionResult verified = executor.verify(action, original, purchaseActor());

        assertSame(original, verified);
        assertEquals("U:" + UPDATE_MILLIS, verified.result().get("versionMarker"));
        verify(purchaseRequisitionService, never()).deleteByIds(any(Long[].class));
        verify(scheduleService, never()).publish(any());
    }

    @Test
    void verifyScheduleReturnsOriginalResultWhenStateAndMarkerMatch() {
        DecisionActionProposal action = scheduleAction(schedulePayload("APS_EDD", "EDD"));
        DecisionActionExecutionResult original = scheduleResult("C:" + CREATE_MILLIS);
        PrdSchedulePlan plan = schedulePlan("COMPLETED", null);
        plan.setUpdateTime(null);
        plan.setCreateTime(new Date(CREATE_MILLIS));
        when(planMapper.selectById(8001L)).thenReturn(plan);

        DecisionActionExecutionResult verified = executor.verify(action, original, scheduleActor());

        assertSame(original, verified);
        assertEquals("C:" + CREATE_MILLIS, verified.result().get("versionMarker"));
        verify(scheduleService, never()).publish(any());
        verify(scheduleService, never()).deleteByIds(any(Long[].class));
    }

    @Test
    void verifyMarkerChangeReturnsStableConflictWithoutReplacingOriginalMarker() {
        DecisionActionProposal action = purchaseAction(purchasePayload());
        DecisionActionExecutionResult original = purchaseResult("U:" + UPDATE_MILLIS);
        when(purchaseRequisitionService.selectById(7001L))
            .thenReturn(purchaseVo("DRAFT", UPDATE_MILLIS + 1));

        DecisionActionExecutionResult verified = executor.verify(action, original, purchaseActor());

        assertFalse(verified.success());
        assertFalse(verified.retryable());
        assertEquals("VERIFY_CONFLICT", verified.errorCode());
        assertEquals("U:" + UPDATE_MILLIS, verified.result().get("versionMarker"));
    }

    @Test
    void verifyMissingAndWrongStateReturnStableCodes() {
        DecisionActionProposal action = purchaseAction(purchasePayload());
        DecisionActionExecutionResult original = purchaseResult("U:" + UPDATE_MILLIS);
        when(purchaseRequisitionService.selectById(7001L))
            .thenReturn(null, purchaseVo("SUBMITTED", UPDATE_MILLIS));

        DecisionActionExecutionResult missing = executor.verify(action, original, purchaseActor());
        DecisionActionExecutionResult wrongState = executor.verify(action, original, purchaseActor());

        assertEquals("VERIFY_NOT_FOUND", missing.errorCode());
        assertEquals("VERIFY_STATE_MISMATCH", wrongState.errorCode());
        assertFalse(missing.success());
        assertFalse(wrongState.success());
    }

    @Test
    void verifyRejectsMismatchedResultTypeBeforeReadingBusinessData() {
        DecisionActionProposal action = purchaseAction(purchasePayload());
        DecisionActionExecutionResult mismatched = new DecisionActionExecutionResult(true, false,
            SCHEDULE_BIZ_TYPE, 7001L, "PR-7001", Map.of("status", "DRAFT",
            "versionMarker", "U:" + UPDATE_MILLIS), null, null);

        assertThrows(ServiceException.class, () -> executor.verify(action, mismatched, purchaseActor()));

        verifyNoInteractions(purchaseRequisitionService, scheduleService, planMapper);
    }

    @Test
    void compensatePurchaseSuccessUsesOnlyConditionalDeleteAndPreservesMarker() {
        DecisionActionProposal action = purchaseAction(purchasePayload());
        DecisionActionExecutionResult original = purchaseResult("U:" + UPDATE_MILLIS);
        when(purchaseRequisitionService.deleteDraftIfUnchanged(7001L, "U:" + UPDATE_MILLIS))
            .thenReturn(true);

        DecisionActionExecutionResult compensated = executor.compensate(action, original, purchaseActor());

        assertTrue(compensated.success());
        assertEquals("COMPENSATED", compensated.result().get("status"));
        assertEquals("U:" + UPDATE_MILLIS, compensated.result().get("versionMarker"));
        verify(purchaseRequisitionService).deleteDraftIfUnchanged(7001L, "U:" + UPDATE_MILLIS);
        verify(purchaseRequisitionService, never()).deleteByIds(any(Long[].class));
        verify(scheduleService, never()).publish(any());
        verifyNoInteractions(planMapper);
    }

    @Test
    void compensateScheduleRefusalReturnsStableNonRetryableFailure() {
        DecisionActionProposal action = scheduleAction(schedulePayload("APS_EDD", "EDD"));
        DecisionActionExecutionResult original = scheduleResult("U:" + UPDATE_MILLIS);
        when(scheduleService.deleteCompletedIfUnchanged(8001L, "U:" + UPDATE_MILLIS)).thenReturn(false);

        DecisionActionExecutionResult compensated = executor.compensate(action, original, scheduleActor());

        assertFalse(compensated.success());
        assertFalse(compensated.retryable());
        assertEquals("COMPENSATION_NOT_ALLOWED", compensated.errorCode());
        assertEquals("U:" + UPDATE_MILLIS, compensated.result().get("versionMarker"));
        verify(scheduleService).deleteCompletedIfUnchanged(8001L, "U:" + UPDATE_MILLIS);
        verify(scheduleService, never()).deleteByIds(any(Long[].class));
        verify(scheduleService, never()).publish(any());
        verify(purchaseRequisitionService, never()).deleteByIds(any(Long[].class));
        verify(planMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void verifyAndCompensateValidateTamperedMetadataBeforeDelegating() {
        DecisionActionProposal tampered = action(DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
            purchasePayload(), "pu:requisition:add", "tampered", target(), "idem");
        DecisionActionExecutionResult original = purchaseResult("U:" + UPDATE_MILLIS);

        assertThrows(ServiceException.class, () -> executor.verify(tampered, original, purchaseActor()));
        assertThrows(ServiceException.class, () -> executor.compensate(tampered, original, purchaseActor()));

        verifyNoInteractions(purchaseRequisitionService, scheduleService, planMapper);
    }

    private DecisionActor purchaseActor() {
        return new DecisionActor("000000", 7L, "tester", Set.of("pu:requisition:add"));
    }

    private DecisionActor scheduleActor() {
        return new DecisionActor("000000", 7L, "tester", Set.of("prd:schedule:run"));
    }

    private DecisionBusinessRef target() {
        return new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER, 42L, "SO-42");
    }

    private DecisionActionProposal purchaseAction(Map<String, Object> payload) {
        return action(DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT, payload,
            "pu:requisition:add", "purchaseRequisitionDraft", target(), "idem-purchase");
    }

    private DecisionActionProposal scheduleAction(Map<String, Object> payload) {
        return action(DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT, payload,
            "prd:schedule:run", "schedulePlanDraft", target(), "idem-schedule");
    }

    private DecisionActionProposal action(DecisionActionType type, Map<String, Object> payload,
                                          String permissionCode, String executorCode,
                                          DecisionBusinessRef target, String idempotencyKey) {
        return new DecisionActionProposal(1L, type, "动作", "原因", target, payload,
            DecisionRiskLevel.HIGH, true, permissionCode, idempotencyKey, executorCode, 1, List.of());
    }

    private Map<String, Object> purchasePayload() {
        return purchasePayloadWithLine(purchaseLine());
    }

    private Map<String, Object> purchasePayloadWithLine(Map<String, Object> line) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", "99");
        payload.put("requiredDate", "2026-09-30");
        payload.put("materialLines", List.of(line));
        return payload;
    }

    private Map<String, Object> purchaseLine() {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("materialId", "101");
        line.put("materialCode", "M-101");
        line.put("materialName", "物料101");
        line.put("quantity", "5.00");
        line.put("gap", "5");
        line.put("requiredDate", "2026-09-28");
        return line;
    }

    private Map<String, Object> schedulePayload(String scenarioCode, String strategy) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", "99");
        payload.put("scenarioCode", scenarioCode);
        if (strategy != null) {
            payload.put("strategy", strategy);
        }
        payload.put("startDate", "2026-09-24");
        payload.put("horizonDays", 30);
        payload.put("workOrderIds", List.of("11", "12"));
        payload.put("workshopId", "21");
        return payload;
    }

    private PuPurchaseRequisitionVo purchaseVo(String status, long updateMillis) {
        PuPurchaseRequisitionVo vo = new PuPurchaseRequisitionVo();
        vo.setRequisitionId(7001L);
        vo.setRequisitionNo("PR-7001");
        vo.setStatus(status);
        vo.setCreateTime(new Date(CREATE_MILLIS));
        vo.setUpdateTime(new Date(updateMillis));
        return vo;
    }

    private PrdSchedulePlan schedulePlan(String status, Long updateMillis) {
        PrdSchedulePlan plan = new PrdSchedulePlan();
        plan.setPlanId(8001L);
        plan.setPlanNo("APS202609240001");
        plan.setStatus(status);
        plan.setCreateTime(new Date(CREATE_MILLIS));
        plan.setUpdateTime(updateMillis == null ? null : new Date(updateMillis));
        return plan;
    }

    private DecisionActionExecutionResult purchaseResult(String marker) {
        return new DecisionActionExecutionResult(true, false, PURCHASE_BIZ_TYPE, 7001L, "PR-7001",
            Map.of("status", "DRAFT", "versionMarker", marker), null, null);
    }

    private DecisionActionExecutionResult scheduleResult(String marker) {
        return new DecisionActionExecutionResult(true, false, SCHEDULE_BIZ_TYPE, 8001L,
            "APS202609240001", Map.of("status", "COMPLETED", "versionMarker", marker,
            "scenarioCode", "APS_EDD"), null, null);
    }
}
