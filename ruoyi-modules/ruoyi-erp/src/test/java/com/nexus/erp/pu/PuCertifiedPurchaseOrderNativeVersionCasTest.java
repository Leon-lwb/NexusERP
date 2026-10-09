package com.nexus.erp.pu;

import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderWorkflowMapper;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderWorkflowMapper.Head;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderWorkflowMapper.Quote;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderWorkflowMapper.Totals;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 在隔离的 H2 MySQL 兼容库中执行生产 Mapper，验证原生版本读取与批准、终态 CAS。
 * 历史 NULL 不是合法出生版本；拒绝路径提交后核对整行，不能靠回滚掩盖错误写入。
 */
public class PuCertifiedPurchaseOrderNativeVersionCasTest {
    private static final String TENANT = "fixture-tenant";
    private static final long ORDER_ID = 101L;
    private static final long REQUISITION_ID = 601L;
    private static final long VENDOR_ID = 701L;
    private static final long QUOTE_ID = 501L;
    private static final long ACTOR_ID = 801L;
    private static final Totals TOTALS = new Totals(decimal("100.00"), decimal("13.00"), decimal("113.00"));

    @Test
    void lockingReadPreservesUnknownNativeNullVersion() throws Exception {
        try (var fixture = new Fixture(null, "DRAFT")) {
            var before = fixture.snapshot();
            Head head = fixture.mapper.lockHead(TENANT, ORDER_ID);
            assertNotNull(head);
            fixture.session.commit(true);
            assertAll(
                () -> assertNull(head.version(), "原生 NULL 不能被锁内读取冒充为版本 0"),
                () -> assertEquals(before, fixture.snapshot(), "锁内读取不得自动修复旧行"));
        }
    }

