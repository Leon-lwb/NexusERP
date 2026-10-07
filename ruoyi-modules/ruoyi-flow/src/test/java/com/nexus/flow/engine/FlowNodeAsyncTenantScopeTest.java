package com.nexus.flow.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.flow.domain.entity.Flow;
import com.nexus.common.flow.mapper.FlowInstanceMapper;
import com.nexus.common.flow.mapper.FlowMapper;
import com.nexus.common.flow.mapper.FlowNodeRunMapper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.flow.model.FlowGraph;
import com.nexus.flow.model.FlowNodeDef;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.node.FlowNodeProvider;
import com.nexus.flow.node.FlowNodeRegistry;
import com.nexus.flow.state.FlowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 验证真实节点异步边界显式绑定请求身份，不能依赖线程创建时继承的上下文。 */
class FlowNodeAsyncTenantScopeTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private ApplicationContext previousContext;
    private GenericApplicationContext applicationContext;
    private ExecutorService executor;

    @BeforeEach
    void prepareEnvironment() {
        previousContext = SpringUtils.context();
        applicationContext = new GenericApplicationContext();
        applicationContext.getEnvironment().getPropertySources().addFirst(
            new MapPropertySource("节点租户配置", Map.of("tenant.enable", "true")));
        applicationContext.refresh();
        new SpringUtils().setApplicationContext(applicationContext);
        executor = Executors.newSingleThreadExecutor();
        AiRequestContext.clear();
    }

    @AfterEach
    void restoreEnvironment() {
        AiRequestContext.clear();
        executor.shutdownNow();
        new SpringUtils().setApplicationContext(previousContext);
        applicationContext.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"000000", "001234", "factory_A"})
    @DisplayName("真实异步节点以执行上下文绑定精确租户与用户，不继承错误身份")
    void actualAsyncBoundaryBindsIdentity(String tenantId) throws Exception {
        AiRequestContext.set("wrong_tenant", 999L, "旧用户", 99L, 88L, "旧模型");
        AiRequestContext.setKnowledgeId(77L);
        AiRequestContext.setLastTokenUsage(10, 20, 88L);
        FlowNodeProvider provider = mock(FlowNodeProvider.class);
        when(provider.execute(any(FlowExecutionContext.class))).thenAnswer(invocation -> {
            FlowExecutionContext ctx = invocation.getArgument(0);
            assertEquals(tenantId, ctx.getTenantId());
            assertEquals(tenantId, AiRequestContext.getTenantId(), "节点不能使用缺失或继承的旧租户");
            assertEquals(42L, AiRequestContext.getUserId(), "节点应使用本次流程用户");
            assertNull(AiRequestContext.getUserName(), "没有显式用户名来源时不能采信调度线程的旧用户名称");
            assertEquals(tenantId, TenantHelper.getDynamic(), "工具与配置查询的动态租户也必须一致");
            assertNull(AiRequestContext.getSessionId(), "旧会话不能进入流程节点");
            assertNull(AiRequestContext.getKnowledgeId(), "旧知识库不能串入节点");
            assertNull(AiRequestContext.getLastInputTokens(), "上次调用用量不能冒充当前节点用量");
            return MAPPER.createObjectNode().put("tenantId", AiRequestContext.getTenantId());
        });
        Flow flow = new Flow();
        flow.setId(101L);
        flow.setConfigJson("{\"nodes\":[{\"id\":\"source_lookup\",\"type\":\"erp_tool\",\"data\":{\"config\":{}}}],\"edges\":[]}");
        FlowGraph graph = FlowGraph.parse(flow.getConfigJson());
        FlowNodeDef node = graph.getNodes().get("source_lookup");
        FlowEngineImpl engine = new FlowEngineImpl(mock(FlowNodeRegistry.class), new FlowStopRegistry(),
            mock(FlowInstanceMapper.class), mock(FlowNodeRunMapper.class), mock(FlowMapper.class),
            new AiProperties(), Runnable::run, executor);
        Method method = FlowEngineImpl.class.getDeclaredMethod("runNodeWithRetry", FlowNodeDef.class,
            FlowNodeProvider.class, FlowState.class, Long.class, Long.class, Flow.class, FlowGraph.class,
            AtomicInteger.class, String.class);
        method.setAccessible(true);
        JsonNode output = (JsonNode) method.invoke(engine, node, provider, new FlowState(null),
            102L, 42L, flow, graph, new AtomicInteger(), tenantId);
        assertEquals(tenantId, output.get("tenantId").asText());
        assertEquals("wrong_tenant", AiRequestContext.getTenantId(), "节点不能破坏调度线程的既有上下文");
        assertEquals(999L, AiRequestContext.getUserId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"bad tenant", "000000'", "123456789012345678901"})
    @DisplayName("非法节点身份在触及工具前拒绝并清理继承身份")
    void invalidIdentityIsRejectedAndCleaned(String tenantId) {
        AiRequestContext.set("wrong_tenant", 999L);
        FlowExecutionContext ctx = new FlowExecutionContext();
        ctx.setTenantId(tenantId);
        FlowNodeProvider provider = mock(FlowNodeProvider.class);
        assertThrows(IllegalArgumentException.class, () -> engine().executeNodeInRequestScope(provider, ctx, null));
        verifyNoInteractions(provider);
        assertNull(AiRequestContext.getTenantId(), "拒绝执行也不能留下继承身份");
        assertNull(AiRequestContext.getUserId());
    }

    @Test
    @DisplayName("成功节点清理全部AI字段并恢复外层动态租户")
    void successCleansAllAiFieldsAndRestoresDynamicTenant() throws Exception {
        runCleanupCase(false);
    }

    @Test
    @DisplayName("异常节点同样清理全部AI字段并恢复外层动态租户")
    void failureCleansAllAiFieldsAndRestoresDynamicTenant() throws Exception {
        runCleanupCase(true);
    }

    private void runCleanupCase(boolean fail) throws Exception {
        TenantHelper.setDynamic("outer_tenant");
        try {
            FlowExecutionContext ctx = new FlowExecutionContext();
            ctx.setTenantId("001234");
            ctx.setUserId(42L);
            FlowNodeProvider provider = mock(FlowNodeProvider.class);
            when(provider.execute(ctx)).thenAnswer(invocation -> {
                assertEquals("001234", TenantHelper.getDynamic());
                AiRequestContext.setKnowledgeId(123L);
                AiRequestContext.setLastTokenUsage(5, 6, 789L);
                if (fail) throw new IllegalStateException("节点失败哨兵");
                return MAPPER.createObjectNode().put("state", "read");
            });
            if (fail) {
                CompletionException failure = assertThrows(CompletionException.class,
                    () -> engine().executeNodeInRequestScope(provider, ctx, "业务用户"));
                assertEquals("节点失败哨兵", failure.getCause().getMessage());
            } else {
                assertEquals("read", engine().executeNodeInRequestScope(provider, ctx, "业务用户").get("state").asText());
            }
            assertNull(AiRequestContext.getTenantId());
            assertNull(AiRequestContext.getUserId());
            assertNull(AiRequestContext.getUserName());
            assertNull(AiRequestContext.getSessionId());
            assertNull(AiRequestContext.getModelId());
            assertNull(AiRequestContext.getModelName());
            assertNull(AiRequestContext.getKnowledgeId());
            assertNull(AiRequestContext.getLastInputTokens());
            assertNull(AiRequestContext.getLastOutputTokens());
            assertNull(AiRequestContext.getLastModelId());
            assertEquals("outer_tenant", TenantHelper.getDynamic());
        } finally {
            TenantHelper.clearDynamic();
        }
    }

    private FlowEngineImpl engine() {
        return new FlowEngineImpl(mock(FlowNodeRegistry.class), new FlowStopRegistry(),
            mock(FlowInstanceMapper.class), mock(FlowNodeRunMapper.class), mock(FlowMapper.class),
            new AiProperties(), Runnable::run, executor);
    }
}
