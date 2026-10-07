package com.nexus.ai.service.query;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.context.AiRequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** SQL 租户身份与既有只读安全链路回归；不连接数据库。 */
class SqlSafetyValidatorTenantTest {

    private final ErpQueryService queries = mock(ErpQueryService.class);
    private final AiProperties properties = new AiProperties();
    private final SqlSafetyValidator validator = new SqlSafetyValidator(queries, properties);

    @BeforeEach
    void clearContext() {
        AiRequestContext.clear();
        properties.setSqlMaxRows(25);
    }

    @AfterEach
    void cleanupContext() {
        AiRequestContext.clear();
    }

    @Test
    void explicitTenantContractIsStringWithoutAmbiguousOverloads() {
        var methods = Arrays.stream(SqlSafetyValidator.class.getMethods())
            .filter(method -> method.getName().equals("validateAndSanitize") && method.getParameterCount() == 3)
            .toList();
        assertEquals(1, methods.size());
        assertEquals(String.class, methods.get(0).getParameterTypes()[2]);
    }

    @Test
    void rewritesQuotedDefaultTenantAsQuotedString() {
        String sql = validator.validateAndSanitize(
            "SELECT s.id FROM wm_material_stock s WHERE s.tenant_id = '999999'", 5, "000000");

        assertEquals("SELECT s.id FROM wm_material_stock s WHERE s.tenant_id = '000000' AND s.tenant_id = '000000' LIMIT 5", sql);
        verify(queries).executeQuery("EXPLAIN " + sql, 1, 2);
    }

    @Test
    void rewritesNumericTenantAsQuotedString() {
        String sql = validator.validateAndSanitize(
            "SELECT id FROM wm_material_stock WHERE tenant_id = 999999", 5, "000123");

        assertTrue(sql.contains("tenant_id = '000123'"), sql);
        assertTrue(sql.contains("wm_material_stock.tenant_id = '000123'"), sql);
    }

    @Test
    void rewritesEveryExplicitTenantPredicateInJoin() {
        String sql = validator.validateAndSanitize(
            "SELECT s.id FROM wm_material_stock s JOIN bd_material m ON m.id = s.material_id "
                + "AND m.tenant_id = '999999' WHERE s.tenant_id = 999999", 5, "000000");

        assertTrue(sql.contains("m.tenant_id = '000000'"), sql);
        assertTrue(sql.contains("WHERE s.tenant_id = '000000' AND s.tenant_id = '000000'"), sql);
        assertFalse(sql.contains("999999"), sql);
    }

