package com.nexus.report.query;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.common.report.catalog.*;
import com.nexus.report.domain.ReportFault;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.SQLException;
import java.util.*;

/** 使用真实H2只读账户验证右表预过滤、精确值和写账户拒绝。 */
class ReportCatalogExecutionTest {
    /** 执行leftJoinKeepsVisibleParentAndRejectsWriteAccount对应的受控处理。 */
    @Test
    void leftJoinKeepsVisibleParentAndRejectsWriteAccount() throws Exception {
        String url = "jdbc:h2:mem:query_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        var admin = new DriverManagerDataSource(url, "sa", "");
        var jdbc = new JdbcTemplate(admin);
        jdbc.execute(
                "CREATE TABLE sample_parent(id BIGINT,tenant_id VARCHAR(20),del_flag"
                        + " CHAR(1),create_dept BIGINT,create_by BIGINT,child_id BIGINT)");
        jdbc.execute(
                "CREATE TABLE sample_child(id BIGINT,tenant_id VARCHAR(20),del_flag"
                        + " CHAR(1),create_dept BIGINT,create_by BIGINT,name VARCHAR(30))");
        jdbc.execute(
                "INSERT INTO sample_parent"
                    + " VALUES(9223372036854775807,'A','0',10,7,1),(2,'B','0',10,7,1),(3,'A','0',20,8,1)");
        jdbc.execute(
                "INSERT INTO sample_child VALUES(1,'A','0',20,8,'不可见'),(2,'B','0',10,7,'另一租户')");
        jdbc.execute("CREATE USER report_reader PASSWORD 'synthetic-only'");
        jdbc.execute("GRANT SELECT ON sample_parent TO report_reader");
        jdbc.execute("GRANT SELECT ON sample_child TO report_reader");
        var parent =
                new ReportEntityCatalog.Entity(
                        "parent",
                        "父",
                        "sample_parent",
                        "id",
                        "parent:list",
                        "tenant_id",
                        "del_flag",
                        "create_dept",
                        "create_by",
                        null,
                        List.of(
                                new ReportEntityCatalog.Field(
                                        "id", "标识", "id", ValueType.ID, null, List.of(), true)));
        var child =
                new ReportEntityCatalog.Entity(
                        "child",
                        "子",
                        "sample_child",
                        "id",
                        "child:list",
                        "tenant_id",
                        "del_flag",
                        "create_dept",
                        "create_by",
                        null,
                        List.of(
                                new ReportEntityCatalog.Field(
                                        "name",
                                        "名称",
                                        "name",
                                        ValueType.STRING,
                                        null,
                                        List.of(),
                                        true)));
        var catalog =
                new ReportEntityCatalog(
                        1,
                        List.of(parent, child),
                        List.of(
                                new ReportEntityCatalog.Relation(
                                        "parent.child",
                                        "parent",
                                        "child",
                                        "child_id",
                                        "id",
                                        "MANY_TO_ONE",
                                        "LEFT")));
        var model =
                new QueryModel(
                        1,
                        1,
                        "parent",
                        List.of("parent.child"),
                        List.of(
                                new QueryModel.Select("parent.id", "id", null),
                                new QueryModel.Select("child.name", "name", null)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of());
        var actor =
                new ReportActor(
                        "A",
                        7,
                        10,
                        false,
                        List.of(
                                new ReportRole(
                                        1,
                                        Set.of("parent:list", "child:list"),
                                        new ReportScope(false, Set.of(10L), false, 7))));
        var plan = new QueryPlanner(catalog).plan(model, actor, Map.of());
        var input = new DatasetQuery(Map.of(), Set.of("id", "name"), 1, 50, false, null, false);
        try (var executor =
                new CatalogQueryExecutor(
                        new DriverManagerDataSource(
                                url.split(";")[0], "report_reader", "synthetic-only"),
                        10)) {
            var result =
                    executor.query(
                            plan,
                            new ReportExecutionContext(
                                    actor, List.of(actor.scopeFor("parent:list"))),
                            input);
            assertEquals(1, result.total());
            assertEquals(
                    "9223372036854775807",
                    TypedValue.of(ValueType.ID, result.rows().get(0).get("id")).value());
            assertNull(result.rows().get(0).get("name"));
        }
        try (var executor = new CatalogQueryExecutor(admin, 10)) {
            assertThrows(
                    ReportFault.class,
                    () ->
                            executor.query(
                                    plan,
                                    new ReportExecutionContext(
                                            actor, List.of(actor.scopeFor("parent:list"))),
                                    input));
        }
        try (var read =
                        new DriverManagerDataSource(
                                        url.split(";")[0], "report_reader", "synthetic-only")
                                .getConnection();
                var statement = read.createStatement()) {
            assertThrows(
                    SQLException.class, () -> statement.executeUpdate("DELETE FROM sample_parent"));
        }
    }

    /** 执行disabledDataSourceNeverFallsBackToBusinessWriter对应的受控处理。 */
    @Test
    void disabledDataSourceNeverFallsBackToBusinessWriter() {
        try (var executor = new CatalogQueryExecutor(new ReportQueryProperties())) {
            assertThrows(ReportFault.class, () -> executor.verify(Set.of("sample")));
        }
    }
}
