package com.nexus.ai.service.query;

import com.nexus.ai.config.AiProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 表存在不是授权；所有 AST 物理表须遵守同一前缀白名单，测试不连接数据库。 */
@Tag("local")
@Tag("dev")
@Tag("prod")
class SqlTablePrefixWhitelistTest {

    private final ErpQueryService queries = mock(ErpQueryService.class);
    private final AiProperties properties = new AiProperties();
    private final SqlSafetyValidator validator = new SqlSafetyValidator(queries, properties);

    @ParameterizedTest
    @ValueSource(strings = {"sys_user", "sys_tenant", "custom_report", "ai_message", "mes_work_order", "iot_device"})
    void existingTableOutsideConfiguredPrefixesIsStillRejected(String table) {
        when(queries.tableExists(anyString())).thenReturn(true);
        assertWhitelistRejected("SELECT id FROM " + table);
    }

    static Stream<Arguments> missingOrInvalidPrefixes() {
        return Stream.of(
            Arguments.of((Object) null),
            Arguments.of(List.of()),
            Arguments.of(List.of("")),
            Arguments.of(List.of(" ")),
            Arguments.of(Arrays.asList("wm_", null)),
            Arguments.of(List.of("wm_", "")),
            Arguments.of(List.of("wm_", " ")),
            Arguments.of(List.of("wm_", "wm_;SELECT"))
        );
    }

    @ParameterizedTest
    @MethodSource("missingOrInvalidPrefixes")
    void invalidWhitelistConfigurationFailsClosedEvenForAllowedTable(List<String> prefixes) {
        properties.setAllowedTablePrefixes(prefixes);
        IllegalArgumentException error = assertThrowsExactly(IllegalArgumentException.class,
            () -> validator.validateAndSanitize("SELECT id FROM wm_material_stock", 5, "000000"));
        assertTrue(error.getMessage().contains("白名单"), error.getMessage());
        verifyNoInteractions(queries);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT s.id FROM wm_material_stock s, sys_user u",
        "SELECT s.id FROM wm_material_stock s, custom_report r",
        "SELECT s.id FROM wm_material_stock s, bd_material m, sys_user u",
        "SELECT s.id FROM sys_user u, wm_material_stock s",
        "SELECT s.id FROM wm_material_stock s JOIN custom_report r ON r.id=s.id",
        "SELECT s.id FROM wm_material_stock s LEFT JOIN sys_user u ON u.id=s.id",
        "SELECT s.id FROM wm_material_stock s CROSS JOIN sys_user u",
        "SELECT s.id FROM wm_material_stock s WHERE EXISTS (SELECT 1 FROM sys_user u WHERE u.id=s.id)",
        "SELECT (SELECT COUNT(*) FROM custom_report r) AS qty FROM wm_material_stock s",
        "SELECT q.id FROM (SELECT u.id FROM sys_user u) q",
        "SELECT s.id FROM wm_material_stock s WHERE s.id IN (SELECT m.material_id FROM bd_material m, custom_report r)",
        "SELECT 'FROM wm_material_stock' AS note FROM sys_user u"
    })
    void everyPhysicalTableIncludingCommaAndNestedSourcesMustBeAuthorized(String sql) {
        when(queries.tableExists(anyString())).thenReturn(true);
        assertWhitelistRejected(sql);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bd_material", "so_order", "pu_order", "prd_work_order", "wm_material_stock", "sch_shift"})
    void defaultSixErpPrefixesRemainAuthorized(String table) {
        String safe = assertDoesNotThrow(() -> validator.validateAndSanitize("SELECT id FROM " + table, 5, "000000"));
        assertTrue(safe.contains(table + ".tenant_id = '000000'"), safe);
        assertOnlyExplain(safe);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT 'FROM sys_user JOIN custom_report' AS note FROM wm_material_stock s",
        "SELECT 'FROM sys_user' AS note",
        "SELECT s.id FROM `wm_material_stock` s",
        "SELECT s.id FROM WM_MATERIAL_STOCK s",
        "SELECT s.id FROM wm_material_stock s, bd_material m",
        "SELECT q.id FROM (SELECT s.id FROM wm_material_stock s) q",
        "SELECT (SELECT COUNT(*) FROM bd_material m) AS qty FROM wm_material_stock s"
    })
    void literalTextAndNormalAuthorizedAstSourcesDoNotCauseFalseRejection(String sql) {
        String safe = assertDoesNotThrow(() -> validator.validateAndSanitize(sql, 5, "000000"));
        assertOnlyExplain(safe);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT s.id FROM wm_schema.sys_user s",
        "SELECT s.id FROM erp.wm_material_stock s",
        "SELECT s.id FROM `erp`.`wm_material_stock` s",
        "SELECT s.id FROM `wm_fake.sys_user` s"
    })
    void schemaQualifiedOrAmbiguousPhysicalIdentifiersAreRejectedBeforeExplain(String sql) {
        when(queries.tableExists(anyString())).thenReturn(true);
        IllegalArgumentException error = assertThrowsExactly(IllegalArgumentException.class,
            () -> validator.validateAndSanitize(sql, 5, "000000"));
        assertTrue(error.getMessage().contains("不支持"), error.getMessage());
        verify(queries, never()).executeQuery(anyString(), anyInt(), anyInt());
        verify(queries, never()).tableExists(anyString());
    }

    @Test
    void configuredSubsetCannotExpandThroughExistingTables() {
        properties.setAllowedTablePrefixes(List.of("wm_"));
        when(queries.tableExists(anyString())).thenReturn(true);
        assertWhitelistRejected("SELECT material_id FROM bd_material");
    }

    private void assertWhitelistRejected(String sql) {
        IllegalArgumentException error = assertThrowsExactly(IllegalArgumentException.class,
            () -> validator.validateAndSanitize(sql, 5, "000000"));
        assertTrue(error.getMessage().contains("白名单"), error.getMessage());
        // 既不探测数据库表存在性，也不能调用 EXPLAIN 或实际查询。
        verify(queries, never()).tableExists(anyString());
        verify(queries, never()).queryErpTables();
        verify(queries, never()).executeQuery(anyString(), anyInt(), anyInt());
    }

    private void assertOnlyExplain(String sql) {
        verify(queries).executeQuery("EXPLAIN " + sql, 1, 2);
        verify(queries, never()).tableExists(anyString());
        verify(queries, never()).queryErpTables();
        verifyNoMoreInteractions(queries);
    }
}
