package com.nexus.erp.report.adapter;

import static com.nexus.erp.report.adapter.FoundationReportFixture.*;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

/** 基础业务报表使用真实安装字段验证隔离、精度与汇总维度。 */
class FoundationDatasetTest {
    /** 验证物料状态中文化、完整ID过滤及租户、部门、软删除隔离。 */
    @Test
    void materialCatalogUsesActualFieldsAndScopes() throws Exception {
        var f = new FoundationReportFixture("bd_material");
        for (long id = 1; id <= 4; id++)
            f.insert(
                    "bd_material",
                    "material_id",
                    id,
                    "material_code",
                    "000" + id,
                    "material_name",
                    "合成物料" + id,
                    "category_id",
                    1L,
                    "material_type",
                    1,
                    "source_type",
                    "PROCUREMENT",
                    "tenant_id",
                    id == 2 ? "B" : "A",
                    "create_dept",
                    id == 3 ? 20L : 10L,
                    "del_flag",
                    id == 4 ? "1" : "0");
        var p = f.provider("MaterialCatalogDatasetProvider");
        var r = p.query(context("bd:material:list"), query(p, Map.of("materialId", "1")));
        assertEquals(1, r.total());
        assertEquals("0001", r.rows().get(0).get("materialCode"));
        assertEquals("原材料", r.rows().get(0).get("materialType"));
        assertEquals("采购", r.rows().get(0).get("sourceType"));
        assertEquals("启用", r.rows().get(0).get("status"));
        assertEquals(1, p.query(context("bd:material:list"), query(p, Map.of())).total());
        assertThrows(
                DatasetAccessException.class,
                () -> p.query(context("bd:client:list"), query(p, Map.of())));
    }

    /** 验证客户目录没有猜测不存在的状态列，并且不暴露联系人和银行字段。 */
    @Test
    void clientCatalogProjectsOnlyNonSensitiveActualFields() throws Exception {
        var f = new FoundationReportFixture("bd_client");
        f.insert(
                "bd_client",
                "client_id",
                9L,
                "client_code",
                "C009",
                "client_name",
                "合成客户",
                "client_nick",
                "示例",
                "sales_rep_name",
                "业务员",
                "dept_name",
                "业务部");
        var p = f.provider("ClientCatalogDatasetProvider");
        var r = p.query(context("bd:client:list"), query(p, Map.of("clientId", "9")));
        assertEquals(1, r.total());
        assertEquals("合成客户", r.rows().get(0).get("clientName"));
        assertEquals("业务员", r.rows().get(0).get("salesRepName"));
        assertFalse(
                p.descriptor().fields().stream()
                        .anyMatch(
                                x ->
                                        Set.of("status", "tel", "email", "bankAccount")
                                                .contains(x.name())));
    }

    /** 验证供应商采用实际状态、行业和区域字段。 */
    @Test
    void vendorCatalogTranslatesKnownStatusAndRetainsUnknownValues() throws Exception {
        var f = new FoundationReportFixture("bd_vendor");
        f.insert(
                "bd_vendor",
                "vendor_id",
                8L,
                "vendor_code",
                "V008",
                "vendor_name",
                "合成供应商",
                "status",
                "FROZEN",
                "region",
                "华东");
        var p = f.provider("VendorCatalogDatasetProvider");
        var r = p.query(context("bd:vendor:list"), query(p, Map.of("vendorId", "8")));
        assertEquals("冻结", r.rows().get(0).get("status"));
        assertEquals("华东", r.rows().get(0).get("region"));
        f.jdbc.update("UPDATE bd_vendor SET status='CUSTOM'");
        assertEquals(
                "CUSTOM",
                p.query(context("bd:vendor:list"), query(p, Map.of())).rows().get(0).get("status"));
    }

    /** 验证库存流水正确区分出入方向，不将绝对发生量或前后余额作为总和。 */
    @Test
    void stockMovementPreservesDirectionAndNonAdditiveBalances() throws Exception {
        var f = new FoundationReportFixture("wm_material_stock_tx");
        for (int i = 0; i < 3; i++)
            f.insert(
                    "wm_material_stock_tx",
                    "tx_id",
                    i + 1L,
                    "tx_type",
                    "MISC_IN",
                    "material_id",
                    4L,
                    "warehouse_id",
                    5L,
                    "material_stock_id",
                    6L,
                    "material_code",
                    "M004",
                    "material_name",
                    "合成零件",
                    "base_uom_name",
                    "KG",
                    "tx_flag",
                    i == 0 ? 1 : i == 1 ? -1 : 0,
                    "tx_quantity",
                    new BigDecimal("0.1250"),
                    "before_quantity",
                    10,
                    "after_quantity",
                    10,
                    "tx_date",
                    "2026-10-15 23:30:00");
        var p = f.provider("StockMovementDatasetProvider");
        var r =
                p.query(
                        context("wm:stocktx:list"),
                        query(
                                p,
                                Map.of(
                                        "fromDate",
                                        "2026-10-01",
                                        "toDate",
                                        "2026-10-15",
                                        "materialId",
                                        "4",
                                        "warehouseId",
                                        "5")));
        assertEquals(3, r.total());
        assertEquals(
                List.of("入库", "出库", "不影响现存量"),
                r.rows().stream().map(x -> x.get("direction")).toList());
        assertFalse(p.descriptor().field("quantity").aggregatable());
        assertFalse(p.descriptor().field("beforeQuantity").aggregatable());
        assertFalse(p.descriptor().field("afterQuantity").aggregatable());
        assertEquals(
                0, new BigDecimal("0.125").compareTo((BigDecimal) r.rows().get(0).get("quantity")));
        f.jdbc.update("UPDATE wm_material_stock_tx SET tx_quantity=-0.1250 WHERE tx_id=1");
        var raw =
                p.query(
                        context("wm:stocktx:list"),
                        query(p, Map.of("fromDate", "2026-10-01", "toDate", "2026-10-15")));
        assertEquals(
                0,
                new BigDecimal("-0.1250")
                        .compareTo((BigDecimal) raw.rows().get(0).get("quantity")));
    }

