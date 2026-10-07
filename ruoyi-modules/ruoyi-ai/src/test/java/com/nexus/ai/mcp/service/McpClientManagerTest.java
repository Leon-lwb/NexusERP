package com.nexus.ai.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.tool.BuiltinToolRegistry;
import com.nexus.common.ai.domain.entity.AiTool;
import com.nexus.common.ai.mapper.AiToolMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** 所有传输和客户端构造均为 mock，不发网络请求、不启动 MCP 进程。 */
class McpClientManagerTest {
    private final AiToolMapper mapper = mock(AiToolMapper.class);
    private final McpClientManager manager = new McpClientManager(mapper, new ObjectMapper());
    private final DefaultMcpClient client = mock(DefaultMcpClient.class);

    @Test
    void cacheHitMustRecheckCurrentTenantVisibility() throws Exception {
        cache().put(1L, client);
        when(mapper.selectById(1L)).thenReturn(null);
        assertNull(manager.getOrCreateClient(1L));
        verify(mapper).selectById(1L);
    }

    @Test
    void cachedClientMustNotBypassDisabledStatus() throws Exception {
        cache().put(1L, client);
        AiTool tool = remote("https://93.184.216.34/mcp");
        tool.setStatus("1");
        when(mapper.selectById(1L)).thenReturn(tool);
        assertNull(manager.getOrCreateClient(1L));
    }

    @Test
    void healthFailsIfToolDiscoveryFailsAndClosesTheCandidate() throws Exception {
        when(mapper.selectById(1L)).thenReturn(remote("https://93.184.216.34/mcp"));
        when(client.listTools()).thenThrow(new IllegalStateException("test-only-discovery-failure"));
        try (MockedStatic<StreamableHttpMcpTransport> transport = mockTransport();
             MockedConstruction<DefaultMcpClient.Builder> builders = mockBuilders()) {
            assertFalse(manager.checkToolHealth(1L), "构造成功不能冒充 tools/list 成功");
            assertEquals(0, manager.getActiveClientCount());
            verify(client).close();
        }
    }

    @Test
    void connectionTestReturnsActualDiscoveredNamesNotConfiguredAlias() {
        when(mapper.selectById(1L)).thenReturn(remote("https://93.184.216.34/mcp"));
        when(client.listTools()).thenReturn(List.of(
            ToolSpecification.builder().name("read_demo_summary").build(),
            ToolSpecification.builder().name("read_demo_status").build()));
        McpToolServiceImpl service = new McpToolServiceImpl(mapper, mock(BuiltinToolRegistry.class), manager);
        try (MockedStatic<StreamableHttpMcpTransport> transport = mockTransport();
             MockedConstruction<DefaultMcpClient.Builder> builders = mockBuilders()) {
            var result = service.testTool(1L);
            assertTrue(result.isSuccess());
            assertEquals(2, result.getToolCount());
            assertEquals(List.of("read_demo_summary", "read_demo_status"), result.getTools());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1:8080/private", "http://10.0.0.1/mcp",
        "http://169.254.169.254/latest/meta-data", "http://[::1]/mcp", "http://[fd00::1]/mcp",
        "file:///etc/passwd", "https://user:pass@93.184.216.34/mcp"})
    void remoteConnectionRejectsUnsafeEndpointsBeforeTransportConstruction(String url) {
        when(mapper.selectById(1L)).thenReturn(remote(url));
        try (MockedStatic<StreamableHttpMcpTransport> transport = mockTransport();
             MockedConstruction<DefaultMcpClient.Builder> builders = mockBuilders()) {
            assertNull(manager.getOrCreateClient(1L));
            transport.verify(StreamableHttpMcpTransport::builder, never());
        }
    }

    @Test
    void discoveryRechecksLatestStatusInsideToolLock() {
        AiTool active = remote("https://93.184.216.34/mcp");
        AiTool disabled = remote("https://93.184.216.34/mcp");
        disabled.setStatus("1");
        when(mapper.selectById(1L)).thenReturn(active, disabled);

        assertNull(manager.discoverToolNames(1L));
        verify(client, never()).listTools();
    }

    @Test
    void remoteTransportDisablesRedirectsAndWireLogs() {
        when(mapper.selectById(1L)).thenReturn(remote("https://93.184.216.34/mcp"));
        StreamableHttpMcpTransport.Builder builder = mock(StreamableHttpMcpTransport.Builder.class, RETURNS_SELF);
        try (MockedStatic<StreamableHttpMcpTransport> transport = mockStatic(StreamableHttpMcpTransport.class);
             MockedConstruction<DefaultMcpClient.Builder> builders = mockBuilders()) {
            transport.when(StreamableHttpMcpTransport::builder).thenReturn(builder);
            assertNotNull(manager.getOrCreateClient(1L));
            verify(builder).followRedirects(false);
            verify(builder).logRequests(false);
            verify(builder).logResponses(false);
            verify(builder, never()).logRequests(true);
        }
    }

