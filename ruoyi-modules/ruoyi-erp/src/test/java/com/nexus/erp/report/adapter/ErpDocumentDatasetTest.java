package com.nexus.erp.report.adapter;

import static com.nexus.erp.report.adapter.FoundationReportFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

/** 使用安装结构和纯合成记录验证新单据的字段、权限与隔离边界。 */
class ErpDocumentDatasetTest {
    /** 验证销售单按真实主键关联明细，保留交期、币种和精确金额。 */
    @Test
    void salesDocumentProjectsActualHeaderAndLineValues() throws Exception {
        var fixture = salesFixture();
        var provider = provider(fixture, "SalesOrderDocumentDatasetProvider");
        var result = provider.query(context("so:orderheader:query"), allFields(provider, "orderId"));

        assertEquals(1, result.total());
        var row = result.rows().get(0);
        assertEquals("SO-SYNTHETIC", row.get("orderNo"));
        assertEquals("合成客户", row.get("clientName"));
        assertEquals("2026-10-17", row.get("orderDate").toString().substring(0, 10));
        assertEquals("2026-10-25", row.get("promiseDate").toString().substring(0, 10));
        assertEquals("2026-10-24", row.get("requestedDate").toString().substring(0, 10));
        assertEquals("USD", row.get("currency"));
        assertEquals("MAT-SYNTHETIC", row.get("materialCode"));
        assertEquals("合成物料", row.get("materialName"));
        assertEquals("SPEC-SYNTHETIC", row.get("specification"));
        assertEquals("PCS", row.get("unitName"));
        assertDecimal("0.1250", row.get("quantity"));
        assertDecimal("1.234567", row.get("unitPrice"));
        assertDecimal("0.15", row.get("lineAmount"));
        assertEquals(Long.MAX_VALUE, ((Number) row.get("lineId")).longValue());
    }

    /** 验证入库单读取实际收货表和实收数量，不依赖采购金额表或成本字段。 */
    @Test
    void receiveDocumentProjectsActualReceiptWithoutPurchaseAmounts() throws Exception {
        var fixture = receiveFixture();
        var provider = provider(fixture, "PurchaseReceiveDocumentDatasetProvider");
        var result = provider.query(context("wm:receive:query"), allFields(provider, "receiveId"));

        assertEquals(1, result.total());
        var row = result.rows().get(0);
        assertEquals("RECEIVE-SYNTHETIC", row.get("receiveNo"));
        assertEquals("合成供应商", row.get("vendorName"));
        assertEquals("PO-SYNTHETIC", row.get("purchaseOrderNo"));
        assertEquals("合成仓库", row.get("warehouseName"));
        assertEquals("2026-10-17", row.get("receiveDate").toString().substring(0, 10));
        assertEquals("MAT-SYNTHETIC", row.get("materialCode"));
        assertEquals("合成物料", row.get("materialName"));
        assertEquals("SPEC-SYNTHETIC", row.get("specification"));
        assertEquals("KG", row.get("unitName"));
        assertEquals("BATCH-SYNTHETIC", row.get("batchCode"));
        assertDecimal("0.1250", row.get("quantity"));
        assertTrue(
                provider.descriptor().fields().stream()
                        .noneMatch(field -> field.name().contains("Amount")
                                || field.name().contains("Price")
                                || field.name().contains("Cost")
                                || field.name().equals("currency")));
        assertThrows(
                DatasetAccessException.class,
                () -> provider.query(
                        context("wm:receive:query"),
                        new DatasetQuery(
                                Map.of("receiveId", "42"), Set.of("unitPrice"),
                                1, 20, false, null, false)));
    }

