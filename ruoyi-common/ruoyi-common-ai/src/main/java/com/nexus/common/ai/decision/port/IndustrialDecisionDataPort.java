package com.nexus.common.ai.decision.port;

import com.nexus.common.ai.decision.dto.ApsScenarioCompareResult;
import com.nexus.common.ai.decision.dto.DecisionActionExecutionResult;
import com.nexus.common.ai.decision.dto.DecisionActionProposal;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.dto.DecisionFreshnessResult;
import com.nexus.common.ai.decision.dto.DecisionPurchaseContractV2;
import com.nexus.common.ai.decision.dto.DeliveryContextSnapshot;
import com.nexus.common.ai.decision.dto.ShortageSimulationResult;
import java.util.List;
import java.util.Map;

public interface IndustrialDecisionDataPort {
    boolean canAccess(DecisionBusinessRef ref, DecisionActor actor);

    /**
     * 决策查询的只读业务权限校验；ERP 适配器验证前默认拒绝。
     */
    default boolean canReadDecision(DecisionBusinessRef ref, DecisionActor actor) { return false; }

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

    /** 输入必须来自服务端已持久化的快照；不接客户端数量或供应商选择。 */
    default List<DecisionPurchaseContractV2> freezePurchaseContracts(
        DeliveryContextSnapshot frozenSnapshot, int horizonDays, DecisionActor actor) {
        throw new UnsupportedOperationException("服务端采购合同冻结端口尚未实现");
    }

    /** 审批及未提交创建前核实际来源，不修改旧动作。 */
    default void requireFreshPurchaseContract(DecisionActionProposal action,
        DeliveryContextSnapshot frozenSnapshot, int horizonDays, DecisionActor actor) {
        throw new UnsupportedOperationException("采购合同新鲜度端口尚未实现");
    }

    /** 仅查已提交结果；空值只表示确定无永久记录，冲突不得伪装成不存在。 */
    default DecisionActionExecutionResult recoverPurchaseAction(DecisionActionProposal action, DecisionActor actor) {
        throw new UnsupportedOperationException("采购提交恢复端口尚未实现");
    }
}
