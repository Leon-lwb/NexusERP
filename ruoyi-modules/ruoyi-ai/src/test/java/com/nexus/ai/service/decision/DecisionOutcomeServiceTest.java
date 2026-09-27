package com.nexus.ai.service.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.*;
import com.nexus.common.ai.domain.entity.*;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static com.nexus.ai.service.decision.DecisionTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DecisionOutcomeServiceTest {
    private final DecisionPersistenceService persistence = mock(DecisionPersistenceService.class);
    private final IndustrialDecisionDataPort port = mock(IndustrialDecisionDataPort.class);
    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new com.nexus.common.json.config.JacksonConfig().registerJavaTimeModule())
        .registerModule(new com.nexus.common.web.config.JacksonConfig().bigNumberModule());
    private final DecisionOutcomeService service = new DecisionOutcomeService(persistence, port, mapper);

    @Test void rolledBackRunCanPersistHonestOutcomeWithoutClaimingActualBenefit() throws Exception {
        var run = run(10L, DecisionRunStatus.ROLLED_BACK);
        run.setBizType("SCHEDULE_PLAN"); run.setBizId(9001L); run.setInputSnapshotHash("hash-old");
        var result = new DecisionResult(10L,
            new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, 9001L, null),
            40, DecisionRiskLevel.HIGH, "HIGH", List.of(), null, List.of(), null,
            Map.of(), List.of(), List.of(), List.of());
        run.setResultJson(mapper.writeValueAsString(result));
        when(persistence.requireRun(10L, "000000")).thenReturn(run);
        when(persistence.listActions(10L)).thenReturn(List.of());
        when(port.canAccess(any(), any())).thenReturn(true);
        when(port.checkFreshness(any(), anyString(), any())).thenReturn(new DecisionFreshnessResult(true, "hash-old", List.of()));
        assertNull(service.measure(10L, actor()).actualAfterKpis());
    }

    @Test void creatingOnlyDraftsNeverClaimsAnActualDeliveryImprovement() throws Exception {
        var run = run(10L, DecisionRunStatus.SUCCEEDED);
        run.setBizType("SCHEDULE_PLAN"); run.setBizId(9001L);
        run.setInputSnapshotHash("hash-old");
        var projected = kpis(1, 2, 1);
        var baseline = kpis(3, 6, 0);
        var result = new DecisionResult(10L, new DecisionBusinessRef(DecisionBusinessType.SCHEDULE_PLAN, 9001L, null),
            40, DecisionRiskLevel.HIGH, "HIGH", List.of("DELIVERY_DELAY"), baseline,
            List.of(scenario("APS_CR", true, projected, 0.2)), "APS_CR", Map.of(), List.of(), List.of(), List.of());
        run.setResultJson(mapper.writeValueAsString(result));
        when(persistence.requireRun(10L, "000000")).thenReturn(run);
        var action = succeededAction(20L); action.setApprovalStatus("APPROVED");
        when(persistence.listActions(10L)).thenReturn(List.of(action));
        when(port.canAccess(any(), any())).thenReturn(true);
        when(port.checkFreshness(any(), anyString(), any())).thenReturn(new DecisionFreshnessResult(true, "hash-old", List.of()));
        var outcome = service.measure(10L, actor());
        assertEquals(projected, outcome.projectedAfterKpis());
        assertNull(outcome.actualAfterKpis());
        assertEquals(List.of(20L), outcome.effectiveActionIds());
        verify(port, never()).simulateSchedules(any(), any(), anyInt(), any());
    }
}
