package com.nexus.ai.service.decision;

import com.nexus.ai.config.AiProperties;
import com.nexus.common.ai.decision.dto.ApsScenarioCompareResult;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DecisionScenario;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.nexus.ai.service.decision.DecisionTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("dev")
class ApsWhatIfSimulationServiceTest {
    private final AiProperties config = new AiProperties();
    private final IndustrialDecisionDataPort port = mock(IndustrialDecisionDataPort.class);
    private final ApsWhatIfSimulationService service = new ApsWhatIfSimulationService(config, port);
    private final DecisionActor user = actor();
    private final DeliveryContextSnapshot snapshot = new DeliveryContextSnapshot(
        new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER, 11L, "WO-11"),
        List.of(), List.of(), Map.of(), List.of(), List.of(), List.of(), Map.of(), List.of(),
        Map.of(), List.of(), "hash", LocalDateTime.of(2026, 9, 24, 12, 0), List.of());

    @Test
    void usesStableDefaultsAndPassesSnapshotToThePortWithoutModification() {
        List<String> defaults = List.of("BASELINE_CURRENT", "APS_EDD", "APS_CR", "APS_CR_EXPEDITE");
        var original = snapshot;
        var expected = new ApsScenarioCompareResult(List.of(scenario("APS_CR", true, kpis(0, 0, 0), 0)), List.of());
        when(port.simulateSchedules(snapshot, defaults, 30, user)).thenReturn(expected);
        assertSame(expected, service.compare(snapshot, null, 30, user));
        assertEquals(original, snapshot);
        verify(port).simulateSchedules(snapshot, defaults, 30, user);
    }

    @Test
    void rejectsDuplicateOrTooManyCodesBeforeInvokingPort() {
        var decision = config.getDecision();
        decision.setMaxScenarios(2);
        config.setDecision(decision);
        assertThrows(IllegalArgumentException.class, () -> service.compare(snapshot, List.of("APS_EDD", "APS_EDD"), 30, user));
        assertThrows(IllegalArgumentException.class, () -> service.compare(snapshot,
            List.of("APS_EDD", "APS_CR", "APS_CR_EXPEDITE"), 30, user));
        assertThrows(IllegalArgumentException.class, () -> service.compare(snapshot, List.of("APS_EDD"), 0, user));
        verifyNoInteractions(port);
    }

    @Test
    void allFailedScenariosRemainVisibleForPersistenceBeforeAnyDomainError() {
        var failure = scenario("APS_CR", false, null, 0);
        when(port.simulateSchedules(snapshot, List.of("APS_CR"), 30, user))
            .thenReturn(new ApsScenarioCompareResult(List.of(failure), List.of("NO_SAVED_SCHEDULE")));
        ApsScenarioCompareResult result = service.compare(snapshot, List.of("APS_CR"), 30, user);
        assertEquals(1, result.scenarios().size());
        assertEquals("APS_CR", result.scenarios().get(0).scenarioCode());
        assertTrue(result.warnings().contains("NO_FEASIBLE_SCENARIO"));
    }

    @Test
    void missingEvidenceListOrMissingScenariosFailsClosed() {
        var broken = new DecisionScenario("APS_CR", "CR", "CR", true, List.of(), kpis(0, 0, 0),
            null, 0, Map.of(), null, List.of(), "hash", List.of(), List.of());
        when(port.simulateSchedules(snapshot, List.of("APS_CR"), 30, user))
            .thenReturn(new ApsScenarioCompareResult(List.of(broken), List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.compare(snapshot, List.of("APS_CR"), 30, user));
    }
}
