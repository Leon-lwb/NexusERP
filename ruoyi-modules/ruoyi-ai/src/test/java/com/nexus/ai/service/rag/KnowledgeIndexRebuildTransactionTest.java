package com.nexus.ai.service.rag;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.nexus.common.ai.domain.bo.AiKnowledgeBo;
import com.nexus.common.ai.domain.entity.AiKnowledge;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.mapper.AiKnowledgeMapper;
import com.nexus.common.ai.mapper.AiModelMapper;
import com.nexus.common.ai.service.IAiKnowledgeService;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.tenant.handle.PlusTenantLineHandler;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.tenant.properties.TenantProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * R2/R7 中文回归：真实 Spring 事务代理与提交回调，JDBC 接口由替身实现，绝不连接数据库。
 * <p>内存状态在 Connection.commit 才可见，rollback 丢弃；不是手动触发事务同步来冒充提交。</p>
 */
class KnowledgeIndexRebuildTransactionTest {
    private static final long KNOWLEDGE_ID = 1L;
    private final AiKnowledgeMapper knowledgeMapper = mock(AiKnowledgeMapper.class);
    private final AiKnowledgeChunkMapper chunkMapper = mock(AiKnowledgeChunkMapper.class);
    private final AiModelMapper modelMapper = mock(AiModelMapper.class);
    private final IAiKnowledgeService knowledgeService = mock(IAiKnowledgeService.class);
    private final VectorStoreService vectorService = mock(VectorStoreService.class);
    private final Executor executor = mock(Executor.class);
    private final List<Runnable> submitted = new ArrayList<>();
    private final MemoryTransactions store = new MemoryTransactions();
    private AnnotationConfigApplicationContext context;
    private ApplicationContext previousContext;
    private KnowledgeIndexRebuildService service;
    private boolean rejectSubmission;
    private boolean rejectConfiguration;

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionConfiguration {
    }

