package com.nexus.common.report;

import static org.junit.jupiter.api.Assertions.*;

import org.aopalliance.intercept.MethodInterceptor;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 只读取数报表提供者测试，负责当前模块对应的契约或处理职责。 */
class JdbcReportProviderTest {
    /**
     * 验证 {@code database} 场景下的结果与边界条件。
     *
     * @return 当前处理返回的固定结果
     */
    private JdbcTemplate database() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(ds);
        jdbc.execute(
                "CREATE TABLE stock(id BIGINT PRIMARY KEY,tenant_id VARCHAR(20),create_dept"
                        + " BIGINT,create_by BIGINT,code VARCHAR(100),amount DECIMAL(20,6),del_flag"
                        + " CHAR DEFAULT '0')");
        jdbc.update(
                "INSERT INTO stock VALUES"
                    + " (1,'A',10,7,'0001',0.1,'0'),(2,'A',20,8,'0002',0.2,'0'),(3,'B',10,7,'0003',0.3,'0')");
        return jdbc;
    }

    /** 提供者，负责当前模块对应的契约或处理职责。 */
    static class Provider extends JdbcDatasetProvider {
        /**
         * 初始化提供者并保存所需依赖或不可变值。
         *
         * @param jdbc 当前操作的 {@code jdbc} 输入
         */
        Provider(JdbcTemplate jdbc) {
            super(
                    jdbc,
                    new DatasetDescriptor(
                            "TEST",
                            "测试",
                            1,
                            "wm:stock:list",
                            false,
                            List.of(
                                    new DatasetField("id", "ID", ValueType.ID),
                                    new DatasetField("code", "编码", ValueType.STRING),
                                    new DatasetField(
                                            "amount",
                                            "成本",
                                            ValueType.DECIMAL,
                                            "wm:stock:query",
                                            List.of())),
                            List.of(new DatasetParameter("code", "编码", ValueType.STRING, false)),
                            List.of("id", "code")),
                    "stock s",
                    "s",
                    "s.id",
                    new LinkedHashMap<>(
                            Map.of("id", "s.id", "code", "s.code", "amount", "s.amount")));
        }

        /**
         * 生成契约允许的绑定筛选条件。
         *
         * @param query 已声明字段和筛选参数
         * @return 当前处理返回的固定结果
         */
        protected List<Filter> filters(DatasetQuery query) {
            return query.parameters().get("code") == null
                    ? List.of()
                    : List.of(new Filter("s.code = ?", List.of(query.parameters().get("code"))));
        }
    }

    /** 验证 {@code inheritedProviderMethodsRemainProxyableForDatasourceRouting} 场景下的结果与边界条件。 */
    @Test
    void inheritedProviderMethodsRemainProxyableForDatasourceRouting() {
        var calls = new AtomicInteger();
        var factory = new ProxyFactory();
        factory.setTarget(new Provider(database()));
        factory.setProxyTargetClass(true);
        factory.addAdvice(
                (MethodInterceptor)
                        invocation -> {
                            calls.incrementAndGet();
                            return invocation.proceed();
                        });
        var proxy = (ReportDatasetProvider) factory.getProxy();
        assertNotNull(proxy.descriptor());
        var actor = new ReportActor("A", 7, 10, true, List.of());
        proxy.query(
                new ReportExecutionContext(actor, List.of(ReportScope.all(7))),
                new DatasetQuery(Map.of(), Set.of("id"), 1, 50, false, null, false));
        assertTrue(
                calls.get() >= 2,
                "descriptor and query must pass through Spring's datasource advisor");
    }

    /**
     * 验证 {@code jdbcResultsRemainTenantAndBusinessRoleScopedDespiteAnOverbroadCallerScope}
     * 场景下的结果与边界条件。
     */
    @Test
    void jdbcResultsRemainTenantAndBusinessRoleScopedDespiteAnOverbroadCallerScope() {
        var actor =
                new ReportActor(
                        "A",
                        7,
                        10,
                        false,
                        List.of(
                                new ReportRole(
                                        1, Set.of("report:definition:list"), ReportScope.all(7)),
                                new ReportRole(
                                        2,
                                        Set.of("wm:stock:list"),
                                        new ReportScope(false, Set.of(10L), false, 7))));
        var context = new ReportExecutionContext(actor, List.of(ReportScope.all(7)));
        var result =
                new Provider(database())
                        .query(
                                context,
                                new DatasetQuery(
                                        Map.of(), Set.of("id", "code"), 1, 50, false, null, false));
        assertEquals(1, result.total());
        assertEquals(1L, result.rows().get(0).get("id"));
        assertEquals("0001", result.rows().get(0).get("code"));
    }

    /** 验证 {@code sensitiveFieldScopesIntersectAndValuesAreBoundAsData} 场景下的结果与边界条件。 */
    @Test
    void sensitiveFieldScopesIntersectAndValuesAreBoundAsData() {
        var actor =
                new ReportActor(
                        "A",
                        7,
                        10,
                        false,
                        List.of(
                                new ReportRole(1, Set.of("wm:stock:list"), ReportScope.all(7)),
                                new ReportRole(
                                        2,
                                        Set.of("wm:stock:query"),
                                        new ReportScope(false, Set.of(20L), false, 7))));
        var provider = new Provider(database());
        var context = new ReportExecutionContext(actor, List.of(ReportScope.all(7)));
        var result =
                provider.query(
                        context,
                        new DatasetQuery(
                                Map.of(), Set.of("id", "amount"), 1, 50, false, null, false));
        assertEquals(1, result.total());
        assertEquals(2L, result.rows().get(0).get("id"));
        assertEquals(
                0,
                provider.query(
                                context,
                                new DatasetQuery(
                                        Map.of("code", "x' OR '1'='1"),
                                        Set.of("id"),
                                        1,
                                        50,
                                        false,
                                        null,
                                        false))
                        .total());
    }

    /** 验证 {@code streamHasTheSamePolicyAndUnknownParametersOrSortsCannotReachJdbc} 场景下的结果与边界条件。 */
    @Test
    void streamHasTheSamePolicyAndUnknownParametersOrSortsCannotReachJdbc() {
        var actor = new ReportActor("A", 7, 10, true, List.of());
        var context = new ReportExecutionContext(actor, List.of(ReportScope.all(7)));
        var provider = new Provider(database());
        var count = new AtomicInteger();
        provider.stream(
                context,
                new DatasetQuery(Map.of(), Set.of("id"), 1, 100001, true, null, false),
                row -> count.incrementAndGet());
        assertEquals(2, count.get());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        provider.query(
                                context,
                                new DatasetQuery(
                                        Map.of("tenantId", "B"),
                                        Set.of("id"),
                                        1,
                                        50,
                                        false,
                                        null,
                                        false)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        provider.query(
                                context,
                                new DatasetQuery(
                                        Map.of(), Set.of("id"), 1, 50, false, "id;DROP", false)));
    }
}
