package com.nexus.ai.service.decision;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.enums.DecisionRunStatus;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;
import com.nexus.common.ai.domain.entity.AiDecisionAction;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.core.exception.ServiceException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** 采购门禁只读取持久化合同，版本号不能代替逐来源证明。 */
class DecisionPurchaseContractGuardTest {
    @Test void completePersistedV2CanPassPureGuard() {
        assertDoesNotThrow(() -> DecisionPurchaseContractGuard.requireExecutable(v2Run(), v2Action()),
            "完整持久化 v2 合同应通过结构门禁，但不等于取得 ERP 创建授权");
    }
    @Test void stablePurchaseKeyUsesCompleteFixedLengthDigest() {
        String key = v2Action().getIdempotencyKey();
        assertEquals(79, key.length(), "完整 SHA-256 键须适配现有 varchar128，而不是截断雪花 ID");
        assertTrue(key.matches("AI_PURCHASE_V2:[0-9a-f]{64}"), "新版采购只接受唯一 helper 产生的确定性键");
    }
    @Test void legacyNullVersionAndForgedPayloadAreRejected() {
        var run = v2Run(); var action = v2Action();
        run.setDecisionContractVersion(null);
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractGuard.requireExecutable(run, action),
            "旧运行不能靠载荷中的数字 2 升级");
        run.setDecisionContractVersion(2); action.setPurchasePayloadVersion(null);
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractGuard.requireExecutable(run, action),
            "旧动作不能补成新版证明");
        action.setPurchasePayloadVersion(2); action.setPayloadJson("{\"runId\":\"10\",\"purchase_payload_version\":2}");
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractGuard.requireExecutable(run, action),
            "缺逐来源及单位合同的伪造 v2 仍须拒绝");
    }
    @Test void mismatchedMarkersTargetPermissionAndStableKeyAreRejected() {
        var mutations =
                List.<Consumer<AiDecisionAction>>of(
                        a -> a.setPurchasePayloadVersion(1),
                        a -> a.setTargetBizId(9002L),
                        a -> a.setTargetBizType("SCHEDULE_PLAN"),
                        a -> a.setTargetBizCode("OTHER"),
                        a -> a.setExecutorCode("schedulePlanDraft"),
                        a -> a.setActionType("SEND_MANAGER_NOTIFICATION"),
                        a -> a.setPermissionCode("ai:decision:execute"),
                        a -> a.setApprovalRequired("N"),
                        a -> a.setIdempotencyKey("replacement-key"),
                        a -> a.setTenantId("other"),
                        a -> a.setRunId(11L));
        for (var mutation : mutations) {
            var a = v2Action(); mutation.accept(a);
            assertThrows(ServiceException.class, () -> DecisionPurchaseContractGuard.requireExecutable(v2Run(), a),
                "运行、目标、执行器、权限及永久请求键须与冻结合同一致");
        }
        var r = v2Run(); r.setDecisionContractVersion(1);
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractGuard.requireExecutable(r, v2Action()));
    }
    @Test void rejectsNonIntegerWireIncompleteSourceAndAdditionalLegacyFields() {
        String payload = v2Action().getPayloadJson();
        for (String bad :
                List.of(
                        payload.replace(
                                "\"purchase_payload_version\":2",
                                "\"purchase_payload_version\":\"2\""),
                        payload.replace(
                                "\"purchase_payload_version\":2",
                                "\"purchase_payload_version\":2.0"),
                        payload.replace(
                                "\"expectedSourceFingerprint\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"",
                                "\"expectedSourceFingerprint\":\"\""),
                        payload.replace("\"runId\":\"10\"", "\"runId\":\"11\""),
                        payload.replace("\"materialId\":\"139\"", "\"materialId\":139"),
                        payload.replace(
                                "\"requirement_detail_ids\":[\"500\"]",
                                "\"requirement_detail_ids\":[\"501\"]"),
                        payload.replace(
                                "\"purchase_payload_version\":2",
                                "\"materialLines\":[],\"purchase_payload_version\":2"),
                        payload.replace(
                                "\"purchase_payload_version\":2",
                                "\"purchase_payload_version\":1,\"purchase_payload_version\":2"))) {
            var a = v2Action(); a.setPayloadJson(bad);
            assertThrows(ServiceException.class, () -> DecisionPurchaseContractGuard.requireExecutable(v2Run(), a),
                "非整数版本、缺失证明、重复字段与旧载荷夹带均不得放行");
        }
    }
    @Test void trailingJsonCannotBeAcceptedAsACompleteFrozenContract() {
        var a = v2Action(); a.setPayloadJson(a.getPayloadJson()+" {}");
        assertThrows(ServiceException.class, () -> DecisionPurchaseContractGuard.requireExecutable(v2Run(), a),
            "完整冻结 JSON 后不能夹带第二份载荷或尾随令牌");
    }
    static AiDecisionRun v2Run() {
        var run = DecisionTestFixtures.run(10L, DecisionRunStatus.EXECUTING);
        run.setBizType("WORK_ORDER"); run.setBizId(9001L); run.setBizCode("WO-9001");
        run.setDecisionContractVersion(2); run.setScoreConfigJson("{\"horizonDays\":30}");
        return run;
    }
    static AiDecisionAction v2Action() {
        var a = new AiDecisionAction(); a.setId(20L); a.setRunId(10L); a.setTenantId("000000");
        a.setActionType("CREATE_PURCHASE_REQUISITION_DRAFT"); a.setExecutorCode("purchaseRequisitionDraft");
        a.setPermissionCode("pu:requisition:add"); a.setApprovalRequired("Y"); a.setApprovalStatus("APPROVED");
        a.setExecutionStatus("EXECUTING"); a.setTargetBizType("WORK_ORDER"); a.setTargetBizId(9001L);
        a.setTargetBizCode("WO-9001"); a.setPurchasePayloadVersion(2); a.setVersion(0); a.setSequenceNo(1);
        a.setRiskLevel("MEDIUM"); a.setDependencyIdsJson("[]");
        a.setPayloadJson("""
            {"purchase_payload_version":2,"runId":"10","materialId":"139","vendorId":"17",
             "windowEnd":"2026-11-01","materialVersion":"material-v1","vendorVersion":"vendor-v1",
             "supplierTermsId":"23","supplierTermsVersion":"terms-v1","baseUomId":"3",
             "baseUomVersion":"uom-v1","orderUomId":"3","orderUomVersion":"uom-v1",
             "conversionVersion":"uom-v1","requirement_detail_ids":["500"],
             "frozenLines":[{"detailId":"500","baseUomId":"3","coveredDemandBase":"5",
               "orderUomId":"3","requestedOrderQty":"5","requiredDate":"2026-10-09"}],
             "expectedSourceFingerprint":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}
            """);
        try {
            var payload =
                    new ObjectMapper()
                            .readValue(
                                    a.getPayloadJson(),
                                    new TypeReference<Map<String, Object>>() {});
            var contract = DecisionPurchaseContractCodec.parse(payload, 10L);
            a.setIdempotencyKey(
                    DecisionPurchaseContractCodec.idempotencyKey(
                            "000000",
                            10L,
                            new DecisionBusinessRef(
                                    DecisionBusinessType.WORK_ORDER, 9001L, "WO-9001"),
                            contract));
        } catch (Exception invalid) { throw new AssertionError("合成采购合同应可由唯一 codec 解析", invalid); }
        return a;
    }
}
