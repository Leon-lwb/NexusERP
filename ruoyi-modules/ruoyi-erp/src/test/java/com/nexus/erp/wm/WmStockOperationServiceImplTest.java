package com.nexus.erp.wm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper.Head;
import com.nexus.erp.wm.mapper.WmCertifiedReceiptPostingMapper.Posting;
import com.nexus.erp.wm.service.WmCertifiedReceiptMutationGuard;
import com.nexus.erp.wm.service.WmSerialInventoryService;
import com.nexus.erp.wm.service.impl.WmStockOperationServiceImpl;
import com.nexus.erp.wm.strategy.StockOperationStrategy;
import com.nexus.erp.wm.strategy.StockOperationStrategyFactory;
import com.nexus.erp.wm.strategy.impl.ProductionIssueStrategy;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.function.Consumer;

import javax.sql.DataSource;

/**
 * 使用真实服务及守卫的事务代理；JDBC 与来源端口使用测试替身，不连接数据库。
 */
class WmStockOperationServiceImplTest {
    static final String TENANT = "000007";
    static final Long RECEIVE = 101L;
    static final Long ORDER = 301L;

    @Test
    void certifiedPurchaseIsRejectedBeforeStrategyLookupInTheServiceTransaction() throws Exception {
        try (Fixture f = new Fixture()) {
            when(f.sources.lockCertifiedOrderIdentities(TENANT, ORDER)).thenReturn(List.of(ORDER));
            assertThrows(ServiceException.class, () -> f.service.postMaterialReceive(RECEIVE));
            verify(f.sources).lockHead(TENANT, RECEIVE);
            verifyNoInteractions(f.factory, f.strategy);
            verify(f.connection).rollback();
            verify(f.connection, never()).commit();
        }
    }

    @Test
    void permanentPostingCannotBeClaimedAsLegacyEvenIfTheHeaderWasReset() throws Exception {
        try (Fixture f = new Fixture()) {
            when(f.sources.lockPosting(TENANT, RECEIVE)).thenReturn(posting());
            assertThrows(ServiceException.class, () -> f.service.postMaterialReceive(RECEIVE));
            verifyNoInteractions(f.factory, f.strategy);
            verify(f.connection).rollback();
        }
    }

    @Test
    void missingOrCrossTenantCurrentSourceCannotReachTheStrategy() throws Exception {
        for (Head bad : new Head[] {null, head("000008")}) {
            try (Fixture f = new Fixture()) {
                doReturn(bad).when(f.sources).lockHead(TENANT, RECEIVE);
                assertThrows(ServiceException.class, () -> f.service.postMaterialReceive(RECEIVE));
                verifyNoInteractions(f.factory, f.strategy);
            }
        }
    }

    @Test
    void missingDisabledOrMalformedTenantFailsBeforeAnySourceOrStrategy() throws Exception {
        for (String tenant : new String[] {null, "", " 000007", "other-tenant"}) {
            try (Fixture f = new Fixture()) {
                f.tenants.when(TenantHelper::getTenantId).thenReturn(tenant);
                assertThrows(ServiceException.class, () -> f.service.postMaterialReceive(RECEIVE));
                verifyNoInteractions(f.sources, f.factory, f.strategy);
            }
        }
        try (Fixture f = new Fixture()) {
            f.tenants.when(TenantHelper::isEnable).thenReturn(false);
            assertThrows(ServiceException.class, () -> f.service.postMaterialReceive(RECEIVE));
            verifyNoInteractions(f.sources, f.factory, f.strategy);
        }
    }

    @Test
    void absentOrNonPositiveReceiptIdentityFailsBeforeStrategyLookup() throws Exception {
        for (Long id : new Long[] {null, 0L, -1L}) {
            try (Fixture f = new Fixture()) {
                assertThrows(ServiceException.class, () -> f.service.postMaterialReceive(id));
                verifyNoInteractions(f.sources, f.factory, f.strategy);
            }
        }
    }

    @Test
    void sourceReadFailureCannotFallBackToLegacyDispatch() throws Exception {
        try (Fixture f = new Fixture()) {
            doThrow(new IllegalStateException("unavailable"))
                    .when(f.sources)
                    .lockHead(TENANT, RECEIVE);
            assertThrows(ServiceException.class, () -> f.service.postMaterialReceive(RECEIVE));
            verifyNoInteractions(f.factory, f.strategy);
        }
    }

