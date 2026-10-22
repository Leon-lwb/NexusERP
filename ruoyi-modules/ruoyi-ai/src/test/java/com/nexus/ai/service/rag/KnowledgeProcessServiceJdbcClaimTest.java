package com.nexus.ai.service.rag;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import com.nexus.common.ai.domain.entity.AiKnowledgeChunk;
import com.nexus.common.ai.domain.entity.AiKnowledgeDocument;
import com.nexus.common.ai.domain.vo.AiKnowledgeVo;
import com.nexus.common.ai.mapper.AiKnowledgeChunkMapper;
import com.nexus.common.ai.mapper.AiKnowledgeDocumentMapper;
import com.nexus.common.ai.service.IAiKnowledgeService;
import com.nexus.common.oss.core.OssClient;
import com.nexus.common.oss.factory.OssFactory;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 知识处理认领的真实 JDBC 行为刻画：真实服务构造条件，生产 Mapper 注入 SQL，H2 决定影响行数。
 * <p>每例只使用一个独立内存库、两个自动提交 SqlSession/连接和一个最多两线程、队列容量为二的执行器。
 * 通过反射直接进入真实 processDocument，以隔离认领本身；不验收上传、认证或异步上下文传播。
 * 观察代理保留服务传来的同一 Wrapper，交给所属会话的生产 Mapper，Executor 拦截器只记录并核对 SQL，
 * 不改条件、参数或返回行数。其他 Mapper、OSS、解析和向量端口为本地替身，分段仍使用真实实现。</p>
 * <p>刻意不安装生产租户插件，单独验证认领显式 WHERE 的租户/知识库约束；来源只在快照读取后、认领前变化，
 * 成功认领后不变更归属，不借缺失插件制造 updateById 的生产缺陷。不替换静态 Spring 全局，
 * 不创建连接池、Embedding/Milvus SDK 客户端，不访问真实数据库、HTTP 或其他服务。
 * H2 仅证明实际 JDBC 条件和原子竞争，不代表 MySQL 事务、网络故障或生产租户拦截器验收；
 * 既有行为通过属于 characterization，不宣称生产 RED/GREEN。</p>
 */
@Isolated("临时使用 MP 元数据，按精确键及原编号生成器复原，禁止其他测试并发改写")
class KnowledgeProcessServiceJdbcClaimTest {

    private static final String TENANT = "000000";
    private static final long KNOWLEDGE_ID = 101L;
    private static final long DOCUMENT_ID = 201L;
    private static final long OTHER_DOCUMENT_ID = 202L;
    private static final String FILE_NAME = "隔离认领.txt";
    private static final String FILE_PATH = "fixture/jdbc-claim.txt";
    private static final String OLD_ERROR = "隔离旧失败记录";
    private static final String OLD_SOURCE = "目标文档旧来源";
    private static final String OTHER_SOURCE = "其他文档来源";
    private static final String NEW_SOURCE = "目标文档新来源";
    private static final String CONTENT = "隔离 JDBC 认领内容";
    private static final long WAIT_SECONDS = 10L;
    private static final String CLAIM_STATEMENT = AiKnowledgeDocumentMapper.class.getName() + ".update";
    private static final Method PROCESS_DOCUMENT = processMethod();