    @Test
    void approvalRejectsStaleExpectedZeroForNativeNullWithoutChangingAnyColumn() throws Exception {
        try (var fixture = new Fixture(null, "DRAFT")) {
            var before = fixture.snapshot();
            int affected = fixture.mapper.approveHead(TENANT, expectedHead(ORDER_ID, "DRAFT", 0), quote(), TOTALS, ACTOR_ID);
            fixture.assertRejectedAndUnchanged(affected, before);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRAFT", "APPROVED"})
    void terminalCasRejectsStaleExpectedZeroForNativeNullWithoutChangingAnyColumn(String status) throws Exception {
        try (var fixture = new Fixture(null, status)) {
            var before = fixture.snapshot();
            String next = "DRAFT".equals(status) ? "CANCELLED" : "CLOSED";
            int affected = fixture.mapper.closeHead(TENANT, expectedHead(ORDER_ID, status, 0), next, ACTOR_ID);
            fixture.assertRejectedAndUnchanged(affected, before);
        }
    }

    @Test
    void approvalOfNativeZeroStillPersistsVersionOneAndQuotedBusinessValues() throws Exception {
        try (var fixture = new Fixture(0, "DRAFT")) {
            Head head = fixture.mapper.lockHead(TENANT, ORDER_ID);
            assertNotNull(head);
            assertEquals(0, head.version());
            assertEquals(1, fixture.mapper.approveHead(TENANT, head, quote(), TOTALS, ACTOR_ID));
            fixture.session.commit(true);
            var row = fixture.snapshot();
            assertAll(
                () -> assertEquals("APPROVED", row.get("status")),
                () -> assertEquals(1, row.get("certified_version")),
                () -> assertEquals(QUOTE_ID, row.get("certified_terms_id")),
                () -> assertEquals(Timestamp.valueOf("2026-10-20 00:00:00"), row.get("expected_arrival_date")),
                () -> assertEquals("EUR", row.get("currency")),
                () -> assertEquals(decimal("1.100000"), row.get("exchange_rate")),
                () -> assertEquals(TOTALS.gross(), row.get("total_amount")),
                () -> assertEquals(TOTALS.net(), row.get("amount_excluding_tax")),
                () -> assertEquals(TOTALS.tax(), row.get("tax_amount")),
                () -> assertEquals(decimal("0.00"), row.get("discount_amount")),
                () -> assertEquals(decimal("0.00"), row.get("freight_amount")),
                () -> assertEquals(decimal("0.00"), row.get("discount_rate")),
                () -> assertEquals("合成报价付款条款", row.get("payment_terms")),
                () -> assertEquals("合成报价交货条款", row.get("delivery_terms")),
                () -> assertEquals(ACTOR_ID, row.get("update_by")),
                () -> assertNotNull(row.get("update_time")));
            Head approved = fixture.mapper.lockHead(TENANT, ORDER_ID);
            assertNotNull(approved);
            assertEquals(1, approved.version(), "生产 Mapper 必须读到实际递增后的版本");
        }
    }

    @ParameterizedTest
    @CsvSource({"0,DRAFT,CANCELLED", "1,APPROVED,CLOSED", "1,APPROVED,CANCELLED"})
    void terminalCasPreservesLegalNativeZeroAndOne(Integer version, String status, String next) throws Exception {
        try (var fixture = new Fixture(version, status)) {
            var before = fixture.snapshot();
            Head head = fixture.mapper.lockHead(TENANT, ORDER_ID);
            assertNotNull(head);
            assertEquals(version, head.version());
            assertEquals(1, fixture.mapper.closeHead(TENANT, head, next, ACTOR_ID));
            fixture.session.commit(true);
            var after = fixture.snapshot();
            var expected = new LinkedHashMap<>(before);
            expected.put("status", next);
            expected.put("certified_version", version + 1);
            expected.put("update_by", ACTOR_ID);
            expected.put("update_time", after.get("update_time"));
            assertAll(
                () -> assertEquals(expected, after, "终态 CAS 只能改变状态、版本及更新审计列"),
                () -> assertNotEquals(before.get("update_time"), after.get("update_time")));
        }
    }

    @ParameterizedTest
    @EnumSource(value = Mismatch.class, names = {"TENANT", "ORDER_ID"})
    void lockingReadRejectsWrongTenantOrOrderId(Mismatch mismatch) throws Exception {
        try (var fixture = new Fixture(0, "DRAFT")) {
            var before = fixture.snapshot();
            Head head = fixture.mapper.lockHead(tenant(mismatch), orderId(mismatch));
            fixture.session.commit(true);
            assertAll(
                () -> assertNull(head),
                () -> assertEquals(before, fixture.snapshot()));
        }
    }

    @ParameterizedTest
    @EnumSource(Mismatch.class)
    void approvalCasRejectsWrongTenantOrderVersionOrStatus(Mismatch mismatch) throws Exception {
        String actualStatus = mismatch == Mismatch.STATUS ? "APPROVED" : "DRAFT";
        try (var fixture = new Fixture(0, actualStatus)) {
            var before = fixture.snapshot();
            Head old = expectedHead(orderId(mismatch), "DRAFT", mismatch == Mismatch.VERSION ? 1 : 0);
            int affected = fixture.mapper.approveHead(tenant(mismatch), old, quote(), TOTALS, ACTOR_ID);
            fixture.assertRejectedAndUnchanged(affected, before);
        }
    }

    @ParameterizedTest
    @EnumSource(Mismatch.class)
    void terminalCasRejectsWrongTenantOrderVersionOrStatus(Mismatch mismatch) throws Exception {
        String actualStatus = mismatch == Mismatch.STATUS ? "DRAFT" : "APPROVED";
        try (var fixture = new Fixture(1, actualStatus)) {
            var before = fixture.snapshot();
            Head old = expectedHead(orderId(mismatch), "APPROVED", mismatch == Mismatch.VERSION ? 0 : 1);
            int affected = fixture.mapper.closeHead(tenant(mismatch), old, "CLOSED", ACTOR_ID);
            fixture.assertRejectedAndUnchanged(affected, before);
        }
    }

    private static String tenant(Mismatch mismatch) {
        return mismatch == Mismatch.TENANT ? "other-fixture-tenant" : TENANT;
    }

    private static long orderId(Mismatch mismatch) {
        return mismatch == Mismatch.ORDER_ID ? ORDER_ID + 1 : ORDER_ID;
    }

    private static Head expectedHead(long orderId, String status, Integer version) {
        return new Head(TENANT, orderId, "PO-FIXTURE", REQUISITION_ID, VENDOR_ID, status, version,
            version != null && version > 0 ? QUOTE_ID : null, "2026-10-01", "0", null, "CNY",
            decimal("1.000000"), decimal("0.00"), decimal("0.00"), decimal("0.00"),
            decimal("0.00"), decimal("0.00"), decimal("0.00"), "合成原付款条款", "合成原交货条款");
    }

    private static Quote quote() {
        String digest = "a".repeat(64);
        return new Quote(QUOTE_ID, TENANT, ORDER_ID, 1, "fixture-quote-request", digest, ACTOR_ID, new Date(0),
            VENDOR_ID, 901L, 1001L, "fixture-source-request", digest, digest, digest,
            decimal("10.0000"), decimal("10.0000"), decimal("13.00"), "EUR", decimal("1.100000"),
            "2026-10-20", "2026-10-01", "2026-10-31", "fixture-supplier-quote", "合成联系人",
            "fixture-evidence", digest, "合成报价付款条款", "合成报价交货条款", "HALF_UP", digest);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    /** 只补足 H2 缺少的 MySQL 日期格式函数；不替换生产 Mapper 的读取或 CAS SQL。 */
    public static String dateFormat(Timestamp value, String format) {
        if (!"%Y-%m-%d".equals(format)) {
            throw new IllegalArgumentException("测试仅支持 Mapper 使用的日期格式");
        }
        return value == null ? null : value.toLocalDateTime().toLocalDate().toString();
    }

    private enum Mismatch {
        TENANT, ORDER_ID, VERSION, STATUS
    }

    /** 每个用例只持有独立内存库和真实 MyBatis 会话，不加载 Spring 或任何环境配置。 */
    private static final class Fixture implements AutoCloseable {
        private final Connection keeper;
        private final SqlSession session;
        private final PuCertifiedPurchaseOrderWorkflowMapper mapper;

        private Fixture(Integer nativeVersion, String status) throws Exception {
            var source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:order-native-version-" + UUID.randomUUID() + ";MODE=MySQL");
            keeper = source.getConnection();
            try (var statement = keeper.createStatement()) {
                statement.execute("CREATE ALIAS DATE_FORMAT FOR \""
                    + PuCertifiedPurchaseOrderNativeVersionCasTest.class.getName() + ".dateFormat\"");
                statement.execute("CREATE TABLE pu_purchase_order (order_id BIGINT PRIMARY KEY,order_no VARCHAR(64),"
                    + "requisition_id BIGINT,requisition_no VARCHAR(64),vendor_id BIGINT,vendor_code VARCHAR(64),vendor_name VARCHAR(128),"
                    + "order_date TIMESTAMP,expected_arrival_date TIMESTAMP,currency VARCHAR(16),exchange_rate DECIMAL(18,6),"
                    + "total_amount DECIMAL(18,2),amount_excluding_tax DECIMAL(18,2),tax_amount DECIMAL(18,2),"
                    + "discount_amount DECIMAL(18,2),freight_amount DECIMAL(18,2),discount_rate DECIMAL(10,2),"
                    + "payment_terms VARCHAR(255),delivery_terms VARCHAR(255),status VARCHAR(32),remark VARCHAR(500),"
                    + "attr1 VARCHAR(500),attr2 VARCHAR(500),attr3 BIGINT,attr4 BIGINT,tenant_id VARCHAR(20),"
                    + "create_dept BIGINT,create_by BIGINT,create_time TIMESTAMP,update_by BIGINT,update_time TIMESTAMP,"
                    + "del_flag CHAR(1),select_flow_id BIGINT,contract_id BIGINT,contract_no VARCHAR(64),"
                    + "dept_id BIGINT,dept_name VARCHAR(100),buyer_id BIGINT,buyer_name VARCHAR(64),"
                    + "certified_version INT NULL,certified_terms_id BIGINT NULL)");
            }
            try (var insert = keeper.prepareStatement("INSERT INTO pu_purchase_order VALUES ("
                + "101,'PO-FIXTURE',601,'REQ-FIXTURE',701,'VENDOR-FIXTURE','合成供应商',"
                + "TIMESTAMP '2026-10-01 08:00:00',TIMESTAMP '2026-10-01 00:00:00','CNY',1.000000,0,0,0,0,0,0,"
                + "'合成原付款条款','合成原交货条款',?,'合成原备注','合成扩展一','合成扩展二',11,12,'fixture-tenant',"
                + "21,22,TIMESTAMP '2026-10-01 08:00:00',23,TIMESTAMP '2026-10-01 09:00:00','0',NULL,"
                + "31,'CONTRACT-FIXTURE',41,'合成部门',51,'合成采购员',?,?)")) {
                insert.setString(1, status);
                insert.setObject(2, nativeVersion);
                insert.setObject(3, nativeVersion != null && nativeVersion > 0 ? QUOTE_ID : null);
                assertEquals(1, insert.executeUpdate());
            }
            var configuration = new Configuration(new Environment("isolated-native-version-cas", new JdbcTransactionFactory(), source));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(PuCertifiedPurchaseOrderWorkflowMapper.class);
            session = new SqlSessionFactoryBuilder().build(configuration).openSession(false);
            mapper = session.getMapper(PuCertifiedPurchaseOrderWorkflowMapper.class);
        }

        /** 快照包含夹具中订单头的全部业务、来源、版本与审计列，且绕过 MyBatis 缓存。 */
        private Map<String, Object> snapshot() throws Exception {
            try (var statement = keeper.createStatement(); var row = statement.executeQuery("SELECT * FROM pu_purchase_order")) {
                assertTrue(row.next());
                var values = new LinkedHashMap<String, Object>();
                var columns = row.getMetaData();
                for (int index = 1; index <= columns.getColumnCount(); index++) {
                    values.put(columns.getColumnLabel(index).toLowerCase(Locale.ROOT), row.getObject(index));
                }
                assertFalse(row.next());
                return values;
            }
        }

        private void assertRejectedAndUnchanged(int affected, Map<String, Object> before) throws Exception {
            session.commit(true);
            var after = snapshot();
            assertAll(
                () -> assertEquals(0, affected, "未知版本或 CAS 不匹配必须影响 0 行"),
                () -> assertEquals(before, after, "提交后所有订单头列必须保持原值"));
        }

        @Override
        public void close() throws Exception {
            try {
                session.close();
            } finally {
                keeper.close();
            }
        }
    }
}
