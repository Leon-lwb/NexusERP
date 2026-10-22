package com.nexus.ai.service.query;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.common.report.ReportActor;
import com.nexus.common.report.ReportAuthorizationPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证括号查询自身的子句不能绕过最终 AST 授权；不访问数据库。 */
class ErpQueryAstBoundaryTest {
    @AfterEach
    void clearIdentity() {
        AiRequestContext.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @ParameterizedTest(name = "括号查询子句必须拒绝，管理员={0}：{1}")
    @MethodSource("unvisitedWrapperClauses")
    void parenthesizedQueryClausesCannotHideUnprotectedPhysicalSources(boolean admin, String sql) {
        var guard = guard(admin);
        assertThrows(IllegalArgumentException.class, () -> guard.authorize(sql));
    }

    @ParameterizedTest(name = "列下标必须拒绝，管理员={0}：{1}")
    @MethodSource("unvisitedColumnSubscripts")
    void columnSubscriptsCannotHideUnprotectedExpressions(boolean admin, String sql) {
        assertThrows(IllegalArgumentException.class, () -> guard(admin).authorize(sql));
    }

    /** 列下标中的未授权子查询必须在创建 JDBC 语句前拒绝。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void columnSubscriptsAreRejectedBeforeJdbc(boolean admin) {
        var jdbc = mock(JdbcTemplate.class);
        var service = new ErpQueryService(jdbc, guard(admin));
        assertThrows(IllegalArgumentException.class, () -> service.executeQuery(
            "SELECT stock_id[(SELECT secret FROM wm_unregistered LIMIT 1)] FROM wm_material_stock", 20, 2));
        verifyNoInteractions(jdbc);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void orderingAndLimitsOnTheInnerSelectRemainProtected(boolean admin) {
        var approved = guard(admin).authorize("SELECT x.stock_id FROM (SELECT stock_id FROM wm_material_stock "
            + "WHERE stock_id>? ORDER BY stock_id LIMIT ?) x").sql();
        assertTrue(approved.contains("'000000'"), approved);
        assertTrue(approved.contains("FROM (SELECT"), approved);
        assertEquals(2, approved.chars().filter(character -> character == '?').count());
    }

    private static Stream<Arguments> unvisitedWrapperClauses() {
        return Stream.of(false, true).flatMap(admin -> Stream.of(
            "SELECT x.stock_id FROM ((SELECT stock_id FROM wm_material_stock) "
                + "ORDER BY (SELECT secret FROM wm_unregistered LIMIT 1)) x",
            "SELECT ((SELECT stock_id FROM wm_material_stock LIMIT 1) "
                + "ORDER BY (SELECT secret FROM wm_unregistered LIMIT 1)) AS leaked",
            "SELECT s.stock_id FROM wm_material_stock s WHERE s.stock_id IN "
                + "((SELECT stock_id FROM wm_material_stock) ORDER BY (SELECT secret FROM wm_unregistered LIMIT 1))",
            "SELECT s.stock_id FROM wm_material_stock s WHERE EXISTS "
                + "((SELECT stock_id FROM wm_material_stock) ORDER BY (SELECT secret FROM wm_unregistered LIMIT 1))",
            "SELECT x.stock_id FROM ((SELECT stock_id FROM wm_material_stock) "
                + "LIMIT (SELECT secret FROM wm_unregistered LIMIT 1)) x",
            "SELECT x.stock_id FROM ((SELECT stock_id FROM wm_material_stock) ORDER BY stock_id) x",
            "SELECT x.stock_id FROM ((SELECT stock_id FROM wm_material_stock) LIMIT ?) x",
            "SELECT x.stock_id FROM ((SELECT stock_id FROM wm_material_stock) OFFSET "
                + "(SELECT secret FROM wm_unregistered LIMIT 1) ROWS) x",
            "SELECT x.stock_id FROM ((SELECT stock_id FROM wm_material_stock) OFFSET 1 ROWS) x",
            "SELECT x.stock_id FROM ((SELECT stock_id FROM wm_material_stock) LIMIT 1 OFFSET ?) x"
        ).map(sql -> Arguments.of(admin, sql)));
    }

    private static Stream<Arguments> unvisitedColumnSubscripts() {
        return Stream.of(false, true).flatMap(admin -> Stream.of(
            "SELECT stock_id[(SELECT secret FROM wm_unregistered LIMIT 1)] FROM wm_material_stock",
            "SELECT s.stock_id FROM wm_material_stock s WHERE "
                + "s.stock_id[(SELECT secret FROM wm_unregistered LIMIT 1)]>0",
            "SELECT stock_id FROM wm_material_stock ORDER BY "
                + "stock_id[(SELECT secret FROM wm_unregistered LIMIT 1)]",
            "SELECT stock_id[1] FROM wm_material_stock"
        ).map(sql -> Arguments.of(admin, sql)));
    }

    private static ErpQueryAuthorizationGuard guard(boolean admin) {
        RequestContextHolder.resetRequestAttributes();
        var actor = admin ? new ReportActor("000000", 1, 1, true, List.of())
            : ErpQueryAuthorizationTest.departmentActor();
        AiRequestContext.set(actor.tenantId(), actor.userId());
        var authorization = new ReportAuthorizationPort() {
            @Override
            public ReportActor resolve(String tenantId, long userId) { return actor; }
            @Override
            public boolean subjectExists(String tenantId, String type, long id) { return false; }
        };
        return new ErpQueryAuthorizationGuard(authorization,
            List.of(ErpQueryAuthorizationTest::catalog), new AiProperties());
    }
}
