package com.nexus.report.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.common.report.catalog.*;
import com.nexus.report.config.*;
import com.nexus.report.dataset.ReportDatasetResolver;
import com.nexus.report.domain.*;
import com.nexus.report.extension.*;
import com.nexus.report.query.*;
import com.nexus.report.service.*;
import com.nexus.report.version.*;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

/** 真实元数据、只读账户和私有快照的查询发布集成回归。 */
class ReportQueryDatasetRuntimeTest {
    /** 保存当前directory状态或配置。 */
    @TempDir Path directory;

    /** 执行fixedQueryDatasetKeepsOldContractAndRevocationBlocksCachedRows对应的受控处理。 */
    @Test
    void fixedQueryDatasetKeepsOldContractAndRevocationBlocksCachedRows() throws Exception {
        try (var f = new RuntimeReviewFixture(directory)) {
            String increment =
                    Files.readString(
                            Path.of("../../script/sql/update/2026-10-15-report-e2-e7.sql"))
                            .replace("\r\n", "\n");
            var ddl =
                    Pattern.compile(
                                    "CREATE TABLE IF NOT EXISTS `rpt_query[^`]*` \\([\\s\\S]*?\\)"
                                            + " ENGINE[^;]*;")
                            .matcher(increment);
            while (ddl.find())
                f.jdbc.execute(
                        ddl.group()
                                .replaceAll("\\) ENGINE[^;]*;", ");")
                                .replaceAll("(?m)^  KEY [^\\n]+\\n", "")
                                .replaceAll(",\\n\\)", "\n)"));
            f.jdbc.execute(
                    "CREATE TABLE sample_query_stock(id BIGINT,tenant_id VARCHAR(20),del_flag"
                        + " CHAR(1),create_dept BIGINT,create_by BIGINT,quantity DECIMAL(38,3))");
            f.jdbc.execute(
                    "INSERT INTO sample_query_stock"
                        + " VALUES(9223372036854775807,'A','0',10,7,9007199254740993.005),(2,'B','0',10,7,22)");
            f.jdbc.execute("CREATE USER query_reader PASSWORD 'synthetic-only'");
            f.jdbc.execute("GRANT SELECT ON sample_query_stock TO query_reader");
            var base = (JdbcDataSource) f.jdbc.getDataSource();
            var read = new JdbcDataSource();
            read.setURL(base.getURL().split(";")[0]);
            read.setUser("query_reader");
            read.setPassword("synthetic-only");
            var entity =
                    new ReportEntityCatalog.Entity(
                            "stock",
                            "合成库存",
                            "sample_query_stock",
                            "id",
                            "wm:stock:list",
                            "tenant_id",
                            "del_flag",
                            "create_dept",
                            "create_by",
                            null,
                            List.of(
                                    new ReportEntityCatalog.Field(
                                            "id", "标识", "id", ValueType.ID, null, List.of(), true),
                                    new ReportEntityCatalog.Field(
                                            "quantity",
                                            "数量",
                                            "quantity",
                                            ValueType.DECIMAL,
                                            "wm:stock:query",
                                            List.of(),
                                            true)));
            var catalog = new ReportEntityCatalog(1, List.of(entity), List.of());
            var catalogs = new ReportCatalogRegistry(List.of(() -> catalog), f.compiler);
            var resources =
                    new ReportResourceStore(
                            f.jdbc,
                            new DataSourceTransactionManager(f.jdbc.getDataSource()),
                            f.store);
            var links = new QueryDatasetBindingStore(f.jdbc);
            var authorization =
                    new ReportAuthorizationPort() {
                        /**
                         * 执行resolve对应的受控处理。
                         *
                         * @param tenant 当前受控操作的tenant输入
                         * @param user 当前受控操作的user输入
                         * @return 当前固定契约或处理结果
                         */
                        public ReportActor resolve(String tenant, long user) {
                            return f.definitions.resolve(tenant, user);
                        }

                        /**
                         * 执行subjectExists对应的受控处理。
                         *
                         * @param tenant 当前受控操作的tenant输入
                         * @param type 当前受控操作的type输入
                         * @param subject 当前受控操作的subject输入
                         * @return 当前固定契约或处理结果
                         */
                        public boolean subjectExists(String tenant, String type, long subject) {
                            return true;
                        }
                    };
            try (var executor = new CatalogQueryExecutor(read, 10)) {
                var queries =
                        new QueryDatasetResolver(links, resources, catalogs, executor, f.compiler);
                var registry = new ReportProviderRegistry(List.of(f.provider));
                var definitions =
                        new ReportDefinitionService(
                                f.store,
                                registry,
                                authorization,
                                f.compiler,
                                ReportEngineRegistry.v1(f.compiler),
                                ReportRuntimeMetrics.local(),
                                new ReportDatasetResolver(registry, queries));
                var service =
                        new ReportQueryService(
                                resources,
                                catalogs,
                                executor,
                                links,
                                f.store,
                                definitions,
                                f.compiler);
                var model =
                        new QueryModel(
                                1,
                                1,
                                "stock",
                                List.of(),
                                List.of(
                                        new QueryModel.Select("stock.id", "id", null),
                                        new QueryModel.Select("stock.quantity", "quantity", null)),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of());
                var draft = service.create(f.owner, "FIXED_QUERY", "固定查询", model);
                var first = service.publish(f.owner, draft.id(), draft.revision());
                var dsl =
                        new ReportDsl(
                                1,
                                "QUERY_REPORT",
                                "查询报表",
                                ReportDsl.Kind.TABLE,
                                first.dataset().code(),
                                first.dataset().publishedVersion(),
                                List.of(
                                        new ReportDsl.Column("id", "标识", 150, 0, null, null),
                                        new ReportDsl.Column("quantity", "数量", 180, 3, null, null)),
                                List.of(),
                                null,
                                List.of(),
                                List.of(),
                                List.of(),
                                true);
                var report = definitions.create(f.owner, dsl, null);
                definitions.publish(f.owner, report.id(), report.revision());
                var properties = new ReportProperties();
                var runtime =
                        new ReportRuntimeService(
                                f.store,
                                definitions,
                                f.compiler,
                                f.snapshots,
                                f.artifacts,
                                properties,
                                f.json);
                var run = runtime.create(f.owner, report.id(), "SNAPSHOT", Map.of());
                var page = runtime.page(f.owner, run.id(), 1, 50, null, false);
                assertEquals(1, page.total());
                assertEquals("9223372036854775807", page.rows().get(0).get("id").value());
                assertEquals("9007199254740993.005", page.rows().get(0).get("quantity").value());
                var current = service.detail(f.owner, draft.id());
                service.save(f.owner, draft.id(), current.revision(), "新标题", model);
                current = service.detail(f.owner, draft.id());
                var next = service.publish(f.owner, draft.id(), current.revision());
                assertNotEquals(
                        first.dataset().publishedVersion(), next.dataset().publishedVersion());
                assertEquals(
                        "9007199254740993.005",
                        runtime.page(f.owner, run.id(), 1, 50, null, false)
                                .rows()
                                .get(0)
                                .get("quantity")
                                .value());
                f.actors.put(
                        7L,
                        RuntimeReviewFixture.actor(
                                7,
                                Set.of("report:runtime:preview", "wm:stock:list"),
                                ReportScope.all(7)));
                assertThrows(
                        ReportFault.class,
                        () -> runtime.page(f.owner, run.id(), 1, 50, null, false));
                f.actors.put(7L, f.owner);
                current = service.detail(f.owner, draft.id());
                service.state(f.owner, draft.id(), current.revision(), "OFFLINE");
                assertThrows(
                        ReportFault.class,
                        () -> runtime.page(f.owner, run.id(), 1, 50, null, false));
                assertDoesNotThrow(() -> definitions.datasets(f.owner));
            }
        }
    }
}
