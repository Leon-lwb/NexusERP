package com.nexus.ai.service.decision.executor;

import com.nexus.common.ai.decision.enums.DecisionActionType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SendExpediteNoticeExecutor extends AbstractDecisionNotificationExecutor {
    public SendExpediteNoticeExecutor(ApplicationEventPublisher publisher) { super(publisher); }
    @Override public DecisionActionType type() { return DecisionActionType.SEND_EXPEDITE_NOTICE; }
}
