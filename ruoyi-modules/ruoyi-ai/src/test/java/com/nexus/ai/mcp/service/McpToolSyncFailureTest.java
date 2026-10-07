package com.nexus.ai.mcp.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nexus.ai.controller.McpToolController;
import com.nexus.ai.mcp.AiToolStatusContract;
import com.nexus.ai.tool.BuiltinToolProvider;
import com.nexus.ai.tool.BuiltinToolRegistry;
import com.nexus.common.ai.domain.entity.AiTool;
import com.nexus.common.ai.mapper.AiToolMapper;
import com.nexus.common.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 内置工具同步失败边界：查询、更新、新增或提供者属性异常均不能被接口误报成功。
 * 事务用真实 Spring 注解拦截器与内存替身验证；不连接数据库、不调用运行API、不启停服务。
 */
class McpToolSyncFailureTest {

    private static final String SAFE_MESSAGE = "内置工具同步失败，本次同步未完成，请联系管理员";
    /** 纯合成错误细节，专门用于证明异常和日志不会泄漏底层SQL、凭据或其他租户信息。 */
    private static final String RAW_DETAIL = "仅测试：SQL INSERT ai_tool；foreign-tenant-demo；credential-demo";

    private final AiToolMapper mapper = mock(AiToolMapper.class);
    private final BuiltinToolRegistry registry = mock(BuiltinToolRegistry.class);
    private final McpClientManager clientManager = mock(McpClientManager.class);
    private final McpToolServiceImpl service = new McpToolServiceImpl(mapper, registry, clientManager);
    private final Logger logger = (Logger) LoggerFactory.getLogger(McpToolServiceImpl.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void 捕获本类日志() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void 清理日志观察器() {
        logger.detachAppender(logs);
        logs.stop();
    }

    @ParameterizedTest(name = "同步{0}失败必须穿透到接口且脱敏")
    @ValueSource(strings = {"查询", "新增", "更新"})
    void 任一存储步骤失败不能返回接口成功(String step) {
        BuiltinToolProvider provider = provider("inventory_query");
        when(registry.getAllProviders()).thenReturn(List.of(provider));
        DuplicateKeyException original = new DuplicateKeyException(RAW_DETAIL, new IllegalStateException(RAW_DETAIL));
        switch (step) {
            case "查询" -> when(mapper.selectOne(any())).thenThrow(original);
            case "新增" -> when(mapper.insert(any(AiTool.class))).thenThrow(original);
            case "更新" -> {
                when(mapper.selectOne(any())).thenReturn(existingTool());
                when(mapper.updateById(any(AiTool.class))).thenThrow(original);
            }
            default -> throw new IllegalArgumentException("测试步骤未覆盖");
        }

        // 直接检查控制器调用链：不能运行到R.ok()，不启动HTTP或真实数据库。
        assertSafeFailure(() -> new McpToolController(service).sync());
        assertFailureLogsAreSafe();
        verifyNoInteractions(clientManager);
    }

    @Test
    void 提供者属性失败也须脱敏且不能在catch中再次读取失败属性() {
        BuiltinToolProvider broken = mock(BuiltinToolProvider.class);
        when(broken.getToolName()).thenThrow(new IllegalStateException(RAW_DETAIL));
        when(registry.getAllProviders()).thenReturn(List.of(broken));

        assertSafeFailure(service::syncBuiltinTools);
        assertFailureLogsAreSafe();
        verify(broken, times(1)).getToolName();
        verifyNoInteractions(mapper, clientManager);
    }

    @Test
    void 前项成功后项失败必须由外层单次事务回滚且停止后续提供者() {
        BuiltinToolProvider first = provider("inventory_query");
        BuiltinToolProvider second = provider("production_query");
        BuiltinToolProvider third = mock(BuiltinToolProvider.class);
        when(registry.getAllProviders()).thenReturn(List.of(first, second, third));

        // 替身的暂存区模拟未提交写入；回滚清空它，仅验证真实事务拦截器的调用结果。
        List<String> attempted = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        List<String> committed = new ArrayList<>();
        when(mapper.insert(any(AiTool.class))).thenAnswer(call -> {
            AiTool tool = call.getArgument(0);
            attempted.add(tool.getToolName());
            if ("production_query".equals(tool.getToolName())) {
                throw new DuplicateKeyException(RAW_DETAIL);
            }
            pending.add(tool.getToolName());
            return 1;
        });
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        doAnswer(call -> {
            committed.addAll(pending);
            pending.clear();
            return null;
        }).when(transactions).commit(status);
        doAnswer(call -> {
            pending.clear();
            return null;
        }).when(transactions).rollback(status);

        assertSafeFailure(() -> transactionalService(transactions).syncBuiltinTools());

        assertEquals(List.of("inventory_query", "production_query"), attempted);
        assertTrue(pending.isEmpty(), "前项成功的暂存写入也必须回滚");
        assertTrue(committed.isEmpty(), "失败同步不能提交部分结果");
        verify(transactions, times(1)).getTransaction(any(TransactionDefinition.class));
        verify(transactions, times(1)).rollback(status);
        verify(transactions, never()).commit(any());
        verifyNoInteractions(third, clientManager);
        assertFailureLogsAreSafe();
    }

    @Test
    void 同步保持单次REQUIRED事务并对异常整体回滚() throws Exception {
        var method = McpToolServiceImpl.class.getMethod("syncBuiltinTools");
        Transactional annotation = method.getAnnotation(Transactional.class);
        assertNotNull(annotation);
        assertEquals(Propagation.REQUIRED, annotation.propagation());
        assertArrayEquals(new Class<?>[]{Exception.class}, annotation.rollbackFor());
        var attribute = new AnnotationTransactionAttributeSource()
            .getTransactionAttribute(method, McpToolServiceImpl.class);
        assertNotNull(attribute);
        assertTrue(attribute.rollbackOn(new ServiceException(SAFE_MESSAGE)));
    }

    @Test
    void 全部成功仍提交且保留已有状态与新增默认停用() {
        BuiltinToolProvider existingProvider = provider("inventory_query");
        BuiltinToolProvider newProvider = provider("production_query");
        when(registry.getAllProviders()).thenReturn(List.of(existingProvider, newProvider));
        AiTool existing = existingTool();
        when(mapper.selectOne(any())).thenReturn(existing, null);
        when(mapper.updateById(any(AiTool.class))).thenReturn(1);
        when(mapper.insert(any(AiTool.class))).thenReturn(1);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactions.getTransaction(any(TransactionDefinition.class))).thenReturn(status);

        assertDoesNotThrow(() -> transactionalService(transactions).syncBuiltinTools());

        verify(transactions, times(1)).commit(status);
        verify(transactions, never()).rollback(any());
        verify(mapper).updateById(existing);
        assertEquals(AiToolStatusContract.ACTIVE, existing.getStatus(), "已有启停状态不能被同步覆盖");
        var inserted = org.mockito.ArgumentCaptor.forClass(AiTool.class);
        verify(mapper).insert(inserted.capture());
        assertEquals("production_query", inserted.getValue().getToolName());
        assertEquals(BuiltinToolRegistry.TYPE_BUILTIN, inserted.getValue().getToolType());
        assertEquals(AiToolStatusContract.DISABLED, inserted.getValue().getStatus());
        assertNull(inserted.getValue().getTenantId(), "不额外构造或迁移租户值");
        verifyNoInteractions(clientManager);
    }

