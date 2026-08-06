package com.nexus.ai.flow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.ai.flow.expr.FlowConditionEvaluator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * IF 条件分支节点执行器
 * <p>输出 {result: true/false}，引擎按 result 匹配 outputs/if/true 或 outputs/if/false 出边</p>
 *
 * @author NexusERP
 */
@Component
public class IfNodeProvider implements FlowNodeProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> OPERATORS = List.of(
        "EQ", "NE", "GT", "GTE", "LT", "LTE",
        "CONTAINS", "STARTS_WITH", "ENDS_WITH", "IS_EMPTY", "IS_NOT_EMPTY"
    );

    @Override
    public String getNodeType() {
        return "if";
    }

    @Override
    public String getDisplayName() {
        return "条件分支";
    }

    @Override
    public List<NodeParamSchema> getParamSchemas() {
        NodeParamSchema logic = new NodeParamSchema("logic", "组合逻辑", "options", "AND", true);
        logic.setOptions(List.of(
            java.util.Map.of("name", "AND（全部满足）", "value", "AND"),
            java.util.Map.of("name", "OR（任一满足）", "value", "OR")
        ));
        return List.of(logic);
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config == null || config.get("conditions") == null || !config.get("conditions").isArray()) {
            throw new IllegalArgumentException("IF 节点缺少 conditions 配置");
        }
        ArrayNode conditions = (ArrayNode) config.get("conditions");
        if (conditions.isEmpty()) {
            throw new IllegalArgumentException("IF 节点 conditions 不能为空");
        }
        for (JsonNode c : conditions) {
            if (c.get("variable") == null || c.get("variable").asText().isBlank()) {
                throw new IllegalArgumentException("IF 节点条件缺少 variable");
            }
            String operator = c.get("operator") == null ? "" : c.get("operator").asText();
            if (!OPERATORS.contains(operator.toUpperCase())) {
                throw new IllegalArgumentException("IF 节点条件使用了不支持的操作符: " + operator + "（支持: " + OPERATORS + "）");
            }
        }
    }

    @Override
    public JsonNode execute(FlowExecutionContext ctx) {
        JsonNode config = ctx.getConfig();
        String logic = config.has("logic") && !config.get("logic").isNull() ? config.get("logic").asText() : "AND";
        ArrayNode conditions = (ArrayNode) config.get("conditions");

        List<FlowConditionEvaluator.Condition> conditionList = new ArrayList<>();
        for (JsonNode c : conditions) {
            conditionList.add(new FlowConditionEvaluator.Condition(
                c.get("variable").asText(),
                c.get("operator").asText(),
                c.has("value") && !c.get("value").isNull() ? c.get("value").asText() : ""
            ));
        }

        boolean result = FlowConditionEvaluator.evaluate(logic, conditionList, ctx.getState());

        ObjectNode output = MAPPER.createObjectNode();
        output.put("result", result);
        output.put("nodeName", ctx.getNodeName());
        output.put("matchedCount", conditionList.size());
        return output;
    }
}
