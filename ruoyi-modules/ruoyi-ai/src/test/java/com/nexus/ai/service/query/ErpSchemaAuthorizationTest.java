package com.nexus.ai.service.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.service.prompt.PromptTemplateService;
import com.nexus.ai.tool.BuiltinToolRegistry;
import com.nexus.ai.tool.QueryAllTablesTool;
import com.nexus.ai.tool.QueryTableSchemaTool;
import com.nexus.common.ai.domain.entity.AiPromptTemplate;
import com.nexus.common.ai.service.IAiPromptTemplateService;
import com.nexus.common.report.*;
import com.nexus.common.tenant.helper.TenantHelper;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/** 私有缓存可以无身份预热；工具、提示词及直接元数据输出均按当前服务端授权过滤。 */
class ErpSchemaAuthorizationTest {
    private ErpTableSchemaManager manager;
    private ReportAuthorizationPort port;
    private AtomicReference<ReportActor> actor;
    private AiProperties properties;
    private Map<String, ErpTableSchemaManager.TableStructure> cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void cache() {
        RequestContextHolder.resetRequestAttributes();
        AiRequestContext.set("000000", 100L);
        properties = new AiProperties();
        actor = new AtomicReference<>(stockActor(false));
        port = mock(ReportAuthorizationPort.class);
        when(port.resolve(anyString(), anyLong())).thenAnswer(invocation -> actor.get());
        manager = new ErpTableSchemaManager();
        ReflectionTestUtils.setField(manager, "aiProperties", properties);
        ReflectionTestUtils.setField(manager, "authorization", new ErpQueryAuthorizationGuard(
            port, List.of(ErpQueryAuthorizationTest::catalog), properties));
        ReflectionTestUtils.setField(manager, "initialized", true);
        ReflectionTestUtils.setField(manager, "lastRefreshTime", System.currentTimeMillis());
        cache = (Map<String, ErpTableSchemaManager.TableStructure>) ReflectionTestUtils.getField(manager, "schemaCache");
        cache.put("wm_material_stock", raw("wm_material_stock", "stock_id", "material_id", "material_code",
            "stock_quantity", "unit_cost", "internal_note", "tenant_id", "create_by", "create_dept", "del_flag"));
        cache.put("bd_material", raw("bd_material", "material_id", "material_name", "internal_note"));
        cache.put("wm_unregistered", raw("wm_unregistered", "secret"));
    }

    @AfterEach
    void identity() { AiRequestContext.clear(); RequestContextHolder.resetRequestAttributes(); }

    @Test
    void rawCacheCannotBeReadWithoutIdentity() {
        AiRequestContext.clear();
        assertThrows(IllegalArgumentException.class, manager::getAllTablesSummary);
        assertThrows(IllegalArgumentException.class, manager::getAllTableSchemas);
        assertThrows(IllegalArgumentException.class, manager::getTableCount);
        assertThrows(IllegalArgumentException.class, () -> manager.getTableSchema("wm_material_stock"));
        assertThrows(IllegalArgumentException.class, manager::getCoreSchemaSummary);
        assertThrows(IllegalArgumentException.class, manager::getFullSchemaSummary);
        assertThrows(IllegalArgumentException.class, () -> manager.getOptimizedSchemaSummary(4000));
        assertThrows(IllegalArgumentException.class, () -> manager.formatEnhancedTableSummary(cache.get("wm_material_stock")));
        verifyNoInteractions(port);
    }

