package com.nexus.ai.service.decision;

import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

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

    @Test void typedRulesGenerateOnlyDraftsAndNoticesWithERPCompatiblePayload() {
        var actions = service.propose(input(Set.of("MATERIAL_SHORTAGE", "PURCHASE_DELAY", "SCHEDULE_DELAY"), "APS_CR_EXPEDITE", true));
        assertEquals(List.of(DecisionActionType.SEND_EXPEDITE_NOTICE, DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT,
            DecisionActionType.CREATE_SCHEDULE_PLAN_DRAFT, DecisionActionType.SEND_MANAGER_NOTIFICATION),
            actions.stream().map(DecisionActionProposal::actionType).toList());
        var purchase = actions.get(1);
        assertEquals("10", purchase.payload().get("runId"));
        assertEquals("2026-09-30", purchase.payload().get("requiredDate"));
        var line = (Map<?, ?>) ((List<?>) purchase.payload().get("materialLines")).get(0);
        assertEquals(Set.of("materialId", "gap", "requiredDate"), line.keySet());
        assertEquals("100", line.get("materialId"));
        assertTrue(purchase.approvalRequired());
        assertEquals("pu:requisition:add", purchase.permissionCode());
        var schedule = actions.get(2);
        assertEquals("APS_CR_EXPEDITE", schedule.payload().get("scenarioCode"));
        assertEquals(List.of("11", "12"), schedule.payload().get("workOrderIds"));
        assertEquals("2026-09-26", schedule.payload().get("startDate"));
        assertTrue(schedule.approvalRequired());
        assertEquals("prd:schedule:run", schedule.permissionCode());
        assertTrue(actions.get(3).approvalRequired());
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
        assertEquals(List.of(DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT),
            actions.stream().map(DecisionActionProposal::actionType).toList());
        assertEquals(2, ((List<?>) actions.get(0).payload().get("materialLines")).size());
        assertTrue(actions.get(0).approvalRequired());
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
