package com.nexus.ai.service.decision;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.*;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Tag("dev")
class DecisionActionProposalServiceTest {
    private final DecisionActionProposalService service = new DecisionActionProposalService();
    private final DecisionBusinessRef ref = new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, 9001L, "APS-DEMO-01");
    private static final List<Map<String, Object>> LINES = List.of(Map.of("materialId", "100", "gap", "10",
        "requiredDate", "2026-09-30", "purchaseLineId", "500"));

    private DecisionActionProposalService.ProposalInput input(Set<String> causes, String recommended, boolean covered) {
        return new DecisionActionProposalService.ProposalInput(10L, "000000", ref, DecisionRiskLevel.HIGH,
            causes, recommended, covered, LINES, 1L, List.of("11", "12"), LocalDate.of(2026, 9, 26), 30, null);
    }

    @Test void oneMaterialContractBuildsOneStableKey() {
        var contract = DecisionPurchaseProposalServiceTest.contract(100L, 200L);
        var input = frozenInput();
        var result = frozenProposals(input, List.of(contract));
        assertEquals(1, result.size(), "一个真实物料供应商合同产生一个动作");
        var action = result.get(0);
        assertEquals(
                DecisionPurchaseContractCodec.toPayload(10L, contract),
                action.payload(),
                "动作保存完整且同份的唯一 wire 合同");
        assertEquals(
                DecisionPurchaseContractCodec.idempotencyKey(
                        "000000", 10L, input.target(), contract),
                action.idempotencyKey(),
                "只允许公共 helper 生成永久键");
        assertTrue(action.approvalRequired(), "采购草稿审批守卫不可取消");
    }

    @Test void twoMaterialContractsDoNotShareIdempotencyKey() {
        var first = DecisionPurchaseProposalServiceTest.contract(9L, 200L);
        var second = DecisionPurchaseProposalServiceTest.contract(10L, 201L);
        var actions = frozenProposals(frozenInput(), List.of(second, first));
        assertEquals(2, actions.size(), "不同物料合同必须分别产生动作");
        assertNotEquals(actions.get(0).idempotencyKey(), actions.get(1).idempotencyKey(), "物料供应商永久键不可共享");
        assertEquals(List.of("9", "10"), actions.stream().map(a -> (String) a.payload().get("materialId")).toList(),
            "物料按数值顺序而不是字符串顺序稳定编排");
        assertEquals(List.of(1, 2), actions.stream().map(DecisionActionProposal::sequence).toList(), "动作序号稳定且连续");
        assertEquals(actions, frozenProposals(frozenInput(), List.of(first, second)), "端口返回顺序不改变保存结果");
    }

    @Test void duplicatedFrozenContractOrZeroQuantityCannotProducePurchase() {
        var frozen = DecisionPurchaseProposalServiceTest.contract(100L, 200L);
        assertThrows(RuntimeException.class, () -> frozenProposals(frozenInput(), List.of(frozen, frozen)),
            "重复合同不能产生同键采购");
        var zero =
                new DecisionPurchaseContractV2(
                        frozen.materialId(),
                        frozen.vendorId(),
                        frozen.windowEnd(),
                        frozen.materialVersion(),
                        frozen.vendorVersion(),
                        frozen.supplierTermsId(),
                        frozen.supplierTermsVersion(),
                        frozen.baseUomId(),
                        frozen.baseUomVersion(),
                        frozen.orderUomId(),
                        frozen.orderUomVersion(),
                        frozen.conversionVersion(),
                        frozen.requirementDetailIds(),
                        List.of(
                                new DecisionPurchaseContractV2.Line(
                                        1100L,
                                        1L,
                                        BigDecimal.ZERO,
                                        1L,
                                        BigDecimal.ZERO,
                                        LocalDate.of(2026, 10, 3))),
                        frozen.expectedSourceFingerprint());
        assertThrows(RuntimeException.class, () -> frozenProposals(frozenInput(), List.of(zero)), "空正额度不能生成采购");
    }

    private DecisionActionProposalService.ProposalInput frozenInput() {
        var target = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, "WO-11");
        return new DecisionActionProposalService.ProposalInput(10L, "000000", target, DecisionRiskLevel.HIGH,
            Set.of("MATERIAL_SHORTAGE"), null, false, List.of(), null);
    }

    @SuppressWarnings("unchecked")
    private List<DecisionActionProposal> frozenProposals(DecisionActionProposalService.ProposalInput input,
                                                        List<DecisionPurchaseContractV2> contracts) {
        try {
            var method = DecisionActionProposalService.class.getMethod("propose",
                DecisionActionProposalService.ProposalInput.class, List.class);
            return (List<DecisionActionProposal>) method.invoke(service, input, contracts);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException cause) throw cause;
            throw new AssertionError("冻结合同提案抛出非预期异常", error.getCause());
        } catch (ReflectiveOperationException error) { throw new AssertionError("缺少强类型冻结合同提案接口", error); }
    }

    @Test void fullSnowflakeIdentityUsesBoundedPublicKeyWithoutChangingOldNoticeKeys() {
        var target = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, Long.MAX_VALUE, "工单显示名");
        var source = DecisionPurchaseProposalServiceTest.contract(Long.MAX_VALUE - 2000L, Long.MAX_VALUE - 1000L);
        var input = new DecisionActionProposalService.ProposalInput(Long.MAX_VALUE - 1L, "12345678901234567890", target,
            DecisionRiskLevel.HIGH, Set.of("MATERIAL_SHORTAGE"), null, false, List.of(), null);
        var action = frozenProposals(input, List.of(source)).get(0);
        assertEquals(79, action.idempotencyKey().length(), "完整雪花标识的公共键必须固定为七十九字符");
        assertTrue(action.idempotencyKey().matches("AI_PURCHASE_V2:[0-9a-f]{64}"), "新版键必须为完整哈希而非截断原标识");
        var changedLine =
                new DecisionPurchaseContractV2.Line(
                        source.frozenLines().get(0).detailId(),
                        1L,
                        new BigDecimal("4"),
                        1L,
                        new BigDecimal("4"),
                        LocalDate.of(2026, 10, 3));
        var changedQuantity = new DecisionPurchaseContractV2(source.materialId(), source.vendorId(), source.windowEnd(),
            source.materialVersion(), source.vendorVersion(), source.supplierTermsId(), source.supplierTermsVersion(),
            source.baseUomId(), source.baseUomVersion(), source.orderUomId(), source.orderUomVersion(), source.conversionVersion(),
            source.requirementDetailIds(), List.of(changedLine), source.expectedSourceFingerprint());
        assertEquals(action.idempotencyKey(), frozenProposals(input, List.of(changedQuantity)).get(0).idempotencyKey(),
            "数量不属于采购身份，改量必须由同键合同冲突守卫处理");
        var notice = service.propose(input(Set.of("PURCHASE_DELAY"), null, true)).stream()
            .filter(a -> a.actionType() == DecisionActionType.SEND_EXPEDITE_NOTICE).findFirst().orElseThrow();
        assertEquals("000000:10:SEND_EXPEDITE_NOTICE:SCHEDULE_PLAN:9001", notice.idempotencyKey(),
            "普通非采购动作继续使用原永久键，不能批量迁移");
    }

    @Test void legacyProposalInputCannotCreatePurchase() {
        var proposed = service.propose(input(Set.of("MATERIAL_SHORTAGE", "PURCHASE_DELAY", "SCHEDULE_DELAY"),
            "APS_CR_EXPEDITE", true));
        assertFalse(proposed.stream().anyMatch(a -> a.actionType() == DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT),
            "旧缺口和供应商输入不能签发采购合同");
        assertTrue(proposed.stream().anyMatch(a -> a.actionType() == DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT),
            "旧接口应保留排程草稿");
        assertTrue(proposed.stream().anyMatch(a -> a.actionType() == DecisionActionType.SEND_EXPEDITE_NOTICE),
            "旧接口应保留真实延期催交通知");
    }

    @Test void typedRulesGenerateOnlyDraftsAndNoticesWithERPCompatiblePayload() {
        var actions = service.propose(input(Set.of("MATERIAL_SHORTAGE", "PURCHASE_DELAY", "SCHEDULE_DELAY"), "APS_CR_EXPEDITE", true));
        assertEquals(List.of(DecisionActionType.SEND_EXPEDITE_NOTICE, DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT,
            DecisionActionType.SEND_MANAGER_NOTIFICATION), actions.stream().map(DecisionActionProposal::actionType).toList(),
            "旧接口仅保留通知及排程草稿");
        var schedule = actions.get(1);
        assertEquals("APS_CR_EXPEDITE", schedule.payload().get("scenarioCode"));
        assertEquals(List.of("11", "12"), schedule.payload().get("workOrderIds"));
        assertEquals("2026-09-26", schedule.payload().get("startDate"));
        assertTrue(schedule.approvalRequired());
        assertEquals("prd:schedule:run", schedule.permissionCode());
        assertTrue(actions.get(2).approvalRequired(), "高风险通知仍须审批");
        assertTrue(actions.stream().allMatch(a -> a.idempotencyKey() != null && a.idempotencyKey().length() <= 128));
        assertEquals(actions, service.propose(input(Set.of("MATERIAL_SHORTAGE", "PURCHASE_DELAY", "SCHEDULE_DELAY"), "APS_CR_EXPEDITE", true)));
    }

    @Test void insufficientTransitNeverClaimsExpediteAndInvalidScheduleInputsFailClosed() {
        var noTransit = service.propose(input(Set.of("PURCHASE_DELAY", "MATERIAL_SHORTAGE", "SCHEDULE_DELAY"), "APS_CR", false));
        assertFalse(noTransit.stream().anyMatch(a -> a.actionType() == DecisionActionType.SEND_EXPEDITE_NOTICE));
        var missingScope = new DecisionActionProposalService.ProposalInput(10L, "000000", ref, DecisionRiskLevel.HIGH,
            Set.of("SCHEDULE_DELAY"), "APS_CR", false, List.of(), 1L,
            List.of(), LocalDate.of(2026, 9, 26), 30, null);
        assertFalse(service.propose(missingScope).stream().anyMatch(a -> a.actionType() == DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT));
    }

    @Test void negativeQuantitiesAndNumericIdsAreNotPersistedAsFalseBusinessFacts() {
        var malicious = new DecisionActionProposalService.ProposalInput(10L, "000000", ref, DecisionRiskLevel.HIGH,
            Set.of("MATERIAL_SHORTAGE"), "APS_CR", false, List.of(Map.of("materialId", 100L, "gap", "-10", "requiredDate", "2026-09-30")),
            1L, List.of("11"), LocalDate.of(2026, 9, 26), 30, null);
        assertThrows(IllegalArgumentException.class, () -> service.propose(malicious));
    }

    @Test void noFeasibleRecommendationCannotCreateScheduleDraftAndLowRiskHasNoManagerApproval() {
        var actions = service.propose(new DecisionActionProposalService.ProposalInput(10L, "000000", ref,
            DecisionRiskLevel.LOW, Set.of("SCHEDULE_DELAY"), "BASELINE_CURRENT", false, List.of(),
            1L, List.of("11"), LocalDate.of(2026, 9, 26), 30, null));
        assertTrue(actions.isEmpty());
    }

    @Test void nullRecommendationCannotCrashOrAuthorizeScheduleDraft() {
        var noFeasible = new DecisionActionProposalService.ProposalInput(10L, "000000", ref,
            DecisionRiskLevel.HIGH, Set.of("SCHEDULE_DELAY"), null, false, List.of(),
            null, List.of("11"), LocalDate.of(2026, 9, 26), 30, null);
        assertTrue(assertDoesNotThrow(() -> service.propose(noFeasible)).isEmpty());
    }

    @Test void multipleShortagesWithNoFeasibleScheduleRetainOnlyVerifiedPurchaseDraft() {
        var lines = List.<Map<String, Object>>of(
            Map.of("materialId", "100", "gap", "37", "requiredDate", "2026-09-30"),
            Map.of("materialId", "101", "gap", "3", "requiredDate", "2026-09-30"));
        var shortage = new DecisionActionProposalService.ProposalInput(10L, "000000", ref,
            DecisionRiskLevel.HIGH, Set.of("MATERIAL_SHORTAGE", "SCHEDULE_DELAY"), null, false, lines,
            null, List.of("11"), LocalDate.of(2026, 9, 26), 30, null);
        var actions = assertDoesNotThrow(() -> service.propose(shortage));
        assertTrue(actions.isEmpty(), "没有冻结合同的旧缺口即使为正也不能生成采购");
    }

    @Test void missingManagerRecipientDoesNotProduceUndeliverableNotification() {
        var input = new DecisionActionProposalService.ProposalInput(10L, "000000", ref,
            DecisionRiskLevel.HIGH, Set.of(), "BASELINE_CURRENT", false, List.of(),
            null, List.of(), LocalDate.of(2026, 9, 26), 30, null);
        assertTrue(service.propose(input).isEmpty());
    }

    @Test void minimalContractDoesNotInventWorkOrderScopeOrApsDate() {
        var minimal = new DecisionActionProposalService.ProposalInput(10L, "000000", ref,
            DecisionRiskLevel.HIGH, Set.of("SCHEDULE_DELAY"), "APS_CR", false, List.of(), 1L);
        var actions = service.propose(minimal);
        assertFalse(actions.stream().anyMatch(a -> a.actionType() == DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT));
    }

    @Test void nullRootIdNeverProducesExecutableERPDraft() {
        var root = new DecisionBusinessRef(DecisionBusinessType.SHORTAGE_HORIZON, null, null);
        var actions = service.propose(new DecisionActionProposalService.ProposalInput(10L, "000000", root,
            DecisionRiskLevel.HIGH, Set.of("MATERIAL_SHORTAGE", "SCHEDULE_DELAY"), "APS_CR", false, LINES,
            1L, List.of("11"), LocalDate.of(2026, 9, 26), 30, null));
        assertFalse(actions.stream().anyMatch(a -> a.actionType() == DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT
            || a.actionType() == DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT));
    }
}