    /** 两个真实读取均取得失败快照；迟到者在前者处理期间或完成后认领，必须由 SQL 返回零行。 */
    @ParameterizedTest
    @ValueSource(strings = {"1", "2"})
    @DisplayName("同一失败快照：前者一行，状态1/2下迟到者零行且不清理、不伪成功")
    void lateWorkerCannotClaimTheSameFailedSnapshot(String lateStatus) throws Exception {
        try (Fixture fixture = new Fixture("3")) {
            Worker first = fixture.worker(0, true);
            Worker late = fixture.worker(1, true);
            if ("1".equals(lateStatus)) {
                first.afterClaimPermit = fixture.gate(1);
            }
            Future<?> firstTask = fixture.start(first);
            Future<?> lateTask = fixture.start(late);
            await(first.snapshotRead, "前者失败快照");
            await(late.snapshotRead, "迟到者失败快照");
            assertNotSame(first.snapshot, late.snapshot, "必须由各自 JDBC 查询返回独立快照");
            assertEquals("3", first.snapshot.getStatus());
            assertEquals("3", late.snapshot.getStatus());

            first.claimPermit.countDown();
            await(first.claimExecuted, "前者 SQL 认领");
            assertEquals(1, first.claim.rows());
            if ("2".equals(lateStatus)) {
                finish(firstTask);
            }
            late.claimPermit.countDown();
            finish(lateTask);
            assertEquals(lateStatus, late.currentBeforeClaim.getStatus(), "迟到者观察的状态必须来自真实数据库");
            assertNoProcessing(late);
            assertEquals(0, late.claim.rows());
            assertNotSame(first.claim.wrapper(), late.claim.wrapper());

            first.afterClaimPermit.countDown();
            finish(firstTask);
            assertSuccessful(first);
            assertEquals("2", fixture.document().getStatus());
            assertEquals(Set.of(NEW_SOURCE, OTHER_SOURCE), fixture.chunkSources);
            assertEquals(Set.of(NEW_SOURCE, OTHER_SOURCE), fixture.vectorSources);
            fixture.assertOtherDocumentUnchanged();
        }
    }

    /** 变化发生在真实快照读取之后；禁止认领旧归属，更不能删除旧片段/向量或覆盖别人写入的状态。 */
    @ParameterizedTest
    @EnumSource(SourceChange.class)
    @DisplayName("读取与认领之间 tenant/knowledge/status 变化：真实 SQL 零行，旧来源不动")
    void changedSourceAfterSnapshotCannotBeClaimed(SourceChange change) throws Exception {
        try (Fixture fixture = new Fixture("3")) {
            Worker worker = fixture.worker(0, true);
            Future<?> task = fixture.start(worker);
            await(worker.snapshotRead, "变化前真实快照");
            assertEquals(TENANT, worker.snapshot.getTenantId());
            assertEquals(KNOWLEDGE_ID, worker.snapshot.getKnowledgeId());
            assertEquals("3", worker.snapshot.getStatus());

            AiKnowledgeDocument patch = new AiKnowledgeDocument();
            patch.setId(DOCUMENT_ID);
            switch (change) {
                case TENANT -> patch.setTenantId("000001");
                case KNOWLEDGE -> patch.setKnowledgeId(102L);
                case PROCESSING -> patch.setStatus("1");
                case COMPLETED -> patch.setStatus("2");
            }
            assertEquals(1, fixture.mapper(1).updateById(patch), "第二会话提交隔离库变化，不手写认领 WHERE");
            AiKnowledgeDocument changed = fixture.document();
            worker.claimPermit.countDown();
            finish(task);

            assertEquals(0, worker.claim.rows());
            assertNoProcessing(worker);
            assertEquals(changed, fixture.document(), "零行认领不得回写失败或伪成功，也不得覆盖来源变化");
            assertEquals(Set.of(OLD_SOURCE, OTHER_SOURCE), fixture.chunkSources);
            assertEquals(Set.of(OLD_SOURCE, OTHER_SOURCE), fixture.vectorSources);
            fixture.assertOtherDocumentUnchanged();
        }
    }

    /** 直接进入工作方法以刻画两种 SQL 白名单，不替代 retryProcess 的入口状态守卫。 */
    @ParameterizedTest
    @CsvSource({"true, 0, 1", "true, 3, 1", "false, 0, 1", "false, 3, 0"})
    @DisplayName("真实服务 SQL：重试只允许0/3，首次只允许0")
    void claimStatusAllowListComesFromTheService(boolean retry, String status, int expectedRows) throws Exception {
        try (Fixture fixture = new Fixture(status)) {
            Worker worker = fixture.worker(0, retry);
            Future<?> task = fixture.start(worker);
            await(worker.snapshotRead, "状态白名单快照");
            assertEquals(status, worker.snapshot.getStatus());
            worker.claimPermit.countDown();
            finish(task);

            assertEquals(expectedRows, worker.claim.rows());
            if (expectedRows == 0) {
                assertNoProcessing(worker);
                assertEquals(status, fixture.document().getStatus());
                assertEquals(OLD_ERROR, fixture.document().getErrorMessage());
                assertEquals(Set.of(OLD_SOURCE, OTHER_SOURCE), fixture.chunkSources);
                assertEquals(Set.of(OLD_SOURCE, OTHER_SOURCE), fixture.vectorSources);
            } else {
                assertSuccessful(worker);
                assertEquals("2", fixture.document().getStatus());
                Set<String> expectedSources = retry ? Set.of(NEW_SOURCE, OTHER_SOURCE)
                    : Set.of(OLD_SOURCE, NEW_SOURCE, OTHER_SOURCE);
                assertEquals(expectedSources, fixture.chunkSources);
                assertEquals(expectedSources, fixture.vectorSources);
            }
            fixture.assertOtherDocumentUnchanged();
        }
    }

