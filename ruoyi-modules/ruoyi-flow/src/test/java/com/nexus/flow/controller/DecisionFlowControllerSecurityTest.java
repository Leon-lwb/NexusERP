package com.nexus.flow.controller;

import com.nexus.common.flow.domain.bo.FlowBo;
import com.nexus.common.flow.domain.entity.Flow;
import com.nexus.common.flow.service.*;
import com.nexus.flow.engine.FlowEngine;
import com.nexus.flow.node.FlowNodeRegistry;
import com.nexus.flow.template.FlowTemplateCatalog;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DecisionFlowControllerSecurityTest {
    private final IFlowService flows = mock(IFlowService.class);
    private final FlowEngine engine = mock(FlowEngine.class);
    private final FlowController controller = new FlowController(flows, mock(IFlowVersionService.class),
        mock(IFlowInstanceService.class), engine, mock(FlowNodeRegistry.class), mock(FlowTemplateCatalog.class));

    @Test void systemFlowCannotBePublishedOrRunFromGeneralApi() {
        Flow protectedFlow = new Flow();
        protectedFlow.setId(1L); protectedFlow.setStatus("PUBLISHED");
        protectedFlow.setFlowKey("system_delivery_risk_analysis_v1_000000");
        protectedFlow.setConfigJson("{\"nodes\":[{\"id\":\"context\",\"type\":\"industrial.deliveryContext\"}],\"edges\":[]}");
        when(flows.selectById(1L)).thenReturn(protectedFlow);
        var request = new FlowController.FlowRunRequest();
        request.setFlowId(1L);
        assertNotEquals(200, controller.run(request).getCode());
        assertNotEquals(200, controller.publish(1L).getCode());
        verifyNoInteractions(engine);
    }

    @Test void genericNodeTestAndStopCannotUseProtectedInstance() {
        Flow protectedFlow = new Flow();
        protectedFlow.setId(1L);
        protectedFlow.setFlowKey("system_delivery_risk_analysis_v1_000000");
        protectedFlow.setConfigJson("{\"nodes\":[{\"id\":\"context\",\"type\":\"industrial.deliveryContext\"}],\"edges\":[]}");
        when(flows.selectById(1L)).thenReturn(protectedFlow);
        var request = new FlowController.FlowNodeTestRequest();
        request.setFlowId(1L); request.setNodeId("context");
        assertNotEquals(200, controller.nodeTest(request).getCode());
        var instance = new com.nexus.common.flow.domain.entity.FlowInstance();
        instance.setId(19L); instance.setConfigSnapshot(protectedFlow.getConfigJson());
        var instances = mock(IFlowInstanceService.class);
        var ctrl = new FlowController(flows, mock(IFlowVersionService.class), instances, engine,
            mock(FlowNodeRegistry.class), mock(FlowTemplateCatalog.class));
        when(instances.selectById(19L)).thenReturn(instance);
        var stop = new FlowController.FlowStopRequest();
        stop.setInstanceId(19L);
        assertNotEquals(200, ctrl.stop(stop).getCode());
        verifyNoInteractions(engine);
    }

    @Test void genericDraftCannotContainForgedIndustrialNode() {
        FlowBo bo = new FlowBo();
        bo.setFlowName("fake"); bo.setFlowKey("fake");
        bo.setConfigJson("{\"nodes\":[{\"id\":\"x\",\"type\":\"industrial.executeApprovedActions\"}],\"edges\":[]}");
        assertNotEquals(200, controller.add(bo).getCode());
        verify(flows, never()).insertByBo(any());
    }
}
