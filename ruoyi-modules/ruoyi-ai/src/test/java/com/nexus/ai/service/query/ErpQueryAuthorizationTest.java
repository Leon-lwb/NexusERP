package com.nexus.ai.service.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.tool.ErpShortageRiskTool;
import com.nexus.common.report.*;
import com.nexus.common.report.catalog.ReportEntityCatalog;
import com.nexus.common.report.catalog.ReportEntityCatalogProvider;
import com.nexus.common.satoken.utils.LoginHelper;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.sql.Connection;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** 通过真实 H2 查询验证 JDBC 边界；仅替换服务端授权端口。 */
class ErpQueryAuthorizationTest {
    private JdbcTemplate jdbc;
    private Connection keepAlive;
    private ErpQueryService query;
    private ReportAuthorizationPort authorization;
    private AtomicReference<ReportActor> actor;
    private AiProperties properties;

    @BeforeEach
    void database() throws Exception {
        AiRequestContext.clear();
        RequestContextHolder.resetRequestAttributes();
        var source = new DriverManagerDataSource("jdbc:h2:mem:query_auth_" + UUID.randomUUID()
            + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        keepAlive = source.getConnection();
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE wm_material_stock(stock_id BIGINT,tenant_id VARCHAR(20),"
            + "del_flag CHAR(1),create_dept BIGINT,create_by BIGINT,material_id BIGINT,"
            + "material_code VARCHAR(40),stock_quantity INT,unit_cost DECIMAL(12,2),internal_note VARCHAR(50))");
        jdbc.update("INSERT INTO wm_material_stock VALUES"
            + "(1,'000000','0',10,100,1,'M1',5,100,'private-one'),"
            + "(2,'000000','0',20,200,2,'M2',7,200,'private-two'),"
            + "(3,'000000','0',20,100,2,'M2',11,300,'private-three'),"
            + "(4,'000001','0',10,100,4,'M4',13,400,'other-tenant'),"
            + "(5,'0','0',10,100,5,'M5',17,500,'short-tenant'),"
            + "(6,'000000','2',10,100,1,'M1',19,600,'deleted'),"
            + "(7,'000000','0',10,200,2,'M2',23,700,'private-seven')");
        jdbc.execute("CREATE TABLE bd_material(material_id BIGINT,tenant_id VARCHAR(20),del_flag CHAR(1),"
            + "create_dept BIGINT,create_by BIGINT,material_name VARCHAR(40),internal_note VARCHAR(50))");
        jdbc.update("INSERT INTO bd_material VALUES"
            + "(1,'000000','0',10,100,'Own','private'),"
            + "(2,'000000','0',20,200,'Other','private'),"
            + "(4,'000001','0',10,100,'Cross','private'),"
            + "(5,'0','0',10,100,'Short','private')");
        jdbc.execute("CREATE TABLE pu_purchase_order(order_id BIGINT,tenant_id VARCHAR(20),del_flag CHAR(1),"
            + "create_dept BIGINT,create_by BIGINT,order_no VARCHAR(40))");
        jdbc.update("INSERT INTO pu_purchase_order VALUES(11,'000000','0',10,100,'P1'),"
            + "(12,'000000','0',20,200,'P2'),(13,'000001','0',10,100,'P3'),(14,'000000','2',10,100,'P4')");
        jdbc.execute("CREATE TABLE pu_purchase_order_line(line_id BIGINT,tenant_id VARCHAR(20),"
            + "del_flag CHAR(1),order_id BIGINT,order_quantity INT)");
        jdbc.update("INSERT INTO pu_purchase_order_line VALUES(21,'000000','0',11,2),"
            + "(22,'000000','0',12,4),(23,'000001','0',11,8),(24,'000000','0',13,16),"
            + "(25,'000000','0',14,32),(26,'000000','2',11,64)");
        jdbc.execute("CREATE TABLE wm_unregistered(id BIGINT,tenant_id VARCHAR(20),secret VARCHAR(50))");
        jdbc.update("INSERT INTO wm_unregistered VALUES(1,'000000','outside-catalog'),(2,'000001','cross')");
        actor = new AtomicReference<>(departmentActor());
        authorization = mock(ReportAuthorizationPort.class);
        when(authorization.resolve(anyString(), anyLong())).thenAnswer(invocation -> actor.get());
        properties = new AiProperties();
        query = new ErpQueryService(jdbc, new ErpQueryAuthorizationGuard(
            authorization, List.<ReportEntityCatalogProvider>of(ErpQueryAuthorizationTest::catalog), properties));
        AiRequestContext.set("000000", 100L);
    }

    @AfterEach
    void close() throws Exception {
        AiRequestContext.clear();
        RequestContextHolder.resetRequestAttributes();
        keepAlive.close();
    }

    @Test
    void missingIdentityCannotReachBusinessRowsOrMetadata() {
        AiRequestContext.clear();
        assertThrows(IllegalArgumentException.class, () -> rows("SELECT stock_id FROM wm_material_stock"));
        assertThrows(IllegalArgumentException.class, query::queryErpTables);
        assertThrows(IllegalArgumentException.class, () -> query.queryTableSchema("wm_material_stock"));
        assertThrows(IllegalArgumentException.class, () -> query.tableExists("wm_material_stock"));
        verifyNoInteractions(authorization);
    }

    @Test
    void departmentScopeProtectsSameTenantRowsAndDeletedRows() {
        assertEquals(List.of(1L, 7L), ids("SELECT stock_id FROM wm_material_stock ORDER BY stock_id"));
        verify(authorization).resolve("000000", 100L);
    }

    @Test
    void selfScopeIncludesOwnRowsInOtherDepartment() {
        actor.set(withRoles(role(1, Set.of("wm:stock:list"), new ReportScope(false, Set.of(), true, 100))));
        assertEquals(List.of(1L, 3L), ids("SELECT stock_id FROM wm_material_stock ORDER BY stock_id"));
    }

    @Test
    void departmentAndSelfScopeUnionStaysInsideExactTenant() {
        actor.set(withRoles(role(1, Set.of("wm:stock:list"), new ReportScope(false, Set.of(10L), true, 100))));
        assertEquals(List.of(1L, 3L, 7L), ids("SELECT stock_id FROM wm_material_stock WHERE 1=1 OR 1=1 ORDER BY stock_id"));
    }

    @Test
    void allScopeIsStillRestrictedToExactTextTenant() {
        actor.set(withRoles(role(1, Set.of("wm:stock:list"), ReportScope.all(100))));
        assertEquals(List.of(1L, 2L, 3L, 7L), ids("SELECT stock_id FROM wm_material_stock ORDER BY stock_id"));
        assertTrue(rows("SELECT stock_id FROM wm_material_stock WHERE tenant_id='0'").isEmpty());
    }

    @Test
    void unrelatedAllScopeCannotWidenBusinessPermission() {
        actor.set(withRoles(role(1, Set.of("wm:stock:list"), departmentScope()),
            role(2, Set.of("ai:assistant:send"), ReportScope.all(100))));
        assertEquals(List.of(1L, 7L), ids("SELECT stock_id FROM wm_material_stock ORDER BY stock_id"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT s.stock_id FROM wm_material_stock s WHERE s.stock_quantity > 100 OR 1=1 ORDER BY s.stock_id",
        "SELECT s.stock_id FROM wm_material_stock s JOIN bd_material m ON m.material_id=s.material_id OR 1=1 GROUP BY s.stock_id ORDER BY s.stock_id",
        "SELECT s.stock_id FROM wm_material_stock s CROSS JOIN bd_material m ORDER BY s.stock_id",
        "SELECT s.stock_id FROM wm_material_stock s, bd_material m ORDER BY s.stock_id"
    })
    void everyPhysicalTableIsProtectedBeforeOrAndJoins(String sql) {
        assertEquals(List.of(1L, 7L), ids(sql));
    }

    @Test
    void outerJoinRetainsOwnRowsWithoutMakingOtherDepartmentVisible() {
        var result = rows("SELECT s.stock_id,m.material_name FROM wm_material_stock s "
            + "LEFT JOIN bd_material m ON m.material_id=s.material_id ORDER BY s.stock_id");
        assertEquals(List.of(1L, 7L), values(result, "stock_id"));
        assertEquals("Own", result.get(0).get("material_name"));
        assertNull(result.get(1).get("material_name"));
    }

    @Test
    void rightJoinCannotExposeUnscopedRows() {
        var result = rows("SELECT s.stock_id,m.material_name FROM wm_material_stock s "
            + "RIGHT JOIN bd_material m ON m.material_id=s.material_id ORDER BY s.stock_id");
        assertEquals(List.of(1L), values(result, "stock_id"));
        assertEquals("Own", result.get(0).get("material_name"));
    }

    @Test
    void aggregateOnlyCountsAuthorizedRowsBeforeHavingAndOrdering() {
        var result = rows("SELECT COUNT(*) AS n,SUM(stock_quantity) AS qty FROM wm_material_stock "
            + "WHERE stock_quantity > 100 OR 1=1 HAVING COUNT(*)>0 ORDER BY qty");
        assertEquals(2L, ((Number) result.get(0).get("n")).longValue());
        assertEquals(28L, ((Number) result.get(0).get("qty")).longValue());
    }

    @Test
    void subqueriesAreProtectedIncludingScalarAndExists() {
        var result = rows("SELECT s.stock_id,(SELECT COUNT(*) FROM bd_material m) AS n "
            + "FROM wm_material_stock s ORDER BY s.stock_id");
        assertEquals(List.of(1L, 7L), values(result, "stock_id"));
        assertEquals(List.of(1L, 1L), values(result, "n"));
        assertEquals(List.of(1L), ids("SELECT s.stock_id FROM wm_material_stock s WHERE EXISTS "
            + "(SELECT 1 FROM bd_material m WHERE m.material_id=s.material_id) ORDER BY s.stock_id"));
    }

    @Test
    void derivedAggregateCannotCountHiddenDepartment() {
        var result = rows("SELECT x.n FROM (SELECT COUNT(*) AS n FROM wm_material_stock) x");
        assertEquals(List.of(2L), values(result, "n"));
    }

    @Test
    void detailScopeComesFromAuthorizedNonDeletedParentAndExactTenant() {
        assertEquals(List.of(21L), values(rows("SELECT line_id FROM pu_purchase_order_line ORDER BY line_id"), "line_id"));
    }

    @Test
    void deniedTableAnywhereInQueryRejectsWholeQuery() {
        actor.set(withRoles(role(1, Set.of("wm:stock:list"), departmentScope())));
        assertThrows(IllegalArgumentException.class, () -> rows("SELECT s.stock_id FROM wm_material_stock s "
            + "LEFT JOIN bd_material m ON m.material_id=s.material_id"));
        assertThrows(IllegalArgumentException.class, () -> rows("SELECT (SELECT COUNT(*) FROM bd_material) FROM wm_material_stock"));
        assertThrows(IllegalArgumentException.class, () -> rows("SELECT id FROM wm_unregistered"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT internal_note FROM wm_material_stock",
        "SELECT stock_id FROM wm_material_stock WHERE internal_note='private-two'",
        "SELECT stock_id FROM wm_material_stock ORDER BY unit_cost",
        "SELECT COUNT(*) FROM wm_material_stock GROUP BY unit_cost",
        "SELECT COUNT(*) FROM wm_material_stock HAVING SUM(unit_cost)>0",
        "SELECT s.stock_id FROM wm_material_stock s JOIN bd_material m ON m.internal_note=s.internal_note",
        "SELECT SUM(unit_cost) FROM wm_material_stock",
        "SELECT tenant_id FROM wm_material_stock"
    })
    void forbiddenFieldsCannotBeSelectedOrUsedToInferRows(String sql) {
        assertThrows(IllegalArgumentException.class, () -> rows(sql));
    }

    @Test
    void wildcardOnlyProjectsApprovedFieldsIncludingInDerivedTables() {
        var result = rows("SELECT s.* FROM wm_material_stock s ORDER BY s.stock_id");
        assertEquals(Set.of("stock_id", "material_id", "material_code", "stock_quantity"), result.get(0).keySet());
        assertFalse(result.toString().contains("private"));
        var derived = rows("SELECT x.* FROM (SELECT * FROM wm_material_stock) x ORDER BY x.stock_id");
        assertEquals(result, derived);
    }

    @Test
    void fieldPermissionUsesItsOwnAssociatedScope() {
        actor.set(withRoles(role(1, Set.of("wm:stock:list"), ReportScope.all(100)),
            role(2, Set.of("wm:stock:query"), new ReportScope(false, Set.of(), true, 100))));
        assertEquals(List.of(1L, 2L, 3L, 7L), ids("SELECT stock_id FROM wm_material_stock ORDER BY stock_id"));
        assertEquals(List.of(1L, 3L), ids("SELECT stock_id,unit_cost FROM wm_material_stock ORDER BY stock_id"));
        assertEquals(List.of(1L, 3L), ids("SELECT * FROM wm_material_stock ORDER BY stock_id"));
    }

    @Test
    void metadataNeverExposesUnapprovedTablesOrFields() {
        actor.set(withRoles(role(1, Set.of("wm:stock:list"), departmentScope())));
        assertEquals(List.of("wm_material_stock"), query.queryErpTables());
        assertTrue(query.tableExists("wm_material_stock"));
        assertFalse(query.tableExists("bd_material"));
        assertFalse(query.tableExists("wm_unregistered"));
        var schema = query.queryTableSchema("wm_material_stock");
        assertTrue(schema.contains("stock_quantity"));
        for (var forbidden : List.of("unit_cost", "internal_note", "create_dept", "create_by", "tenant_id", "del_flag"))
            assertFalse(schema.contains(forbidden), schema);
        assertThrows(IllegalArgumentException.class, () -> query.queryTableSchema("wm_unregistered"));
    }

    @Test
    void cachedExistenceDoesNotCacheAuthorizationOrFieldPermission() {
        assertTrue(query.tableExists("wm_material_stock"));
        actor.set(withRoles());
        assertFalse(query.tableExists("wm_material_stock"));
        assertTrue(query.queryErpTables().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> query.queryTableSchema("wm_material_stock"));
    }

    @Test
    void roleRevocationAndScopeChangesTakeEffectOnEveryExecution() {
        assertEquals(List.of(1L, 7L), ids("SELECT stock_id FROM wm_material_stock ORDER BY stock_id"));
        actor.set(withRoles(role(1, Set.of("wm:stock:list"), new ReportScope(false, Set.of(), true, 100))));
        assertEquals(List.of(1L, 3L), ids("SELECT stock_id FROM wm_material_stock ORDER BY stock_id"));
        actor.set(withRoles());
        assertThrows(IllegalArgumentException.class, () -> rows("SELECT stock_id FROM wm_material_stock"));
        verify(authorization, times(3)).resolve("000000", 100L);
    }

    @Test
    void parameterOrderIsPreservedAcrossProjectionWhereAndLimit() {
        var result = query.executeParameterizedQuery("SELECT ? AS marker,s.stock_id FROM wm_material_stock s "
            + "WHERE s.stock_id>=? AND (s.stock_quantity<? OR s.stock_id=?) ORDER BY s.stock_id LIMIT ?",
            new Object[]{"marker-value", 1, 10, 7, 10}, 20, 2);
        assertEquals(List.of(1L, 7L), values(result, "stock_id"));
        assertEquals(List.of("marker-value", "marker-value"), result.stream().map(r -> r.get("marker")).toList());
    }

    @Test
    void parameterOrderSurvivesNestedQueriesAndParentScope() {
        var result = query.executeParameterizedQuery("SELECT (SELECT COUNT(*) FROM bd_material m "
            + "WHERE m.material_name=?) AS n,l.line_id FROM pu_purchase_order_line l WHERE l.order_quantity>?",
            new Object[]{"Own", 1}, 20, 2);
        assertEquals(List.of(21L), values(result, "line_id"));
        assertEquals(List.of(1L), values(result, "n"));
    }

    @Test
    void callerTenantParametersCannotCoerceOrWidenTrustedTextTenant() {
        for (Object supplied : List.of("000001", "0", 0)) {
            var result = query.executeParameterizedQuery("SELECT stock_id FROM wm_material_stock "
                + "WHERE tenant_id=? OR 1=1 ORDER BY stock_id", new Object[]{supplied}, 20, 2);
            assertEquals(List.of(1L, 7L), values(result, "stock_id"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"000001", "0", " 000000", "000000 ", "", "bad'"})
    void tenantMismatchOrInvalidIdentityIsRejected(String tenant) {
        AiRequestContext.set(tenant, 100L);
        assertThrows(IllegalArgumentException.class, () -> rows("SELECT stock_id FROM wm_material_stock"));
    }

    @Test
    void mismatchedResolvedUserOrTenantAndDisabledAccountAreRejected() {
        actor.set(new ReportActor("000000", 200, 10, false, departmentActor().roles()));
        assertThrows(IllegalArgumentException.class, () -> rows("SELECT stock_id FROM wm_material_stock"));
        actor.set(new ReportActor("000001", 100, 10, false, departmentActor().roles()));
        assertThrows(IllegalArgumentException.class, () -> rows("SELECT stock_id FROM wm_material_stock"));
        when(authorization.resolve(anyString(), anyLong())).thenThrow(new IllegalStateException("private account detail"));
        var error = assertThrows(IllegalArgumentException.class, () -> rows("SELECT stock_id FROM wm_material_stock"));
        assertFalse(error.getMessage().contains("private account"));
    }

    @Test
    void partialContextCannotFallBackToAnotherLogin() {
        AiRequestContext.set("000000", null);
        assertThrows(IllegalArgumentException.class, () -> rows("SELECT stock_id FROM wm_material_stock"));
        AiRequestContext.set(null, 100L);
        assertThrows(IllegalArgumentException.class, () -> rows("SELECT stock_id FROM wm_material_stock"));
        AiRequestContext.set("000000", 0L);
        assertThrows(IllegalArgumentException.class, () -> rows("SELECT stock_id FROM wm_material_stock"));
    }

    @Test
    void loginFallbackRequiresAnExplicitValidSynchronousRequest() {
        AiRequestContext.clear();
        try (var login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::isLogin).thenReturn(true);
            login.when(LoginHelper::getTenantId).thenReturn("000000");
            login.when(LoginHelper::getUserId).thenReturn(100L);
            assertThrows(IllegalArgumentException.class, () -> rows("SELECT stock_id FROM wm_material_stock"));
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
            assertEquals(List.of(1L, 7L), ids("SELECT stock_id FROM wm_material_stock ORDER BY stock_id"));
            login.when(LoginHelper::getUserId).thenReturn(200L);
            AiRequestContext.set("000000", 100L);
            assertThrows(IllegalArgumentException.class, () -> rows("SELECT stock_id FROM wm_material_stock"));
        }
    }

    @Test
    void superadminNeedsLiveIdentityAndStillUsesReadOnlySqlSafetyAndExactTenant() {
        actor.set(new ReportActor("000000", 1, 1, true, List.of()));
        AiRequestContext.set("000000", 1L);
        var result = rows("SELECT secret FROM wm_unregistered");
        assertEquals(List.of("outside-catalog"), result.stream().map(r -> r.get("secret")).toList());
        for (var sql : List.of("DELETE FROM wm_material_stock", "SELECT * FROM INFORMATION_SCHEMA.TABLES",
            "SELECT SLEEP(1)", "SELECT secret FROM wm_unregistered UNION SELECT secret FROM wm_unregistered",
            "SELECT secret FROM wm_unregistered; SELECT 1", "SELECT * FROM system_users"))
            assertThrows(IllegalArgumentException.class, () -> rows(sql), sql);
        AiRequestContext.clear();
        assertThrows(IllegalArgumentException.class, () -> rows("SELECT secret FROM wm_unregistered"));
    }

    @Test
    void explainPreflightUsesSameAuthorizationWithoutReturningPhysicalPlanToRestrictedActor() {
        assertTrue(rows("EXPLAIN SELECT stock_id FROM wm_material_stock").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> rows("EXPLAIN SELECT internal_note FROM wm_material_stock"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "WITH x AS (SELECT stock_id FROM wm_material_stock) SELECT * FROM x",
        "SELECT stock_id FROM wm_material_stock UNION SELECT stock_id FROM wm_material_stock",
        "SELECT * FROM (wm_material_stock s JOIN bd_material m ON s.material_id=m.material_id)",
        "SELECT wm_material_stock.internal_note AS stock_id FROM wm_material_stock",
        "SELECT (SELECT internal_note FROM bd_material m LIMIT 1) FROM wm_material_stock",
        "SELECT stock_id FROM other_schema.wm_material_stock",
        "SELECT @secret FROM wm_material_stock",
        "SELECT stock_id FROM wm_material_stock FOR UPDATE"
    })
    void unsupportedOrUnapprovedStructuresFailClosed(String sql) {
        assertThrows(IllegalArgumentException.class, () -> rows(sql));
    }

    @Test
    void missingOrAmbiguousCatalogAndUnmappableScopeFailClosed() {
        var noCatalog = new ErpQueryService(jdbc, new ErpQueryAuthorizationGuard(authorization, List.of(), properties));
        assertThrows(IllegalArgumentException.class, () -> noCatalog.executeQuery("SELECT stock_id FROM wm_material_stock", 20, 2));
        var duplicate = new ErpQueryService(jdbc, new ErpQueryAuthorizationGuard(authorization,
            List.of(ErpQueryAuthorizationTest::catalog, ErpQueryAuthorizationTest::catalog), properties));
        assertThrows(IllegalArgumentException.class, () -> duplicate.executeQuery("SELECT stock_id FROM wm_material_stock", 20, 2));
        var unmappedEntity = new ReportEntityCatalog.Entity("unmapped", "unmapped", "wm_material_stock", "stock_id",
            "wm:stock:list", "tenant_id", "del_flag", null, null, null,
            List.of(field("id", "stock_id", null)));
        var unmapped = new ErpQueryService(jdbc, new ErpQueryAuthorizationGuard(authorization,
            List.of(() -> new ReportEntityCatalog(1, List.of(unmappedEntity), List.of())), properties));
        assertThrows(IllegalArgumentException.class, () -> unmapped.executeQuery("SELECT stock_id FROM wm_material_stock", 20, 2));
    }


    @Test
    void fixedShortageCteKeepsAdminBehaviorAndProtectsEveryPhysicalSource() {
        actor.set(new ReportActor("000000", 1, 1, true, List.of()));
        AiRequestContext.set("000000", 1L);
        var result = query.executeParameterizedQuery("""
            WITH demand AS (
                SELECT d.material_id,SUM(d.stock_quantity) AS demand_qty
                FROM wm_material_stock d WHERE d.stock_quantity>? AND d.tenant_id=? AND d.del_flag='0'
                GROUP BY d.material_id
            ), stock AS (
                SELECT s.material_id,SUM(s.stock_quantity) AS available_qty
                FROM wm_material_stock s WHERE s.tenant_id=? AND s.del_flag='0' GROUP BY s.material_id
            ), onorder AS (
                SELECT l.order_id,SUM(l.order_quantity) AS on_order_qty
                FROM pu_purchase_order_line l JOIN pu_purchase_order o ON o.order_id=l.order_id
                WHERE o.order_id>? AND o.tenant_id=? GROUP BY l.order_id
            )
            SELECT m.material_name AS 物料名称,dm.demand_qty AS 期内需求,st.available_qty AS 可用库存,
                   CASE WHEN dm.demand_qty>0 THEN '需核对' ELSE '无风险' END AS 建议
            FROM demand dm LEFT JOIN stock st ON st.material_id=dm.material_id
            LEFT JOIN bd_material m ON m.material_id=dm.material_id
            WHERE dm.demand_qty>0 ORDER BY 期内需求 DESC
            """, new Object[]{0, "000000", "000000", 0, "000000"}, 20, 2);
        assertFalse(result.isEmpty());
        assertTrue(result.stream().allMatch(row -> Set.of("Own", "Other").contains(row.get("物料名称"))));
        assertEquals(46L, result.stream().mapToLong(row -> ((Number) row.get("期内需求")).longValue()).sum());
    }

    @Test
    void cteChainingDoesNotExemptAnUnrelatedPhysicalTableOrLoseParameterOrder() {
        actor.set(new ReportActor("000000", 1, 1, true, List.of()));
        AiRequestContext.set("000000", 1L);
        var result = query.executeParameterizedQuery("""
            WITH stock AS (SELECT stock_id,stock_quantity FROM wm_material_stock WHERE stock_id>=?),
                 demand AS (SELECT stock_id FROM stock WHERE stock_quantity<?)
            SELECT ? AS marker,s.stock_id FROM demand s ORDER BY s.stock_id
            """, new Object[]{1, 10, "cte-marker"}, 20, 2);
        assertEquals(List.of(1L, 2L), values(result, "stock_id"));
        assertEquals(List.of("cte-marker", "cte-marker"), result.stream().map(row -> row.get("marker")).toList());
        assertThrows(IllegalArgumentException.class, () -> rows("WITH stock AS (SELECT * FROM system_users) SELECT * FROM stock"));
    }

    @Test
    void approvedExpressionsSupportChineseProjectionAliases() {
        var result = rows("SELECT SUM(stock_quantity) AS 库存数量 FROM wm_material_stock ORDER BY 库存数量");
        assertEquals(28L, ((Number) result.get(0).get("库存数量")).longValue());
    }

    @Test
    void databaseErrorsDoNotExposePhysicalScopeColumnsOrSqlParameters() {
        var error = assertThrows(IllegalArgumentException.class, () -> query.executeParameterizedQuery(
            "SELECT stock_id FROM wm_material_stock WHERE stock_id=?", new Object[]{}, 20, 2));
        assertFalse(error.getMessage().contains("create_dept"));
        assertFalse(error.getMessage().contains("__ai_row"));
    }


    @Test
    void projectionAliasCannotBypassAFieldPermissionsNarrowerScope() {
        actor.set(withRoles(role(1, Set.of("wm:stock:list"), ReportScope.all(100)),
            role(2, Set.of("wm:stock:query"), new ReportScope(false, Set.of(), true, 100))));
        var result = rows("SELECT 0 AS unit_cost,COUNT(*) AS n FROM wm_material_stock GROUP BY unit_cost");
        assertEquals(2L, result.stream().mapToLong(row -> ((Number) row.get("n")).longValue()).sum());
    }

    @Test
    void recursiveFixedBomShapeProtectsBothSeedAndRecursivePhysicalSources() {
        actor.set(new ReportActor("000000", 1, 1, true, List.of()));
        AiRequestContext.set("000000", 1L);
        var result = query.executeParameterizedQuery("""
            WITH RECURSIVE bom_tree(stock_id,material_id,depth) AS (
                SELECT s.stock_id,s.material_id,1 AS depth FROM wm_material_stock s WHERE s.stock_id=?
                UNION ALL
                SELECT child.stock_id,child.material_id,bt.depth+1
                FROM bom_tree bt JOIN wm_material_stock child ON child.stock_id=bt.stock_id+1 WHERE bt.depth<?
            )
            SELECT stock_id FROM bom_tree ORDER BY stock_id
            """, new Object[]{1, 10}, 20, 2);
        assertEquals(List.of(1L, 2L, 3L), values(result, "stock_id"));
    }

    @Test
    void actualFixedShortageToolSqlStillPassesTheBoundaryPolicy() {
        actor.set(new ReportActor("000000", 1, 1, true, List.of()));
        AiRequestContext.set("000000", 1L);
        var capture = mock(ErpQueryService.class);
        when(capture.executeParameterizedQuery(anyString(), any(Object[].class), anyInt(), anyInt())).thenReturn(List.of());
        new ErpShortageRiskTool(capture).queryShortageRisk(14);
        var sql = ArgumentCaptor.forClass(String.class);
        verify(capture).executeParameterizedQuery(sql.capture(), any(Object[].class), anyInt(), anyInt());
        var guard = new ErpQueryAuthorizationGuard(authorization, List.of(ErpQueryAuthorizationTest::catalog), properties);
        assertDoesNotThrow(() -> guard.authorize(sql.getValue()));
    }

    @Test
    void trimKeepsApprovedFieldsAndChineseAliasesAtTheJdbcBoundary() {
        var result = rows("SELECT TRIM(material_code) AS 物料编码,stock_id FROM wm_material_stock ORDER BY stock_id");
        assertEquals(List.of(1L, 7L), values(result, "stock_id"));
        assertEquals(List.of("M1", "M2"), result.stream().map(row -> row.get("物料编码")).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT TRIM(internal_note) FROM wm_material_stock",
        "SELECT stock_id FROM wm_material_stock WHERE TRIM(unit_cost)='100'",
        "SELECT TRIM(internal_note FROM material_code) FROM wm_material_stock",
        "SELECT TRIM(material_code FROM internal_note) FROM wm_material_stock"
    })
    void trimOperandsCannotHideForbiddenFieldReferences(String sql) {
        assertThrows(IllegalArgumentException.class, () -> rows(sql));
    }

    @Test
    void groupConcatExecutesOnlyAuthorizedRowsAndPreservesLiteralSeparatorAndOrdering() {
        var result = rows("SELECT GROUP_CONCAT(material_code ORDER BY stock_id DESC SEPARATOR '|') AS 物料集合 "
            + "FROM wm_material_stock");
        assertEquals("M2|M1", result.get(0).get("物料集合"));
    }

    @Test
    void groupConcatKeepsParameterOrderAcrossItsExpressionsAndOuterPredicates() {
        var result = query.executeParameterizedQuery("SELECT GROUP_CONCAT(CONCAT(?,material_code) "
            + "ORDER BY stock_id DESC SEPARATOR '、') AS 物料集合 FROM wm_material_stock WHERE stock_quantity>?",
            new Object[]{"P:", 0}, 20, 2);
        assertEquals("P:M2、P:M1", result.get(0).get("物料集合"));
    }

    @Test
    void groupConcatOrderingUsesTheFieldsOwnPermissionScope() {
        actor.set(withRoles(role(1, Set.of("wm:stock:list"), ReportScope.all(100)),
            role(2, Set.of("wm:stock:query"), new ReportScope(false, Set.of(), true, 100))));
        var result = rows("SELECT GROUP_CONCAT(stock_id ORDER BY unit_cost SEPARATOR ',') AS ids FROM wm_material_stock");
        assertEquals("1,3", result.get(0).get("ids"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT GROUP_CONCAT(internal_note) FROM wm_material_stock",
        "SELECT GROUP_CONCAT(material_code ORDER BY internal_note) FROM wm_material_stock",
        "SELECT GROUP_CONCAT(material_code ORDER BY unit_cost) FROM wm_material_stock",
        "SELECT GROUP_CONCAT(material_code,internal_note ORDER BY stock_id) FROM wm_material_stock",
        "SELECT GROUP_CONCAT(material_code ORDER BY (SELECT internal_note FROM bd_material LIMIT 1)) FROM wm_material_stock",
        "SELECT 0 AS unit_cost,GROUP_CONCAT(material_code ORDER BY unit_cost) FROM wm_material_stock",
        "SELECT stock_id FROM wm_material_stock GROUP BY stock_id HAVING GROUP_CONCAT(internal_note)<>''",
        "SELECT GROUP_CONCAT(material_code SEPARATOR internal_note) FROM wm_material_stock",
        "SELECT GROUP_CONCAT(material_code SEPARATOR CONCAT(material_code,internal_note)) FROM wm_material_stock",
        "SELECT GROUP_CONCAT(material_code SEPARATOR ?) FROM wm_material_stock"
    })
    void groupConcatCannotHideForbiddenFieldsOrderingOrNonliteralSeparators(String sql) {
        assertThrows(IllegalArgumentException.class, () -> rows(sql));
    }

    private List<Map<String, Object>> rows(String sql) { return query.executeQuery(sql, 100, 2); }
    private List<Long> ids(String sql) { return values(rows(sql), "stock_id"); }
    private static List<Long> values(List<Map<String, Object>> rows, String column) {
        return rows.stream().map(row -> ((Number) row.get(column)).longValue()).toList();
    }
    private static ReportScope departmentScope() { return new ReportScope(false, Set.of(10L), false, 100); }
    private static ReportRole role(long id, Set<String> permissions, ReportScope scope) { return new ReportRole(id, permissions, scope); }
    private static ReportActor withRoles(ReportRole... roles) { return new ReportActor("000000", 100, 10, false, List.of(roles)); }
    static ReportActor departmentActor() {
        return withRoles(role(1, Set.of("wm:stock:list", "bd:material:list", "pu:order:list"), departmentScope()));
    }
    private static ReportEntityCatalog.Field field(String id, String column, String permission) {
        return new ReportEntityCatalog.Field(id, id, column, ValueType.STRING, permission, List.of(), true);
    }
    static ReportEntityCatalog catalog() {
        return new ReportEntityCatalog(1, List.of(
            new ReportEntityCatalog.Entity("stock", "库存", "wm_material_stock", "stock_id", "wm:stock:list", "tenant_id",
                "del_flag", "create_dept", "create_by", null, List.of(field("id", "stock_id", null),
                    field("materialId", "material_id", null), field("materialCode", "material_code", null),
                    field("quantity", "stock_quantity", null), field("cost", "unit_cost", "wm:stock:query"))),
            new ReportEntityCatalog.Entity("material", "物料", "bd_material", "material_id", "bd:material:list", "tenant_id",
                "del_flag", "create_dept", "create_by", null, List.of(field("id", "material_id", null), field("name", "material_name", null))),
            new ReportEntityCatalog.Entity("purchase", "采购", "pu_purchase_order", "order_id", "pu:order:list", "tenant_id",
                "del_flag", "create_dept", "create_by", null, List.of(field("id", "order_id", null), field("number", "order_no", null))),
            new ReportEntityCatalog.Entity("purchase_line", "采购明细", "pu_purchase_order_line", "line_id", "pu:order:list", "tenant_id",
                "del_flag", null, null, new ReportEntityCatalog.ParentPolicy("purchase", "order_id", "order_id"),
                List.of(field("id", "line_id", null), field("orderId", "order_id", null), field("quantity", "order_quantity", null)))
        ), List.of());
    }
}
