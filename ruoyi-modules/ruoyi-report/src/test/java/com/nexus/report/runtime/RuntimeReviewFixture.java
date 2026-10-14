package com.nexus.report.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.config.*;
import com.nexus.report.domain.ReportDsl;
import com.nexus.report.domain.ReportRecords.*;
import com.nexus.report.engine.ReportCompiler;
import com.nexus.report.mapper.JdbcReportStore;
import com.nexus.report.service.ReportDefinitionService;
import com.nexus.report.storage.*;

import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.OutputStream;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;
import java.util.regex.Pattern;

/** 运行与生命周期权限回归共用的真实 H2 和私有文件测试夹具。 */
public final class RuntimeReviewFixture implements AutoCloseable {
    /** 待处理定义文本。 */
    public final ObjectMapper json = new ObjectMapper();

    /** 保存当前 {@code jdbc} 值。 */
    public final JdbcTemplate jdbc;

    /** 租户化元数据或私有存储。 */
    public final JdbcReportStore store;

    /** 报表编译器。 */
    public final ReportCompiler compiler = new ReportCompiler(json);

    /** 保存当前 {@code definitions} 值。 */
    public final ReportDefinitionService definitions;

    /** 保存当前 {@code artifacts} 值。 */
    public final ObservedArtifacts artifacts;

    /** 保存当前 {@code snapshots} 值。 */
    public final ReportSnapshotStore snapshots;

    /** 保存当前 {@code runtime} 值。 */
    public final ReportRuntimeService runtime;

    /** 保存当前 {@code provider} 值。 */
    public final TestProvider provider = new TestProvider();

    /** 保存当前 {@code actors} 值。 */
    public final Map<Long, ReportActor> actors = new ConcurrentHashMap<>();

    /** 保存当前 {@code root} 值。 */
    public final ReportActor root = new ReportActor("A", 1, 10, true, List.of());

    /** 租约或资源持有者。 */
    public final ReportActor owner = actor(7, Set.of("*:*:*"), ReportScope.all(7));

    /** 保存当前 {@code viewer} 值。 */
    public final ReportActor viewer =
            actor(
                    8,
                    Set.of(
                            "report:runtime:preview",
                            "report:definition:edit",
                            "report:definition:add",
                            "report:definition:list",
                            "report:dataset:list",
                            "wm:stock:list"),
                    ReportScope.all(8));

    /** 保存当前 {@code dataset} 值。 */
    public final Dataset dataset;

    /** 保存当前 {@code definition} 值。 */
    public final Definition definition;

    /** on定义Read。 */
    public volatile Runnable onDefinitionRead = () -> {};

    /** 保存当前 {@code onSubjectCheck} 值。 */
    public volatile Runnable onSubjectCheck = () -> {};

    /** 保存当前 {@code resolveOverride} 值。 */
    public volatile BiFunction<String, Long, ReportActor> resolveOverride;

    /**
     * 初始化运行回归测试夹具并保存所需依赖或不可变值。
     *
     * @param directory 测试私有目录
     */
    public RuntimeReviewFixture(Path directory) {
        this(
                directory,
                List.of(new DatasetParameter("customerId", "客户", ValueType.ID, false, null)));
    }

