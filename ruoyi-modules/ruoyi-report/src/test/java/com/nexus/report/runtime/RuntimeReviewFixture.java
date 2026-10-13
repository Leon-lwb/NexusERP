package com.nexus.report.runtime;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Shared real H2/private-file fixture for runtime and lifecycle security regressions. */
public final class RuntimeReviewFixture implements AutoCloseable {
    public final ObjectMapper json = new ObjectMapper();
    public final JdbcTemplate jdbc;
    public final JdbcReportStore store;
    public final ReportCompiler compiler = new ReportCompiler(json);
    public final ReportDefinitionService definitions;
    public final ObservedArtifacts artifacts;
    public final ReportSnapshotStore snapshots;
    public final ReportRuntimeService runtime;
    public final TestProvider provider = new TestProvider();
    public final Map<Long, ReportActor> actors = new ConcurrentHashMap<>();
    public final ReportActor root = new ReportActor("A", 1, 10, true, List.of());
    public final ReportActor owner = actor(7, Set.of("*:*:*"), ReportScope.all(7));
    public final ReportActor viewer = actor(8, Set.of("report:runtime:preview", "report:definition:edit", "report:definition:add", "report:definition:list", "report:dataset:list", "wm:stock:list"), ReportScope.all(8));
    public final Dataset dataset;
    public final Definition definition;
    public volatile Runnable onDefinitionRead = () -> { };
    public volatile Runnable onSubjectCheck = () -> { };
    public volatile BiFunction<String, Long, ReportActor> resolveOverride;

    public RuntimeReviewFixture(Path directory) {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=8000");
        jdbc = new JdbcTemplate(source);
        try {
            String sql = Files.readString(Path.of("../../script/sql/update/2026-10-14-report-p0.sql"));
            var matcher = Pattern.compile("CREATE TABLE IF NOT EXISTS \u0060rpt_[^\u0060]+\u0060 \\([\\s\\S]*?\\) ENGINE[^;]*;").matcher(sql);
            int tables = 0;
            while (matcher.find()) {
                jdbc.execute(matcher.group().replaceAll("\\) ENGINE[^;]*;", ");")
                    .replaceAll("(?m)^  KEY \u0060[^\u0060]+\u0060 \\([^\\n]+\\),?\\n", "").replaceAll(",\\n\\)", "\n)"));
                tables++;
            }
            assertEquals(9, tables);
        } catch (Exception failure) { throw new IllegalStateException(failure); }
        actors.put(root.userId(), root); actors.put(owner.userId(), owner); actors.put(viewer.userId(), viewer);
        store = new JdbcReportStore(jdbc, json) {
            @Override public Optional<Definition> definition(String tenant, String id) {
                var result = super.definition(tenant, id);
                onDefinitionRead.run();
                return result;
            }
        };
        definitions = new ReportDefinitionService(store, new ReportProviderRegistry(List.of(provider)), new ReportAuthorizationPort() {
            public ReportActor resolve(String tenant, long user) {
                if (resolveOverride != null) return resolveOverride.apply(tenant, user);
                ReportActor actor = actors.get(user);
                return actor != null && actor.tenantId().equals(tenant) ? actor : null;
            }
            public boolean subjectExists(String tenant, String type, long subject) {
                onSubjectCheck.run();
                return tenant.equals("A") && subject > 0;
            }
        }, compiler);
        dataset = definitions.registerDataset(root, "SECURE_TEST", 1);
        store.replaceAcl(root, "DATASET", dataset.id(), List.of(new Grant("USER", "7", "VIEW"), new Grant("USER", "7", "EDIT"), new Grant("USER", "7", "MANAGE"), new Grant("USER", "7", "EXPORT"), new Grant("USER", "8", "VIEW"), new Grant("USER", "8", "EXPORT")));
        definition = definitions.create(owner, dsl("TEST_REPORT"), null);
        definitions.publish(owner, definition.id(), definition.revision());
        store.replaceAcl(owner, "REPORT", definition.id(), List.of(new Grant("USER", "8", "VIEW"), new Grant("USER", "8", "EDIT"), new Grant("USER", "8", "EXPORT"), new Grant("USER", "8", "MANAGE")));
        artifacts = new ObservedArtifacts(new LocalReportArtifactStore(directory, 20L * 1024 * 1024), directory);
        snapshots = new ReportSnapshotStore(artifacts, json);
        runtime = new ReportRuntimeService(store, definitions, compiler, snapshots, artifacts, new ReportProperties(), json);
    }

