package com.nexus.ai.mcp.service;

import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import com.nexus.ai.mcp.McpPinnedHttpClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.controller.McpMarketController;
import com.nexus.ai.mcp.service.impl.McpMarketServiceImpl;
import com.nexus.ai.tool.BuiltinToolRegistry;
import com.nexus.common.ai.domain.entity.AiMcpMarket;
import com.nexus.common.ai.domain.entity.AiMcpMarketTool;
import com.nexus.common.ai.domain.entity.AiTool;
import com.nexus.common.ai.mapper.AiMcpMarketMapper;
import com.nexus.common.ai.mapper.AiMcpMarketToolMapper;
import com.nexus.common.ai.mapper.AiToolMapper;
import com.nexus.common.core.exception.ServiceException;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 保存入口与真实连接入口必须读取同一运维配置；所有传输均 mock，不启停服务。 */
class McpLocalEndpointAllowlistTest {
    private static final String LOCAL = "http://127.0.0.1:19540/mcp";
    private final AiToolMapper tools = mock(AiToolMapper.class);
    private final AiMcpMarketMapper markets = mock(AiMcpMarketMapper.class);
    private final AiMcpMarketToolMapper marketTools = mock(AiMcpMarketToolMapper.class);
    private final ObjectMapper json = new ObjectMapper();
    private final McpPinnedHttpClientBuilder.PinnedClient marketHttp = mock(McpPinnedHttpClientBuilder.PinnedClient.class);

    @Test
    void allValidationEntrypointsUseServerOnlyPropertyWithEmptyDefault() {
        for (Class<?> type : List.of(McpClientManager.class, McpMarketServiceImpl.class,
            McpToolServiceImpl.class, McpMarketController.class)) {
            var field = ReflectionUtils.findField(type, "allowedLocalEndpoints");
            assertNotNull(field, type.getSimpleName());
            Value property = field.getAnnotation(Value.class);
            assertNotNull(property);
            assertEquals("${nexus.ai.mcp.allowed-local-endpoints:}", property.value());
        }
    }

    @Test
    void toolAddAndEditAcceptTheAuthorizedEndpointButNotAnAdjacentPath() {
        McpToolServiceImpl service = configured(new McpToolServiceImpl(tools, mock(BuiltinToolRegistry.class),
            mock(McpClientManager.class)), LOCAL);
        assertDoesNotThrow(() -> service.addTool(tool(LOCAL)));
        verify(tools).insert(any(AiTool.class));
        when(tools.selectById(1L)).thenReturn(tool("https://93.184.216.34/mcp"));
        when(tools.updateById(any(AiTool.class))).thenReturn(1);
        assertDoesNotThrow(() -> service.updateTool(tool(LOCAL)));
        assertThrows(ServiceException.class, () -> service.updateTool(tool(LOCAL + "/child")));
    }

    @Test
    void marketAddAndEditAcceptTheAuthorizedEndpointButNotAnAdjacentPort() {
        McpMarketController controller = configured(new McpMarketController(markets, marketTools, mock(IMcpMarketService.class)), LOCAL);
        assertDoesNotThrow(() -> controller.add(market(LOCAL)));
        verify(markets).insert(any(AiMcpMarket.class));
        when(markets.selectById(1L)).thenReturn(market("https://93.184.216.34/catalog"));
        assertDoesNotThrow(() -> controller.edit(market(LOCAL)));
        assertThrows(ServiceException.class, () -> controller.add(market("http://127.0.0.1:19541/mcp")));
    }

    @Test
    void marketRefreshAllowsOnlyTheExactConfiguredEndpointAndStillDisablesRedirects() {
        McpMarketServiceImpl service = configured(new McpMarketServiceImpl(markets, marketTools, tools, json), LOCAL);
        when(markets.selectById(1L)).thenReturn(market(LOCAL));
        try (var builders = marketTransport("[]")) {
            assertEquals(0, assertDoesNotThrow(() -> service.refreshMarketTools(1L)).addedCount());
            ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
            verify(marketHttp).execute(request.capture());
            assertEquals(LOCAL, request.getValue().url());
            assertEquals(1, builders.constructed().size());
            verify(marketHttp).close();
        }
    }

    @Test
    void catalogRefreshImportsOneRemoteServiceEntryWithTheSeparateMcpBaseUrl() throws Exception {
        String catalog = "http://127.0.0.1:19540/catalog";
        McpMarketServiceImpl service = configured(new McpMarketServiceImpl(markets, marketTools, tools, json),
            LOCAL + "," + catalog);
        when(markets.selectById(1L)).thenReturn(market(catalog));
        AtomicReference<AiMcpMarketTool> imported = new AtomicReference<>();
        when(marketTools.insert(any(AiMcpMarketTool.class))).thenAnswer(call -> {
            AiMcpMarketTool entry = call.getArgument(0);
            entry.setId(2L);
            imported.set(entry);
            return 1;
        });
        when(marketTools.selectById(2L)).thenAnswer(call -> imported.get());
        when(tools.insert(any(AiTool.class))).thenAnswer(call -> {
            AiTool tool = call.getArgument(0);
            tool.setId(3L);
            return 1;
        });
        try (var builders = marketTransport("{\"tools\":[{\"name\":\"local_business_docs\",\"baseUrl\":\"" + LOCAL + "\"}]}")) {
            var refreshed = service.refreshMarketTools(1L);
            assertEquals(1, refreshed.addedCount());
            assertEquals(0, refreshed.updatedCount());
            assertEquals(3L, service.loadToolToLocal(2L));
            ArgumentCaptor<AiTool> loaded = ArgumentCaptor.forClass(AiTool.class);
            verify(tools).insert(loaded.capture());
            assertEquals("REMOTE", loaded.getValue().getToolType());
            assertEquals("1", loaded.getValue().getStatus(), "市场加载仍默认停用，不能冒称连通");
            assertEquals(LOCAL, json.readTree(loaded.getValue().getConfigJson()).path("baseUrl").asText());
            ArgumentCaptor<HttpRequest> request = ArgumentCaptor.forClass(HttpRequest.class);
            verify(marketHttp).execute(request.capture());
            assertEquals(catalog, request.getValue().url());
            verify(marketHttp).close();
        }
    }

