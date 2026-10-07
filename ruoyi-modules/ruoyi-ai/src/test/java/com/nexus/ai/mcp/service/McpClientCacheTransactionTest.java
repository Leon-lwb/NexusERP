package com.nexus.ai.mcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.tool.BuiltinToolRegistry;
import com.nexus.common.ai.domain.entity.AiTool;
import com.nexus.common.ai.mapper.AiToolMapper;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 提交时序及配置版本回归，所有客户端/传输/Mapper均为替身，不启停服务。 */
class McpClientCacheTransactionTest {
    private final AiToolMapper mapper=mock(AiToolMapper.class);
    private final McpClientManager manager=new McpClientManager(mapper,new ObjectMapper());
    private final McpToolServiceImpl service=new McpToolServiceImpl(mapper,mock(BuiltinToolRegistry.class),manager);

    @AfterEach void 清理模拟事务上下文(){
        if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test void 提交前不得失效提交后必须失效(){
        McpClientManager mockManager=mock(McpClientManager.class);
        McpToolServiceImpl target=new McpToolServiceImpl(mapper,mock(BuiltinToolRegistry.class),mockManager);
        when(mapper.selectById(1L)).thenReturn(tool("A"));when(mapper.updateById(any(AiTool.class))).thenReturn(1);
        begin();target.updateTool(tool("B"));
        verifyNoInteractions(mockManager);
        var callbacks=TransactionSynchronizationManager.getSynchronizations();assertFalse(callbacks.isEmpty());
        callbacks.forEach(TransactionSynchronization::afterCommit);
        verify(mockManager).refreshClient(1L);
    }

    @Test void 回滚后不得失效仍可用的旧配置客户端(){
        McpClientManager mockManager=mock(McpClientManager.class);
        McpToolServiceImpl target=new McpToolServiceImpl(mapper,mock(BuiltinToolRegistry.class),mockManager);
        when(mapper.selectById(1L)).thenReturn(tool("A"));when(mapper.updateById(any(AiTool.class))).thenReturn(1);
        begin();target.toggleToolStatus(1L,"1");
        TransactionSynchronizationManager.getSynchronizations().forEach(callback->callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(mockManager);
    }

    @Test void 删除也只能在提交后失效(){
        McpClientManager mockManager=mock(McpClientManager.class);
        McpToolServiceImpl target=new McpToolServiceImpl(mapper,mock(BuiltinToolRegistry.class),mockManager);
        when(mapper.selectById(1L)).thenReturn(tool("A"));when(mapper.deleteBatchIds(any())).thenReturn(1);
        begin();assertEquals(1,target.deleteTools(List.of(1L)));verifyNoInteractions(mockManager);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(mockManager).refreshClient(1L);
    }

    @Test void 缓存命中也必须核对完整配置指纹(){
        AtomicReference<AiTool> visible=new AtomicReference<>(tool("A"));when(mapper.selectById(1L)).thenAnswer(call->visible.get());
        DefaultMcpClient first=mock(DefaultMcpClient.class),second=mock(DefaultMcpClient.class);
        try(var transport=transport();var clients=clients(first,second)){
            assertSame(first,manager.getOrCreateClient(1L));visible.set(tool("B"));
            assertSame(second,manager.getOrCreateClient(1L),"状态相同但认证配置已变，不能返回旧客户端");
            verify(first).close();
        }catch(Exception e){throw new AssertionError(e);}
    }

    @Test void 创建期间配置变化不能发布旧版本(){
        AtomicReference<AiTool> visible=new AtomicReference<>(tool("A"));when(mapper.selectById(1L)).thenAnswer(call->visible.get());
        DefaultMcpClient candidate=mock(DefaultMcpClient.class);
        try(var transport=transport();var clients=mockConstruction(DefaultMcpClient.Builder.class,withSettings().defaultAnswer(RETURNS_SELF),(builder,context)->when(builder.build()).thenAnswer(call->{visible.set(tool("B"));return candidate;}))){
            assertNull(manager.getOrCreateClient(1L));assertEquals(0,manager.getActiveClientCount());verify(candidate).close();
        }catch(Exception e){throw new AssertionError(e);}
    }

    @Test void discovery期间配置变化不能发布旧版本或返回发现成功(){
        AtomicReference<AiTool> visible=new AtomicReference<>(tool("A"));when(mapper.selectById(1L)).thenAnswer(call->visible.get());
        DefaultMcpClient candidate=mock(DefaultMcpClient.class);when(candidate.listTools()).thenAnswer(call->{visible.set(tool("B"));return List.of();});
        try(var transport=transport();var clients=clients(candidate)){
            assertNull(manager.discoverToolNames(1L));assertEquals(0,manager.getActiveClientCount());verify(candidate).close();
        }catch(Exception e){throw new AssertionError(e);}
    }

    @Test void 创建期间已发生显式失效不能重新发布候选客户端(){
        when(mapper.selectById(1L)).thenReturn(tool("A"));DefaultMcpClient candidate=mock(DefaultMcpClient.class);
        try(var transport=transport();var clients=mockConstruction(DefaultMcpClient.Builder.class,withSettings().defaultAnswer(RETURNS_SELF),(builder,context)->when(builder.build()).thenAnswer(call->{manager.refreshClient(1L);return candidate;}))){
            assertNull(manager.getOrCreateClient(1L));assertEquals(0,manager.getActiveClientCount());verify(candidate).close();
        }catch(Exception e){throw new AssertionError(e);}
    }

    @Test void 没有事务时保持直接失效兼容(){
        McpClientManager mockManager=mock(McpClientManager.class);
        McpToolServiceImpl target=new McpToolServiceImpl(mapper,mock(BuiltinToolRegistry.class),mockManager);
        when(mapper.selectById(1L)).thenReturn(tool("A"));when(mapper.updateById(any(AiTool.class))).thenReturn(1);
        target.toggleToolStatus(1L,"1");verify(mockManager).refreshClient(1L);
    }

    @Test void 配置摘要必须区分空值与空字符串() {
        AiTool absent=tool("A"),blank=tool("A");absent.setConfigJson(null);blank.setConfigJson("");
        String left=org.springframework.test.util.ReflectionTestUtils.invokeMethod(manager,"configurationFingerprint",absent);
        String right=org.springframework.test.util.ReflectionTestUtils.invokeMethod(manager,"configurationFingerprint",blank);
        assertNotEquals(left,right,"完整配置版本不能把null与空字符串合并");
    }

    @org.junit.jupiter.params.ParameterizedTest(name="失效与{0}发布并发时不能留下旧候选")
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void 创建和发现共享失效锁且正在等待的提交回调可阻止发布(boolean discovery) throws Exception {
        when(mapper.selectById(1L)).thenReturn(tool("A"));
        DefaultMcpClient candidate=mock(DefaultMcpClient.class);
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        if(discovery)when(candidate.listTools()).thenAnswer(call->{entered.countDown();assertTrue(release.await(10,java.util.concurrent.TimeUnit.SECONDS));return List.of();});
        try {
            var creating=pool.submit(()->{
                try(var transport=transport();var clients=mockConstruction(DefaultMcpClient.Builder.class,withSettings().defaultAnswer(RETURNS_SELF),
                    (builder,context)->when(builder.build()).thenAnswer(call->{if(!discovery){entered.countDown();assertTrue(release.await(10,java.util.concurrent.TimeUnit.SECONDS));}return candidate;}))){
                    return discovery?manager.discoverToolNames(1L):manager.getOrCreateClient(1L);
                }
            });
            assertTrue(entered.await(10,java.util.concurrent.TimeUnit.SECONDS));
            var refreshing=pool.submit(()->manager.refreshClient(1L));
            @SuppressWarnings("unchecked") var generations=(java.util.Map<Long,java.util.concurrent.atomic.AtomicLong>)org.springframework.test.util.ReflectionTestUtils.getField(manager,"clientGenerations");
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while(generations.get(1L).get()==0 && System.nanoTime()<deadline)Thread.sleep(1);
            assertTrue(generations.get(1L).get()>0,"提交后失效必须先标记代数，再等待创建/发现锁");
            release.countDown();
            assertNull(creating.get(10,java.util.concurrent.TimeUnit.SECONDS));refreshing.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(0,manager.getActiveClientCount());verify(candidate).close();
        } finally {release.countDown();pool.shutdownNow();assertTrue(pool.awaitTermination(10,java.util.concurrent.TimeUnit.SECONDS));}
    }

    private void begin(){TransactionSynchronizationManager.initSynchronization();TransactionSynchronizationManager.setActualTransactionActive(true);}
    private AiTool tool(String version){AiTool tool=new AiTool();tool.setId(1L);tool.setToolType("REMOTE");tool.setStatus("0");tool.setToolName("readonly_docs");tool.setConfigJson("{\"baseUrl\":\"https://93.184.216.34/mcp\",\"headers\":{\"Authorization\":\"REVIEW_ONLY_"+version+"\"}}");return tool;}
    private MockedStatic<StreamableHttpMcpTransport> transport(){var transport=mockStatic(StreamableHttpMcpTransport.class);var builder=mock(StreamableHttpMcpTransport.Builder.class,RETURNS_SELF);transport.when(StreamableHttpMcpTransport::builder).thenReturn(builder);return transport;}
    private MockedConstruction<DefaultMcpClient.Builder> clients(DefaultMcpClient... clients){var next=new java.util.concurrent.atomic.AtomicInteger();return mockConstruction(DefaultMcpClient.Builder.class,withSettings().defaultAnswer(RETURNS_SELF),(builder,context)->when(builder.build()).thenReturn(clients[Math.min(next.getAndIncrement(),clients.length-1)]));}
}
