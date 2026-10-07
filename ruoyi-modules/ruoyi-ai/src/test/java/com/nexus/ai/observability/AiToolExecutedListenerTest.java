package com.nexus.ai.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 完成事件没有工具级计时，兼容展示不能冒充实时开始或零毫秒执行。 */
class AiToolExecutedListenerTest {

    private final AiToolExecutedListener listener = new AiToolExecutedListener();
    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void 清理事件通道() {
        AiToolExecutedListener.clearChannel();
    }

    @Test
    void 无真实计时时必须明确未知并保留完成结果() throws Exception {
        List<JsonNode> events = receive("查询成功：精密电机壳体库存 12 件");
        assertEquals(2, events.size(), "旧前端需要兼容建卡与完成事件配对，但不得把它当作实时进度");
        JsonNode end = events.get(1);
        assertEquals("TOOL_END", end.path("type").asText());
        assertEquals(-1L, end.path("duration").asLong(), "负值只表示未知，不是测得耗时");
        assertTrue(end.path("resultSummary").asText().contains("unknown"));
        assertTrue(end.path("resultSummary").asText().contains("非实时"));
        assertTrue(end.path("resultSummary").asText().contains("精密电机壳体"));
        assertFalse(end.has("startedAt"));
    }

    @Test
    void 完成后补发的兼容建卡必须明确标为快照() throws Exception {
        JsonNode start = receive("查询成功，但未返回数据。").get(0);
        assertEquals("TOOL_START", start.path("type").asText());
        assertEquals("queryInventoryValue", start.path("toolName").asText());
        assertTrue(start.path("arguments").asText().contains("完成后补发"));
        assertTrue(start.path("arguments").asText().contains("非实时"));
        assertTrue(start.path("arguments").asText().contains("{}"));
        assertFalse(start.has("startedAt"));
    }

    @Test
    void 长结果截断后仍须保留未知计时说明() throws Exception {
        JsonNode end = receive("精密电机壳体库存明细".repeat(80)).get(1);
        assertTrue(end.path("resultSummary").asText().contains("unknown"));
        assertTrue(end.path("resultSummary").asText().contains("完成后补发"));
        assertTrue(end.path("resultSummary").asText().length() <= 303);
    }

    @Test
    void 清除通道后不得污染后续请求() throws Exception {
        OutputChannel channel = new OutputChannel();
        AiToolExecutedListener.setChannel(channel);
        AiToolExecutedListener.clearChannel();
        assertDoesNotThrow(() -> listener.onEvent(event("查询成功，但未返回数据。")));
        channel.complete();
        List<String> output = new ArrayList<>();
        channel.drain(output::add);
        assertTrue(output.isEmpty());
    }

    private List<JsonNode> receive(String result) throws Exception {
        OutputChannel channel = new OutputChannel();
        AiToolExecutedListener.setChannel(channel);
        listener.onEvent(event(result));
        channel.complete();
        List<String> output = new ArrayList<>();
        channel.drain(output::add);
        List<JsonNode> parsed = new ArrayList<>();
        for (String message : output) parsed.add(mapper.readTree(message));
        return parsed;
    }

    private ToolExecutedEvent event(String result) {
        InvocationContext context = mock(InvocationContext.class);
        when(context.invocationId()).thenReturn(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        return ToolExecutedEvent.builder()
            .invocationContext(context)
            .request(ToolExecutionRequest.builder().id("tool-1").name("queryInventoryValue").arguments("{}").build())
            .resultText(result)
            .build();
    }
}
