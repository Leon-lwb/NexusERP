package com.nexus.flow.node.control;
import com.nexus.flow.node.FlowNodeProvider;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.node.NodeParamSchema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.flow.model.FlowEdgeDef;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 合并节点执行器
 * <p>等待全部上游完成（现有 inDegree 语义天然实现——merge 入度=输入边数，全部到达才入队），
 * 输出 {inputs: {nodeId: output}, count} 供下游统一引用。
 * 前端渲染多输入端口（inputs/main/0、inputs/main/1...）</p>
 *
 * @author NexusERP
 */
@Component
public class MergeNodeProvider implements FlowNodeProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getNodeType() {
        return "merge";
    }

    @Override
    public String getDisplayName() {
        return "合并汇总";
    }

    @Override
    public List<NodeParamSchema> getParamSchemas() {
        return List.of();
    }

    @Override
    public void validateConfig(JsonNode config) {
        // 无参数，无需校验
    }

    @Override
    public JsonNode execute(FlowExecutionContext ctx) {
        ObjectNode output = MAPPER.createObjectNode();
        ObjectNode inputs = output.putObject("inputs");
        int count = 0;

        // 只收集本节点的直接上游输出（引擎保证非 SKIPPED 上游全部 SUCCEEDED——merge 入度=输入边数）
        // 注意：SKIPPED 上游参与入度簿记但无 output 节点，需宽容处理（跳过）
        if (ctx.getGraph() != null) {
            for (FlowEdgeDef edge : ctx.getGraph().getInEdges(ctx.getNodeId())) {
                JsonNode upstreamOutput = null;
                try {
                    upstreamOutput = ctx.getState().get("nodes." + edge.getSource() + ".output");
                } catch (IllegalArgumentException e) {
                    // 上游未执行（SKIPPED 分支），跳过该输入
                    continue;
                }
                if (upstreamOutput != null && !upstreamOutput.isMissingNode() && !upstreamOutput.isNull()) {
                    inputs.set(edge.getSource(), upstreamOutput);
                    count++;
                }
            }
        }
        output.put("count", count);
        return output;
    }
}
