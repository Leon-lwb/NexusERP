package com.nexus.erp.pu;

import com.nexus.erp.pu.mapper.PuCertifiedPurchaseWorkflowMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用真实 MyBatis INSERT 验证认证转单的出生元数据，不依赖数据库默认值。
 * H2 只在内存运行；nullable/no-default 的旧列刻意保留，不能把历史 NULL 自动认证为 0。
 */
class PuCertifiedPurchaseWorkflowOrderBirthVersionTest {
    private static final String TENANT = "fixture-tenant";

    @Test
    void certifiedOrderStartsAtVersionZeroWhenNullableColumnHasNoDefault() throws Exception {
        JdbcDataSource source = source(false);
        insert(source, 101L);
        try (Connection connection = source.getConnection();
             var statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT * FROM pu_purchase_order WHERE order_id=101")) {
            assertTrue(row.next());
            assertEquals(0, row.getObject("certified_version"), "认证创建必须显式写版本0，存量列允许NULL不是出生版本依据");
            assertNull(row.getObject("certified_terms_id"), "未定价草稿不得关联报价");
            assertNull(row.getObject("expected_arrival_date"), "未定价草稿不得伪造到货承诺");
            assertEquals("DRAFT", row.getString("status"));
            assertEquals(TENANT, row.getString("tenant_id"));
            assertEquals(601L, row.getLong("requisition_id"));
            assertEquals("REQ-FIXTURE", row.getString("requisition_no"));
            assertEquals(701L, row.getLong("vendor_id"));
            assertEquals(801L, row.getLong("create_by"));
            assertEquals(901L, row.getLong("create_dept"));
            assertNotNull(row.getTimestamp("create_time"));
            assertFalse(row.next());
        }
    }

    @Test
    void certifiedBirthDoesNotInheritConflictingVersionOrQuoteDefaults() throws Exception {
        JdbcDataSource source = source(true);
        insert(source, 102L);
        try (Connection connection = source.getConnection();
             var statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT certified_version,certified_terms_id FROM pu_purchase_order WHERE order_id=102")) {
            assertTrue(row.next());
            assertEquals(0, row.getObject("certified_version"));
            assertNull(row.getObject("certified_terms_id"));
        }
    }

    @Test
    void explicitCertifiedBirthDoesNotRewriteLegacyNullRowsOrColumnDefaults() throws Exception {
        JdbcDataSource source = source(false);
        try (Connection connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO pu_purchase_order(order_id,status) VALUES(900,'DRAFT')");
        }
        insert(source, 103L);
        try (Connection connection = source.getConnection();
             var statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT order_id,certified_version FROM pu_purchase_order ORDER BY order_id")) {
            assertTrue(rows.next()); assertEquals(103L, rows.getLong("order_id"));
            assertEquals(0, rows.getObject("certified_version"));
            assertTrue(rows.next()); assertEquals(900L, rows.getLong("order_id"));
            assertNull(rows.getObject("certified_version"), "正常创建不能顺带给历史NULL补出生版本");
            assertFalse(rows.next());
        }
        try (Connection connection = source.getConnection(); var statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT COLUMN_DEFAULT,IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='PU_PURCHASE_ORDER' AND COLUMN_NAME='CERTIFIED_VERSION'")) {
            assertTrue(row.next()); assertNull(row.getString("COLUMN_DEFAULT"));
            assertEquals("YES", row.getString("IS_NULLABLE"));
        }
    }

    private static JdbcDataSource source(boolean conflictingDefault) throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:order-birth-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        String version = conflictingDefault ? "INT DEFAULT 47" : "INT NULL";
        String quote = conflictingDefault ? "BIGINT DEFAULT 99" : "BIGINT NULL";
        try (Connection connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE pu_purchase_order (order_id BIGINT PRIMARY KEY,tenant_id VARCHAR(20),"
                + "order_no VARCHAR(64),requisition_id BIGINT,requisition_no VARCHAR(64),vendor_id BIGINT,"
                + "vendor_code VARCHAR(64),vendor_name VARCHAR(64),order_date TIMESTAMP,expected_arrival_date TIMESTAMP,"
                + "status VARCHAR(20),certified_version " + version + ",certified_terms_id " + quote + ","
                + "buyer_id BIGINT,buyer_name VARCHAR(64),create_by BIGINT,create_dept BIGINT,create_time TIMESTAMP,del_flag CHAR(1))");
        }
        return source;
    }

    private static void insert(JdbcDataSource source, long id) {
        Configuration configuration = new Configuration(new Environment("isolated-order-birth", new JdbcTransactionFactory(), source));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PuCertifiedPurchaseWorkflowMapper.class);
        var factory = new SqlSessionFactoryBuilder().build(configuration);
        try (var session = factory.openSession(true)) {
            var header = new PuCertifiedPurchaseWorkflowMapper.Head(TENANT, 601L, "REQ-FIXTURE", "AI_DECISION", "APPROVED", "fixture-request", 1, "0", null);
            var vendor = new PuCertifiedPurchaseWorkflowMapper.Vendor(TENANT, 701L, "VENDOR-FIXTURE", "合成供应商", "ACTIVE", "0");
            assertEquals(1, session.getMapper(PuCertifiedPurchaseWorkflowMapper.class).insertOrder(TENANT, id,
                "PO-FIXTURE-" + id, header, vendor, 801L, "合成操作人", 901L));
        }
    }
}