    /** 验证两个新单据均声明必填完整ID，并拒绝列表权限、未知参数及注入值。 */
    @Test
    void documentsRequireTheirOwnExactIdAndDetailPermission() throws Exception {
        var fixtures = List.of(salesFixture(), receiveFixture());
        var names = List.of("SalesOrderDocumentDatasetProvider", "PurchaseReceiveDocumentDatasetProvider");
        var codes = List.of("ERP_SALES_ORDER_DOCUMENT", "ERP_PURCHASE_RECEIVE_DOCUMENT");
        var parameters = List.of("orderId", "receiveId");
        var permissions = List.of("so:orderheader:query", "wm:receive:query");
        var listPermissions = List.of("so:orderheader:list", "wm:receive:list");
        for (int index = 0; index < names.size(); index++) {
            var provider = provider(fixtures.get(index), names.get(index));
            var descriptor = provider.descriptor();
            String parameter = parameters.get(index);
            var allowed = context(permissions.get(index));
            assertEquals(codes.get(index), descriptor.code());
            assertTrue(descriptor.document());
            assertEquals(1, descriptor.version());
            assertEquals(permissions.get(index), descriptor.permission());
            assertEquals(List.of(new DatasetParameter(parameter, descriptor.parameters().get(0).label(),
                    ValueType.ID, true, null)), descriptor.parameters());
            assertThrows(DatasetAccessException.class,
                    () -> provider.query(allowed, query(provider, Map.of())));
            for (String invalid : List.of("", "0", "42 OR 1=1", "42.0", "9223372036854775808")) {
                var failure = assertThrows(DatasetAccessException.class,
                        () -> provider.query(allowed, query(provider, Map.of(parameter, invalid))));
                assertEquals("INVALID_PARAMETER", failure.code());
            }
            assertThrows(DatasetAccessException.class,
                    () -> provider.query(allowed, query(provider, Map.of(parameter, "42", "sql", "1=1"))));
            var denied = context(listPermissions.get(index));
            assertEquals("SCOPE_DENIED", assertThrows(DatasetAccessException.class,
                    () -> provider.query(denied, query(provider, Map.of(parameter, "42")))).code());
        }
    }

    /** 验证表头的租户、删除、部门权限及资源范围均约束整张单据。 */
    @Test
    void documentsIntersectHeaderOwnershipWithEveryResourceScope() throws Exception {
        var fixtures = List.of(salesFixture(), receiveFixture());
        var names = List.of("SalesOrderDocumentDatasetProvider", "PurchaseReceiveDocumentDatasetProvider");
        var tables = List.of("so_sales_order_header", "wm_material_receive");
        var parameters = List.of("orderId", "receiveId");
        var permissions = List.of("so:orderheader:query", "wm:receive:query");
        for (int index = 0; index < names.size(); index++) {
            var fixture = fixtures.get(index);
            var provider = provider(fixture, names.get(index));
            var allowed = context(permissions.get(index));
            var request = query(provider, Map.of(parameters.get(index), "42"));
            assertEquals(1, provider.query(allowed, request).total());
            var narrowed = new ReportExecutionContext(allowed.actor(),
                    List.of(new ReportScope(false, Set.of(20L), false, 7)));
            assertEquals(0, provider.query(narrowed, request).total());
            for (String assignment : List.of("tenant_id='B'", "del_flag='1'", "create_dept=20")) {
                fixture.jdbc.update("UPDATE " + tables.get(index) + " SET " + assignment);
                assertEquals(0, provider.query(allowed, request).total());
                fixture.jdbc.update("UPDATE " + tables.get(index)
                        + " SET tenant_id='A',del_flag='0',create_dept=10");
            }
        }
    }

    /** 验证销售金额继承既有明细权限和汇总维度，默认列不自动暴露金额。 */
    @Test
    void salesMoneyContractMatchesExistingLinesAndDefaultsStaySafe() throws Exception {
        var fixture = salesFixture();
        var provider = provider(fixture, "SalesOrderDocumentDatasetProvider");
        var descriptor = provider.descriptor();
        var existing = new SalesOrderLinesDatasetProvider(fixture.jdbc).descriptor();
        for (String name : List.of("unitPrice", "lineAmount")) {
            var expected = existing.field(name);
            var actual = descriptor.field(name);
            assertEquals(expected.permission(), actual.permission());
            assertEquals(expected.type(), actual.type());
            assertEquals(expected.aggregationKeys(), actual.aggregationKeys());
            assertEquals(expected.aggregatable(), actual.aggregatable());
            assertFalse(descriptor.defaultColumns().contains(name));
        }
        assertEquals(List.of("materialCode", "unitName"), descriptor.field("quantity").aggregationKeys());
        assertEquals(List.of("materialCode", "materialName", "specification", "quantity", "unitName"),
                descriptor.defaultColumns());
    }