    @BeforeEach
    void bindRealTransactionInfrastructure() throws Exception {
        previousContext = SpringUtils.context();
        when(knowledgeMapper.selectById(KNOWLEDGE_ID)).thenAnswer(invocation -> copy(store.current()));
        when(chunkMapper.selectCount(any())).thenReturn(7L);
        when(chunkMapper.selectList(any())).thenReturn(List.of());
        when(vectorService.resolveEmbeddingDimension(anyString())).thenReturn(768);
        when(knowledgeMapper.update(any(AiKnowledge.class), any())).thenAnswer(invocation -> {
            AiKnowledge patch = invocation.getArgument(0);
            AiKnowledge current = store.current();
            if (KnowledgeIndexRebuildService.STATUS_REBUILDING.equals(patch.getVectorIndexStatus())) {
                if (!store.claimAllowed || KnowledgeIndexRebuildService.STATUS_REBUILDING.equals(current.getVectorIndexStatus())) return 0;
            } else if (!KnowledgeIndexRebuildService.STATUS_REBUILDING.equals(current.getVectorIndexStatus())) {
                return 0;
            }
            store.writeState(patch);
            return 1;
        });
        when(knowledgeMapper.updateById(any(AiKnowledge.class))).thenAnswer(invocation -> {
            store.writeState(invocation.getArgument(0));
            return 1;
        });
        when(knowledgeService.updateByBo(any(AiKnowledgeBo.class))).thenAnswer(invocation -> {
            if (rejectConfiguration) throw new ServiceException("配置校验拒绝");
            AiKnowledgeBo bo = invocation.getArgument(0);
            store.events.add("configure");
            if (bo.getEmbeddingModel() != null) store.current().setEmbeddingModel(bo.getEmbeddingModel());
            if (bo.getVectorStore() != null) store.current().setVectorStore(bo.getVectorStore());
            return 1;
        });
        doAnswer(invocation -> {
            store.events.add("submit");
            if (rejectSubmission) throw new RejectedExecutionException("R7_EXECUTOR_MESSAGE_SENTINEL", new IllegalStateException("R7_EXECUTOR_CAUSE_SENTINEL"));
            submitted.add(invocation.getArgument(0));
            return null;
        }).when(executor).execute(any(Runnable.class));

        // 模拟实际公共服务的 REQUIRED 事务；基线外层缺事务时，它确实会独立提交配置。
        RuleBasedTransactionAttribute attribute = new RuleBasedTransactionAttribute();
        attribute.setRollbackRules(List.of(new RollbackRuleAttribute(Exception.class)));
        NameMatchTransactionAttributeSource attributes = new NameMatchTransactionAttributeSource();
        attributes.addTransactionalMethod("updateByBo", attribute);
        ProxyFactory inner = new ProxyFactory(knowledgeService);
        inner.addAdvice(new TransactionInterceptor(store.manager, attributes));

        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("RAG只读测试配置", Map.of("tenant.enable", "true")));
        context.registerBean(AiKnowledgeMapper.class, () -> knowledgeMapper);
        context.registerBean(AiKnowledgeChunkMapper.class, () -> chunkMapper);
        context.registerBean(AiModelMapper.class, () -> modelMapper);
        context.registerBean(IAiKnowledgeService.class, () -> (IAiKnowledgeService) inner.getProxy());
        context.registerBean(VectorStoreService.class, () -> vectorService);
        context.registerBean("aiIndexRebuildExecutor", Executor.class, () -> executor);
        context.registerBean(PlatformTransactionManager.class, () -> store.manager);
        context.register(TransactionConfiguration.class, KnowledgeIndexRebuildService.class);
        context.refresh();
        new SpringUtils().setApplicationContext(context);
        service = context.getBean(KnowledgeIndexRebuildService.class);
        // 所有既有R2/R7事务用例显式绑定真实000000租户，不依赖缺失身份的fallback。
        TenantHelper.setDynamic("000000");
    }

    @AfterEach
    void restoreThreadAndSpringState() {
        TenantHelper.clearDynamic();
        new SpringUtils().setApplicationContext(previousContext);
        context.close();
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive(), "测试不能遗留事务同步状态");
        assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty(), "测试不能遗留连接资源");
    }

    @Test
    @DisplayName("已重建时拒绝编辑，已提交模型和存储配置完全不变")
    void activeRebuildCannotCommitRejectedConfiguration() {
        store.committed.setVectorIndexStatus(KnowledgeIndexRebuildService.STATUS_REBUILDING);
        assertThrows(ServiceException.class, () -> service.updateConfiguration(edit(true)));
        assertEquals("模型A", store.committed.getEmbeddingModel(), "失败请求不能先提交模型B");
        assertEquals("milvus", store.committed.getVectorStore());
        verify(knowledgeService, never()).updateByBo(any());
        assertTrue(submitted.isEmpty());
    }

    @Test
    @DisplayName("原子抢占失败在配置写入前拒绝，不依赖先读状态防竞态")
    void losingClaimCannotCommitConfiguration() {
        store.claimAllowed = false;
        assertThrows(ServiceException.class, () -> service.updateConfiguration(edit(true)));
        assertEquals("模型A", store.committed.getEmbeddingModel());
        verify(knowledgeService, never()).updateByBo(any());
        assertTrue(submitted.isEmpty());
    }

    @Test
    @DisplayName("真实事务拦截把配置与REBUILDING一次提交，任务只在提交后入队")
    void actualInterceptorCommitsBeforeSubmitting() {
        store.beforeCommit = () -> assertTrue(submitted.isEmpty(), "简单加事务但提前提交任务仍必须失败");
        assertEquals(1, service.updateConfiguration(edit(true)));
        assertTrue(AopUtils.isAopProxy(service), "必须经过真实事务代理，不只检测注解文本");
        assertEquals(0, store.nonTransactionalStateWrites, "配置和状态必须共享事务边界");
        assertEquals(1, store.commits);
        assertEquals("模型B", store.committed.getEmbeddingModel());
        assertEquals(KnowledgeIndexRebuildService.STATUS_REBUILDING, store.committed.getVectorIndexStatus());
        assertEquals(1, submitted.size());
        assertTrue(store.events.indexOf("commit") < store.events.indexOf("submit"), "提交必须先于任务入队");
    }

    @Test
    @DisplayName("真实工作线程在提交后读到模型B，不会拿旧配置提前重建")
    void realWorkerStartsWithCommittedConfiguration() {
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            CompletableFuture.runAsync(task).join();
            return null;
        }).when(executor).execute(any(Runnable.class));
        service.updateConfiguration(edit(true));
        verify(vectorService).rebuildKnowledge(eq(KNOWLEDGE_ID), eq("模型B"), eq(768), anyList());
        assertEquals("模型B", store.committed.getEmbeddingModel());
        assertEquals(KnowledgeIndexRebuildService.STATUS_READY, store.committed.getVectorIndexStatus());
        assertEquals(1, store.commits);
    }

    @Test
    @DisplayName("绕过事务代理的裸调用在配置和状态写入前拒绝")
    void bypassingTransactionProxyFailsBeforeWrites() throws Exception {
        KnowledgeIndexRebuildService target = (KnowledgeIndexRebuildService) ((Advised) service).getTargetSource().getTarget();
        assertThrows(IllegalStateException.class, () -> target.updateConfiguration(edit(true)));
        assertEquals("模型A", store.committed.getEmbeddingModel());
        assertEquals(KnowledgeIndexRebuildService.STATUS_READY, store.committed.getVectorIndexStatus());
        assertEquals(0, store.nonTransactionalStateWrites);
        assertTrue(submitted.isEmpty());
    }

    @Test
    @DisplayName("配置更新零行回滚抢占并释放预约，不能创建空成功重建")
    void zeroRowConfigurationUpdateRollsBackReservation() {
        when(knowledgeService.updateByBo(any())).thenReturn(0);
        assertThrows(ServiceException.class, () -> service.updateConfiguration(edit(true)));
        assertEquals("模型A", store.committed.getEmbeddingModel());
        assertEquals(KnowledgeIndexRebuildService.STATUS_READY, store.committed.getVectorIndexStatus());
        assertTrue(submitted.isEmpty());
        service.requestRebuild(KNOWLEDGE_ID);
        assertEquals(1, submitted.size());
    }

    @Test
    @DisplayName("配置校验失败回滚已抢占状态，且不会注册可执行任务")
    void validationFailureRollsBackClaim() {
        rejectConfiguration = true;
        assertThrows(ServiceException.class, () -> service.updateConfiguration(edit(true)));
        assertEquals("模型A", store.committed.getEmbeddingModel());
        assertEquals(KnowledgeIndexRebuildService.STATUS_READY, store.committed.getVectorIndexStatus());
        assertTrue(submitted.isEmpty());
        rejectConfiguration = false;
        service.requestRebuild(KNOWLEDGE_ID);
        assertEquals(1, submitted.size(), "回滚必须释放本机预约，允许后续显式重建");
    }

    @Test
    @DisplayName("外层事务回滚不启动任务，并撤销配置状态与本机预约")
    void outerRollbackNeverSubmitsWork() {
        new TransactionTemplate(store.manager).executeWithoutResult(status -> {
            service.updateConfiguration(edit(true));
            assertTrue(submitted.isEmpty(), "外层尚未提交，不能提前启动任务");
            status.setRollbackOnly();
        });
        assertEquals("模型A", store.committed.getEmbeddingModel());
        assertEquals(KnowledgeIndexRebuildService.STATUS_READY, store.committed.getVectorIndexStatus());
        assertTrue(submitted.isEmpty());
        service.requestRebuild(KNOWLEDGE_ID);
        assertEquals(1, submitted.size());
    }

    @Test
    @DisplayName("提交失败不入队，完成回调释放预约而不是遗留活跃任务")
    void commitFailureDoesNotSubmitOrLeakReservation() {
        store.failNextCommit = true;
        assertThrows(RuntimeException.class, () -> service.updateConfiguration(edit(true)));
        assertTrue(submitted.isEmpty());
        assertEquals("模型A", store.committed.getEmbeddingModel());
        service.requestRebuild(KNOWLEDGE_ID);
        assertEquals(1, submitted.size());
    }

    @Test
    @DisplayName("提交后线程池拒绝在独立新事务持久化FAILED")
    void rejectionAfterCommitUsesNewTransaction() {
        rejectSubmission = true;
        assertEquals(1, service.updateConfiguration(edit(true)));
        assertEquals(KnowledgeIndexRebuildService.STATUS_FAILED, store.committed.getVectorIndexStatus());
        assertEquals(2, store.commits, "失败状态不能写进已提交但尚未解绑的原连接");
        assertEquals(0, store.nonTransactionalStateWrites);
        rejectSubmission = false;
        service.requestRebuild(KNOWLEDGE_ID);
        assertEquals(1, submitted.size());
    }

    @Test
    @DisplayName("配置不变但rebuildIndex=true仍真实重建且先提交后执行")
    void unchangedConfigurationWithExplicitFlagStillRebuilds() {
        AiKnowledgeBo bo = edit(true);
        bo.setEmbeddingModel("模型A");
        service.updateConfiguration(bo);
        assertEquals(1, submitted.size());
        submitted.get(0).run();
        verify(vectorService).rebuildKnowledge(eq(KNOWLEDGE_ID), eq("模型A"), eq(768), anyList());
        assertEquals(KnowledgeIndexRebuildService.STATUS_READY, store.committed.getVectorIndexStatus());
    }

    @Test
    @DisplayName("提交后保留000000文本身份，仅当前七片段重建，不接入历史27条tenant0")
    void afterCommitKeepsExactTenantAndHistoricalPartition() {
        List<AiKnowledgeChunk> current = chunks("000000", 7);
        List<AiKnowledgeChunk> historical = chunks("0", 27);
        when(chunkMapper.selectList(any())).thenAnswer(invocation -> {
            String tenant = TenantHelper.getTenantId();
            TenantProperties properties = new TenantProperties();
            properties.setEnable(true);
            properties.setExcludes(List.of());
            String sql = new TenantLineInnerInterceptor(new PlusTenantLineHandler(properties))
                .parserSingle("SELECT id FROM ai_knowledge_chunk WHERE knowledge_id = 1", null);
            assertEquals("000000", tenant, "异步租户不能取整为0或丢失");
            assertTrue(sql.contains("tenant_id = '000000'"), "真实租户SQL插件保持精确文本身份");
            // 替身只模拟持久层分区，历史行不是本轮写入目标。
            return "000000".equals(tenant) ? current : historical;
        });
        TenantHelper.setDynamic("000000");
        service.requestRebuild(KNOWLEDGE_ID);
        TenantHelper.clearDynamic();
        CompletableFuture.runAsync(submitted.get(0)).join();
        verify(vectorService).rebuildKnowledge(eq(KNOWLEDGE_ID), eq("模型A"), eq(768), eq(current));
        assertEquals(27, historical.size());
        assertTrue(historical.stream().allMatch(chunk -> "0".equals(chunk.getTenantId())));
    }

    @Test
    @DisplayName("失败日志不保留异常消息或cause链sentinel，FAILED错误仍脱敏")
    void failureLogsNeverExposeThrowableSentinels() {
        store.committed.setVectorIndexStatus(KnowledgeIndexRebuildService.STATUS_REBUILDING);
        AiModel model = new AiModel();
        model.setApiKey("R7_CONFIG_SENTINEL");
        when(modelMapper.selectOne(any())).thenReturn(model);
        doThrow(new IllegalStateException("R7_CONFIG_SENTINEL R7_MESSAGE_SENTINEL", new RuntimeException("R7_CAUSE_SENTINEL")))
            .when(vectorService).rebuildKnowledge(anyLong(), anyString(), anyInt(), anyList());
        assertSafeLogs(() -> service.rebuild(KNOWLEDGE_ID), "R7_CONFIG_SENTINEL", "R7_MESSAGE_SENTINEL", "R7_CAUSE_SENTINEL");
        assertEquals(KnowledgeIndexRebuildService.STATUS_FAILED, store.committed.getVectorIndexStatus());
        assertFalse(store.committed.getVectorIndexError().contains("R7_CONFIG_SENTINEL"));
    }

    @Test
    @DisplayName("失败状态写入异常也不能把数据库异常原文带入日志")
    void failurePersistenceLogsAreAlsoSafe() {
        store.committed.setVectorIndexStatus(KnowledgeIndexRebuildService.STATUS_REBUILDING);
        doThrow(new IllegalStateException("重建失败"))
            .when(vectorService).rebuildKnowledge(anyLong(), anyString(), anyInt(), anyList());
        when(knowledgeMapper.update(any(AiKnowledge.class), any())).thenThrow(new RuntimeException("R7_PERSISTENCE_SENTINEL"));
        assertSafeLogs(() -> service.rebuild(KNOWLEDGE_ID), "R7_PERSISTENCE_SENTINEL");
    }

    @Test
    @DisplayName("任务提交失败日志不输出线程池拒绝异常的消息和cause")
    void submissionFailureLogsAreSafe() {
        rejectSubmission = true;
        assertSafeLogs(() -> service.updateConfiguration(edit(true)), "R7_EXECUTOR_MESSAGE_SENTINEL", "R7_EXECUTOR_CAUSE_SENTINEL");
    }

    static Stream<Arguments> invalidTenantRequests() {
        return Stream.of("确认重建编辑", "普通配置编辑", "无片段配置编辑", "独立重建端点")
            .flatMap(operation -> Stream.of((String) null, "", " ", " 000000", "000000 ",
                "000000'", "tenant/path", "工厂", "123456789012345678901", "000000\n")
                .map(tenant -> Arguments.of(operation, tenant)));
    }

    @ParameterizedTest(name = "{0} 拒绝非法租户 [{1}]")
    @MethodSource("invalidTenantRequests")
    @DisplayName("缺失或非法身份在触及配置/状态前拒绝，之后合法请求仍可重建")
    void invalidCaptureRefusesEveryEntryAndAllowsNextValidRequest(String operation, String tenantId) throws Exception {
        try (MockedStatic<TenantHelper> tenants = mockStatic(TenantHelper.class, CALLS_REAL_METHODS)) {
            tenants.when(TenantHelper::getTenantId).thenReturn(tenantId);
            assertTenantFailureWithoutWrites(operation);
            tenants.when(TenantHelper::getTenantId).thenReturn("000000");
            assertNextValidRebuildRuns();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"确认重建编辑", "普通配置编辑", "无片段配置编辑", "独立重建端点"})
    @DisplayName("获取租户异常必须拒绝且不输出原文，也不留下预约或异步FAILED写入")
    void captureExceptionRefusesWithoutFallbackAndAllowsNextValidRequest(String operation) throws Exception {
        try (MockedStatic<TenantHelper> tenants = mockStatic(TenantHelper.class, CALLS_REAL_METHODS)) {
            tenants.when(TenantHelper::getTenantId).thenThrow(new IllegalStateException("租户获取异常原文哨兵"));
            ServiceException failure = assertTenantFailureWithoutWrites(operation);
            assertFalse(failure.getMessage().contains("租户获取异常原文哨兵"));
            assertNull(failure.getCause(), "捕获异常不能把凭据或异常原文包装回API");
            tenants.when(TenantHelper::getTenantId).thenReturn("000000");
            assertNextValidRebuildRuns();
        }
    }

    @Test
    @DisplayName("捕获校验先于runningRebuilds.add，不能先预约再获知租户")
    void tenantCapturePrecedesReservation() throws Exception {
        Set<Long> reservations = reservations();
        AtomicInteger captures = new AtomicInteger();
        try (MockedStatic<TenantHelper> tenants = mockStatic(TenantHelper.class, CALLS_REAL_METHODS)) {
            tenants.when(TenantHelper::getTenantId).thenAnswer(invocation -> {
                assertFalse(reservations.contains(KNOWLEDGE_ID), "捕获租户时必须尚未预约");
                captures.incrementAndGet();
                return "000021";
            });
            service.updateConfiguration(edit(true));
            assertEquals(1, captures.get(), "只捕获一次明确身份，不在提交后重新读取漂移上下文");
            assertEquals(1, submitted.size());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"000000", "000021", "factory-A_2", "abcdefghijklmnopqrst"})
    @DisplayName("原租户String提交后无损绑定，不trim、不转Long、不补零")
    void validCapturedStringIsBoundExactly(String tenantId) {
        TenantHelper.setDynamic(tenantId);
        doAnswer(invocation -> {
            assertEquals(tenantId, TenantHelper.getDynamic());
            assertEquals(tenantId, TenantHelper.getTenantId());
            return null;
        }).when(vectorService).rebuildKnowledge(anyLong(), anyString(), anyInt(), anyList());
        service.requestRebuild(KNOWLEDGE_ID);
        TenantHelper.clearDynamic();
        CompletableFuture.runAsync(submitted.get(0)).join();
        verify(vectorService).rebuildKnowledge(eq(KNOWLEDGE_ID), eq("模型A"), eq(768), anyList());
    }

    @Test
    @DisplayName("提交后拒绝的FAILED新事务使用已捕获身份，不采信提交前漂移租户")
    void rejectedTaskWritesFailedOnlyInsideCapturedTenant() {
        rejectSubmission = true;
        TenantHelper.setDynamic("000021");
        new TransactionTemplate(store.manager).executeWithoutResult(status -> {
            service.requestRebuild(KNOWLEDGE_ID);
            TenantHelper.setDynamic("another-tenant");
        });
        assertEquals("000021", store.lastFailedTenant);
        assertEquals(KnowledgeIndexRebuildService.STATUS_FAILED, store.committed.getVectorIndexStatus());
        assertEquals("another-tenant", TenantHelper.getDynamic(), "失败回写不能改变提交线程外层租户");
        assertEquals(2, store.commits, "保留R2/R7的提交后FAILED独立事务边界");
    }

    private ServiceException assertTenantFailureWithoutWrites(String operation) throws Exception {
        // 即使线程池会拒绝，也必须在入队前因身份拒绝，不能走无租户FAILED回写。
        rejectSubmission = true;
        ServiceException failure = assertThrows(ServiceException.class, () -> invokeRequest(operation));
        assertEquals("模型A", store.committed.getEmbeddingModel());
        assertEquals("milvus", store.committed.getVectorStore());
        assertEquals(KnowledgeIndexRebuildService.STATUS_READY, store.committed.getVectorIndexStatus());
        assertNull(store.committed.getVectorIndexError());
        assertEquals(0, store.nonTransactionalStateWrites);
        verifyNoInteractions(knowledgeService, knowledgeMapper, chunkMapper, executor, vectorService);
        assertTrue(submitted.isEmpty());
        assertTrue(reservations().isEmpty(), "拒绝请求不能留下本机预约");
        return failure;
    }

    private void assertNextValidRebuildRuns() {
        rejectSubmission = false;
        service.requestRebuild(KNOWLEDGE_ID);
        assertEquals(1, submitted.size(), "同一个Service拒绝后，下次合法请求应成功");
        CompletableFuture.runAsync(submitted.get(0)).join();
        assertEquals(KnowledgeIndexRebuildService.STATUS_READY, store.committed.getVectorIndexStatus());
    }

    private void invokeRequest(String operation) {
        if ("独立重建端点".equals(operation)) {
            service.requestRebuild(KNOWLEDGE_ID);
            return;
        }
        AiKnowledgeBo bo = edit(true);
        if ("普通配置编辑".equals(operation)) {
            bo.setEmbeddingModel("模型A");
            bo.setRebuildIndex(false);
        } else if ("无片段配置编辑".equals(operation)) {
            when(chunkMapper.selectCount(any())).thenReturn(0L);
        }
        service.updateConfiguration(bo);
    }

    @SuppressWarnings("unchecked")
    private Set<Long> reservations() throws Exception {
        Object target = ((Advised) service).getTargetSource().getTarget();
        return (Set<Long>) ReflectionTestUtils.getField(target, "runningRebuilds");
    }

    private void assertSafeLogs(Runnable operation, String... sentinels) {
        Logger logger = (Logger) LoggerFactory.getLogger(KnowledgeIndexRebuildService.class);
        boolean additive = logger.isAdditive();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setAdditive(false);
        try {
            operation.run();
            assertFalse(appender.list.isEmpty(), "保留安全诊断日志，不能通过完全不记录逃过脱敏测试");
            for (ILoggingEvent event : appender.list) {
                assertNull(event.getThrowableProxy(), "日志不能附带原始异常链");
                for (String sentinel : sentinels) assertFalse(event.getFormattedMessage().contains(sentinel), "日志不能输出异常原文哨兵");
            }
        } finally {
            logger.detachAppender(appender);
            logger.setAdditive(additive);
            appender.stop();
        }
    }

    private static AiKnowledgeBo edit(boolean rebuild) {
        AiKnowledgeBo bo = new AiKnowledgeBo();
        bo.setId(KNOWLEDGE_ID);
        bo.setEmbeddingModel("模型B");
        bo.setVectorStore("milvus");
        bo.setRebuildIndex(rebuild);
        return bo;
    }

    private static List<AiKnowledgeChunk> chunks(String tenant, int count) {
        return IntStream.range(0, count).mapToObj(index -> {
            AiKnowledgeChunk chunk = new AiKnowledgeChunk();
            chunk.setId((long) index + 1);
            chunk.setKnowledgeId(KNOWLEDGE_ID);
            chunk.setTenantId(tenant);
            chunk.setContent("作业规范片段" + index);
            return chunk;
        }).toList();
    }

    private static AiKnowledge copy(AiKnowledge source) {
        AiKnowledge result = new AiKnowledge();
        BeanUtils.copyProperties(source, result);
        return result;
    }

    /** 只使用内存状态和Mockito连接的真实Spring事务资源；不会创建或连接任何数据库。 */
    private static class MemoryTransactions {
        final DataSource dataSource = mock(DataSource.class);
        final DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        final Map<Connection, AiKnowledge> sessions = new ConcurrentHashMap<>();
        final List<String> events = new ArrayList<>();
        AiKnowledge committed = initial();
        boolean claimAllowed = true;
        boolean failNextCommit;
        int commits;
        int nonTransactionalStateWrites;
        Runnable beforeCommit;
        String lastFailedTenant;

        MemoryTransactions() {
            try {
                when(dataSource.getConnection()).thenAnswer(invocation -> {
                    Connection connection = mock(Connection.class);
                    AiKnowledge pending = copy(committed);
                    sessions.put(connection, pending);
                    AtomicBoolean autoCommit = new AtomicBoolean(true);
                    when(connection.getAutoCommit()).thenAnswer(call -> autoCommit.get());
                    doAnswer(call -> { autoCommit.set(call.getArgument(0)); return null; }).when(connection).setAutoCommit(anyBoolean());
                    doAnswer(call -> {
                        if (beforeCommit != null) beforeCommit.run();
                        if (failNextCommit) {
                            failNextCommit = false;
                            throw new SQLException("只读事务测试提交失败");
                        }
                        committed = copy(pending);
                        commits++;
                        events.add("commit");
                        return null;
                    }).when(connection).commit();
                    doAnswer(call -> { events.add("rollback"); return null; }).when(connection).rollback();
                    doAnswer(call -> { sessions.remove(connection); return null; }).when(connection).close();
                    return connection;
                });
            } catch (SQLException e) {
                throw new IllegalStateException("无法初始化事务连接替身", e);
            }
        }

        AiKnowledge current() {
            Object holder = TransactionSynchronizationManager.getResource(dataSource);
            return holder instanceof ConnectionHolder connection ? sessions.get(connection.getConnection()) : committed;
        }

        void writeState(AiKnowledge patch) {
            if (KnowledgeIndexRebuildService.STATUS_FAILED.equals(patch.getVectorIndexStatus())) {
                lastFailedTenant = TenantHelper.getDynamic();
            }
            if (!TransactionSynchronizationManager.isActualTransactionActive()) nonTransactionalStateWrites++;
            AiKnowledge target = current();
            if (patch.getVectorIndexStatus() != null) target.setVectorIndexStatus(patch.getVectorIndexStatus());
            if (patch.getVectorIndexProgress() != null) target.setVectorIndexProgress(patch.getVectorIndexProgress());
            if (patch.getVectorIndexError() != null) target.setVectorIndexError(patch.getVectorIndexError());
        }

        private static AiKnowledge initial() {
            AiKnowledge knowledge = new AiKnowledge();
            knowledge.setId(KNOWLEDGE_ID);
            knowledge.setTenantId("000000");
            knowledge.setEmbeddingModel("模型A");
            knowledge.setVectorStore("milvus");
            knowledge.setVectorIndexStatus(KnowledgeIndexRebuildService.STATUS_READY);
            return knowledge;
        }
    }
}
