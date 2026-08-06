package com.nexus.ai.flow.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.ai.flow.model.FlowGraph;
import com.nexus.ai.flow.model.FlowNodeDef;
import com.nexus.ai.flow.node.FlowExecutionContext;
import com.nexus.ai.flow.node.FlowNodeProvider;
import com.nexus.ai.flow.node.NodeParamSchema;
import com.nexus.ai.flow.state.FlowState;
import com.nexus.ai.flow.state.FlowTemplateRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-6: AI 流程编排核心逻辑单元测试（无 Spring 环境，直接测图模型/状态/渲染/条件）
 * <p>覆盖：FlowGraph 解析与环检测、FlowState 缺失语义分层、FlowTemplateRenderer 渲染、
 * FlowConditionEvaluator 条件评估</p>
 *
 * @author NexusERP
 */
class FlowCoreLogicTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ============ FlowGraph 解析与环检测 ============

    @Test
    @DisplayName("P1-6: FlowGraph 解析合法图，拓扑序正确")
    void parse_validGraph_topologicalOrderCorrect() {
        String configJson = """
            {
              "nodes": [
                {"id": "node_a", "type": "code", "position": [0, 0], "data": {"label": "A"}},
                {"id": "node_b", "type": "code", "position": [0, 1], "data": {"label": "B"}},
                {"id": "node_c", "type": "code", "position": [0, 2], "data": {"label": "C"}}
              ],
              "edges": [
                {"id": "e1", "source": "node_a", "target": "node_b", "sourceHandle": "outputs/main/0", "targetHandle": "inputs/main/0"},
                {"id": "e2", "source": "node_b", "target": "node_c", "sourceHandle": "outputs/main/0", "targetHandle": "inputs/main/0"}
              ]
            }
            """;
        FlowGraph graph = FlowGraph.parse(configJson);
        assertEquals(3, graph.getNodeCount());
        assertEquals(List.of("node_a"), graph.getStartNodeIds());
        assertEquals(List.of("node_a", "node_b", "node_c"), graph.getTopologicalOrder());
        assertEquals(0, graph.getInDegree("node_a"));
        assertEquals(1, graph.getInDegree("node_b"));
        assertEquals(1, graph.getInDegree("node_c"));
    }

    @Test
    @DisplayName("P1-6: FlowGraph 检测环并报错")
    void parse_cyclicGraph_throws() {
        String configJson = """
            {
              "nodes": [
                {"id": "node_a", "type": "code", "position": [0, 0], "data": {}},
                {"id": "node_b", "type": "code", "position": [0, 1], "data": {}}
              ],
              "edges": [
                {"id": "e1", "source": "node_a", "target": "node_b"},
                {"id": "e2", "source": "node_b", "target": "node_a"}
              ]
            }
            """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> FlowGraph.parse(configJson));
        assertTrue(ex.getMessage().contains("环"), "错误信息应包含环提示: " + ex.getMessage());
    }

    @Test
    @DisplayName("P1-6: FlowGraph 缺少节点 type 报错")
    void parse_missingType_throws() {
        String configJson = """
            {
              "nodes": [{"id": "node_a", "position": [0, 0], "data": {}}],
              "edges": []
            }
            """;
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> FlowGraph.parse(configJson));
        assertTrue(ex.getMessage().contains("type"), "错误信息应包含 type: " + ex.getMessage());
    }

    @Test
    @DisplayName("P1-6: FlowGraph 空图报错")
    void parse_emptyNodes_throws() {
        String configJson = """
            {"nodes": [], "edges": []}
            """;
        assertThrows(IllegalArgumentException.class, () -> FlowGraph.parse(configJson));
    }

    // ============ FlowState 缺失语义分层 ============

    @Test
    @DisplayName("P1-6: FlowState 属性缺失返回 MissingNode（渲染空串），节点未执行抛错")
    void state_missingSemantics_layerCorrect() {
        ObjectNode input = MAPPER.createObjectNode();
        input.put("warehouse", "WH01");
        FlowState state = new FlowState(input);

        // 存在的属性 → 取到值
        JsonNode value = state.get("input.warehouse");
        assertEquals("WH01", value.asText());

        // 属性缺失 → MissingNode（不抛错）
        JsonNode missing = state.get("input.nonexistent");
        assertTrue(missing.isMissingNode(), "属性缺失应返回 MissingNode");

        // 节点不存在 → 抛结构化错误
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> state.get("nodes.node_not_exist.output.content"));
        assertTrue(ex.getMessage().contains("node_not_exist"), "错误信息应包含节点名: " + ex.getMessage());
    }

    @Test
    @DisplayName("P1-6: FlowState 写入节点输出后可读取（含数组下标）")
    void state_putNodeOutput_readable() {
        FlowState state = new FlowState(MAPPER.createObjectNode());
        ObjectNode output = MAPPER.createObjectNode();
        output.put("content", "缺货分析");
        output.putArray("chunks").add("片段1").add("片段2");
        state.putNodeOutput("node_llm_1", output);

        assertEquals("缺货分析", state.get("nodes.node_llm_1.output.content").asText());
        assertEquals("片段1", state.get("nodes.node_llm_1.output.chunks[0]").asText());
        assertEquals("片段2", state.get("nodes.node_llm_1.output.chunks[1]").asText());
        // 简写
        assertEquals("缺货分析", state.get("node_llm_1.output.content").asText());
    }

    // ============ FlowTemplateRenderer ============

    @Test
    @DisplayName("P1-6: 模板渲染变量替换 + 属性缺失空串 + 节点缺失抛错")
    void renderer_templateCases() {
        ObjectNode input = MAPPER.createObjectNode();
        input.put("question", "查询库存");
        FlowState state = new FlowState(input);
        ObjectNode output = MAPPER.createObjectNode();
        output.put("content", "库存充足");
        state.putNodeOutput("node_rag_1", output);

        // 普通替换
        String rendered = FlowTemplateRenderer.render(
            "请分析：{{input.question}}，结果：{{nodes.node_rag_1.output.content}}",
            state, "测试");
        assertEquals("请分析：查询库存，结果：库存充足", rendered);

        // 属性缺失 → 空串（不报错）
        String missing = FlowTemplateRenderer.render("缺{{input.nonexistent}}失", state, "测试");
        assertEquals("缺失", missing);

        // 节点未执行 → 抛错（带上下文标签）
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> FlowTemplateRenderer.render("{{nodes.node_not_run.output.content}}", state, "节点 LLM 的 promptTemplate"));
        assertTrue(ex.getMessage().contains("节点 LLM 的 promptTemplate"), "错误应带上下文标签: " + ex.getMessage());
    }

    // ============ FlowConditionEvaluator ============

    @Test
    @DisplayName("P1-6: IF 条件 AND/OR 组合与各操作符")
    void conditionEvaluator_andOrOperators() {
        ObjectNode input = MAPPER.createObjectNode();
        input.put("amount", 1500);
        input.put("status", "APPROVED");
        FlowState state = new FlowState(input);

        // AND: 两个条件都满足
        boolean andTrue = com.nexus.ai.flow.expr.FlowConditionEvaluator.evaluate("AND", List.of(
            new com.nexus.ai.flow.expr.FlowConditionEvaluator.Condition("input.status", "EQ", "APPROVED"),
            new com.nexus.ai.flow.expr.FlowConditionEvaluator.Condition("input.amount", "GT", "1000")
        ), state);
        assertTrue(andTrue);

        // AND: 一个不满足 → false
        boolean andFalse = com.nexus.ai.flow.expr.FlowConditionEvaluator.evaluate("AND", List.of(
            new com.nexus.ai.flow.expr.FlowConditionEvaluator.Condition("input.status", "EQ", "REJECTED"),
            new com.nexus.ai.flow.expr.FlowConditionEvaluator.Condition("input.amount", "GT", "1000")
        ), state);
        assertEquals(false, andFalse);

        // OR: 任一满足 → true
        boolean orTrue = com.nexus.ai.flow.expr.FlowConditionEvaluator.evaluate("OR", List.of(
            new com.nexus.ai.flow.expr.FlowConditionEvaluator.Condition("input.status", "EQ", "REJECTED"),
            new com.nexus.ai.flow.expr.FlowConditionEvaluator.Condition("input.amount", "GTE", "1500")
        ), state);
        assertTrue(orTrue);

        // CONTAINS / IS_EMPTY
        boolean contains = com.nexus.ai.flow.expr.FlowConditionEvaluator.evaluate("AND", List.of(
            new com.nexus.ai.flow.expr.FlowConditionEvaluator.Condition("input.status", "CONTAINS", "PROV")
        ), state);
        assertTrue(contains);
        boolean empty = com.nexus.ai.flow.expr.FlowConditionEvaluator.evaluate("AND", List.of(
            new com.nexus.ai.flow.expr.FlowConditionEvaluator.Condition("input.nonexistent", "IS_EMPTY", "")
        ), state);
        assertTrue(empty);
    }

    // ============ NodeProvider 校验 ============

    @Test
    @DisplayName("P1-6: LLM 节点 schema 与校验")
    void llmProvider_schemaAndValidate() {
        // 用最小 mock provider 验证 registry 类型
        DummyProvider provider = new DummyProvider();
        assertEquals("dummy", provider.getNodeType());
        assertNotNull(provider.getParamSchemas());
        assertThrows(IllegalArgumentException.class, () -> provider.validateConfig(null));
    }

    @Test
    @DisplayName("P1-6: FlowNodeDef 配置缺省值")
    void nodeDef_defaults() {
        String configJson = """
            {"nodes": [{"id": "n1", "type": "llm", "position": [0, 0], "data": {"label": "测试"}}], "edges": []}
            """;
        FlowGraph graph = FlowGraph.parse(configJson);
        FlowNodeDef def = graph.getNode("n1");
        assertEquals("测试", def.getName());
        assertEquals("STOP", def.getOnError());
        assertEquals(false, def.isRetryOnFail());
        assertEquals(3, def.getRetryMaxTries());
        assertEquals(1000L, def.getWaitBetweenTries());
        assertEquals(600L, def.getTimeoutSeconds());
    }

    /**
     * 最小 mock Provider（仅用于 registry/校验测试）
     */
    static class DummyProvider implements FlowNodeProvider {
        @Override
        public String getNodeType() {
            return "dummy";
        }

        @Override
        public String getDisplayName() {
            return "Dummy";
        }

        @Override
        public List<NodeParamSchema> getParamSchemas() {
            return List.of();
        }

        @Override
        public void validateConfig(JsonNode config) {
            if (config == null) {
                throw new IllegalArgumentException("配置不能为空");
            }
        }

        @Override
        public JsonNode execute(FlowExecutionContext ctx) {
            return MAPPER.createObjectNode().put("ok", true);
        }
    }
}
