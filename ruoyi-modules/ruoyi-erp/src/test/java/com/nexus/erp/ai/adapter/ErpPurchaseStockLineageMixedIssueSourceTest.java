package com.nexus.erp.ai.adapter;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.nexus.erp.pu.mapper.ErpPurchaseStockLineageMapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** H2仅核查询范围及原生字段映射，不证明MySQL行锁、间隙锁或现场并发已验收。 */
class ErpPurchaseStockLineageMixedIssueSourceTest {
    private static Configuration configuration() {
        Configuration configuration = new Configuration();
        configuration.addMapper(ErpPurchaseStockLineageMapper.class);
        return configuration;
    }

    private static String sql(String name) {
        return configuration().getMappedStatement(ErpPurchaseStockLineageMapper.class.getName() + "." + name)
            .getBoundSql(Map.of("tenantId", "000000", "materialId", 140L, "stockIds", List.of(91L), "ids", List.of(601L)))
            .getSql().replaceAll("\\s+", " ").trim().toUpperCase();
    }

    @Test
    void targetTransactionAndStockReadersKeepTheirOriginalMaterialScopeAndLockOrder() {
        String query = sql("lockTransactions");
        assertTrue(query.endsWith("ORDER BY TX_ID ASC FOR UPDATE"), query);
        assertTrue(query.contains("WHERE (TENANT_ID = ? AND MATERIAL_ID = ?) OR MATERIAL_STOCK_ID IN"), query);
        String where = query.substring(query.indexOf(" WHERE "));
        for (String hiddenOrExpanded : List.of("DEL_FLAG", "TX_TYPE", "SOURCE_DOC_ID", "SOURCE_DOC_LINE_ID", "TX_QUANTITY", " JOIN ", "SUM(", "GROUP BY", "CAST(", "COALESCE(", "IFNULL(")) {
            assertFalse(where.contains(hiddenOrExpanded), hiddenOrExpanded + ": " + query);
        }
        assertTrue(sql("lockStocks").endsWith("ORDER BY STOCK_ID ASC FOR UPDATE"));
        assertEquals(1, List.of(ErpPurchaseStockLineageMapper.class.getMethods()).stream()
            .filter(method -> method.getName().toLowerCase().contains("transaction")).count(), "不得新增兄弟流水锁读取器");
        assertTrue(query.contains("WAREHOUSE_ID AS WAREHOUSEID"));
        assertTrue(query.contains("AREA_ID AS AREAID")); assertTrue(query.contains("BATCH_ID AS BATCHID"));
        assertFalse(query.contains("_EXACT"));
    }

    @Test
    void originalsKeepEveryPeerLineAndMrpRowWithoutTenantStateOrMaterialFilters() {
        for (String name : List.of("lockIssueLines", "lockRequirementDetails", "lockRequirementHeads", "lockWorkOrders")) {
            String query = sql(name), where = query.substring(query.indexOf(" WHERE "));
            for (String hidden : List.of("TENANT_ID", "MATERIAL_ID", "DEL_FLAG", "STATUS", " JOIN ", "SUM(", "GROUP BY", "CAST(", "COALESCE(", "IFNULL(")) {
                assertFalse(where.contains(hidden), hidden + ": " + query);
            }
            assertTrue(query.endsWith("FOR UPDATE"), query);
        }
        assertTrue(sql("lockWorkOrders").contains("SOURCE_TYPE AS SOURCETYPE"));
        assertTrue(sql("lockWorkOrders").contains("SOURCE_ID AS SOURCEID"));
    }

    @Test
    void completeOriginalReadsDisableCachesAndImplicitTenantFiltering() {
        for (String name : List.of("lockTransactions", "lockIssueHeads", "lockIssueLines", "lockRequirementDetails", "lockRequirementHeads", "lockWorkOrders")) {
            var method = List.of(ErpPurchaseStockLineageMapper.class.getMethods()).stream().filter(m -> name.equals(m.getName())).findFirst().orElseThrow();
            Options options = method.getAnnotation(Options.class);
            assertNotNull(options); assertFalse(options.useCache()); assertEquals(Options.FlushCachePolicy.TRUE, options.flushCache());
            InterceptorIgnore ignore = method.getAnnotation(InterceptorIgnore.class);
            assertNotNull(ignore); assertEquals("true", ignore.tenantLine()); assertEquals("true", ignore.dataPermission());
        }
    }

