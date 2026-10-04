package com.nexus.ai.service.decision;

import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DecisionFrozenHorizonTest {
    @Test void acceptsOnlyPersistedIntegralHorizonWithinBounds() {
        var run = new AiDecisionRun();
        for (int days : new int[] {1, 30, 90}) {
            run.setScoreConfigJson("{\"horizonDays\":" + days + "}");
            assertEquals(days, DecisionFrozenHorizon.requireHorizonDays(run));
        }
    }

    @Test void missingMalformedOrUntrustedHorizonCannotBeCoerced() {
        assertThrows(ServiceException.class, () -> DecisionFrozenHorizon.requireHorizonDays(null));
        var run = new AiDecisionRun();
        for (String config : new String[] {null, "", "null", "[]", "{}", "invalid-json",
            "{\"horizonDays\":null}", "{\"horizonDays\":true}", "{\"horizonDays\":\"30\"}",
            "{\"horizonDays\":30.0}", "{\"horizonDays\":30.5}", "{\"horizonDays\":0}",
            "{\"horizonDays\":-1}", "{\"horizonDays\":91}", "{\"horizonDays\":9223372036854775808}"}) {
            run.setScoreConfigJson(config);
            assertThrows(ServiceException.class, () -> DecisionFrozenHorizon.requireHorizonDays(run));
        }
    }

    @Test void parserFailureDoesNotExposeFrozenConfigContents() {
        var run = new AiDecisionRun();
        run.setScoreConfigJson("{\"horizonDays\":\"Bearer private-marker\"");
        var failure = assertThrows(ServiceException.class, () -> DecisionFrozenHorizon.requireHorizonDays(run));
        assertFalse(failure.getMessage().contains("private-marker"));
        assertNull(failure.getCause());
    }
}
