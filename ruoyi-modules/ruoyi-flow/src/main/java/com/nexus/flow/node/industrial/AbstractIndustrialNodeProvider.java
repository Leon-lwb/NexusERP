package com.nexus.flow.node.industrial;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexus.common.ai.decision.dto.DecisionActor;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.node.FlowNodeProvider;
import com.nexus.flow.node.NodeParamSchema;

import java.util.List;
import java.util.function.Supplier;

/**
 * 工业节点不接受来自画布的可编辑身份或业务配置。
 */
public abstract class AbstractIndustrialNodeProvider implements FlowNodeProvider {
    protected final DecisionNodeSupport support;
    protected AbstractIndustrialNodeProvider(DecisionNodeSupport support) { this.support = support; }
    @Override public String getDisplayName() { return getNodeType(); }
    @Override public List<NodeParamSchema> getParamSchemas() { return List.of(); }
    @Override public void validateConfig(JsonNode config) {
        if (config == null || !config.isObject() || !config.isEmpty())
            throw new IllegalArgumentException("工业节点不接受可编辑的身份或业务参数");
    }
    @Override public JsonNode execute(FlowExecutionContext ctx) {
        return support.output(ctx, actor -> run(ctx, actor));
    }
    protected abstract Object run(FlowExecutionContext ctx, DecisionActor actor);
}
