package com.nexus.ai.service.decision;

import com.nexus.ai.service.sse.AiDecisionSseMessageHelper;
import com.nexus.common.ai.decision.dto.DecisionOutcome;
import com.nexus.common.ai.decision.enums.DecisionRunStatus;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static com.nexus.ai.service.decision.DecisionTestFixtures.*;
import static org.mockito.Mockito.*;

class DecisionFinalizeServiceTest {
    @Test void outcomeIsPersistedBeforeTerminalEvent() {
        var persistence = mock(DecisionPersistenceService.class);
        var outcomes = mock(DecisionOutcomeService.class);
        var sse = mock(AiDecisionSseMessageHelper.class);
        var service = new DecisionFinalizeService(persistence, outcomes, sse);
        var outcome = new DecisionOutcome(null, null, null, Map.of(), List.of(), List.of(),
            LocalDateTime.of(2026, 9, 26, 15, 0), "PROJECTED_ONLY");
        when(persistence.requireRun(10L, "000000")).thenReturn(run(10L, DecisionRunStatus.SUCCEEDED));
        when(outcomes.measure(10L, actor())).thenReturn(outcome);
        service.finalizeRun(10L, actor());
        var ordered = inOrder(outcomes, persistence, sse);
        ordered.verify(outcomes).measure(10L, actor());
        ordered.verify(persistence).saveOutcome(10L, outcome);
        ordered.verify(sse).sendDone(1L, 10L, "SUCCEEDED");
    }
}