    private McpToolServiceImpl transactionalService(PlatformTransactionManager transactions) {
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(interceptor);
        return (McpToolServiceImpl) factory.getProxy();
    }

    private void assertSafeFailure(org.junit.jupiter.api.function.Executable invocation) {
        ServiceException failure = assertThrowsExactly(ServiceException.class, invocation);
        assertEquals(SAFE_MESSAGE, failure.getMessage());
        assertNull(failure.getCause(), "不能将原SQL/凭据异常接入cause链");
        assertNull(failure.getDetailMessage());
        assertEquals(0, failure.getSuppressed().length);
        assertFalse(failure.toString().contains(RAW_DETAIL));
    }

    private void assertFailureLogsAreSafe() {
        assertTrue(logs.list.stream().anyMatch(event -> event.getFormattedMessage().contains("同步内置工具失败")));
        for (ILoggingEvent event : logs.list) {
            assertNull(event.getThrowableProxy(), "错误日志也不能保留原异常或cause链");
            assertFalse(event.getFormattedMessage().contains(RAW_DETAIL));
            assertFalse(event.getFormattedMessage().contains("同步完成:"), "失败同步不能打印成功汇总");
        }
    }

    private BuiltinToolProvider provider(String name) {
        BuiltinToolProvider provider = mock(BuiltinToolProvider.class);
        when(provider.getToolName()).thenReturn(name);
        when(provider.getDisplayName()).thenReturn("业务查询工具");
        when(provider.getDescription()).thenReturn("只读业务查询");
        return provider;
    }

    private AiTool existingTool() {
        AiTool tool = new AiTool();
        tool.setId(1L);
        tool.setToolName("inventory_query");
        tool.setToolType(BuiltinToolRegistry.TYPE_BUILTIN);
        tool.setDescription("旧描述");
        tool.setStatus(AiToolStatusContract.ACTIVE);
        return tool;
    }
}
