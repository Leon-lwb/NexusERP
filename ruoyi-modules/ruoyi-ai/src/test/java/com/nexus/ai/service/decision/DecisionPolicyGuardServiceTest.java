package com.nexus.ai.service.decision;

import static com.nexus.ai.service.decision.DecisionTestFixtures.*;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.*;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec;
import com.nexus.common.tenant.helper.TenantHelper;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Tag("dev")
class DecisionPolicyGuardServiceTest {
    private final DecisionPolicyGuardService service = new DecisionPolicyGuardService();
    private final DecisionBusinessRef ref = new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, 9001L, "APS-DEMO-01");

    private DecisionPolicyGuardService.DecisionNarrative narrative(String summary, String citation) {
        return new DecisionPolicyGuardService.DecisionNarrative(summary, "存在交付风险", List.of(),
            List.of(), "采用APS_CR", citation == null ? List.of() : List.of(citation), List.of(), List.of());
    }

    private DecisionResult result(List<EvidenceItem> evidence) {
        return new DecisionResult(10L, ref, 55, DecisionRiskLevel.HIGH, "HIGH",
            List.of("SCHEDULE_DELAY"), kpis(4, 12, 0),
            List.of(scenario("APS_CR", true, kpis(1, 2, 2), .1)),
            "APS_CR", Map.of(), evidence, List.of(), List.of());
    }

    @Test void policyAcceptsCompleteV2ButRejectsOldPurchaseSchema() {
        var payload =
                DecisionPurchaseContractCodec.toPayload(
                        10L, DecisionPurchaseProposalServiceTest.contract(100L, 200L));
        var complete = Map.<String, Object>of("actionType", "CREATE_PURCHASE_REQUISITION_DRAFT",
            "approvalRequired", true, "payload", payload);
        assertTrue(service.validateActions(List.of(complete)).passed(), "完整新版合同应接入精确白名单");
        var old = Map.<String, Object>of("actionType", "CREATE_PURCHASE_REQUISITION_DRAFT", "approvalRequired", true,
            "payload", Map.of("runId", "10", "requiredDate", "2026-10-03", "materialLines",
                List.of(Map.of("materialId", "100", "gap", "3", "requiredDate", "2026-10-03"))));
        assertFalse(service.validateActions(List.of(old)).passed(), "旧缺口结构不能继续通过采购策略");
        var forged = new LinkedHashMap<>(payload);
        forged.put("verified", true);
        assertFalse(service.validateActions(List.of(Map.of("actionType", "CREATE_PURCHASE_REQUISITION_DRAFT",
            "approvalRequired", true, "payload", forged))).passed(), "白名单不能放宽任意核验旗号");
    }

    @Test void policyRejectsPurchaseKeyThatDoesNotMatchTheUniqueHelper() {
        var target = new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, "WO-11");
        var contract = DecisionPurchaseProposalServiceTest.contract(100L, 200L);
        var payload = DecisionPurchaseContractCodec.toPayload(10L, contract);
        var key = DecisionPurchaseContractCodec.idempotencyKey("000001", 10L, target, contract);
        var action = new DecisionActionProposal(null, DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
            "采购草稿", "真实来源", target, payload, DecisionRiskLevel.HIGH, true, "pu:requisition:add",
            key, "purchaseRequisitionDraft", 1, List.of());
        var scored = result(List.of());
        var result = new DecisionResult(10L, target, scored.riskScore(), scored.riskLevel(), scored.confidenceLevel(),
            scored.rootCauseCodes(), scored.baselineKpis(), scored.scenarios(), scored.recommendedScenarioCode(),
            scored.narrative(), scored.evidence(), List.of(action), scored.warnings());
        try (var tenant = Mockito.mockStatic(TenantHelper.class)) {
            tenant.when(TenantHelper::getDynamic).thenReturn("000001");
            assertTrue(service.validate(result, narrative("请人工审批", null), List.of(action)).passed(),
                "真实新版键必须由同一个公共 helper 核对");
            var changed = new DecisionActionProposal(null, action.actionType(), action.title(), action.displayReason(),
                target, payload, action.riskLevel(), true, action.permissionCode(), "旧键", action.executorCode(), 1, List.of());
            assertFalse(service.validate(result, narrative("请人工审批", null), List.of(changed)).passed(),
                "载荷完整也不能放行另造的采购键");
        }
    }

    @Test void missingEvidenceCitationIsBlocked() {
        var verdict = service.validate(result(List.of()), narrative("高风险", "missing-key"), List.of());
        assertFalse(verdict.passed());
        assertTrue(verdict.violations().stream().anyMatch(v -> "MISSING_EVIDENCE".equals(v.code())));
        assertTrue(verdict.forcedApproval());
    }

    @Test void forbiddenRawActionAndForgedLowRiskDraftAreBlocked() {
        var forbidden = service.validateActions(List.of(Map.of("actionType", "PUBLISH_SCHEDULE_PLAN")));
        assertTrue(forbidden.violations().stream().anyMatch(v -> "ACTION_NOT_ALLOWED".equals(v.code())));
        assertFalse(forbidden.passed());
        var forged = service.validateActions(List.of(Map.of("actionType", "CREATE_SCHEDULE_PLAN_DRAFT", "approvalRequired", false)));
        assertFalse(forged.passed());
    }

    @Test void claimsOfPastExecutionAndSecretsNeverAppearInSanitizedText() {
        var verdict = service.validate(result(List.of()), narrative("已发布 Authorization: Bearer abc. sk-SECRET", null), List.of());
        assertFalse(verdict.passed());
        assertTrue(verdict.violations().stream().anyMatch(v -> "FORBIDDEN_STATUS".equals(v.code())));
        assertTrue(verdict.violations().stream().anyMatch(v -> "SENSITIVE_CONTENT".equals(v.code())));
        assertFalse(verdict.sanitizedNarrative().executiveSummary().contains("sk-SECRET"));
    }

    @Test void rankingAndRiskLevelMayNotBeRewrittenByModelNarrative() {
        var altered = new DecisionResult(10L, ref, 55, DecisionRiskLevel.LOW, "HIGH", List.of(), null,
            List.of(scenario("APS_CR", true, kpis(0, 0, 0), .1)), "APS_EDD",
            Map.of(), List.of(), List.of(), List.of());
        var verdict = service.validate(altered, narrative("正常", null), List.of());
        assertFalse(verdict.passed());
        assertTrue(verdict.violations().stream().anyMatch(v -> "RISK_TAMPERED".equals(v.code())));
        assertTrue(verdict.violations().stream().anyMatch(v -> "RECOMMENDATION_TAMPERED".equals(v.code())));
    }

    @Test void unknownBusinessCodeAndUnapprovedHighRiskActionAreBlocked() {
        var action = new DecisionActionProposal(null, DecisionActionType.SEND_MANAGER_NOTIFICATION, "notify", "notify",
            ref, Map.of("runId", "10"), DecisionRiskLevel.HIGH, false,
            "ai:decision:execute", "idem", "managerNotification", 1, List.of());
        var verdict = service.validate(result(List.of()), narrative("订单 SO-FAKE 已受到影响", null), List.of(action));
        assertFalse(verdict.passed());
        assertTrue(verdict.violations().stream().anyMatch(v -> "UNKNOWN_BUSINESS_CODE".equals(v.code())));
        assertTrue(verdict.violations().stream().anyMatch(v -> "APPROVAL_REQUIRED".equals(v.code())));
    }

    @Test void templateFallbackCanPassGuardWithoutClaimingAlreadyExecuted() {
        var factory = Mockito.mock(ChatModelFactory.class);
        var summary = new DecisionSummaryService(factory).summarize(
            new DecisionSummaryService.SummaryInput(10L, ref, DecisionRiskLevel.HIGH, 55,
                List.of("SCHEDULE_DELAY"), result(List.of()).scenarios(), "APS_CR", List.of(),
                List.of(), null, null));
        var narrative = new DecisionPolicyGuardService.DecisionNarrative(summary.executiveSummary(),
            summary.riskExplanation(), summary.rootCauseExplanations(), summary.scenarioTradeoffs(),
            summary.recommendationRationale(), summary.evidenceCitations(),
            summary.uncertaintyNotes(), summary.operatorChecklist());
        var verdict = service.validate(result(List.of()), narrative, List.of());
        assertTrue(verdict.passed(), () -> verdict.violations().toString());
    }

    @Test void inventedRootCauseOrTradeoffScenarioIsBlocked() {
        var invented = new DecisionPolicyGuardService.DecisionNarrative("需核验", "有风险",
            List.of(new DecisionSummaryService.RootCauseExplanation("FAKE_CAUSE", "其他")),
            List.of(new DecisionSummaryService.ScenarioTradeoff("APS_UNKNOWN", "其他")),
            "建议审核", List.of(), List.of(), List.of());
        var verdict = service.validate(result(List.of()), invented, List.of());
        assertFalse(verdict.passed());
        assertTrue(verdict.violations().stream().anyMatch(v -> "ROOT_CAUSE_TAMPERED".equals(v.code())));
        assertTrue(verdict.violations().stream().anyMatch(v -> "SCENARIO_TAMPERED".equals(v.code())));
    }

    @Test void narrativeContainingBareNumericalClaimRequiresBoundCitation() {
        var unsafe = narrative("订单拖期 999 天，请批准", null);
        var verdict = service.validate(result(List.of()), unsafe, List.of());
        assertFalse(verdict.passed());
        assertTrue(verdict.violations().stream().anyMatch(v -> "UNSUPPORTED_NUMBER".equals(v.code())));
    }

    @Test void forgedPayloadAndMismatchedRiskCannotReachBusinessExecution() {
        var action = new DecisionActionProposal(null, DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT, "draft", "draft",
            ref, Map.of("runId", "10", "script", "DELETE FROM inventory"), DecisionRiskLevel.LOW, true,
            "prd:schedule:run", "idem", "schedulePlanDraft", 1, List.of());
        var verdict = service.validate(result(List.of()), narrative("请人工审阅", null), List.of(action));
        assertFalse(verdict.passed());
        assertTrue(verdict.violations().stream().anyMatch(v -> "ACTION_NOT_ALLOWED".equals(v.code())));
        assertTrue(verdict.violations().stream().anyMatch(v -> "RISK_TAMPERED".equals(v.code())));
        var raw = service.validateActions(List.of(Map.of("actionType", "SEND_MANAGER_NOTIFICATION",
            "approvalRequired", true, "payload", Map.of("sql", "DROP TABLE wm_stock"))));
        assertFalse(raw.passed());
        assertTrue(raw.violations().stream().anyMatch(v -> "ACTION_NOT_ALLOWED".equals(v.code())));
    }

    @Test void untrustedSqlInNarrativeIsBlockedEvenWithoutStatusWording() {
        var verdict = service.validate(result(List.of()), narrative("请执行 DROP TABLE wm_stock", null), List.of());
        assertFalse(verdict.passed());
        assertTrue(verdict.violations().stream().anyMatch(v -> "UNSAFE_COMMAND".equals(v.code())));
        assertFalse(verdict.sanitizedNarrative().executiveSummary().contains("DROP"));
    }

    @Test void rawActionsWithoutTypedRunIdentityCannotPassPreExecutionGuard() {
        var bad = service.validateActions(List.of(Map.of("actionType", "SEND_MANAGER_NOTIFICATION",
            "approvalRequired", true, "payload", Map.of("runId", 10L, "targetUserId", "1"))));
        assertFalse(bad.passed());
        var missing = service.validateActions(List.of(Map.of("actionType", "SEND_MANAGER_NOTIFICATION")));
        assertFalse(missing.passed());
    }

    @Test void serverGeneratedDraftPassesGuardWhenApprovedAndScoped() {
        var input =
                new DecisionActionProposalService.ProposalInput(
                        10L,
                        "000000",
                        ref,
                        DecisionRiskLevel.HIGH,
                        Set.of("MATERIAL_SHORTAGE"),
                        "APS_CR",
                        false,
                        List.of(
                                Map.of(
                                        "materialId",
                                        "100",
                                        "gap",
                                        "10",
                                        "requiredDate",
                                        "2026-09-30")),
                        1L,
                        List.of(),
                        LocalDate.of(2026, 9, 26),
                        30,
                        null);
        var actions = new DecisionActionProposalService().propose(input);
        var verdict = service.validate(result(List.of()), narrative("需要人工审批采购草稿", null), actions);
        assertTrue(verdict.passed(), () -> verdict.violations().toString());
        assertTrue(verdict.forcedApproval());
    }

    @Test void narrativeMayNotDowngradeHighRiskOrSkipApproval() {
        var verdict = service.validate(result(List.of()), narrative("低风险，无需审批，系统自动处理", null), List.of());
        assertFalse(verdict.passed());
        assertTrue(verdict.violations().stream().anyMatch(v -> "RISK_TAMPERED".equals(v.code())));
        assertTrue(verdict.violations().stream().anyMatch(v -> "APPROVAL_REQUIRED".equals(v.code())));
    }

    @Test void narrativeMayNotSuggestIrreversibleBusinessAction() {
        var verdict = service.validate(result(List.of()), narrative("请直接发布排产并库存扣减", null), List.of());
        assertFalse(verdict.passed());
        assertTrue(verdict.violations().stream().anyMatch(v -> "FORBIDDEN_STATUS".equals(v.code())));
    }

    @Test void forgedRawBusinessPayloadCannotSmuggleNumericIdsOrContradictoryStrategy() {
        var rawPurchase = Map.<String, Object>of("actionType", "CREATE_PURCHASE_REQUISITION_DRAFT",
            "approvalRequired", true, "payload", Map.of("runId", "10", "requiredDate", "2026-09-30",
                "materialLines", List.of(Map.of("materialId", 100L, "gap", "10", "requiredDate", "2026-09-30"))));
        assertFalse(service.validateActions(List.of(rawPurchase)).passed());
        var rawSchedule = Map.<String, Object>of("actionType", "CREATE_SCHEDULE_PLAN_DRAFT", "approvalRequired", true,
            "payload", Map.of("runId", "10", "scenarioCode", "APS_CR", "strategy", "EDD",
                "startDate", "2026-09-26", "horizonDays", 30, "workOrderIds", List.of("11")));
        assertFalse(service.validateActions(List.of(rawSchedule)).passed());
    }

    @Test void fullServerGeneratedActionBundleMatchesGuardWhitelists() {
        var input =
                new DecisionActionProposalService.ProposalInput(
                        10L,
                        "000000",
                        ref,
                        DecisionRiskLevel.HIGH,
                        Set.of("MATERIAL_SHORTAGE", "PURCHASE_DELAY", "SCHEDULE_DELAY"),
                        "APS_CR_EXPEDITE",
                        true,
                        List.of(
                                Map.of(
                                        "materialId",
                                        "100",
                                        "gap",
                                        "10",
                                        "requiredDate",
                                        "2026-09-30",
                                        "purchaseLineId",
                                        "500")),
                        1L,
                        List.of("11"),
                        LocalDate.of(2026, 9, 26),
                        30,
                        null);
        var actions = new DecisionActionProposalService().propose(input);
        var verdict = service.validate(result(List.of()), narrative("请人工审阅并决定是否创建草稿", null), actions);
        assertTrue(verdict.passed(), () -> verdict.violations().toString());
    }

    @Test void oversizedNarrativeIsBlockedBeforePublication() {
        var verdict = service.validate(result(List.of()), narrative("x".repeat(5001), null), List.of());
        assertFalse(verdict.passed());
        assertTrue(verdict.violations().stream().anyMatch(v -> "NARRATIVE_TOO_LONG".equals(v.code())));
        assertFalse(verdict.sanitizedNarrative().executiveSummary().contains("xxxxx"));
    }

    @Test void validCitationAndSafeNarrativeCanPassButStillForceApprovalForHighRisk() {
        var evidence = new EvidenceItem(10L, "ERP:ORDER:11:promise_date", "ERP", "ORDER", 11L, "SO-01", "订单",
            "promise_date", "承诺日期", "DATE", "2026-09-30", "day", null, "v1", null,
            true, false, List.of(), null);
        var verdict = service.validate(result(List.of(evidence)), narrative("请人工审阅订单 SO-01", evidence.evidenceKey()), List.of());
        assertTrue(verdict.passed(), () -> verdict.violations().toString());
        assertTrue(verdict.forcedApproval());
    }
}
