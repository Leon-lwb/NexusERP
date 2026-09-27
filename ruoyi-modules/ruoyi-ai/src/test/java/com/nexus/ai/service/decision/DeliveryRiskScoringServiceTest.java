package com.nexus.ai.service.decision;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.service.decision.DeliveryRiskScoringService.RiskInput;
import com.nexus.ai.service.decision.DeliveryRiskScoringService.RiskResult;
import com.nexus.common.ai.decision.enums.DecisionRiskLevel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class DeliveryRiskScoringServiceTest {
    private final AiProperties properties = new AiProperties();
    private final DeliveryRiskScoringService service = new DeliveryRiskScoringService(properties);

    private static RiskInput complete(int tardiness, boolean shortage, double utilization,
                                      double supplierRate, boolean reliable) {
        return new RiskInput(tardiness, shortage ? 0 : Integer.MAX_VALUE,
            utilization, supplierRate, reliable ? 3 : 0, true, true, true, true, true, true);
    }

    @Test
    void tardinessThresholdsAndRiskLevelsAreStable() {
        assertEquals(0, service.tardinessPoints(0));
        assertEquals(15, service.tardinessPoints(1));
        assertEquals(15, service.tardinessPoints(2));
        assertEquals(25, service.tardinessPoints(3));
        assertEquals(25, service.tardinessPoints(5));
        assertEquals(40, service.tardinessPoints(6));
        assertEquals(DecisionRiskLevel.LOW, service.score(complete(0, false, 70, 98, true)).level());
    }

    @Test
    void completeCompositeRiskProducesCriticalLevelWithAuditableDimensions() {
        RiskResult result = service.score(complete(7, true, 105, 65, true));
        assertEquals(100.0, result.score(), 0.001);
        assertEquals(DecisionRiskLevel.CRITICAL, result.level());
        assertEquals(40, result.dimensionScores().get("tardiness"));
        assertEquals(30, result.dimensionScores().get("shortage"));
        assertEquals(20, result.dimensionScores().get("capacity"));
        assertEquals(10, result.dimensionScores().get("supplier"));
        assertEquals("HIGH", result.confidence());
    }

    @Test
    void unreliableSupplierDataContributesZeroAndDoesNotAssertHighConfidence() {
        RiskResult result = service.score(complete(0, false, 70, 5, false));
        assertEquals(0.0, result.score());
        assertEquals(0, result.dimensionScores().get("supplier"));
        assertEquals("MEDIUM", result.confidence());
        assertTrue(result.rootCauseCodes().contains("SUPPLIER_EVIDENCE_INSUFFICIENT"));
    }

    @Test
    void configurationIsReadAtEveryCallWithoutReusingPreviousScore() {
        assertEquals(40, service.tardinessPoints(6));
        AiProperties.DecisionConfig updated = properties.getDecision();
        updated.getRisk().setTardinessPoints(35);
        properties.setDecision(updated);
        assertEquals(35, service.tardinessPoints(6));
        assertEquals(35, service.score(complete(7, false, 70, 98, true)).score());
    }

    @Test
    void invalidMeasurementsFailInsteadOfBeingPresentedAsNoRisk() {
        assertThrows(IllegalArgumentException.class, () -> service.score(complete(0, false, Double.NaN, 99, true)));
        assertThrows(IllegalArgumentException.class, () -> service.score(complete(0, false, 70, 101, true)));
        assertThrows(IllegalArgumentException.class, () -> service.score(complete(0, false, -1, 99, true)));
    }
}
