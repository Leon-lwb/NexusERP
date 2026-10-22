package com.nexus.ai.service.rag;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.domain.entity.AiKnowledgeDocument;
import com.nexus.common.ai.domain.vo.AiKnowledgeVo;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.mapper.AiKnowledgeDocumentMapper;
import com.nexus.common.ai.service.IAiKnowledgeService;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.oss.core.OssClient;
import com.nexus.common.oss.entity.UploadResult;
import com.nexus.common.oss.factory.OssFactory;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.handle.PlusTenantLineHandler;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.tenant.properties.TenantProperties;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * 文档处理的租户与来源回归：运行真实异步任务、分段和租户 SQL 插件，不连接 OSS、数据库或模型。
 */
class KnowledgeProcessServiceTenantTest {

    private static final long KNOWLEDGE_ID = 101L;
    private static final long DOCUMENT_ID = 201L;
    private final DocumentParserService parser = mock(DocumentParserService.class);
    private final VectorStoreService vectors = mock(VectorStoreService.class);
    private final AiKnowledgeDocumentMapper documents = mock(AiKnowledgeDocumentMapper.class);
    private final AiKnowledgeChunkMapper chunks = mock(AiKnowledgeChunkMapper.class);
    private final IAiKnowledgeService knowledgeService = mock(IAiKnowledgeService.class);
    private final KnowledgeIndexRebuildService indexGate = mock(KnowledgeIndexRebuildService.class);
    private final OssClient oss = mock(OssClient.class);
    private final List<Runnable> submitted = new ArrayList<>();
    private final List<String> statusTenants = new ArrayList<>();
    private final List<LambdaUpdateWrapper<AiKnowledgeDocument>> claimAttempts = new ArrayList<>();
    private final List<Integer> claimRows = new ArrayList<>();
    private ApplicationContext previousContext;
    private GenericApplicationContext context;
    private KnowledgeProcessService service;
    private AiKnowledgeDocument document;