    @Test
    void everySummaryAndSnapshotUsesApprovedTablesAndFields() {
        assertEquals(1, manager.getTableCount());
        assertEquals(List.of("wm_material_stock"), manager.getAllTableSchemas().stream()
            .map(ErpTableSchemaManager.TableStructure::getTableName).toList());
        assertNull(manager.getTableSchema("bd_material"));
        assertNull(manager.getTableSchema("wm_unregistered"));
        var snapshot = manager.getTableSchema("wm_material_stock");
        assertEquals(
                Set.of("stock_id", "material_id", "material_code", "stock_quantity"),
                snapshot.getColumns().stream()
                        .map(ErpTableSchemaManager.ColumnInfo::getColumnName)
                        .collect(Collectors.toSet()));
        assertNull(snapshot.getDdl());
        assertTrue(snapshot.getForeignKeys().isEmpty());
        for (String output : List.of(manager.getAllTablesSummary(), manager.getCoreSchemaSummary(),
            manager.getFullSchemaSummary(), manager.getOptimizedSchemaSummary(4000), manager.getOptimizedSchemaSummary(40),
            manager.formatEnhancedTableSummary(cache.get("wm_material_stock")), snapshot.toString())) assertRestricted(output);
        assertTrue(manager.getFullSchemaSummary().contains("stock_quantity"));
    }

    @Test
    void metadataGrantRevocationAndStaleSnapshotsAreRechecked() {
        actor.set(stockActor(true));
        var old = manager.getTableSchema("wm_material_stock");
        assertTrue(manager.formatEnhancedTableSummary(old).contains("unit_cost"));
        actor.set(stockActor(false));
        assertFalse(manager.formatEnhancedTableSummary(old).contains("unit_cost"));
        assertFalse(manager.getFullSchemaSummary().contains("unit_cost"));
        actor.set(new ReportActor("000000", 100, 10, false, List.of()));
        assertTrue(manager.getAllTablesSummary().isEmpty());
        assertEquals(0, manager.getTableCount());
        assertNull(manager.getTableSchema("wm_material_stock"));
        assertThrows(IllegalArgumentException.class, () -> manager.formatEnhancedTableSummary(old));
    }

    @Test
    void detachedObjectsCannotModifyThePrivateCacheOrSmuggleFieldsIntoFormatting() {
        var view = manager.getTableSchema("wm_material_stock");
        view.getColumns().get(0).setColumnName("internal_note");
        view.setDdl("private-ddl");
        view.setTableComment("private-comment");
        assertRestricted(manager.formatEnhancedTableSummary(view));
        assertEquals("stock_id", manager.getTableSchema("wm_material_stock").getColumns().get(0).getColumnName());
        assertTrue(cache.get("wm_material_stock").getDdl().contains("internal_note"));
    }

    @Test
    void realToolCacheHitsNeverExposeRawFieldsOrForeignKeys() {
        var jdbc = mock(ErpQueryService.class);
        var schemaTool = new QueryTableSchemaTool(manager, jdbc, properties);
        var tableTool = new QueryAllTablesTool(manager);
        var output = schemaTool.queryTableSchema("wm_material_stock");
        assertTrue(output.contains("stock_quantity"));
        assertRestricted(output);
        assertRestricted(tableTool.queryAllTables());
        verifyNoInteractions(jdbc);
        actor.set(new ReportActor("000000", 100, 10, false, List.of()));
        assertTrue(tableTool.queryAllTables().contains("No ERP business tables"));
    }

    @Test
    void promptsReauthorizeSchemaEvenWhenTheTemplateIsWarm() {
        try (var tenants = mockStatic(TenantHelper.class)) {
            tenants.when(TenantHelper::getTenantId).thenReturn("000000");
            var templates = mock(IAiPromptTemplateService.class);
            var template = new AiPromptTemplate();
            template.setContent("{{schemaSummary}}");
            when(templates.selectByTemplateKey("erp_report_agent")).thenReturn(template);
            var prompt = new PromptTemplateService(templates, manager, mock(BuiltinToolRegistry.class), mock(SqlExampleService.class));
            actor.set(stockActor(true));
            assertTrue(prompt.renderReportAgentContext().contains("unit_cost"));
            actor.set(stockActor(false));
            assertRestricted(prompt.renderReportAgentContext());
            actor.set(new ReportActor("000000", 100, 10, false, List.of()));
            assertEquals("", prompt.renderReportAgentContext());
            verify(templates).selectByTemplateKey("erp_report_agent");
        }
    }

