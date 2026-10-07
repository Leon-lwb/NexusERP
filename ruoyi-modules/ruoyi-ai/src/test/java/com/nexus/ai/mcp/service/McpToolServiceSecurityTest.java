package com.nexus.ai.mcp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.tool.BuiltinToolRegistry;
import com.nexus.common.ai.domain.entity.AiTool;
import com.nexus.common.ai.mapper.AiToolMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** MCP 写入仅采用当前租户边界；脱敏回显按 JSON 结构恢复。 */
class McpToolServiceSecurityTest {
    private final AiToolMapper mapper = mock(AiToolMapper.class);
    private final McpClientManager manager = mock(McpClientManager.class);
    private final McpToolServiceImpl service = new McpToolServiceImpl(mapper, mock(BuiltinToolRegistry.class), manager);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void addDiscardsTenantFromRequest() throws Exception {
        AiTool request = json.readValue("{\"toolName\":\"demo\",\"toolType\":\"LOCAL\",\"status\":\"1\",\"tenantId\":\"123456\"}", AiTool.class);
        request.setConfigJson("{\"command\":\"node\",\"args\":[\"server.mjs\"]}");
        service.addTool(request);
        assertNull(request.getTenantId());
    }

    @Test
    void maskedEditRestoresOnlyMaskedFieldsRegardlessOfWhitespace() throws Exception {
        AiTool stored = remote("{\"baseUrl\":\"https://93.184.216.34/mcp\",\"headers\":{\"Authorization\":\"test-only-secret\"},\"label\":\"old\"}");
        when(mapper.selectById(1L)).thenReturn(stored);
        when(mapper.updateById(any(AiTool.class))).thenReturn(1);
        AiTool request = remote("""
            {"baseUrl":"https://93.184.216.34/mcp", "headers": {"Authorization": "****"}, "label":"new"}
            """);
        service.updateTool(request);
        JsonNode config = json.readTree(request.getConfigJson());
        assertEquals("test-only-secret", config.path("headers").path("Authorization").asText());
        assertEquals("new", config.path("label").asText());
    }

    @Test
    void editDiscardsTenantFromRequest() throws Exception {
        AiTool request = json.readValue("{\"id\":\"1\",\"tenantId\":\"123456\",\"displayName\":\"new\"}", AiTool.class);
        when(mapper.selectById(1L)).thenReturn(remote("{\"baseUrl\":\"https://93.184.216.34/mcp\"}"));
        service.updateTool(request);
        assertNull(request.getTenantId());
    }

    @Test
    void foreignOrMissingToolMustNotUpdateOrInvalidateClient() {
        AiTool request = remote("{\"baseUrl\":\"https://93.184.216.34/mcp\"}");
        when(mapper.selectById(1L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.updateTool(request));
        verify(mapper, never()).updateById(any(AiTool.class));
        verify(manager, never()).refreshClient(any());
    }

    @Test
    void addRejectsLoopbackRemoteEndpointBeforeInsert() {
        AiTool request = remote("{\"baseUrl\":\"http://127.0.0.1:8080/private\"}");
        assertThrows(RuntimeException.class, () -> service.addTool(request));
        verify(mapper, never()).insert(any(AiTool.class));
    }

    @Test
    void addRejectsLegacyStatusBeforeInsert() {
        AiTool request = remote("{\"baseUrl\":\"https://93.184.216.34/mcp\"}");
        request.setStatus("ENABLED");
        assertThrows(RuntimeException.class, () -> service.addTool(request));
        verify(mapper, never()).insert(any(AiTool.class));
    }

    @Test
    void foreignStatusChangeMustNotInvalidateAnotherTenantsClient() {
        when(mapper.selectById(1L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.toggleToolStatus(1L, "0"));
        verify(mapper, never()).updateById(any(AiTool.class));
        verify(manager, never()).refreshClient(any());
    }

    @Test
    void deletingAnInvisibleIdMustNotInvalidateAnotherTenantsClient() {
        when(mapper.selectById(1L)).thenReturn(null);
        assertEquals(0, service.deleteTools(java.util.List.of(1L)));
        verify(manager, never()).refreshClient(any());
    }

    private AiTool remote(String config) {
        AiTool tool = new AiTool();
        tool.setId(1L);
        tool.setToolName("configured_alias");
        tool.setToolType("REMOTE");
        tool.setStatus("0");
        tool.setConfigJson(config);
        return tool;
    }
}