    @Test
    void unproxiedServiceCannotBypassTheMandatoryGuardTransaction() throws Exception {
        try (Fixture f = new Fixture()) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertThrows(
                    IllegalTransactionStateException.class,
                    () -> f.target.postMaterialReceive(RECEIVE));
            verifyNoInteractions(f.sources, f.factory, f.strategy);
        }
    }

    @Test
    void unprovenLegacyStillDelegatesWithoutCreatingACertifiedPosting() throws Exception {
        try (Fixture f = new Fixture()) {
            doAnswer(
                            call -> {
                                assertTrue(
                                        TransactionSynchronizationManager
                                                .isActualTransactionActive());
                                assertFalse(
                                        TransactionSynchronizationManager
                                                .isCurrentTransactionReadOnly());
                                return null;
                            })
                    .when(f.strategy)
                    .execute(RECEIVE);
            assertDoesNotThrow(() -> f.service.postMaterialReceive(RECEIVE));
            var order = inOrder(f.sources, f.factory, f.strategy);
            order.verify(f.sources).lockHead(TENANT, RECEIVE);
            order.verify(f.sources).lockPosting(TENANT, RECEIVE);
            order.verify(f.sources).lockCertifiedOrderIdentities(TENANT, ORDER);
            order.verify(f.factory).getStrategy(StockOperationTypeEnum.RECEIVE_IN);
            order.verify(f.strategy).execute(RECEIVE);
            verify(f.connection).commit();
            verify(f.connection, never()).rollback();
        }
    }

    @Test
    void downstreamLegacyFailureKeepsTheExistingRollbackBoundary() throws Exception {
        try (Fixture f = new Fixture()) {
            doThrow(new ServiceException("legacy failed")).when(f.strategy).execute(RECEIVE);
            assertThrows(ServiceException.class, () -> f.service.postMaterialReceive(RECEIVE));
            verify(f.connection).rollback();
            verify(f.connection, never()).commit();
        }
    }

    @Test
    void unrelatedStockOperationsKeepTheirExistingDispatch() throws Exception {
        try (Fixture f = new Fixture()) {
            when(f.factory.getStrategy(StockOperationTypeEnum.SALES_OUT)).thenReturn(f.strategy);
            f.service.postSalesIssue(202L);
            verify(f.strategy).execute(202L);
            verifyNoInteractions(f.sources);
        }
    }

    static Head head(String tenant) {
        return new Head(
                tenant,
                RECEIVE,
                "RK-101",
                ORDER,
                401L,
                "SHELVED",
                501L,
                601L,
                null,
                BigDecimal.ONE,
                BigDecimal.TEN,
                "0");
    }

    static Posting posting() {
        return new Posting(
                TENANT,
                RECEIVE,
                201L,
                901L,
                "a".repeat(64),
                new BigDecimal("3"),
                "2026-10-03 10:00:00",
                "ENQUEUED");
    }

    @SuppressWarnings("unchecked")
    static <T> T transactionProxy(T target, DataSourceTransactionManager manager) {
        var transactions = new TransactionInterceptor();
        transactions.setTransactionManager(manager);
        transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        proxy.addAdvice(transactions);
        return (T) proxy.getProxy();
    }

    @Test
    void failedSpringSetupReleasesItsOwnTenantStaticMock() throws Exception {
        assertThrows(
                IllegalStateException.class,
                () ->
                        new Fixture(
                                context ->
                                        context.addBeanFactoryPostProcessor(
                                                factory -> {
                                                    throw new IllegalStateException(
                                                            "fixture refresh failed");
                                                })));
        try (MockedStatic<TenantHelper> recovered = mockStatic(TenantHelper.class)) {
            recovered.when(TenantHelper::getTenantId).thenReturn("recovered");
            assertEquals("recovered", TenantHelper.getTenantId());
        }
        try (Fixture next = new Fixture()) {
            assertDoesNotThrow(() -> next.service.postMaterialReceive(RECEIVE));
        }
    }

    static class Fixture implements AutoCloseable {
        final MockedStatic<TenantHelper> tenants;
        final WmCertifiedReceiptPostingMapper sources = mock(WmCertifiedReceiptPostingMapper.class);
        final StockOperationStrategyFactory factory = mock(StockOperationStrategyFactory.class);
        final StockOperationStrategy strategy = mock(StockOperationStrategy.class);
        final ProductionIssueStrategy production = mock(ProductionIssueStrategy.class);
        final DataSource dataSource = mock(DataSource.class);
        final Connection connection = mock(Connection.class);
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        final WmStockOperationServiceImpl target;
        final WmStockOperationServiceImpl service;

        Fixture() throws Exception {
            this(context -> {});
        }

        Fixture(Consumer<AnnotationConfigApplicationContext> beforeRefresh) throws Exception {
            tenants = mockStatic(TenantHelper.class);
            try {
                tenants.when(TenantHelper::isEnable).thenReturn(true);
                tenants.when(TenantHelper::getTenantId).thenReturn(TENANT);
                when(dataSource.getConnection()).thenReturn(connection);
                when(connection.getAutoCommit()).thenReturn(true);
                var manager = new DataSourceTransactionManager(dataSource);
                var guard = transactionProxy(new WmCertifiedReceiptMutationGuard(sources), manager);
                when(sources.lockHead(TENANT, RECEIVE))
                        .thenAnswer(
                                call -> {
                                    assertTrue(
                                            TransactionSynchronizationManager
                                                    .isActualTransactionActive());
                                    return head(TENANT);
                                });
                when(sources.lockCertifiedOrderIdentities(TENANT, ORDER)).thenReturn(List.of());
                when(factory.getStrategy(StockOperationTypeEnum.RECEIVE_IN)).thenReturn(strategy);
                context.registerBean(StockOperationStrategyFactory.class, () -> factory);
                context.registerBean(ProductionIssueStrategy.class, () -> production);
                context.registerBean(
                        WmSerialInventoryService.class, () -> mock(WmSerialInventoryService.class));
                context.registerBean(WmCertifiedReceiptMutationGuard.class, () -> guard);
                context.registerBean(WmStockOperationServiceImpl.class);
                beforeRefresh.accept(context);
                context.refresh();
                target = context.getBean(WmStockOperationServiceImpl.class);
                service = transactionProxy(target, manager);
            } catch (Exception | Error failure) {
                try {
                    close();
                } catch (Throwable cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        @Override
        public void close() {
            try {
                context.close();
            } finally {
                tenants.close();
            }
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
        }
    }
}