    /** 验证销售明细父子租户一致、删除和取消过滤，余量不受退货字段误导。 */
    @Test
    void salesLinesEnforceChildTenantAndCurrencyBoundaries() throws Exception {
        var f = new FoundationReportFixture("so_sales_order_header", "so_sales_order_line");
        f.insert(
                "so_sales_order_header",
                "so_id",
                42L,
                "so_no",
                "SO-42",
                "order_date",
                "2020-01-01",
                "client_id",
                9L,
                "currency",
                "CNY",
                "order_status",
                "CONFIRMED");
        for (int i = 0; i < 5; i++)
            f.insert(
                    "so_sales_order_line",
                    "line_id",
                    i + 1L,
                    "so_id",
                    42L,
                    "material_id",
                    4L,
                    "material_code",
                    "M004",
                    "material_name",
                    "合成物料",
                    "unit",
                    "PCS",
                    "ordered_qty",
                    new BigDecimal("10.1250"),
                    "delivered_qty",
                    new BigDecimal("3.1000"),
                    "returned_qty",
                    new BigDecimal("1.0000"),
                    "unit_price",
                    new BigDecimal("0.123456"),
                    "line_amount",
                    new BigDecimal("1.25"),
                    "tenant_id",
                    i == 1 ? "B" : "A",
                    "del_flag",
                    i == 2 ? "1" : "0",
                    "line_status",
                    i == 3 ? "CANCELLED" : "NORMAL");
        f.jdbc.update("UPDATE so_sales_order_line SET delivered_qty=20 WHERE line_id=5");
        var p = f.provider("SalesOrderLinesDatasetProvider");
        var r =
                p.query(
                        context("so:orderheader:list", "so:orderheader:query"),
                        query(p, Map.of("orderId", "42", "clientId", "9", "materialId", "4")));
        assertEquals(2, r.total());
        assertEquals(
                0,
                new BigDecimal("7.0250")
                        .compareTo((BigDecimal) r.rows().get(0).get("remainingQuantity")));
        assertEquals(
                0,
                BigDecimal.ZERO.compareTo((BigDecimal) r.rows().get(1).get("remainingQuantity")));
        assertEquals(List.of("currency"), p.descriptor().field("lineAmount").aggregationKeys());
        assertEquals(
                List.of("materialCode", "unitName"),
                p.descriptor().field("orderedQuantity").aggregationKeys());
        assertFalse(p.descriptor().field("unitPrice").aggregatable());
        assertThrows(
                DatasetAccessException.class,
                () -> p.query(context("so:orderheader:list"), query(p, Map.of("orderId", "42"))));
        f.jdbc.update("UPDATE so_sales_order_header SET order_status='CANCELLED'");
        assertEquals(
                0,
                p.query(context("so:orderheader:list", "so:orderheader:query"), query(p, Map.of()))
                        .total());
    }

    /** 验证采购到货明细保留原始退货数量并排除异租户及已删除明细。 */
    @Test
    void purchaseLinesEnforceTenantAndFinancialPermission() throws Exception {
        var f = new FoundationReportFixture("pu_purchase_order", "pu_purchase_order_line");
        f.insert(
                "pu_purchase_order",
                "order_id",
                42L,
                "order_no",
                "PO-42",
                "order_date",
                "2020-01-01",
                "vendor_id",
                8L,
                "currency",
                "USD",
                "status",
                "CONFIRMED");
        for (int i = 0; i < 3; i++)
            f.insert(
                    "pu_purchase_order_line",
                    "line_id",
                    i + 1L,
                    "order_id",
                    42L,
                    "material_id",
                    4L,
                    "material_code",
                    "M004",
                    "unit",
                    "KG",
                    "order_quantity",
                    new BigDecimal("0.30"),
                    "received_quantity",
                    new BigDecimal("0.10"),
                    "returned_quantity",
                    new BigDecimal("0.05"),
                    "line_amount",
                    new BigDecimal("0.03"),
                    "tenant_id",
                    i == 1 ? "B" : "A",
                    "del_flag",
                    i == 2 ? "1" : "0");
        var p = f.provider("PurchaseOrderLinesDatasetProvider");
        var r =
                p.query(
                        context("pu:order:list", "pu:order:query"),
                        query(p, Map.of("orderId", "42", "vendorId", "8", "materialId", "4")));
        assertEquals(1, r.total());
        assertEquals("USD", r.rows().get(0).get("currency"));
        assertEquals(
                0,
                new BigDecimal("0.20")
                        .compareTo((BigDecimal) r.rows().get(0).get("remainingQuantity")));
        assertEquals(
                0,
                new BigDecimal("0.05")
                        .compareTo((BigDecimal) r.rows().get(0).get("returnedQuantity")));
        assertEquals(List.of("currency"), p.descriptor().field("lineAmount").aggregationKeys());
        assertFalse(p.descriptor().field("unitPrice").aggregatable());
        assertThrows(
                DatasetAccessException.class,
                () -> p.query(context("pu:order:list"), query(p, Map.of())));
        f.jdbc.update("UPDATE pu_purchase_order SET status='CANCELLED'");
        assertEquals(
                0, p.query(context("pu:order:list", "pu:order:query"), query(p, Map.of())).total());
    }
}
