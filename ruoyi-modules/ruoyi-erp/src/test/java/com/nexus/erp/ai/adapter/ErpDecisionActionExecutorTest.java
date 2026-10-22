package com.nexus.erp.ai.adapter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** ERP 决策动作结构化幂等、验证与原子补偿测试。 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ErpDecisionActionExecutorTest {

    private static final long RUN_ID = 99L;
    private static final long PURCHASE_ACTION_ID = 501L;
    private static final long SCHEDULE_ACTION_ID = 502L;
    private static final String PURCHASE_KEY = "idem-purchase-501";
    private static final String SCHEDULE_KEY = "idem-schedule-502";
    private static final String PURCHASE_BIZ_TYPE = "PU_PURCHASE_REQUISITION";
    private static final String SCHEDULE_BIZ_TYPE = "PRD_SCHEDULE_PLAN";
    private static final String WORK_ORDER_IDS_JSON = "[\"11\",\"12\"]";

    @Mock
    private ErpDecisionPurchaseGate purchaseGate;
    @Mock
    private IPuPurchaseRequisitionService purchaseRequisitionService;
    @Mock
    private IPrdScheduleService scheduleService;
    @Mock
    private PrdSchedulePlanMapper planMapper;
    @Mock
    private DecisionPurchaseDraftEnricher purchaseEnricher;

    @BeforeEach
    void permitIndependentlyTestedMetadataInExecutorUnitTests() {
        Mockito.lenient().when(purchaseEnricher.matchesPersisted(any(), any())).thenReturn(true);
    }

    @InjectMocks
    private ErpDecisionActionExecutor executor;

    @BeforeAll
    static void initPlanTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PrdSchedulePlan.class);
    }

    @Test
    void p0LegacyPurchaseIsRejectedByRealErpGateIncludingForgedV2Marker() {
        ErpDecisionPurchaseGate gate = new ErpDecisionPurchaseGate();
        var original = purchaseAction(purchasePayload());
        assertThrows(ServiceException.class, () -> gate.require(original, purchaseActor()));
        var forged = new LinkedHashMap<>(purchasePayload());
        forged.put("purchase_payload_version", 2);
        assertThrows(ServiceException.class, () -> gate.require(purchaseAction(forged), purchaseActor()));
        verifyNoInteractions(purchaseRequisitionService);
    }

    @Test
    void defaultErpExecutorRefusesLegacyAndForgedPurchaseWithoutBusinessWrite() {
        var guarded = new ErpDecisionActionExecutor(purchaseRequisitionService, scheduleService,
            planMapper, purchaseEnricher, new ErpDecisionPurchaseGate());
        assertThrows(ServiceException.class,
            () -> guarded.execute(purchaseAction(purchasePayload()), purchaseActor()));
        var forged = new LinkedHashMap<>(purchasePayload());
        forged.put("purchase_payload_version", 2);
        assertThrows(ServiceException.class,
            () -> guarded.execute(purchaseAction(forged), purchaseActor()));
        verifyNoInteractions(purchaseRequisitionService, purchaseEnricher);
    }
    @Test
    void directErpExecuteRequiresSourceGateBeforeCreatingPurchaseDraft() {
        var action = purchaseAction(purchasePayload());
        var actor = purchaseActor();
        Mockito.doThrow(new ServiceException("采购来源未核验")).when(purchaseGate).require(action, actor);
        assertThrows(ServiceException.class, () -> executor.execute(action, actor));
        verify(purchaseGate).require(action, actor);
        verifyNoInteractions(purchaseRequisitionService, purchaseEnricher);
    }
    @Test
    void frozenIntentWithoutDisplayNamesAcceptsServerEnrichedNamesButNotChangedQuantities() {
        var payload=new LinkedHashMap<>(purchasePayload());
        var line=new LinkedHashMap<>((Map<String,Object>)((List<?>)payload.get("materialLines")).get(0));
        line.remove("materialCode");line.remove("materialName");payload.put("materialLines",List.of(line));
        var action=purchaseAction(payload);
        when(purchaseRequisitionService.createDraft(any())).thenReturn(7001L);
        when(purchaseRequisitionService.selectByIdForDecision(7001L)).thenReturn(purchaseVo());
        assertTrue(executor.execute(action,purchaseActor()).success());
        var altered=purchaseVo();altered.getPuPurchaseRequisitionLineList().get(0).setApplyQuantity(new BigDecimal("6"));
        when(purchaseRequisitionService.selectById(7001L)).thenReturn(altered);
        assertFalse(executor.verify(action,purchaseResult(7001L,"PR-7001","V:0","99","501"),purchaseActor()).success());
    }

    @Test
    void independentlyInvalidSupplementalMetadataCannotPassTheFrozenBusinessIntent() {
        when(purchaseRequisitionService.selectById(7001L)).thenReturn(purchaseVo());
        when(purchaseEnricher.matchesPersisted(any(),any())).thenReturn(false);
        assertFalse(executor.verify(purchaseAction(purchasePayload()),
            purchaseResult(7001L,"PR-7001","V:0","99","501"),purchaseActor()).success());
    }

    @Test
    void executeIsPublicTransactionalRollbackForException() throws Exception {
        Transactional transactional = ErpDecisionActionExecutor.class
            .getMethod("execute", DecisionActionProposal.class, DecisionActor.class)
            .getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertTrue(List.of(transactional.rollbackFor()).contains(Exception.class));
    }

    @Test
    void executePurchasePopulatesStructuredProvenanceActorAndVersionResult() {
        DecisionActionProposal action = purchaseAction(purchasePayload());
        when(purchaseRequisitionService.createDraft(any(PuPurchaseRequisitionBo.class))).thenReturn(7001L);
        when(purchaseRequisitionService.selectByIdForDecision(7001L)).thenReturn(purchaseVo());

        DecisionActionExecutionResult result = executor.execute(action, purchaseActor());

        assertTrue(result.success());
        assertEquals(PURCHASE_BIZ_TYPE, result.resultBizType());
        assertEquals(7001L, result.resultBizId());
        assertEquals("PR-7001", result.resultBizCode());
        assertEquals("DRAFT", result.result().get("status"));
        assertEquals("V:0", result.result().get("versionMarker"));
        assertEquals("99", result.result().get("runId"));
        assertEquals("501", result.result().get("actionId"));

        ArgumentCaptor<PuPurchaseRequisitionBo> bo = ArgumentCaptor.forClass(PuPurchaseRequisitionBo.class);
        verify(purchaseRequisitionService).createDraft(bo.capture());
        assertEquals(RUN_ID, bo.getValue().getDecisionRunId());
        assertEquals(PURCHASE_ACTION_ID, bo.getValue().getDecisionActionId());
        assertEquals(PURCHASE_KEY, bo.getValue().getDecisionIdempotencyKey());
        assertEquals(0, bo.getValue().getVersion());
        assertEquals(7L, bo.getValue().getCreateBy());
        assertEquals(7L, bo.getValue().getUpdateBy());
        assertEquals(7L, bo.getValue().getApplicantId());
        assertEquals("tester", bo.getValue().getApplicantName());
        assertEquals(Date.valueOf("2026-09-30"), bo.getValue().getRequiredDate());
        PuPurchaseRequisitionLine line = bo.getValue().getPuPurchaseRequisitionLineList().get(0);
        assertEquals(RUN_ID, line.getSourceId());
        assertEquals("AI_DECISION", line.getSourceType());
    }

    @Test
    void executeSchedulePersistsStableStructuredScopeAndVersionResult() {
        DecisionActionProposal action = scheduleAction(schedulePayload("APS_CR_EXPEDITE", "CR"));
        PrdSchedulePlanVo created = new PrdSchedulePlanVo();
        created.setPlanId(8001L);
        when(scheduleService.runSchedule(any(PrdSchedulePlanBo.class), anyCollection())).thenReturn(created);
        when(planMapper.selectOne(any(Wrapper.class))).thenReturn(schedulePlan("APS_CR_EXPEDITE", "CR"));

        DecisionActionExecutionResult result = executor.execute(action, scheduleActor());

        assertTrue(result.success());
        assertEquals(SCHEDULE_BIZ_TYPE, result.resultBizType());
        assertEquals("V:0", result.result().get("versionMarker"));
        assertEquals("99", result.result().get("runId"));
        assertEquals("502", result.result().get("actionId"));
        assertEquals("APS_CR_EXPEDITE", result.result().get("scenarioCode"));
        ArgumentCaptor<PrdSchedulePlanBo> bo = ArgumentCaptor.forClass(PrdSchedulePlanBo.class);
        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Collection<Long>> ids = (ArgumentCaptor) ArgumentCaptor.forClass(Collection.class);
        verify(scheduleService).runSchedule(bo.capture(), ids.capture());
        assertEquals(RUN_ID, bo.getValue().getDecisionRunId());
        assertEquals(SCHEDULE_ACTION_ID, bo.getValue().getDecisionActionId());
        assertEquals(SCHEDULE_KEY, bo.getValue().getDecisionIdempotencyKey());
        assertEquals("APS_CR_EXPEDITE", bo.getValue().getDecisionScenarioCode());
        assertEquals(WORK_ORDER_IDS_JSON, bo.getValue().getDecisionWorkOrderIdsJson());
        assertEquals(0, bo.getValue().getVersion());
        assertEquals(7L, bo.getValue().getCreateBy());
        assertEquals(7L, bo.getValue().getUpdateBy());
        assertEquals(List.of(11L, 12L), List.copyOf(ids.getValue()));
        verify(scheduleService, never()).publish(any());
    }

    @Test
    void executePurchasePostCreateUsesBeanCurrentReadInsteadOfOldSnapshot() {
        when(purchaseRequisitionService.createDraft(any(PuPurchaseRequisitionBo.class))).thenReturn(7001L);
        when(purchaseRequisitionService.selectByIdForDecision(7001L)).thenReturn(purchaseVo());

        assertTrue(assertDoesNotThrow(() -> executor.execute(
            purchaseAction(purchasePayload()), purchaseActor())).success());
        verify(purchaseRequisitionService).selectByIdForDecision(7001L);
        verify(purchaseRequisitionService, never()).selectById(7001L);
    }

    @Test
    void executeSchedulePostCreateUsesCurrentReadInsteadOfOldSnapshot() {
        PrdSchedulePlanVo created = new PrdSchedulePlanVo();
        created.setPlanId(8001L);
        when(scheduleService.runSchedule(any(PrdSchedulePlanBo.class), anyCollection())).thenReturn(created);
        when(planMapper.selectOne(any(Wrapper.class))).thenAnswer(invocation -> {
            Wrapper<?> query = invocation.getArgument(0);
            return query.getSqlSegment().toUpperCase().contains("FOR UPDATE")
                ? schedulePlan("APS_EDD", "EDD") : null;
        });

        assertTrue(assertDoesNotThrow(() -> executor.execute(
            scheduleAction(schedulePayload("APS_EDD", "EDD")), scheduleActor())).success());
        ArgumentCaptor<Wrapper<PrdSchedulePlan>> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(planMapper).selectOne(query.capture());
        assertTrue(query.getValue().getSqlSegment().toUpperCase().contains("FOR UPDATE"));
    }

    @Test
    void sameKeyReplayReturnsSameBusinessId() {
        DecisionActionProposal action = purchaseAction(purchasePayload());
        when(purchaseRequisitionService.createDraft(any(PuPurchaseRequisitionBo.class))).thenReturn(7001L);
        when(purchaseRequisitionService.selectByIdForDecision(7001L)).thenReturn(purchaseVo());

        DecisionActionExecutionResult first = executor.execute(action, purchaseActor());
        DecisionActionExecutionResult replay = executor.execute(action, purchaseActor());

        assertEquals(first.resultBizId(), replay.resultBizId());
        assertEquals(first.resultBizCode(), replay.resultBizCode());
    }

    @Test
    void sameKeyDifferentPayloadConflictPropagatesWithoutFabricatingResult() {
        DecisionActionProposal action = purchaseAction(purchasePayload());
        when(purchaseRequisitionService.createDraft(any(PuPurchaseRequisitionBo.class)))
            .thenReturn(7001L)
            .thenThrow(new ServiceException("幂等键对应的采购申请来源或载荷不一致"));
        when(purchaseRequisitionService.selectByIdForDecision(7001L)).thenReturn(purchaseVo());

        assertTrue(executor.execute(action, purchaseActor()).success());
        assertThrows(ServiceException.class, () -> executor.execute(action, purchaseActor()));
    }

    @Test
    void actionIdApprovalMetadataAndKeyLengthFailBeforeDelegates() {
        DecisionActionProposal zeroActionId = action(0L, DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
            purchasePayload(), true, "pu:requisition:add", "purchaseRequisitionDraft", PURCHASE_KEY);
        DecisionActionProposal wrongApproval = action(PURCHASE_ACTION_ID,
            DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT, purchasePayload(), false,
            "pu:requisition:add", "purchaseRequisitionDraft", PURCHASE_KEY);
        DecisionActionProposal longKey = action(PURCHASE_ACTION_ID,
            DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT, purchasePayload(), true,
            "pu:requisition:add", "purchaseRequisitionDraft", "x".repeat(129));

        assertThrows(ServiceException.class, () -> executor.execute(zeroActionId, purchaseActor()));
        assertThrows(ServiceException.class, () -> executor.execute(wrongApproval, purchaseActor()));
        assertThrows(ServiceException.class, () -> executor.execute(longKey, purchaseActor()));

        verifyNoInteractions(purchaseRequisitionService, scheduleService, planMapper);
    }

    @Test
    void tenantUserPermissionAndActionMetadataTamperingFailBeforeDelegates() {
        DecisionActionProposal valid = purchaseAction(purchasePayload());
        DecisionActor blankTenant = new DecisionActor(" ", 7L, "tester", Set.of("pu:requisition:add"));
        DecisionActor invalidUser = new DecisionActor("000000", 0L, "tester", Set.of("pu:requisition:add"));
        DecisionActor missingPermission = new DecisionActor("000000", 7L, "tester", Set.of("prd:schedule:run"));
        DecisionActionProposal wrongPermission = action(PURCHASE_ACTION_ID,
            DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT, purchasePayload(), true,
            "prd:schedule:run", "purchaseRequisitionDraft", PURCHASE_KEY);
        DecisionActionProposal wrongExecutor = action(PURCHASE_ACTION_ID,
            DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT, purchasePayload(), true,
            "pu:requisition:add", "tampered", PURCHASE_KEY);

        assertThrows(ServiceException.class, () -> executor.execute(valid, blankTenant));
        assertThrows(ServiceException.class, () -> executor.execute(valid, invalidUser));
        assertThrows(ServiceException.class, () -> executor.execute(valid, missingPermission));
        assertThrows(ServiceException.class, () -> executor.execute(wrongPermission, purchaseActor()));
        assertThrows(ServiceException.class, () -> executor.execute(wrongExecutor, purchaseActor()));

        verifyNoInteractions(purchaseRequisitionService, scheduleService, planMapper);
    }

    @Test
    void executePurchaseRejectsMissingUserNameBeforeDelegating() {
        DecisionActionProposal action = purchaseAction(purchasePayload());
        for (String name : new String[]{null, "", " \t "}) {
            DecisionActor actor = new DecisionActor("000000", 7L, name, Set.of("pu:requisition:add"));
            assertThrows(ServiceException.class, () -> executor.execute(action, actor));
        }
        verifyNoInteractions(purchaseRequisitionService, scheduleService, planMapper);
    }

    @Test
    void executeScheduleRejectsMissingUserNameBeforeDelegating() {
        DecisionActionProposal action = scheduleAction(schedulePayload("APS_EDD", "EDD"));
        for (String name : new String[]{null, "", " \t "}) {
            DecisionActor actor = new DecisionActor("000000", 7L, name, Set.of("prd:schedule:run"));
            assertThrows(ServiceException.class, () -> executor.execute(action, actor));
        }
        verifyNoInteractions(purchaseRequisitionService, scheduleService, planMapper);
    }

    @Test
    void verifyRejectsMissingUserNameBeforeReadingEitherBusinessObject() {
        for (String name : new String[]{null, "", " \t "}) {
            DecisionActor purchase = new DecisionActor("000000", 7L, name, Set.of("pu:requisition:add"));
            DecisionActor schedule = new DecisionActor("000000", 7L, name, Set.of("prd:schedule:run"));
            assertThrows(ServiceException.class, () -> executor.verify(purchaseAction(purchasePayload()),
                purchaseResult(7001L, "PR-7001", "V:0", "99", "501"), purchase));
            assertThrows(ServiceException.class, () -> executor.verify(scheduleAction(schedulePayload("APS_EDD", "EDD")),
                scheduleResult(8001L, "APS202609240001", "V:0", "99", "502", "APS_EDD"), schedule));
        }
        verifyNoInteractions(purchaseRequisitionService, scheduleService, planMapper);
    }

    @Test
    void decimalRejectsScalePrecisionAndUnboundedInputBeforeDelegate() {
        Map<String, Object> tooMuchScale = purchasePayloadWithQuantity("1.00001");
        Map<String, Object> tooLarge = purchasePayloadWithQuantity("100000000000000.0000");
        Map<String, Object> unbounded = purchasePayloadWithQuantity("9".repeat(200));

        assertThrows(ServiceException.class,
            () -> executor.execute(purchaseAction(tooMuchScale), purchaseActor()));
        assertThrows(ServiceException.class,
            () -> executor.execute(purchaseAction(tooLarge), purchaseActor()));
        assertThrows(ServiceException.class,
            () -> executor.execute(purchaseAction(unbounded), purchaseActor()));

        verifyNoInteractions(purchaseRequisitionService, scheduleService, planMapper);
    }

    @Test
    void decimalAcceptsMaximumDecimal18Scale4Value() {
        Map<String, Object> payload = purchasePayloadWithQuantity("99999999999999.9999");
        when(purchaseRequisitionService.createDraft(any(PuPurchaseRequisitionBo.class))).thenReturn(7001L);
        PuPurchaseRequisitionVo vo = purchaseVo();
        vo.getPuPurchaseRequisitionLineList().get(0)
            .setApplyQuantity(new BigDecimal("99999999999999.9999"));
        vo.getPuPurchaseRequisitionLineList().get(0)
            .setShortageQuantity(new BigDecimal("99999999999999.9999"));
        when(purchaseRequisitionService.selectByIdForDecision(7001L)).thenReturn(vo);

        assertTrue(executor.execute(purchaseAction(payload), purchaseActor()).success());
    }

    @Test
    void strictWhitelistsDatesDuplicatesAndStrategyConflictsStillFailClosed() {
        Map<String, Object> unknown = new LinkedHashMap<>(purchasePayload());
        unknown.put("freeForm", "forbidden");
        Map<String, Object> duplicates = new LinkedHashMap<>(schedulePayload("APS_EDD", "EDD"));
        duplicates.put("workOrderIds", List.of("11", "11"));
        Map<String, Object> conflict = schedulePayload("APS_EDD", "CR");
        Map<String, Object> invalidDate = new LinkedHashMap<>(purchasePayload());
        invalidDate.put("requiredDate", "2026-02-30");
        Map<String, Object> baseline = schedulePayload("BASELINE_CURRENT", null);

        assertThrows(ServiceException.class, () -> executor.execute(purchaseAction(unknown), purchaseActor()));
        assertThrows(ServiceException.class, () -> executor.execute(scheduleAction(duplicates), scheduleActor()));
        assertThrows(ServiceException.class, () -> executor.execute(scheduleAction(conflict), scheduleActor()));
        assertThrows(ServiceException.class, () -> executor.execute(purchaseAction(invalidDate), purchaseActor()));
        assertThrows(ServiceException.class, () -> executor.execute(scheduleAction(baseline), scheduleActor()));

        verifyNoInteractions(purchaseRequisitionService, scheduleService, planMapper);
    }

    @Test
    void verifyPurchaseRequiresExactStructuredHeaderCodeAndNormalizedLines() {
        DecisionActionProposal action = purchaseAction(purchasePayload());
        DecisionActionExecutionResult original = purchaseResult(7001L, "PR-7001", "V:0", "99", "501");
        when(purchaseRequisitionService.selectById(7001L)).thenReturn(purchaseVo());

        DecisionActionExecutionResult verified = executor.verify(action, original, purchaseActor());

        assertSame(original, verified);
    }

    @Test
    void verifyPurchaseRejectsDelimiterCollisionBetweenMaterialCodeAndName() {
        Map<String, Object> payload = purchasePayload();
        Map<String, Object> requested = new LinkedHashMap<>();
        requested.put("materialId", "101");
        requested.put("materialCode", "A|B");
        requested.put("materialName", "C");
        requested.put("quantity", "5.0000");
        requested.put("gap", "5.0000");
        requested.put("requiredDate", "2026-09-28");
        payload.put("materialLines", List.of(requested));
        PuPurchaseRequisitionVo persisted = purchaseVo();
        PuPurchaseRequisitionLine persistedLine = persisted.getPuPurchaseRequisitionLineList().get(0);
        persistedLine.setMaterialCode("A");
        persistedLine.setMaterialName("B|C");
        when(purchaseRequisitionService.selectById(7001L)).thenReturn(persisted);

        DecisionActionExecutionResult verified = executor.verify(purchaseAction(payload),
            purchaseResult(7001L, "PR-7001", "V:0", "99", "501"), purchaseActor());

        assertFalse(verified.success());
        assertEquals("VERIFY_CONFLICT", verified.errorCode());
    }

    @Test
    void verifyPurchasePreservesDuplicateLineCountsAndIgnoresLineOrderAndDecimalScale() {
        Map<String, Object> payload = purchasePayload();
        Map<String, Object> first = Map.of(
            "materialId", "101", "materialCode", "M-101", "materialName", "物料101",
            "quantity", "5.0000", "gap", "5.0000", "requiredDate", "2026-09-28");
        Map<String, Object> other = new LinkedHashMap<>(first);
        other.put("materialId", "202");
        payload.put("materialLines", List.of(first, first, other));
        PuPurchaseRequisitionVo persisted = purchaseVo();
        PuPurchaseRequisitionLine existing = persisted.getPuPurchaseRequisitionLineList().get(0);
        existing.setApplyQuantity(new BigDecimal("5.0"));
        PuPurchaseRequisitionLine existingOther = purchaseVo().getPuPurchaseRequisitionLineList().get(0);
        existingOther.setMaterialId(202L);
        persisted.setPuPurchaseRequisitionLineList(List.of(existingOther, existing, existing));
        PuPurchaseRequisitionVo missingDuplicate = purchaseVo();
        missingDuplicate.setPuPurchaseRequisitionLineList(List.of(existingOther, existing));
        when(purchaseRequisitionService.selectById(7001L)).thenReturn(persisted, missingDuplicate);
        DecisionActionProposal action = purchaseAction(payload);
        DecisionActionExecutionResult original = purchaseResult(7001L, "PR-7001", "V:0", "99", "501");

        assertSame(original, executor.verify(action, original, purchaseActor()));
        assertEquals("VERIFY_CONFLICT", executor.verify(action, original, purchaseActor()).errorCode());
    }

    @Test
    void verifyPurchaseRejectsChangedProvenanceHeaderLineOrResultCode() {
        DecisionActionProposal action = purchaseAction(purchasePayload());
        DecisionActionExecutionResult original = purchaseResult(7001L, "PR-7001", "V:0", "99", "501");
        PuPurchaseRequisitionVo provenanceChanged = purchaseVo();
        provenanceChanged.setDecisionActionId(999L);
        PuPurchaseRequisitionVo headerChanged = purchaseVo();
        headerChanged.setRequiredDate(Date.valueOf("2026-10-01"));
        PuPurchaseRequisitionVo lineChanged = purchaseVo();
        lineChanged.getPuPurchaseRequisitionLineList().get(0).setShortageQuantity(new BigDecimal("6"));
        when(purchaseRequisitionService.selectById(7001L))
            .thenReturn(provenanceChanged, headerChanged, lineChanged, purchaseVo());

        assertEquals("VERIFY_CONFLICT", executor.verify(action, original, purchaseActor()).errorCode());
        assertEquals("VERIFY_CONFLICT", executor.verify(action, original, purchaseActor()).errorCode());
        assertEquals("VERIFY_CONFLICT", executor.verify(action, original, purchaseActor()).errorCode());
        DecisionActionExecutionResult wrongCode = purchaseResult(7001L, "PR-TAMPERED", "V:0", "99", "501");
        assertEquals("VERIFY_CONFLICT", executor.verify(action, wrongCode, purchaseActor()).errorCode());
    }

    @Test
    void verifyScheduleRequiresExactStructuredScopeAndPayload() {
        DecisionActionProposal action = scheduleAction(schedulePayload("APS_EDD", "EDD"));
        DecisionActionExecutionResult original = scheduleResult(8001L, "APS202609240001", "V:0",
            "99", "502", "APS_EDD");
        when(planMapper.selectById(8001L)).thenReturn(schedulePlan("APS_EDD", "EDD"));

        assertSame(original, executor.verify(action, original, scheduleActor()));
    }

    @Test
    void verifyScheduleRejectsChangedScopeStrategyScenarioOrCode() {
        DecisionActionProposal action = scheduleAction(schedulePayload("APS_EDD", "EDD"));
        DecisionActionExecutionResult original = scheduleResult(8001L, "APS202609240001", "V:0",
            "99", "502", "APS_EDD");
        PrdSchedulePlan scopeChanged = schedulePlan("APS_EDD", "EDD");
        scopeChanged.setDecisionWorkOrderIdsJson("[\"11\"]");
        PrdSchedulePlan strategyChanged = schedulePlan("APS_EDD", "CR");
        PrdSchedulePlan scenarioChanged = schedulePlan("APS_CR", "EDD");
        when(planMapper.selectById(8001L))
            .thenReturn(scopeChanged, strategyChanged, scenarioChanged, schedulePlan("APS_EDD", "EDD"));

        assertEquals("VERIFY_CONFLICT", executor.verify(action, original, scheduleActor()).errorCode());
        assertEquals("VERIFY_CONFLICT", executor.verify(action, original, scheduleActor()).errorCode());
        assertEquals("VERIFY_CONFLICT", executor.verify(action, original, scheduleActor()).errorCode());
        DecisionActionExecutionResult wrongCode = scheduleResult(8001L, "APS-TAMPERED", "V:0",
            "99", "502", "APS_EDD");
        assertEquals("VERIFY_CONFLICT", executor.verify(action, wrongCode, scheduleActor()).errorCode());
    }

    @Test
    void compensatePurchasePassesVersionAndStructuredProvenanceOnly() {
        DecisionActionProposal action = purchaseAction(purchasePayload());
        DecisionActionExecutionResult original = purchaseResult(7001L, "PR-7001", "V:0", "99", "501");
        when(purchaseRequisitionService.deleteDraftIfUnchanged(
            7001L, "V:0", RUN_ID, PURCHASE_ACTION_ID, PURCHASE_KEY)).thenReturn(true);

        DecisionActionExecutionResult compensated = executor.compensate(action, original, purchaseActor());

        assertTrue(compensated.success());
        verify(purchaseRequisitionService).deleteDraftIfUnchanged(
            7001L, "V:0", RUN_ID, PURCHASE_ACTION_ID, PURCHASE_KEY);
        verify(purchaseRequisitionService, never()).deleteByIds(any(Long[].class));
    }

    @Test
    void compensateSchedulePassesVersionScenarioAndStructuredProvenanceOnly() {
        DecisionActionProposal action = scheduleAction(schedulePayload("APS_EDD", "EDD"));
        DecisionActionExecutionResult original = scheduleResult(8001L, "APS202609240001", "V:0",
            "99", "502", "APS_EDD");
        when(scheduleService.deleteCompletedIfUnchanged(
            8001L, "V:0", RUN_ID, SCHEDULE_ACTION_ID, SCHEDULE_KEY, "APS_EDD")).thenReturn(true);

        assertTrue(executor.compensate(action, original, scheduleActor()).success());

        verify(scheduleService).deleteCompletedIfUnchanged(
            8001L, "V:0", RUN_ID, SCHEDULE_ACTION_ID, SCHEDULE_KEY, "APS_EDD");
        verify(scheduleService, never()).deleteByIds(any(Long[].class));
        verify(scheduleService, never()).publish(any());
        verify(planMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void tamperedResultProvenanceFailsBeforeDeleteAndTamperedIdCannotBypassAtomicService() {
        DecisionActionProposal action = purchaseAction(purchasePayload());
        DecisionActionExecutionResult tamperedProvenance = purchaseResult(
            7001L, "PR-7001", "V:0", "100", "501");

        assertThrows(ServiceException.class,
            () -> executor.compensate(action, tamperedProvenance, purchaseActor()));
        verifyNoInteractions(purchaseRequisitionService, scheduleService, planMapper);

        DecisionActionExecutionResult tamperedId = purchaseResult(
            9999L, "PR-7001", "V:0", "99", "501");
        when(purchaseRequisitionService.deleteDraftIfUnchanged(
            9999L, "V:0", RUN_ID, PURCHASE_ACTION_ID, PURCHASE_KEY)).thenReturn(false);
        DecisionActionExecutionResult refused = executor.compensate(action, tamperedId, purchaseActor());
        assertFalse(refused.success());
        assertEquals("COMPENSATION_NOT_ALLOWED", refused.errorCode());
    }

    @Test
    void verifyMissingAndStateMismatchKeepStableCodes() {
        DecisionActionProposal action = purchaseAction(purchasePayload());
        DecisionActionExecutionResult original = purchaseResult(7001L, "PR-7001", "V:0", "99", "501");
        PuPurchaseRequisitionVo submitted = purchaseVo();
        submitted.setStatus("SUBMITTED");
        when(purchaseRequisitionService.selectById(7001L)).thenReturn(null, submitted);

        assertEquals("VERIFY_NOT_FOUND", executor.verify(action, original, purchaseActor()).errorCode());
        assertEquals("VERIFY_STATE_MISMATCH", executor.verify(action, original, purchaseActor()).errorCode());
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
        return action(PURCHASE_ACTION_ID, DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
            payload, true, "pu:requisition:add", "purchaseRequisitionDraft", PURCHASE_KEY);
    }

    private DecisionActionProposal scheduleAction(Map<String, Object> payload) {
        return action(SCHEDULE_ACTION_ID, DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT,
            payload, true, "prd:schedule:run", "schedulePlanDraft", SCHEDULE_KEY);
    }

    private DecisionActionProposal action(Long actionId, DecisionActionType type, Map<String, Object> payload,
                                          boolean approvalRequired, String permissionCode,
                                          String executorCode, String idempotencyKey) {
        return new DecisionActionProposal(actionId, type, "动作", "原因", target(), payload,
            DecisionRiskLevel.HIGH, approvalRequired, permissionCode, idempotencyKey,
            executorCode, 1, List.of());
    }

    private Map<String, Object> purchasePayload() {
        return purchasePayloadWithQuantity("5.0000");
    }

    private Map<String, Object> purchasePayloadWithQuantity(String quantity) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("materialId", "101");
        line.put("materialCode", "M-101");
        line.put("materialName", "物料101");
        line.put("quantity", quantity);
        line.put("gap", quantity);
        line.put("requiredDate", "2026-09-28");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", "99");
        payload.put("requiredDate", "2026-09-30");
        payload.put("materialLines", List.of(line));
        return payload;
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

    private PuPurchaseRequisitionVo purchaseVo() {
        PuPurchaseRequisitionVo vo = new PuPurchaseRequisitionVo();
        vo.setRequisitionId(7001L);
        vo.setRequisitionNo("PR-7001");
        vo.setRequisitionName("AI交付风险建议-SO-42");
        vo.setApplicantId(7L);
        vo.setApplicantName("tester");
        vo.setRequiredDate(Date.valueOf("2026-09-30"));
        vo.setSourceType("AI_DECISION");
        vo.setStatus("DRAFT");
        vo.setDecisionRunId(RUN_ID);
        vo.setDecisionActionId(PURCHASE_ACTION_ID);
        vo.setDecisionIdempotencyKey(PURCHASE_KEY);
        vo.setVersion(0);
        PuPurchaseRequisitionLine line = new PuPurchaseRequisitionLine();
        line.setMaterialId(101L);
        line.setMaterialCode("M-101");
        line.setMaterialName("物料101");
        line.setApplyQuantity(new BigDecimal("5.0000"));
        line.setShortageQuantity(new BigDecimal("5.0000"));
        line.setRequiredDate(Date.valueOf("2026-09-28"));
        line.setSourceType("AI_DECISION");
        line.setSourceId(RUN_ID);
        vo.setPuPurchaseRequisitionLineList(List.of(line));
        return vo;
    }

    private PrdSchedulePlan schedulePlan(String scenario, String strategy) {
        PrdSchedulePlan plan = new PrdSchedulePlan();
        plan.setPlanId(8001L);
        plan.setPlanNo("APS202609240001");
        plan.setPlanName("AI交付风险方案-" + scenario);
        plan.setWorkshopId(21L);
        plan.setStartDate(Date.valueOf("2026-09-24"));
        plan.setHorizonDays(30);
        plan.setStrategy(strategy);
        plan.setStatus("COMPLETED");
        plan.setDecisionRunId(RUN_ID);
        plan.setDecisionActionId(SCHEDULE_ACTION_ID);
        plan.setDecisionIdempotencyKey(SCHEDULE_KEY);
        plan.setDecisionScenarioCode(scenario);
        plan.setDecisionWorkOrderIdsJson(WORK_ORDER_IDS_JSON);
        plan.setVersion(0);
        return plan;
    }

    private DecisionActionExecutionResult purchaseResult(Long id, String code, String marker,
                                                         String runId, String actionId) {
        return new DecisionActionExecutionResult(true, false, PURCHASE_BIZ_TYPE, id, code,
            Map.of("status", "DRAFT", "versionMarker", marker,
                "runId", runId, "actionId", actionId), null, null);
    }

    private DecisionActionExecutionResult scheduleResult(Long id, String code, String marker,
                                                         String runId, String actionId,
                                                         String scenario) {
        return new DecisionActionExecutionResult(true, false, SCHEDULE_BIZ_TYPE, id, code,
            Map.of("status", "COMPLETED", "versionMarker", marker,
                "runId", runId, "actionId", actionId, "scenarioCode", scenario), null, null);
    }
}
