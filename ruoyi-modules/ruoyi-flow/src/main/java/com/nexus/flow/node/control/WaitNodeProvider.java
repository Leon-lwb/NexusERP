package com.nexus.flow.node.control;
import com.nexus.flow.node.FlowNodeProvider;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.node.NodeParamSchema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 延迟等待节点执行器
 * <p>sleep 指定秒数后透传输入（对齐 n8n Wait 的简化版）。
 * 受引擎节点超时护栏保护（timeoutSeconds 默认 600s，wait 超过超时会被打断）</p>
 *
 * @author NexusERP
 */
@Component
public class WaitNodeProvider implements FlowNodeProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getNodeType() {
        return "wait";
    }

    @Override
    public String getDisplayName() {
        return "延迟等待";
    }

    @Override
    public List<NodeParamSchema> getParamSchemas() {
        return List.of(
            new NodeParamSchema("waitSeconds", "等待秒数", "number", "5", true)
        );
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config == null || !config.has("waitSeconds") || !config.get("waitSeconds").canConvertToInt()) {
            throw new IllegalArgumentException("延迟节点缺少 waitSeconds 配置");
        }
        int seconds = config.get("waitSeconds").asInt();
        if (seconds < 0 || seconds > 3600) {
            throw new IllegalArgumentException("延迟节点 waitSeconds 必须在 0-3600 之间");
        }
    }

    @Override
    public JsonNode execute(FlowExecutionContext ctx) throws InterruptedException {
        JsonNode config = ctx.getConfig();
        int seconds = config.get("waitSeconds").asInt();
        if (seconds > 0) {
            Thread.sleep(seconds * 1000L);
        }
        ObjectNode output = MAPPER.createObjectNode();
        output.put("waitedSeconds", seconds);
        output.put("result", "waited");
        return output;
    }
}
