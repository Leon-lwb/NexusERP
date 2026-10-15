package com.nexus.common.report;

import static org.junit.jupiter.api.Assertions.*;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/** 通过隔离H2验证候选检索和按值回显的真实SQL及权限边界。 */
class JdbcReportLookupProviderTest {
    /** 仅属于当前测试的内存数据连接。 */
    private JdbcTemplate jdbc;

    /** 测试绑定不接收客户端物理表名。 */
    private final ReportLookupBinding binding =
            new ReportLookupBinding("TEST", 1, "entityId", "业务对象", "test:entity:list");

    /** 构建跨租户、跨部门及已删除的合成候选。 */
    @BeforeEach
    void database() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute(
                "CREATE TABLE lookup_entity(entity_id BIGINT,entity_code VARCHAR(100),entity_name"
                        + " VARCHAR(100),tenant_id VARCHAR(20),del_flag CHAR(1),create_dept"
                        + " BIGINT,create_by BIGINT)");
        jdbc.update(
                "INSERT INTO lookup_entity VALUES"
                    + " (9223372036854775807,'CODE-A','合成对象','A','0',10,7),(2,'CODE-B','其他部门','A','0',20,8),(3,'CODE-C','其他租户','B','0',10,7),(4,'CODE-D','已删除','A','2',10,7),(5,'CODE-%_!','通配符样本','A','0',10,7)");
    }

    /** 业务候选范围由该实体对应的权限限定。 */
    private ReportExecutionContext context() {
        var actor =
                new ReportActor(
                        "A",
                        7,
                        10,
                        false,
                        List.of(
                                new ReportRole(
                                        1,
                                        Set.of(binding.permission()),
                                        new ReportScope(false, Set.of(10L), false, 7))));
        return new ReportExecutionContext(actor, List.of(ReportScope.all(7)));
    }

    /** 只安装固定可信标识符，测试实际公共候选底座。 */
    private JdbcReportLookupProvider provider() {
        return new JdbcReportLookupProvider(
                jdbc,
                List.of(
                        new JdbcReportLookupProvider.Rule(
                                binding,
                                "lookup_entity",
                                "entity_id",
                                "entity_code",
                                "entity_name",
                                ValueType.ID))) {};
    }

    /** 名称、编码及完整雪花ID都能找到同一个有权访问的对象。 */
    @Test
    void searchesNamesCodesAndExactIdsWithoutPrecisionLoss() {
        var provider = provider();
        for (String keyword : List.of("合成对象", "CODE-A", "9223372036854775807")) {
            var result =
                    provider.query(
                            binding, context(), new ReportLookupProvider.Query(keyword, 1, 20));
            assertEquals(1, result.total());
            assertEquals("9223372036854775807", result.options().get(0).value());
            assertEquals("CODE-A · 合成对象", result.options().get(0).label());
        }
    }

    /** 按值回显不返回其他租户、部门或已删除对象。 */
    @Test
    void resolvesOnlyCurrentTenantAndBusinessScope() {
        var provider = provider();
        assertEquals(
                "CODE-A · 合成对象",
                provider.selected(binding, context(), "9223372036854775807").orElseThrow().label());
        for (String id : List.of("2", "3", "4", "999"))
            assertTrue(provider.selected(binding, context(), id).isEmpty());
    }

    /** SQL注入及LIKE通配符只作为绑定参数参与字面查询。 */
    @Test
    void escapesSearchAndRejectsInvalidIds() {
        var provider = provider();
        assertEquals(
                0,
                provider.query(
                                binding,
                                context(),
                                new ReportLookupProvider.Query("%' OR 1=1 --", 1, 20))
                        .total());
        assertEquals(
                1,
                provider.query(binding, context(), new ReportLookupProvider.Query("%_!", 1, 20))
                        .total());
        for (String id : List.of("1 OR 1=1", "1e18", "-1", "0", "9223372036854775808"))
            assertThrows(
                    DatasetAccessException.class, () -> provider.selected(binding, context(), id));
    }

    /** 即使上下文带全量范围，没有实体权限仍不能检索或回显。 */
    @Test
    void requiresEntityPermissionForBothOperations() {
        var denied =
                new ReportExecutionContext(
                        new ReportActor("A", 7, 10, false, List.of()), List.of(ReportScope.all(7)));
        assertThrows(
                DatasetAccessException.class,
                () -> provider().query(binding, denied, new ReportLookupProvider.Query("", 1, 20)));
        assertThrows(
                DatasetAccessException.class,
                () -> provider().selected(binding, denied, "9223372036854775807"));
    }

    /** 分页按主键稳定排序，候选总数不包含不可访问记录。 */
    @Test
    void pagesVisibleCandidatesWithoutDuplicates() {
        var provider = provider();
        var first = provider.query(binding, context(), new ReportLookupProvider.Query("", 1, 1));
        var second = provider.query(binding, context(), new ReportLookupProvider.Query("", 2, 1));
        assertEquals(2, first.total());
        assertEquals(2, second.total());
        assertNotEquals(first.options().get(0).value(), second.options().get(0).value());
    }

    /** 编码型参数的前导零保持为文本，不按数值ID解析。 */
    @Test
    void codeSelectionsPreserveLeadingZeros() {
        jdbc.update(
                "UPDATE lookup_entity SET entity_code='000001' WHERE"
                        + " entity_id=9223372036854775807");
        var codeBinding =
                new ReportLookupBinding("TEST", 1, "materialCode", "物料", binding.permission());
        var provider =
                new JdbcReportLookupProvider(
                        jdbc,
                        List.of(
                                new JdbcReportLookupProvider.Rule(
                                        codeBinding,
                                        "lookup_entity",
                                        "entity_code",
                                        "entity_code",
                                        "entity_name",
                                        ValueType.STRING))) {};
        assertEquals(
                "000001",
                provider.selected(codeBinding, context(), "000001").orElseThrow().value());
    }

    /** 注册时拒绝物理标识符注入和重复规则。 */
    @Test
    void rejectsUntrustedOrDuplicateRules() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new JdbcReportLookupProvider.Rule(
                                binding,
                                "lookup_entity;DROP TABLE x",
                                "entity_id",
                                "entity_code",
                                "entity_name",
                                ValueType.ID));
        var rule =
                new JdbcReportLookupProvider.Rule(
                        binding,
                        "lookup_entity",
                        "entity_id",
                        "entity_code",
                        "entity_name",
                        ValueType.ID);
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcReportLookupProvider(jdbc, List.of(rule, rule)) {});
    }
}