    /**
     * 安装带指定参数目录的隔离夹具，用于验证隐藏默认值和调度冻结。
     *
     * @param directory 当前契约的directory配置或状态
     * @param parameters 固定参数契约
     */
    public RuntimeReviewFixture(Path directory, List<DatasetParameter> parameters) {
        provider.parameters = List.copyOf(parameters);
        var source = new JdbcDataSource();
        source.setURL(
                "jdbc:h2:mem:"
                        + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=8000");
        jdbc = new JdbcTemplate(source);
        try {
            String sql =
                    Files.readString(Path.of("../../script/sql/update/2026-10-14-report-p0.sql"));
            var matcher =
                    Pattern.compile(
                                    "CREATE TABLE IF NOT EXISTS \u0060rpt_[^\u0060]+\u0060"
                                            + " \\([\\s\\S]*?\\) ENGINE[^;]*;")
                            .matcher(sql);
            int tables = 0;
            while (matcher.find()) {
                jdbc.execute(
                        matcher.group()
                                .replaceAll("\\) ENGINE[^;]*;", ");")
                                .replaceAll(
                                        "(?m)^  KEY \u0060[^\u0060]+\u0060 \\([^\\n]+\\),?\\n", "")
                                .replaceAll(",\\n\\)", "\n)"));
                tables++;
            }
            assertEquals(9, tables);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
        actors.put(root.userId(), root);
        actors.put(owner.userId(), owner);
        actors.put(viewer.userId(), viewer);
        store =
                new JdbcReportStore(jdbc, json) {
                    /**
                     * 读取满足操作权限的报表定义。
                     *
                     * @param tenant 当前租户标识
                     * @param id 资源标识
                     * @return 当前处理返回的固定结果
                     */
                    @Override
                    public Optional<Definition> definition(String tenant, String id) {
                        var result = super.definition(tenant, id);
                        onDefinitionRead.run();
                        return result;
                    }
                };
        definitions =
                new ReportDefinitionService(
                        store,
                        new ReportProviderRegistry(List.of(provider)),
                        new ReportAuthorizationPort() {
                            /**
                             * 重新解析当前账户和业务权限。
                             *
                             * @param tenant 当前租户标识
                             * @param user 当前操作的 {@code user} 输入
                             * @return 当前处理返回的固定结果
                             */
                            public ReportActor resolve(String tenant, long user) {
                                if (resolveOverride != null)
                                    return resolveOverride.apply(tenant, user);
                                ReportActor actor = actors.get(user);
                                return actor != null && actor.tenantId().equals(tenant)
                                        ? actor
                                        : null;
                            }

                            /**
                             * 校验授权主体属于当前租户。
                             *
                             * @param tenant 当前租户标识
                             * @param type 当前操作的 {@code type} 输入
                             * @param subject 当前操作的 {@code subject} 输入
                             * @return 当前检查或条件操作是否成功
                             */
                            public boolean subjectExists(String tenant, String type, long subject) {
                                onSubjectCheck.run();
                                return tenant.equals("A") && subject > 0;
                            }
                        },
                        compiler);
        dataset = definitions.registerDataset(root, "SECURE_TEST", 1);
        store.replaceAcl(
                root,
                "DATASET",
                dataset.id(),
                List.of(
                        new Grant("USER", "7", "VIEW"),
                        new Grant("USER", "7", "EDIT"),
                        new Grant("USER", "7", "MANAGE"),
                        new Grant("USER", "7", "EXPORT"),
                        new Grant("USER", "8", "VIEW"),
                        new Grant("USER", "8", "EXPORT")));
        definition = definitions.create(owner, dsl("TEST_REPORT"), null);
        definitions.publish(owner, definition.id(), definition.revision());
        store.replaceAcl(
                owner,
                "REPORT",
                definition.id(),
                List.of(
                        new Grant("USER", "8", "VIEW"),
                        new Grant("USER", "8", "EDIT"),
                        new Grant("USER", "8", "EXPORT"),
                        new Grant("USER", "8", "MANAGE")));
        artifacts =
                new ObservedArtifacts(
                        new LocalReportArtifactStore(directory, 20L * 1024 * 1024), directory);
        snapshots = new ReportSnapshotStore(artifacts, json);
        runtime =
                new ReportRuntimeService(
                        store,
                        definitions,
                        compiler,
                        snapshots,
                        artifacts,
                        new ReportProperties(),
                        json);
    }

    /**
     * 完成 {@code actor} 对应的运行回归测试夹具处理。
     *
     * @param user 当前操作的 {@code user} 输入
     * @param permissions 当前操作的 {@code permissions} 输入
     * @param scope 当前操作的 {@code scope} 输入
     * @return 当前处理返回的固定结果
     */
    public static ReportActor actor(long user, Set<String> permissions, ReportScope scope) {
        return new ReportActor(
                "A", user, 10, false, List.of(new ReportRole(2, permissions, scope)));
    }

    /**
     * 完成 {@code dsl} 对应的运行回归测试夹具处理。
     *
     * @param code 业务编码
     * @return 当前处理返回的固定结果
     */
    public ReportDsl dsl(String code) {
        return new ReportDsl(
                1,
                code,
                "测试",
                ReportDsl.Kind.TABLE,
                "SECURE_TEST",
                dataset.publishedVersion(),
                List.of(new ReportDsl.Column("code", "编码", 100, 0, null, null)),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                true);
    }

    /** 验证 {@code revokeReportView} 场景下的结果与边界条件。 */
    public void revokeReportView() {
        store.replaceAcl(owner, "REPORT", definition.id(), List.of(new Grant("USER", "8", "EDIT")));
    }

    /**
     * 完成 {@code narrow} 对应的运行回归测试夹具处理。
     *
     * @param user 当前操作的 {@code user} 输入
     */
    public void narrow(long user) {
        var prior = actors.get(user);
        actors.put(
                user,
                actor(
                        user,
                        prior.roles().get(0).permissions(),
                        new ReportScope(false, Set.of(20L), false, user)));
    }

    /**
     * 验证 {@code committedFiles} 场景下的结果与边界条件。
     *
     * @return 当前处理返回的固定结果
     */
    public long committedFiles() {
        try (var files = Files.walk(artifacts.directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(
                            p ->
                                    !p.getFileName().toString().startsWith("pending-")
                                            && !p.getFileName().toString().endsWith(".fence"))
                    .count();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** 释放当前对象持有的文件或扫描句柄。 */
    @Override
    public void close() {
        jdbc.execute("SHUTDOWN");
    }

    /** 测试提供者，负责当前模块对应的契约或处理职责。 */
    public final class TestProvider implements ReportDatasetProvider {
        /** on契约描述。 */
        /** 隔离目录参数，构造夹具时固定并随数据集发布。 */
        public List<DatasetParameter> parameters;

        /** 隔离测试读取参数目录时执行的故障或时序钩子。 */
        public volatile Runnable onDescriptor = () -> {};

        /** on查询。 */
        public volatile Runnable onQuery = () -> {};

        /** 保存当前 {@code streamOverride} 值。 */
        public volatile BiConsumer<ReportExecutionContext, Consumer<Map<String, Object>>>
                streamOverride;

        /** 保存当前 {@code queries} 值。 */
        public final List<DatasetQuery> queries = new ArrayList<>();

        /**
         * 读取固定代码契约描述。
         *
         * @return 当前处理返回的固定结果
         */
        public DatasetDescriptor descriptor() {
            onDescriptor.run();
            return new DatasetDescriptor(
                    "SECURE_TEST",
                    "安全测试",
                    1,
                    "wm:stock:list",
                    false,
                    List.of(
                            new DatasetField("code", "编码", ValueType.STRING),
                            new DatasetField(
                                    "secret",
                                    "受控字段",
                                    ValueType.STRING,
                                    "wm:stock:secret",
                                    List.of())),
                    parameters,
                    List.of("code", "secret"));
        }

        /**
         * 执行受控只读查询。
         *
         * @param context 服务端执行上下文
         * @param query 已声明字段和筛选参数
         * @return 当前处理返回的固定结果
         */
        public DatasetResult query(ReportExecutionContext context, DatasetQuery query) {
            queries.add(query);
            onQuery.run();
            return new DatasetResult(List.of(row()), 1, Instant.now());
        }

        /**
         * 在取消与容量控制下逐行读取。
         *
         * @param context 服务端执行上下文
         * @param query 已声明字段和筛选参数
         * @param consumer 当前操作的 {@code consumer} 输入
         */
        public void stream(
                ReportExecutionContext context,
                DatasetQuery query,
                Consumer<Map<String, Object>> consumer) {
            if (streamOverride != null) streamOverride.accept(context, consumer);
            else {
                onQuery.run();
                context.cancellation().check();
                consumer.accept(row());
            }
        }

        /**
         * 验证 {@code row} 场景下的结果与边界条件。
         *
         * @return 当前处理返回的固定结果
         */
        public Map<String, Object> row() {
            return Map.of(
                    "code",
                    "001",
                    "secret",
                    "protected-value",
                    "internalSecret",
                    "unexpected-provider-value");
        }
    }

    /** ObservedArtifacts，负责当前模块对应的契约或处理职责。 */
    public static final class ObservedArtifacts implements ReportArtifactStore {
        /** 保存当前 {@code delegate} 值。 */
        private final ReportArtifactStore delegate;

        /** 测试私有目录。 */
        public final Path directory;

        /** 保存当前 {@code afterCommit} 值。 */
        public volatile Runnable afterCommit = () -> {};

        /** 保存当前 {@code afterChecksum} 值。 */
        public volatile Runnable afterChecksum = () -> {};

        /**
         * 初始化ObservedArtifacts并保存所需依赖或不可变值。
         *
         * @param delegate 当前操作的 {@code delegate} 输入
         */
        public ObservedArtifacts(ReportArtifactStore delegate) {
            this.delegate = delegate;
            directory = null;
        }

        /**
         * 初始化ObservedArtifacts并保存所需依赖或不可变值。
         *
         * @param delegate 当前操作的 {@code delegate} 输入
         * @param directory 测试私有目录
         */
        public ObservedArtifacts(ReportArtifactStore delegate, Path directory) {
            this.delegate = delegate;
            this.directory = directory;
        }

        /**
         * 读取私有存储逻辑命名空间。
         *
         * @return 当前处理返回的固定结果
         */
        public String storeId() {
            return delegate.storeId();
        }

        /**
         * 创建当前租户的受控暂存文件。
         *
         * @param tenant 当前租户标识
         * @param kind 受支持类型
         * @return 当前处理返回的固定结果
         */
        public Path temporary(String tenant, String kind) {
            return delegate.temporary(tenant, kind);
        }

        /**
         * 打开有界私有产物输出流。
         *
         * @param tenant 当前租户标识
         * @param temporary 本次操作拥有的暂存文件
         * @return 当前处理返回的固定结果
         */
        public OutputStream output(String tenant, Path temporary) {
            return delegate.output(tenant, temporary);
        }

        /**
         * 将本次暂存文件登记为不可变私有产物。
         *
         * @param tenant 当前租户标识
         * @param temporary 本次操作拥有的暂存文件
         * @param kind 受支持类型
         * @return 当前处理返回的固定结果
         */
        public String commit(String tenant, Path temporary, String kind) {
            String ref = delegate.commit(tenant, temporary, kind);
            afterCommit.run();
            return ref;
        }

        /**
         * 读取并检查当前租户私有产物。
         *
         * @param tenant 当前租户标识
         * @param reference 固定私有产物引用
         * @return 当前处理返回的固定结果
         */
        public Path read(String tenant, String reference) {
            return delegate.read(tenant, reference);
        }

        /**
         * 完成 {@code exists} 对应的ObservedArtifacts处理。
         *
         * @param tenant 当前租户标识
         * @param reference 固定私有产物引用
         * @return 当前检查或条件操作是否成功
         */
        public boolean exists(String tenant, String reference) {
            return delegate.exists(tenant, reference);
        }

        /**
         * 删除当前租户明确指定的私有产物。
         *
         * @param tenant 当前租户标识
         * @param reference 固定私有产物引用
         */
        public void delete(String tenant, String reference) {
            delegate.delete(tenant, reference);
        }

        /**
         * 释放本次操作拥有的暂存文件。
         *
         * @param tenant 当前租户标识
         * @param temporary 本次操作拥有的暂存文件
         */
        public void discard(String tenant, Path temporary) {
            delegate.discard(tenant, temporary);
        }

        /**
         * 计算或核对私有产物完整性。
         *
         * @param tenant 当前租户标识
         * @param reference 固定私有产物引用
         * @return 当前处理返回的固定结果
         */
        public String checksum(String tenant, String reference) {
            String checksum = delegate.checksum(tenant, reference);
            afterChecksum.run();
            return checksum;
        }
    }
}
