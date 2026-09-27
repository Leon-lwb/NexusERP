package com.nexus.erp.ai.adapter;

import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DecisionFreshnessResult;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 可重算关键指纹的新鲜度判定测试。 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class ErpDecisionFreshnessCheckerTest {

    @Mock
    private DeliveryContextAssembler assembler;

    @InjectMocks
    private ErpDecisionFreshnessChecker checker;

    private final DecisionBusinessRef ref = new DecisionBusinessRef(
        DecisionBusinessType.WORK_ORDER, 11L, "WO-11");
    private final DecisionActor actor = new DecisionActor(
        "000000", 7L, "tester", Set.of("ai:decision:run", "prd:work:query"));

    @Test
    void matchingCriticalHashIsFreshWithoutChangedFields() {
        when(assembler.computeFreshnessHash(ref, actor)).thenReturn("hash-current");

        DecisionFreshnessResult result = checker.check(ref, "hash-current", actor);

        assertTrue(result.fresh());
        assertEquals("hash-current", result.currentHash());
        assertEquals(List.of(), result.changedFields());
    }

    @Test
    void changedCriticalHashReturnsStableGenericChangeCategory() {
        when(assembler.computeFreshnessHash(ref, actor)).thenReturn("hash-new");

        DecisionFreshnessResult result = checker.check(ref, "hash-old", actor);

        assertFalse(result.fresh());
        assertEquals("hash-new", result.currentHash());
        assertEquals(List.of("criticalSnapshotHash"), result.changedFields());
    }

    @Test
    void blankExpectedHashFailsBeforeRecomputing() {
        ServiceException nullHash = assertThrows(ServiceException.class,
            () -> checker.check(ref, null, actor));
        ServiceException blankHash = assertThrows(ServiceException.class,
            () -> checker.check(ref, "  ", actor));

        assertTrue(nullHash.getMessage().contains("哈希"), nullHash.getMessage());
        assertTrue(blankHash.getMessage().contains("哈希"), blankHash.getMessage());
        verifyNoInteractions(assembler);
    }
}
