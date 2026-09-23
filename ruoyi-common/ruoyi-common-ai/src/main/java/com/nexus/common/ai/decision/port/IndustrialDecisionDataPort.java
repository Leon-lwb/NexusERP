package com.nexus.common.ai.decision.port;

import com.nexus.common.ai.decision.dto.ApsScenarioCompareResult;
import com.nexus.common.ai.decision.dto.DecisionActionExecutionResult;
import com.nexus.common.ai.decision.dto.DecisionActionProposal;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DecisionFreshnessResult;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.dto.ShortageSimulationResult;
import java.util.List;
import java.util.Map;

public interface IndustrialDecisionDataPort {
    boolean canAccess(DecisionBusinessRef ref, DecisionActor actor);

    DeliveryContextSnapshot loadDeliveryContext(DecisionBusinessRef ref, int horizonDays, DecisionActor actor);

    ShortageSimulationResult simulateShortage(DeliveryContextSnapshot snapshot, int horizonDays,
                                              Map<String, Object> overrides, DecisionActor actor);

    ApsScenarioCompareResult simulateSchedules(DeliveryContextSnapshot snapshot,
                                               List<String> scenarioCodes, int horizonDays, DecisionActor actor);

    DecisionFreshnessResult checkFreshness(DecisionBusinessRef ref, String expectedHash, DecisionActor actor);

    DecisionActionExecutionResult executeBusinessAction(DecisionActionProposal action, DecisionActor actor);

    DecisionActionExecutionResult verifyBusinessAction(DecisionActionProposal action,
                                                       DecisionActionExecutionResult result, DecisionActor actor);

    DecisionActionExecutionResult compensateBusinessAction(DecisionActionProposal action,
                                                           DecisionActionExecutionResult result, DecisionActor actor);
}