    /** 零行返回必须在配置读取、清理、下载、解析及完成回写之前结束，不能只断言最终状态。 */
    private static void assertNoProcessing(Worker worker) {
        assertNotNull(worker.claim, "必须实际执行生产 Mapper 的认领 SQL");
        assertEquals(0, worker.claim.rows());
        assertTrue(worker.statusWrites.isEmpty(), "未认领不得调用 updateById 写解析中、成功或失败");
        verifyNoInteractions(worker.knowledge, worker.indexGate, worker.chunks, worker.vectors, worker.parser, worker.oss);
    }

    /** 成功路径也须走完真实方法，避免夹具的异常被服务 catch 后误算作一次成功认领。 */
    private static void assertSuccessful(Worker worker) {
        assertEquals(1, worker.claim.rows());
        assertEquals(List.of("1", "2"), worker.statusWrites, "须真实完成处理，不能落入异常失败回写");
        verify(worker.parser).parse(any(InputStream.class), eq(FILE_NAME));
        verify(worker.chunks).insert(any(AiKnowledgeChunk.class));
        verify(worker.vectors).storeChunks(eq(KNOWLEDGE_ID), anyList(), eq("fixture-embedding"));
        verify(worker.vectors, times(worker.retry ? 1 : 0)).deleteByDocumentId(DOCUMENT_ID, KNOWLEDGE_ID);
        verify(worker.chunks, times(worker.retry ? 1 : 0))
                .delete(ArgumentMatchers.<Wrapper<AiKnowledgeChunk>>any());
    }

    private enum SourceChange { TENANT, KNOWLEDGE, PROCESSING, COMPLETED }

    /** 记录真实服务对象引用、实际注入语句、实际绑定参数和 JDBC 返回值，不解释条件以模拟 CAS。 */
    private record Claim(LambdaUpdateWrapper<AiKnowledgeDocument> wrapper, String sql, List<Object> parameters, int rows) { }

