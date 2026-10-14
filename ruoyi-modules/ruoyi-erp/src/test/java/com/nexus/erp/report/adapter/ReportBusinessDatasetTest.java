package com.nexus.erp.report.adapter;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.*;

/** 报表Business数据集测试，负责当前模块对应的契约或处理职责。 */
class ReportBusinessDatasetTest {
    /**
     * 验证 {@code database} 场景下的结果与边界条件。
     *
     * @return 当前处理返回的固定结果
     */
    private JdbcTemplate database() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return new JdbcTemplate(ds);
    }

    /**
     * 构造当前身份的业务执行范围。
     *
     * @param permission 待核对权限
     * @return 当前处理返回的固定结果
     */
    private ReportExecutionContext context(String permission) {
        var actor =
                new ReportActor(
                        "A",
                        7,
                        10,
                        false,
                        List.of(
                                new ReportRole(
                                        2,
                                        Set.of(permission),
                                        new ReportScope(false, Set.of(10L), false, 7))));
        return new ReportExecutionContext(actor, List.of(ReportScope.all(7)));
    }

    /**
     * 验证 {@code inventoryUsesTheActualStockQuantityAndPreservesUnitPartitionAndOwnership}
     * 场景下的结果与边界条件。
     */
    @Test
    void inventoryUsesTheActualStockQuantityAndPreservesUnitPartitionAndOwnership() {
        var jdbc = database();
        jdbc.execute(
                "CREATE TABLE wm_material_stock(stock_id BIGINT,tenant_id VARCHAR(20),create_dept"
                        + " BIGINT,create_by BIGINT,del_flag CHAR,material_code"
                        + " VARCHAR(50),stock_quantity DECIMAL(20,6),base_uom_name VARCHAR(20))");
        jdbc.update(
                "INSERT INTO wm_material_stock"
                    + " VALUES(1,'A',10,7,'0','0001',0.1,'KG'),(2,'B',10,7,'0','0002',0.2,'KG'),(3,'A',20,8,'0','0003',0.3,'PCS')");
        var provider = new InventoryBalanceDatasetProvider(jdbc);
        var result =
                provider.query(
                        context("wm:stock:list"),
                        new DatasetQuery(
                                Map.of(),
                                Set.of("stockId", "materialCode", "quantity", "unitName"),
                                1,
                                50,
                                false,
                                null,
                                false));
        assertEquals(1, result.total());
        assertEquals("0001", result.rows().get(0).get("materialCode"));
        assertEquals(
                0,
                new BigDecimal("0.1").compareTo((BigDecimal) result.rows().get(0).get("quantity")));
        assertEquals(
                List.of("materialCode", "unitName"),
                provider.descriptor().field("quantity").aggregationKeys());
        assertFalse(provider.descriptor().field("totalCost").aggregatable());
    }

    /** 验证 {@code purchasePrintCannotJoinALineFromAnotherTenant} 场景下的结果与边界条件。 */
    @Test
    void purchasePrintCannotJoinALineFromAnotherTenant() {
        var jdbc = database();
        jdbc.execute(
                "CREATE TABLE pu_purchase_order(order_id BIGINT,tenant_id VARCHAR(20),create_dept"
                        + " BIGINT,create_by BIGINT,del_flag CHAR,order_no VARCHAR(50),currency"
                        + " VARCHAR(10))");
        jdbc.execute(
                "CREATE TABLE pu_purchase_order_line(line_id BIGINT,order_id BIGINT,tenant_id"
                        + " VARCHAR(20),del_flag CHAR,material_code VARCHAR(50),order_quantity"
                        + " DECIMAL(20,6),unit VARCHAR(20))");
        jdbc.update("INSERT INTO pu_purchase_order VALUES(42,'A',10,7,'0','PO-TEST','CNY')");
        jdbc.update(
                "INSERT INTO pu_purchase_order_line"
                        + " VALUES(1,42,'A','0','M-A',1,'PCS'),(2,42,'B','0','M-B',2,'PCS')");
        var result =
                new PurchaseOrderDocumentDatasetProvider(jdbc)
                        .query(
                                context("pu:order:query"),
                                new DatasetQuery(
                                        Map.of("orderId", "42"),
                                        Set.of(
                                                "orderNo",
                                                "materialCode",
                                                "quantity",
                                                "unitName",
                                                "currency"),
                                        1,
                                        50,
                                        false,
                                        null,
                                        false));
        assertEquals(1, result.total());
        assertEquals("M-A", result.rows().get(0).get("materialCode"));
        assertEquals("CNY", result.rows().get(0).get("currency"));
    }
}
