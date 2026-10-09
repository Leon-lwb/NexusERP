package com.nexus.ai.service.rag;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.nexus.ai.config.AiProperties;
import com.nexus.ai.config.AiThreadPoolConfig;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.common.ai.domain.entity.AiKnowledge;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.domain.entity.AiKnowledgeDocument;
import com.nexus.common.ai.domain.vo.AiKnowledgeVo;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.mapper.AiKnowledgeDocumentMapper;
import com.nexus.common.ai.mapper.AiKnowledgeMapper;
import com.nexus.common.ai.mapper.AiModelMapper;
import com.nexus.common.ai.service.IAiKnowledgeService;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.oss.core.OssClient;
import com.nexus.common.oss.factory.OssFactory;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 知识处理真实执行器的隔离故障矩阵，补充既有顺序任务和直接内联替身未覆盖的交错、取消与恢复。
 * <p>运行真实 KnowledgeProcessService、ChunkingService、VectorStoreService 及配置拒绝策略；
 * Mapper 使用内存状态和真实条件包装器模拟原子更新，OSS、Embedding 和 Milvus 均为模拟外部端口。
 * Milvus 客户端预注入缓存且只操作本地内存存储，不创建 SDK 客户端，不连接数据库或任何网络服务。
 * 本类不是数据库事务、真实 Milvus 网络故障或生产线程池验收。</p>
 */
@Isolated("真实MP元数据仅临时初始化并按键复原，禁止与其他测试并发改写缓存")
class KnowledgeProcessServiceExecutorFaultMatrixTest {

    private static final String TENANT = "000000";
    private static final String WORKER_TENANT = "fixture-worker";
    private static final long USER_ID = 42L;
    private static final long KNOWLEDGE_ID = 101L;
    private static final long DOCUMENT_ID = 201L;
    private static final long OTHER_DOCUMENT_ID = 202L;
    private static final String OLD_CONTENT = "隔离旧片段";
    private static final String NEW_CONTENT = "隔离知识文档新片段";
    private static final String OTHER_CONTENT = "隔离其他文档片段";
    private static final String PROCESS_FAILURE = "文档处理失败，请检查资料格式、知识库配置或联系管理员。";
    private static final long WAIT_SECONDS = 10L;
    private static final String UNSUPPORTED_CAS_SHAPE = "fixture CAS条件形状不受支持，仅允许精确纯AND认领条件";

    private final List<CountDownLatch> gates = new ArrayList<>();
    private final List<TestExecutor> executors = new ArrayList<>();
    private final ConcurrentLinkedQueue<Thread> createdWorkers = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Throwable> workerFailures = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean cleanupInterrupted = new AtomicBoolean();
    private FixtureMetadata metadata;
    private ExternalPorts callerPorts;
    private Ports ports;

    /** Spring 配置只在线程内桩替，不替换 SpringUtils 全局上下文；认证、OSS 均禁止真实外部访问。 */
    @BeforeEach
    void prepareIsolatedPorts() throws Exception {
        metadata = new FixtureMetadata();
        ports = new Ports();
        callerPorts = new ExternalPorts(ports.oss);
        TenantHelper.setDynamic(TENANT);
        AiRequestContext.clear();
    }

    /** 每阶段共用一个截止时间，不按线程数累加等待；一个资源关闭失败也不能跳过其他资源和元数据复原。 */
    @AfterEach
    void restoreIsolatedState() {
        List<Executable> checks = new ArrayList<>();
        try {
            gates.forEach(gate -> {
                while (gate.getCount() > 0) {
                    gate.countDown();
                }
            });
            executors.forEach(ThreadPoolExecutor::shutdown);
            long deadline = cleanupDeadline();
            for (TestExecutor executor : executors) {
                boolean graceful = awaitPoolTermination(executor, deadline, checks);
                checks.add(() -> assertTrue(graceful, "fixture检查点释放后未正常退出，禁止用清理中断掩盖卡死"));
            }
            executors.stream().filter(executor -> !executor.isTerminated()).forEach(ThreadPoolExecutor::shutdownNow);
            deadline = cleanupDeadline();
            for (TestExecutor executor : executors) {
                boolean terminated = awaitPoolTermination(executor, deadline, checks);
                checks.add(() -> assertTrue(terminated, "fixture强制终止阶段仍有未退出的执行器"));
                checks.add(executor::assertNoWorkerFailure);
            }
            deadline = cleanupDeadline();
            for (Thread worker : createdWorkers) {
                joinWorker(worker, deadline, checks);
                checks.add(() -> assertFalse(worker.isAlive(), "fixture仍有后台worker，必须先排查夹具清理"));
            }
            workerFailures.forEach(failure -> checks.add(() -> {
                throw new AssertionError("fixture工作线程生命周期异常", failure);
            }));
        } finally {
            checks.add(() -> assertTrue(createdWorkers.stream().noneMatch(Thread::isAlive),
                "fixture复原前worker必须全部退出，不能把清理故障报告为生产RED"));
            try {
                AiRequestContext.clear();
                if (callerPorts != null) {
                    TenantHelper.clearDynamic();
                }
            } catch (Throwable failure) {
                checks.add(() -> { throw new AssertionError("fixture请求线程清理失败", failure); });
            }
            for (AutoCloseable resource : new AutoCloseable[]{callerPorts, metadata}) {
                if (resource != null) {
                    try {
                        resource.close();
                    } catch (Throwable failure) {
                        checks.add(() -> { throw new AssertionError("fixture资源复原失败", failure); });
                    }
                }
            }
            if (cleanupInterrupted.get()) {
                Thread.currentThread().interrupt();
            }
        }
        assertAll("隔离夹具收尾", checks);
    }

    /** 清理中断只延迟到复原结束再恢复标志，仍在共同截止时间内等待所有pool退出。 */
    private boolean awaitPoolTermination(TestExecutor executor, long deadline, List<Executable> checks) {
        while (true) {
            try {
                return executor.awaitTermination(remainingNanos(deadline), TimeUnit.NANOSECONDS);
            } catch (InterruptedException interrupted) {
                recordCleanupInterrupt(interrupted, checks);
                if (remainingNanos(deadline) == 0L) {
                    return executor.isTerminated();
                }
            }
        }
    }