    @Test
    void mismatchedTenantAndDisabledIdentityCannotReadWarmMetadata() {
        actor.set(new ReportActor("000001", 100, 10, false, stockActor(false).roles()));
        assertThrows(IllegalArgumentException.class, manager::getAllTablesSummary);
        actor.set(stockActor(false));
        when(port.resolve(anyString(), anyLong())).thenThrow(new IllegalStateException("private account data"));
        var error = assertThrows(IllegalArgumentException.class, manager::getFullSchemaSummary);
        assertFalse(error.getMessage().contains("private account data"));
    }

    @Test
    void administratorGetsDetachedFullMetadataAfterLiveIdentityResolution() {
        actor.set(new ReportActor("000000", 1, 1, true, List.of()));
        AiRequestContext.set("000000", 1L);
        var view = manager.getTableSchema("wm_material_stock");
        assertTrue(view.getDdl().contains("internal_note"));
        assertEquals(1, view.getForeignKeys().size());
        assertEquals(3, manager.getTableCount());
        view.getColumns().clear();
        view.getForeignKeys().clear();
        assertFalse(cache.get("wm_material_stock").getColumns().isEmpty());
        assertFalse(cache.get("wm_material_stock").getForeignKeys().isEmpty());
        verify(port, times(2)).resolve("000000", 1L);
    }

    @Test
    void startupCanPopulatePrivateCacheWithoutAnyUser() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:metadata_auth_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE wm_material_stock(stock_id BIGINT,internal_note VARCHAR(20))");
            ReflectionTestUtils.setField(manager, "erpDataSource", source);
            ReflectionTestUtils.setField(manager, "initialized", false);
            cache.clear();
            AiRequestContext.clear();
            manager.initializeSchema();
            assertTrue(manager.isInitialized());
            assertTrue(cache.containsKey("wm_material_stock"));
            assertThrows(IllegalArgumentException.class, manager::getTableCount);
            AiRequestContext.set("000000", 100L);
            assertEquals(List.of("stock_id"), manager.getTableSchema("wm_material_stock").getColumns().stream()
                .map(ErpTableSchemaManager.ColumnInfo::getColumnName).toList());
        }
    }

    @Test
    void startupLoggerDoesNotCallAnAuthorizedPublicCountWithoutIdentity() {
        AiRequestContext.clear();
        var startupCache = spy(manager);
        new ErpTableSchemaInitializer(startupCache).onApplicationReady();
        verify(startupCache, never()).getTableCount();
    }

    private static void assertRestricted(String output) {
        for (String forbidden : List.of("internal_note", "unit_cost", "create_dept", "create_by", "tenant_id", "del_flag",
            "wm_unregistered", "private-comment", "private-enum", "private-default", "private-ddl"))
            assertFalse(output.contains(forbidden), output);
    }

    private static ReportActor stockActor(boolean cost) {
        Set<String> permissions = cost ? Set.of("wm:stock:list", "wm:stock:query") : Set.of("wm:stock:list");
        return new ReportActor("000000", 100, 10, false,
            List.of(new ReportRole(1, permissions, new ReportScope(false, Set.of(10L), false, 100))));
    }

    private static ErpTableSchemaManager.TableStructure raw(String name, String... fields) {
        var raw = new ErpTableSchemaManager.TableStructure();
        raw.setTableName(name);
        raw.setTableComment("private-comment internal_note");
        raw.setDdl("private-ddl internal_note unit_cost");
        raw.setPrimaryKey("internal_note");
        raw.setPrimaryKeyColumns(List.of("internal_note"));
        raw.setColumns(Arrays.stream(fields).map(field -> {
            var column = new ErpTableSchemaManager.ColumnInfo();
            column.setColumnName(field);
            column.setColumnType("VARCHAR");
            column.setColumnComment("private-comment internal_note");
            column.setDefaultValue("private-default");
            column.setEnumValues("private-enum");
            return column;
        }).toList());
        var foreignKey = new ErpTableSchemaManager.ForeignKeyRelation();
        foreignKey.setColumnName("material_id");
        foreignKey.setReferencedTable("wm_unregistered");
        foreignKey.setReferencedColumn("internal_note");
        raw.setForeignKeys(List.of(foreignKey));
        return raw;
    }
}