    @Test
    void h2ReadsAllPeerOriginalsAndCumulativeMrpButOnlyTargetTransactionHistory() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:mixed_" + UUID.randomUUID() + ";MODE=MySQL");
        try (Connection connection = source.getConnection()) {
            schema(connection); populate(connection);
            Configuration configuration = configuration();
            configuration.setEnvironment(new Environment("混料来源离线H2", new JdbcTransactionFactory(), source));
            try (var session = new SqlSessionFactoryBuilder().build(configuration).openSession(false)) {
                var mapper = session.getMapper(ErpPurchaseStockLineageMapper.class);
                var rows = mapper.lockTransactions("000000", 140L, List.of(91L));
                assertEquals(List.of(100L, 103L, 109L, 110L), rows.stream().map(ErpPurchaseStockLineageMapper.TransactionRow::txId).toList());
                var raw = rows.stream().filter(row -> row.txId() == 103L).findFirst().orElseThrow();
                assertEquals(8L, raw.warehouseId()); assertEquals(18L, raw.areaId()); assertEquals(81L, raw.batchId());
                assertEquals(28L, raw.locationId()); assertNull(raw.snId());
                assertEquals(new BigDecimal("10.0000"), raw.txQuantity());
                var lines = mapper.lockIssueLines("000000", List.of(601L));
                assertEquals(List.of(611L, 612L, 613L), lines.stream().map(ErpPurchaseStockLineageMapper.IssueLine::lineId).toList());
                assertEquals("other", lines.get(2).tenantId()); assertEquals("1", lines.get(2).delFlag());
                var requirements = mapper.lockRequirementDetails("000000", List.of(701L, 702L, 703L));
                assertEquals(new BigDecimal("20.0000"), requirements.get(1).issuedQuantity());
                assertEquals(new BigDecimal("10.0000"), lines.get(1).issuedQty(), "兄弟累计可跨其他头，当前父行不能硬等累计");
                var wo = mapper.lockWorkOrders("000000", List.of(801L)).get(0);
                assertEquals("MATERIAL_REQUIREMENT", wo.sourceType()); assertEquals(711L, wo.sourceId());
                session.rollback();
            }
        }
    }

    @Test
    void transactionLegacySeventeenArgumentDescriptorPreservesOriginalsAndLeavesNativeDimensionsUnknown() {
        Class<?>[] types = {Long.class, String.class, Long.class, Long.class, String.class, String.class, Integer.class,
            BigDecimal.class, BigDecimal.class, BigDecimal.class, String.class, Long.class, Long.class, String.class,
            Long.class, BigDecimal.class, String.class};
        var constructor = assertDoesNotThrow(() -> ErpPurchaseStockLineageMapper.TransactionRow.class.getConstructor(types), "旧17参数签名必须继续可调用");
        var row = assertDoesNotThrow(() -> constructor.newInstance(103L, "000000", 140L, 91L, "26", "PRODUCTION_ISSUE", -1,
            new BigDecimal("10.0000"), new BigDecimal("10.0000"), new BigDecimal("0.0000"), "WM_PRODUCTION_ISSUE", 601L, 611L,
            null, null, new BigDecimal("1000"), "0"));
        assertAll(() -> assertEquals(103L, row.txId()), () -> assertEquals(140L, row.materialId()),
            () -> assertEquals(91L, row.stockId()), () -> assertEquals(611L, row.sourceDocLineId()),
            () -> assertEquals(new BigDecimal("10.0000"), row.txQuantity()),
            () -> assertNull(row.warehouseId()), () -> assertNull(row.areaId()), () -> assertNull(row.locationId()),
            () -> assertNull(row.batchId()), () -> assertNull(row.snId()));
    }

    @Test
    void workOrderLegacyEightArgumentDescriptorPreservesOwnershipButCannotInventDraftSourceFields() {
        Class<?>[] types = {Long.class, String.class, String.class, Long.class, Long.class, Long.class, String.class, String.class};
        var constructor = assertDoesNotThrow(() -> ErpPurchaseStockLineageMapper.WorkOrder.class.getConstructor(types), "旧8参数签名必须继续可调用");
        var row = assertDoesNotThrow(() -> constructor.newInstance(801L, "000000", "WO801", 901L, 1001L, 240L, "COMPLETED", "0"));
        assertAll(() -> assertEquals(801L, row.woId()), () -> assertEquals("000000", row.tenantId()),
            () -> assertEquals(901L, row.planId()), () -> assertEquals(1001L, row.bomId()), () -> assertEquals(240L, row.materialId()),
            () -> assertEquals("COMPLETED", row.woStatus()), () -> assertNull(row.sourceType()), () -> assertNull(row.sourceId()));
    }

    private static void schema(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE wm_material_stock_tx (
                    tx_id BIGINT PRIMARY KEY, tenant_id VARCHAR(20), material_id BIGINT, material_stock_id BIGINT,
                    base_uom_id VARCHAR(64), tx_type VARCHAR(40), tx_flag INTEGER,
                    tx_quantity DECIMAL(18,4), before_quantity DECIMAL(18,4), after_quantity DECIMAL(18,4),
                    source_doc_type VARCHAR(64), source_doc_id BIGINT, source_doc_line_id BIGINT, source_doc_code VARCHAR(64),
                    related_tx_id BIGINT, tx_date TIMESTAMP, del_flag VARCHAR(1),
                    warehouse_id BIGINT, area_id BIGINT, location_id BIGINT, batch_id BIGINT, sn_id BIGINT)
                """);
            statement.execute("""
                CREATE TABLE wm_production_issue_line (
                    line_id BIGINT PRIMARY KEY, tenant_id VARCHAR(20), issue_id BIGINT, material_id BIGINT,
                    base_uom_id VARCHAR(64), warehouse_id BIGINT, location_id BIGINT, batch_id BIGINT,
                    quantity DECIMAL(18,4), required_qty DECIMAL(18,4), issued_qty DECIMAL(18,4), short_qty DECIMAL(18,4),
                    requirement_detail_id BIGINT, line_status VARCHAR(20), del_flag VARCHAR(1))
                """);
            statement.execute("""
                CREATE TABLE prd_material_requirement_detail (
                    detail_id BIGINT PRIMARY KEY, tenant_id VARCHAR(20), requirement_id BIGINT, material_id BIGINT,
                    unit VARCHAR(64), required_quantity DECIMAL(18,4), issued_quantity DECIMAL(18,4),
                    source_type VARCHAR(20), status BIGINT, backflush_flag INTEGER, del_flag VARCHAR(1))
                """);
            statement.execute("""
                CREATE TABLE prd_work_order (
                    wo_id BIGINT PRIMARY KEY, tenant_id VARCHAR(20), wo_no VARCHAR(64), plan_id BIGINT, bom_id BIGINT,
                    material_id BIGINT, wo_status VARCHAR(20), del_flag VARCHAR(1), source_type VARCHAR(40), source_id BIGINT)
                """);
            statement.execute("INSERT INTO wm_production_issue_line VALUES (611,'000000',601,140,'26',8,28,81,10,10,10,0,701,'ISSUED','0'),(612,'000000',601,141,'26',8,28,82,10,10,10,0,702,'ISSUED','0'),(613,'other',601,142,'26',8,28,83,20,20,20,0,703,'ISSUED','1')");
            statement.execute("INSERT INTO prd_material_requirement_detail VALUES (701,'000000',711,140,'26',10,10,'PROCUREMENT',1,0,'0'),(702,'000000',711,141,'26',20,20,'PROCUREMENT',1,0,'0'),(703,'other',711,142,'26',20,20,'PROCUREMENT',1,0,'1')");
            statement.execute("INSERT INTO prd_work_order VALUES (801,'000000','WO801',901,1001,240,'COMPLETED','0','MATERIAL_REQUIREMENT',711)");
        }
    }

    private static void populate(Connection connection) throws Exception {
        tx(connection, 100, "000000", 140, 91, "RECEIVE_IN", "WM_MATERIAL_RECEIVE", 501, 511, 1, "0");
        tx(connection, 103, "000000", 140, 91, "PRODUCTION_ISSUE", "WM_PRODUCTION_ISSUE", 601, 611, -1, "0");
        tx(connection, 104, "000000", 141, 92, "PRODUCTION_ISSUE", "WM_PRODUCTION_ISSUE", 601, 612, -1, "0");
        tx(connection, 105, "other", 142, 93, "PRODUCTION_ISSUE", "WM_PRODUCTION_ISSUE", 601, 613, -1, "1");
        tx(connection, 109, "000000", 140, 999, "PRODUCTION_ISSUE", "WM_PRODUCTION_ISSUE", 602, 614, -1, "0");
        tx(connection, 110, "other", 777, 91, "PRODUCTION_ISSUE", "WM_PRODUCTION_ISSUE", 999, 999, -1, "0");
        tx(connection, 999, "000000", 444, 94, "PRODUCTION_ISSUE", "WM_PRODUCTION_ISSUE", 777, 888, -1, "0");
    }

    // H2的UNIX_TIMESTAMP为INTEGER；小epoch仅核字段映射，业务时间认证由Verifier的独立回归覆盖。
    private static void tx(Connection connection, long id, String tenant, long material, long stock,
            String type, String sourceType, long head, long line, int flag, String deleted) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO wm_material_stock_tx VALUES (?, ?, ?, ?, '26', ?, ?, 10.0000, ?, ?,
                    ?, ?, ?, NULL, NULL, TIMESTAMP '1970-01-02 08:00:00', ?, 8, 18, 28, 81, NULL)
                """)) {
            statement.setLong(1, id); statement.setString(2, tenant); statement.setLong(3, material); statement.setLong(4, stock);
            statement.setString(5, type); statement.setInt(6, flag);
            statement.setBigDecimal(7, new BigDecimal("RECEIVE_IN".equals(type) ? "0.0000" : "10.0000"));
            statement.setBigDecimal(8, new BigDecimal("RECEIVE_IN".equals(type) ? "10.0000" : "0.0000"));
            statement.setString(9, sourceType); statement.setLong(10, head); statement.setLong(11, line); statement.setString(12, deleted);
            statement.executeUpdate();
        }
    }
}
