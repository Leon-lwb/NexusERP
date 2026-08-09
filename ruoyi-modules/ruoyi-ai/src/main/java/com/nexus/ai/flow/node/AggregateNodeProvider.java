package com.nexus.ai.flow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 聚合节点（对齐 n8n Aggregate）
 * <p>把循环各轮（或重复执行）的上游节点输出收集为数组，供下游一次性处理。
 * 引擎在执行节点时把每轮输出追加到 root.batchOutputs.&lt;nodeId&gt;（见 FlowEngineImpl），
 * 本节点读取直接上游的批次历史合并输出：{items: [...], sources: {nodeId: count}}</p>
 *
 * @author NexusERP
 */
@Component
public class AggregateNodeProvider implements FlowNodeProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getNodeType() {
        return "aggregate";
    }

    @Override
    public String getDisplayName() {
        return "聚合汇总";
    }

    @Override
    public List<NodeParamSchema> getParamSchemas() {
        NodeParamSchema mode = new NodeParamSchema("mode", "聚合方式", "options", "ALL_UPSTREAM", false);
        mode.setOptions(List.of(Map.of("name", "全部上游（收集直接上游每轮输出）", "value", "ALL_UPSTREAM")));
        return List.of(mode);
    }

    @Override
    public void validateConfig(JsonNode config) {
        // 无强制配置
    }

    @Override
    public JsonNode execute(FlowExecutionContext ctx) {
        JsonNode root = ctx.getState().getRoot();
        JsonNode batchOutputs = root == null ? null : root.get("batchOutputs");

        ArrayNode items = MAPPER.createArrayNode();
        ObjectNode sources = MAPPER.createObjectNode();
        // 遍历直接上游，收集其批次历史输出（无历史 → 空数组）
        for (com.nexus.ai.flow.model.FlowEdgeDef edge : ctx.getGraph().getInEdges(ctx.getNodeId())) {
            // 上游是 split 循环节点时：收集其循环体尾节点的输出（loop 返回边 source）——
            // 语义对齐 n8n：聚合的是"每轮循环处理结果"，split 自身的 done 空批输出不进入
            String collectFrom = edge.getSource();
            var sourceNode = ctx.getGraph().getNode(edge.getSource());
            if (sourceNode != null && "split".equals(sourceNode.getType())) {
                for (var out : ctx.getGraph().getOutEdges(edge.getSource())) {
                    if (out.isLoopEdge()) {
                        collectFrom = out.getSource();
                        break;
                    }
                }
            }
            JsonNode history = null;
            if (batchOutputs != null && batchOutputs.isObject()) {
                history = batchOutputs.get(collectFrom);
            }
            int count = 0;
            if (history != null && history.isArray()) {
                for (JsonNode item : history) {
                    items.add(item);
                    count++;
                }
            }
            sources.put(collectFrom, count);
        }

        ObjectNode output = MAPPER.createObjectNode();
        output.set("items", items);
        output.set("sources", sources);
        output.put("count", items.size());
        return output;
    }
}
