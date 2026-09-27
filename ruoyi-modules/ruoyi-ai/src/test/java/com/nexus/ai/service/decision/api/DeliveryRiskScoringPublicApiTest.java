package com.nexus.ai.service.decision.api;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.service.decision.DeliveryRiskScoringService;
import com.nexus.ai.service.decision.DeliveryRiskScoringService.RiskInput;
import com.nexus.ai.service.decision.DeliveryRiskScoringService.RiskResult;
import com.nexus.common.ai.decision.enums.DecisionRiskLevel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class DeliveryRiskScoringPublicApiTest {
    private final DeliveryRiskScoringService service = new DeliveryRiskScoringService(new AiProperties());

    @Test
    void downstreamFlowNodeCanConsumePublicRiskContractAndFullEvidence() {
        RiskResult complete = service.score(new RiskInput(7, 0, 105.0, 65.0, 3,
            true, true, true, true, true, true));
        assertEquals(100.0, complete.score());
        assertEquals(DecisionRiskLevel.CRITICAL, complete.level());
        assertEquals("HIGH", complete.confidence());
    }

    @Test
    void missingCriticalEvidenceCannotProduceNoRiskScore() {
        RiskResult missing = service.score(new RiskInput(0, null, null, null, 0,
            false, false, false, false, false, false));
        assertNull(missing.score());
        assertNull(missing.level());
        assertEquals("LOW", missing.confidence());
        assertTrue(missing.rootCauseCodes().contains("SHORTAGE_EVIDENCE_INSUFFICIENT"));
    }

    @Test
    void incompleteSupplierSamplesGiveMediumConfidenceAndDoNotInventSupplierRisk() {
        RiskResult result = service.score(new RiskInput(0, Integer.MAX_VALUE, 70.0, null, 0,
            true, true, true, true, true, true));
        assertEquals(0.0, result.score());
        assertEquals(DecisionRiskLevel.LOW, result.level());
        assertEquals("MEDIUM", result.confidence());
        assertEquals(0, result.dimensionScores().get("supplier"));
    }

    @Test
    void shortageWithinThirtyDaysContributesTieredPoints() {
        RiskResult result = service.score(new RiskInput(0, 10, 70.0, 97.0, 3,
            true, true, true, true, true, true));
        assertEquals(20.0, result.score());
        assertEquals(DecisionRiskLevel.MEDIUM, result.level());
    }
}
