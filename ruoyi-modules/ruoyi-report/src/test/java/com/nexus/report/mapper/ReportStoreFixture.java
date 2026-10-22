package com.nexus.report.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.DatasetDescriptor;
import com.nexus.common.report.ReportActor;
import com.nexus.common.report.ReportRole;
import com.nexus.common.report.ReportScope;
import com.nexus.report.domain.ReportRecords.*;

import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** 使用真实 H2 事务，仅通过语句门控确保并发交错可重复。 */
final class ReportStoreFixture implements AutoCloseable {
    /** 保存当前 {@code jdbc} 值。 */
    final JdbcTemplate jdbc;

    /** 租户化元数据或私有存储。 */
    final JdbcReportStore store;

    /** 来源定义或资源。 */
    private final JdbcDataSource source = new JdbcDataSource();

    /** 保存当前 {@code reports} 值。 */
    private final AtomicInteger reports = new AtomicInteger();

    /** before查询条件。 */
    volatile Consumer<String> beforeSql = sql -> {};

    /** 初始化报表存储测试夹具并保存所需依赖或不可变值。 */
    ReportStoreFixture() {
        source.setURL(
                "jdbc:h2:mem:"
                        + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=8000");
        jdbc = new JdbcTemplate(source);
        try {
            String sql =
                    Files.readString(Path.of("../../script/sql/update/2026-10-14-report-p0.sql"));
            var match =
                    Pattern.compile(
                                    "CREATE TABLE IF NOT EXISTS \u0060rpt_[^\u0060]+\u0060"
                                            + " \\([\\s\\S]*?\\) ENGINE[^;]*;")
                            .matcher(sql);
            int tables = 0;
            while (match.find()) {
                String ddl =
                        match.group()
                                .replaceAll("\\) ENGINE[^;]*;", ");")
                                .replaceAll(
                                        "(?m)^  KEY \u0060[^\u0060]+\u0060 \\([^\\n]+\\),?\\n", "")
                                .replaceAll(",\\n\\)", "\n)");
                jdbc.execute(ddl);
                tables++;
            }
            assertEquals(9, tables, "The DAO fixture must use all nine real report tables");
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
        store = newStore();
    }

    /**
     * 验证 {@code newStore} 场景下的结果与边界条件。
     *
     * @return 当前处理返回的固定结果
     */
    JdbcReportStore newStore() {
        var observed =
                new AbstractDataSource() {
                    /**
                     * 验证 {@code getConnection} 场景下的结果与边界条件。
                     *
                     * @return 当前处理返回的固定结果
                     */
                    @Override
                    public Connection getConnection() throws SQLException {
                        return observe(source.getConnection());
                    }

                    /**
                     * 读取Connection配置。
                     *
                     * @param user 当前操作的 {@code user} 输入
                     * @param password 当前操作的 {@code password} 输入
                     * @return 当前处理返回的固定结果
                     */
                    @Override
                    public Connection getConnection(String user, String password)
                            throws SQLException {
                        return observe(source.getConnection(user, password));
                    }
                };
        return new JdbcReportStore(new JdbcTemplate(observed), new ObjectMapper());
    }

    /**
     * 完成 {@code observe} 对应的报表存储测试夹具处理。
     *
     * @param connection 当前操作的 {@code connection} 输入
     * @return 当前处理返回的固定结果
     */
    private Connection observe(Connection connection) {
        return (Connection)
                Proxy.newProxyInstance(
                        Connection.class.getClassLoader(),
                        new Class<?>[] {Connection.class},
                        (proxy, method, arguments) -> {
                            Object result = invoke(connection, method, arguments);
                            if (method.getName().equals("prepareStatement")
                                    && result instanceof PreparedStatement statement) {
                                String sql = (String) arguments[0];
                                return Proxy.newProxyInstance(
                                        PreparedStatement.class.getClassLoader(),
                                        new Class<?>[] {PreparedStatement.class},
                                        (p, m, a) -> {
                                            if (m.getName().startsWith("execute"))
                                                beforeSql.accept(sql);
                                            return invoke(statement, m, a);
                                        });
                            }
                            return result;
                        });
    }

    /**
     * 完成 {@code invoke} 对应的报表存储测试夹具处理。
     *
     * @param target 目标定义或资源
     * @param method 当前操作的 {@code method} 输入
     * @param arguments 当前操作的 {@code arguments} 输入
     * @return 当前处理返回的固定结果
     */
    private static Object invoke(Object target, Method method, Object[] arguments)
            throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    /**
     * 完成 {@code actor} 对应的报表存储测试夹具处理。
     *
     * @param tenant 当前租户标识
     * @param user 当前操作的 {@code user} 输入
     * @return 当前处理返回的固定结果
     */
    static ReportActor actor(String tenant, long user) {
        return new ReportActor(
                tenant,
                user,
                10,
                false,
                List.of(new ReportRole(1, Set.of("*:*:*"), ReportScope.all(user))));
    }

    /**
     * 读取固定代码契约描述。
     *
     * @param code 业务编码
     * @param version 固定版本
     * @return 当前处理返回的固定结果
     */
    static DatasetDescriptor descriptor(String code, int version) {
        return new DatasetDescriptor(
                code, "测试数据集", version, "erp:test:list", true, List.of(), List.of(), List.of());
    }

    /**
     * 完成 {@code dataset} 对应的报表存储测试夹具处理。
     *
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    Dataset dataset(ReportActor actor) {
        var dataset =
                store.registerDataset(
                        actor, descriptor("TEST_DATA", 1), "{\"v\":1}", "data-hash-1");
        if (dataset.owner() != actor.userId()) {
            var grants = new ArrayList<>(store.acl(actor.tenantId(), "DATASET", dataset.id()));
            grants.add(new Grant("USER", Long.toString(actor.userId()), "VIEW"));
            store.replaceAcl(
                    actor(actor.tenantId(), dataset.owner()), "DATASET", dataset.id(), grants);
        }
        return dataset;
    }

    /**
     * 读取满足操作权限的报表定义。
     *
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    Definition definition(ReportActor actor) {
        return store.createDefinition(
                actor, "TEST_REPORT_" + reports.incrementAndGet(), "测试报表", "TABLE", null, "{}");
    }

    /**
     * 在修订与授权检查后创建不可变发布版本。
     *
     * @param actor 当前服务端身份
     * @param definition 当前操作的 {@code definition} 输入
     * @return 当前处理返回的固定结果
     */
    Version publish(ReportActor actor, Definition definition) {
        return store.publish(
                actor,
                definition.id(),
                definition.revision(),
                definition.json(),
                "report-hash",
                dataset(actor).publishedVersion());
    }

    /**
     * 完成 {@code completedRun} 对应的报表存储测试夹具处理。
     *
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    Run completedRun(ReportActor actor) {
        var definition = definition(actor);
        var version = publish(actor, definition);
        var run =
                store.createRun(
                        actor,
                        definition.id(),
                        version.id(),
                        "SNAPSHOT",
                        "{}",
                        "scope-hash",
                        Instant.now().plusSeconds(3600));
        store.runState(actor.tenantId(), run.id(), "RUNNING", null, null, 0, "{}", null);
        store.runState(
                actor.tenantId(),
                run.id(),
                "SUCCEEDED",
                "snapshot/" + run.id(),
                "plan/" + run.id(),
                1,
                "{}",
                null);
        return store.run(actor.tenantId(), run.id()).orElseThrow();
    }

    /**
     * 完成 {@code completedLiveRun} 对应的报表存储测试夹具处理。
     *
     * @param actor 当前服务端身份
     * @return 当前处理返回的固定结果
     */
    Run completedLiveRun(ReportActor actor) {
        var definition = definition(actor);
        var version = publish(actor, definition);
        var run =
                store.createRun(
                        actor,
                        definition.id(),
                        version.id(),
                        "LIVE",
                        "{}",
                        "scope-hash",
                        Instant.now().plusSeconds(3600));
        assertEquals(
                true,
                store.transitionRun(
                        actor.tenantId(), run.id(), "RUNNING", null, null, 0, "{}", null));
        assertEquals(
                true,
                store.transitionRun(
                        actor.tenantId(), run.id(), "SUCCEEDED", null, null, 0, "{}", null));
        return store.run(actor.tenantId(), run.id()).orElseThrow();
    }

    /**
     * 完成 {@code job} 对应的报表存储测试夹具处理。
     *
     * @param actor 当前服务端身份
     * @param run 当前报表运行
     * @param key 当前操作的 {@code key} 输入
     * @return 当前处理返回的固定结果
     */
    ExportJob job(ReportActor actor, Run run, String key) {
        return store.createJob(
                actor, run.id(), "PDF", key, "request-hash", Instant.now().plusSeconds(1800));
    }

    /**
     * 完成 {@code reload} 对应的报表存储测试夹具处理。
     *
     * @param job 当前导出任务
     * @return 当前处理返回的固定结果
     */
    ExportJob reload(ExportJob job) {
        return store.job(job.tenant(), job.id()).orElseThrow();
    }

    /**
     * 完成 {@code expireLease} 对应的报表存储测试夹具处理。
     *
     * @param job 当前导出任务
     */
    void expireLease(ExportJob job) {
        jdbc.update(
                "UPDATE rpt_export_job SET lease_until=? WHERE job_id=?",
                Timestamp.from(Instant.now().minusSeconds(60)),
                Long.valueOf(job.id()));
    }

    /**
     * 完成 {@code count} 对应的报表存储测试夹具处理。
     *
     * @param table 当前操作的 {@code table} 输入
     * @return 当前处理返回的固定结果
     */
    long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    /** 释放当前对象持有的文件或扫描句柄。 */
    @Override
    public void close() {
        jdbc.execute("SHUTDOWN");
    }
}
