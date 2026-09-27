package com.nexus.flow.node.industrial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.service.decision.DecisionActionProposalService;
import com.nexus.ai.service.decision.DecisionPersistenceService;
import com.nexus.ai.service.decision.DecisionPolicyGuardService;
import com.nexus.ai.service.decision.DeliveryRiskDecisionOrchestrator;
import com.nexus.ai.service.sse.AiDecisionSseMessageHelper;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.common.flow.mapper.FlowInstanceMapper;
import com.nexus.common.flow.mapper.FlowMapper;
import com.nexus.flow.adapter.AiDecisionWorkflowAdapter;
import com.nexus.flow.adapter.DecisionFlowDefinitionFactory;
import com.nexus.flow.engine.FlowEngine;
import com.nexus.flow.node.FlowNodeRegistry;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class DecisionWorkflowWiringTest {
    @Test
    void actionProposalProviderCanRegisterAlongsideRealOrchestratorAndWorkflowAdapter() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getDefaultListableBeanFactory().setAllowCircularReferences(false);
            context.register(DecisionActionProposalNodeProvider.class, FlowNodeRegistry.class,
                AiDecisionWorkflowAdapter.class, DeliveryRiskDecisionOrchestrator.class);
            registerMock(context, DecisionNodeSupport.class);
            registerMock(context, DecisionPersistenceService.class);
            registerMock(context, DecisionActionProposalService.class);
            registerMock(context, DecisionPolicyGuardService.class);
            registerMock(context, AiDecisionSseMessageHelper.class);
            registerMock(context, AiProperties.class);
            registerMock(context, IndustrialDecisionDataPort.class);
            registerMock(context, RedissonClient.class);
            registerMock(context, DecisionFlowDefinitionFactory.class);
            registerMock(context, FlowMapper.class);
            registerMock(context, FlowInstanceMapper.class);
            registerMock(context, FlowEngine.class);
            registerMock(context, IAiModelService.class);
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());

            context.refresh();

            assertSame(context.getBean(DecisionActionProposalNodeProvider.class),
                context.getBean(FlowNodeRegistry.class).getProvider("industrial.actionProposal"));
            assertNotNull(context.getBean(AiDecisionWorkflowAdapter.class));
            assertNotNull(context.getBean(DeliveryRiskDecisionOrchestrator.class));
        }
    }

    private static <T> void registerMock(AnnotationConfigApplicationContext context, Class<T> type) {
        context.registerBean(type, () -> mock(type));
    }
}