    /** 为请求线程绑定明确租户，所有外部依赖均由本地替身隔离。 */
    @BeforeEach
    void prepareTenantAndDocument() throws Exception {
        previousContext = SpringUtils.context();
        context = new GenericApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
            new MapPropertySource("文档处理测试租户", Map.of("tenant.enable", "true")));
        context.refresh();
        new SpringUtils().setApplicationContext(context);
        TenantHelper.setDynamic("000000");
        AiRequestContext.clear();
        service = new KnowledgeProcessService(parser, new ChunkingService(), vectors, documents, chunks,
            knowledgeService, indexGate, submitted::add);
        document = new AiKnowledgeDocument();
        document.setId(DOCUMENT_ID);
        document.setTenantId("000000");
        document.setKnowledgeId(KNOWLEDGE_ID);
        document.setFileName("作业规范.txt");
        document.setFilePath("fixture/document.txt");
        document.setStatus("3");
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
            AiKnowledgeDocument.class);
        when(documents.selectById(DOCUMENT_ID)).thenAnswer(invocation -> snapshotDocument());
        when(documents.insert(any(AiKnowledgeDocument.class))).thenAnswer(invocation -> {
            AiKnowledgeDocument inserted = invocation.getArgument(0);
            inserted.setId(DOCUMENT_ID);
            synchronized (document) {
                BeanUtils.copyProperties(inserted, document);
            }
            return 1;
        });
        when(documents.updateById(any(AiKnowledgeDocument.class)))
            .thenAnswer(invocation -> applyDocumentPatch(invocation.getArgument(0)));
        when(documents.update(
                        ArgumentMatchers.<AiKnowledgeDocument>any(),
                        ArgumentMatchers.<Wrapper<AiKnowledgeDocument>>any()))
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0) == null
                                        ? applyAtomicClaim(invocation.getArgument(1))
                                        : applyRejectedSubmission(
                                                invocation.getArgument(0),
                                                invocation.getArgument(1)));
        when(chunks.insert(any(AiKnowledgeChunk.class))).thenAnswer(invocation -> {
            invocation.<AiKnowledgeChunk>getArgument(0).setId(301L);
            return 1;
        });
        AiKnowledgeVo knowledge = new AiKnowledgeVo();
        knowledge.setId(KNOWLEDGE_ID);
        knowledge.setTenantId("000000");
        knowledge.setEmbeddingModel("本地测试模型替身");
        when(knowledgeService.selectById(KNOWLEDGE_ID)).thenReturn(knowledge);
        when(parser.isSupported(anyString())).thenReturn(true);
        when(parser.parse(any(), anyString())).thenReturn("制造作业规范");
        when(oss.uploadSuffix(any(byte[].class), anyString(), anyString()))
            .thenReturn(UploadResult.builder().url(document.getFilePath()).build());
        when(oss.getObjectContent(anyString())).thenAnswer(invocation -> new ByteArrayInputStream(new byte[]{1}));
        when(vectors.storeChunks(anyLong(), anyList(), anyString())).thenReturn(List.of("vector-301"));
    }

    /** 恢复线程与 Spring 全局状态，不影响同模块其他测试。 */
    @AfterEach
    void restoreTenantAndContext() {
        AiRequestContext.clear();
        TenantHelper.clearDynamic();
        new SpringUtils().setApplicationContext(previousContext);
        context.close();
    }

    /** 缺失或非法身份不能进入上传写入链，更不能由数据库默认值兜底。 */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", " 000000", "000000'", "tenant/path", "123456789012345678901"})
    @DisplayName("上传在任何外部操作前拒绝缺失或非法租户")
    void uploadRejectsMissingOrInvalidTenant(String tenant) {
        TenantHelper.setDynamic(tenant);
        try (MockedStatic<OssFactory> factory = mockStatic(OssFactory.class)) {
            factory.when(OssFactory::instance).thenReturn(oss);
            assertThrows(ServiceException.class, this::upload);
            verifyNoInteractions(oss, documents, chunks, vectors, indexGate, knowledgeService);
            assertTrue(submitted.isEmpty());
        }
    }

    /** 重试在删除旧片段或向量之前必须得到明确租户。 */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "000000 ", "tenant/path"})
    @DisplayName("重试在删除旧来源前拒绝缺失或非法租户")
    void retryRejectsMissingOrInvalidTenant(String tenant) {
        TenantHelper.setDynamic(tenant);
        assertThrows(ServiceException.class, () -> service.retryProcess(DOCUMENT_ID));
        verifyNoInteractions(documents, chunks, vectors, indexGate, knowledgeService);
        assertTrue(submitted.isEmpty());
    }

    /** 明确的当前租户不能替历史未知文档认领来源。 */
    @Test
    @DisplayName("租户000000不能重试历史tenant0文档，也不能删除其旧片段")
    void retryRefusesLegacyDocumentWithoutChangingOwnership() {
        document.setTenantId("0");
        assertThrows(ServiceException.class, () -> service.retryProcess(DOCUMENT_ID));
        verifyNoInteractions(chunks, vectors, oss);
        assertTrue(submitted.isEmpty());
        assertEquals("0", document.getTenantId());
    }

    /** 合法上传在真正的工作线程中仍保留精确身份及文档、知识库关联。 */
    @Test
    @DisplayName("正常异步上传保留000000、原文档ID及知识库ID，结束后清理AI上下文")
    void uploadKeepsExactTenantAndDocumentProvenance() throws Exception {
        try (MockedStatic<OssFactory> factory = mockStatic(OssFactory.class);
             MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class, CALLS_REAL_METHODS)) {
            factory.when(OssFactory::instance).thenReturn(oss);
            login.when(LoginHelper::getUserId).thenReturn(42L);
            assertEquals(DOCUMENT_ID, upload());
        }
        AtomicReference<String> vectorTenant = new AtomicReference<>();
        AtomicReference<String> aiTenant = new AtomicReference<>();
        AtomicReference<Long> aiUser = new AtomicReference<>();
        when(vectors.storeChunks(eq(KNOWLEDGE_ID), anyList(), anyString())).thenAnswer(invocation -> {
            vectorTenant.set(TenantHelper.getTenantId());
            aiTenant.set(AiRequestContext.getTenantId());
            aiUser.set(AiRequestContext.getUserId());
            TenantProperties properties = new TenantProperties();
            properties.setEnable(true);
            properties.setExcludes(List.of());
            String sql = new TenantLineInnerInterceptor(new PlusTenantLineHandler(properties))
                .parserSingle("SELECT id FROM ai_knowledge_chunk WHERE knowledge_id = 101", null);
            assertTrue(sql.contains("tenant_id = '000000'"));
            return List.of("vector-301");
        });
        runWorker();
        ArgumentCaptor<AiKnowledgeChunk> saved = ArgumentCaptor.forClass(AiKnowledgeChunk.class);
        verify(chunks).insert(saved.capture());
        assertEquals(KNOWLEDGE_ID, saved.getValue().getKnowledgeId());
        assertEquals(DOCUMENT_ID, saved.getValue().getDocumentId());
        assertEquals("000000", saved.getValue().getTenantId());
        assertEquals("000000", vectorTenant.get());
        assertEquals("000000", aiTenant.get());
        assertEquals(42L, aiUser.get());
        ArgumentCaptor<AiKnowledgeDocument> uploaded = ArgumentCaptor.forClass(AiKnowledgeDocument.class);
        verify(documents).insert(uploaded.capture());
        assertEquals("000000", uploaded.getValue().getTenantId());
        assertEquals(KNOWLEDGE_ID, uploaded.getValue().getKnowledgeId());
        assertEquals(List.of("000000", "000000"), statusTenants);
        verify(documents).updateById(argThat((AiKnowledgeDocument patch) ->
            "2".equals(patch.getStatus()) && Integer.valueOf(1).equals(patch.getChunkCount())));
    }

    /** 知识库在任务排队后不可见时必须失败，不能默认配置继续制造孤儿片段。 */
    @Test
    @DisplayName("异步知识库不可见时失败关闭，不生成无来源片段或向量")
    void missingKnowledgeInWorkerDoesNotUseDefaultConfiguration() throws Exception {
        service.retryProcess(DOCUMENT_ID);
        clearInvocations(documents);
        when(knowledgeService.selectById(KNOWLEDGE_ID)).thenReturn(null);
        runWorker();
        verify(chunks, never()).insert(any(AiKnowledgeChunk.class));
        verify(vectors, never()).storeChunks(anyLong(), anyList(), any());
        verify(documents, never()).updateById(argThat((AiKnowledgeDocument patch) -> "2".equals(patch.getStatus())));
        verify(documents).updateById(argThat((AiKnowledgeDocument patch) -> "3".equals(patch.getStatus())));
        verifyNoInteractions(chunks, vectors, oss);
    }

    /** 工作任务重读的文档必须仍属于提交时的知识库，关联失效前后均不得清理旧来源。 */
    @Test
    @DisplayName("排队期间文档知识库关联变化时既不清理旧来源，也不生成新片段")
    void changedDocumentKnowledgeCannotCreateForgedChunkAssociation() throws Exception {
        service.retryProcess(DOCUMENT_ID);
        document.setKnowledgeId(999L);
        runWorker();
        verifyNoInteractions(chunks, vectors, oss);
        verify(documents, never()).updateById(any(AiKnowledgeDocument.class));
    }

    /** 最早的状态写入失败也必须在捕获的租户内回写失败状态。 */
    @Test
    @DisplayName("异步解析中状态更新异常后，失败状态回写仍受原租户过滤")
    void earlyWorkerFailureKeepsTenantDuringFailureWrite() throws Exception {
        service.retryProcess(DOCUMENT_ID);
        when(documents.updateById(any(AiKnowledgeDocument.class))).thenAnswer(invocation -> {
            AiKnowledgeDocument patch = invocation.getArgument(0);
            if ("1".equals(patch.getStatus())) {
                throw new IllegalStateException("本地状态写入故障替身");
            }
            return applyDocumentPatch(patch);
        });
        runWorker();
        assertEquals(List.of("000000"), statusTenants, "失败状态不能退回线程池原租户或无租户");
        verify(chunks, never()).insert(any(AiKnowledgeChunk.class));
    }

    /** 提交拒绝不能先删除外部向量，否则 SQL 回滚也无法恢复旧文档的索引。 */
    @Test
    @DisplayName("重试提交被拒绝时不清理SQL旧片段或外部向量")
    void rejectedRetryNeverCleansChunksOrVectors() {
        service =
                new KnowledgeProcessService(
                        parser,
                        new ChunkingService(),
                        vectors,
                        documents,
                        chunks,
                        knowledgeService,
                        indexGate,
                        task -> {
                            throw new RejectedExecutionException("private-executor-credentials");
                        });

        ServiceException rejected = assertThrows(ServiceException.class, () -> service.retryProcess(DOCUMENT_ID));

        assertEquals("文档处理任务未提交，请稍后重试。", rejected.getMessage());
        assertNull(rejected.getCause());
        verifyNoInteractions(chunks, vectors, oss);
        assertTrue(submitted.isEmpty());
        verify(documents)
                .update(
                        argThat(
                                (AiKnowledgeDocument row) ->
                                        "3".equals(row.getStatus())
                                                && "文档处理任务未提交，请稍后重试。"
                                                        .equals(row.getErrorMessage())),
                        ArgumentMatchers.<Wrapper<AiKnowledgeDocument>>any());
        assertEquals(List.of("000000"), statusTenants);
    }

    /** 旧来源只在实际工作线程重新核对文档、知识库与索引就绪后清理，排队本身没有删除副作用。 */
    @Test
    @DisplayName("正常重试在worker核对归属和IndexReady后按原租户清理旧片段及向量")
    void retryCleanupRunsOnlyInValidatedWorkerTenant() throws Exception {
        List<String> cleanupContexts = new ArrayList<>();
        doAnswer(
                        invocation -> {
                            cleanupContexts.add(
                                    Thread.currentThread().getName()
                                            + "|"
                                            + TenantHelper.getTenantId()
                                            + "|"
                                            + AiRequestContext.getTenantId());
                            return 1;
                        })
                .when(chunks)
                .delete(ArgumentMatchers.<Wrapper<AiKnowledgeChunk>>any());
        doAnswer(invocation -> {
            cleanupContexts.add(Thread.currentThread().getName() + "|" + TenantHelper.getTenantId()
                + "|" + AiRequestContext.getTenantId());
            return null;
        }).when(vectors).deleteByDocumentId(DOCUMENT_ID, KNOWLEDGE_ID);

        service.retryProcess(DOCUMENT_ID);

        assertEquals(1, submitted.size());
        verifyNoInteractions(chunks, vectors, oss);
        clearInvocations(documents, knowledgeService, indexGate);
        runWorker();

        assertEquals(List.of("rag-document-test-worker|000000|000000",
            "rag-document-test-worker|000000|000000"), cleanupContexts);
        var validation = inOrder(documents, knowledgeService, indexGate);
        validation.verify(documents).selectById(DOCUMENT_ID);
        validation.verify(knowledgeService).selectById(KNOWLEDGE_ID);
        validation.verify(indexGate).assertIndexReadyForDocumentOps(KNOWLEDGE_ID);
        var cleanup = inOrder(indexGate, vectors, chunks, oss, parser);
        cleanup.verify(indexGate).assertIndexReadyForDocumentOps(KNOWLEDGE_ID);
        cleanup.verify(vectors).deleteByDocumentId(DOCUMENT_ID, KNOWLEDGE_ID);
        cleanup.verify(chunks).delete(ArgumentMatchers.<Wrapper<AiKnowledgeChunk>>any());
        cleanup.verify(oss).getObjectContent(document.getFilePath());
        cleanup.verify(parser).parse(any(), eq(document.getFileName()));
        verify(chunks).insert(any(AiKnowledgeChunk.class));
        verify(vectors).storeChunks(eq(KNOWLEDGE_ID), anyList(), anyString());
        verify(documents).updateById(argThat((AiKnowledgeDocument row) -> "2".equals(row.getStatus())
            && Integer.valueOf(1).equals(row.getChunkCount())));
    }

    /** 外部向量清理明确失败时必须保留 SQL 旧片段，不继续解析或生成新来源。 */
    @Test
    @DisplayName("重试向量清理失败时不删除SQL旧片段，按原租户记录固定失败")
    void retryVectorCleanupFailurePreservesSqlChunks() throws Exception {
        service.retryProcess(DOCUMENT_ID);
        doThrow(new ServiceException("向量清理失败，请稍后重试。"))
            .when(vectors).deleteByDocumentId(DOCUMENT_ID, KNOWLEDGE_ID);

        runWorker();

        verify(vectors).deleteByDocumentId(DOCUMENT_ID, KNOWLEDGE_ID);
        verifyNoInteractions(chunks, oss, parser);
        verify(vectors, never()).storeChunks(anyLong(), anyList(), any());
        verify(documents, never()).updateById(argThat((AiKnowledgeDocument row) -> "2".equals(row.getStatus())));
        verify(documents).updateById(argThat((AiKnowledgeDocument row) -> "3".equals(row.getStatus())
            && "文档处理失败，请检查资料格式、知识库配置或联系管理员。".equals(row.getErrorMessage())));
        assertEquals(List.of("000000"), statusTenants);
    }

    /** 排队期间进入重建或失败状态时，工作线程必须先停在索引安全门，不能删除既有来源。 */
    @Test
    @DisplayName("重试排队后索引不再READY时，worker重新拒绝且不清理旧来源")
    void queuedRetryRechecksIndexReadyBeforeCleanup() throws Exception {
        service.retryProcess(DOCUMENT_ID);
        clearInvocations(indexGate, documents);
        doThrow(new ServiceException("本地索引重建状态替身"))
            .when(indexGate).assertIndexReadyForDocumentOps(KNOWLEDGE_ID);

        runWorker();

        verify(indexGate).assertIndexReadyForDocumentOps(KNOWLEDGE_ID);
        verifyNoInteractions(chunks, vectors, oss);
        verify(documents, never()).updateById(argThat((AiKnowledgeDocument row) -> "2".equals(row.getStatus())));
        verify(documents).updateById(argThat((AiKnowledgeDocument row) -> "3".equals(row.getStatus())
            && "文档处理失败，请检查资料格式、知识库配置或联系管理员。".equals(row.getErrorMessage())));
        assertEquals("000000", statusTenants.get(statusTenants.size() - 1));
    }

    /** 两次请求均可排队，但后到任务必须原子认领失败，不能删除先到任务已完成的来源。 */
    @Test
    @DisplayName("同文档双重试排队，A成功后迟到B不清理或覆盖成功状态")
    void lateQueuedRetryDoesNotTouchCompletedDocument() throws Exception {
        service.retryProcess(DOCUMENT_ID);
        service.retryProcess(DOCUMENT_ID);
        assertEquals(2, submitted.size());
        assertEquals("3", document.getStatus(), "请求排队不能提前认领或修改文档状态");
        verify(documents, never())
                .update(
                        ArgumentMatchers.<AiKnowledgeDocument>any(),
                        ArgumentMatchers.<Wrapper<AiKnowledgeDocument>>any());
        runWorker(submitted.get(0));
        assertEquals("2", document.getStatus());
        assertEquals(1, document.getChunkCount());
        clearInvocations(documents, chunks, vectors, oss, parser);
        when(parser.parse(any(), anyString())).thenThrow(new IllegalStateException("迟到任务不应解析"));

        runWorker(submitted.get(1));

        verifyNoInteractions(chunks, vectors, oss, parser);
        verify(documents, never()).updateById(any(AiKnowledgeDocument.class));
        assertEquals(List.of(1, 0), claimRows);
        assertEquals("2", document.getStatus());
        assertEquals(1, document.getChunkCount());
    }

    /** 模拟读后另一任务已认领或完成，保证陈旧读取不能替代条件 UPDATE 的原子性。 */
    @ParameterizedTest
    @ValueSource(strings = {"1", "2"})
    @DisplayName("worker读到旧状态3但CAS时已处理中或成功，认领失败无副作用")
    void lostClaimAfterStaleReadDoesNotOverwriteActiveOrCompletedDocument(String competingStatus) throws Exception {
        service.retryProcess(DOCUMENT_ID);
        when(documents.selectById(DOCUMENT_ID)).thenAnswer(invocation -> {
            synchronized (document) {
                AiKnowledgeDocument stale = snapshotDocument();
                document.setStatus(competingStatus);
                document.setChunkCount(7);
                return stale;
            }
        });
        clearInvocations(documents);

        runWorker();

        verify(documents)
                .update(
                        ArgumentMatchers.<AiKnowledgeDocument>any(),
                        ArgumentMatchers.<Wrapper<AiKnowledgeDocument>>any());
        verify(documents, never()).updateById(any(AiKnowledgeDocument.class));
        verifyNoInteractions(chunks, vectors, oss, parser);
        assertEquals(List.of(0), claimRows);
        assertEquals(competingStatus, document.getStatus());
        assertEquals(7, document.getChunkCount());
        assertTrue(statusTenants.isEmpty());
    }

    /** 核对真实 LambdaUpdateWrapper 的绑定参数，不能靠租户插件或非原子读取隐式补条件。 */
    @ParameterizedTest
    @ValueSource(strings = {"0", "3"})
    @DisplayName("重试CAS显式绑定文档ID、精确租户、捕获知识库及状态0或3，设置状态1")
    void retryClaimBindsExactTenantKnowledgeAndRetryableStatuses(String initialStatus) throws Exception {
        document.setStatus(initialStatus);
        service.retryProcess(DOCUMENT_ID);

        runWorker();

        assertEquals(List.of(1), claimRows);
        assertEquals(1, claimAttempts.size());
        LambdaUpdateWrapper<AiKnowledgeDocument> claim = claimAttempts.get(0);
        String where = claim.getSqlSegment();
        Map<String, Object> parameters = claim.getParamNameValuePairs();
        assertEquals(DOCUMENT_ID, parameterForColumn(where, "id", parameters));
        assertEquals("000000", parameterForColumn(where, "tenant_id", parameters));
        assertEquals(KNOWLEDGE_ID, parameterForColumn(where, "knowledge_id", parameters));
        assertEquals(List.of("0", "3"), statusesForClaim(claim));
        assertEquals("1", parameterForColumn(claim.getSqlSet(), "status", parameters));
        assertEquals("2", document.getStatus());
    }

    /** 首次上传任务只能认领持久化状态0，不能借上传路径重新处理失败3留下的片段。 */
    @Test
    @DisplayName("首次上传insert持久化状态0，worker的CAS只允许状态0")
    void uploadClaimOnlyAllowsPendingStatus() throws Exception {
        try (MockedStatic<OssFactory> factory = mockStatic(OssFactory.class)) {
            factory.when(OssFactory::instance).thenReturn(oss);
            assertEquals(DOCUMENT_ID, upload());
        }
        assertEquals("0", document.getStatus());

        runWorker();

        assertEquals(List.of(1), claimRows);
        assertEquals(1, claimAttempts.size());
        assertEquals(List.of("0"), statusesForClaim(claimAttempts.get(0)));
        assertEquals("2", document.getStatus());
        verify(vectors, never()).deleteByDocumentId(anyLong(), anyLong());
        verify(chunks, never()).delete(ArgumentMatchers.<Wrapper<AiKnowledgeChunk>>any());
    }

    /** 重试先执行并失败后，迟到的首次上传任务不能不清理旧片段就再次插入。 */
    @Test
    @DisplayName("重试先执行失败为3后，迟到首次上传CAS失败且不重复生成partial片段")
    void lateInitialUploadCannotReprocessFailedRetryWithoutCleanup() throws Exception {
        try (MockedStatic<OssFactory> factory = mockStatic(OssFactory.class)) {
            factory.when(OssFactory::instance).thenReturn(oss);
            upload();
        }
        service.retryProcess(DOCUMENT_ID);
        assertEquals(2, submitted.size());
        when(vectors.storeChunks(anyLong(), anyList(), anyString()))
            .thenThrow(new ServiceException("本地向量写入失败替身"));
        runWorker(submitted.get(1));
        assertEquals("3", document.getStatus());
        verify(chunks).insert(any(AiKnowledgeChunk.class));
        clearInvocations(documents, chunks, vectors, oss, parser);

        runWorker(submitted.get(0));

        verifyNoInteractions(chunks, vectors, oss, parser);
        verify(documents, never()).updateById(any(AiKnowledgeDocument.class));
        assertEquals(List.of(1, 0), claimRows);
        assertEquals(List.of("0", "3"), statusesForClaim(claimAttempts.get(0)));
        assertEquals(List.of("0"), statusesForClaim(claimAttempts.get(1)));
        assertEquals("3", document.getStatus());
    }

    /** 拒绝回写也会与其他任务竞争，不能把已认领或已完成的文档重新置为可重试。 */
    @ParameterizedTest
    @ValueSource(strings = {"1", "2"})
    @DisplayName("提交拒绝不会覆盖另一任务的处理中或成功状态")
    void rejectedRetryDoesNotOverwriteAnotherTaskState(String otherState) {
        service =
                new KnowledgeProcessService(
                        parser,
                        new ChunkingService(),
                        vectors,
                        documents,
                        chunks,
                        knowledgeService,
                        indexGate,
                        task -> {
                            document.setStatus(otherState);
                            throw new RejectedExecutionException("私有执行器诊断");
                        });

        assertThrows(ServiceException.class, () -> service.retryProcess(DOCUMENT_ID));

        assertEquals(otherState, document.getStatus(), "拒绝回写不得重新开放已被其他任务认领的文档");
        verifyNoInteractions(chunks, vectors, oss);
    }

    /** 同步 OSS 失败可经 URL 导入结果返回，必须在上传边界清除原始异常与原因。 */
    @Test
    @DisplayName("同步上传异常不经文档或URL导入接口回显凭据")
    void uploadFailureExposesOnlyFixedPublicMessage() {
        when(oss.uploadSuffix(any(byte[].class), anyString(), anyString())).thenThrow(
            new IllegalStateException("endpoint=private-address password=private-value Bearer private-token"));
        try (MockedStatic<OssFactory> factory = mockStatic(OssFactory.class)) {
            factory.when(OssFactory::instance).thenReturn(oss);
            var rejected = assertThrows(ServiceException.class, this::upload);
            assertEquals("文档上传失败，请稍后重试或联系管理员。", rejected.getMessage());
            assertNull(rejected.getCause());
        }
        verify(documents, never()).insert(any(AiKnowledgeDocument.class));
        assertTrue(submitted.isEmpty());
    }

    /** 执行器明确拒绝时上传不能假成功，已创建文档须按捕获租户记录可重试失败。 */
    @Test
    @DisplayName("任务提交拒绝时回写固定失败并向上传方明确报错")
    void rejectedSubmissionMarksOwnedDocumentFailedWithoutLeakingExecutorDetails() {
        service =
                new KnowledgeProcessService(
                        parser,
                        new ChunkingService(),
                        vectors,
                        documents,
                        chunks,
                        knowledgeService,
                        indexGate,
                        task -> {
                            throw new RejectedExecutionException("private-executor-credentials");
                        });
        try (MockedStatic<OssFactory> factory = mockStatic(OssFactory.class)) {
            factory.when(OssFactory::instance).thenReturn(oss);
            var rejected = assertThrows(ServiceException.class, this::upload);
            assertEquals("文档处理任务未提交，请稍后重试。", rejected.getMessage());
            assertNull(rejected.getCause());
        }
        verify(documents)
                .update(
                        argThat(
                                (AiKnowledgeDocument row) ->
                                        "3".equals(row.getStatus())
                                                && "文档处理任务未提交，请稍后重试。"
                                                        .equals(row.getErrorMessage())),
                        ArgumentMatchers.<Wrapper<AiKnowledgeDocument>>any());
        assertEquals(List.of("000000"), statusTenants);
        verifyNoInteractions(chunks, vectors);
    }

    /** 文档列表会公开失败信息，解析器和向量厂商的原始异常不得持久化到展示字段。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("解析或向量化失败只保存固定公共消息，不回显地址、凭据和Token")
    void workerFailureDoesNotPublishPrivateParserOrProviderMessage(boolean vectorFailure) throws Exception {
        var privateError = new IllegalStateException(
            "jdbc:mysql://private-host password=private-value Bearer private-token apiKey=private-key");
        if (vectorFailure) {
            when(vectors.storeChunks(anyLong(), anyList(), anyString())).thenThrow(privateError);
        } else {
            when(parser.parse(any(), anyString())).thenThrow(privateError);
        }
        service.retryProcess(DOCUMENT_ID);
        clearInvocations(documents);

        runWorker();

        var updates = ArgumentCaptor.forClass(AiKnowledgeDocument.class);
        verify(documents, atLeastOnce()).updateById(updates.capture());
        var failure = updates.getAllValues().stream().filter(row -> "3".equals(row.getStatus()))
            .findFirst().orElseThrow();
        assertEquals("文档处理失败，请检查资料格式、知识库配置或联系管理员。", failure.getErrorMessage());
        assertFalse(failure.getErrorMessage().contains("private"));
        assertEquals("000000", statusTenants.get(statusTenants.size() - 1));
    }

    /** 生产线程池满载时会退回请求线程，任务不能清掉仍在使用的请求上下文。 */
    @Test
    @DisplayName("CallerRuns内联处理完成后保留请求线程原有AI上下文")
    void callerRunsPreservesRequestAiContext() {
        service = new KnowledgeProcessService(parser, new ChunkingService(), vectors, documents, chunks,
            knowledgeService, indexGate, Runnable::run);
        AiRequestContext.set("request-previous", 81L, "测试用户", 82L, 83L, "请求模型");
        AiRequestContext.setKnowledgeId(84L);
        AiRequestContext.setLastTokenUsage(85, 86, 87L);
        try (MockedStatic<OssFactory> factory = mockStatic(OssFactory.class)) {
            factory.when(OssFactory::instance).thenReturn(oss);
            assertEquals(DOCUMENT_ID, upload());
        }
        assertEquals("request-previous", AiRequestContext.getTenantId());
        assertEquals(81L, AiRequestContext.getUserId());
        assertEquals("测试用户", AiRequestContext.getUserName());
        assertEquals(82L, AiRequestContext.getSessionId());
        assertEquals(83L, AiRequestContext.getModelId());
        assertEquals("请求模型", AiRequestContext.getModelName());
        assertEquals(84L, AiRequestContext.getKnowledgeId());
        assertEquals(85, AiRequestContext.getLastInputTokens());
        assertEquals(86, AiRequestContext.getLastOutputTokens());
        assertEquals(87L, AiRequestContext.getLastModelId());
        assertEquals("000000", TenantHelper.getTenantId());
    }

    /** 历史未知知识库不能被当前租户认领；入口必须先于 OSS 写入拒绝。 */
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"0", "another-tenant"})
    @DisplayName("知识库归属缺失或不一致时，上传和重试都不产生副作用")
    void unknownKnowledgeOwnershipIsRejectedBeforeSideEffects(String tenant) {
        AiKnowledgeVo unknown = new AiKnowledgeVo();
        unknown.setId(KNOWLEDGE_ID);
        unknown.setTenantId(tenant);
        when(knowledgeService.selectById(KNOWLEDGE_ID)).thenReturn(unknown);
        try (MockedStatic<OssFactory> factory = mockStatic(OssFactory.class)) {
            factory.when(OssFactory::instance).thenReturn(oss);
            assertThrows(ServiceException.class, this::upload);
            assertThrows(ServiceException.class, () -> service.retryProcess(DOCUMENT_ID));
        }
        verifyNoInteractions(oss, chunks, vectors, indexGate);
        verify(documents, never()).insert(any(AiKnowledgeDocument.class));
        assertTrue(submitted.isEmpty());
        assertEquals(tenant, unknown.getTenantId());
    }

    /** 返回独立的持久化快照，模拟 Mapper 读取而非共享可变实体引用。 */
    private AiKnowledgeDocument snapshotDocument() {
        synchronized (document) {
            AiKnowledgeDocument snapshot = new AiKnowledgeDocument();
            BeanUtils.copyProperties(document, snapshot);
            return snapshot;
        }
    }

    /** 状态化模拟 updateById，仅应用非空列，保留真实成功、失败及片段计数。 */
    private int applyDocumentPatch(AiKnowledgeDocument patch) {
        synchronized (document) {
            statusTenants.add(TenantHelper.getTenantId());
            if (patch.getStatus() != null) {
                document.setStatus(patch.getStatus());
            }
            if (patch.getChunkCount() != null) {
                document.setChunkCount(patch.getChunkCount());
            }
            if (patch.getErrorMessage() != null) {
                document.setErrorMessage(patch.getErrorMessage());
            }
            return 1;
        }
    }

    /** 拒绝回写的独立条件更新也必须保护租户、知识库及可重试状态，不能覆盖运行中任务。 */
    private int applyRejectedSubmission(AiKnowledgeDocument patch, LambdaUpdateWrapper<AiKnowledgeDocument> condition) {
        String where = condition.getSqlSegment();
        Map<String, Object> parameters = condition.getParamNameValuePairs();
        assertEquals(DOCUMENT_ID, parameterForColumn(where, "id", parameters));
        assertEquals("000000", parameterForColumn(where, "tenant_id", parameters));
        assertEquals(KNOWLEDGE_ID, parameterForColumn(where, "knowledge_id", parameters));
        List<Object> statuses = statusesForClaim(condition);
        assertTrue(statuses.equals(List.of("0")) || statuses.equals(List.of("0", "3")));
        assertEquals("3", patch.getStatus());
        synchronized (document) {
            if (!statuses.contains(document.getStatus())) return 0;
            return applyDocumentPatch(patch);
        }
    }

    /** 真实解析条件包装器并在同一临界区比较、更新状态，模拟数据库原子 CAS，不连接数据库。 */
    private int applyAtomicClaim(LambdaUpdateWrapper<AiKnowledgeDocument> claim) {
        String where = claim.getSqlSegment();
        String set = claim.getSqlSet();
        Map<String, Object> parameters = claim.getParamNameValuePairs();
        assertFalse(where.toUpperCase(Locale.ROOT).contains(" OR "), "认领条件不能使用OR放宽归属");
        Object id = parameterForColumn(where, "id", parameters);
        Object tenant = parameterForColumn(where, "tenant_id", parameters);
        Object knowledgeId = parameterForColumn(where, "knowledge_id", parameters);
        List<Object> statuses = statusesForClaim(claim);
        assertTrue(statuses.equals(List.of("0")) || statuses.equals(List.of("0", "3")), "认领只能包含待解析或失败状态");
        assertEquals("1", parameterForColumn(set, "status", parameters), "认领必须原子设置处理中状态");
        synchronized (document) {
            claimAttempts.add(claim);
            boolean matches = Objects.equals(id, document.getId()) && Objects.equals(tenant, document.getTenantId())
                && Objects.equals(knowledgeId, document.getKnowledgeId()) && statuses.contains(document.getStatus());
            int rows = matches ? 1 : 0;
            if (matches) {
                document.setStatus("1");
            }
            claimRows.add(rows);
            return rows;
        }
    }

    /** 从真实 SQL 占位符读取列的绑定值，不依赖 MPGENVAL 编号或客户端声明。 */
    private Object parameterForColumn(String sql, String column, Map<String, Object> parameters) {
        var match = Pattern.compile("\\b" + column + "\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.([^}]+)\\}",
            Pattern.CASE_INSENSITIVE).matcher(sql);
        assertTrue(match.find(), "CAS SQL必须显式绑定列：" + column + "，实际SQL：" + sql);
        return parameters.get(match.group(1));
    }

    /** 读取真实状态 IN 条件及绑定值，防止把处理中或成功状态纳入可认领范围。 */
    private List<Object> statusesForClaim(LambdaUpdateWrapper<AiKnowledgeDocument> claim) {
        var statuses = Pattern.compile("\\bstatus\\s+IN\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE)
            .matcher(claim.getSqlSegment());
        assertTrue(statuses.find(), "CAS必须使用显式状态IN条件");
        return Pattern.compile("#\\{ew\\.paramNameValuePairs\\.([^}]+)\\}").matcher(statuses.group(1))
            .results().map(match -> claim.getParamNameValuePairs().get(match.group(1))).toList();
    }

    /** 用内存文件调用真实上传入口；OSS 工厂由当前线程的替身隔离。 */
    private Long upload() {
        return service.uploadAndProcess(KNOWLEDGE_ID,
            new MockMultipartFile("file", document.getFileName(), "text/plain", new byte[]{1}));
    }

    /** 单任务场景保持原有断言，多任务场景显式指定要执行的队列项。 */
    private void runWorker() throws Exception {
        assertEquals(1, submitted.size());
        runWorker(submitted.get(0));
    }

    /** 独立工作线程模拟线程池复用，检查任务恢复外层租户并清理 AI 上下文。 */
    private void runWorker(Runnable task) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            TenantHelper.setDynamic("worker-previous");
            AiRequestContext.clear();
            try (MockedStatic<OssFactory> factory = mockStatic(OssFactory.class)) {
                factory.when(OssFactory::instance).thenReturn(oss);
                task.run();
                assertEquals("worker-previous", TenantHelper.getTenantId());
                assertNull(AiRequestContext.getTenantId());
                assertNull(AiRequestContext.getUserId());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                AiRequestContext.clear();
                TenantHelper.clearDynamic();
            }
        }, "rag-document-test-worker");
        worker.start();
        worker.join(10000);
        assertFalse(worker.isAlive(), "本地工作任务必须在十秒内完成");
        if (failure.get() != null) {
            throw new AssertionError("异步任务断言失败", failure.get());
        }
    }
}
