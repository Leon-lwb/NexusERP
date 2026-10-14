package com.nexus.report.query;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.common.report.catalog.*;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/** DATE逻辑字段在实际datetime源上筛选和分组语义一致。 */
class ReportQueryDateTest {
    /** 执行dateEqualityIncludesNonMidnightOrdersAndGroupsOneCalendarDay对应的受控处理。 */
    @Test
    void dateEqualityIncludesNonMidnightOrdersAndGroupsOneCalendarDay() throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute(
                "CREATE TABLE sample_orders(id BIGINT,tenant_id VARCHAR(20),created_at TIMESTAMP)");
        jdbc.execute(
                "INSERT INTO sample_orders VALUES(1,'A','2026-10-15 14:30:00'),(2,'A','2026-10-15"
                        + " 19:00:00'),(3,'B','2026-10-15 00:00:00')");
        var entity =
                new ReportEntityCatalog.Entity(
                        "orders",
                        "订单",
                        "sample_orders",
                        "id",
                        "order:list",
                        "tenant_id",
                        null,
                        null,
                        null,
                        null,
                        List.of(
                                new ReportEntityCatalog.Field(
                                        "date",
                                        "日期",
                                        "created_at",
                                        ValueType.DATE,
                                        null,
                                        List.of(),
                                        true),
                                new ReportEntityCatalog.Field(
                                        "id", "标识", "id", ValueType.ID, null, List.of(), true)));
        var actor = new ReportActor("A", 1, 10, true, List.of());
        var query =
                new QueryModel(
                        1,
                        1,
                        "orders",
                        List.of(),
                        List.of(
                                new QueryModel.Select("orders.date", "date", null),
                                new QueryModel.Select("orders.id", "count", "COUNT_ROWS")),
                        List.of(new QueryModel.Filter("orders.date", "EQ", "date")),
                        List.of(new QueryModel.Parameter("date", ValueType.DATE, true)),
                        List.of("orders.date"),
                        List.of());
        var plan =
                new QueryPlanner(new ReportEntityCatalog(1, List.of(entity), List.of()))
                        .plan(query, actor, Map.of("date", "2026-10-15"));
        var results = jdbc.queryForList(plan.sql(), plan.values().toArray());
        assertEquals(1, results.size());
        assertEquals(2L, ((Number) results.get(0).get("COUNT")).longValue());
    }
}
