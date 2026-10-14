package com.nexus.common.report;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

/** 报表Contracts测试，负责当前模块对应的契约或处理职责。 */
class ReportContractsTest {
    /** 验证 {@code decimalAndSnowflakeValuesRemainExact} 场景下的结果与边界条件。 */
    @Test
    void decimalAndSnowflakeValuesRemainExact() {
        assertEquals(
                "9007199254740993.123400",
                TypedValue.of(ValueType.DECIMAL, new BigDecimal("9007199254740993.123400"))
                        .value());
        assertEquals("9223372036854775807", TypedValue.of(ValueType.ID, Long.MAX_VALUE).value());
        assertNull(TypedValue.of(ValueType.DECIMAL, null).value());
    }

    /** 验证 {@code rowScopeIsAUnionOfDepartmentsAndSelfButNeverAnUnspecifiedAll} 场景下的结果与边界条件。 */
    @Test
    void rowScopeIsAUnionOfDepartmentsAndSelfButNeverAnUnspecifiedAll() {
        var scope = new ReportScope(false, Set.of(10L), true, 7L);
        assertTrue(scope.allows(10L, 99L));
        assertTrue(scope.allows(20L, 7L));
        assertFalse(scope.allows(20L, 99L));
        assertFalse(ReportScope.none(7L).allows(null, null));
    }

    /** 验证 {@code onlyRolesGrantingTheBusinessPermissionContributeItsDataRange} 场景下的结果与边界条件。 */
    @Test
    void onlyRolesGrantingTheBusinessPermissionContributeItsDataRange() {
        var broadReportRole =
                new ReportRole(1L, Set.of("report:definition:list"), ReportScope.all(7L));
        var limitedStockRole =
                new ReportRole(
                        2L,
                        Set.of("wm:stock:list"),
                        new ReportScope(false, Set.of(10L), false, 7L));
        var actor =
                new ReportActor("A", 7L, 10L, false, List.of(broadReportRole, limitedStockRole));
        assertTrue(actor.has("wm:stock:list"));
        assertFalse(actor.scopeFor("wm:stock:list").allows(20L, 99L));
        assertTrue(actor.scopeFor("wm:stock:list").allows(10L, 99L));
        assertFalse(actor.has("wm:stock:remove"));
    }

    /** 验证 {@code contextAndResultDefensivelyCopyMutableInputs} 场景下的结果与边界条件。 */
    @Test
    void contextAndResultDefensivelyCopyMutableInputs() {
        var role = new ReportRole(1L, Set.of("*:*:*"), ReportScope.all(7L));
        var actor = new ReportActor("A", 7L, 10L, false, List.of(role));
        var params = new HashMap<String, Object>();
        params.put("code", "001");
        var query = new DatasetQuery(params, Set.of("code"), 1, 50, false, null, false);
        params.put("code", "changed");
        assertEquals("001", query.parameters().get("code"));
        assertThrows(
                UnsupportedOperationException.class, () -> query.parameters().put("code", "bad"));
        assertTrue(actor.has("wm:stock:list"));
    }

    /** 验证 {@code sqlScopeAlwaysBindsTenantAndIntersectsSensitiveFieldScopes} 场景下的结果与边界条件。 */
    @Test
    void sqlScopeAlwaysBindsTenantAndIntersectsSensitiveFieldScopes() {
        var actor = new ReportActor("A", 7L, 10L, true, List.of());
        var context =
                new ReportExecutionContext(
                        actor,
                        List.of(
                                ReportScope.all(7L),
                                new ReportScope(false, Set.of(10L), false, 7L)));
        var clause = ReportSqlScope.where("s", context);
        assertTrue(clause.sql().contains("s.tenant_id = ?"));
        assertTrue(clause.sql().contains("s.create_dept IN (?)"));
        assertEquals(List.of("A", 10L), clause.parameters());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReportActor("", 7L, 10L, true, List.of()));
    }
}
