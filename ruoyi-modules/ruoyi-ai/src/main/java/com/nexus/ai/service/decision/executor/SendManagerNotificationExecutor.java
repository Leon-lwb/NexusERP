package com.nexus.ai.service.decision.executor;

import com.nexus.common.ai.decision.enums.DecisionActionType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SendManagerNotificationExecutor extends AbstractDecisionNotificationExecutor {
    public SendManagerNotificationExecutor(ApplicationEventPublisher publisher) { super(publisher); }
    @Override public DecisionActionType type() { return DecisionActionType.SEND_MANAGER_NOTIFICATION; }
}