    /** 验证采购实收量仅按物料和单位汇总，批次保留在安全默认列。 */
    @Test
    void receiveDefaultsKeepBatchAndQuantityDimensions() throws Exception {
        var provider = provider(receiveFixture(), "PurchaseReceiveDocumentDatasetProvider");
        assertEquals(List.of("materialCode", "unitName"),
                provider.descriptor().field("quantity").aggregationKeys());
        assertEquals(
                List.of("materialCode", "materialName", "specification", "quantity", "unitName", "batchCode"),
                provider.descriptor().defaultColumns());
    }

    /**
     * 构建真实销售表结构以及同主键跨租户、已删除和孤立的合成明细。
     *
     * @return 只包含合成记录的内存数据库
     * @throws Exception 测试表结构构造失败
     */
    private FoundationReportFixture salesFixture() throws Exception {
        var fixture = new FoundationReportFixture("so_sales_order_header", "so_sales_order_line");
        fixture.insert("so_sales_order_header", "so_id", 42, "so_no", "SO-SYNTHETIC",
                "client_name", "合成客户", "order_date", "2026-10-17", "promise_date", "2026-10-25",
                "requested_date", "2026-10-24", "currency", "USD");
        for (int index = 0; index < 4; index++) {
            fixture.insert("so_sales_order_line", "line_id", index == 0 ? Long.MAX_VALUE : index,
                    "so_id", index == 3 ? 43 : 42, "material_code", "MAT-SYNTHETIC",
                    "material_name", "合成物料", "material_spec", "SPEC-SYNTHETIC", "unit", "PCS",
                    "ordered_qty", new BigDecimal("0.1250"), "unit_price", new BigDecimal("1.234567"),
                    "line_amount", new BigDecimal("0.15"), "promise_date", "2026-11-01",
                    "tenant_id", index == 1 ? "B" : "A", "del_flag", index == 2 ? "1" : "0");
        }
        return fixture;
    }

    /**
     * 构建真实收货表结构，不建立采购订单表以验证无额外金额查询。
     *
     * @return 只包含合成记录的内存数据库
     * @throws Exception 测试表结构构造失败
     */
    private FoundationReportFixture receiveFixture() throws Exception {
        var fixture = new FoundationReportFixture("wm_material_receive", "wm_material_receive_line");
        fixture.insert("wm_material_receive", "receive_id", 42, "receive_code", "RECEIVE-SYNTHETIC",
                "receive_name", "合成收货单", "vendor_name", "合成供应商", "pur_code", "PO-SYNTHETIC",
                "warehouse_name", "合成仓库",
                "receive_date", "2026-10-17");
        for (int index = 0; index < 4; index++) {
            fixture.insert("wm_material_receive_line", "line_id", index + 1,
                    "receive_id", index == 3 ? 43 : 42,
                    "material_id", 8, "material_code", "MAT-SYNTHETIC", "material_name", "合成物料",
                    "material_spec", "SPEC-SYNTHETIC", "base_uom_name", "KG",
                    "batch_code", "BATCH-SYNTHETIC",
                    "quantity_received", new BigDecimal("0.1250"), "tenant_id", index == 1 ? "B" : "A",
                    "del_flag", index == 2 ? "1" : "0");
        }
        return fixture;
    }

    /**
     * 将尚未实现的提供者转换为明确断言失败，避免编译缺失掩盖回归意图。
     *
     * @param fixture 当前隔离数据库
     * @param name 新数据集类名
     * @return 使用隔离数据库的业务提供者
     */
    private ReportDatasetProvider provider(FoundationReportFixture fixture, String name) {
        return assertDoesNotThrow(() -> fixture.provider(name), "应安装新单据提供者：" + name);
    }

    /**
     * 查询契约全部字段，检查固定投影映射的真实可执行性。
     *
     * @param provider 业务提供者
     * @param parameter 必填单据参数名
     * @return 固定主键且包含完整字段的查询
     */
    private DatasetQuery allFields(ReportDatasetProvider provider, String parameter) {
        var fields = new LinkedHashSet<String>();
        provider.descriptor().fields().forEach(field -> fields.add(field.name()));
        return new DatasetQuery(Map.of(parameter, "42"), fields, 1, 20, false, null, false);
    }

    /**
     * 忽略数据库小数位差异，检查业务精确值不被浮点转换。
     *
     * @param expected 十进制期望值
     * @param actual 查询返回的原始类型值
     */
    private void assertDecimal(String expected, Object actual) {
        assertInstanceOf(BigDecimal.class, actual);
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) actual));
    }
}
