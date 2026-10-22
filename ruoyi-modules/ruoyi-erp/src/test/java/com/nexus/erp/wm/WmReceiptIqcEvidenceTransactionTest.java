package com.nexus.erp.wm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper;
import com.nexus.erp.pu.mapper.PuRequirementMaterialGuardMapper;
import com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.wm.mapper.WmReceiptIqcEvidenceMapper;
import com.nexus.erp.wm.service.WmReceiptIqcEvidenceService;
import com.zaxxer.hikari.HikariDataSource;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class WmReceiptIqcEvidenceTransactionTest {
    final WmReceiptIqcEvidenceServiceTest fixture = new WmReceiptIqcEvidenceServiceTest();
    final HikariDataSource erp = mock(HikariDataSource.class);
    final HikariDataSource master = mock(HikariDataSource.class);
    final DynamicRoutingDataSource routing = new DynamicRoutingDataSource(List.of());
    final Connection connection = mock(Connection.class);
    final AtomicBoolean closed = new AtomicBoolean();
    final AtomicBoolean autoCommit = new AtomicBoolean(true);
    final AtomicInteger isolation = new AtomicInteger(Connection.TRANSACTION_READ_COMMITTED);
    WmReceiptIqcEvidenceService service;
    void arrange() throws Exception {
        fixture.arrange("QUALIFIED"); routing.addDataSource("erp", erp); routing.addDataSource("master", master);
        when(erp.getConnection()).thenReturn(connection);
        when(connection.isClosed()).thenAnswer(i -> closed.get());
        doAnswer(i -> { closed.set(true); return null; }).when(connection).close();
        when(connection.getAutoCommit()).thenAnswer(i -> autoCommit.get());
        doAnswer(i -> { autoCommit.set(i.getArgument(0)); return null; }).when(connection).setAutoCommit(anyBoolean());
        when(connection.getTransactionIsolation()).thenAnswer(i -> isolation.get());
        doAnswer(i -> { isolation.set(i.getArgument(0)); return null; }).when(connection).setTransactionIsolation(anyInt());
        Configuration config = new Configuration(new Environment("mocked-physical-erp", new SpringManagedTransactionFactory(), routing));
        config.addMapper(PuRequirementCompetitionPoolMapper.class); config.addMapper(PuRequirementMaterialGuardMapper.class);
        config.addMapper(WmReceiptIqcEvidenceMapper.class);
        var factory = new DefaultSqlSessionFactory(config);
        service = new WmReceiptIqcEvidenceService(fixture.mapper, fixture.source,
            new PuRequirementErpTransactionExecutor(factory), new PuRequirementErpTransactionQualification(factory));
        doAnswer(
                        i -> {
                            assertThat(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive())
                                    .isFalse();
                            assertThat(TransactionSynchronizationManager.getResourceMap())
                                    .isEmpty();
                            return fixture.observation(
                                    "QUALIFIED", new BigDecimal("12"), "a".repeat(64));
                        })
                .when(fixture.source)
                .readCompleted("000007", 501L);
    }
    @AfterEach void cleanup() { TransactionSynchronizationManager.clear(); }

    @Test void missingOriginalContractFailsBeforeBorrowingErpOrMasterPhysicalConnections() throws Exception {
        arrange();
        try (var tenant = fixture.tenant()) {
            assertThatThrownBy(() -> service.consumeCompleted(fixture.message(), actual -> fail("must not project")))
                .hasMessage("UNIT_ORIGIN_UNPROVEN");
            verify(erp, never()).getConnection(); verify(master, never()).getConnection();
            verify(connection, never()).commit(); verify(connection, never()).rollback();
            assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
        }
    }
    @Test void nativeCatalogAndLegacyEvidenceRefusalParticipateInActualOwnedErpRr() throws Exception {
        arrange();
        try (var tenant = fixture.tenant()) {
            fixture.stored = fixture.legacyEvidence("QUALIFIED");
            var factory = new DefaultSqlSessionFactory(configuration());
            var transactions = new PuRequirementErpTransactionExecutor(factory);
            var guarded = new WmReceiptIqcEvidenceService(fixture.mapper, fixture.source, transactions,
                new PuRequirementErpTransactionQualification(factory));
            doAnswer(i -> {
                assertThat(((ConnectionHolder) TransactionSynchronizationManager.getResource(erp)).getConnection()).isSameAs(connection);
                assertThat(isolation.get()).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
                return WmReceiptIqcEvidenceSchemaTest.erpColumns();
            }).when(fixture.mapper).selectNativeColumns();
            assertThatThrownBy(
                            () ->
                                    transactions.executeRepeatableRead(
                                            "000007",
                                            () -> {
                                                guarded.requireLocked(
                                                        "000007",
                                                        101L,
                                                        201L,
                                                        301L,
                                                        new BigDecimal("12"));
                                                return null;
                                            }))
                    .isInstanceOf(ServiceException.class);
            verify(connection).rollback(); verify(connection, never()).commit(); verify(master, never()).getConnection();
            assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
        }
    }
    private Configuration configuration() {
        Configuration config = new Configuration(new Environment("mocked-physical-erp", new SpringManagedTransactionFactory(), routing));
        config.addMapper(PuRequirementCompetitionPoolMapper.class); config.addMapper(PuRequirementMaterialGuardMapper.class);
        config.addMapper(WmReceiptIqcEvidenceMapper.class); return config;
    }

    @Test void trueNegativeProjectionRunsOnOwnedPhysicalErpRrWithoutAnyCertificateOrQuantity() throws Exception {
        arrange(); doReturn(fixture.observation("UNQUALIFIED", null, "a".repeat(64))).when(fixture.source).readCompletedIdentity("000007", 501L);
        try (var tenant = fixture.tenant()) {
            service.consumeCompleted(fixture.message(), actual -> {
                assertThat(((ConnectionHolder) TransactionSynchronizationManager.getResource(erp)).getConnection()).isSameAs(connection);
                assertThat(isolation.get()).isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
                assertThat(actual.result()).isEqualTo("UNQUALIFIED"); assertThat(actual.quantity()).isNull();
            });
            verify(connection).commit(); verify(connection, never()).rollback(); verify(master, never()).getConnection();
            verify(fixture.mapper, never()).insertEvidence(any());
        }
    }
    @Test void failedNegativeProjectionRollsBackPhysicalErpRrAndNeverAcknowledgesCertificate() throws Exception {
        arrange(); doReturn(fixture.observation("UNQUALIFIED", null, "a".repeat(64))).when(fixture.source).readCompletedIdentity("000007", 501L);
        try (var tenant = fixture.tenant()) {
            assertThatThrownBy(() -> service.consumeCompleted(fixture.message(), actual -> { throw new ServiceException("safety failure"); }))
                .isInstanceOf(ServiceException.class);
            verify(connection).rollback(); verify(connection, never()).commit(); verify(master, never()).getConnection();
            verify(fixture.mapper, never()).insertEvidence(any());
        }
    }
}