    @Test
    void missingTenantFailsClosedBeforeExplain() {
        var error = assertThrows(IllegalArgumentException.class,
            () -> validator.validateAndSanitize("SELECT id FROM wm_material_stock", 5, null));

        assertTrue(error.getMessage().contains("租户"));
        verifyNoInteractions(queries);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "INSERT INTO wm_material_stock VALUES (1)", "UPDATE wm_material_stock SET id = 1",
        "DELETE FROM wm_material_stock", "DROP TABLE wm_material_stock"
    })
    void nonSelectIsStillRejected(String sql) {
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSanitize(sql, 5, "100001"));
        verifyNoInteractions(queries);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT SLEEP(1) FROM wm_material_stock", "SELECT LOAD_FILE('/etc/passwd') FROM wm_material_stock",
        "SELECT * FROM wm_material_stock INTO OUTFILE '/tmp/out'",
        "SELECT id FROM wm_material_stock UNION SELECT id FROM bd_material",
        "SELECT id FROM wm_material_stock UNION ALL SELECT id FROM bd_material"
    })
    void forbiddenFunctionsAndUnionRemainRejected(String sql) {
        assertThrows(IllegalArgumentException.class, () -> validator.validateAndSanitize(sql, 5, "100001"));
        verifyNoInteractions(queries);
    }

    @Test
    void disallowedTableRemainsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> validator.validateAndSanitize("SELECT id FROM outside_table", 5, "100001"));
        verify(queries, never()).executeQuery(anyString(), anyInt(), anyInt());
    }

    @Test
    void implicitAndNullExplicitTenantUseNonNullContextWithoutNormalization() throws NoSuchMethodException {
        // 同时兼容隔离基线的 Long 上下文和主线程即将合入的 String 上下文。
        boolean stringContext = AiRequestContext.class.getMethod("getTenantId").getReturnType() == String.class;
        String tenantId = stringContext ? "000123" : "123";
        String expected = "SELECT id FROM wm_material_stock WHERE wm_material_stock.tenant_id = '" + tenantId + "' LIMIT 5";
        try (var context = mockStatic(AiRequestContext.class)) {
            context.when(AiRequestContext::getTenantId).thenAnswer(invocation -> stringContext ? tenantId : Long.valueOf(tenantId));
            assertEquals(expected, validator.validateAndSanitize("SELECT id FROM wm_material_stock", 5));
            assertEquals(expected, validator.validateAndSanitize("SELECT id FROM wm_material_stock", 5, null));
        }
        verify(queries, times(2)).executeQuery("EXPLAIN " + expected, 1, 2);
    }

    @Test
    void explicitTenantDoesNotReadOrOverrideFromContext() {
        try (var context = mockStatic(AiRequestContext.class)) {
            String sql = validator.validateAndSanitize("SELECT id FROM wm_material_stock", 5, "tenant-A");
            assertTrue(sql.contains("tenant_id = 'tenant-A'"), sql);
            context.verifyNoInteractions();
        }
    }

    @Test
    void blankExplicitTenantCannotFallBackToContext() {
        try (var context = mockStatic(AiRequestContext.class)) {
            assertThrowsExactly(IllegalArgumentException.class,
                () -> validator.validateAndSanitize("SELECT id FROM wm_material_stock", 5, ""));
            context.verifyNoInteractions();
        }
        verifyNoInteractions(queries);
    }

    @Test
    void implicitMissingContextNeverBecomesLiteralNull() {
        assertThrowsExactly(IllegalArgumentException.class,
            () -> validator.validateAndSanitize("SELECT id FROM wm_material_stock", 5));
        verifyNoInteractions(queries);
    }

    @ParameterizedTest
    @ValueSource(strings = {"000000", "000123", "0007", "00123456", "7", "tenant-A", "ACME_01"})
    void preservesExactTenantIdentityWithoutPaddingOrNumericCoercion(String tenantId) {
        String expected = "SELECT id FROM wm_material_stock WHERE wm_material_stock.tenant_id = '" + tenantId + "' LIMIT 5";
        assertEquals(expected, validator.validateAndSanitize("SELECT id FROM wm_material_stock", 5, tenantId));
        verify(queries).executeQuery("EXPLAIN " + expected, 1, 2);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", " 000000", "000000 ", "000000'", "0' OR '1'='1", "tenant\\A",
        "tenant$1", "tenant;SELECT", "tenant-- A", "tenant/**/A", "tenant\nA", "tenant\"A"})
    void invalidOrMissingTenantFailsBeforeExplain(String tenantId) {
        var error = assertThrowsExactly(IllegalArgumentException.class,
            () -> validator.validateAndSanitize("SELECT id FROM wm_material_stock", 5, tenantId));
        assertTrue(error.getMessage().contains("租户"));
        verifyNoInteractions(queries);
    }


    @Test
    void topLevelOrCannotBypassTenantGuard() {
        String sql = validator.validateAndSanitize(
            "SELECT s.id FROM wm_material_stock s WHERE s.stock_quantity > 0 OR 1 = 1", 5, "000000");

        assertFalse(sql.matches("(?is).*WHERE\\s+s\\.tenant_id\\s*=\\s*'000000'\\s+AND\\s+s\\.stock_quantity.*OR\\s+1\\s*=\\s*1.*"), sql);
        assertTrue(sql.contains("s.tenant_id = '000000'"), sql);
        assertTrue(sql.contains("OR 1 = 1"), sql);
    }

    @Test
    void everyJoinedAndNestedSourceReceivesTenantGuard() {
        String sql = validator.validateAndSanitize(
            "SELECT s.id FROM wm_material_stock s CROSS JOIN bd_material m "
                + "WHERE s.material_id IN (SELECT m2.material_id FROM bd_material m2 WHERE m2.enabled = 1)",
            5, "000000");

        assertTrue(sql.contains("s.tenant_id = '000000'"), sql);
        assertTrue(sql.contains("m.tenant_id = '000000'"), sql);
        assertTrue(sql.contains("m2.tenant_id = '000000'"), sql);
    }

    @Test
    void selectingTenantColumnOrHidingItInCommentsDoesNotSkipGuard() {
        String sql = validator.validateAndSanitize(
            "SELECT tenant_id, update_time FROM wm_material_stock /*\n tenant_id = '999999' \n*/", 5, "000000");

        assertTrue(sql.contains("WHERE wm_material_stock.tenant_id = '000000'"), sql);
        assertFalse(sql.contains("999999"), sql);
        assertFalse(sql.contains("/*"), sql);
    }

    @Test
    void limitIsStillForcedAndExplainErrorPropagates() {
        String sql = validator.validateAndSanitize("SELECT id FROM wm_material_stock LIMIT 9999", null, "000000");
        assertTrue(sql.endsWith(" LIMIT 25"), sql);
        assertFalse(sql.contains("9999"), sql);

        when(queries.executeQuery(anyString(), eq(1), eq(2))).thenThrow(new IllegalStateException("bad syntax"));
        var error = assertThrows(IllegalArgumentException.class,
            () -> validator.validateAndSanitize("SELECT id FROM wm_material_stock", 5, "000000"));
        assertTrue(error.getMessage().contains("bad syntax"));
    }
}