    public static ReportActor actor(long user, Set<String> permissions, ReportScope scope) {
        return new ReportActor("A", user, 10, false, List.of(new ReportRole(2, permissions, scope)));
    }
    public ReportDsl dsl(String code) {
        return new ReportDsl(1, code, "测试", ReportDsl.Kind.TABLE, "SECURE_TEST", dataset.publishedVersion(),
            List.of(new ReportDsl.Column("code", "编码", 100, 0, null, null)), List.of(), null, List.of(), List.of(), List.of(), true);
    }
    public void revokeReportView() {
        store.replaceAcl(owner, "REPORT", definition.id(), List.of(new Grant("USER", "8", "EDIT")));
    }
    public void narrow(long user) {
        var prior = actors.get(user);
        actors.put(user, actor(user, prior.roles().get(0).permissions(), new ReportScope(false, Set.of(20L), false, user)));
    }
    public long committedFiles() {
        try (var files = Files.walk(artifacts.directory)) {
            return files.filter(Files::isRegularFile).filter(p -> !p.getFileName().toString().startsWith("pending-")).count();
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    @Override public void close() { jdbc.execute("SHUTDOWN"); }

    public final class TestProvider implements ReportDatasetProvider {
        public volatile Runnable onDescriptor = () -> { };
        public volatile Runnable onQuery = () -> { };
        public volatile BiConsumer<ReportExecutionContext, Consumer<Map<String, Object>>> streamOverride;
        public final List<DatasetQuery> queries = new ArrayList<>();
        public DatasetDescriptor descriptor() {
            onDescriptor.run();
            return new DatasetDescriptor("SECURE_TEST", "安全测试", 1, "wm:stock:list", false,
                List.of(new DatasetField("code", "编码", ValueType.STRING), new DatasetField("secret", "受控字段", ValueType.STRING, "wm:stock:secret", List.of())),
                List.of(new DatasetParameter("customerId", "客户", ValueType.ID, false, null)), List.of("code", "secret"));
        }
        public DatasetResult query(ReportExecutionContext context, DatasetQuery query) {
            queries.add(query);
            onQuery.run();
            return new DatasetResult(List.of(row()), 1, Instant.now());
        }
        public void stream(ReportExecutionContext context, DatasetQuery query, Consumer<Map<String, Object>> consumer) {
            if (streamOverride != null) streamOverride.accept(context, consumer);
            else { onQuery.run(); context.cancellation().check(); consumer.accept(row()); }
        }
        public Map<String, Object> row() { return Map.of("code", "001", "secret", "protected-value", "internalSecret", "unexpected-provider-value"); }
    }

    public static final class ObservedArtifacts implements ReportArtifactStore {
        private final ReportArtifactStore delegate;
        public final Path directory;
        public volatile Runnable afterCommit = () -> { };
        public volatile Runnable afterChecksum = () -> { };
        public ObservedArtifacts(ReportArtifactStore delegate) {
            this.delegate = delegate;
            directory = null;
        }
        public ObservedArtifacts(ReportArtifactStore delegate, Path directory) { this.delegate = delegate; this.directory = directory; }
        public String storeId() { return delegate.storeId(); }
        public Path temporary(String tenant, String kind) { return delegate.temporary(tenant, kind); }
        public OutputStream output(String tenant, Path temporary) { return delegate.output(tenant, temporary); }
        public String commit(String tenant, Path temporary, String kind) { String ref = delegate.commit(tenant, temporary, kind); afterCommit.run(); return ref; }
        public Path read(String tenant, String reference) { return delegate.read(tenant, reference); }
        public boolean exists(String tenant, String reference) { return delegate.exists(tenant, reference); }
        public void delete(String tenant, String reference) { delegate.delete(tenant, reference); }
        public void discard(String tenant, Path temporary) { delegate.discard(tenant, temporary); }
        public String checksum(String tenant, String reference) { String checksum = delegate.checksum(tenant, reference); afterChecksum.run(); return checksum; }
    }
}
