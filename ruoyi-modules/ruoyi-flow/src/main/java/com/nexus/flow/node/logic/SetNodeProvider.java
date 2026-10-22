package com.nexus.flow.node.logic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.common.flow.execution.FlowReplayPolicy;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.node.FlowNodeProvider;
import com.nexus.flow.node.NodeParamSchema;
import com.nexus.flow.state.FlowTemplateRenderer;

import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 变量设置节点执行器
 * <p>一组 {字段名: {{}}模板表达式}，渲染后输出（面向非技术用户，与 CODE 节点同构但无需 SpEL 语法）。
 * 下游可通过 nodes.&lt;nodeId&gt;.output.&lt;字段名&gt; 引用</p>
 *
 * @author NexusERP
 */
@Component
public class SetNodeProvider implements FlowNodeProvider {
    @Override
    public FlowReplayPolicy replayPolicy(JsonNode config) {
        return FlowReplayPolicy.SAFE;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getNodeType() {
        return "set";
    }

    @Override
    public String getDisplayName() {
        return "变量设置";
    }

    @Override
    public List<NodeParamSchema> getParamSchemas() {
        NodeParamSchema variables = new NodeParamSchema("variables", "变量映射", "json", "{}", true);
        variables.setEditor("keyvalue");
        return List.of(variables);
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config == null
                || config.get("variables") == null
                || !config.get("variables").isObject()) {
            throw new IllegalArgumentException("变量节点缺少 variables 配置");
        }
    }

    @Override
    public JsonNode execute(FlowExecutionContext ctx) {
        JsonNode config = ctx.getConfig();
        JsonNode variables = config.get("variables");
        String label = "节点 " + ctx.getNodeName();

        ObjectNode output = MAPPER.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = variables.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            String value =
                    e.getValue() == null || e.getValue().isNull() ? "" : e.getValue().asText();
            // 渲染 {{}} 模板；非模板纯文本原样保留
            String rendered =
                    FlowTemplateRenderer.render(value, ctx.getState(), label + " 的 " + e.getKey());
            output.put(e.getKey(), rendered);
        }
        return output;
    }
}
