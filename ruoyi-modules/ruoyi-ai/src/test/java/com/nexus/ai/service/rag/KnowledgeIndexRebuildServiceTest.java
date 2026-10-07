package com.nexus.ai.service.rag;

import com.nexus.common.ai.domain.bo.AiKnowledgeBo;
import com.nexus.common.ai.domain.entity.AiKnowledge;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.mapper.AiKnowledgeMapper;
import com.nexus.common.ai.mapper.AiModelMapper;
import com.nexus.common.ai.service.IAiKnowledgeService;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 知识库向量索引重建生命周期单元测试（轻量真实事务代理 + Mockito，无 Milvus 无数据库）
 * <p>覆盖：REBUILD_INDEX_REQUIRED 确认门、REBUILDING 条件抢占先于异步提交落库、
 * 并发抢占失败拒绝、重建端点强制重启、线程池拒绝转 FAILED、失败/重嵌入不完整转 FAILED、
 * 成功 READY/100/完成时间、无片段/同配置直通。</p>
 *
 * @author nexus-school
 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class KnowledgeIndexRebuildServiceTest {

    private static final Long KNOWLEDGE_ID = 1L;

    private final AiKnowledgeMapper knowledgeMapper = mock(AiKnowledgeMapper.class);
    private final AiKnowledgeChunkMapper chunkMapper = mock(AiKnowledgeChunkMapper.class);
    private final AiModelMapper modelMapper = mock(AiModelMapper.class);
    private final IAiKnowledgeService aiKnowledgeService = mock(IAiKnowledgeService.class);
    private final VectorStoreService vectorStoreService = mock(VectorStoreService.class);
    private final Executor aiIndexRebuildExecutor = mock(Executor.class);

    private ApplicationContext previousContext;
    private GenericApplicationContext tenantContext;

    @BeforeEach
    void bindExplicitFixtureTenant() {
        // 原17例只补真实明确的000000身份，不能让单测null成为生产异步兜底。
        previousContext = SpringUtils.context();
        tenantContext = new GenericApplicationContext();
        tenantContext.getEnvironment().getPropertySources().addFirst(
            new MapPropertySource("重建测试租户", Map.of("tenant.enable", "true")));
        tenantContext.refresh();
        new SpringUtils().setApplicationContext(tenantContext);
        TenantHelper.setDynamic("000000");
    }

    @AfterEach
    void restoreFixtureTenant() {
        TenantHelper.clearDynamic();
        new SpringUtils().setApplicationContext(previousContext);
        tenantContext.close();
    }

    private KnowledgeIndexRebuildService newService() {
        // 保留原17例的业务断言，入口使用真实事务代理，不能以裸构造绕过提交边界。
        DataSource dataSource = mock(DataSource.class);
        try {
            org.mockito.Mockito.when(dataSource.getConnection()).thenAnswer(invocation -> {
                Connection connection = mock(Connection.class);
                org.mockito.Mockito.when(connection.getAutoCommit()).thenReturn(true);
                return connection;
            });
        } catch (SQLException e) {
            throw new AssertionError("事务连接替身初始化失败", e);
        }
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        KnowledgeIndexRebuildService target = new KnowledgeIndexRebuildService(knowledgeMapper, chunkMapper, modelMapper,
            aiKnowledgeService, vectorStoreService, aiIndexRebuildExecutor, transactionManager);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(transactionManager, new AnnotationTransactionAttributeSource()));
        return (KnowledgeIndexRebuildService) proxy.getProxy();
    }

    // ------------------------------------------------------------------
    // 计划内三条测试
    // ------------------------------------------------------------------

    @Test
    void changingEmbeddingConfigurationWithChunksRequiresExplicitRebuildConfirmation() {
        when(knowledgeMapper.selectById(KNOWLEDGE_ID)).thenReturn(persistedKnowledge());
        when(chunkMapper.selectCount(any())).thenReturn(2L);

        ServiceException e = assertThrows(ServiceException.class,
            () -> newService().updateConfiguration(changedEmbeddingBo(false)));
        // 前端 Task 10 按该精确 token 弹重建确认框，消息必须一字不差
        assertEquals("REBUILD_INDEX_REQUIRED", e.getMessage());
    }

    @Test
    void confirmedRebuildMarksKnowledgeRebuildingBeforeSubmittingAsyncWork() {
        when(knowledgeMapper.selectById(KNOWLEDGE_ID)).thenReturn(persistedKnowledge());
        when(chunkMapper.selectCount(any())).thenReturn(2L);
        when(aiKnowledgeService.updateByBo(any(AiKnowledgeBo.class))).thenReturn(1);
        when(knowledgeMapper.update(any(AiKnowledge.class), any())).thenReturn(1);

        newService().updateConfiguration(changedEmbeddingBo(true));

        // 顺序敏感：REBUILDING 条件抢占必须先落库，再提交异步任务（保证任务执行前状态可见）
        InOrder inOrder = inOrder(knowledgeMapper, aiIndexRebuildExecutor);
        inOrder.verify(knowledgeMapper).update(
            org.mockito.ArgumentMatchers.<AiKnowledge>argThat(k ->
                KnowledgeIndexRebuildService.STATUS_REBUILDING.equals(k.getVectorIndexStatus())
                    && Integer.valueOf(0).equals(k.getVectorIndexProgress())),
            any());
        inOrder.verify(aiIndexRebuildExecutor).execute(any(Runnable.class));
    }

    @Test
    void failedRebuildStoresOnlySanitizedError() {
        AiKnowledge knowledge = persistedKnowledge();
        knowledge.setId(100L);
        when(knowledgeMapper.selectById(100L)).thenReturn(knowledge);
        when(chunkMapper.selectList(any())).thenReturn(List.of());
        doThrow(new IllegalStateException("Bearer secret-value failed"))
            .when(vectorStoreService).rebuildKnowledge(anyLong(), anyString(), anyInt(), any());

        newService().rebuild(100L);

        // 失败写入为条件 UPDATE（WHERE REBUILDING），实体不含 id/完成时间
        verify(knowledgeMapper).update(org.mockito.ArgumentMatchers.<AiKnowledge>argThat(k ->
            KnowledgeIndexRebuildService.STATUS_FAILED.equals(k.getVectorIndexStatus())
                && k.getVectorIndexError() != null
                && !k.getVectorIndexError().contains("secret-value")
                // FAILED 行不写完成时间（列语义为最近完成时间）
                && k.getVectorIndexUpdatedTime() == null), any());
    }

    // ------------------------------------------------------------------
    // 并发抢占 / 强制重启 / 线程池拒绝
    // ------------------------------------------------------------------

    @Test
    void confirmedEditRejectedWhenConcurrentClaimLosesRace() {
        // 条件抢占 UPDATE 影响 0 行 = 另一重建任务已持有锁（含确认编辑与端点同时到达的竞争）
        when(knowledgeMapper.selectById(KNOWLEDGE_ID)).thenReturn(persistedKnowledge());
        when(chunkMapper.selectCount(any())).thenReturn(2L);
        when(aiKnowledgeService.updateByBo(any(AiKnowledgeBo.class))).thenReturn(1);
        when(knowledgeMapper.update(any(AiKnowledge.class), any())).thenReturn(0);

        ServiceException e = assertThrows(ServiceException.class,
            () -> newService().updateConfiguration(changedEmbeddingBo(true)));
        assertTrue(e.getMessage().contains("向量索引正在重建中"), e.getMessage());
        verifyNoInteractions(aiIndexRebuildExecutor);
    }

    @Test
    void rebuildRequestHappyPathClaimsRebuildingAndSubmitsTask() {
        when(knowledgeMapper.selectById(KNOWLEDGE_ID)).thenReturn(persistedKnowledge());
        when(knowledgeMapper.update(any(AiKnowledge.class), any())).thenReturn(1);

        newService().requestRebuild(KNOWLEDGE_ID);

        InOrder inOrder = inOrder(knowledgeMapper, aiIndexRebuildExecutor);
        inOrder.verify(knowledgeMapper).update(
            org.mockito.ArgumentMatchers.<AiKnowledge>argThat(k ->
                KnowledgeIndexRebuildService.STATUS_REBUILDING.equals(k.getVectorIndexStatus())
                    && Integer.valueOf(0).equals(k.getVectorIndexProgress())),
            any());
        inOrder.verify(aiIndexRebuildExecutor).execute(any(Runnable.class));
    }

    @Test
    void rebuildEndpointForceRestartsInterruptedRebuildingTask() {
        // claim 失败且无存活任务（集合为空）：重建端点的恢复路径——强制回收（幂等 DROP+重嵌入）后照常提交
        AiKnowledge knowledge = persistedKnowledge();
        knowledge.setVectorIndexStatus(KnowledgeIndexRebuildService.STATUS_REBUILDING);
        when(knowledgeMapper.selectById(KNOWLEDGE_ID)).thenReturn(knowledge);
        when(knowledgeMapper.update(any(AiKnowledge.class), any())).thenReturn(0);

        newService().requestRebuild(KNOWLEDGE_ID);

        verify(knowledgeMapper).updateById(org.mockito.ArgumentMatchers.<AiKnowledge>argThat(k ->
            KnowledgeIndexRebuildService.STATUS_REBUILDING.equals(k.getVectorIndexStatus())
                && Integer.valueOf(0).equals(k.getVectorIndexProgress())));
        verify(aiIndexRebuildExecutor).execute(any(Runnable.class));
    }

    @Test
    void secondEndpointCallWhileTaskIsLiveIsRejected() {
        // 模拟活跃任务：捕获提交的 runnable 不执行 → 集合登记仍在（任务 finally 未跑）
        List<Runnable> submitted = new java.util.ArrayList<>();
        doAnswer(invocation -> {
            submitted.add((Runnable) invocation.getArgument(0));
            return null;
        }).when(aiIndexRebuildExecutor).execute(any(Runnable.class));
        when(knowledgeMapper.selectById(KNOWLEDGE_ID)).thenReturn(persistedKnowledge());
        when(knowledgeMapper.update(any(AiKnowledge.class), any())).thenReturn(1);

        KnowledgeIndexRebuildService service = newService();
        service.requestRebuild(KNOWLEDGE_ID);
        assertEquals(1, submitted.size());

        // 在跑任务窗口内的二次调用：拒绝，绝不二次提交（双跑会让 T2 DROP 掉 T1 的半成品向量）
        ServiceException e = assertThrows(ServiceException.class,
            () -> service.requestRebuild(KNOWLEDGE_ID));
        assertTrue(e.getMessage().contains("向量索引正在重建中"), e.getMessage());
        assertEquals(1, submitted.size());

        // 任务执行完毕（finally 移除登记）后可再次发起重建
        submitted.get(0).run();
        service.requestRebuild(KNOWLEDGE_ID);
        assertEquals(2, submitted.size());
    }

    @Test
    void executorRejectionMarksKnowledgeFailed() {
        // 专用线程池满载被 AbortPolicy 拒绝 → 回写 FAILED，管理员可重试（此前在 CallerRunsPolicy 上是死代码）
        when(knowledgeMapper.selectById(KNOWLEDGE_ID)).thenReturn(persistedKnowledge());
        when(knowledgeMapper.update(any(AiKnowledge.class), any())).thenReturn(1);
        doThrow(new RejectedExecutionException("pool saturated"))
            .when(aiIndexRebuildExecutor).execute(any(Runnable.class));

        newService().requestRebuild(KNOWLEDGE_ID);

        verify(knowledgeMapper).update(org.mockito.ArgumentMatchers.<AiKnowledge>argThat(k ->
            KnowledgeIndexRebuildService.STATUS_FAILED.equals(k.getVectorIndexStatus())
                && k.getVectorIndexError() != null
                && k.getVectorIndexError().contains("提交失败")), any());
    }

    // ------------------------------------------------------------------
    // 直通路径 / 成功路径 / 重建失败语义
    // ------------------------------------------------------------------

    @Test
    void noChunksEditUpdatesNormallyWithoutTouchingIndexState() {
        when(knowledgeMapper.selectById(KNOWLEDGE_ID)).thenReturn(persistedKnowledge());
        when(chunkMapper.selectCount(any())).thenReturn(0L);
        when(aiKnowledgeService.updateByBo(any(AiKnowledgeBo.class))).thenReturn(1);

        newService().updateConfiguration(changedEmbeddingBo(false));

        verify(aiKnowledgeService).updateByBo(any(AiKnowledgeBo.class));
        verify(knowledgeMapper, never()).updateById(any(AiKnowledge.class));
        verify(knowledgeMapper, never()).update(any(AiKnowledge.class), any());
        verifyNoInteractions(aiIndexRebuildExecutor);
    }

    @Test
    void sameConfigEditDoesNotRequireRebuild() {
        when(knowledgeMapper.selectById(KNOWLEDGE_ID)).thenReturn(persistedKnowledge());
        when(chunkMapper.selectCount(any())).thenReturn(2L);
        when(aiKnowledgeService.updateByBo(any(AiKnowledgeBo.class))).thenReturn(1);

        AiKnowledgeBo bo = changedEmbeddingBo(false);
        bo.setEmbeddingModel("text-embedding-old");
        bo.setVectorStore(null);

        newService().updateConfiguration(bo);

        verify(aiKnowledgeService).updateByBo(any(AiKnowledgeBo.class));
        verify(knowledgeMapper, never()).updateById(any(AiKnowledge.class));
        verify(knowledgeMapper, never()).update(any(AiKnowledge.class), any());
        verifyNoInteractions(aiIndexRebuildExecutor);
    }

    @Test
    void sameConfigWithExplicitRebuildActuallyRebuildsExistingChunks() {
        when(knowledgeMapper.selectById(KNOWLEDGE_ID)).thenReturn(persistedKnowledge());
        when(chunkMapper.selectCount(any())).thenReturn(2L);
        when(aiKnowledgeService.updateByBo(any(AiKnowledgeBo.class))).thenReturn(1);
        when(knowledgeMapper.update(any(AiKnowledge.class), any())).thenReturn(1);

        AiKnowledgeBo bo = changedEmbeddingBo(true);
        bo.setEmbeddingModel("text-embedding-old");
        bo.setVectorStore("milvus");

        newService().updateConfiguration(bo);

        verify(aiKnowledgeService).updateByBo(any(AiKnowledgeBo.class));
        verify(knowledgeMapper).update(
            org.mockito.ArgumentMatchers.<AiKnowledge>argThat(k ->
                KnowledgeIndexRebuildService.STATUS_REBUILDING.equals(k.getVectorIndexStatus())
                    && Integer.valueOf(0).equals(k.getVectorIndexProgress())), any());
        verify(aiIndexRebuildExecutor).execute(any(Runnable.class));
    }

    @Test
    void vectorStoreChangeWithChunksAlsoRequiresRebuildConfirmation() {
        when(knowledgeMapper.selectById(KNOWLEDGE_ID)).thenReturn(persistedKnowledge());
        when(chunkMapper.selectCount(any())).thenReturn(2L);

        AiKnowledgeBo bo = changedEmbeddingBo(false);
        bo.setEmbeddingModel("text-embedding-old");
        bo.setVectorStore("inmemory");

        ServiceException e = assertThrows(ServiceException.class,
            () -> newService().updateConfiguration(bo));
        assertEquals("REBUILD_INDEX_REQUIRED", e.getMessage());
    }

    @Test
    void partialEditWithoutEmbeddingModelDoesNotRequireRebuild() {
        // 部分更新：请求未携带 embeddingModel（null=不修改该列），
        // 按持久化模型参与比较（维度解析委托 VectorStoreService 单一事实来源），不得误触发确认门
        when(knowledgeMapper.selectById(KNOWLEDGE_ID)).thenReturn(persistedKnowledge());
        when(chunkMapper.selectCount(any())).thenReturn(2L);
        when(aiKnowledgeService.updateByBo(any(AiKnowledgeBo.class))).thenReturn(1);

        AiKnowledgeBo bo = changedEmbeddingBo(false);
        bo.setEmbeddingModel(null);

        newService().updateConfiguration(bo);

        verify(aiKnowledgeService).updateByBo(any(AiKnowledgeBo.class));
        verify(knowledgeMapper, never()).update(any(AiKnowledge.class), any());
        verifyNoInteractions(aiIndexRebuildExecutor);
    }

    @Test
    void rebuildSuccessMarksReadyWithProgressAndCompletionTime() {
        AiKnowledge knowledge = persistedKnowledge();
        knowledge.setId(100L);
        when(knowledgeMapper.selectById(100L)).thenReturn(knowledge);
        AiKnowledgeChunk first = new AiKnowledgeChunk();
        first.setId(11L);
        AiKnowledgeChunk second = new AiKnowledgeChunk();
        second.setId(12L);
        when(chunkMapper.selectList(any())).thenReturn(List.of(first, second));

        newService().rebuild(100L);

        verify(vectorStoreService).rebuildKnowledge(eq(100L), eq("text-embedding-old"), anyInt(),
            argThat(chunks -> chunks != null && chunks.size() == 2));
        // 完成写入为条件 UPDATE（WHERE REBUILDING）
        verify(knowledgeMapper).update(org.mockito.ArgumentMatchers.<AiKnowledge>argThat(k ->
            KnowledgeIndexRebuildService.STATUS_READY.equals(k.getVectorIndexStatus())
                && Integer.valueOf(100).equals(k.getVectorIndexProgress())
                && k.getVectorIndexUpdatedTime() != null), any());
    }

    @Test
    void staleTaskReadyWriteIsNoOpWhenSuperseded() {
        // 条件完成写入命中 0 行（状态已不处于 REBUILDING，任务被取代）：
        // 仅告警跳过，绝不再有无条件状态写入
        AiKnowledge knowledge = persistedKnowledge();
        knowledge.setId(100L);
        when(knowledgeMapper.selectById(100L)).thenReturn(knowledge);
        when(chunkMapper.selectList(any())).thenReturn(List.of());
        when(knowledgeMapper.update(any(AiKnowledge.class), any())).thenReturn(0);

        newService().rebuild(100L);

        verify(knowledgeMapper).update(org.mockito.ArgumentMatchers.<AiKnowledge>argThat(k ->
            KnowledgeIndexRebuildService.STATUS_READY.equals(k.getVectorIndexStatus())), any());
        verify(knowledgeMapper, never()).updateById(any(AiKnowledge.class));
    }

    @Test
    void incompleteReembedMarksKnowledgeFailed() {
        // 严格模式的重嵌入不完整（x/y）必须转 FAILED，绝不带着空/残索引返回 READY
        AiKnowledge knowledge = persistedKnowledge();
        knowledge.setId(100L);
        when(knowledgeMapper.selectById(100L)).thenReturn(knowledge);
        when(chunkMapper.selectList(any())).thenReturn(List.of());
        doThrow(new IllegalStateException("重嵌入不完整: 1/2"))
            .when(vectorStoreService).rebuildKnowledge(anyLong(), anyString(), anyInt(), any());

        newService().rebuild(100L);

        verify(knowledgeMapper).update(org.mockito.ArgumentMatchers.<AiKnowledge>argThat(k ->
            KnowledgeIndexRebuildService.STATUS_FAILED.equals(k.getVectorIndexStatus())
                && k.getVectorIndexError() != null
                && k.getVectorIndexError().contains("重嵌入不完整")), any());
    }

    @Test
    void updateConfigurationRejectsMissingKnowledge() {
        when(knowledgeMapper.selectById(404L)).thenReturn(null);
        ServiceException e = assertThrows(ServiceException.class,
            () -> newService().updateConfiguration(changedEmbeddingBoFor(404L)));
        assertTrue(e.getMessage().contains("知识库不存在"), e.getMessage());
    }

    // ------------------------------------------------------------------
    // 测试数据
    // ------------------------------------------------------------------

    private AiKnowledge persistedKnowledge() {
        AiKnowledge knowledge = new AiKnowledge();
        knowledge.setId(KNOWLEDGE_ID);
        knowledge.setKnowledgeName("测试知识库");
        knowledge.setEmbeddingModel("text-embedding-old");
        knowledge.setVectorStore("milvus");
        knowledge.setVectorIndexStatus(KnowledgeIndexRebuildService.STATUS_READY);
        return knowledge;
    }

    private AiKnowledgeBo changedEmbeddingBo(boolean rebuildIndex) {
        return changedEmbeddingBoFor(KNOWLEDGE_ID, rebuildIndex);
    }

    private AiKnowledgeBo changedEmbeddingBoFor(Long id) {
        return changedEmbeddingBoFor(id, false);
    }

    private AiKnowledgeBo changedEmbeddingBoFor(Long id, boolean rebuildIndex) {
        AiKnowledgeBo bo = new AiKnowledgeBo();
        bo.setId(id);
        bo.setKnowledgeName("测试知识库");
        bo.setEmbeddingModel("text-embedding-new");
        bo.setRebuildIndex(rebuildIndex);
        return bo;
    }
}
