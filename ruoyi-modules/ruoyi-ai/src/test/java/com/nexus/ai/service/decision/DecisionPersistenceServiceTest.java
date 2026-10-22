package com.nexus.ai.service.decision;

import static com.nexus.ai.service.decision.DecisionTestFixtures.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.hutool.extra.spring.SpringUtil;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.*;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;
import com.nexus.common.ai.domain.entity.*;
import com.nexus.common.ai.mapper.*;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.json.config.JacksonConfig;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

// 同名类型说明：本文件同时使用 com.nexus.common.json.config.JacksonConfig 和
// com.nexus.common.web.config.JacksonConfig，后者保留全限定名以区分。

@Tag("dev")
class DecisionPersistenceServiceTest {
    private final AiDecisionRunMapper runs = mock(AiDecisionRunMapper.class);
    private final AiDecisionEvidenceMapper evidence = mock(AiDecisionEvidenceMapper.class);
    private final AiDecisionActionMapper actions = mock(AiDecisionActionMapper.class);
    private final ObjectMapper mapper =
            new ObjectMapper()
                    .registerModule(new JacksonConfig().registerJavaTimeModule())
                    .registerModule(
                            new com.nexus.common.web.config.JacksonConfig().bigNumberModule());
    private final DecisionPersistenceService store = new DecisionPersistenceService(
        runs, evidence, actions, new DecisionRunStateMachine(), new DecisionEvidenceService(), mapper);
    @BeforeAll static void initMyBatisMetadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AiDecisionRun.class);
        TableInfoHelper.initTableInfo(assistant, AiDecisionEvidence.class);
        TableInfoHelper.initTableInfo(assistant, AiDecisionAction.class);
    }

    private static final DecisionBusinessRef REF = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 9007199254740993L, "WO-11");
    private static final DecisionActor ACTOR = new DecisionActor("TENANT-A", 7L, "tester", Set.of("ai:decision:run"));

    private AiDecisionRun current(DecisionRunStatus status, int version) {
        AiDecisionRun row = new AiDecisionRun();
        row.setId(99L); row.setTenantId("TENANT-A"); row.setSceneCode("DELIVERY_RISK");
        row.setBizType(REF.type().name()); row.setBizId(REF.id()); row.setBizCode(REF.code());
        row.setStatus(status.name()); row.setVersion(version);
        return row;
    }

    @Test void onlyNewRunInsertSetsContractVersionTwo() {
        withTenant(
                () -> {
                    var request = new DeliveryRiskRunRequest();
                    request.setBizType(REF.type());
                    request.setBizId(REF.id());
                    request.setHorizonDays(30);
                    when(runs.insert(any(AiDecisionRun.class)))
                            .thenAnswer(
                                    call -> {
                                        ((AiDecisionRun) call.getArgument(0)).setId(99L);
                                        return 1;
                                    });
                    var created = store.createRun(request, ACTOR);
                    assertEquals(2, created.getDecisionContractVersion(), "只有新插入运行应显式标记新版合同");
                    var inserted = ArgumentCaptor.forClass(AiDecisionRun.class);
                    verify(runs).insert(inserted.capture());
                    assertEquals(2, inserted.getValue().getDecisionContractVersion(), "版本必须随新运行插入");
                    verify(runs, never()).update(isNull(), any());
                    return null;
                });
    }

    @Test void legacyNullRunIsNeverUpgraded() {
        withTenant(
                () -> {
                    var legacy = current(DecisionRunStatus.CREATED, 0);
                    when(runs.selectOne(any())).thenReturn(legacy);
                    when(runs.update(isNull(), any())).thenReturn(1);
                    store.transition(99L, DecisionRunStatus.CREATED, DecisionRunStatus.RUNNING);
                    legacy.setStatus(DecisionRunStatus.RUNNING.name());
                    store.attachAnalysisFlow(99L, 77L);
                    assertNull(legacy.getDecisionContractVersion(), "旧运行的空合同版本不得补写");
                    var updates = ArgumentCaptor.forClass(Wrapper.class);
                    verify(runs, times(2)).update(isNull(), updates.capture());
                    for (var update : updates.getAllValues()) {
                        assertFalse(
                                ((LambdaUpdateWrapper<?>) update)
                                        .getSqlSet()
                                        .contains("decision_contract_version"),
                                "普通状态及流程绑定不得回填合同版本");
                    }
                    return null;
                });
    }

    @ParameterizedTest(name = "完整采购信封拒绝反例：{0}")
    @MethodSource("invalidRecoveryEnvelopeCases")
    void recoveryIndependentlyRejectsIncompleteOrDriftedEnvelope(String label, String field, Object replacement,
                                                                boolean remove, boolean line) {
        withTenant(
                "000001",
                () -> {
                    when(runs.selectOne(any())).thenReturn(v2Run(DecisionRunStatus.EXECUTING, 3));
                    when(actions.selectOne(any())).thenReturn(recoveryAction("EXECUTING", 4));
                    when(actions.update(isNull(), any())).thenReturn(1);
                    Map<String, Object> envelope =
                            new LinkedHashMap<>(recoveryResult(true).result());
                    if ("空信封".equals(label)) envelope = null;
                    else if ("空集合信封".equals(label)) envelope = Map.of();
                    else if (line) {
                        var row =
                                new LinkedHashMap<>(
                                        (Map<String, Object>)
                                                ((List<?>) envelope.get("lines")).get(0));
                        if (remove) row.remove(field);
                        else row.put(field, replacement);
                        envelope.put("lines", List.of(row));
                    } else if (remove) envelope.remove(field);
                    else envelope.put(field, replacement);
                    var invalid = resultWithEnvelope(envelope);
                    assertThrows(
                            ServiceException.class,
                            () -> recoveredSave(501L, 4, invalid),
                            "master 必须独立拒绝：" + label);
                    verify(actions, never()).update(isNull(), any());
                    return null;
                });
    }

    private static Stream<Arguments> invalidRecoveryEnvelopeCases() {
        var cases = new ArrayList<Arguments>();
        cases.add(Arguments.of("空信封", "", null, false, false));
        cases.add(Arguments.of("空集合信封", "", null, false, false));
        for (String field : List.of("purchase_payload_version", "runId", "actionId", "tenantId", "requestKey", "materialId", "vendorId",
            "sourceType", "sourceFingerprint", "payloadFingerprint", "status", "versionMarker", "beforeState", "afterState", "replayed", "lines"))
            cases.add(Arguments.of("顶层少字段：" + field, field, null, true, false));
        Map<String, Object> changed = Map.ofEntries(Map.entry("purchase_payload_version", "2"), Map.entry("runId", "100"),
            Map.entry("actionId", "502"), Map.entry("tenantId", "000002"), Map.entry("requestKey", "AI_PURCHASE_V2:" + "b".repeat(64)),
            Map.entry("materialId", "101"), Map.entry("vendorId", "201"), Map.entry("sourceType", "MANUAL"),
            Map.entry("sourceFingerprint", "c".repeat(64)), Map.entry("payloadFingerprint", "d".repeat(64)), Map.entry("status", "CLOSED"),
            Map.entry("versionMarker", "v1"), Map.entry("beforeState", "ABSENT"), Map.entry("afterState", "CANCELLED"),
            Map.entry("replayed", "true"), Map.entry("lines", List.of()), Map.entry("verified", true));
        changed.forEach((field, value) -> cases.add(Arguments.of("顶层漂移或未知字段：" + field, field, value, false, false)));
        for (String field : List.of("lineId", "requirementDetailId", "materialId", "baseUomId", "orderUomId", "coveredDemandBase",
            "requestedOrderQty", "requiredDate")) cases.add(Arguments.of("行少字段：" + field, field, null, true, true));
        Map<String, Object> changedLine = Map.ofEntries(Map.entry("lineId", "0"), Map.entry("requirementDetailId", "1101"),
            Map.entry("materialId", "101"), Map.entry("baseUomId", "2"), Map.entry("orderUomId", "2"),
            Map.entry("coveredDemandBase", "4"), Map.entry("requestedOrderQty", "3.0"), Map.entry("requiredDate", "2026-10-04"),
            Map.entry("unit", "伪造单位"));
        changedLine.forEach((field, value) -> cases.add(Arguments.of("行漂移或未知字段：" + field, field, value, false, true)));
        cases.add(Arguments.of("行标识不能为数字", "lineId", 801L, false, true));
        cases.add(Arguments.of("行标识不可溢出", "lineId", "9223372036854775808", false, true));
        cases.add(Arguments.of("行标识不能前导零", "lineId", "0801", false, true));
        cases.add(Arguments.of("顶层未知空字段", "unknown", null, false, false));
        cases.add(Arguments.of("行未知空字段", "unknown", null, false, true));
        return cases.stream();
    }

    @ParameterizedTest(name = "原业务引用保护：{0}／{1}")
    @MethodSource("existingRecoveryReferenceCases")
    void executingAndFailedRecoveryCannotOverwriteAnyExistingConflictingReference(String status, String corruption) {
        withTenant(
                "000001",
                () -> {
                    when(runs.selectOne(any())).thenReturn(v2Run(DecisionRunStatus.EXECUTING, 3));
                    var row = recoveryAction(status, 4);
                    switch (corruption) {
                        case "错误业务标识" -> row.setResultBizId(701L);
                        case "错误业务类型" -> row.setResultBizType("PRD_SCHEDULE_PLAN");
                        case "错误业务编号" -> row.setResultBizCode("PR-701");
                        case "同标识但不完整引用" -> row.setResultBizId(700L);
                        case "同完整引用但没有信封" -> setExistingRecovery(row, recoveryResult(false), false);
                        case "同信封但没有引用" -> row.setResultJson(recoveryJson(recoveryResult(false)));
                        case "信封来源漂移" -> {
                            var changed = new LinkedHashMap<>(recoveryResult(false).result());
                            changed.put("sourceFingerprint", "b".repeat(64));
                            setExistingRecovery(row, resultWithEnvelope(changed), true);
                        }
                        case "信封行标识漂移" -> {
                            var changed = new LinkedHashMap<>(recoveryResult(false).result());
                            var line =
                                    new LinkedHashMap<>(
                                            (Map<String, Object>)
                                                    ((List<?>) changed.get("lines")).get(0));
                            line.put("lineId", "802");
                            changed.put("lines", List.of(line));
                            setExistingRecovery(row, resultWithEnvelope(changed), true);
                        }
                        case "旧空信封" -> setExistingRecovery(row, resultWithEnvelope(null), true);
                        case "未知旧JSON字段" -> {
                            setExistingRecovery(row, recoveryResult(false), true);
                            row.setResultJson(
                                    row.getResultJson().replaceFirst("\\{", "{\"unknown\":true,"));
                        }
                        case "旧结果标识与引用冲突" ->
                                setExistingRecovery(
                                        row,
                                        new DecisionActionExecutionResult(
                                                true,
                                                false,
                                                "PU_PURCHASE_REQUISITION",
                                                701L,
                                                "PR-701",
                                                recoveryResult(false).result(),
                                                null,
                                                null),
                                        true);
                        case "无效旧JSON" -> row.setResultJson("null");
                        default -> throw new AssertionError("测试反例必须明确");
                    }
                    when(actions.selectOne(any())).thenReturn(row);
                    when(actions.update(isNull(), any())).thenReturn(1);
                    var before =
                            List.of(
                                    Objects.toString(row.getResultBizId()),
                                    Objects.toString(row.getResultBizType()),
                                    Objects.toString(row.getResultBizCode()),
                                    Objects.toString(row.getResultJson()));
                    assertThrows(
                            ServiceException.class,
                            () -> recoveredSave(501L, 4, recoveryResult(true)),
                            "已有引用或信封不能覆盖：" + corruption);
                    verify(actions, never()).update(isNull(), any());
                    assertEquals(
                            before,
                            List.of(
                                    Objects.toString(row.getResultBizId()),
                                    Objects.toString(row.getResultBizType()),
                                    Objects.toString(row.getResultBizCode()),
                                    Objects.toString(row.getResultJson())),
                            "拒绝不得清空或改写原引用");
                    return null;
                });
    }

    private static Stream<Arguments> existingRecoveryReferenceCases() {
        return Stream.of("EXECUTING", "FAILED")
                .flatMap(
                        status ->
                                Stream.of(
                                                "错误业务标识",
                                                "错误业务类型",
                                                "错误业务编号",
                                                "同标识但不完整引用",
                                                "同完整引用但没有信封",
                                                "同信封但没有引用",
                                                "信封来源漂移",
                                                "信封行标识漂移",
                                                "旧空信封",
                                                "未知旧JSON字段",
                                                "旧结果标识与引用冲突",
                                                "无效旧JSON")
                                        .map(corruption -> Arguments.of(status, corruption)));
    }

    @ParameterizedTest(name = "采购信封必须核验全部结果行：{0}")
    @MethodSource("multiLineRecoveryCases")
    void recoveryValidatesEveryLineAndUniqueBusinessLineIds(String mutation) {
        withTenant(
                "000001",
                () -> {
                    when(runs.selectOne(any())).thenReturn(v2Run(DecisionRunStatus.EXECUTING, 3));
                    var row = recoveryAction("EXECUTING", 4);
                    var original = DecisionPurchaseProposalServiceTest.contract(100L, 200L);
                    var second =
                            new DecisionPurchaseContractV2.Line(
                                    1101L,
                                    1L,
                                    new BigDecimal("4"),
                                    1L,
                                    new BigDecimal("4"),
                                    LocalDate.of(2026, 10, 4));
                    var twoLines =
                            new DecisionPurchaseContractV2(
                                    original.materialId(),
                                    original.vendorId(),
                                    original.windowEnd(),
                                    original.materialVersion(),
                                    original.vendorVersion(),
                                    original.supplierTermsId(),
                                    original.supplierTermsVersion(),
                                    original.baseUomId(),
                                    original.baseUomVersion(),
                                    original.orderUomId(),
                                    original.orderUomVersion(),
                                    original.conversionVersion(),
                                    List.of(1100L, 1101L),
                                    List.of(original.frozenLines().get(0), second),
                                    original.expectedSourceFingerprint());
                    try {
                        row.setPayloadJson(
                                mapper.writeValueAsString(
                                        DecisionPurchaseContractCodec.toPayload(99L, twoLines)));
                    } catch (Exception error) {
                        throw new AssertionError("两行冻结合同应可序列化", error);
                    }
                    when(actions.selectOne(any())).thenReturn(row);
                    when(actions.update(isNull(), any())).thenReturn(1);
                    var envelope =
                            new LinkedHashMap<>(
                                    recoveryResult(row, recoveryActor(), true).result());
                    var lines = new ArrayList<Map<String, Object>>();
                    for (Object line : (List<?>) envelope.get("lines"))
                        lines.add(new LinkedHashMap<>((Map<String, Object>) line));
                    switch (mutation) {
                        case "完整两行" -> {}
                        case "第二行重复单据行标识" -> lines.get(1).put("lineId", lines.get(0).get("lineId"));
                        case "第二行来源漂移" -> lines.get(1).put("requirementDetailId", "1102");
                        case "第二行单位漂移" -> lines.get(1).put("baseUomId", "2");
                        case "第二行数量漂移" -> lines.get(1).put("requestedOrderQty", "5");
                        case "少第二行" -> lines.remove(1);
                        case "交换来源行顺序" -> Collections.reverse(lines);
                        default -> throw new AssertionError("多行反例必须明确");
                    }
                    envelope.put("lines", lines);
                    var result = resultWithEnvelope(envelope);
                    if ("完整两行".equals(mutation)) {
                        assertDoesNotThrow(() -> recoveredSave(501L, 4, result), "完整逐行信封应正常恢复");
                        verify(actions, times(1)).update(isNull(), any());
                    } else {
                        assertThrows(
                                ServiceException.class,
                                () -> recoveredSave(501L, 4, result),
                                "不能只核验第一行：" + mutation);
                        verify(actions, never()).update(isNull(), any());
                    }
                    return null;
                });
    }

    private static Stream<String> multiLineRecoveryCases() {
        return Stream.of("完整两行", "第二行重复单据行标识", "第二行来源漂移", "第二行单位漂移", "第二行数量漂移", "少第二行", "交换来源行顺序");
    }

    @Test void existingSameReceiptAllowsOnlyLegalReplayObservations() {
        withTenant("000001", () -> {
            when(runs.selectOne(any())).thenReturn(v2Run(DecisionRunStatus.EXECUTING, 3));
            var row = recoveryAction("FAILED", 4); setExistingRecovery(row, recoveryResult(false), true);
            when(actions.selectOne(any())).thenReturn(row); when(actions.update(isNull(), any())).thenReturn(1);
            assertDoesNotThrow(() -> recoveredSave(501L, 4, recoveryResult(true)), "同单同完整信封可从失败恢复，合法观察差异不另造采购");
            verify(actions, times(1)).update(isNull(), any());
            return null;
        });
    }

    @Test void boundExecutionActorIsRebuiltBeforeFingerprintValidation() {
        withTenant("000001", () -> {
            var run = v2Run(DecisionRunStatus.EXECUTING, 3);
            when(runs.selectOne(any())).thenReturn(run); when(actions.selectOne(any())).thenReturn(recoveryAction("EXECUTING", 4));
            var other = new DecisionActor("000001", 8L, "另一执行员", recoveryActor().permissions());
            var mismatched = recoveryResult(recoveryAction("EXECUTING", 4), other, true);
            assertThrows(ServiceException.class, () -> recoveredSave(501L, 4, mismatched), "不能借另一执行人的正确格式摘要恢复原动作");
            run.setApprovalContextJson(null);
            assertThrows(ServiceException.class, () -> recoveredSave(501L, 4, recoveryResult(true)), "没有原绑定执行身份不得回写成功");
            verify(actions, never()).update(isNull(), any());
            return null;
        });
    }

    @Test void anomalousRecoveryUpdateCountCannotBeAcceptedAsConcurrentSuccess() {
        withTenant("000001", () -> {
            when(runs.selectOne(any())).thenReturn(v2Run(DecisionRunStatus.EXECUTING, 3));
            var initial = recoveryAction("EXECUTING", 4); var latest = recoveryAction("SUCCEEDED", 5);
            setExistingRecovery(latest, recoveryResult(false), true);
            when(actions.selectOne(any())).thenReturn(initial, latest); when(actions.update(isNull(), any())).thenReturn(2);
            assertThrows(ServiceException.class, () -> recoveredSave(501L, 4, recoveryResult(true)), "多行受影响必须直接失败以触发回滚");
            verify(actions, times(1)).selectOne(any());
            return null;
        });
    }

    @Test void anomalousFinishUpdateCountThrowsRatherThanReturningRaceFalse() {
        withTenant("000001", () -> {
            when(runs.selectOne(any())).thenReturn(v2Run(DecisionRunStatus.EXECUTING, 3));
            when(actions.selectList(any())).thenReturn(List.of(recoveryAction("SUCCEEDED", 5)));
            when(runs.update(isNull(), any())).thenReturn(2);
            assertThrows(ServiceException.class, () -> recoveredFinish(99L, 3), "运行多行更新不能当普通竞争失败");
            return null;
        });
    }

    @Test void concurrentReceiptIsRevalidatedBeforeSameDocumentAcceptance() {
        withTenant(
                "000001",
                () -> {
                    when(runs.selectOne(any())).thenReturn(v2Run(DecisionRunStatus.EXECUTING, 3));
                    var initial = recoveryAction("EXECUTING", 4);
                    var latest = recoveryAction("SUCCEEDED", 5);
                    var invalid = new LinkedHashMap<>(recoveryResult(false).result());
                    invalid.put("verified", true);
                    setExistingRecovery(latest, resultWithEnvelope(invalid), true);
                    when(actions.selectOne(any())).thenReturn(initial, latest);
                    when(actions.update(isNull(), any())).thenReturn(0);
                    assertThrows(
                            ServiceException.class,
                            () -> recoveredSave(501L, 4, recoveryResult(true)),
                            "CAS竞争后的信封仍须完整独立复核");
                    verify(actions, times(1)).update(isNull(), any());
                    return null;
                });
    }

    private String recoveryJson(DecisionActionExecutionResult result) {
        try { return mapper.writeValueAsString(result); }
        catch (Exception error) { throw new AssertionError("恢复测试信封必须可序列化", error); }
    }

    private void setExistingRecovery(AiDecisionAction row, DecisionActionExecutionResult result, boolean envelope) {
        row.setResultBizId(700L); row.setResultBizType("PU_PURCHASE_REQUISITION"); row.setResultBizCode("PR-700");
        if (envelope) row.setResultJson(recoveryJson(result));
    }

    @Test void recoveredResultCasRejectsTerminalRunAndVersionConflict() {
        withTenant("000001", () -> {
            var row = recoveryAction("EXECUTING", 4);
            when(actions.selectOne(any())).thenReturn(row);
            when(runs.selectOne(any())).thenReturn(v2Run(DecisionRunStatus.FAILED, 3));
            assertThrows(ServiceException.class, () -> recoveredSave(501L, 4, recoveryResult(false)),
                "终态运行不得补成功结果");
            var running = v2Run(DecisionRunStatus.EXECUTING, 3); running.setDecisionContractVersion(2);
            when(runs.selectOne(any())).thenReturn(running);
            assertThrows(ServiceException.class, () -> recoveredSave(501L, 3, recoveryResult(false)),
                "恢复必须匹配读取到的动作版本");
            row.setPurchasePayloadVersion(null);
            assertThrows(ServiceException.class, () -> recoveredSave(501L, 4, recoveryResult(false)),
                "旧采购动作不得借恢复回填版本");
            verify(actions, never()).update(isNull(), any());
            return null;
        });
    }

    @Test void recoveredResultWritesTenantRunStatusVersionAndRealBusinessReference() {
        withTenant(
                "000001",
                () -> {
                    var running = v2Run(DecisionRunStatus.EXECUTING, 3);
                    running.setDecisionContractVersion(2);
                    when(runs.selectOne(any())).thenReturn(running);
                    when(actions.selectOne(any())).thenReturn(recoveryAction("FAILED", 4));
                    when(actions.update(isNull(), any())).thenReturn(1);
                    recoveredSave(501L, 4, recoveryResult(true));
                    var update = ArgumentCaptor.forClass(Wrapper.class);
                    verify(actions).update(isNull(), update.capture());
                    var values = params(update.getValue()).values();
                    for (Object expected :
                            List.of(
                                    "000001",
                                    99L,
                                    501L,
                                    4,
                                    "APPROVED",
                                    "FAILED",
                                    "SUCCEEDED",
                                    700L,
                                    "PU_PURCHASE_REQUISITION",
                                    "PR-700")) {
                        assertTrue(
                                values.contains(expected), "恢复回写必须绑定租户、运行、版本和真实业务引用：" + expected);
                    }
                    verify(runs, never()).update(isNull(), any());
                    return null;
                });
    }

    @Test void concurrentlyRecoveredSameStableResultIgnoresReplayObservation() {
        withTenant("000001", () -> {
            var running = v2Run(DecisionRunStatus.EXECUTING, 3); running.setDecisionContractVersion(2);
            when(runs.selectOne(any())).thenReturn(running);
            var inflight = recoveryAction("EXECUTING", 4);
            var succeeded = recoveryAction("SUCCEEDED", 5);
            succeeded.setResultBizType("PU_PURCHASE_REQUISITION"); succeeded.setResultBizId(700L);
            succeeded.setResultBizCode("PR-700");
            try { succeeded.setResultJson(mapper.writeValueAsString(recoveryResult(false))); }
            catch (Exception error) { throw new AssertionError("恢复测试结果应可序列化", error); }
            when(actions.selectOne(any())).thenReturn(inflight, succeeded);
            when(actions.update(isNull(), any())).thenReturn(0);
            assertDoesNotThrow(() -> recoveredSave(501L, 4, recoveryResult(true)),
                "另一执行者回写同一采购时，重放及执行前状态差异不构成冲突");
            verify(actions, times(1)).update(isNull(), any());
            var changed = new DecisionActionExecutionResult(true, false, "PU_PURCHASE_REQUISITION", 701L,
                "PR-701", recoveryResult(true).result(), null, null);
            assertThrows(ServiceException.class, () -> recoveredSave(501L, 4, changed),
                "不同稳定业务引用必须拒绝覆盖");
            return null;
        });
    }

    @Test void succeededRecoveryCannotAcceptNegativeExpectedVersion() {
        withTenant("000001", () -> {
            when(runs.selectOne(any())).thenReturn(v2Run(DecisionRunStatus.EXECUTING, 3));
            var row = recoveryAction("SUCCEEDED", 5); row.setResultBizType("PU_PURCHASE_REQUISITION");
            row.setResultBizId(700L); row.setResultBizCode("PR-700");
            try { row.setResultJson(mapper.writeValueAsString(recoveryResult(false))); }
            catch (Exception error) { throw new AssertionError("回执应可序列化", error); }
            when(actions.selectOne(any())).thenReturn(row);
            assertThrows(ServiceException.class, () -> recoveredSave(501L, -1, recoveryResult(true)),
                "即便并发已成功也不能接受无效的版本输入");
            verify(actions, never()).update(isNull(), any());
            return null;
        });
    }

    @Test void recoveredFinishMustReadCurrentActionsInsteadOfRepeatableReadSnapshot() {
        withTenant(
                "000001",
                () -> {
                    when(runs.selectOne(any())).thenReturn(v2Run(DecisionRunStatus.EXECUTING, 3));
                    when(actions.selectList(any()))
                            .thenReturn(List.of(recoveryAction("SUCCEEDED", 5)));
                    when(runs.update(isNull(), any())).thenReturn(1);
                    assertTrue(recoveredFinish(99L, 3), "读取全体批准动作现态后可收敛");
                    var query = ArgumentCaptor.forClass(Wrapper.class);
                    verify(actions).selectList(query.capture());
                    assertTrue(
                            query.getValue().getSqlSegment().contains("FOR UPDATE"),
                            "恢复事务不能用可重复读中的旧动作快照把混合运行提前收敛");
                    return null;
                });
    }

    @Test void persistedHorizonMustBeStrictIntegerRatherThanCoercedOldJson() {
        var run = current(DecisionRunStatus.RUNNING, 0);
        for (String value : List.of("\"30\"", "30.5", "true", "null", "9223372036854775808")) {
            run.setScoreConfigJson("{\"horizonDays\":" + value + "}");
            assertThrows(ServiceException.class, () -> store.requireHorizonDays(run),
                "冻结分析时段必须严格整数，不能从旧配置强制转换：" + value);
        }
    }

    @Test void packageVisibleRecoveryUsesRealSpringCglibProxyAcrossBeans() {
        withTenant(
                "000001",
                () -> {
                    var running = v2Run(DecisionRunStatus.EXECUTING, 3);
                    when(runs.selectOne(any())).thenReturn(running);
                    when(actions.selectOne(any())).thenReturn(recoveryAction("EXECUTING", 4));
                    when(actions.selectList(any()))
                            .thenReturn(List.of(recoveryAction("SUCCEEDED", 5)));
                    when(actions.update(isNull(), any()))
                            .thenAnswer(
                                    call -> {
                                        assertTrue(
                                                TransactionSynchronizationManager
                                                        .isActualTransactionActive(),
                                                "跨类调用包可见恢复方法时必须经过真实事务拦截器");
                                        assertEquals(
                                                "000001",
                                                TenantHelper.getDynamic(),
                                                "代理调用不得以请求字段替换动态租户");
                                        return 1;
                                    });
                    when(runs.update(isNull(), any()))
                            .thenAnswer(
                                    call -> {
                                        assertTrue(
                                                TransactionSynchronizationManager
                                                        .isActualTransactionActive(),
                                                "整单收敛同样必须在真实代理开启的事务内");
                                        return 1;
                                    });
                    var manager = new RecoveryMemoryTransactions();
                    try (var context = new AnnotationConfigApplicationContext()) {
                        context.register(RecoveryProxyConfiguration.class);
                        context.registerBean(
                                "transactionManager",
                                PlatformTransactionManager.class,
                                () -> manager);
                        context.registerBean(DecisionPersistenceService.class, () -> store);
                        context.registerBean(
                                RecoveryBeanCaller.class,
                                () ->
                                        new RecoveryBeanCaller(
                                                context.getBean(DecisionPersistenceService.class)));
                        context.refresh();
                        var proxy = context.getBean(DecisionPersistenceService.class);
                        assertTrue(AopUtils.isCglibProxy(proxy), "项目类代理应覆盖包可见事务方法");
                        assertNotSame(store, proxy, "调用方必须持有代理 Bean，而不是自行构造的目标对象");
                        var caller = context.getBean(RecoveryBeanCaller.class);
                        caller.save(501L, 4, recoveryResult(true));
                        assertTrue(caller.finish(99L, 3), "没有外层事务时跨 Bean 也能提交收敛");
                        assertThrows(
                                ServiceException.class,
                                () -> caller.save(501L, 3, recoveryResult(true)),
                                "恢复版本冲突通过真实事务代理回滚");
                        assertEquals(3, manager.begins, "三个跨 Bean 调用分别开启事务");
                        assertEquals(2, manager.commits, "两次成功调用分别提交");
                        assertEquals(1, manager.rollbacks, "失败调用回滚而不能留脏结果");
                        assertFalse(
                                TransactionSynchronizationManager.isActualTransactionActive(),
                                "代理完成后清理事务线程上下文");
                    }
                    return null;
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class RecoveryProxyConfiguration {}

    /** 模拟同包执行服务持有真实持久化代理；调用方没有外层事务。 */
    static class RecoveryBeanCaller {
        private final DecisionPersistenceService persistence;
        RecoveryBeanCaller(DecisionPersistenceService persistence) { this.persistence = persistence; }
        void save(Long actionId, int version, DecisionActionExecutionResult result) {
            persistence.saveRecoveredPurchaseResult(actionId, version, result);
        }
        boolean finish(Long runId, int version) { return persistence.finishRecoveredExecution(runId, version); }
    }

    /** 只验证真实 Spring 事务拦截与提交回滚，不伪装物理数据库事务。 */
    static class RecoveryMemoryTransactions extends AbstractPlatformTransactionManager {
        int begins; int commits; int rollbacks;
        @Override
        protected Object doGetTransaction() { return new Object(); }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            begins++;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }

    @Test void recoveredRunFinishesOnlyWhenAllApprovedActionsSucceeded() {
        withTenant(
                "000001",
                () -> {
                    var running = v2Run(DecisionRunStatus.EXECUTING, 3);
                    running.setDecisionContractVersion(2);
                    when(runs.selectOne(any())).thenReturn(running);
                    var purchase = recoveryAction("SUCCEEDED", 5);
                    var schedule = new AiDecisionAction();
                    schedule.setId(502L);
                    schedule.setRunId(99L);
                    schedule.setTenantId("000001");
                    schedule.setApprovalStatus("APPROVED");
                    schedule.setExecutionStatus("NOT_STARTED");
                    when(actions.selectList(any())).thenReturn(List.of(purchase, schedule));
                    assertFalse(recoveredFinish(99L, 3), "单个采购成功不能把未开始的排程动作伪造成整单成功");
                    schedule.setExecutionStatus("FAILED");
                    assertFalse(recoveredFinish(99L, 3), "仍有失败动作时应留在执行中");
                    schedule.setApprovalStatus("REJECTED");
                    when(runs.update(isNull(), any())).thenReturn(1);
                    assertTrue(recoveredFinish(99L, 3), "所有已批准动作成功后才可收敛运行");
                    var update = ArgumentCaptor.forClass(Wrapper.class);
                    verify(runs).update(isNull(), update.capture());
                    var values = params(update.getValue()).values();
                    for (Object expected : List.of("000001", 99L, 3, "EXECUTING", "SUCCEEDED")) {
                        assertTrue(values.contains(expected), "运行收敛必须带租户状态版本条件：" + expected);
                    }
                    return null;
                });
    }

    @Test void recoveredRunVersionConflictOrEmptyApprovalNeverFinishes() {
        withTenant("000001", () -> {
            var running = v2Run(DecisionRunStatus.EXECUTING, 3); running.setDecisionContractVersion(2);
            when(runs.selectOne(any())).thenReturn(running);
            when(actions.selectList(any())).thenReturn(List.of());
            assertFalse(recoveredFinish(99L, 3), "没有已批准动作不能空集成功");
            when(actions.selectList(any())).thenReturn(List.of(recoveryAction("SUCCEEDED", 5)));
            assertFalse(recoveredFinish(99L, 2), "版本不匹配时不得收敛");
            when(runs.update(isNull(), any())).thenReturn(0);
            assertFalse(recoveredFinish(99L, 3), "运行收敛竞争失败时应保留现态并返回失败");
            return null;
        });
    }

    private AiDecisionAction recoveryAction(String status, int version) {
        var row = new AiDecisionAction(); row.setId(501L); row.setRunId(99L); row.setTenantId("000001");
        row.setActionType("CREATE_PURCHASE_REQUISITION_DRAFT"); row.setPurchasePayloadVersion(2);
        row.setApprovalStatus("APPROVED"); row.setExecutionStatus(status); row.setVersion(version);
        row.setTargetBizType(REF.type().name()); row.setTargetBizId(REF.id()); row.setTargetBizCode(REF.code());
        row.setIdempotencyKey(v2Proposal().idempotencyKey());
        try { row.setPayloadJson(mapper.writeValueAsString(v2Proposal().payload())); }
        catch (Exception error) { throw new AssertionError("恢复合同应可序列化", error); }
        return row;
    }

    private DecisionActor recoveryActor() {
        return new DecisionActor("000001", 7L, "执行员", Set.of("ai:decision:execute", "pu:requisition:add"));
    }

    private DecisionActionExecutionResult recoveryResult(boolean replayed) {
        return recoveryResult(recoveryAction("EXECUTING", 4), recoveryActor(), replayed);
    }

    private DecisionActionExecutionResult recoveryResult(AiDecisionAction row, DecisionActor actor, boolean replayed) {
        try {
            var payload =
                    mapper.readValue(
                            row.getPayloadJson(), new TypeReference<Map<String, Object>>() {});
            var contract = DecisionPurchaseContractCodec.parse(payload, row.getRunId());
            var proposal = new DecisionActionProposal(row.getId(), DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
                row.getTitle(), row.getDisplayReason(), new DecisionBusinessRef(DecisionBusinessType.valueOf(row.getTargetBizType()),
                    row.getTargetBizId(), row.getTargetBizCode()), payload, DecisionRiskLevel.HIGH, true,
                "pu:requisition:add", row.getIdempotencyKey(), "purchaseRequisitionDraft", 1, List.of());
            var lines = new ArrayList<Map<String, Object>>();
            int lineId = 801;
            for (var line : contract.frozenLines()) {
                lines.add(Map.of("lineId", Integer.toString(lineId++), "requirementDetailId", line.detailId().toString(),
                    "materialId", contract.materialId().toString(), "baseUomId", line.baseUomId().toString(),
                    "orderUomId", line.orderUomId().toString(), "coveredDemandBase", line.coveredDemandBase().stripTrailingZeros().toPlainString(),
                    "requestedOrderQty", line.requestedOrderQty().stripTrailingZeros().toPlainString(), "requiredDate", line.requiredDate().toString()));
            }
            var envelope = Map.<String, Object>ofEntries(Map.entry("purchase_payload_version", 2), Map.entry("runId", row.getRunId().toString()),
                Map.entry("actionId", row.getId().toString()), Map.entry("tenantId", actor.tenantId()), Map.entry("requestKey", row.getIdempotencyKey()),
                Map.entry("materialId", contract.materialId().toString()), Map.entry("vendorId", contract.vendorId().toString()),
                Map.entry("sourceType", "AI_DECISION"), Map.entry("sourceFingerprint", contract.expectedSourceFingerprint()),
                Map.entry("payloadFingerprint", DecisionPurchaseContractCodec.payloadFingerprint(proposal, actor)), Map.entry("status", "DRAFT"),
                Map.entry("versionMarker", "v0"), Map.entry("beforeState", replayed ? "COMMITTED" : "ABSENT"),
                Map.entry("afterState", "DRAFT"), Map.entry("replayed", replayed), Map.entry("lines", lines));
            return resultWithEnvelope(envelope);
        } catch (Exception error) { throw new AssertionError("新版恢复测试必须构造完整真实结构的冻结信封", error); }
    }

    private DecisionActionExecutionResult resultWithEnvelope(Map<String, Object> envelope) {
        return new DecisionActionExecutionResult(true, false, "PU_PURCHASE_REQUISITION", 700L, "PR-700", envelope, null, null);
    }

    private void recoveredSave(Long id, int version, DecisionActionExecutionResult result) {
        invokeRecovery("saveRecoveredPurchaseResult", new Class<?>[] {Long.class, int.class,
            DecisionActionExecutionResult.class}, id, version, result);
    }

    private boolean recoveredFinish(Long id, int version) {
        return (boolean) invokeRecovery("finishRecoveredExecution", new Class<?>[] {Long.class, int.class}, id, version);
    }

    private Object invokeRecovery(String name, Class<?>[] types, Object... values) {
        try {
            var method = DecisionPersistenceService.class.getDeclaredMethod(name, types);
            assertFalse(Modifier.isPublic(method.getModifiers()), "恢复接口必须保持包内可见");
            return method.invoke(store, values);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException cause) throw cause;
            if (error.getCause() instanceof Error cause) throw cause;
            throw new AssertionError("恢复接口抛出非预期异常", error.getCause());
        } catch (ReflectiveOperationException error) { throw new AssertionError("缺少约定的包内恢复接口", error); }
    }

    @Test void purchaseActionPersistsExplicitVersionAndSameWirePayload() {
        withTenant("000001", () -> {
            var run = v2Run(DecisionRunStatus.RUNNING, 0);
            when(runs.selectOne(any())).thenReturn(run);
            when(actions.insert(any(AiDecisionAction.class))).thenAnswer(call -> {
                ((AiDecisionAction) call.getArgument(0)).setId(501L); return 1;
            });
            var proposal = v2Proposal();
            var saved = store.saveActions(99L, List.of(proposal), v2Actor()).get(0);
            assertEquals(2, saved.getPurchasePayloadVersion(), "只有完整新版采购才写入动作版本二");
            assertTrue(sameStoredPayload(proposal.payload(), saved.getPayloadJson()),
                "保存的 wire 必须与冻结端口产出的同份合同一致");
            return null;
        });
    }

    @Test void legacyRunAndOldPayloadCannotPersistNewPurchase() {
        withTenant("000001", () -> {
            var legacy = v2Run(DecisionRunStatus.RUNNING, 0); legacy.setDecisionContractVersion(null);
            when(runs.selectOne(any())).thenReturn(legacy);
            assertThrows(ServiceException.class, () -> store.saveActions(99L, List.of(v2Proposal()), v2Actor()),
                "旧运行不能通过完整新载荷升级");
            assertNull(legacy.getDecisionContractVersion(), "拒绝后旧版本仍为空");
            var current = v2Run(DecisionRunStatus.RUNNING, 0);
            when(runs.selectOne(any())).thenReturn(current);
            var valid = v2Proposal();
            var old = new DecisionActionProposal(null, valid.actionType(), valid.title(), valid.displayReason(), REF,
                Map.of("runId", "99", "requiredDate", "2026-10-03", "materialLines", List.of()), valid.riskLevel(),
                true, valid.permissionCode(), valid.idempotencyKey(), valid.executorCode(), 1, List.of());
            assertThrows(ServiceException.class, () -> store.saveActions(99L, List.of(old), v2Actor()),
                "运行版本二也不能把旧载荷当作完整合同");
            verify(actions, never()).insert(any(AiDecisionAction.class));
            return null;
        });
    }

    @Test void sameActionRejectsVersionOrContractMismatch() {
        withTenant("000001", () -> {
            when(runs.selectOne(any())).thenReturn(v2Run(DecisionRunStatus.RUNNING, 0));
            when(actions.insert(any(AiDecisionAction.class))).thenAnswer(call -> {
                ((AiDecisionAction) call.getArgument(0)).setId(501L); return 1;
            });
            var proposal = v2Proposal();
            var prior = store.saveActions(99L, List.of(proposal), v2Actor()).get(0);
            clearInvocations(actions);
            when(actions.selectList(any())).thenReturn(List.of(prior));
            prior.setPurchasePayloadVersion(null);
            assertThrows(ServiceException.class, () -> store.saveActions(99L, List.of(proposal), v2Actor()),
                "同键旧动作不能回填版本后当作同一合同");
            prior.setPurchasePayloadVersion(2); prior.setPayloadJson("{}");
            assertThrows(ServiceException.class, () -> store.saveActions(99L, List.of(proposal), v2Actor()),
                "同键合同内容不一致必须拒绝");
            verify(actions, never()).insert(any(AiDecisionAction.class));
            return null;
        });
    }

    @Test void recoveredPurchaseStillRequiresCompleteCodecContract() {
        withTenant("000001", () -> {
            when(runs.selectOne(any())).thenReturn(v2Run(DecisionRunStatus.EXECUTING, 3));
            var row = recoveryAction("EXECUTING", 4); row.setPayloadJson("{}");
            when(actions.selectOne(any())).thenReturn(row);
            assertThrows(ServiceException.class, () -> recoveredSave(501L, 4, recoveryResult(true)),
                "版本旗号不能代替恢复所需完整合同");
            verify(actions, never()).update(isNull(), any());
            return null;
        });
    }

    private boolean sameStoredPayload(Map<String, Object> desired, String actual) {
        try { return mapper.valueToTree(desired).equals(mapper.readTree(actual)); }
        catch (Exception error) { throw new AssertionError("保存合同应可解码", error); }
    }

    private AiDecisionRun v2Run(DecisionRunStatus status, int version) {
        var run = current(status, version); run.setTenantId("000001"); run.setDecisionContractVersion(2);
        if (status == DecisionRunStatus.EXECUTING) {
            try { run.setApprovalContextJson(mapper.writeValueAsString(Map.of("userId", recoveryActor().userId(),
                "userName", recoveryActor().userName(), "permissions", recoveryActor().permissions()))); }
            catch (Exception error) { throw new AssertionError("新版恢复运行必须已有绑定执行身份", error); }
        }
        return run;
    }

    private DecisionActor v2Actor() { return new DecisionActor("000001", 7L, "分析员", Set.of("ai:decision:run")); }

    private DecisionActionProposal v2Proposal() {
        var contract = DecisionPurchaseProposalServiceTest.contract(100L, 200L);
        return new DecisionActionProposal(
                null,
                DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
                "采购草稿",
                "真实逐来源合同",
                REF,
                DecisionPurchaseContractCodec.toPayload(99L, contract),
                DecisionRiskLevel.HIGH,
                true,
                "pu:requisition:add",
                DecisionPurchaseContractCodec.idempotencyKey("000001", 99L, REF, contract),
                "purchaseRequisitionDraft",
                1,
                List.of());
    }

    @Test void unlinkedExecutionFlowIdIsAuditedWithTenantStatusAndVersionCas() {
        withTenant(
                () -> {
                    when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.EXECUTING, 3));
                    when(runs.update(isNull(), any())).thenReturn(1);
                    store.recordUnlinkedExecutionFlow(99L, 77L);
                    var update = ArgumentCaptor.forClass(Wrapper.class);
                    verify(runs).update(isNull(), update.capture());
                    String bound = params(update.getValue()).values().toString();
                    assertTrue(bound.contains("TENANT-A"));
                    assertTrue(bound.contains("EXECUTING"));
                    assertTrue(bound.contains("ORPHAN_EXECUTION_FLOW"));
                    assertTrue(bound.contains("77"));
                    assertTrue(bound.contains("3"));
                    return null;
                });
    }

    @Test void compensationResultIsVersionedAndTenantScoped() {
        withTenant(
                () -> {
                    when(runs.selectOne(any()))
                            .thenReturn(current(DecisionRunStatus.ROLLING_BACK, 5));
                    var row = new AiDecisionAction();
                    row.setId(501L);
                    row.setRunId(99L);
                    row.setTenantId("TENANT-A");
                    row.setVersion(7);
                    row.setApprovalStatus("APPROVED");
                    row.setExecutionStatus("COMPENSATING");
                    when(actions.selectOne(any())).thenReturn(row);
                    when(actions.update(isNull(), any())).thenReturn(1);
                    var result =
                            new DecisionActionExecutionResult(
                                    true,
                                    false,
                                    "PRD_SCHEDULE_PLAN",
                                    600L,
                                    "PLAN-600",
                                    Map.of("compensated", true),
                                    null,
                                    null);
                    store.saveCompensationResult(501L, result);
                    var update = ArgumentCaptor.forClass(Wrapper.class);
                    verify(actions).update(isNull(), update.capture());
                    assertTrue(params(update.getValue()).containsValue("TENANT-A"));
                    assertTrue(params(update.getValue()).containsValue(7));
                    assertTrue(params(update.getValue()).containsValue("COMPENSATED"));
                    return null;
                });
    }

    @Test void actionResultJsonNeverPersistsUnsanitizedProviderSecrets() {
        withTenant(
                () -> {
                    when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.EXECUTING, 2));
                    AiDecisionAction action = new AiDecisionAction();
                    action.setId(501L);
                    action.setRunId(99L);
                    action.setTenantId("TENANT-A");
                    action.setVersion(3);
                    action.setApprovalStatus("APPROVED");
                    action.setExecutionStatus("EXECUTING");
                    when(actions.selectOne(any())).thenReturn(action);
                    when(actions.update(isNull(), any())).thenReturn(1);
                    var result =
                            new DecisionActionExecutionResult(
                                    false,
                                    false,
                                    null,
                                    null,
                                    null,
                                    Map.of(),
                                    "UPSTREAM",
                                    "Bearer secret-provider-token");
                    store.saveActionResult(501L, result, 1);
                    var update = ArgumentCaptor.forClass(Wrapper.class);
                    verify(actions).update(isNull(), update.capture());
                    assertFalse(
                            params(update.getValue())
                                    .values()
                                    .toString()
                                    .contains("secret-provider-token"));
                    return null;
                });
    }

    @Test void wildcardAdminApprovalScopeCanBeRestoredForExecution() {
        withTenant(() -> {
            AiDecisionRun run = current(DecisionRunStatus.APPROVED, 6);
            run.setApprovalContextJson("{\"userId\":7,\"userName\":\"admin\",\"permissions\":[\"*:*:*\"]}");
            when(runs.selectOne(any())).thenReturn(run);
            assertEquals(Set.of("*:*:*"), store.trustedExecutionActor(99L, "TENANT-A").permissions());
            return null;
        });
    }

    @Test void executionIdentityIsBoundToApproverAndCannotUseFlowInputUser() {
        withTenant(
                () -> {
                    var run = current(DecisionRunStatus.WAITING_APPROVAL, 4);
                    when(runs.selectOne(any())).thenReturn(run);
                    when(runs.update(isNull(), any())).thenReturn(1);
                    store.bindExecutionActor(99L, ACTOR);
                    var update = ArgumentCaptor.forClass(Wrapper.class);
                    verify(runs).update(isNull(), update.capture());
                    assertTrue(params(update.getValue()).containsValue("TENANT-A"));
                    assertTrue(params(update.getValue()).containsValue(4));
                    assertTrue(
                            params(update.getValue()).values().stream()
                                    .filter(String.class::isInstance)
                                    .map(String.class::cast)
                                    .anyMatch(json -> json.contains("\"userId\":7")));
                    return null;
                });
    }

    @Test void terminalOutcomeUsesTenantStatusAndVersionCompareAndSet() {
        withTenant(
                () -> {
                    AiDecisionRun row = current(DecisionRunStatus.SUCCEEDED, 4);
                    when(runs.selectOne(any())).thenReturn(row);
                    when(runs.update(isNull(), any())).thenReturn(1);
                    var outcome =
                            new DecisionOutcome(
                                    null,
                                    null,
                                    null,
                                    Map.of(),
                                    List.of(),
                                    List.of(),
                                    LocalDateTime.of(2026, 9, 26, 10, 0),
                                    "PROJECTED_ONLY");
                    store.saveOutcome(99L, outcome);
                    var update = ArgumentCaptor.forClass(Wrapper.class);
                    verify(runs).update(isNull(), update.capture());
                    var values = params(update.getValue()).values();
                    assertTrue(values.contains("TENANT-A"));
                    assertTrue(values.contains("SUCCEEDED"));
                    assertTrue(values.contains(4));
                    return null;
                });
    }

    @Test void approvedActionTransitionUsesTenantRunVersionAndExpectedStatus() {
        withTenant(
                () -> {
                    when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.APPROVED, 2));
                    var action = new AiDecisionAction();
                    action.setId(501L);
                    action.setRunId(99L);
                    action.setTenantId("TENANT-A");
                    action.setVersion(4);
                    action.setApprovalStatus("APPROVED");
                    action.setExecutionStatus("NOT_STARTED");
                    when(actions.selectOne(any())).thenReturn(action);
                    when(actions.update(isNull(), any())).thenReturn(1);
                    store.transitionAction(501L, "APPROVED", "NOT_STARTED", "EXECUTING");
                    var update = ArgumentCaptor.forClass(Wrapper.class);
                    verify(actions).update(isNull(), update.capture());
                    var values = params(update.getValue()).values();
                    assertTrue(values.contains("TENANT-A"));
                    assertTrue(values.contains(4));
                    assertTrue(values.contains("NOT_STARTED"));
                    assertTrue(values.contains("EXECUTING"));
                    return null;
                });
    }

    @Test void trustedActorIsRebuiltFromImmutableRunScopeNotClientInput() {
        withTenant(() -> {
            AiDecisionRun row = current(DecisionRunStatus.RUNNING, 1);
            row.setCreateBy(7L);
            row.setScoreConfigJson("{\"horizonDays\":30,\"userName\":\"tester\",\"permissions\":[\"ai:decision:run\"]}");
            when(runs.selectOne(any())).thenReturn(row);
            DecisionActor trusted = store.trustedActor(99L, "TENANT-A");
            assertEquals(7L, trusted.userId());
            assertEquals(Set.of("ai:decision:run"), trusted.permissions());
            return null;
        });
    }

    @Test void noTenantContextIsRejectedBeforeAnyMapperAccess() {
        assertThrows(ServiceException.class, () -> store.transition(99L, DecisionRunStatus.CREATED, DecisionRunStatus.RUNNING));
        assertThrows(ServiceException.class, () -> store.requireRun(99L, "TENANT-A"));
        assertThrows(ServiceException.class, () -> store.findActiveRun(REF, " "));
        verifyNoInteractions(runs, evidence, actions);
    }

    @Test void activeLookupIncludesTenantAndExactBusinessRef() {
        withTenant(
                () -> {
                    when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.RUNNING, 0));
                    assertNotNull(store.findActiveRun(REF, "TENANT-A"));
                    var query = ArgumentCaptor.forClass(Wrapper.class);
                    verify(runs).selectOne(query.capture());
                    assertTrue(params(query.getValue()).containsValue("TENANT-A"));
                    assertTrue(params(query.getValue()).containsValue(REF.id()));
                    assertEquals("TENANT-A", TenantHelper.getDynamic());
                    return null;
                });
    }

    @Test void createdToRunningUsesAtomicStatusVersionAndTenantCas() {
        withTenant(
                () -> {
                    when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.CREATED, 2));
                    when(runs.update(isNull(), any())).thenReturn(1);
                    store.transition(99L, DecisionRunStatus.CREATED, DecisionRunStatus.RUNNING);
                    var update = ArgumentCaptor.forClass(Wrapper.class);
                    verify(runs).update(isNull(), update.capture());
                    var values = params(update.getValue()).values();
                    assertTrue(values.contains("TENANT-A"));
                    assertTrue(values.contains("CREATED"));
                    assertTrue(values.contains(2));
                    assertTrue(values.contains("RUNNING"));
                    return null;
                });
    }

    @Test void staleConcurrentTransitionAndIllegalTransitionFailClosed() {
        withTenant(() -> {
            when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.CREATED, 0));
            when(runs.update(isNull(), any())).thenReturn(0);
            assertThrows(ServiceException.class, () -> store.transition(99L, DecisionRunStatus.CREATED, DecisionRunStatus.RUNNING));
            assertThrows(ServiceException.class, () -> store.transition(99L, DecisionRunStatus.CREATED, DecisionRunStatus.SUCCEEDED));
            verify(runs, times(1)).update(isNull(), any());
            return null;
        });
    }

    @Test void fastFlowCompletionStillAllowsFlowInstanceAttachment() {
        withTenant(
                () -> {
                    when(runs.selectOne(any()))
                            .thenReturn(current(DecisionRunStatus.WAITING_APPROVAL, 3));
                    when(runs.update(isNull(), any())).thenReturn(1);
                    store.attachAnalysisFlow(99L, 199L);
                    var update = ArgumentCaptor.forClass(Wrapper.class);
                    verify(runs).update(isNull(), update.capture());
                    assertTrue(params(update.getValue()).containsValue(199L));
                    return null;
                });
    }

    @Test void snapshotJsonPreservesSnowflakeIdAsStringWithInjectedGlobalMapper() {
        withTenant(
                () -> {
                    var row = current(DecisionRunStatus.RUNNING, 0);
                    when(runs.selectOne(any())).thenReturn(row);
                    when(runs.update(isNull(), any())).thenReturn(1);
                    var snapshot =
                            new DeliveryContextSnapshot(
                                    REF,
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
                                    "sha-test",
                                    LocalDateTime.of(2026, 9, 26, 10, 0),
                                    List.of());
                    store.saveSnapshot(99L, snapshot);
                    var update = ArgumentCaptor.forClass(Wrapper.class);
                    verify(runs).update(isNull(), update.capture());
                    assertTrue(
                            params(update.getValue()).values().stream()
                                    .filter(String.class::isInstance)
                                    .map(String.class::cast)
                                    .anyMatch(
                                            text -> text.contains("\"id\":\"9007199254740993\"")));
                    return null;
                });
    }

    @Test void failureMessageIsRedactedAndBoundedBeforePersistence() {
        withTenant(
                () -> {
                    when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.RUNNING, 0));
                    when(runs.update(isNull(), any())).thenReturn(1);
                    store.fail(
                            99L,
                            "MODEL_ERROR",
                            "Bearer eyJ123456789 and apiKey=my-secret jdbc:mysql://host/password "
                                    + "x".repeat(1500));
                    var update = ArgumentCaptor.forClass(Wrapper.class);
                    verify(runs).update(isNull(), update.capture());
                    String joined = params(update.getValue()).values().toString();
                    assertFalse(joined.contains("eyJ123456789"));
                    assertFalse(joined.contains("my-secret"));
                    assertFalse(joined.contains("jdbc:mysql://host"));
                    return null;
                });
    }

    @Test void savedActionsAreBoundToTenantRunAndReturnAssignedIds() {
        withTenant(() -> {
            when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.RUNNING, 0));
            when(actions.insert(any(AiDecisionAction.class))).thenAnswer(call -> { ((AiDecisionAction) call.getArgument(0)).setId(501L); return 1; });
            var proposal = new DecisionActionProposal(null, DecisionActionType.SEND_MANAGER_NOTIFICATION,
                "draft", "draft", REF, Map.of("runId", "99", "targetUserId", "7"),
                DecisionRiskLevel.HIGH, true, "ai:decision:execute", "TENANT-A:99:notice", "managerNotification", 1, List.of());
            var saved = store.saveActions(99L, List.of(proposal), ACTOR);
            assertEquals(501L, saved.get(0).getId());
            assertEquals("TENANT-A", saved.get(0).getTenantId());
            assertEquals("PROPOSED", saved.get(0).getApprovalStatus());
            assertEquals("NOT_STARTED", saved.get(0).getExecutionStatus());
            return null;
        });
    }

    @Test void completionRejectsNumericConclusionsWithoutProvenance() {
        withTenant(() -> {
            when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.RUNNING, 0));
            var result = new DecisionResult(99L, REF, 0, DecisionRiskLevel.LOW, "LOW", List.of(),
                null, List.of(), null, Map.of(), List.of(), List.of(), List.of());
            assertThrows(IllegalArgumentException.class, () -> store.completeAnalysis(99L, result, "TEMPLATE_FALLBACK"));
            verify(runs, never()).update(isNull(), any());
            return null;
        });
    }

    @Test void completionRefusesClaimedEvidenceThatWasNeverPersisted() {
        withTenant(() -> {
            var running = current(DecisionRunStatus.RUNNING, 2);
            running.setInputSnapshotHash("hash-1");
            running.setInputSnapshotJson("{}");
            when(runs.selectOne(any())).thenReturn(running);
            var root = new EvidenceItem(99L, "ERP:STOCK:101:stock_qty", "ERP", "STOCK", 101L,
                "101", null, "stock_qty", null, "NUMBER", "10", null,
                LocalDateTime.of(2026, 9, 26, 10, 0), "hash-1", null, true, false, List.of(), null);
            var score = new EvidenceItem(99L, "DERIVED:CALC:RISK:risk_score", "DERIVED", "CALC", null,
                "RISK", null, "risk_score", null, "NUMBER", "0", null,
                root.observedAt(), "hash-1", null, true, false, List.of(root.evidenceKey()), null);
            var result = new DecisionResult(99L, REF, 0, DecisionRiskLevel.LOW, "HIGH", List.of(),
                null, List.of(), null, Map.of(), List.of(root, score), List.of(), List.of());
            assertThrows(ServiceException.class, () -> store.completeAnalysis(99L, result, "TEMPLATE_FALLBACK"));
            verify(runs, never()).update(isNull(), any());
            return null;
        });
    }

    @Test void matchedPersistedEvidenceAllowsLowRiskCompletionWithStatusVersionCas() {
        withTenant(
                () -> {
                    var running = current(DecisionRunStatus.RUNNING, 2);
                    running.setInputSnapshotHash("hash-1");
                    running.setInputSnapshotJson("{}");
                    when(runs.selectOne(any())).thenReturn(running);
                    var root =
                            new EvidenceItem(
                                    99L,
                                    "ERP:STOCK:101:stock_qty",
                                    "ERP",
                                    "STOCK",
                                    101L,
                                    "101",
                                    null,
                                    "stock_qty",
                                    null,
                                    "NUMBER",
                                    "10",
                                    null,
                                    LocalDateTime.of(2026, 9, 26, 10, 0),
                                    "hash-1",
                                    null,
                                    true,
                                    false,
                                    List.of(),
                                    null);
                    var score =
                            new EvidenceItem(
                                    99L,
                                    "DERIVED:CALC:RISK:risk_score",
                                    "DERIVED",
                                    "CALC",
                                    null,
                                    "RISK",
                                    null,
                                    "risk_score",
                                    null,
                                    "NUMBER",
                                    "0",
                                    null,
                                    root.observedAt(),
                                    "hash-1",
                                    null,
                                    true,
                                    false,
                                    List.of(root.evidenceKey()),
                                    null);
                    AiDecisionEvidence persistedRoot = savedEvidence(root);
                    AiDecisionEvidence persistedScore = savedEvidence(score);
                    when(evidence.selectList(any()))
                            .thenReturn(List.of(persistedRoot, persistedScore));
                    when(runs.update(isNull(), any())).thenReturn(1);
                    var result =
                            new DecisionResult(
                                    99L,
                                    REF,
                                    0,
                                    DecisionRiskLevel.LOW,
                                    "HIGH",
                                    List.of(),
                                    null,
                                    List.of(),
                                    null,
                                    Map.of(),
                                    List.of(root, score),
                                    List.of(),
                                    List.of());
                    store.completeAnalysis(99L, result, "TEMPLATE_FALLBACK");
                    var update = ArgumentCaptor.forClass(Wrapper.class);
                    verify(runs).update(isNull(), update.capture());
                    assertTrue(params(update.getValue()).containsValue("RUNNING"));
                    assertTrue(params(update.getValue()).containsValue("TENANT-A"));
                    assertTrue(params(update.getValue()).containsValue(2));
                    return null;
                });
    }

    private static AiDecisionEvidence savedEvidence(EvidenceItem item) {
        var row = new AiDecisionEvidence();
        row.setRunId(item.runId()); row.setEvidenceKey(item.evidenceKey());
        row.setSourceDomain(item.sourceDomain()); row.setSourceType(item.sourceType());
        row.setSourceId(item.sourceId()); row.setSourceCode(item.sourceCode());
        row.setMetricCode(item.metricCode());
        row.setMetricName(item.metricName() == null ? item.metricCode() : item.metricName());
        row.setValueType(item.valueType());
        row.setMetricValue(item.value()); row.setObservedAt(item.observedAt());
        row.setDataVersion(item.dataVersion()); row.setVerifiable("Y"); row.setNormative("N");
        row.setDerivedFromJson(item.derivedFromEvidenceKeys().isEmpty() ? "[]" :
            "[\"" + String.join("\",\"", item.derivedFromEvidenceKeys()) + "\"]");
        return row;
    }

    @Test void createRunStoresActorAndSceneCodeInExplicitTenantScope() {
        withTenant(() -> {
            var request = request(false); request.setBizType(REF.type()); request.setBizId(REF.id());
            when(runs.insert(any(AiDecisionRun.class))).thenAnswer(call -> { ((AiDecisionRun) call.getArgument(0)).setId(99L); return 1; });
            var created = store.createRun(request, ACTOR);
            assertEquals(99L, created.getId());
            assertEquals("TENANT-A", created.getTenantId());
            assertEquals("DELIVERY_RISK", created.getSceneCode());
            assertEquals("CREATED", created.getStatus());
            assertEquals(7L, created.getCreateBy());
            return null;
        });
    }

    @Test void unnamedErpFactIsPersistedWithRequiredMetricName() {
        withTenant(
                () -> {
                    var run = current(DecisionRunStatus.RUNNING, 1);
                    run.setInputSnapshotHash("hash-1");
                    when(runs.selectOne(any())).thenReturn(run);
                    when(evidence.selectList(any())).thenReturn(List.of());
                    when(evidence.insert(any(AiDecisionEvidence.class))).thenReturn(1);
                    var item =
                            new EvidenceItem(
                                    99L,
                                    "ERP:ARRIVAL:11:remaining_qty",
                                    "ERP",
                                    "ARRIVAL",
                                    11L,
                                    "11",
                                    null,
                                    "remaining_qty",
                                    null,
                                    "NUMBER",
                                    "1",
                                    null,
                                    LocalDateTime.of(2026, 9, 26, 10, 0),
                                    "hash-1",
                                    null,
                                    true,
                                    false,
                                    List.of(),
                                    null);
                    store.replaceEvidence(99L, List.of(item), ACTOR);
                    var inserted = ArgumentCaptor.forClass(AiDecisionEvidence.class);
                    verify(evidence).insert(inserted.capture());
                    assertEquals("remaining_qty", inserted.getValue().getMetricName());
                    assertEquals(
                            "ERP:ARRIVAL:11:remaining_qty", inserted.getValue().getEvidenceKey());
                    return null;
                });
    }

    @Test void databaseDatetimePrecisionAndDecimalScaleDoNotBreakEvidenceReplay() {
        withTenant(() -> {
            var run = current(DecisionRunStatus.RUNNING, 1); run.setInputSnapshotHash("hash-1");
            when(runs.selectOne(any())).thenReturn(run);
            AiDecisionEvidence prior = new AiDecisionEvidence();
            prior.setRunId(99L); prior.setTenantId("TENANT-A");
            prior.setEvidenceKey("ERP:STOCK:11:stock_qty");
            prior.setSourceDomain("ERP"); prior.setSourceType("STOCK"); prior.setSourceId(11L);
            prior.setSourceCode("11"); prior.setMetricCode("stock_qty"); prior.setMetricName("stock_qty");
            prior.setMetricValue("10");
            prior.setValueType("NUMBER"); prior.setVerifiable("Y"); prior.setNormative("N");
            prior.setDerivedFromJson("[]"); prior.setDataVersion("hash-1");
            prior.setObservedAt(LocalDateTime.of(2026, 9, 26, 10, 0, 0));
            prior.setConfidence(new BigDecimal("1.0000"));
            when(evidence.selectList(any())).thenReturn(List.of(prior));
            var item = new EvidenceItem(99L, prior.getEvidenceKey(), "ERP", "STOCK", 11L,
                "11", null, "stock_qty", null, "NUMBER", "10", null,
                prior.getObservedAt().plusNanos(123_000_000), "hash-1", BigDecimal.ONE,
                true, false, List.of(), null);
            assertDoesNotThrow(() -> store.replaceEvidence(99L, List.of(item), ACTOR));
            verify(evidence, never()).delete(any());
            verify(evidence, never()).insert(any(AiDecisionEvidence.class));
            return null;
        });
    }

    @Test void evidenceReplayDoesNotSoftDeleteUnderRunKeyUniqueConstraint() {
        withTenant(() -> {
            var run = current(DecisionRunStatus.RUNNING, 1); run.setInputSnapshotHash("sha-test");
            when(runs.selectOne(any())).thenReturn(run);
            AiDecisionEvidence existing = new AiDecisionEvidence();
            existing.setId(22L); existing.setTenantId("TENANT-A"); existing.setRunId(99L);
            existing.setEvidenceKey("ERP:ORDER:11:risk_score"); existing.setSourceDomain("ERP");
            existing.setSourceType("ORDER"); existing.setSourceId(11L);
            existing.setSourceCode("RISK"); existing.setMetricCode("risk_score");
            existing.setMetricName("risk_score"); existing.setMetricValue("40"); existing.setValueType("NUMBER");
            existing.setVerifiable("Y"); existing.setNormative("N");
            existing.setDerivedFromJson("[]");
            when(evidence.selectList(any())).thenReturn(List.of(existing));
            EvidenceItem item = new EvidenceItem(99L, existing.getEvidenceKey(), "ERP", "ORDER", 11L,
                "RISK", null, "risk_score", null, "NUMBER", "40", null, null, null,
                null, true, false, List.of(), null);
            store.replaceEvidence(99L, List.of(item), ACTOR);
            verify(evidence, never()).delete(any());
            verify(evidence, never()).insert(any(AiDecisionEvidence.class));
            EvidenceItem conflict = new EvidenceItem(99L, existing.getEvidenceKey(), "ERP", "ORDER", 11L,
                "RISK", null, "risk_score", null, "NUMBER", "41", null, null, null,
                null, true, false, List.of(), null);
            assertThrows(ServiceException.class, () -> store.replaceEvidence(99L, List.of(conflict), ACTOR));
            verify(evidence, never()).delete(any());
            return null;
        });
    }

    @Test void duplicateActionKeyMayReplayOnlyIdenticalStructuredPayload() {
        withTenant(
                () -> {
                    when(runs.selectOne(any())).thenReturn(current(DecisionRunStatus.RUNNING, 0));
                    var proposal =
                            new DecisionActionProposal(
                                    null,
                                    DecisionActionType.SEND_MANAGER_NOTIFICATION,
                                    "notify",
                                    "notify",
                                    REF,
                                    Map.of("runId", "99", "targetUserId", "7"),
                                    DecisionRiskLevel.HIGH,
                                    true,
                                    "ai:decision:execute",
                                    "TENANT-A:99:notify",
                                    "managerNotification",
                                    1,
                                    List.of());
                    AiDecisionAction prior = new AiDecisionAction();
                    prior.setId(501L);
                    prior.setRunId(99L);
                    prior.setTenantId("TENANT-A");
                    prior.setIdempotencyKey(proposal.idempotencyKey());
                    prior.setActionType(proposal.actionType().name());
                    try {
                        prior.setPayloadJson(mapper.writeValueAsString(proposal.payload()));
                    } catch (JsonProcessingException e) {
                        throw new AssertionError(e);
                    }
                    prior.setTargetBizType(REF.type().name());
                    prior.setTargetBizId(REF.id());
                    prior.setTargetBizCode(REF.code());
                    prior.setPermissionCode(proposal.permissionCode());
                    prior.setExecutorCode(proposal.executorCode());
                    prior.setTitle(proposal.title());
                    prior.setDisplayReason(proposal.displayReason());
                    prior.setRiskLevel(proposal.riskLevel().name());
                    prior.setApprovalRequired("Y");
                    prior.setSequenceNo(1);
                    prior.setDependencyIdsJson("[]");
                    when(actions.selectList(any())).thenReturn(List.of(prior));
                    assertEquals(
                            501L, store.saveActions(99L, List.of(proposal), ACTOR).get(0).getId());
                    verify(actions, never()).insert(any(AiDecisionAction.class));
                    var changed =
                            new DecisionActionProposal(
                                    null,
                                    proposal.actionType(),
                                    proposal.title(),
                                    proposal.displayReason(),
                                    REF,
                                    Map.of("runId", "99", "targetUserId", "8"),
                                    proposal.riskLevel(),
                                    true,
                                    proposal.permissionCode(),
                                    proposal.idempotencyKey(),
                                    proposal.executorCode(),
                                    1,
                                    List.of());
                    assertThrows(
                            ServiceException.class,
                            () -> store.saveActions(99L, List.of(changed), ACTOR));
                    return null;
                });
    }

    private static Map<String, Object> params(Wrapper<?> wrapper) {
        var query = ((AbstractWrapper<?, ?, ?>) wrapper);
        query.getSqlSegment(); // MyBatis-Plus 延迟绑定参数。
        return query.getParamNameValuePairs();
    }

    private <T> T withTenant(Supplier<T> action) { return withTenant("TENANT-A", action); }

    private <T> T withTenant(String tenant, Supplier<T> action) {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class);
             MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            try { return TenantHelper.dynamic(tenant, action); }
            finally { TenantHelper.clearDynamic(); }
        }
    }
}
