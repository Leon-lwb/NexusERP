package com.nexus.ai.mcp.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.mcp.service.impl.McpMarketServiceImpl;
import com.nexus.ai.tool.BuiltinToolRegistry;
import com.nexus.common.ai.domain.entity.AiMcpMarketTool;
import com.nexus.common.ai.domain.entity.AiTool;
import com.nexus.common.ai.mapper.AiMcpMarketMapper;
import com.nexus.common.ai.mapper.AiMcpMarketToolMapper;
import com.nexus.common.ai.mapper.AiToolMapper;
import com.nexus.common.core.exception.ServiceException;

import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.net.InetAddress;

/** 当前真实MCP库没有DNS/HTTP-client注入入口：域名REMOTE明确拒绝，数字IP能力保留。 */
class McpRemoteDnsFailClosedTest {
    private static final String HOST="review-remote.example.test";
    private static final String URL="https://"+HOST+"/mcp";
    private final AiToolMapper mapper=mock(AiToolMapper.class);

    @Test void 域名REMOTE配置保存必须明确拒绝而非留下不受约束的连接() throws Exception {
        McpToolServiceImpl service=new McpToolServiceImpl(mapper,mock(BuiltinToolRegistry.class),mock(McpClientManager.class));
        try(var dns=publicDns()){
            ServiceException error=assertThrows(ServiceException.class,()->service.addTool(tool(URL)));
            assertTrue(error.getMessage().contains("数字IP"));assertNull(error.getCause());verify(mapper,never()).insert(any(AiTool.class));
        }
    }

    @Test void 存量域名REMOTE不能触发默认客户端构造() throws Exception {
        McpClientManager manager=new McpClientManager(mapper,new ObjectMapper());when(mapper.selectById(1L)).thenReturn(tool(URL));
        try(var dns=publicDns();var transport=mockStatic(StreamableHttpMcpTransport.class)){
            assertNull(manager.getOrCreateClient(1L));transport.verify(StreamableHttpMcpTransport::builder,never());
        }
    }

    @Test void 市场导入不能绕过域名REMOTE限制() throws Exception {
        AiMcpMarketTool entry=new AiMcpMarketTool();entry.setId(2L);entry.setToolName("review_docs");entry.setToolMetadata("{\"baseUrl\":\""+URL+"\"}");
        AiMcpMarketToolMapper catalog=mock(AiMcpMarketToolMapper.class);when(catalog.selectById(2L)).thenReturn(entry);
        McpMarketServiceImpl service=new McpMarketServiceImpl(mock(AiMcpMarketMapper.class),catalog,mapper,new ObjectMapper());
        try(var dns=publicDns()){
            assertThrows(ServiceException.class,()->service.loadToolToLocal(2L));verify(mapper,never()).insert(any(AiTool.class));verify(catalog,never()).updateById(any(AiMcpMarketTool.class));
        }
    }

    @Test void 公网数字IP仍按既有策略允许保存不修改URL(){
        String url="https://93.184.216.34/mcp";AiTool request=tool(url);McpToolServiceImpl service=new McpToolServiceImpl(mapper,mock(BuiltinToolRegistry.class),mock(McpClientManager.class));
        assertDoesNotThrow(()->service.addTool(request));verify(mapper).insert(request);assertTrue(request.getConfigJson().contains(url));
    }

    private MockedStatic<InetAddress> publicDns() throws Exception {
        InetAddress address = InetAddress.getByAddress(new byte[] {93, (byte) 184, (byte) 216, 34});
        var dns = mockStatic(InetAddress.class);
        dns.when(() -> InetAddress.getAllByName(HOST)).thenReturn(new InetAddress[] {address});
        return dns;
    }

    private AiTool tool(String url){AiTool tool=new AiTool();tool.setId(1L);tool.setToolName("review_docs");tool.setToolType("REMOTE");tool.setStatus("0");tool.setConfigJson("{\"baseUrl\":\""+url+"\"}");return tool;}
}
