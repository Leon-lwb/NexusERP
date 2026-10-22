package com.nexus.common.ai.decision;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

@Tag("dev")
class DecisionEvidenceExceptionTest {
    @Test void onlyFinalTypedFiniteReasonsExposeFixedPublicCodesAndMessages() throws Exception {
        var type = assertDoesNotThrow(() -> Class.forName("com.nexus.common.ai.decision.exception.DecisionEvidenceException"));
        assertTrue(Modifier.isFinal(type.getModifiers()));
        var reasonType = Class.forName(type.getName() + "$Reason");
        var expected = Set.of("ROUTING_EVIDENCE_MISSING", "SCHEDULE_EVIDENCE_MISSING",
            "SHORTAGE_SIMULATION_INCOMPLETE", "CAPACITY_EVIDENCE_MISSING", "NO_ANALYZABLE_WORK_ORDER");
        var codes = new HashSet<String>();
        for (Object reason : reasonType.getEnumConstants()) {
            var rejected = (RuntimeException) type.getConstructor(reasonType).newInstance(reason);
            assertSame(reason, type.getMethod("reason").invoke(rejected));
            String code = (String) reasonType.getMethod("code").invoke(reason);
            String message = (String) reasonType.getMethod("publicMessage").invoke(reason);
            codes.add(code); assertEquals(message, rejected.getMessage());
            assertTrue(code.matches("[A-Z_]{1,64}")); assertTrue(message.length() < 100);
            assertNull(rejected.getCause());
        }
        assertEquals(expected, codes);
        assertEquals(1, type.getConstructors().length, "No arbitrary message/cause/code constructor");
        var error = assertThrows(InvocationTargetException.class, () -> type.getConstructor(reasonType).newInstance(new Object[]{null}));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
    }
}
