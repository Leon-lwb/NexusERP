package com.nexus.flow.node.control;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.common.flow.execution.FlowReplayPolicy;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.node.FlowNodeProvider;
import com.nexus.flow.node.NodeParamSchema;
import com.nexus.flow.state.FlowTemplateRenderer;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 循环分批节点执行器（对齐 n8n SplitInBatches）
 * <p>把数组按 batchSize 分批：每轮输出一批到 loop 输出端口（outputs/loop/main），
 * 循环体尾节点通过 loop 返回边（target=split 自身）回到本节点取下一批；
 * 全部取完输出 done 到 outputs/loop/done 端口。</p>
 * <p>迭代状态存 FlowState 的 loops 段（root.loops.&lt;nodeId&gt; = {index, total}），
 * 由引擎在 fanOut 时读取本节点 output.done 决定重入队或结束</p>
 *
 * @author NexusERP
 */
@Component
public class SplitInBatchesProvider implements FlowNodeProvider {
    @Override
    public FlowReplayPolicy replayPolicy(JsonNode config) {
        return FlowReplayPolicy.SAFE;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 单实例迭代上限（业务护栏，防死循环） */
    private static final int MAX_ITERATIONS = 1000;

    @Override
    public String getNodeType() {
        return "split";
    }

    @Override
    public String getDisplayName() {
        return "循环分批";
    }

    @Override
    public List<NodeParamSchema> getParamSchemas() {
        return List.of(
            new NodeParamSchema("arrayPath", "数组路径", "string", "input.items", true),
            new NodeParamSchema("batchSize", "每批数量", "number", "1", false)
        );
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config == null
                || config.get("arrayPath") == null
                || config.get("arrayPath").asText().isBlank()) {
            throw new IllegalArgumentException("循环节点缺少 arrayPath 配置");
        }
        if (config.has("batchSize") && config.get("batchSize").canConvertToInt()) {
            int batchSize = config.get("batchSize").asInt();
            if (batchSize < 1 || batchSize > 100) {
                throw new IllegalArgumentException("循环节点 batchSize 必须在 1-100 之间");
            }
        }
    }

    @Override
    public JsonNode execute(FlowExecutionContext ctx) {
        JsonNode config = ctx.getConfig();
        String arrayPath = config.get("arrayPath").asText();
        int batchSize = config.has("batchSize") && config.get("batchSize").canConvertToInt()
            ? Math.max(1, Math.min(config.get("batchSize").asInt(), 100)) : 1;
        String label = "节点 " + ctx.getNodeName();

        // 渲染数组路径（支持 {{}} 模板）
        String resolvedPath =
                FlowTemplateRenderer.render(arrayPath, ctx.getState(), label + " 的 arrayPath");
        // 取数组（缺失/非数组 → 空数组直接 done）
        ArrayNode items = MAPPER.createArrayNode();
        try {
            JsonNode value = ctx.getState().get(resolvedPath);
            if (value != null && value.isArray()) {
                items = (ArrayNode) value.deepCopy();
            } else if (value != null && value.isValueNode()) {
                items.add(value);
            }
        } catch (IllegalArgumentException e) {
            // 数组源不存在 → 空数组（直接 done）
        }

        int total = items.size();
        // 迭代状态：从 loops 段读取（首轮 index=0）
        JsonNode loops = ctx.getState().getRoot().get("loops");
        int index = 0;
        if (loops != null && loops.isObject() && loops.has(ctx.getNodeId())) {
            JsonNode state = loops.get(ctx.getNodeId());
            if (state != null && state.has("index")) {
                index = state.get("index").asInt();
            }
        }
        if (index >= MAX_ITERATIONS) {
            throw new IllegalStateException(
                    "循环节点 " + ctx.getNodeName() + " 迭代次数超过上限 " + MAX_ITERATIONS);
        }

        // 切当前批
        int start = index * batchSize;
        boolean done = start >= total;
        ArrayNode batch = MAPPER.createArrayNode();
        if (!done) {
            int end = Math.min(start + batchSize, total);
            for (int i = start; i < end; i++) {
                batch.add(items.get(i));
            }
        }
        int nextIndex = done ? index : index + 1;

        // 写回迭代状态
        ObjectNode loopState =
                (ObjectNode)
                        ctx.getState()
                                .getRoot()
                                .withObject("/loops")
                                .withObject("/" + ctx.getNodeId());
        loopState.put("index", nextIndex);
        loopState.put("total", total);

        // 组装输出（引擎按 done 决定 loop/done 分支）
        ObjectNode output = MAPPER.createObjectNode();
        output.set("batch", batch);
        output.put("batchSize", batchSize);
        output.put("index", index);
        output.put("total", total);
        output.put("done", done);
        return output;
    }
}