    @Test
    void remoteHeadersAreActuallyAppliedToTheTransport() {
        AiTool tool = remote("https://93.184.216.34/mcp");
        tool.setConfigJson("{\"baseUrl\":\"https://93.184.216.34/mcp\",\"headers\":{\"Authorization\":\"test-only-secret\"}}");
        when(mapper.selectById(1L)).thenReturn(tool);
        StreamableHttpMcpTransport.Builder builder = mock(StreamableHttpMcpTransport.Builder.class, RETURNS_SELF);
        try (MockedStatic<StreamableHttpMcpTransport> transport = mockStatic(StreamableHttpMcpTransport.class);
             MockedConstruction<DefaultMcpClient.Builder> builders = mockBuilders()) {
            transport.when(StreamableHttpMcpTransport::builder).thenReturn(builder);
            assertNotNull(manager.getOrCreateClient(1L));
            verify(builder).customHeaders(Map.of("Authorization", "test-only-secret"));
        }
    }

    @Test
    void localEnvironmentIsAppliedWithoutStartingAnyRealProcess() throws Exception {
        AiTool tool = remote("https://93.184.216.34/mcp");
        tool.setToolType("LOCAL");
        tool.setConfigJson("{\"command\":\"node\",\"args\":[\"server.mjs\"],\"env\":{\"TZ\":\"UTC\",\"DEMO_READ_ONLY\":\"1\"}}");
        when(mapper.selectById(1L)).thenReturn(tool);
        when(client.listTools()).thenReturn(List.of());
        Process process = mock(Process.class);
        when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(process.exitValue()).thenReturn(0);
        StdioMcpTransport.Builder builder = mock(StdioMcpTransport.Builder.class, RETURNS_SELF);
        try (MockedConstruction<ProcessBuilder> probes = mockConstruction(ProcessBuilder.class,
                 (probe, context) -> {
                     when(probe.redirectErrorStream(true)).thenReturn(probe);
                     when(probe.start()).thenReturn(process);
                 });
             MockedStatic<StdioMcpTransport> transport = mockStatic(StdioMcpTransport.class);
             MockedConstruction<DefaultMcpClient.Builder> builders = mockBuilders()) {
            transport.when(StdioMcpTransport::builder).thenReturn(builder);
            assertTrue(manager.checkToolHealth(1L));
            verify(builder).environment(Map.of("TZ", "UTC", "DEMO_READ_ONLY", "1"));
            verify(builder).logEvents(false);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"node", "uv", "uvx"})
    void windowsExecutablesMustNotBeRewrittenToNonexistentCmdScripts(String executable) throws Exception {
        Method method = McpClientManager.class.getDeclaredMethod("resolveCommand", String.class);
        method.setAccessible(true);
        assertEquals(executable, method.invoke(manager, executable));
    }

    private MockedStatic<StreamableHttpMcpTransport> mockTransport() {
        MockedStatic<StreamableHttpMcpTransport> transport = mockStatic(StreamableHttpMcpTransport.class);
        StreamableHttpMcpTransport.Builder builder = mock(StreamableHttpMcpTransport.Builder.class, RETURNS_SELF);
        transport.when(StreamableHttpMcpTransport::builder).thenReturn(builder);
        return transport;
    }

    private MockedConstruction<DefaultMcpClient.Builder> mockBuilders() {
        return mockConstruction(DefaultMcpClient.Builder.class, withSettings().defaultAnswer(RETURNS_SELF),
            (builder, context) -> when(builder.build()).thenReturn(client));
    }

    @SuppressWarnings("unchecked")
    private Map<Long, McpClient> cache() throws Exception {
        Field field = McpClientManager.class.getDeclaredField("activeClients");
        field.setAccessible(true);
        return (Map<Long, McpClient>) field.get(manager);
    }

    private AiTool remote(String url) {
        AiTool tool = new AiTool();
        tool.setId(1L);
        tool.setToolName("configured_alias");
        tool.setToolType("REMOTE");
        tool.setStatus("0");
        tool.setConfigJson("{\"baseUrl\":\"" + url + "\"}");
        return tool;
    }
}
