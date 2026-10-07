package com.nexus.ai.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.mcp.McpPinnedHttpClientBuilder;
import com.nexus.ai.mcp.service.impl.McpMarketServiceImpl;
import com.nexus.common.ai.domain.entity.AiMcpMarket;
import com.nexus.common.ai.mapper.AiMcpMarketMapper;
import com.nexus.common.ai.mapper.AiMcpMarketToolMapper;
import com.nexus.common.ai.mapper.AiToolMapper;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 保留市场URL/导入/跳转/异常回归；改用受控HTTP客户端替身，不发请求。 */
class McpMarketServiceImplTest {
    private final AiMcpMarketMapper mapper = mock(AiMcpMarketMapper.class);
    private final McpMarketServiceImpl service = new McpMarketServiceImpl(mapper,
        mock(AiMcpMarketToolMapper.class), mock(AiToolMapper.class), new ObjectMapper());
    private final McpPinnedHttpClientBuilder.PinnedClient http = mock(McpPinnedHttpClientBuilder.PinnedClient.class);

    @Test void refreshRejectsStoredLoopbackUrlBeforeHttpRequest() {
        when(mapper.selectById(1L)).thenReturn(market("http://127.0.0.1:8080/private"));
        try(var builders=fakeResponse()){
            assertThrows(RuntimeException.class,()->service.refreshMarketTools(1L));
            assertTrue(builders.constructed().isEmpty());verifyNoInteractions(http);
        }
    }

    @Test void loadingUnauthorizedRemoteMetadataFailsBeforeWritingLocalTool() {
        var entry=new com.nexus.common.ai.domain.entity.AiMcpMarketTool();entry.setId(2L);entry.setToolName("unauthorized_remote");
        entry.setToolMetadata("{\"baseUrl\":\"http://127.0.0.1:19540/mcp\"}");entry.setIsLoaded("0");
        var entries=mock(AiMcpMarketToolMapper.class);var tools=mock(AiToolMapper.class);
        var guarded=new McpMarketServiceImpl(mapper,entries,tools,new ObjectMapper());when(entries.selectById(2L)).thenReturn(entry);
        assertThrows(RuntimeException.class,()->guarded.loadToolToLocal(2L));
        verify(tools,never()).insert(any(com.nexus.common.ai.domain.entity.AiTool.class));
        verify(entries,never()).updateById(any(com.nexus.common.ai.domain.entity.AiMcpMarketTool.class));
    }

    @Test void refreshDisablesAutomaticRedirects() {
        when(mapper.selectById(1L)).thenReturn(market("https://93.184.216.34/catalog"));
        try(var builders=fakeResponse()){
            assertEquals(0,service.refreshMarketTools(1L).addedCount());assertEquals(1,builders.constructed().size());
            // 受控适配器的真实DNS/代理/禁止跳转设置由McpPinnedHttpClientTest验证。
            var request=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);verify(http).execute(request.capture());
            assertEquals("https://93.184.216.34/catalog",request.getValue().url());verify(http).close();
        }
    }

    @Test void refreshNetworkFailureDoesNotExposeUrlCredentials() {
        when(mapper.selectById(1L)).thenReturn(market("https://93.184.216.34/catalog?token=test-only-secret"));
        try(var builders=fakeResponse()){
            when(http.execute(any())).thenThrow(new IllegalStateException("request url contains test-only-secret"));
            RuntimeException error=assertThrows(RuntimeException.class,()->service.refreshMarketTools(1L));
            assertFalse(error.getMessage().contains("test-only-secret"));assertNull(error.getCause());verify(http).close();
        }
    }

    private MockedConstruction<McpPinnedHttpClientBuilder> fakeResponse(){
        when(http.execute(any())).thenReturn(SuccessfulHttpResponse.builder().statusCode(200).body("[]").build());
        return mockConstruction(McpPinnedHttpClientBuilder.class,withSettings().defaultAnswer(RETURNS_SELF),
            (builder,context)->when(builder.build()).thenReturn(http));
    }
    private AiMcpMarket market(String url){AiMcpMarket market=new AiMcpMarket();market.setId(1L);market.setUrl(url);return market;}
}