    /** 只有真实join等待，不用sleep轮询；不得传入零毫秒造成无限等待。 */
    private void joinWorker(Thread worker, long deadline, List<Executable> checks) {
        while (worker.isAlive()) {
            long remaining = remainingNanos(deadline);
            if (remaining == 0L) {
                return;
            }
            try {
                worker.join(Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining)));
            } catch (InterruptedException interrupted) {
                recordCleanupInterrupt(interrupted, checks);
            }
        }
    }

    /** 首次清理中断保存fixture诊断并立即取消本地pool，不让中断跳过其余资源和元数据复原。 */
    private void recordCleanupInterrupt(InterruptedException interrupted, List<Executable> checks) {
        if (cleanupInterrupted.compareAndSet(false, true)) {
            checks.add(() -> { throw new AssertionError("fixture收尾线程被中断", interrupted); });
        }
        executors.forEach(ThreadPoolExecutor::shutdownNow);
    }

    /** 三个清理阶段分别共享十秒预算，避免线程数量放大超时。 */
    private static long cleanupDeadline() {
        return System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
    }

    /** 截止后传入零等待；join单独避免零毫秒造成无限等待。 */
    private static long remainingNanos(long deadline) {
        return Math.max(0L, deadline - System.nanoTime());
    }

    /** 真正构造OR、NOT及未知谓词，不能忽略数据库布尔结构后用Java AND模拟出假绿。 */
    @ParameterizedTest
    @ValueSource(strings = {"OR_STATUS", "NOT_STATUS", "UNKNOWN_PREDICATE"})
    @DisplayName("fixture CAS对真实OR、NOT及未知WHERE形状失败关闭，不修改已认领文档或来源")
    void unsupportedCasWhereIsRejectedBeforeAnyStateChange(String shape) throws Exception {
        synchronized (ports.stateLock) {
            ports.document.setStatus("1");
        }
        AiKnowledgeDocument before = ports.documentSnapshot();
        LambdaUpdateWrapper<AiKnowledgeDocument> condition = new LambdaUpdateWrapper<AiKnowledgeDocument>()
            .eq(AiKnowledgeDocument::getId, DOCUMENT_ID)
            .eq(AiKnowledgeDocument::getTenantId, TENANT)
            .eq(AiKnowledgeDocument::getKnowledgeId, KNOWLEDGE_ID)
            .set(AiKnowledgeDocument::getStatus, "1");
        switch (shape) {
            case "OR_STATUS" -> condition.or().in(AiKnowledgeDocument::getStatus, List.of("0", "3"));
            case "NOT_STATUS" -> condition.not(nested -> nested.in(AiKnowledgeDocument::getStatus, List.of("0", "3")));
            case "UNKNOWN_PREDICATE" -> condition.in(AiKnowledgeDocument::getStatus, List.of("0", "3")).apply("1 = 0");
            default -> throw new AssertionError("未知负例fixture：" + shape);
        }
        String sql = condition.getSqlSegment();
        switch (shape) {
            case "OR_STATUS" -> assertTrue(sql.contains(" OR "), "必须由真实Wrapper生成OR分支");
            case "NOT_STATUS" -> assertTrue(sql.contains("NOT ("), "必须由真实Wrapper生成NOT分支");
            case "UNKNOWN_PREDICATE" -> {
                assertTrue(sql.contains("AND 1 = 0"), "未知形状必须包含真实额外谓词");
                assertFalse(sql.contains(" OR ") || sql.contains("NOT"), "未知形状不能只重复OR或NOT扫描");
            }
            default -> throw new AssertionError("未知负例fixture：" + shape);
        }
        AssertionError rejected = assertThrows(AssertionError.class, () -> ports.conditionalUpdate(null, condition));
        assertEquals(UNSUPPORTED_CAS_SHAPE, rejected.getMessage(), "必须由形状门禁拒绝而非其他绑定断言碰巧失败");
        assertEquals(before, ports.documentSnapshot());
        assertTrue(ports.claimRows.isEmpty(), "不支持的SQL不能产生伪造的零行claim结果");
        assertTrue(ports.statusTenants.isEmpty());
        assertEquals(0, ports.completedWrites.get());
        assertEquals(0, ports.failedWrites.get());
        assertOldSourcesUntouched();
    }

    /** 已关闭的真实池必须明确拒绝，登记失败但保留旧来源；新隔离池随后可以正常重试。 */
    @Test
    @DisplayName("真实shutdown拒绝知识重试，旧片段与向量保留，换池后可重新处理")
    void shutdownRejectsWithoutCleanupAndFreshExecutorCanRecover() throws Exception {
        TestExecutor stopped = executor(1, 1, 0);
        stopped.shutdown();
        assertTrue(stopped.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS));
        ServiceException rejected = assertThrows(ServiceException.class,
            () -> service(stopped).retryProcess(DOCUMENT_ID));

        assertEquals("文档处理任务未提交，请稍后重试。", rejected.getMessage());
        assertNull(rejected.getCause());
        assertEquals("3", ports.documentSnapshot().getStatus());
        assertEquals(rejected.getMessage(), ports.documentSnapshot().getErrorMessage());
        assertEquals(1, ports.rejectionWrites.get());
        assertTrue(ports.claimRows.isEmpty(), "shutdown不能静默运行或丢弃后假装提交成功");
        assertOldSourcesUntouched();
        assertEquals(TENANT, TenantHelper.getTenantId());
        assertNull(AiRequestContext.getTenantId());
        recoverWithFreshExecutor();
        assertEquals(List.of(1), List.copyOf(ports.claimRows));
    }

    /** 用工作线程和队列同时占满真实池，而不是用 Runnable::run 代替 CallerRuns 触发条件。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("真实满载CallerRuns在成功或模拟Milvus删除失败后恢复请求租户与完整AI上下文")
    void saturatedCallerRunsRestoresCallerContext(boolean deletionFails) throws Exception {
        TestExecutor busy = executor(1, 1, 0);
        CountDownLatch occupied = gate(1);
        CountDownLatch release = gate(1);
        busy.executeHarness(() -> {
            occupied.countDown();
            await(release, "释放满载工作线程");
        });
        await(occupied, "工作线程已占用");
        busy.executeHarness(() -> { });
        assertEquals(1, busy.getQueue().size(), "队列必须实际占满才能触发拒绝策略");
        Thread caller = Thread.currentThread();
        ports.beforeVectorDelete = () -> {
            assertSame(caller, Thread.currentThread(), "满载知识任务必须由提交线程内联执行");
            if (deletionFails) {
                throw new IllegalStateException("fixture-endpoint password=fixture-only Bearer fixture-token");
            }
        };
        AiRequestContext.set("fixture-request", 81L, "隔离请求用户", 82L, 83L, "隔离请求模型");
        AiRequestContext.setKnowledgeId(84L);
        AiRequestContext.setLastTokenUsage(85, 86, 87L);
        ContextSnapshot before = ContextSnapshot.capture();

        service(busy).retryProcess(DOCUMENT_ID);

        assertEquals(before, ContextSnapshot.capture(), "内联成功和失败均必须恢复完整请求上下文");
        assertEquals(List.of(1), List.copyOf(ports.claimRows));
        assertEquals(1, ports.vectorDeleteCalls.get());
        assertEquals(TENANT, ports.deleteContexts.element().tenant());
        assertEquals(TENANT, ports.deleteContexts.element().aiTenant());
        assertEquals(USER_ID, ports.deleteContexts.element().user());
        if (deletionFails) {
            assertDeletionFailurePreservedSources();
        } else {
            assertSuccessfulReplacement();
        }
        release.countDown();
    }

    /** 两个真正的 worker 同时持有状态3快照，迟到 CAS 分别在状态1和状态2发生，都只能有一者认领。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("双worker陈旧读取竞争，处理中或完成后迟到CAS均不能清理或覆盖赢家")
    void staleConcurrentRetryOnlyOneWorkerClaims(boolean loserAfterCompletion) throws Exception {
        TestExecutor pool = executor(2, 2, 2);
        CountDownLatch occupied = gate(2);
        CountDownLatch startWorkers = gate(1);
        for (int i = 0; i < 2; i++) {
            pool.executeHarness(() -> {
                occupied.countDown();
                await(startWorkers, "释放双工作线程");
            });
        }
        await(occupied, "双工作线程已占用");
        CountDownLatch bothRead = gate(2);
        CountDownLatch allowClaims = gate(1);
        CountDownLatch winnerDeleting = gate(1);
        CountDownLatch allowDeletion = gate(1);
        CountDownLatch allowLoser = gate(1);
        CountDownLatch loserClaimed = gate(1);
        CountDownLatch winnerFinished = gate(1);
        AtomicInteger readSequence = new AtomicInteger();
        Map<Thread, Integer> roles = new ConcurrentHashMap<>();
        ports.afterWorkerRead = snapshot -> {
            assertEquals("3", snapshot.getStatus(), "两任务必须真实持有同一可重试旧快照");
            roles.put(Thread.currentThread(), readSequence.incrementAndGet());
            bothRead.countDown();
            await(allowClaims, "双快照读取后开始CAS");
        };
        ports.beforeClaim = () -> {
            if (Integer.valueOf(2).equals(roles.get(Thread.currentThread()))) {
                await(allowLoser, "允许迟到任务CAS");
            }
        };
        ports.afterClaim = rows -> {
            if (Integer.valueOf(2).equals(roles.get(Thread.currentThread()))) {
                assertEquals(0, rows, "陈旧任务不得重新认领处理中或成功文档");
                loserClaimed.countDown();
            }
        };
        ports.beforeVectorDelete = () -> {
            winnerDeleting.countDown();
            await(allowDeletion, "允许赢家完成向量删除");
        };
        pool.afterServiceTask = () -> {
            if (Integer.valueOf(1).equals(roles.get(Thread.currentThread()))) {
                winnerFinished.countDown();
            }
        };
        KnowledgeProcessService service = service(pool);
        service.retryProcess(DOCUMENT_ID);
        service.retryProcess(DOCUMENT_ID);
        assertEquals(2, pool.getQueue().size());
        assertTrue(ports.claimRows.isEmpty(), "排队本身不能认领或清理旧来源");
        startWorkers.countDown();
        await(bothRead, "两worker已读取旧快照");
        allowClaims.countDown();
        await(winnerDeleting, "赢家已原子认领并停在外部删除前");
        if (loserAfterCompletion) {
            allowDeletion.countDown();
            await(winnerFinished, "赢家处理完成且上下文已恢复");
            assertEquals("2", ports.documentSnapshot().getStatus());
        } else {
            assertEquals("1", ports.documentSnapshot().getStatus());
        }
        allowLoser.countDown();
        await(loserClaimed, "迟到CAS已返回零行");
        assertEquals(List.of(1, 0), List.copyOf(ports.claimRows));
        if (!loserAfterCompletion) {
            assertEquals("1", ports.documentSnapshot().getStatus(), "迟到任务不能覆盖赢家处理中状态");
            assertEquals(List.of(OLD_CONTENT), ports.documentChunks().stream().map(AiKnowledgeChunk::getContent).toList());
            assertEquals(0, ports.chunkDeletes.get());
            allowDeletion.countDown();
        }
        pool.awaitServiceTasks();
        assertSuccessfulReplacement();
        assertEquals(1, ports.vectorDeleteCalls.get());
        assertEquals(1, ports.chunkDeletes.get());
        assertEquals(1, ports.completedWrites.get(), "不能出现第二次成功覆盖");
        assertEquals(0, ports.failedWrites.get(), "未认领任务不能回写失败");
        pool.assertWorkerContextsRestored(2);
    }

    /** shutdownNow 移出已入队但尚未认领的重试；它不同于提交拒绝，旧失败来源仍须能显式重试。 */
    @Test
    @DisplayName("真实shutdownNow取消排队重试，未claim不清理旧来源，新池可恢复")
    void shutdownNowDrainsQueuedRetryWithoutClaimAndAllowsResubmission() throws Exception {
        TestExecutor pool = executor(1, 1, 0);
        CountDownLatch occupied = gate(1);
        CountDownLatch release = gate(1);
        pool.executeHarness(() -> {
            occupied.countDown();
            try {
                assertTrue(release.await(WAIT_SECONDS, TimeUnit.SECONDS), "排队取消须由shutdownNow而非等待超时驱动");
            } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
            }
        });
        await(occupied, "工作线程已占用");
        service(pool).retryProcess(DOCUMENT_ID);
        assertEquals(1, pool.getQueue().size());

        List<Runnable> cancelled = pool.shutdownNow();

        assertEquals(1, cancelled.size(), "确认知识任务被真实执行器移出队列，而非自行省略执行");
        assertTrue(pool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS));
        assertTrue(ports.claimRows.isEmpty());
        assertEquals(0, ports.rejectionWrites.get(), "队列取消不等于execute抛出的提交拒绝");
        assertEquals("3", ports.documentSnapshot().getStatus());
        assertOldSourcesUntouched();
        recoverWithFreshExecutor();
        assertEquals(List.of(1), List.copyOf(ports.claimRows));
    }

    /** 中断发生在模拟外部删除的等待检查点；失败不能删除 SQL 来源，池退出后可重新完成处理。 */
    @Test
    @DisplayName("真实shutdownNow中断模拟Milvus删除，不继续SQL删除或成功回写，随后可恢复")
    void shutdownNowInterruptsVectorDeletionAndFreshExecutorCanRecover() throws Exception {
        TestExecutor pool = executor(1, 1, 1);
        CountDownLatch deleting = gate(1);
        CountDownLatch release = gate(1);
        ports.beforeVectorDelete = () -> {
            deleting.countDown();
            try {
                assertTrue(release.await(WAIT_SECONDS, TimeUnit.SECONDS), "等待真实shutdownNow中断删除");
                fail("取消路径必须由中断结束，不能由超时或主动释放伪造");
            } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("fixture-Milvus删除等待被中断", cancelled);
            }
        };
        service(pool).retryProcess(DOCUMENT_ID);
        await(deleting, "已认领任务进入外部删除检查点");
        assertEquals("1", ports.documentSnapshot().getStatus());
        assertEquals(0, ports.chunkDeletes.get());

        assertTrue(pool.shutdownNow().isEmpty(), "此场景取消运行中的唯一任务，而不是取消队列项");
        pool.awaitServiceTasks();
        assertTrue(pool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS));

        assertDeletionFailurePreservedSources();
        pool.assertWorkerContextsRestored(1);
        ports.beforeVectorDelete = () -> { };
        recoverWithFreshExecutor();
        assertEquals(List.of(1, 1), List.copyOf(ports.claimRows));
        assertEquals(2, ports.vectorDeleteCalls.get());
        assertEquals(1, ports.chunkDeletes.get());
        assertEquals(1, ports.completedWrites.get());
    }

    /** 新建独立小池，运行真实业务入口并等待 afterExecute，避免只断言 mock 调用次数。 */
    private void recoverWithFreshExecutor() throws Exception {
        TestExecutor recovery = executor(1, 1, 1);
        service(recovery).retryProcess(DOCUMENT_ID);
        recovery.awaitServiceTasks();
        assertSuccessfulReplacement();
        recovery.assertWorkerContextsRestored(1);
    }

    /** 来源仍在状态化 Mapper 和内存向量端口中，且没有解析、SQL 删除、新向量或成功写入。 */
    private void assertOldSourcesUntouched() {
        assertEquals(List.of(OLD_CONTENT), ports.documentChunks().stream().map(AiKnowledgeChunk::getContent).toList());
        assertEquals(List.of(OTHER_CONTENT, OLD_CONTENT).stream().sorted().toList(), ports.vectorContents());
        assertEquals(0, ports.vectorDeleteCalls.get());
        assertEquals(0, ports.chunkDeletes.get());
        assertEquals(0, ports.parses.get());
        assertEquals(0, ports.vectorAdds.get());
        assertEquals(0, ports.completedWrites.get());
    }

    /** 删除失败必须保留旧 SQL 行及旧向量，并以原租户保存固定失败消息而非成功覆盖。 */
    private void assertDeletionFailurePreservedSources() {
        assertProcessingIdentity();
        AiKnowledgeDocument document = ports.documentSnapshot();
        assertEquals("3", document.getStatus());
        assertEquals(PROCESS_FAILURE, document.getErrorMessage());
        assertEquals(1, document.getChunkCount());
        assertEquals(List.of(OLD_CONTENT), ports.documentChunks().stream().map(AiKnowledgeChunk::getContent).toList());
        assertEquals(List.of(OTHER_CONTENT, OLD_CONTENT).stream().sorted().toList(), ports.vectorContents());
        assertEquals(0, ports.chunkDeletes.get());
        assertEquals(0, ports.parses.get());
        assertEquals(0, ports.vectorAdds.get());
        assertEquals(0, ports.completedWrites.get());
        assertEquals(List.of(TENANT), List.copyOf(ports.statusTenants));
    }

    /** 成功由持久化替身内容证明；其他文档不被删除，新片段具有正确来源及向量 ID。 */
    private void assertSuccessfulReplacement() {
        assertProcessingIdentity();
        AiKnowledgeDocument document = ports.documentSnapshot();
        assertEquals("2", document.getStatus());
        assertEquals(1, document.getChunkCount());
        List<AiKnowledgeChunk> rows = ports.documentChunks();
        assertEquals(1, rows.size());
        AiKnowledgeChunk chunk = rows.get(0);
        assertEquals(NEW_CONTENT, chunk.getContent());
        assertEquals(TENANT, chunk.getTenantId());
        assertEquals(DOCUMENT_ID, chunk.getDocumentId());
        assertEquals(KNOWLEDGE_ID, chunk.getKnowledgeId());
        assertNotNull(chunk.getVectorId());
        assertEquals(List.of(OTHER_CONTENT, NEW_CONTENT).stream().sorted().toList(), ports.vectorContents());
        assertEquals(1, ports.parses.get());
        assertEquals(1, ports.vectorAdds.get());
        assertTrue(ports.statusTenants.stream().allMatch(TENANT::equals));
    }

    /** 删除端口处记录真实任务身份，异步任务不能使用池内旧租户、旧用户或空 AI 身份。 */
    private void assertProcessingIdentity() {
        assertFalse(ports.deleteContexts.isEmpty());
        ports.deleteContexts.forEach(snapshot -> {
            assertEquals(TENANT, snapshot.tenant());
            assertEquals(TENANT, snapshot.aiTenant());
            assertEquals(USER_ID, snapshot.user());
        });
    }

    /** 不加载 Spring 业务容器，也不使用 AOP、数据库连接或生产单例。 */
    private KnowledgeProcessService service(TestExecutor executor) {
        return new KnowledgeProcessService(ports.parser, new ChunkingService(), ports.vectors, ports.documents,
            ports.chunks, ports.knowledgeService, ports.indexGate, executor);
    }

    /** 只从新建且没有任务的配置实例提取现有 handler，立即关闭原池，缩小测试容量为一或二。 */
    private TestExecutor executor(int workers, int queueCapacity, int expectedServiceTasks) {
        ThreadPoolTaskExecutor configured = (ThreadPoolTaskExecutor) new AiThreadPoolConfig().aiExecutor();
        RejectedExecutionHandler handler;
        try {
            handler = configured.getThreadPoolExecutor().getRejectedExecutionHandler();
        } finally {
            configured.shutdown();
        }
        TestExecutor executor = new TestExecutor(workers, queueCapacity, expectedServiceTasks, handler);
        executors.add(executor);
        return executor;
    }

    /** 注册检查点，使任意断言失败后清理阶段也能释放等待者。 */
    private CountDownLatch gate(int count) {
        CountDownLatch gate = new CountDownLatch(count);
        gates.add(gate);
        return gate;
    }

    /** 有界等待仅用于确定性检查点；不使用 sleep、轮询、超大任务或数据制造交错。 */
    private static void await(CountDownLatch gate, String checkpoint) {
        try {
            assertTrue(gate.await(WAIT_SECONDS, TimeUnit.SECONDS), "检查点未到达：" + checkpoint);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("检查点被意外中断：" + checkpoint, interrupted);
        }
    }

    /** 所有外部静态端口在线程内成组安装、逆序关闭，不修改 SpringUtil 的 applicationContext 或 beanFactory。 */
    private static class ExternalPorts implements AutoCloseable {
        private final List<AutoCloseable> resources = new ArrayList<>();

        /** 同一安装路径用于请求和worker，认证默认全桩，防止CALLS_REAL_METHODS意外访问登录存储。 */
        ExternalPorts(OssClient oss) {
            try {
                MockedStatic<SpringUtil> spring = own(resources, mockStatic(SpringUtil.class));
                spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
                MockedStatic<LoginHelper> login = own(resources, mockStatic(LoginHelper.class));
                login.when(LoginHelper::isLogin).thenReturn(false);
                login.when(LoginHelper::getUserId).thenReturn(USER_ID);
                MockedStatic<OssFactory> factory = own(resources, mockStatic(OssFactory.class));
                factory.when(OssFactory::instance).thenReturn(oss);
                MockedStatic<MilvusEmbeddingStore> sdk = own(resources, mockStatic(MilvusEmbeddingStore.class));
                sdk.when(MilvusEmbeddingStore::builder).thenThrow(new AssertionError(
                    "fixture禁止构造Milvus SDK客户端；缓存替身缺失须先判夹具故障"));
            } catch (Throwable failure) {
                try {
                    close();
                } catch (Throwable closing) {
                    failure.addSuppressed(closing);
                }
                throw new AssertionError("fixture外部端口初始化失败", failure);
            }
        }

        /** 关闭失败也继续撤销其他线程内静态桩，避免后续测试报already registered。 */
        @Override
        public void close() {
            closeFixtureResources("fixture外部端口撤销", resources);
        }
    }

    /** 按MP 3.5.16实际字段保存被改写条目，不清空全局Map，也不覆盖与本类无关的实体配置。 */
    private static class FixtureMetadata implements AutoCloseable {
        private final List<AutoCloseable> restores = new ArrayList<>();

        /** 同一临时Configuration初始化两实体，保存实体、表名、Lambda列和GlobalConfig四类缓存。 */
        FixtureMetadata() throws Exception {
            MybatisConfiguration configuration = new MybatisConfiguration();
            try {
                preserve(TableInfoHelper.class, "TABLE_INFO_CACHE", AiKnowledgeDocument.class, AiKnowledgeChunk.class);
                preserve(TableInfoHelper.class, "TABLE_NAME_INFO_CACHE",
                    AiKnowledgeDocument.class.getAnnotation(TableName.class).value(),
                    AiKnowledgeChunk.class.getAnnotation(TableName.class).value());
                preserve(LambdaUtils.class, "COLUMN_CACHE_MAP", AiKnowledgeDocument.class.getName(), AiKnowledgeChunk.class.getName());
                preserve(GlobalConfigUtils.class, "GLOBAL_CONFIG", Integer.toHexString(configuration.hashCode()));
                MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
                TableInfoHelper.initTableInfo(assistant, AiKnowledgeDocument.class);
                TableInfoHelper.initTableInfo(assistant, AiKnowledgeChunk.class);
            } catch (Throwable failure) {
                try {
                    close();
                } catch (Throwable closing) {
                    failure.addSuppressed(closing);
                }
                throw new AssertionError("fixture MP元数据初始化失败", failure);
            }
        }

        /** 保存原对象引用；原来不存在的键在收尾删除，存在的键恢复到原TableInfo或列缓存对象。 */
        @SuppressWarnings("unchecked")
        private void preserve(Class<?> owner, String fieldName, Object... keys) throws ReflectiveOperationException {
            var field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            Map<Object, Object> cache = (Map<Object, Object>) field.get(null);
            for (Object key : keys) {
                Object previous = cache.get(key);
                restores.add(() -> {
                    if (previous == null) {
                        cache.remove(key);
                        assertFalse(cache.containsKey(key), "fixture新增缓存键必须删除：" + fieldName);
                    } else {
                        cache.put(key, previous);
                        assertTrue(previous == cache.get(key), "fixture缓存必须恢复原对象：" + fieldName);
                    }
                });
            }
        }

        /** 仅复原上述精确键；任何单项异常都不能跳过余下缓存的复原。 */
        @Override
        public void close() {
            closeFixtureResources("fixture MP元数据复原", restores);
        }
    }

    /** 立即登记已获得资源，保证后续安装失败时仍可撤销此前成功安装的静态桩。 */
    private static <T extends AutoCloseable> T own(List<AutoCloseable> resources, T resource) {
        resources.add(resource);
        return resource;
    }

    /** 实际逐项关闭后再聚合异常；清空只针对夹具自己的资源列表，不清空任何MP缓存。 */
    private static void closeFixtureResources(String label, List<AutoCloseable> resources) {
        List<Executable> failures = new ArrayList<>();
        for (int i = resources.size() - 1; i >= 0; i--) {
            try {
                resources.get(i).close();
            } catch (Throwable failure) {
                failures.add(() -> { throw new AssertionError(label, failure); });
            }
        }
        resources.clear();
        assertAll(label, failures);
    }

    /** 完整上下文快照区分请求内联恢复和异步 worker 清理，所有值均为合成 fixture。 */
    private record ContextSnapshot(String tenant, String aiTenant, Long user, String userName, Long session,
                                   Long model, String modelName, Long knowledge, Integer inputTokens,
                                   Integer outputTokens, Long tokenModel) {
        /** 当前线程的租户和全部 AI 字段，不访问登录记录、日志或业务数据。 */
        static ContextSnapshot capture() {
            return new ContextSnapshot(TenantHelper.getTenantId(), AiRequestContext.getTenantId(),
                AiRequestContext.getUserId(), AiRequestContext.getUserName(), AiRequestContext.getSessionId(),
                AiRequestContext.getModelId(), AiRequestContext.getModelName(), AiRequestContext.getKnowledgeId(),
                AiRequestContext.getLastInputTokens(), AiRequestContext.getLastOutputTokens(),
                AiRequestContext.getLastModelId());
        }
    }

    /** 业务Runnable不包装；只在真实before/afterExecute安装端口和观察状态，现有handler仍负责拒绝和回退。 */
    private class TestExecutor extends ThreadPoolExecutor {
        private final CountDownLatch serviceTasksFinished;
        private final Set<Runnable> harnessTasks = Collections.newSetFromMap(new ConcurrentHashMap<>());
        private final ConcurrentLinkedQueue<ContextSnapshot> restoredContexts = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        private final ThreadLocal<ExternalPorts> workerPorts = new ThreadLocal<>();
        private volatile Runnable afterServiceTask = () -> { };

        /** 线程工厂仅启动真实Worker，端口安装失败也由runWorker的正常退出机制处理，不留下虚假的workerCount。 */
        TestExecutor(int workers, int queueCapacity, int expectedServiceTasks, RejectedExecutionHandler handler) {
            super(workers, workers, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(queueCapacity), runnable -> {
                Thread worker = new Thread(runnable, "rag-executor-fixture-worker");
                worker.setDaemon(true);
                worker.setUncaughtExceptionHandler((thread, failure) -> workerFailures.add(failure));
                createdWorkers.add(worker);
                return worker;
            }, handler);
            serviceTasksFinished = new CountDownLatch(expectedServiceTasks);
        }

        /** 每个任务在所属worker内安装外部桩并绑定池内旧上下文，异常明确标为fixture问题。 */
        @Override
        protected void beforeExecute(Thread worker, Runnable task) {
            super.beforeExecute(worker, task);
            try {
                workerPorts.set(new ExternalPorts(ports.oss));
                TenantHelper.setDynamic(WORKER_TENANT);
                AiRequestContext.set("fixture-stale", 91L, "隔离旧用户", 92L, 93L, "隔离旧模型");
                AiRequestContext.setKnowledgeId(94L);
                AiRequestContext.setLastTokenUsage(95, 96, 97L);
                ports.workerThreads.add(worker);
            } catch (Throwable failure) {
                failures.add(new AssertionError("fixture worker端口准备失败", failure));
                closeWorkerPorts();
                if (!harnessTasks.remove(task)) {
                    serviceTasksFinished.countDown();
                }
                throw new AssertionError("fixture禁止在端口未隔离时执行知识任务", failure);
            }
        }

        /** 区分占用工作线程的测试检查点与业务任务，不替换业务Runnable。 */
        void executeHarness(Runnable task) {
            harnessTasks.add(task);
            execute(task);
        }

        /** 先观察业务finally后的状态，再关闭夹具作用域，禁止夹具自己的clear制造虚假的上下文恢复。 */
        @Override
        protected void afterExecute(Runnable task, Throwable failure) {
            boolean serviceTask = !harnessTasks.remove(task);
            try {
                if (failure != null) {
                    failures.add(failure);
                }
                if (serviceTask) {
                    restoredContexts.add(ContextSnapshot.capture());
                    afterServiceTask.run();
                }
            } catch (Throwable observationFailure) {
                failures.add(observationFailure);
            } finally {
                closeWorkerPorts();
                if (serviceTask) {
                    serviceTasksFinished.countDown();
                }
                super.afterExecute(task, failure);
            }
        }

        /** 本线程关闭本线程的Mockito作用域；某项清理失败不跳过其他静态桩关闭。 */
        private void closeWorkerPorts() {
            ExternalPorts scope = workerPorts.get();
            workerPorts.remove();
            ports.workerThreads.remove(Thread.currentThread());
            try {
                AiRequestContext.clear();
                if (scope != null) {
                    TenantHelper.clearDynamic();
                }
            } catch (Throwable failure) {
                failures.add(new AssertionError("fixture worker上下文清理失败", failure));
            }
            if (scope != null) {
                try {
                    scope.close();
                } catch (Throwable failure) {
                    failures.add(new AssertionError("fixture worker静态桩撤销失败", failure));
                }
            }
        }

        /** 完成信号来自真实afterExecute，且已撤销worker静态桩；不能用端口调用提前报告成功。 */
        void awaitServiceTasks() {
            await(serviceTasksFinished, "真实知识任务执行结束");
            assertNoWorkerFailure();
        }

        /** 检查的是业务finally后的快照，而不是夹具清理后的状态。 */
        void assertWorkerContextsRestored(int expected) {
            assertEquals(expected, restoredContexts.size());
            ContextSnapshot clean = new ContextSnapshot(WORKER_TENANT, null, null, null, null, null,
                null, null, null, null, null);
            restoredContexts.forEach(snapshot -> assertEquals(clean, snapshot));
            assertNoWorkerFailure();
        }

        /** 所有异步错误返回测试线程；带fixture前缀的安装、清理错误必须先于生产RED判定排查。 */
        void assertNoWorkerFailure() {
            if (!failures.isEmpty()) {
                throw new AssertionError("隔离worker出现异常，先核对fixture端口和收尾", failures.element());
            }
        }
    }

    /** 支持检查点的外部端口；文档、切片以临界区保存，向量以本地存储保存，不连接网络。 */
    private static class Ports {
        /** 只支持现有retry/rejection的四谓词纯AND形状；完整匹配而非扫描OR，不解释任何扩展SQL。 */
        private static final String CAS_PARAMETER = "#\\{ew\\.paramNameValuePairs\\.[A-Za-z_][A-Za-z0-9_]*\\}";
        private static final Pattern PURE_AND_CAS = Pattern.compile(
            "\\(\\s*id\\s*=\\s*" + CAS_PARAMETER
                + "\\s+AND\\s+tenant_id\\s*=\\s*" + CAS_PARAMETER
                + "\\s+AND\\s+knowledge_id\\s*=\\s*" + CAS_PARAMETER
                + "\\s+AND\\s+status\\s+IN\\s*\\(\\s*" + CAS_PARAMETER
                + "\\s*,\\s*" + CAS_PARAMETER + "\\s*\\)\\s*\\)", Pattern.CASE_INSENSITIVE);

        private final Object stateLock = new Object();
        private final AiKnowledgeDocument document = new AiKnowledgeDocument();
        private final Map<Long, AiKnowledgeChunk> chunkRows = new LinkedHashMap<>();
        private final AtomicInteger nextChunkId = new AtomicInteger(400);
        private final ConcurrentLinkedQueue<Integer> claimRows = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<String> statusTenants = new ConcurrentLinkedQueue<>();
        private final ConcurrentLinkedQueue<ContextSnapshot> deleteContexts = new ConcurrentLinkedQueue<>();
        private final Set<Thread> workerThreads = Collections.newSetFromMap(new ConcurrentHashMap<>());
        private final AtomicInteger rejectionWrites = new AtomicInteger();
        private final AtomicInteger failedWrites = new AtomicInteger();
        private final AtomicInteger completedWrites = new AtomicInteger();
        private final AtomicInteger vectorDeleteCalls = new AtomicInteger();
        private final AtomicInteger chunkDeletes = new AtomicInteger();
        private final AtomicInteger parses = new AtomicInteger();
        private final AtomicInteger vectorAdds = new AtomicInteger();
        private final DocumentParserService parser = mock(DocumentParserService.class);
        private final AiKnowledgeDocumentMapper documents = mock(AiKnowledgeDocumentMapper.class);
        private final AiKnowledgeChunkMapper chunks = mock(AiKnowledgeChunkMapper.class);
        private final IAiKnowledgeService knowledgeService = mock(IAiKnowledgeService.class);
        private final KnowledgeIndexRebuildService indexGate = mock(KnowledgeIndexRebuildService.class);
        private final OssClient oss = mock(OssClient.class);
        private final InMemoryEmbeddingStore<TextSegment> vectorRows = new InMemoryEmbeddingStore<>();
        private final Embedding embedding = Embedding.from(new float[]{1.0f, 0.0f});
        private final VectorStoreService vectors;
        private volatile CheckedAction beforeVectorDelete = () -> { };
        private volatile CheckedAction beforeClaim = () -> { };
        private volatile Consumer<AiKnowledgeDocument> afterWorkerRead = snapshot -> { };
        private volatile IntConsumer afterClaim = rows -> { };

        /** 使用真实服务的 Milvus 分支，缓存预装模拟客户端，故不会执行 SDK builder 或 RPC。 */
        @SuppressWarnings("unchecked")
        Ports() throws Exception {
            document.setId(DOCUMENT_ID);
            document.setTenantId(TENANT);
            document.setKnowledgeId(KNOWLEDGE_ID);
            document.setFileName("隔离规范.txt");
            document.setFilePath("fixture/isolated-document.txt");
            document.setStatus("3");
            document.setChunkCount(1);
            String oldVector = vectorRows.add(embedding, segment(OLD_CONTENT, DOCUMENT_ID));
            vectorRows.add(embedding, segment(OTHER_CONTENT, OTHER_DOCUMENT_ID));
            AiKnowledgeChunk old = new AiKnowledgeChunk();
            old.setId(301L);
            old.setTenantId(TENANT);
            old.setKnowledgeId(KNOWLEDGE_ID);
            old.setDocumentId(DOCUMENT_ID);
            old.setContent(OLD_CONTENT);
            old.setChunkIndex(0);
            old.setVectorId(oldVector);
            chunkRows.put(old.getId(), old);
            when(documents.selectById(DOCUMENT_ID)).thenAnswer(invocation -> {
                AiKnowledgeDocument snapshot = documentSnapshot();
                if (workerThreads.contains(Thread.currentThread())) {
                    afterWorkerRead.accept(snapshot);
                }
                return snapshot;
            });
            when(documents.updateById(any(AiKnowledgeDocument.class))).thenAnswer(invocation -> {
                AiKnowledgeDocument patch = invocation.getArgument(0);
                assertEquals(DOCUMENT_ID, patch.getId());
                synchronized (stateLock) {
                    applyPatch(patch);
                }
                return 1;
            });
            when(documents.update(org.mockito.ArgumentMatchers.<AiKnowledgeDocument>any(),
                org.mockito.ArgumentMatchers.<Wrapper<AiKnowledgeDocument>>any()))
                .thenAnswer(invocation -> conditionalUpdate(invocation.getArgument(0), invocation.getArgument(1)));
            when(chunks.delete(org.mockito.ArgumentMatchers.<Wrapper<AiKnowledgeChunk>>any())).thenAnswer(invocation -> {
                LambdaQueryWrapper<AiKnowledgeChunk> condition = invocation.getArgument(0);
                Object documentId = columnValue(condition.getSqlSegment(), "document_id", condition.getParamNameValuePairs());
                assertEquals(TENANT, TenantHelper.getTenantId());
                synchronized (stateLock) {
                    int before = chunkRows.size();
                    chunkRows.values().removeIf(chunk -> Objects.equals(documentId, chunk.getDocumentId()));
                    chunkDeletes.incrementAndGet();
                    return before - chunkRows.size();
                }
            });
            when(chunks.insert(any(AiKnowledgeChunk.class))).thenAnswer(invocation -> {
                AiKnowledgeChunk chunk = invocation.getArgument(0);
                chunk.setId((long) nextChunkId.incrementAndGet());
                synchronized (stateLock) {
                    chunkRows.put(chunk.getId(), copyChunk(chunk));
                }
                return 1;
            });
            when(chunks.updateById(any(AiKnowledgeChunk.class))).thenAnswer(invocation -> {
                AiKnowledgeChunk patch = invocation.getArgument(0);
                synchronized (stateLock) {
                    AiKnowledgeChunk persisted = chunkRows.get(patch.getId());
                    assertNotNull(persisted, "向量回写必须对应真实已保存的切片替身行");
                    persisted.setVectorId(patch.getVectorId());
                }
                return 1;
            });
            AiKnowledgeVo knowledge = new AiKnowledgeVo();
            knowledge.setId(KNOWLEDGE_ID);
            knowledge.setTenantId(TENANT);
            knowledge.setTextBlockSize(64);
            knowledge.setOverlapChar(0);
            knowledge.setEmbeddingModel("fixture-embedding");
            when(knowledgeService.selectById(KNOWLEDGE_ID)).thenReturn(knowledge);
            when(parser.parse(any(), anyString())).thenAnswer(invocation -> {
                parses.incrementAndGet();
                return NEW_CONTENT;
            });
            when(oss.getObjectContent(anyString())).thenAnswer(invocation ->
                new ByteArrayInputStream(NEW_CONTENT.getBytes(StandardCharsets.UTF_8)));
            EmbeddingService embeddings = mock(EmbeddingService.class);
            EmbeddingModel model = mock(EmbeddingModel.class);
            when(embeddings.getEmbeddingModel("fixture-embedding")).thenReturn(model);
            when(model.embedAll(anyList())).thenAnswer(invocation -> {
                List<TextSegment> segments = invocation.getArgument(0);
                return new Response<>(segments.stream().map(segment -> embedding).toList());
            });
            AiKnowledgeMapper knowledgeMapper = mock(AiKnowledgeMapper.class);
            AiKnowledge storedKnowledge = new AiKnowledge();
            storedKnowledge.setId(KNOWLEDGE_ID);
            storedKnowledge.setVectorStore("milvus");
            when(knowledgeMapper.selectById(KNOWLEDGE_ID)).thenReturn(storedKnowledge);
            vectors = new VectorStoreService(embeddings, chunks, knowledgeMapper, mock(AiModelMapper.class),
                new AiProperties());
            EmbeddingStore<TextSegment> simulatedMilvus = mock(EmbeddingStore.class);
            doAnswer(invocation -> {
                vectorDeleteCalls.incrementAndGet();
                deleteContexts.add(ContextSnapshot.capture());
                beforeVectorDelete.run();
                vectorRows.removeAll(invocation.<Filter>getArgument(0));
                return null;
            }).when(simulatedMilvus).removeAll(any(Filter.class));
            when(simulatedMilvus.addAll(anyList(), anyList())).thenAnswer(invocation -> {
                vectorAdds.incrementAndGet();
                return vectorRows.addAll(invocation.getArgument(0), invocation.getArgument(1));
            });
            var field = VectorStoreService.class.getDeclaredField("milvusStores");
            field.setAccessible(true);
            Cache<Long, EmbeddingStore<TextSegment>> cache =
                (Cache<Long, EmbeddingStore<TextSegment>>) field.get(vectors);
            cache.put(KNOWLEDGE_ID, simulatedMilvus);
        }

        /** 消费真实 CAS 绑定条件，在同一临界区比较和更新，绝不把所有 update 无条件桩为成功。 */
        private int conditionalUpdate(AiKnowledgeDocument patch, LambdaUpdateWrapper<AiKnowledgeDocument> condition)
                throws Exception {
            String sql = condition.getSqlSegment();
            if (!PURE_AND_CAS.matcher(sql).matches()) {
                throw new AssertionError(UNSUPPORTED_CAS_SHAPE);
            }
            if (patch == null) {
                beforeClaim.run();
            }
            Map<String, Object> parameters = condition.getParamNameValuePairs();
            Object id = columnValue(sql, "id", parameters);
            Object tenant = columnValue(sql, "tenant_id", parameters);
            Object knowledge = columnValue(sql, "knowledge_id", parameters);
            var statuses = Pattern.compile("\\bstatus\\s+IN\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE).matcher(sql);
            assertTrue(statuses.find(), "状态CAS必须显式限定可认领状态");
            List<Object> allowed = Pattern.compile("#\\{ew\\.paramNameValuePairs\\.([^}]+)\\}")
                .matcher(statuses.group(1)).results().map(match -> parameters.get(match.group(1))).toList();
            assertEquals(List.of("0", "3"), allowed, "此矩阵只通过真实retry入口提交任务");
            if (patch == null) {
                assertEquals("1", columnValue(condition.getSqlSet(), "status", parameters));
            }
            int rows;
            synchronized (stateLock) {
                boolean matches = Objects.equals(id, document.getId()) && Objects.equals(tenant, document.getTenantId())
                    && Objects.equals(knowledge, document.getKnowledgeId()) && allowed.contains(document.getStatus());
                rows = matches ? 1 : 0;
                if (patch == null) {
                    if (matches) {
                        document.setStatus("1");
                    }
                    claimRows.add(rows);
                } else if (matches) {
                    rejectionWrites.incrementAndGet();
                    applyPatch(patch);
                }
            }
            if (patch == null) {
                afterClaim.accept(rows);
            }
            return rows;
        }

        /** 模拟非空列更新；失败信息不凭 fixture 自行清空，避免掩盖实际 Mapper 的更新策略。 */
        private void applyPatch(AiKnowledgeDocument patch) {
            assertEquals(TENANT, TenantHelper.getTenantId());
            statusTenants.add(TenantHelper.getTenantId());
            if (patch.getStatus() != null) {
                document.setStatus(patch.getStatus());
                if ("2".equals(patch.getStatus())) {
                    completedWrites.incrementAndGet();
                } else if ("3".equals(patch.getStatus())) {
                    failedWrites.incrementAndGet();
                }
            }
            if (patch.getChunkCount() != null) {
                document.setChunkCount(patch.getChunkCount());
            }
            if (patch.getErrorMessage() != null) {
                document.setErrorMessage(patch.getErrorMessage());
            }
        }

        /** 返回独立快照，不能让读取对象随后随另一个 worker 的持久化修改自动变新。 */
        private AiKnowledgeDocument documentSnapshot() {
            synchronized (stateLock) {
                AiKnowledgeDocument snapshot = new AiKnowledgeDocument();
                BeanUtils.copyProperties(document, snapshot);
                return snapshot;
            }
        }

        /** 用保存内容和来源关联验证最终状态，不以 verify 调用发生与否代替实际产出。 */
        private List<AiKnowledgeChunk> documentChunks() {
            synchronized (stateLock) {
                return chunkRows.values().stream().filter(chunk -> chunk.getDocumentId().equals(DOCUMENT_ID))
                    .map(Ports::copyChunk).toList();
            }
        }

        /** 读取本地向量端口实际保存的数据，其他文档必须始终保留。 */
        private List<String> vectorContents() {
            return vectorRows.search(EmbeddingSearchRequest.builder().queryEmbedding(embedding)
                    .maxResults(8).minScore(0.0).build()).matches().stream()
                .map(match -> match.embedded().text()).sorted().toList();
        }

        /** 仅使用小型合成文本和文档元数据，复用真实文档过滤器执行删除。 */
        private static TextSegment segment(String content, long documentId) {
            return TextSegment.from(content, new dev.langchain4j.data.document.Metadata()
                .put("documentId", Long.toString(documentId)));
        }

        /** 模拟 Mapper 持久化值与调用方对象分离，防止引用共享制造虚假的向量回写成功。 */
        private static AiKnowledgeChunk copyChunk(AiKnowledgeChunk source) {
            AiKnowledgeChunk copy = new AiKnowledgeChunk();
            BeanUtils.copyProperties(source, copy);
            return copy;
        }

        /** 按真实 SQL 占位符读取绑定值，不假设 MPGENVAL 编号或放宽 CAS 条件。 */
        private static Object columnValue(String sql, String column, Map<String, Object> parameters) {
            var match = Pattern.compile("\\b" + column + "\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.([^}]+)\\}",
                Pattern.CASE_INSENSITIVE).matcher(sql);
            assertTrue(match.find(), "真实条件缺少绑定列：" + column);
            return parameters.get(match.group(1));
        }
    }

    /** 只让模拟外部端口在确定性检查点抛出异常或响应中断，不模拟生产服务的执行逻辑。 */
    @FunctionalInterface
    private interface CheckedAction {
        /** 执行测试检查点；允许携带合成异常进入真实服务的失败路径。 */
        void run() throws Exception;
    }
}