    @Test
    void remoteDiscoveryAllowsTheExactEndpointAndStillDisablesRedirectsAndWireLogs() {
        McpClientManager manager = configured(new McpClientManager(tools, json), LOCAL);
        when(tools.selectById(1L)).thenReturn(tool(LOCAL));
        DefaultMcpClient client = mock(DefaultMcpClient.class);
        when(client.listTools()).thenReturn(List.of());
        StreamableHttpMcpTransport.Builder builder = mock(StreamableHttpMcpTransport.Builder.class, RETURNS_SELF);
        try (MockedStatic<StreamableHttpMcpTransport> transport = mockStatic(StreamableHttpMcpTransport.class);
             MockedConstruction<DefaultMcpClient.Builder> clients = mockConstruction(DefaultMcpClient.Builder.class,
                 withSettings().defaultAnswer(RETURNS_SELF), (candidate, context) -> when(candidate.build()).thenReturn(client))) {
            transport.when(StreamableHttpMcpTransport::builder).thenReturn(builder);
            assertTrue(manager.checkToolHealth(1L));
            verify(client).listTools();
            verify(builder).url(LOCAL);
            verify(builder).followRedirects(false);
            verify(builder).logRequests(false);
            verify(builder).logResponses(false);
        }
    }

    @Test
    void apiConfigurationCannotGrantTheLocalEndpointException() {
        McpClientManager manager = new McpClientManager(tools, json);
        AiTool request = tool(LOCAL);
        request.setConfigJson("{\"baseUrl\":\"" + LOCAL + "\",\"allowPrivate\":true,\"allowedLocalEndpoints\":[\"" + LOCAL + "\"]}");
        when(tools.selectById(1L)).thenReturn(request);
        try (MockedStatic<StreamableHttpMcpTransport> transport = mockStatic(StreamableHttpMcpTransport.class)) {
            assertNull(manager.getOrCreateClient(1L));
            transport.verify(StreamableHttpMcpTransport::builder, never());
        }
    }

    @Test
    void invalidServerConfigurationBlocksTransportBeforeAnyRequest() {
        McpClientManager manager = configured(new McpClientManager(tools, json), LOCAL + ",invalid");
        when(tools.selectById(1L)).thenReturn(tool("https://93.184.216.34/mcp"));
        DefaultMcpClient client = mock(DefaultMcpClient.class);
        StreamableHttpMcpTransport.Builder builder = mock(StreamableHttpMcpTransport.Builder.class, RETURNS_SELF);
        try (MockedStatic<StreamableHttpMcpTransport> transport = mockStatic(StreamableHttpMcpTransport.class);
             MockedConstruction<DefaultMcpClient.Builder> clients = mockConstruction(DefaultMcpClient.Builder.class,
                 withSettings().defaultAnswer(RETURNS_SELF), (candidate, context) -> when(candidate.build()).thenReturn(client))) {
            transport.when(StreamableHttpMcpTransport::builder).thenReturn(builder);
            assertNull(manager.getOrCreateClient(1L));
            transport.verify(StreamableHttpMcpTransport::builder, never());
        }
    }

    private MockedConstruction<McpPinnedHttpClientBuilder> marketTransport(String body) {
        when(marketHttp.execute(any())).thenReturn(SuccessfulHttpResponse.builder().statusCode(200).body(body).build());
        return mockConstruction(McpPinnedHttpClientBuilder.class, withSettings().defaultAnswer(RETURNS_SELF),
            (builder, context) -> when(builder.build()).thenReturn(marketHttp));
    }

    private <T> T configured(T bean, String allowed) {
        ReflectionTestUtils.setField(bean, "allowedLocalEndpoints", allowed);
        return bean;
    }

    private AiTool tool(String url) {
        AiTool tool = new AiTool();
        tool.setId(1L);
        tool.setToolName("local_readonly_docs");
        tool.setToolType("REMOTE");
        tool.setStatus("0");
        tool.setConfigJson("{\"baseUrl\":\"" + url + "\"}");
        return tool;
    }

    private AiMcpMarket market(String url) {
        AiMcpMarket market = new AiMcpMarket();
        market.setId(1L);
        market.setName("local_readonly_docs");
        market.setUrl(url);
        return market;
    }
}
