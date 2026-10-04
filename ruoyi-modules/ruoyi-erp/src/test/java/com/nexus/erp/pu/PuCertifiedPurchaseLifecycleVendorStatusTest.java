package com.nexus.erp.pu;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper.VendorIdentity;
import com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.Operation;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** 使用内存数据库的真实 VARCHAR 映射复现供应商状态错误，不作为本地业务或现场验收。 */
class PuCertifiedPurchaseLifecycleVendorStatusTest {
    private static final String TENANT = "000001";
    private static final Long VENDOR = 1501L;

    @Test
    void vendorStatusContractMatchesTheStringBusinessModel() {
        assertEquals(String.class, VendorIdentity.class.getRecordComponents()[2].getType());
    }

    @Test
    void actualMapperReadsActiveWithoutNumericCoercion() {
        var vendor = assertDoesNotThrow(() -> readVendor("ACTIVE"));
        assertEquals("ACTIVE", vendor.status());
        assertEquals(TENANT, vendor.tenantId());
        assertEquals(VENDOR, vendor.vendorId());
    }

    @Test
    void approvalAcceptsTheActualActiveVarcharSupplier() {
        var vendor = assertDoesNotThrow(() -> readVendor("ACTIVE"));
        try (var fixture = new PuCertifiedPurchaseLifecycleServiceTest.Fixture()) {
            when(fixture.mapper.lockLifecycleVendor(TENANT, VENDOR)).thenReturn(vendor);
            assertDoesNotThrow(() -> fixture.service.applyLocked(fixture.command(Operation.APPROVE, null)));
            verify(fixture.events).insert(any());
        }
    }

    @Test
    void numericTextIsNotAcceptedAsAnActiveSupplier() throws Exception {
        var vendor = readVendor("1");
        try (var fixture = new PuCertifiedPurchaseLifecycleServiceTest.Fixture()) {
            when(fixture.mapper.lockLifecycleVendor(TENANT, VENDOR)).thenReturn(vendor);
            assertThrows(ServiceException.class,
                () -> fixture.service.applyLocked(fixture.command(Operation.APPROVE, null)));
            verify(fixture.ledgers, never()).updateWithVersion(any(), any(), any());
            verify(fixture.events, never()).insert(any());
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"INACTIVE", "SUSPENDED", "FROZEN", "BLACKLISTED", "1", "active", ""})
    void inactiveOrUnknownStatusesCannotMutateLedger(String status) {
        var vendor = assertDoesNotThrow(() -> readVendor(status));
        assertEquals(status, vendor.status());
        try (var fixture = new PuCertifiedPurchaseLifecycleServiceTest.Fixture()) {
            when(fixture.mapper.lockLifecycleVendor(TENANT, VENDOR)).thenReturn(vendor);
            assertThrows(ServiceException.class,
                () -> fixture.service.applyLocked(fixture.command(Operation.APPROVE, null)));
            verify(fixture.ledgers, never()).updateWithVersion(any(), any(), any());
            verify(fixture.events, never()).insert(any());
        }
    }

    @ParameterizedTest
    @MethodSource("invalidSupplierIdentities")
    void supplierIdentityOrDeletionCannotMutateLedger(VendorIdentity vendor) {
        try (var fixture = new PuCertifiedPurchaseLifecycleServiceTest.Fixture()) {
            when(fixture.mapper.lockLifecycleVendor(TENANT, VENDOR)).thenReturn(vendor);
            assertThrows(ServiceException.class,
                () -> fixture.service.applyLocked(fixture.command(Operation.APPROVE, null)));
            verify(fixture.ledgers, never()).updateWithVersion(any(), any(), any());
            verify(fixture.events, never()).insert(any());
        }
    }

    private static Stream<VendorIdentity> invalidSupplierIdentities() {
        return Stream.of(
            new VendorIdentity("000002", VENDOR, "ACTIVE", "0"),
            new VendorIdentity(TENANT, 1502L, "ACTIVE", "0"),
            new VendorIdentity(TENANT, null, "ACTIVE", "0"),
            new VendorIdentity(TENANT, VENDOR, "ACTIVE", "1"),
            new VendorIdentity(TENANT, VENDOR, "ACTIVE", null));
    }

    private static VendorIdentity readVendor(String status) throws Exception {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:lifecycle-vendor-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE bd_vendor (tenant_id VARCHAR(20), vendor_id BIGINT, status VARCHAR(20), del_flag CHAR(1))");
            try (var insert = connection.prepareStatement("INSERT INTO bd_vendor VALUES (?, ?, ?, ?)")) {
                insert.setString(1, TENANT);
                insert.setLong(2, VENDOR);
                insert.setString(3, status);
                insert.setString(4, "0");
                insert.executeUpdate();
            }
        }
        var configuration = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PuCertifiedPurchaseLifecycleMapper.class);
        var factory = new SqlSessionFactoryBuilder().build(configuration);
        try (var session = factory.openSession()) {
            return session.getMapper(PuCertifiedPurchaseLifecycleMapper.class).lockLifecycleVendor(TENANT, VENDOR);
        }
    }
}
