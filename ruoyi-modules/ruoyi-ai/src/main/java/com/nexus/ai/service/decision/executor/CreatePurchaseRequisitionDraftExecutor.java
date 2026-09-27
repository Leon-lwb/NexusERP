package com.nexus.ai.service.decision.executor;

import com.nexus.common.ai.decision.enums.DecisionActionType;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import org.springframework.stereotype.Component;

@Component
public class CreatePurchaseRequisitionDraftExecutor extends AbstractErpDraftExecutor {
    public CreatePurchaseRequisitionDraftExecutor(IndustrialDecisionDataPort port) { super(port); }
    @Override public DecisionActionType type() { return DecisionActionType.CREATE_PURCHASE_REQUISITION_DRAFT; }
}
