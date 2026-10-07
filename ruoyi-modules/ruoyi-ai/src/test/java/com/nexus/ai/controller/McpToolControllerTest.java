package com.nexus.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.mcp.service.McpToolServiceImpl;
import com.nexus.common.ai.domain.entity.AiTool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** MCP 查询出参不得暴露连接凭证，也不得污染内部连接配置。 */
class McpToolControllerTest {
    private final McpToolServiceImpl service = mock(McpToolServiceImpl.class);
    private final McpToolController controller = new McpToolController(service);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void listMasksUrlEnvironmentHeadersAndEscapedSecrets() throws Exception {
        AiTool tool = tool("""
            {"baseUrl":"https://example.test/mcp?access_token=test-url-secret",
             "env":{"SERVICE_API_KEY":"test-env-secret"},
             "headers":{"Authorization":"test-header-secret"},
             "password":"test-escaped-\\\"secret"}
            """);
        json.readTree(tool.getConfigJson()); // 先确认原始夹具有效，再验证出参是否仍是合法 JSON
        when(service.getAllTools()).thenReturn(List.of(tool));
        String output = controller.list().getData().get(0).getConfigJson();
        JsonNode masked = assertDoesNotThrow(() -> json.readTree(output));
        assertFalse(output.contains("test-url-secret"));
        assertEquals("****", masked.path("env").path("SERVICE_API_KEY").asText());
        assertEquals("****", masked.path("headers").path("Authorization").asText());
        assertEquals("****", masked.path("password").asText());
    }

    @Test
    void queryMaskingDoesNotModifyTheServiceEntity() {
        AiTool tool = tool("{\"apiKey\":\"test-only-secret\"}");
        when(service.getToolById(1L)).thenReturn(tool);
        AiTool masked = controller.getInfo(1L).getData();
        assertEquals("{\"apiKey\":\"test-only-secret\"}", tool.getConfigJson());
        assertNotSame(tool, masked);
        assertFalse(masked.getConfigJson().contains("test-only-secret"));
    }

    @Test
    void malformedConfigurationFailsClosed() {
        when(service.getToolById(1L)).thenReturn(tool("{invalid test-only-secret"));
        assertEquals("****", controller.getInfo(1L).getData().getConfigJson());
    }

    @Test
    void commandArgumentsMustNotExposeCredentialFlags() {
        AiTool tool = tool("""
            {"command":"node", "args":["server.mjs","--token","test-arg-secret",
             "--api-key=test-inline-secret","https://example.test/?key=test-query-secret"]}
            """);
        when(service.getAllEnabledTools()).thenReturn(List.of(tool));
        String output = controller.getAvailableMcpTools().getData().get(0).getConfigJson();
        assertFalse(output.contains("test-arg-secret"));
        assertFalse(output.contains("test-inline-secret"));
        assertFalse(output.contains("test-query-secret"));
        assertTrue(output.contains("server.mjs"));
    }

    private AiTool tool(String config) {
        AiTool tool = new AiTool();
        tool.setId(1L);
        tool.setToolType("REMOTE");
        tool.setConfigJson(config);
        return tool;
    }
}
