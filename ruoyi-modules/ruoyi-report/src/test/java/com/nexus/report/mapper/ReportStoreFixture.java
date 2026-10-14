package com.nexus.report.mapper;

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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Real H2 transactions, with statement gates only to make interleavings reproducible. */
final class ReportStoreFixture implements AutoCloseable {
    final JdbcTemplate jdbc;
    final JdbcReportStore store;
    private final JdbcDataSource source = new JdbcDataSource();
    private final AtomicInteger reports = new AtomicInteger();
    volatile Consumer<String> beforeSql = sql -> { };

    ReportStoreFixture() {
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=8000");
        jdbc = new JdbcTemplate(source);
        try {
            String sql = Files.readString(Path.of("../../script/sql/update/2026-10-14-report-p0.sql"));
            var match = Pattern.compile("CREATE TABLE IF NOT EXISTS \u0060rpt_[^\u0060]+\u0060 \\([\\s\\S]*?\\) ENGINE[^;]*;").matcher(sql);
            int tables = 0;
            while (match.find()) {
                String ddl = match.group().replaceAll("\\) ENGINE[^;]*;", ");")
                    .replaceAll("(?m)^  KEY \u0060[^\u0060]+\u0060 \\([^\\n]+\\),?\\n", "")
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

    JdbcReportStore newStore() {
        var observed = new AbstractDataSource() {
            @Override public Connection getConnection() throws SQLException { return observe(source.getConnection()); }
            @Override public Connection getConnection(String user, String password) throws SQLException {
                return observe(source.getConnection(user, password));
            }
        };
        return new JdbcReportStore(new JdbcTemplate(observed), new ObjectMapper());
    }

    private Connection observe(Connection connection) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
            (proxy, method, arguments) -> {
                Object result = invoke(connection, method, arguments);
                if (method.getName().equals("prepareStatement") && result instanceof PreparedStatement statement) {
                    String sql = (String) arguments[0];
                    return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class},
                        (p, m, a) -> {
                            if (m.getName().startsWith("execute")) beforeSql.accept(sql);
                            return invoke(statement, m, a);
                        });
                }
                return result;
            });
    }

    private static Object invoke(Object target, Method method, Object[] arguments) throws Throwable {
        try { return method.invoke(target, arguments); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    static ReportActor actor(String tenant, long user) {
        return new ReportActor(tenant, user, 10, false,
            List.of(new ReportRole(1, Set.of("*:*:*"), ReportScope.all(user))));
    }

    static DatasetDescriptor descriptor(String code, int version) {
        return new DatasetDescriptor(code, "测试数据集", version, "erp:test:list", true, List.of(), List.of(), List.of());
    }

    Dataset dataset(ReportActor actor) {
        var dataset=store.registerDataset(actor, descriptor("TEST_DATA", 1), "{\"v\":1}", "data-hash-1");
        if(dataset.owner()!=actor.userId()){var grants=new java.util.ArrayList<>(store.acl(actor.tenantId(),"DATASET",dataset.id()));grants.add(new Grant("USER",Long.toString(actor.userId()),"VIEW"));store.replaceAcl(actor(actor.tenantId(),dataset.owner()),"DATASET",dataset.id(),grants);}
        return dataset;
    }

    Definition definition(ReportActor actor) {
        return store.createDefinition(actor, "TEST_REPORT_" + reports.incrementAndGet(), "测试报表", "TABLE", null, "{}");
    }

    Version publish(ReportActor actor, Definition definition) {
        return store.publish(actor, definition.id(), definition.revision(), definition.json(), "report-hash", dataset(actor).publishedVersion());
    }

    Run completedRun(ReportActor actor) {
        var definition = definition(actor);
        var version = publish(actor, definition);
        var run = store.createRun(actor, definition.id(), version.id(), "SNAPSHOT", "{}", "scope-hash", Instant.now().plusSeconds(3600));
        store.runState(actor.tenantId(), run.id(), "RUNNING", null, null, 0, "{}", null);
        store.runState(actor.tenantId(), run.id(), "SUCCEEDED", "snapshot/" + run.id(), "plan/" + run.id(), 1, "{}", null);
        return store.run(actor.tenantId(), run.id()).orElseThrow();
    }

    Run completedLiveRun(ReportActor actor) {
        var definition = definition(actor);
        var version = publish(actor, definition);
        var run = store.createRun(actor, definition.id(), version.id(), "LIVE", "{}", "scope-hash", Instant.now().plusSeconds(3600));
        assertEquals(true, store.transitionRun(actor.tenantId(), run.id(), "RUNNING", null, null, 0, "{}", null));
        assertEquals(true, store.transitionRun(actor.tenantId(), run.id(), "SUCCEEDED", null, null, 0, "{}", null));
        return store.run(actor.tenantId(), run.id()).orElseThrow();
    }

    ExportJob job(ReportActor actor, Run run, String key) {
        return store.createJob(actor, run.id(), "PDF", key, "request-hash", Instant.now().plusSeconds(1800));
    }

    ExportJob reload(ExportJob job) { return store.job(job.tenant(), job.id()).orElseThrow(); }

    void expireLease(ExportJob job) {
        jdbc.update("UPDATE rpt_export_job SET lease_until=? WHERE job_id=?", Timestamp.from(Instant.now().minusSeconds(60)), Long.valueOf(job.id()));
    }

    long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }

    @Override public void close() { jdbc.execute("SHUTDOWN"); }
}
