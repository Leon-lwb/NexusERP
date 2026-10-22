package com.nexus.flow.node.industrial;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.hutool.extra.spring.SpringUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.service.decision.DecisionPersistenceService;
import com.nexus.ai.service.sse.AiDecisionSseMessageHelper;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.common.ai.decision.exception.DecisionEvidenceException;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.state.FlowState;

import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.Set;

class DecisionNodeSupportEvidenceFailureTest {
    private final DecisionPersistenceService store = mock(DecisionPersistenceService.class);
    private final AiDecisionSseMessageHelper events = mock(AiDecisionSseMessageHelper.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final DecisionNodeSupport support = spy(new DecisionNodeSupport(store, mock(IndustrialDecisionDataPort.class), mapper, events));
    private final FlowExecutionContext ctx = new FlowExecutionContext();
    private MockedStatic<SpringUtil> spring;

    @BeforeEach void setup() throws Exception {
        ctx.setTenantId("tenant-A"); ctx.setNodeId("rank"); ctx.setNodeName("rank");
        ctx.setState(new FlowState(mapper.readTree("{\"runId\":\"21\"}")));
        doReturn(new DecisionActor("tenant-A", 7L, "trusted", Set.of("ai:decision:run"))).when(support).actor(ctx);
        spring = mockStatic(SpringUtil.class);
        spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("false");
    }
    @AfterEach void close() { spring.close(); }

    @Test void exactTypedReasonsReachRunSseAndBoundedNodeFailureMessage() {
        for (var reason : DecisionEvidenceException.Reason.values()) {
            var error = assertThrows(ServiceException.class, () -> support.output(ctx, () -> { throw new DecisionEvidenceException(reason); }));
            assertEquals(reason.publicMessage(), error.getMessage());
            verify(store).fail(21L, reason.code(), reason.publicMessage());
            verify(events).sendError(7L, 21L, reason.code(), reason.publicMessage());
            verify(events).sendNodeEnd(eq(7L), eq(21L), eq(0L), eq("rank"), eq("rank"), eq("FAILED"), anyLong(), eq(reason.publicMessage()));
            clearInvocations(store, events);
        }
    }
    @Test void plainTextCodeAndSecretsNeverBecomeTrustedEvidenceRejection() {
        var forged = new ServiceException("ROUTING_EVIDENCE_MISSING jdbc:mysql://secret Bearer secret-token sk-SECRET");
        var error = assertThrows(ServiceException.class, () -> support.output(ctx, () -> { throw forged; }));
        assertEquals("工业决策节点执行失败", error.getMessage());
        verify(store).fail(21L, "DECISION_NODE_FAILED", "工业决策节点执行失败");
        verify(events).sendError(7L, 21L, "DECISION_NODE_FAILED", "工业决策节点执行失败");
    }
    @Test void wrappedTypedCauseIsNotUnwrappedThroughUnknownFailure() {
        var wrapped = new IllegalStateException("database secret", new DecisionEvidenceException(DecisionEvidenceException.Reason.ROUTING_EVIDENCE_MISSING));
        assertThrows(ServiceException.class, () -> support.output(ctx, () -> { throw wrapped; }));
        verify(store).fail(21L, "DECISION_NODE_FAILED", "工业决策节点执行失败");
    }
    @Test void failedSseTransportCannotPreventEvidenceRefusalPersistenceOrLeakTransportDetails() {
        var reason = DecisionEvidenceException.Reason.ROUTING_EVIDENCE_MISSING;
        doThrow(new IllegalStateException("redis credential secret")).when(events).sendNodeEnd(any(), any(), anyLong(), anyString(), anyString(), eq("FAILED"), anyLong(), anyString());
        doThrow(new IllegalStateException("redis credential secret")).when(events).sendError(any(), any(), anyString(), anyString());
        var error = assertThrows(ServiceException.class, () -> support.output(ctx, () -> { throw new DecisionEvidenceException(reason); }));
        assertEquals(reason.publicMessage(), error.getMessage());
        verify(store).fail(21L, reason.code(), reason.publicMessage());
    }
}
