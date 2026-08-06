package com.nexus.ai.flow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.ai.flow.expr.SpelFlowExpressionEvaluator;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CODE 代码转换节点执行器
 * <p>一组 {目标字段: SpEL 表达式}，root 为流程状态 Map 视图（只读安全模式）</p>
 *
 * @author NexusERP
 */
public class CodeNodeProvider implements FlowNodeProvider {

    private final SpelFlowExpressionEvaluator evaluator = new SpelFlowExpressionEvaluator();

    @Override
    public String getNodeType() {
        return "code";
    }

    @Override
    public String getDisplayName() {
        return "代码转换";
    }

    @Override
    public List<NodeParamSchema> getParamSchemas() {
        return List.of(
            new NodeParamSchema("expressions", "表达式映射", "json", "", true),
            new NodeParamSchema("expressionLimit", "表达式长度上限", "number", "2000", false)
        );
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config == null || config.get("expressions") == null || !config.get("expressions").isObject()) {
            throw new IllegalArgumentException("CODE 节点缺少 expressions 配置");
        }
        if (config.get("expressions").isEmpty()) {
            throw new IllegalArgumentException("CODE 节点 expressions 不能为空");
        }
    }

    @Override
    public JsonNode execute(FlowExecutionContext ctx) {
        JsonNode config = ctx.getConfig();
        JsonNode expressions = config.get("expressions");

        Map<String, String> exprMap = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = expressions.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            exprMap.put(e.getKey(), e.getValue() == null || e.getValue().isNull() ? "" : e.getValue().asText());
        }

        return evaluator.evaluate(exprMap, ctx.getState(), "节点 " + ctx.getNodeName());
    }
}
