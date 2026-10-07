package com.nexus.ai.service.query;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.context.AiRequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 未证明安全的 AST 结构必须在 EXPLAIN 前拒绝；此类测试不连接数据库。 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class SqlUnsupportedStructureRejectionTest {

    private final ErpQueryService queries = mock(ErpQueryService.class);
    private final SqlSafetyValidator validator = new SqlSafetyValidator(queries, new AiProperties());

    @BeforeEach
    void clearContext() {
        AiRequestContext.clear();
    }

    @AfterEach
    void cleanupContext() {
        AiRequestContext.clear();
    }

    @Test
    void caseSubqueryIsRejectedBeforeExplain() {
        assertRejected("SELECT CASE WHEN 1=1 THEN (SELECT COUNT(*) FROM bd_material m) "
            + "ELSE 0 END AS qty FROM wm_material_stock s", "CASE");
    }

    @Test
    void havingSubqueryIsRejectedBeforeExplain() {
        assertRejected("SELECT s.id FROM wm_material_stock s GROUP BY s.id "
            + "HAVING (SELECT COUNT(*) FROM bd_material m)>1", "HAVING");
    }

    @Test
    void nestedWithDefinitionIsRejectedBeforeExplain() {
        assertRejected("SELECT q.n FROM (WITH bd_c AS (SELECT COUNT(*) AS n, '000000' AS tenant_id "
            + "FROM bd_material m) SELECT n FROM bd_c) q", "WITH");
    }

    @Test
    void parenthesizedCrossJoinIsRejectedBeforeExplain() {
        assertRejected("SELECT m.material_id FROM (wm_material_stock s CROSS JOIN bd_material m) "
            + "ORDER BY m.material_id", "括号");
    }

    static Stream<Arguments> nestedUnsupportedStructures() {
        return Stream.of(
            Arguments.of("SELECT CASE (SELECT COUNT(*) FROM bd_material m) WHEN 1 THEN 1 ELSE 0 END FROM wm_material_stock s", "CASE"),
            Arguments.of("SELECT CASE WHEN EXISTS (SELECT 1 FROM bd_material m) THEN 1 ELSE 0 END FROM wm_material_stock s", "CASE"),
            Arguments.of("SELECT CASE WHEN 1=0 THEN 0 ELSE (SELECT COUNT(*) FROM bd_material m) END FROM wm_material_stock s", "CASE"),
            Arguments.of("SELECT COALESCE(CASE WHEN 1=1 THEN (SELECT COUNT(*) FROM bd_material m) ELSE 0 END,0) FROM wm_material_stock s", "CASE"),
            Arguments.of("SELECT s.id FROM wm_material_stock s WHERE CASE WHEN 1=1 THEN (SELECT COUNT(*) FROM bd_material m) ELSE 0 END > 1", "CASE"),
            Arguments.of("SELECT d.qty FROM (SELECT CASE WHEN 1=1 THEN (SELECT COUNT(*) FROM bd_material m) ELSE 0 END AS qty FROM wm_material_stock s) d", "CASE"),
            Arguments.of("SELECT s.id FROM wm_material_stock s GROUP BY s.id HAVING EXISTS (SELECT 1 FROM bd_material m)", "HAVING"),
            Arguments.of("SELECT d.id FROM (SELECT s.id FROM wm_material_stock s GROUP BY s.id HAVING COALESCE((SELECT COUNT(*) FROM bd_material m),0)>1) d", "HAVING"),
            Arguments.of("SELECT (WITH bd_c AS (SELECT material_id FROM bd_material m) SELECT COUNT(*) FROM bd_c) AS qty FROM wm_material_stock s", "WITH"),
            Arguments.of("SELECT s.id FROM wm_material_stock s WHERE EXISTS (WITH bd_c AS (SELECT material_id FROM bd_material m) SELECT 1 FROM bd_c)", "WITH"),
            Arguments.of("SELECT s.id FROM wm_material_stock s ORDER BY (WITH bd_c AS (SELECT material_id FROM bd_material m) SELECT COUNT(*) FROM bd_c)", "WITH"),
            Arguments.of("SELECT d.n FROM (SELECT q.n FROM (WITH bd_c AS (SELECT COUNT(*) AS n,'000000' AS tenant_id FROM bd_material m) SELECT n FROM bd_c) q) d", "WITH"),
            Arguments.of("SELECT s.id FROM (wm_material_stock s JOIN bd_material m ON s.material_id=m.material_id)", "括号"),
            Arguments.of("SELECT s.id FROM wm_material_stock s JOIN (bd_material m CROSS JOIN bd_material m2) ON s.material_id=m.material_id", "括号"),
            Arguments.of("SELECT d.id FROM (SELECT s.id FROM ((wm_material_stock s JOIN bd_material m ON s.material_id=m.material_id))) d", "括号"),
            Arguments.of("SELECT s.id FROM wm_material_stock s JOIN bd_material m ON m.material_id=s.material_id AND (SELECT COUNT(*) FROM bd_material m2)>1", "JOIN"),
            Arguments.of("SELECT s.id FROM wm_material_stock s LEFT JOIN bd_material m ON m.material_id=s.material_id AND EXISTS (SELECT 1 FROM bd_material m2)", "JOIN")
        );
    }

    @ParameterizedTest(name = "嵌套结构在查询前拒绝：{1}")
    @MethodSource("nestedUnsupportedStructures")
    void nestedUnsupportedStructureCannotReachExplain(String sql, String structure) {
        assertRejected(sql, structure);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT CASE WHEN s.stock_quantity>0 THEN s.id ELSE 0 END AS id FROM wm_material_stock s",
        "SELECT s.material_id,COUNT(*) AS qty FROM wm_material_stock s GROUP BY s.material_id HAVING COUNT(*)>0",
        "SELECT d.material_id FROM (SELECT m.material_id FROM bd_material m) d",
        "SELECT s.id FROM wm_material_stock s WHERE (s.stock_quantity>0 OR 1=1)",
        "SELECT 'WITH CASE HAVING' AS note FROM wm_material_stock s",
        "SELECT s.id FROM wm_material_stock s LEFT JOIN bd_material m ON s.material_id=m.material_id"
    })
    void supportedSyntaxStillReachesExplain(String sql) {
        String safe = assertDoesNotThrow(() -> validator.validateAndSanitize(sql, 20, "000000"));
        assertTrue(safe.contains("tenant_id = '000000'"), safe);
        verify(queries).executeQuery("EXPLAIN " + safe, 1, 2);
        verifyNoMoreInteractions(queries);
    }

    private void assertRejected(String sql, String structure) {
        IllegalArgumentException error = assertThrowsExactly(IllegalArgumentException.class,
            () -> validator.validateAndSanitize(sql, 20, "000000"));
        assertTrue(error.getMessage().contains("不支持"), error.getMessage());
        assertTrue(error.getMessage().contains(structure), error.getMessage());
        // mock 默认允许 EXPLAIN 成功，故失败必须来自真实结构守卫，而非数据库语法报错。
        verifyNoInteractions(queries);
    }
}