    /** 不启动 Spring；仅反射调用已有私有工作方法，不复制业务实现，不构造替代认领条件。 */
    private static Method processMethod() {
        try {
            Method method = KnowledgeProcessService.class.getDeclaredMethod("processDocument",
                Long.class, Long.class, String.class, boolean.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("真实知识处理方法签名已变化，须复核测试边界", failure);
        }
    }

    /** 所有检查点有界等待，以 latch 固定交错，不以 sleep 或调度概率冒充并发证据。 */
    private static void await(CountDownLatch latch, String label) throws InterruptedException {
        assertTrue(latch.await(WAIT_SECONDS, TimeUnit.SECONDS), "隔离检查点超时：" + label);
    }

    private static void finish(Future<?> task) throws Exception {
        task.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    /** 反射代理只观察方法，真实调用的异常原样传播；不伪造成功行数。 */
    private static Object invoke(Method method, Object target, Object... arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    /** 每个 worker 独占一个真实会话；外部端口及 OSS 静态替身只在该工作线程生命周期内安装。 */
    private static final class Worker {
        private final Fixture fixture;
        private final AiKnowledgeDocumentMapper realMapper;
        private final boolean retry;
        private final CountDownLatch snapshotRead = new CountDownLatch(1);
        private final CountDownLatch claimExecuted = new CountDownLatch(1);
        private final CountDownLatch claimPermit;
        private CountDownLatch afterClaimPermit;
        private final List<String> statusWrites = new CopyOnWriteArrayList<>();
        private final DocumentParserService parser = mock(DocumentParserService.class);
        private final AiKnowledgeChunkMapper chunks = mock(AiKnowledgeChunkMapper.class);
        private final VectorStoreService vectors = mock(VectorStoreService.class);
        private final IAiKnowledgeService knowledge = mock(IAiKnowledgeService.class);
        private final KnowledgeIndexRebuildService indexGate = mock(KnowledgeIndexRebuildService.class);
        private final OssClient oss = mock(OssClient.class);
        private final KnowledgeProcessService service;
        private volatile AiKnowledgeDocument snapshot;
        private volatile AiKnowledgeDocument currentBeforeClaim;
        private volatile LambdaUpdateWrapper<AiKnowledgeDocument> sourceWrapper;
        private volatile Claim claim;

        Worker(Fixture fixture, int slot, boolean retry) throws Exception {
            this.fixture = fixture;
            this.realMapper = fixture.mapper(slot);
            this.retry = retry;
            claimPermit = fixture.gate(1);
            afterClaimPermit = fixture.gate(0);
            AiKnowledgeVo config = new AiKnowledgeVo();
            config.setId(KNOWLEDGE_ID);
            config.setTenantId(TENANT);
            config.setTextBlockSize(100);
            config.setOverlapChar(0);
            config.setSeparator("#");
            config.setEmbeddingModel("fixture-embedding");
            when(knowledge.selectById(KNOWLEDGE_ID)).thenReturn(config);
            when(oss.getObjectContent(FILE_PATH)).thenAnswer(call ->
                new ByteArrayInputStream(CONTENT.getBytes(StandardCharsets.UTF_8)));
            when(parser.parse(any(InputStream.class), eq(FILE_NAME))).thenReturn(CONTENT);
            when(chunks.delete(ArgumentMatchers.<Wrapper<AiKnowledgeChunk>>any()))
                    .thenAnswer(
                            call -> {
                                fixture.chunkSources.remove(OLD_SOURCE);
                                return 1;
                            });
            when(chunks.insert(any(AiKnowledgeChunk.class))).thenAnswer(call -> {
                AiKnowledgeChunk chunk = call.getArgument(0);
                assertEquals(TENANT, chunk.getTenantId());
                assertEquals(DOCUMENT_ID, chunk.getDocumentId());
                assertEquals(KNOWLEDGE_ID, chunk.getKnowledgeId());
                chunk.setId(301L);
                fixture.chunkSources.add(NEW_SOURCE);
                return 1;
            });
            doAnswer(call -> {
                assertEquals(DOCUMENT_ID, (Long) call.getArgument(0));
                assertEquals(KNOWLEDGE_ID, (Long) call.getArgument(1));
                fixture.vectorSources.remove(OLD_SOURCE);
                return null;
            }).when(vectors).deleteByDocumentId(DOCUMENT_ID, KNOWLEDGE_ID);
            when(vectors.storeChunks(eq(KNOWLEDGE_ID), anyList(), eq("fixture-embedding"))).thenAnswer(call -> {
                fixture.vectorSources.add(NEW_SOURCE);
                return List.of("fixture-vector-301");
            });
            service = new KnowledgeProcessService(parser, new ChunkingService(), vectors, observedMapper(), chunks,
                knowledge, indexGate, task -> { throw new AssertionError("本测试只进入真实工作方法，不应再次提交任务"); });
        }

        /** selectById/update/updateById 均委托 production Mapper；只在认领前后加检查点与观察。 */
        @SuppressWarnings("unchecked")
        private AiKnowledgeDocumentMapper observedMapper() {
            return (AiKnowledgeDocumentMapper) Proxy.newProxyInstance(AiKnowledgeDocumentMapper.class.getClassLoader(),
                new Class<?>[]{AiKnowledgeDocumentMapper.class}, (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return invoke(method, realMapper, arguments);
                    }
                    switch (method.getName()) {
                        case "selectById" -> {
                            assertNull(snapshot, "服务快照读取结构变化，须重新复核夹具");
                            snapshot = (AiKnowledgeDocument) invoke(method, realMapper, arguments);
                            assertNotNull(snapshot);
                            snapshotRead.countDown();
                            return snapshot;
                        }
                        case "update" -> {
                            assertEquals(2, arguments.length, "只接受真实两参数认领 update");
                            assertNull(arguments[0], "认领只能由服务 Wrapper SET 更新，不接受实体补写");
                            assertNull(sourceWrapper, "每 worker 只允许一次认领");
                            sourceWrapper = (LambdaUpdateWrapper<AiKnowledgeDocument>)
                                assertInstanceOf(LambdaUpdateWrapper.class, arguments[1]);
                            await(claimPermit, "服务 Wrapper 已捕获、JDBC 认领获准");
                            currentBeforeClaim = realMapper.selectById(DOCUMENT_ID);
                            Object rows = invoke(method, realMapper, arguments);
                            assertNotNull(claim, "必须经过真实 MP 注入 update 语句");
                            assertEquals(claim.rows(), rows);
                            claimExecuted.countDown();
                            await(afterClaimPermit, "认领返回后的受控暂停");
                            return rows;
                        }
                        case "updateById" -> {
                            AiKnowledgeDocument patch = (AiKnowledgeDocument) arguments[0];
                            statusWrites.add(patch.getStatus());
                            Object rows = invoke(method, realMapper, arguments);
                            assertEquals(1, rows, "成功认领后归属不变，真实状态回写须为一行");
                            return rows;
                        }
                        default -> throw new AssertionError("不支持的文档 Mapper 调用，不能扩大夹具解释范围：" + method);
                    }
                });
        }

        /** 仅安装 OSS 线程内替身，不调用 TenantHelper/SpringUtils 或登录/缓存外部端口。 */
        private void process() {
            fixture.currentWorker.set(this);
            try (MockedStatic<OssFactory> factory = mockStatic(OssFactory.class)) {
                factory.when(OssFactory::instance).thenReturn(oss);
                invoke(PROCESS_DOCUMENT, service, DOCUMENT_ID, KNOWLEDGE_ID, TENANT, retry);
            } catch (Throwable failure) {
                throw new AssertionError("隔离 worker 执行失败，不能算作生产认领成功", failure);
            } finally {
                fixture.currentWorker.remove();
            }
        }
    }

    /** Executor.update 接收到的正是 Mapper 参数；仅接受现行精确纯 AND 形状，未知结构直接失败。 */
    @Intercepts(@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}))
    private static final class ClaimSqlCapture implements Interceptor {
        private final Fixture fixture;

