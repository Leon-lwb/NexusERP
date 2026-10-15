package com.nexus.erp.report;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.erp.report.adapter.ErpReportLookupProvider;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/** 报表选择器提供者测试，负责当前模块对应的契约或处理职责。 */
class ReportLookupProviderTest {
    /** 验证 {@code lookupBindsSearchAndEveryTenantAndDepartmentScope} 场景下的结果与边界条件。 */
    @Test
    void lookupBindsSearchAndEveryTenantAndDepartmentScope() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute(
                "CREATE TABLE pu_purchase_order(order_id BIGINT,order_no VARCHAR(100),vendor_name"
                        + " VARCHAR(100),tenant_id VARCHAR(20),del_flag CHAR(1),create_dept"
                        + " BIGINT,create_by BIGINT)");
        jdbc.update(
                "INSERT INTO pu_purchase_order VALUES"
                    + " (9223372036854775807,'PO001','供应商','A','0',10,7),(2,'PO002','其他部门','A','0',20,8),(3,'PO003','其他租户','B','0',10,7),(4,'PO004','删除','A','2',10,7)");
        var actor =
                new ReportActor(
                        "A",
                        7,
                        10,
                        false,
                        List.of(
                                new ReportRole(
                                        1,
                                        Set.of("pu:order:query"),
                                        new ReportScope(false, Set.of(10L), false, 7))));
        var provider = new ErpReportLookupProvider(jdbc);
        var binding =
                provider.bindings().stream()
                        .filter(b -> b.parameter().equals("orderId"))
                        .findFirst()
                        .orElseThrow();
        var result =
                provider.query(
                        binding,
                        new ReportExecutionContext(
                                actor, List.of(actor.scopeFor("pu:order:query"))),
                        new ReportLookupProvider.Query("PO", 1, 20));
        assertEquals(1, result.total());
        assertEquals("9223372036854775807", result.options().get(0).value());
        assertEquals(
                0,
                provider.query(
                                binding,
                                new ReportExecutionContext(
                                        actor, List.of(actor.scopeFor("pu:order:query"))),
                                new ReportLookupProvider.Query("%' OR 1=1 --", 1, 20))
                        .total());
        assertThrows(
                DatasetAccessException.class,
                () ->
                        provider.query(
                                binding,
                                new ReportExecutionContext(
                                        new ReportActor("A", 9, 10, false, List.of()),
                                        List.of(ReportScope.all(9))),
                                new ReportLookupProvider.Query("", 1, 20)));
    }
}
