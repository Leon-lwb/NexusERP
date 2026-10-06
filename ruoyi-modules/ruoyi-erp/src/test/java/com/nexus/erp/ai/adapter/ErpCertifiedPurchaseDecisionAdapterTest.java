package com.nexus.erp.ai.adapter;

import com.nexus.common.ai.decision.dto.DecisionActionExecutionResult;
import com.nexus.common.ai.decision.dto.DecisionActionProposal;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.enums.DecisionRiskLevel;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.pu.domain.bo.PuCertifiedPurchaseCommand;
import com.nexus.erp.pu.domain.vo.PuCertifiedPurchaseResult;
import com.nexus.erp.pu.domain.vo.PuCertifiedPurchaseResult.Line;
import com.nexus.erp.pu.service.PuCertifiedPurchaseWriteService;
import com.nexus.erp.pu.service.PuCertifiedPurchaseWriteService.FrozenLine;
import com.nexus.erp.pu.service.PuCertifiedPurchaseWriteService.FrozenContract;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ErpCertifiedPurchaseDecisionAdapterTest {
    private static final String TENANT = "000001";
    private static final String FINGERPRINT = "a".repeat(64);
    private static final Long MATERIAL = 9007199254740993L;
    private static final Long DETAIL = 9007199254741001L;
    private static final DecisionActor ACTOR = new DecisionActor(TENANT, 7L, "tester",
        Set.of("ai:decision:execute", "pu:requisition:add"));
    private static final DecisionBusinessRef TARGET =
        new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, "WO-11");
    private static final String KEY=DecisionPurchaseContractCodec.idempotencyKey(TENANT,10L,TARGET,DecisionPurchaseContractCodec.parse(payload(),10L));
    @Mock private PuCertifiedPurchaseWriteService writer;
    private ErpCertifiedPurchaseDecisionAdapter adapter;
    private MockedStatic<TenantHelper> tenant;

    @BeforeEach
    void setUp() {
        adapter = new ErpCertifiedPurchaseDecisionAdapter(writer);
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        tenant.when(TenantHelper::getTenantId).thenReturn(TENANT);
    }

    @AfterEach
    void tearDown() {
        tenant.close();
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
    }

    private DecisionActionExecutionResult recoverExisting(DecisionActionProposal action) {
        return adapter.recover(action,ACTOR);
    }
    @Test void v2KeyIsRecomputedAndNonWorkOrderTargetNeverReachesWriter() {
        var original=action(payload());
        var wrongKey=new DecisionActionProposal(original.actionId(),original.actionType(),original.title(),original.displayReason(),TARGET,
            original.payload(),original.riskLevel(),true,original.permissionCode(),"AI_PURCHASE_V2:"+"f".repeat(64),original.executorCode(),1,List.of());
        assertThrows(ServiceException.class,()->adapter.execute(wrongKey,ACTOR),"新v2键必须与完整身份前像一致");
        var otherTarget=new DecisionActionProposal(original.actionId(),original.actionType(),original.title(),original.displayReason(),
            new DecisionBusinessRef(DecisionBusinessType.SALES_ORDER,11L,null),original.payload(),original.riskLevel(),true,original.permissionCode(),
            KEY,original.executorCode(),1,List.of());
        assertThrows(ServiceException.class,()->adapter.execute(otherTarget,ACTOR),"首包不扩展非工单根");
        verifyNoInteractions(writer);
    }
    @Test void recoverMissDoesNotCallExistingCreationWriter() throws Exception {
        assertNull(recoverExisting(action(payload())),"只有确定无永久提交才可返回空结果");
        verify(writer).recoverForDecision(eq(command()),eq(ACTOR),eq(10L),eq(101L),eq(frozenLines()),any(FrozenContract.class));
        verify(writer,never()).createForDecision(any(),any(),any(),any(),any(),any());
    }
    @Test void recoverCommittedDraftUsesActualReceiptAndFullOriginalContract() throws Exception {
        when(writer.recoverForDecision(any(),eq(ACTOR),eq(10L),eq(101L),anyList(),any(FrozenContract.class))).thenReturn(receipt(true));
        var result=recoverExisting(action(payload()));
        assertTrue(result.success(),"草稿恢复应返回真实成功结果信封");
        assertEquals(receipt(true).requisitionId(),result.resultBizId(),"只能认领原实际单据");
        var captured=org.mockito.ArgumentCaptor.forClass(FrozenContract.class);
        verify(writer).recoverForDecision(any(),eq(ACTOR),eq(10L),eq(101L),anyList(),captured.capture());
        assertEquals(11L,captured.getValue().rootWorkOrderId(),"恢复仍须绑定持久化动作的根身份");
        verify(writer,never()).createForDecision(any(),any(),any(),any(),any(),any());
    }
    @Test void recoverAdvancedDocumentReturnsActualBusinessIdWithoutRecreation() throws Exception {
        var old=receipt(true);
        when(writer.recoverForDecision(any(),eq(ACTOR),eq(10L),eq(101L),anyList(),any(FrozenContract.class))).thenReturn(
            new PuCertifiedPurchaseResult(old.requisitionId(),old.requisitionNo(),"ORDERED",1,true,FINGERPRINT,old.lines()));
        var result=recoverExisting(action(payload()));
        assertFalse(result.success(),"原单已变更不能声称创建新草稿成功");
        assertEquals("VERIFY_STATE_MISMATCH",result.errorCode(),"状态不匹配须明确报告");
        assertEquals(old.requisitionId(),result.resultBizId(),"失败结果仍保留实际业务单据身份");
        verify(writer,never()).createForDecision(any(),any(),any(),any(),any(),any());
    }
    @Test void recoverConflictIsNotCaughtAsMissing() throws Exception {
        when(writer.recoverForDecision(any(),eq(ACTOR),eq(10L),eq(101L),anyList(),any(FrozenContract.class)))
            .thenThrow(new ServiceException("永久请求键已被不同合同占用"));
        assertThrows(ServiceException.class,()->recoverExisting(action(payload())),"冲突必须传播而不是空结果");
        verify(writer,never()).createForDecision(any(),any(),any(),any(),any(),any());
    }
    @Test
    void certifiedPurchaseHasSeparateNonTransactionalComponent() {
        assertNotNull(ErpCertifiedPurchaseDecisionAdapter.class.getAnnotation(Component.class));
        assertNull(ErpCertifiedPurchaseDecisionAdapter.class.getAnnotation(Transactional.class));
        for (var method : ErpCertifiedPurchaseDecisionAdapter.class.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                assertNull(method.getAnnotation(Transactional.class), method.getName());
            }
        }
    }

    @Test
    void executePassesOnlySourceCommandAndExactFrozenLinesToWriterAndUsesActualReceipt() {
        when(writer.createForDecision(any(), eq(ACTOR), eq(10L), eq(101L), anyList(), any(FrozenContract.class)))
            .thenReturn(receipt(false));
        var result = adapter.execute(action(payload()), ACTOR);
        assertTrue(result.success());
        assertFalse(result.retryable());
        assertEquals("PU_PURCHASE_REQUISITION", result.resultBizType());
        assertEquals(9007199254741100L, result.resultBizId());
        assertEquals("PR-CERT-100", result.resultBizCode());
        assertEquals("10", result.result().get("runId"));
        assertEquals("101", result.result().get("actionId"));
        assertEquals("AI_DECISION", result.result().get("sourceType"));
        assertEquals("v0", result.result().get("versionMarker"));
        assertEquals("ABSENT", result.result().get("beforeState"));
        assertEquals("DRAFT", result.result().get("afterState"));
        assertEquals(FINGERPRINT, result.result().get("sourceFingerprint"));
        assertEquals(false, result.result().get("replayed"));
        assertTrue(((String) result.result().get("payloadFingerprint")).matches("[0-9a-f]{64}"));
        assertNull(result.errorCode());
        var command = ArgumentCaptor.forClass(PuCertifiedPurchaseCommand.class);
        @SuppressWarnings("unchecked") var lines = ArgumentCaptor.forClass((Class<List<FrozenLine>>) (Class<?>) List.class);
        var contract = ArgumentCaptor.forClass(FrozenContract.class);
        verify(writer).createForDecision(command.capture(), eq(ACTOR), eq(10L), eq(101L), lines.capture(), contract.capture());
        assertEquals(result.result().get("payloadFingerprint"), contract.getValue().payloadFingerprint());
        assertEquals(frozenMetadata(payload()), contract.getValue().metadata());
        assertThrows(UnsupportedOperationException.class, () -> contract.getValue().metadata().put("vendorVersion", "changed"));
        assertEquals(new PuCertifiedPurchaseCommand(KEY, MATERIAL, 200L, LocalDate.of(2026, 10, 20),
            List.of(DETAIL), FINGERPRINT), command.getValue());
        assertEquals(List.of(new FrozenLine(DETAIL, 300L, new BigDecimal("5.0000"), 300L,
            new BigDecimal("5.0000"), LocalDate.of(2026, 10, 15))), lines.getValue());
        verifyNoMoreInteractions(writer);
        var actualLines = (List<?>) result.result().get("lines");
        var actualLine = (Map<?, ?>) actualLines.get(0);
        assertEquals("9007199254741200", actualLine.get("lineId"));
        assertEquals(DETAIL.toString(), actualLine.get("requirementDetailId"));
        assertEquals(MATERIAL.toString(), actualLine.get("materialId"));
        assertEquals("300", actualLine.get("baseUomId"));
        assertEquals("5", actualLine.get("coveredDemandBase"));
        assertThrows(UnsupportedOperationException.class, () -> result.result().put("status", "CLOSED"));
    }

    @Test
    void executeReplaysOriginalCommittedDocumentRatherThanCreatingSecondResultIdentity() {
        when(writer.createForDecision(any(), eq(ACTOR), eq(10L), eq(101L), anyList(), any(FrozenContract.class)))
            .thenReturn(receipt(true));
        var result = adapter.execute(action(payload()), ACTOR);
        assertTrue(result.success());
        assertEquals(9007199254741100L, result.resultBizId());
        assertEquals(true, result.result().get("replayed"));
        assertEquals("COMMITTED", result.result().get("beforeState"));
        verifyNoMoreInteractionsAfterCreate();
    }

    @Test
    void adminPermissionMayExecuteButRunPermissionAloneMayNot() {
        var admin = new DecisionActor(TENANT, 7L, "tester", Set.of("*:*:*"));
        when(writer.createForDecision(any(), eq(admin), eq(10L), eq(101L), anyList(), any(FrozenContract.class))).thenReturn(receipt(false));
        assertTrue(adapter.execute(action(payload()), admin).success());
    }

    @ParameterizedTest
    @MethodSource("invalidPayloads")
    void malformedOldOrUnattributedPayloadFailsBeforeAnyWriterOperation(Map<String, Object> payload) {
        assertThrows(ServiceException.class, () -> adapter.execute(action(payload), ACTOR));
        verifyNoInteractions(writer);
    }

    static Stream<Map<String, Object>> invalidPayloads() {
        List<Map<String, Object>> cases = new ArrayList<>();
        for (String field : payload().keySet()) {
            var missing = payload(); missing.remove(field); cases.add(missing);
        }
        for (Object version : List.of(1, "2", 2.0, true)) cases.add(changed("purchase_payload_version", version));
        for (String field : List.of("runId", "materialId", "vendorId", "baseUomId", "orderUomId", "supplierTermsId")) {
            cases.add(changed(field, "0"));
            cases.add(changed(field, "9223372036854775808"));
        }
        for (String field : List.of("materialVersion", "vendorVersion", "supplierTermsVersion",
            "baseUomVersion", "orderUomVersion", "conversionVersion")) {
            cases.add(changed(field, "")); cases.add(changed(field, " "));
        }
        for (Object fingerprint : List.of("a".repeat(63), "A".repeat(64), true)) {
            cases.add(changed("expectedSourceFingerprint", fingerprint));
        }
        cases.add(changed("windowEnd", "2026-02-30"));
        cases.add(changed("windowEnd", "2026-10-20T00:00:00"));
        cases.add(changed("windowEnd", "0999-10-20"));
        cases.add(changed("requirement_detail_ids", List.of()));
        cases.add(changed("requirement_detail_ids", List.of(DETAIL.toString(), DETAIL.toString())));
        cases.add(changed("requirement_detail_ids", List.of("9007199254741002", DETAIL.toString())));
        cases.add(changed("requirement_detail_ids", List.of("9007199254741002")));
        cases.add(changed("frozenLines", List.of()));
        cases.add(changed("frozenLines", List.of(frozen(), frozen())));
        cases.add(changed("frozenLines", List.of("not-a-line")));
        cases.add(changed("orderUomId", "301"));
        cases.add(changed("conversionVersion", "18"));
        cases.add(changed("materialLines", List.of(Map.of("materialId", MATERIAL.toString(), "quantity", "5"))));
        cases.add(changed("quantity", "5"));
        cases.add(changed("payloadVersion", 2));
        for (String field : frozen().keySet()) {
            var missing = frozen(); missing.remove(field); cases.add(changed("frozenLines", List.of(missing)));
        }
        for (Object quantity : List.of("0", "-1", "NaN", "1e3", "5.00001", "100000000000000", 5.0, " 5")) {
            cases.add(changedLine("coveredDemandBase", quantity));
            cases.add(changedLine("requestedOrderQty", quantity));
        }
        cases.add(changedLine("requestedOrderQty", "6"));
        cases.add(changedLine("baseUomId", "301"));
        cases.add(changedLine("orderUomId", "301"));
        cases.add(changedLine("detailId", "9007199254741002"));
        cases.add(changedLine("requiredDate", "2026-10-21"));
        cases.add(changedLine("requiredDate", "2026-02-30"));
        cases.add(changedLine("materialId", "101"));
        cases.add(changedLine("quantity", "5"));
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("invalidActors")
    void executeVerifyAndCompensateRequireBothPermissionsAndCurrentTenantBeforeWriter(DecisionActor actor) {
        var action = action(payload());
        assertThrows(ServiceException.class, () -> adapter.execute(action, actor));
        assertThrows(ServiceException.class, () -> adapter.verify(action, null, actor));
        assertThrows(ServiceException.class, () -> adapter.compensate(action, null, actor));
        verifyNoInteractions(writer);
    }

    static Stream<DecisionActor> invalidActors() {
        return Stream.of(null, new DecisionActor(TENANT, 7L, "tester", Set.of("ai:decision:run", "pu:requisition:add")),
            new DecisionActor(TENANT, 7L, "tester", Set.of("ai:decision:execute")),
            new DecisionActor(TENANT, 7L, "tester", Set.of("pu:requisition:add")),
            new DecisionActor("000002", 7L, "tester", ACTOR.permissions()),
            new DecisionActor(TENANT, 0L, "tester", ACTOR.permissions()),
            new DecisionActor(TENANT, 7L, " ", ACTOR.permissions()),
            new DecisionActor(TENANT, 7L, "tester", null));
    }

    @Test
    void outerActualTransactionSynchronizationOrBoundResourceCannotWrapAnyOperation() {
        var action = action(payload());
        for (int mode = 0; mode < 3; mode++) {
            if (mode == 0) TransactionSynchronizationManager.setActualTransactionActive(true);
            else if (mode == 1) TransactionSynchronizationManager.initSynchronization();
            else TransactionSynchronizationManager.bindResource("F-foreign-resource", new Object());
            try {
                assertThrows(ServiceException.class, () -> adapter.execute(action, ACTOR));
                assertThrows(ServiceException.class, () -> adapter.verify(action, null, ACTOR));
                assertThrows(ServiceException.class, () -> adapter.compensate(action, null, ACTOR));
            } finally {
                if (mode == 2) TransactionSynchronizationManager.unbindResource("F-foreign-resource");
                TransactionSynchronizationManager.clear();
            }
        }
        verifyNoInteractions(writer);
    }

    @Test
    void changedTransactionCertifiedFactsCannotBecomeSuccessfulPurchase() {
        when(writer.createForDecision(any(), eq(ACTOR), eq(10L), eq(101L), anyList(), any(FrozenContract.class)))
            .thenThrow(new ServiceException("冻结来源已变化"));
        assertThrows(ServiceException.class, () -> adapter.execute(action(payload()), ACTOR));
        verifyNoMoreInteractionsAfterCreate();
    }

    @Test
    void verifyRecoversOnlyExistingCommandAndKeepsOriginalEnvelope() {
        var action = action(payload());
        var executed = executeReceipt(action);
        clearInvocations(writer);
        when(writer.recoverForDecision(any(), eq(ACTOR), eq(10L), eq(101L), anyList(), any(FrozenContract.class))).thenReturn(receipt(true));
        assertSame(executed, adapter.verify(action, executed, ACTOR));
        verify(writer).recoverForDecision(eq(command()), eq(ACTOR), eq(10L), eq(101L), eq(frozenLines()), any(FrozenContract.class));
        verifyNoMoreInteractions(writer);
    }

    @Test
    void verifyMissingCommandDoesNotCreateDraftOrCallCreateForDecision() {
        var action = action(payload());
        var executed = executeReceipt(action);
        clearInvocations(writer);
        var result = adapter.verify(action, executed, ACTOR);
        assertFalse(result.success());
        assertFalse(result.retryable());
        assertEquals("VERIFY_NOT_FOUND", result.errorCode());
        assertEquals(executed.resultBizId(), result.resultBizId());
        verify(writer).recoverForDecision(eq(command()), eq(ACTOR), eq(10L), eq(101L), eq(frozenLines()), any(FrozenContract.class));
        verifyNoMoreInteractions(writer);
    }

    @ParameterizedTest
    @MethodSource("invalidEnvelopeFields")
    void verifyRejectsTamperedIdentityVersionsStatesAndRowsBeforeRecovery(String field, Object value) {
        var action = action(payload());
        var executed = executeReceipt(action);
        clearInvocations(writer);
        var details = new LinkedHashMap<>(executed.result()); details.put(field, value);
        var forged = new DecisionActionExecutionResult(true, false, executed.resultBizType(), executed.resultBizId(),
            executed.resultBizCode(), details, null, null);
        assertThrows(ServiceException.class, () -> adapter.verify(action, forged, ACTOR));
        verifyNoInteractions(writer);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> invalidEnvelopeFields() {
        return Stream.of(
            org.junit.jupiter.params.provider.Arguments.of("runId", "11"),
            org.junit.jupiter.params.provider.Arguments.of("actionId", "102"),
            org.junit.jupiter.params.provider.Arguments.of("tenantId", "000002"),
            org.junit.jupiter.params.provider.Arguments.of("materialId", "101"),
            org.junit.jupiter.params.provider.Arguments.of("vendorId", "201"),
            org.junit.jupiter.params.provider.Arguments.of("requestKey", KEY + "-other"),
            org.junit.jupiter.params.provider.Arguments.of("purchase_payload_version", "2"),
            org.junit.jupiter.params.provider.Arguments.of("versionMarker", "v1"),
            org.junit.jupiter.params.provider.Arguments.of("beforeState", "COMMITTED"),
            org.junit.jupiter.params.provider.Arguments.of("afterState", "ORDERED"),
            org.junit.jupiter.params.provider.Arguments.of("status", "ORDERED"),
            org.junit.jupiter.params.provider.Arguments.of("sourceType", "MANUAL"),
            org.junit.jupiter.params.provider.Arguments.of("sourceFingerprint", "b".repeat(64)),
            org.junit.jupiter.params.provider.Arguments.of("payloadFingerprint", "b".repeat(64)),
            org.junit.jupiter.params.provider.Arguments.of("replayed", "false"),
            org.junit.jupiter.params.provider.Arguments.of("lines", List.of()),
            org.junit.jupiter.params.provider.Arguments.of("unknown", true));
    }

    @Test
    void verifyRejectsWrongOuterResultTypeAndNeverUsesNumericOrNameBasedUnit() {
        var action = action(payload());
        var executed = executeReceipt(action);
        clearInvocations(writer);
        for (var forged : List.of(
            new DecisionActionExecutionResult(true, false, "PRD_SCHEDULE_PLAN", executed.resultBizId(), "PR-CERT-100", executed.result(), null, null),
            new DecisionActionExecutionResult(true, false, executed.resultBizType(), 0L, "PR-CERT-100", executed.result(), null, null),
            new DecisionActionExecutionResult(true, true, executed.resultBizType(), executed.resultBizId(), "PR-CERT-100", executed.result(), null, null),
            new DecisionActionExecutionResult(false, false, executed.resultBizType(), executed.resultBizId(), "PR-CERT-100", executed.result(), "FAILED", "error"))) {
            assertThrows(ServiceException.class, () -> adapter.verify(action, forged, ACTOR));
        }
        var row = new LinkedHashMap<String, Object>();
        ((Map<?, ?>) ((List<?>) executed.result().get("lines")).get(0)).forEach((key, value) -> row.put(key.toString(), value));
        row.put("baseUomId", "个");
        var details = new LinkedHashMap<>(executed.result()); details.put("lines", List.of(row));
        var forged = new DecisionActionExecutionResult(true, false, executed.resultBizType(), executed.resultBizId(),
            executed.resultBizCode(), details, null, null);
        assertThrows(ServiceException.class, () -> adapter.verify(action, forged, ACTOR));
        verifyNoInteractions(writer);
    }

    @Test
    void verifyRejectsChangedFrozenVersionsWithSameResultAndSourceKey() {
        var original = action(payload());
        var executed = executeReceipt(original);
        clearInvocations(writer);
        var changed = action(changed("supplierTermsVersion", "terms-revision-42"));
        assertThrows(ServiceException.class, () -> adapter.verify(changed, executed, ACTOR));
        verifyNoInteractions(writer);
    }

    @ParameterizedTest
    @MethodSource("changedReceipts")
    void verifyDoesNotReportSuccessWhenActualDocumentVersionStateOrSourceChanged(PuCertifiedPurchaseResult current) {
        var action = action(payload());
        var executed = executeReceipt(action);
        clearInvocations(writer);
        when(writer.recoverForDecision(any(), eq(ACTOR), eq(10L), eq(101L), anyList(), any(FrozenContract.class))).thenReturn(current);
        var checked = adapter.verify(action, executed, ACTOR);
        assertFalse(checked.success());
        assertFalse(checked.retryable());
        assertTrue(Set.of("VERIFY_STATE_MISMATCH", "VERIFY_CONFLICT").contains(checked.errorCode()));
        verify(writer).recoverForDecision(eq(command()), eq(ACTOR), eq(10L), eq(101L), eq(frozenLines()), any(FrozenContract.class));
        verifyNoMoreInteractions(writer);
    }

    static Stream<PuCertifiedPurchaseResult> changedReceipts() {
        var r = receipt(true);
        return Stream.of(
            new PuCertifiedPurchaseResult(r.requisitionId(), r.requisitionNo(), "ORDERED", 1, true, FINGERPRINT, r.lines()),
            new PuCertifiedPurchaseResult(r.requisitionId(), r.requisitionNo(), "DRAFT", 1, true, FINGERPRINT, r.lines()),
            new PuCertifiedPurchaseResult(9007199254741101L, r.requisitionNo(), "DRAFT", 0, true, FINGERPRINT, r.lines()),
            new PuCertifiedPurchaseResult(r.requisitionId(), "PR-CERT-OTHER", "DRAFT", 0, true, FINGERPRINT, r.lines()),
            new PuCertifiedPurchaseResult(r.requisitionId(), r.requisitionNo(), "DRAFT", 0, true, "b".repeat(64), r.lines()),
            new PuCertifiedPurchaseResult(r.requisitionId(), r.requisitionNo(), "DRAFT", 0, true, FINGERPRINT,
                List.of(new Line(9007199254741200L, DETAIL, MATERIAL, 300L, new BigDecimal("6"), LocalDate.of(2026, 10, 15)))),
            new PuCertifiedPurchaseResult(r.requisitionId(), r.requisitionNo(), "DRAFT", 0, true, FINGERPRINT,
                List.of(new Line(9007199254741200L, DETAIL, MATERIAL, 301L, new BigDecimal("5"), LocalDate.of(2026, 10, 15)))),
            new PuCertifiedPurchaseResult(r.requisitionId(), r.requisitionNo(), "DRAFT", 0, true, FINGERPRINT, List.of()));
    }

    @Test
    void compensationExplicitlyRefusesUntilAtomicWholeDocumentLifecycleExists() {
        var action = action(payload());
        var executed = executeReceipt(action);
        clearInvocations(writer);
        var result = adapter.compensate(action, executed, ACTOR);
        assertFalse(result.success());
        assertFalse(result.retryable());
        assertEquals("COMPENSATION_NOT_SUPPORTED", result.errorCode());
        assertEquals(executed.resultBizId(), result.resultBizId());
        assertEquals("DRAFT", result.result().get("status"));
        verifyNoInteractions(writer);
    }

    @Test
    void sortedMultipleMrpSourcesUseRealReceiptLineIdsAndCanonicalResultOrder() {
        var second = frozen(); second.put("detailId", "9007199254741002");
        second.put("coveredDemandBase", "1.2500"); second.put("requestedOrderQty", "1.2500");
        var payload = changed("requirement_detail_ids", List.of(DETAIL.toString(), "9007199254741002"));
        payload.put("frozenLines", List.of(frozen(), second));
        var receipt = receipt(false);
        var actual = new PuCertifiedPurchaseResult(receipt.requisitionId(), receipt.requisitionNo(), "DRAFT", 0, false,
            FINGERPRINT, List.of(new Line(9007199254741201L, 9007199254741002L, MATERIAL, 300L,
                new BigDecimal("1.2500"), LocalDate.of(2026, 10, 15)), receipt.lines().get(0)));
        when(writer.createForDecision(any(), eq(ACTOR), eq(10L), eq(101L), anyList(), any(FrozenContract.class))).thenReturn(actual);
        var result = adapter.execute(action(payload), ACTOR);
        assertTrue(result.success());
        var rows = (List<?>) result.result().get("lines");
        assertEquals(DETAIL.toString(), ((Map<?, ?>) rows.get(0)).get("requirementDetailId"));
        assertEquals("9007199254741002", ((Map<?, ?>) rows.get(1)).get("requirementDetailId"));
        assertEquals("1.25", ((Map<?, ?>) rows.get(1)).get("requestedOrderQty"));
        assertEquals("9007199254741201", ((Map<?, ?>) rows.get(1)).get("lineId"));
    }

    @Test
    void replayOfAdvancedDocumentNeverCreatesOrClaimsFreshDraftSuccess() {
        var actual = receipt(true);
        when(writer.createForDecision(any(), eq(ACTOR), eq(10L), eq(101L), anyList(), any(FrozenContract.class))).thenReturn(
            new PuCertifiedPurchaseResult(actual.requisitionId(), actual.requisitionNo(), "ORDERED", 1, true,
                FINGERPRINT, actual.lines()));
        var result = adapter.execute(action(payload()), ACTOR);
        assertFalse(result.success());
        assertFalse(result.retryable());
        assertEquals(actual.requisitionId(), result.resultBizId());
        assertEquals("VERIFY_STATE_MISMATCH", result.errorCode());
        assertEquals("ORDERED", result.result().get("status"));
        verifyNoMoreInteractionsAfterCreate();
    }

    @ParameterizedTest
    @MethodSource("changedReceipts")
    void executeCannotAcceptWriterReceiptWithDifferentFrozenSourceOrLine(PuCertifiedPurchaseResult current) {
        // 已变更的单据状态/版本保留原身份但返回失败；来源/行变化属于违反 writer 回执合同。
        when(writer.createForDecision(any(), eq(ACTOR), eq(10L), eq(101L), anyList(), any(FrozenContract.class))).thenReturn(current);
        if (!"DRAFT".equals(current.status()) || current.version() != 0) {
            assertFalse(adapter.execute(action(payload()), ACTOR).success());
        } else if (current.requisitionId().equals(receipt(true).requisitionId())
            && current.requisitionNo().equals(receipt(true).requisitionNo())) {
            assertThrows(ServiceException.class, () -> adapter.execute(action(payload()), ACTOR));
        } else {
            // 首次执行仅认 writer 的实际单据身份，而非客户端猜测的编号/ID。
            assertTrue(adapter.execute(action(payload()), ACTOR).success());
        }
    }

    @Test
    void wrongActionTypeFixedMetadataRequestKeyAndDisabledTenantFailClosed() {
        var valid = action(payload());
        for (var invalid : List.of(
            new DecisionActionProposal(0L, valid.actionType(), valid.title(), valid.displayReason(), TARGET, valid.payload(),
                valid.riskLevel(), true, valid.permissionCode(), KEY, valid.executorCode(), 1, List.of()),
            new DecisionActionProposal(valid.actionId(), DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT, valid.title(),
                valid.displayReason(), TARGET, valid.payload(), valid.riskLevel(), true, valid.permissionCode(), KEY,
                valid.executorCode(), 1, List.of()),
            new DecisionActionProposal(valid.actionId(), valid.actionType(), valid.title(), valid.displayReason(), TARGET,
                valid.payload(), valid.riskLevel(), false, valid.permissionCode(), KEY, valid.executorCode(), 1, List.of()),
            new DecisionActionProposal(valid.actionId(), valid.actionType(), valid.title(), valid.displayReason(), TARGET,
                valid.payload(), valid.riskLevel(), true, "pu:requisition:edit", KEY, valid.executorCode(), 1, List.of()),
            new DecisionActionProposal(valid.actionId(), valid.actionType(), valid.title(), valid.displayReason(), TARGET,
                valid.payload(), valid.riskLevel(), true, valid.permissionCode(), "too-short", valid.executorCode(), 1, List.of()),
            new DecisionActionProposal(valid.actionId(), valid.actionType(), valid.title(), valid.displayReason(), TARGET,
                valid.payload(), valid.riskLevel(), true, valid.permissionCode(), KEY, "schedulePlanDraft", 1, List.of()))) {
            assertThrows(ServiceException.class, () -> adapter.execute(invalid, ACTOR));
        }
        tenant.when(TenantHelper::isEnable).thenReturn(false);
        assertThrows(ServiceException.class, () -> adapter.execute(valid, ACTOR));
        verifyNoInteractions(writer);
    }

    @Test
    void executeAndRecoveryMustPassFullFrozenContractToWriterNotOnlyEnvelope() {
        var action = action(payload());
        var result = executeReceipt(action);
        var created = ArgumentCaptor.forClass(FrozenContract.class);
        verify(writer).createForDecision(eq(command()), eq(ACTOR), eq(10L), eq(101L), eq(frozenLines()), created.capture());
        assertEquals(result.result().get("payloadFingerprint"), created.getValue().payloadFingerprint());
        assertEquals(frozenMetadata(payload()), created.getValue().metadata());
        assertThrows(UnsupportedOperationException.class, () -> created.getValue().metadata().put("vendorVersion", "changed"));
        clearInvocations(writer);
        when(writer.recoverForDecision(any(), eq(ACTOR), eq(10L), eq(101L), anyList(), any(FrozenContract.class)))
            .thenReturn(receipt(true));
        assertSame(result, adapter.verify(action, result, ACTOR));
        var recovered = ArgumentCaptor.forClass(FrozenContract.class);
        verify(writer).recoverForDecision(eq(command()), eq(ACTOR), eq(10L), eq(101L), eq(frozenLines()), recovered.capture());
        assertEquals(created.getValue(), recovered.getValue(), "恢复必须以同一完整合同核对永久请求");
        verify(writer, never()).createForDecision(any(), any(), anyLong(), anyLong(), anyList());
        verify(writer, never()).recoverForDecision(any(), any(), anyLong(), anyLong(), anyList());
        verifyNoMoreInteractions(writer);
    }

    @ParameterizedTest
    @MethodSource("changedFullIntents")
    void everyFrozenIdentityVersionAndTargetAffectsContractSentToPermanentWriter(DecisionActionProposal changed) {
        var stoppedAtWriter = new ServiceException("STOP_AT_WRITER");
        when(writer.createForDecision(any(), eq(ACTOR), anyLong(), anyLong(), anyList(), any(FrozenContract.class)))
            .thenThrow(stoppedAtWriter);
        assertSame(stoppedAtWriter, assertThrows(ServiceException.class, () -> adapter.execute(action(payload()), ACTOR)));
        var baseline = ArgumentCaptor.forClass(FrozenContract.class);
        verify(writer).createForDecision(any(), eq(ACTOR), anyLong(), anyLong(), anyList(), baseline.capture());
        clearInvocations(writer);
        assertSame(stoppedAtWriter, assertThrows(ServiceException.class, () -> adapter.execute(changed, ACTOR)));
        var changedContract = ArgumentCaptor.forClass(FrozenContract.class);
        verify(writer).createForDecision(any(), eq(ACTOR), anyLong(), anyLong(), anyList(), changedContract.capture());
        assertNotEquals(baseline.getValue().payloadFingerprint(), changedContract.getValue().payloadFingerprint());
        assertTrue(changedContract.getValue().payloadFingerprint().matches("[0-9a-f]{64}"));
        assertEquals(frozenMetadata(changed.payload()), changedContract.getValue().metadata());
        verifyNoMoreInteractions(writer);
    }

    static Stream<DecisionActionProposal> changedFullIntents() {
        List<DecisionActionProposal> changes = new ArrayList<>();
        for (String field : List.of("materialVersion", "vendorVersion", "supplierTermsVersion")) {
            changes.add(action(changed(field, "d".repeat(64))));
        }
        changes.add(action(changed("supplierTermsId", "401")));
        var units = payload(); units.put("baseUomId", "301"); units.put("orderUomId", "301");
        var lineUnits = frozen(); lineUnits.put("baseUomId", "301"); lineUnits.put("orderUomId", "301");
        units.put("frozenLines", List.of(lineUnits)); changes.add(action(units));
        var conversions = payload(); conversions.put("baseUomVersion", "18"); conversions.put("orderUomVersion", "18");
        conversions.put("conversionVersion", "18"); changes.add(action(conversions));
        changes.add(action(changed("runId", "11")));
        changes.add(action(changed("materialId", "9007199254740994")));
        changes.add(action(changed("vendorId", "201")));
        changes.add(action(changed("windowEnd", "2026-10-21")));
        changes.add(action(changed("expectedSourceFingerprint", "d".repeat(64))));
        var source = payload(); source.put("requirement_detail_ids", List.of("9007199254741002"));
        var lineSource = frozen(); lineSource.put("detailId", "9007199254741002");
        source.put("frozenLines", List.of(lineSource)); changes.add(action(source));
        var quantities = frozen(); quantities.put("coveredDemandBase", "6.0000"); quantities.put("requestedOrderQty", "6.0000");
        changes.add(action(changed("frozenLines", List.of(quantities))));
        changes.add(action(changedLine("requiredDate", "2026-10-16")));
        var original = action(payload());
        for (DecisionBusinessRef target : List.of(new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 12L, "WO-12"))) {
            changes.add(new DecisionActionProposal(original.actionId(), original.actionType(), original.title(),
                original.displayReason(), target, original.payload(), original.riskLevel(), original.approvalRequired(),
                original.permissionCode(), key(original.payload(),target), original.executorCode(), 1, List.of()));
        }
        changes.add(new DecisionActionProposal(102L, original.actionType(), original.title(), original.displayReason(), TARGET,
            original.payload(), original.riskLevel(), true, original.permissionCode(), KEY, original.executorCode(), 1, List.of()));
        return changes.stream();
    }

    private static Map<String, String> frozenMetadata(Map<String, Object> payload) {
        var metadata = new LinkedHashMap<String, String>();
        for (String key : List.of("materialVersion", "vendorVersion", "supplierTermsId", "supplierTermsVersion",
            "baseUomId", "baseUomVersion", "orderUomId", "orderUomVersion", "conversionVersion")) {
            metadata.put(key, (String) payload.get(key));
        }
        return Map.copyOf(metadata);
    }

    private DecisionActionExecutionResult executeReceipt(DecisionActionProposal action) {
        when(writer.createForDecision(any(), eq(ACTOR), eq(10L), eq(101L), anyList(), any(FrozenContract.class))).thenReturn(receipt(false));
        return adapter.execute(action, ACTOR);
    }

    private void verifyNoMoreInteractionsAfterCreate() {
        verify(writer).createForDecision(eq(command()), eq(ACTOR), eq(10L), eq(101L), eq(frozenLines()), any(FrozenContract.class));
        verifyNoMoreInteractions(writer);
    }

    private static PuCertifiedPurchaseCommand command() {
        return new PuCertifiedPurchaseCommand(KEY, MATERIAL, 200L, LocalDate.of(2026, 10, 20), List.of(DETAIL), FINGERPRINT);
    }

    private static List<FrozenLine> frozenLines() {
        return List.of(new FrozenLine(DETAIL, 300L, new BigDecimal("5.0000"), 300L,
            new BigDecimal("5.0000"), LocalDate.of(2026, 10, 15)));
    }

    private static PuCertifiedPurchaseResult receipt(boolean replayed) {
        return new PuCertifiedPurchaseResult(9007199254741100L, "PR-CERT-100", "DRAFT", 0, replayed, FINGERPRINT,
            List.of(new Line(9007199254741200L, DETAIL, MATERIAL, 300L, new BigDecimal("5.0000"), LocalDate.of(2026, 10, 15))));
    }

    private static DecisionActionProposal action(Map<String, Object> payload) {
        return new DecisionActionProposal(101L, DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
            "draft", "reason", TARGET, payload, DecisionRiskLevel.HIGH, true,
            "pu:requisition:add", key(payload,TARGET), "purchaseRequisitionDraft", 1, List.of());
    }

    /** 合成合法动作重算统一键；畸形载荷留给被测codec拒绝，不在夹具提前失败。 */
    private static String key(Map<String,Object> payload,DecisionBusinessRef target) {
        try {
            var contract=DecisionPurchaseContractCodec.parse(payload,null);
            return DecisionPurchaseContractCodec.idempotencyKey(TENANT,Long.valueOf((String)payload.get("runId")),target,contract);
        } catch(ServiceException invalid) {return KEY;}
    }

    private static Map<String, Object> changed(String key, Object value) {
        var map = payload(); map.put(key, value); return map;
    }

    private static Map<String, Object> changedLine(String key, Object value) {
        var row = frozen(); row.put(key, value); return changed("frozenLines", List.of(row));
    }

    private static Map<String, Object> payload() {
        return new LinkedHashMap<>(Map.ofEntries(
            Map.entry("purchase_payload_version", 2), Map.entry("runId", "10"),
            Map.entry("materialId", MATERIAL.toString()), Map.entry("vendorId", "200"),
            Map.entry("windowEnd", "2026-10-20"), Map.entry("materialVersion", "b".repeat(64)),
            Map.entry("vendorVersion", "c".repeat(64)), Map.entry("supplierTermsId", "400"),
            Map.entry("supplierTermsVersion", "terms-revision-41"), Map.entry("baseUomId", "300"),
            Map.entry("baseUomVersion", "17"), Map.entry("orderUomId", "300"),
            Map.entry("orderUomVersion", "17"), Map.entry("conversionVersion", "17"),
            Map.entry("requirement_detail_ids", List.of(DETAIL.toString())),
            Map.entry("frozenLines", List.of(frozen())), Map.entry("expectedSourceFingerprint", FINGERPRINT)));
    }

    private static Map<String, Object> frozen() {
        return new LinkedHashMap<>(Map.of("detailId", DETAIL.toString(), "baseUomId", "300",
            "coveredDemandBase", "5.0000", "orderUomId", "300", "requestedOrderQty", "5.0000", "requiredDate", "2026-10-15"));
    }
}