        ClaimSqlCapture(Fixture fixture) { this.fixture = fixture; }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            if (!CLAIM_STATEMENT.equals(statement.getId())) {
                return invocation.proceed();
            }
            Worker worker = fixture.currentWorker.get();
            assertNotNull(worker, "认领必须在所属 worker 会话执行");
            assertNull(worker.claim, "禁止重复执行认领语句");
            Map<?, ?> arguments = (Map<?, ?>) invocation.getArgs()[1];
            assertSame(worker.sourceWrapper, arguments.get("ew"), "必须执行服务传来的同一个 Wrapper，不能重建");
            assertNull(arguments.get("et"));
            BoundSql bound = statement.getBoundSql(arguments);
            String sql = bound.getSql().replaceAll("\\s+", " ").trim();
            String statusParameters = worker.retry ? "\\?\\s*,\\s*\\?" : "\\?";
            String expectedSql = "UPDATE ai_knowledge_document SET status\\s*=\\s*\\? WHERE \\(id\\s*=\\s*\\?"
                + " AND tenant_id\\s*=\\s*\\? AND knowledge_id\\s*=\\s*\\? AND status IN \\(" + statusParameters + "\\)\\)";
            assertTrue(Pattern.compile(expectedSql, Pattern.CASE_INSENSITIVE).matcher(sql).matches(),
                "仅允许现行认领 SQL，不宽松解释 OR/额外条件/扩展结构：" + sql);
            List<Object> parameters = new ArrayList<>();
            var meta = statement.getConfiguration().newMetaObject(arguments);
            bound.getParameterMappings().forEach(mapping -> parameters.add(bound.hasAdditionalParameter(mapping.getProperty())
                ? bound.getAdditionalParameter(mapping.getProperty()) : meta.getValue(mapping.getProperty())));
            List<Object> expectedParameters = new ArrayList<>(List.of("1", DOCUMENT_ID, TENANT, KNOWLEDGE_ID, "0"));
            if (worker.retry) expectedParameters.add("3");
            assertEquals(expectedParameters, parameters, "绑定值也必须来自真实服务，保留租户前导零");
            Object rows = invocation.proceed();
            worker.claim = new Claim(worker.sourceWrapper, sql, List.copyOf(parameters), (Integer) rows);
            return rows;
        }
    }

    /** 非池化 H2 数据源；两个打开的会话自身保活，最后一个连接关闭后独立内存库自动消失。 */
    private static final class Fixture implements AutoCloseable {
        private final List<AutoCloseable> resources = new ArrayList<>();
        private final List<CountDownLatch> gates = new ArrayList<>();
        private final List<Future<?>> tasks = new ArrayList<>();
        private final List<Thread> threads = new CopyOnWriteArrayList<>();
        private final ThreadLocal<Worker> currentWorker = new ThreadLocal<>();
        private final SqlSession[] sessions = new SqlSession[2];
        private final Connection[] connections = new Connection[2];
        private final Set<String> chunkSources = ConcurrentHashMap.newKeySet();
        private final Set<String> vectorSources = ConcurrentHashMap.newKeySet();
        private ThreadPoolExecutor pool;
        private boolean closed;

        Fixture(String initialStatus) throws Exception {
            try {
                MybatisConfiguration configuration = new MybatisConfiguration();
                resources.add(new MetadataScope(configuration));
                JdbcDataSource source = new JdbcDataSource();
                source.setURL("jdbc:h2:mem:knowledge_claim_" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=0;LOCK_TIMEOUT=3000");
                source.setUser("sa");
                source.setPassword("");
                configuration.setEnvironment(new Environment("isolated-knowledge-claim", new JdbcTransactionFactory(), source));
                configuration.setMapUnderscoreToCamelCase(true);
                configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
                configuration.setCacheEnabled(false);
                configuration.setDefaultStatementTimeout(3);
                configuration.setLogImpl(NoLoggingImpl.class);
                GlobalConfig global = new GlobalConfig().setDbConfig(new GlobalConfig.DbConfig())
                    .setSqlInjector(new DefaultSqlInjector()).setEnableSqlRunner(false)
                    .setIdentifierGenerator(entity -> { throw new AssertionError("隔离数据必须显式指定 ID，禁止默认生成器探测网络"); });
                GlobalConfigUtils.setGlobalConfig(configuration, global);
                configuration.addInterceptor(new ClaimSqlCapture(this));
                configuration.addMapper(AiKnowledgeDocumentMapper.class);
                var factory = new MybatisSqlSessionFactoryBuilder().build(configuration);
                for (int i = 0; i < sessions.length; i++) {
                    final int slot = i;
                    SqlSession session = factory.openSession(true);
                    sessions[slot] = session;
                    resources.add(() -> {
                        try {
                            session.close();
                        } finally {
                            if (connections[slot] != null) {
                                connections[slot].close();
                                assertTrue(connections[slot].isClosed(), "真实 JDBC 连接必须关闭");
                            }
                        }
                    });
                    connections[slot] = session.getConnection();
                    assertTrue(connections[slot].getAutoCommit());
                }
                assertNotSame(sessions[0], sessions[1]);
                assertNotSame(connections[0], connections[1]);
                try (var statement = connections[0].createStatement()) {
                    statement.execute("CREATE TABLE ai_knowledge_document (id BIGINT PRIMARY KEY, tenant_id VARCHAR(20), "
                        + "knowledge_id BIGINT, file_name VARCHAR(200), file_path VARCHAR(400), file_size BIGINT, "
                        + "chunk_count INT, status VARCHAR(1), error_message VARCHAR(500), create_dept BIGINT, "
                        + "create_by BIGINT, create_time TIMESTAMP, update_by BIGINT, update_time TIMESTAMP)");
                }
                seed(DOCUMENT_ID, initialStatus, 1);
                seed(OTHER_DOCUMENT_ID, "3", 7);
                chunkSources.addAll(Set.of(OLD_SOURCE, OTHER_SOURCE));
                vectorSources.addAll(Set.of(OLD_SOURCE, OTHER_SOURCE));
                pool = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(2), task -> {
                    Thread worker = new Thread(task, "rag-jdbc-claim-" + threads.size());
                    worker.setDaemon(true);
                    threads.add(worker);
                    return worker;
                }, new ThreadPoolExecutor.AbortPolicy());
            } catch (Throwable failure) {
                try { close(); } catch (Throwable closing) { failure.addSuppressed(closing); }
                throw new AssertionError("隔离 H2/真实 Mapper 夹具初始化失败", failure);
            }
        }

        private void seed(long id, String status, int chunkCount) {
            AiKnowledgeDocument document = new AiKnowledgeDocument();
            document.setId(id);
            document.setTenantId(TENANT);
            document.setKnowledgeId(KNOWLEDGE_ID);
            document.setFileName(FILE_NAME);
            document.setFilePath(FILE_PATH);
            document.setFileSize(32L);
            document.setStatus(status);
            document.setChunkCount(chunkCount);
            document.setErrorMessage(OLD_ERROR);
            assertEquals(1, mapper(0).insert(document));
        }

        private AiKnowledgeDocumentMapper mapper(int slot) { return sessions[slot].getMapper(AiKnowledgeDocumentMapper.class); }
        private AiKnowledgeDocument document() { return mapper(1).selectById(DOCUMENT_ID); }
        private Worker worker(int slot, boolean retry) throws Exception { return new Worker(this, slot, retry); }
        private CountDownLatch gate(int count) {
            CountDownLatch gate = new CountDownLatch(count);
            gates.add(gate);
            return gate;
        }
        private Future<?> start(Worker worker) {
            Future<?> task = pool.submit(worker::process);
            tasks.add(task);
            return task;
        }

        /** 同租户、同知识库、同失败状态的邻居须保留，实际行数和此断言共同约束 id 谓词。 */
        private void assertOtherDocumentUnchanged() {
            AiKnowledgeDocument other = mapper(1).selectById(OTHER_DOCUMENT_ID);
            assertEquals(TENANT, other.getTenantId());
            assertEquals(KNOWLEDGE_ID, other.getKnowledgeId());
            assertEquals("3", other.getStatus());
            assertEquals(7, other.getChunkCount());
            assertEquals(OLD_ERROR, other.getErrorMessage());
        }

        /** 先释放检查点、正常关闭池；降级中断也必须报告，最后逆序关闭两会话和精确元数据快照。 */
        @Override
        public void close() {
            if (closed) return;
            closed = true;
            List<Executable> checks = new ArrayList<>();
            boolean interrupted = false;
            gates.forEach(CountDownLatch::countDown);
            if (pool != null) {
                pool.shutdown();
                try {
                    boolean graceful = pool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
                    checks.add(() -> assertTrue(graceful, "检查点释放后须正常退出，不能靠清理中断掩盖卡死"));
                    if (!graceful) {
                        pool.shutdownNow();
                        boolean terminated = pool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
                        checks.add(() -> assertTrue(terminated, "两线程执行器未在有界清理内退出"));
                    }
                } catch (InterruptedException failure) {
                    interrupted = true;
                    pool.shutdownNow();
                    checks.add(() -> { throw new AssertionError("隔离执行器关闭被中断", failure); });
                }
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
                for (Thread thread : threads) {
                    try {
                        long remaining = deadline - System.nanoTime();
                        if (remaining > 0) thread.join(Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remaining)));
                    } catch (InterruptedException failure) {
                        interrupted = true;
                        checks.add(() -> { throw new AssertionError("隔离 worker 关闭被中断", failure); });
                    }
                    boolean stopped = !thread.isAlive();
                    checks.add(() -> assertTrue(stopped, "关闭会话/恢复元数据前 worker 必须退出"));
                }
                boolean terminated = pool.isTerminated();
                checks.add(() -> assertTrue(terminated, "关闭会话前执行器必须终止，不能只看 Future 完成"));
                for (Future<?> task : tasks) {
                    if (task.isDone()) {
                        try { task.get(); } catch (Exception failure) {
                            if (failure instanceof InterruptedException) interrupted = true;
                            checks.add(() -> { throw new AssertionError("未观察到的隔离任务异常", failure); });
                        }
                    } else checks.add(() -> fail("仍有未结束的隔离任务"));
                }
            }
            for (int i = resources.size() - 1; i >= 0; i--) {
                try { resources.get(i).close(); } catch (Throwable failure) {
                    checks.add(() -> { throw new AssertionError("关闭隔离会话/复原元数据失败", failure); });
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
            assertAll("真实 JDBC 夹具必须有界关闭且不泄漏全局状态", checks);
        }
    }

    /** 按本地 MP 3.5.16 字段保存精确条目及原生成器；仅删除/恢复这些键，绝不 clear 全局 Map。 */
    private static final class MetadataScope implements AutoCloseable {
        private final List<AutoCloseable> restores = new ArrayList<>();
        private boolean closed;

        MetadataScope(MybatisConfiguration configuration) throws Exception {
            try {
                preserve(TableInfoHelper.class, "TABLE_INFO_CACHE", AiKnowledgeDocument.class);
                preserve(TableInfoHelper.class, "TABLE_NAME_INFO_CACHE", "ai_knowledge_document");
                preserve(LambdaUtils.class, "COLUMN_CACHE_MAP", AiKnowledgeDocument.class.getName());
                preserve(ReflectionKit.class, "CLASS_FIELD_CACHE", AiKnowledgeDocument.class);
                preserve(GlobalConfigUtils.class, "GLOBAL_CONFIG", Integer.toHexString(configuration.hashCode()));
                Field generatorField = IdWorker.class.getDeclaredField("IDENTIFIER_GENERATOR");
                generatorField.setAccessible(true);
                IdentifierGenerator previous = (IdentifierGenerator) generatorField.get(null);
                restores.add(() -> {
                    IdWorker.setIdentifierGenerator(previous);
                    assertSame(previous, generatorField.get(null), "必须恢复前一工厂的原编号生成器对象");
                });
            } catch (Throwable failure) {
                try { close(); } catch (Throwable closing) { failure.addSuppressed(closing); }
                throw new AssertionError("MP 精确键快照初始化失败，不能静默跳过字段漂移", failure);
            }
        }

        /** 保存原引用并暂移除该键，确保注册的是本测试真实 Configuration，不沿用其他测试工厂。 */
        @SuppressWarnings("unchecked")
        private void preserve(Class<?> owner, String fieldName, Object key) throws ReflectiveOperationException {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            Map<Object, Object> cache = (Map<Object, Object>) field.get(null);
            boolean existed = cache.containsKey(key);
            Object previous = cache.get(key);
            restores.add(() -> {
                if (existed) {
                    cache.put(key, previous);
                    assertSame(previous, cache.get(key), "必须恢复元数据原对象：" + fieldName);
                } else {
                    cache.remove(key);
                    assertFalse(cache.containsKey(key), "必须移除夹具新增键：" + fieldName);
                }
            });
            cache.remove(key);
        }

        /** 即使单项复原异常也继续其余项目，错误聚合报告；重复 close 不会二次覆盖外部状态。 */
        @Override
        public void close() {
            if (closed) return;
            closed = true;
            List<Executable> checks = new ArrayList<>();
            for (int i = restores.size() - 1; i >= 0; i--) {
                try { restores.get(i).close(); } catch (Throwable failure) {
                    checks.add(() -> { throw new AssertionError("MP 精确键/编号生成器复原失败", failure); });
                }
            }
            assertAll("只复原本夹具触及的 MP 条目", checks);
        }
    }
}
