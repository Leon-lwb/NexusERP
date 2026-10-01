package com.nexus.erp.pu;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationAdvisor;
import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationInterceptor;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import com.baomidou.dynamic.datasource.enums.SeataMode;
import com.baomidou.dynamic.datasource.processor.DsSpelExpressionProcessor;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.dynamic.datasource.tx.TransactionContext;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.p6spy.engine.spy.P6DataSource;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper;
import com.nexus.erp.pu.mapper.PuRequirementMaterialGuardMapper;
import com.nexus.erp.pu.service.PuRequirementCompetitionPoolReader;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.pu.service.PuRequirementMaterialLockService;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSession;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mybatis.spring.SqlSessionHolder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.SqlSessionUtils;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 内部 ERP 物理事务入口的离线 RED 契约，不代表采购接线、数据库验收或用户进程已经加载。
 * 缺类通过测试内反射断言失败，而非 javac 引用不存在的类型。正例只调用真实入口，
 * 不由夹具补绑 router；真实 Spring、DefaultSqlSessionFactory 及 SqlSessionTemplate
 * 执行生命周期，仅数据源、JDBC 和 reader 业务依赖使用非联网模拟，不启动 Boot 或服务。
 * 本轮仅验证有界事务归属和正常 MyBatis 参与，不提供权限、来源或额度认证。
 */
@Tag("dev")
class PuRequirementErpTransactionExecutorTest {

    private static final String EXECUTOR = "com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor";
    private static final String TENANT = "t-100";
    private static final Long MATERIAL = 139L;
    private static final LocalDate WINDOW_END = LocalDate.of(2026, 10, 1);
    /** 纯离线哨兵，不含真实 SQL、口令或业务数据。 */
    private static final String PRIVATE_DIAGNOSTIC = "OFFLINE_SQL_PAYLOAD|OFFLINE_CREDENTIAL_PAYLOAD";

    @AfterEach
    void threadResourcesMustBeClean() {
        assertCleanThread();
    }

    @Test
    void springConstructsNamedFactoryComponentWithoutBorrowOrGlobalTransactionManager() throws SQLException {
        Fixture fixture = new Fixture();
        assertNotNull(executorType().getAnnotation(Component.class));
        assertNotNull(fixture.entry());
        fixture.assertNoBorrow();
    }

    @Test
    void realEntryQualifiesActualReaderProxyAndMybatisFlushesSameConnectionBeforeCommit() throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        fixture.checkOwnedAliasesAtJdbcCompletion = true;
        Object expected = new Object();
        DynamicDataSourceContextHolder.push("master");
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertSame(expected, entry.execute(TENANT, () -> {
                fixture.assertOwnedWritableTransaction();
                fixture.qualification.requireWritableErpTransaction();
                assertNotNull(fixture.proxiedReader().materialPool(TENANT, MATERIAL, WINDOW_END));
                assertEquals(List.of("guard", "lockRequirementDetails", "lockStocks", "lockOrderLines",
                    "lockRequisitionLines", "lockAllocations"), fixture.readerTrace);
                assertEquals("master", DynamicDataSourceContextHolder.peek(), "实际 @DS 代理应恢复外层路由");
                fixture.stageBatch();
                assertSame(fixture.configuration, fixture.sessionHolder().getSqlSession().getConfiguration());
                assertSame(fixture.erpJdbc.connection, fixture.template.getConnection());
                assertThrows(ServiceException.class, fixture.qualification::requireWritableErpTransaction,
                    "不能通过放宽原资格对已绑定会话的拒绝实现入口末端核验");
                fixture.observeCompletion();
                return expected;
            }), "回调值只能在真实提交及清理后返回");
            assertEquals("master", DynamicDataSourceContextHolder.peek());
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
        assertBefore(fixture.events, "erp.flush", "erp.commit");
        assertBefore(fixture.events, "erp.commit", "completion.0");
        assertBefore(fixture.events, "completion.0", "erp.close");
        verify(fixture.erpJdbc.statement, times(1)).executeBatch();
        verify(fixture.erpJdbc.connection, times(1)).commit();
        verify(fixture.erpJdbc.connection, never()).rollback();
        verify(fixture.erpJdbc.connection, times(1)).close();
        assertTrue(fixture.erpJdbc.autoCommit.get());
        fixture.assertOnlyErpBorrowed(1);
        fixture.assertNoIdentityGuessing();
    }

    @Test
    void explicitItemRegistrationStartsTransactionOnItsRealPhysicalDatasource() throws SQLException {
        Fixture fixture = new Fixture();
        ItemDataSource registered = new ItemDataSource("erp", fixture.erp, fixture.erp, false, false, SeataMode.AT);
        fixture.routing.getDataSources().put("erp", registered);
        Entry entry = fixture.entry();
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertEquals("physical", entry.execute(TENANT, () -> {
                fixture.assertOwnedWritableTransaction();
                assertNull(TransactionSynchronizationManager.getResource(registered));
                fixture.qualification.requireWritableErpTransaction();
                fixture.stageBatch();
                return "physical";
            }));
        }
        fixture.assertOnlyErpBorrowed(1);
        fixture.assertNoIdentityGuessing();
    }

    /**
     * 标准 Item/P6 链必须兼容当前 p6spy=true 配置，不要求用户关闭全局 Spy。
     * 本正例选择内部专属事务直取真实 Hikari 的最小拓扑：此路径不借 P6 connection，
     * 因而不能声称产生了该连接的 Spy 日志；不修改全局 Spy 或安全审计配置。
     */
    @Test
    void knownItemP6RegistrationUsesSamePhysicalHikariWithoutBorrowingSpyConnection() throws SQLException {
        Fixture fixture = new Fixture();
        P6DataSource checkedSpy = spy(new P6DataSource(fixture.erp));
        assertEquals(P6DataSource.class, checkedSpy.getClass(), "已依赖 inline mock 保持精确已核类型");
        ItemDataSource registered = new ItemDataSource("erp", fixture.erp, checkedSpy, true, false, SeataMode.AT);
        fixture.routing.getDataSources().put("erp", registered);
        Entry entry = fixture.entry();
        fixture.checkOwnedAliasesAtJdbcCompletion = true;
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertEquals("p6-config-supported", entry.execute(TENANT, () -> {
                fixture.assertOwnedWritableTransaction();
                assertNull(TransactionSynchronizationManager.getResource(registered));
                assertNull(TransactionSynchronizationManager.getResource(checkedSpy));
                fixture.qualification.requireWritableErpTransaction();
                assertNotNull(fixture.proxiedReader().materialPool(TENANT, MATERIAL, WINDOW_END));
                fixture.stageBatch();
                assertSame(fixture.erpJdbc.connection, fixture.template.getConnection());
                return "p6-config-supported";
            }));
        }
        fixture.assertOnlyErpBorrowed(1);
        verify(checkedSpy, never()).getConnection();
        verify(fixture.erpJdbc.statement, times(1)).executeBatch();
        verify(fixture.erpJdbc.connection, times(1)).commit();
        verify(fixture.erpJdbc.connection, never()).rollback();
        verify(fixture.erpJdbc.connection, times(1)).close();
        fixture.assertNoIdentityGuessing();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void callbackFailureOrRollbackOnlyCannotReturnSuccessOrFlushBatch(boolean rollbackOnly) throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        fixture.checkOwnedAliasesAtJdbcCompletion = true;
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertThrows(RuntimeException.class, () -> entry.execute(TENANT, () -> {
                fixture.stageBatch();
                fixture.observeCompletion();
                if (!rollbackOnly) {
                    throw new ServiceException("离线业务校验拒绝");
                }
                fixture.holder(fixture.erp).setRollbackOnly();
                return "must-not-return";
            }));
        }
        fixture.assertRolledBack();
        verify(fixture.erpJdbc.statement, never()).executeBatch();
        assertBefore(fixture.events, "erp.rollback", "completion.1");
        assertBefore(fixture.events, "completion.1", "erp.close");
        fixture.assertOnlyErpBorrowed(1);
    }

    /** 仅构造并捕获模拟 OOM 对象，不耗尽内存；要求清理完成后仍传播同一对象。 */
    @Test
    void outOfMemoryErrorPropagatesIdenticallyAfterRollbackAndAllowsNextCall() throws SQLException {
        assertFatalRollbackAndReuse(new OutOfMemoryError("离线模拟 OOM，不制造真实内存耗尽"));
    }

    /** 仅构造并捕获模拟 ThreadDeath，不调用 stop/interrupt 或结束实际线程。 */
    @Test
    void threadDeathPropagatesIdenticallyAfterRollbackAndAllowsNextCall() throws SQLException {
        assertFatalRollbackAndReuse(new ThreadDeath());
    }

    @Test
    void independentCallAfterRollbackUsesFreshConnectionAndCleansEachLifecycle() throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        JdbcEndpoint next = fixture.newEndpoint("erp-next");
        fixture.availableErpConnections.addLast(next);
        fixture.checkOwnedAliasesAtJdbcCompletion = true;
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertThrows(RuntimeException.class, () -> entry.execute(TENANT, () -> {
                fixture.stageBatch();
                fixture.holder(fixture.erp).setRollbackOnly();
                return "discard";
            }));
            assertCleanThread();
            assertEquals("next", entry.execute(TENANT, () -> {
                fixture.assertOwnedWritableTransaction();
                assertSame(next.connection, fixture.holder(fixture.erp).getConnection());
                fixture.stageBatch();
                assertSame(next.connection, fixture.template.getConnection());
                return "next";
            }));
        }
        verify(fixture.erpJdbc.connection, times(1)).rollback();
        verify(fixture.erpJdbc.connection, never()).commit();
        verify(next.connection, times(1)).commit();
        verify(next.connection, never()).rollback();
        verify(fixture.erpJdbc.connection, times(1)).close();
        verify(next.connection, times(1)).close();
        fixture.assertOnlyErpBorrowed(2);
    }

    @Test
    void nestedEntryIsRejectedBeforeAdditionalBorrowWithoutDamagingOuterTransaction() throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        AtomicBoolean innerCalled = new AtomicBoolean();
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertEquals("outer", entry.execute(TENANT, () -> {
                Map<Object, Object> before = TransactionSynchronizationManager.getResourceMap();
                assertThrows(ServiceException.class, () -> entry.execute(TENANT, () -> {
                    innerCalled.set(true);
                    return null;
                }));
                assertEquals(before, TransactionSynchronizationManager.getResourceMap());
                fixture.qualification.requireWritableErpTransaction();
                return "outer";
            }));
        }
        assertFalse(innerCalled.get());
        fixture.assertOnlyErpBorrowed(1);
        verify(fixture.erpJdbc.connection, times(1)).commit();
    }

    @ParameterizedTest
    @MethodSource("invalidTenants")
    void tenantMustBeEnabledPresentAndMatchBeforeBorrow(boolean enabled, String current, String requested)
        throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        try (MockedStatic<TenantHelper> ignored = tenant(enabled, current)) {
            fixture.assertRejectedBeforeBorrow(entry, requested);
        }
    }

    static Stream<Arguments> invalidTenants() {
        return Stream.of(
            Arguments.of(false, TENANT, TENANT), Arguments.of(true, null, TENANT),
            Arguments.of(true, "", TENANT), Arguments.of(true, "other-tenant", TENANT),
            Arguments.of(true, TENANT, null), Arguments.of(true, TENANT, " "),
            Arguments.of(true, TENANT, "other-tenant"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void existingActiveFlagOrSynchronizationIsRejectedWithoutClearingCallerState(boolean synchronization)
        throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        TransactionSynchronization marker = new TransactionSynchronization() { };
        if (synchronization) {
            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.registerSynchronization(marker);
        } else {
            TransactionSynchronizationManager.setActualTransactionActive(true);
        }
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            fixture.assertRejectedBeforeBorrow(entry, TENANT);
            assertEquals(synchronization, TransactionSynchronizationManager.isSynchronizationActive());
            assertEquals(!synchronization, TransactionSynchronizationManager.isActualTransactionActive());
            if (synchronization) {
                assertEquals(List.of(marker), TransactionSynchronizationManager.getSynchronizations());
            }
        } finally {
            if (synchronization) {
                TransactionSynchronizationManager.clearSynchronization();
            } else {
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ExistingResource.class)
    void anyExistingResourceIsRetainedAndRejectedBeforeBorrow(ExistingResource kind) throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        Object key = switch (kind) {
            case MASTER -> fixture.master;
            case ERP -> fixture.erp;
            case ROUTER -> fixture.routing;
            case SQL_SESSION -> fixture.sessionFactory;
            case UNKNOWN -> new Object();
        };
        SqlSession session = mock(SqlSession.class);
        Object caller = kind == ExistingResource.SQL_SESSION ? new SqlSessionHolder(session, ExecutorType.SIMPLE, null)
            : new Object();
        TransactionSynchronizationManager.bindResource(key, caller);
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            fixture.assertRejectedBeforeBorrow(entry, TENANT);
            assertEquals(Map.of(key, caller), TransactionSynchronizationManager.getResourceMap());
            verifyNoInteractions(session);
        } finally {
            releaseCallerResource(key, caller);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void realExternalMasterTransactionIsNotJoinedSuspendedOrAugmented(boolean readOnly) throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        TransactionTemplate caller = new TransactionTemplate(new DataSourceTransactionManager(fixture.master));
        caller.setReadOnly(readOnly);
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            caller.execute(status -> {
                Map<Object, Object> resources = TransactionSynchronizationManager.getResourceMap();
                List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
                try {
                    fixture.assertRejectedBeforeCallback(entry, TENANT);
                    assertEquals(resources, TransactionSynchronizationManager.getResourceMap());
                    assertEquals(synchronizations, TransactionSynchronizationManager.getSynchronizations());
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    assertEquals(readOnly, TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                    assertNull(TransactionSynchronizationManager.getResource(fixture.erp));
                    assertNull(TransactionSynchronizationManager.getResource(fixture.routing));
                } finally {
                    status.setRollbackOnly();
                }
                return null;
            });
        }
        verify(fixture.erp, never()).getConnection();
        verify(fixture.master, times(1)).getConnection();
        verify(fixture.masterJdbc.connection, times(1)).rollback();
        fixture.assertNoIdentityGuessing();
    }

    @ParameterizedTest
    @EnumSource(ExternalErpTransaction.class)
    void externalErpTransactionsCannotBeConvertedIntoEntryOwnedTransactions(ExternalErpTransaction kind)
        throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        boolean routerOnly = kind == ExternalErpTransaction.ROUTER_ONLY;
        boolean alias = kind == ExternalErpTransaction.EXTERNALLY_ALIASED;
        DynamicDataSourceContextHolder.push("erp");
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            DataSource source = routerOnly ? fixture.routing : fixture.erp;
            TransactionTemplate caller = new TransactionTemplate(new DataSourceTransactionManager(source));
            caller.execute(status -> {
                ConnectionHolder original = fixture.holder(source);
                if (alias) {
                    /** 仅为拒绝外部补绑的反例，绝不作为新入口的正例或生产证明。 */
                    TransactionSynchronizationManager.bindResource(fixture.routing, original);
                }
                try {
                    if (alias) {
                        fixture.qualification.requireWritableErpTransaction();
                    }
                    Map<Object, Object> resources = TransactionSynchronizationManager.getResourceMap();
                    fixture.assertRejectedBeforeCallback(entry, TENANT);
                    assertEquals(resources, TransactionSynchronizationManager.getResourceMap());
                    assertSame(original, fixture.holder(source));
                    if (routerOnly) {
                        assertNull(TransactionSynchronizationManager.getResource(fixture.erp));
                    } else if (!alias) {
                        assertNull(TransactionSynchronizationManager.getResource(fixture.routing));
                    }
                } finally {
                    if (alias) {
                        releaseCallerResource(fixture.routing, original);
                    }
                    status.setRollbackOnly();
                }
                return null;
            });
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
        fixture.assertOnlyErpBorrowed(1);
    }

    @Test
    void existingDynamicTransactionXidIsRejectedBeforeBorrowAndPreserved() throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        TransactionContext.bind("offline-existing-xid");
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            fixture.assertRejectedBeforeBorrow(entry, TENANT);
            assertEquals("offline-existing-xid", TransactionContext.getXID());
        } finally {
            TransactionContext.remove();
        }
    }

    @ParameterizedTest
    @EnumSource(InvalidTopology.class)
    void invalidFactoryOrRegistryFailsClosedBeforeCallbackAndBorrow(InvalidTopology topology) throws SQLException {
        executorType();
        Fixture fixture = new Fixture(topology);
        AtomicBoolean called = new AtomicBoolean();
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertThrows(RuntimeException.class, () -> fixture.entry().execute(TENANT, () -> {
                called.set(true);
                return null;
            }));
        }
        assertFalse(called.get());
        fixture.assertNoBorrow();
        fixture.assertNoIdentityGuessing();
    }

    /** 标准委托与不继承 DelegatingDataSource 的自定义包装均不得当作已核物理池。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unreviewedErpWrapperIsRejectedBeforeErpOrMasterBorrow(boolean customWrapper) throws SQLException {
        Fixture fixture = new Fixture();
        DataSource wrapper = customWrapper ? new UnreviewedDataSourceWrapper(fixture.master)
            : new DelegatingDataSource(fixture.master);
        fixture.routing.getDataSources().put("erp", wrapper);
        Entry entry = fixture.entry();
        AtomicBoolean called = new AtomicBoolean();
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertAll("未知包装必须在 callback 和任一物理借连接之前拒绝，不按 catalog 猜 ERP",
                () -> assertThrows(ServiceException.class, () -> entry.execute(TENANT, () -> {
                    called.set(true);
                    return "must-not-run";
                })),
                () -> assertFalse(called.get(), "拒绝不能等到包装已借 master 之后"),
                () -> verify(fixture.erp, never()).getConnection(),
                () -> verify(fixture.master, never()).getConnection(),
                () -> verify(fixture.erpJdbc.connection, never()).commit(),
                () -> verify(fixture.masterJdbc.connection, never()).commit(),
                fixture::assertNoIdentityGuessing,
                PuRequirementErpTransactionExecutorTest::assertCleanThread);
        }
    }

    /** P6 外形不授物理资格；未知包装、覆写子类、内层错池、Seata 和共享池仍在借连接前拒绝。 */
    @ParameterizedTest
    @EnumSource(InvalidP6Registration.class)
    void unreviewedOrMismatchedP6RegistrationCannotBorrowErpOrMaster(InvalidP6Registration invalid)
        throws SQLException {
        Fixture fixture = new Fixture();
        DataSource decorated = switch (invalid) {
            case UNKNOWN_WRAPPER -> new UnreviewedDataSourceWrapper(fixture.master);
            case P6_SUBCLASS -> new UnreviewedP6DataSource(fixture.erp, fixture.master);
            case UNKNOWN_P6_INNER -> new P6DataSource(new UnreviewedDataSourceWrapper(fixture.erp));
            case WRONG_INNER_POOL -> new P6DataSource(fixture.master);
            case SEATA, SHARED_ERP_POOL -> new P6DataSource(fixture.erp);
        };
        fixture.routing.getDataSources().put("erp", new ItemDataSource("erp", fixture.erp, decorated,
            true, invalid == InvalidP6Registration.SEATA, SeataMode.AT));
        if (invalid == InvalidP6Registration.SHARED_ERP_POOL) {
            fixture.routing.getDataSources().put("master", new ItemDataSource("master", fixture.erp,
                new P6DataSource(fixture.erp), true, false, SeataMode.AT));
        }
        Entry entry = fixture.entry();
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            fixture.assertRejectedBeforeBorrow(entry, TENANT);
        }
        verify(fixture.erpJdbc.connection, never()).commit();
        verify(fixture.masterJdbc.connection, never()).commit();
        fixture.assertNoIdentityGuessing();
    }

    @ParameterizedTest
    @EnumSource(ConfigurationDrift.class)
    void registryOrEnvironmentIdentityDriftCannotCommitOrBorrowReplacement(ConfigurationDrift drift)
        throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        DataSource replacement = mock(DataSource.class);
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertThrows(RuntimeException.class, () -> entry.execute(TENANT, () -> {
                fixture.stageBatch();
                switch (drift) {
                    case PHYSICAL -> fixture.routing.getDataSources().put("erp", replacement);
                    case REGISTRATION -> fixture.routing.getDataSources().put("erp",
                        new ItemDataSource("erp", fixture.erp, fixture.erp, false, false, SeataMode.AT));
                    case ENVIRONMENT -> fixture.configuration.setEnvironment(new Environment(
                        "offline-replaced", new SpringManagedTransactionFactory(), fixture.routing));
                }
                return "discard";
            }));
        }
        fixture.assertRolledBack();
        verify(replacement, never()).getConnection();
        fixture.assertOnlyErpBorrowed(1);
    }

    @Test
    void unknownResourceAddedByCallbackIsRetainedWhileOwnedTransactionRollsBack() throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        Object key = new Object();
        Object value = new Object();
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertThrows(RuntimeException.class, () -> entry.execute(TENANT, () -> {
                TransactionSynchronizationManager.bindResource(key, value);
                return "discard";
            }));
            assertOnlyCallerResource(key, value);
        } finally {
            releaseCallerResource(key, value);
        }
        fixture.assertRolledBack();
        fixture.assertOnlyErpBorrowed(1);
    }

    @Test
    void replacedRoutingAliasIsRejectedWithoutOverwritingOrRemovingCallerHolder() throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        Connection callerConnection = mock(Connection.class);
        ConnectionHolder caller = new ConnectionHolder(callerConnection);
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertThrows(RuntimeException.class, () -> entry.execute(TENANT, () -> {
                assertSame(fixture.holder(fixture.erp), TransactionSynchronizationManager.unbindResource(fixture.routing));
                TransactionSynchronizationManager.bindResource(fixture.routing, caller);
                return "discard";
            }));
            assertOnlyCallerResource(fixture.routing, caller);
            verifyNoInteractions(callerConnection);
        } finally {
            releaseCallerResource(fixture.routing, caller);
        }
        fixture.assertRolledBack();
        fixture.assertOnlyErpBorrowed(1);
    }

    @Test
    void lateBeforeCommitResourceMutationAfterMybatisFlushStillPreventsJdbcCommit() throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        Object key = new Object();
        Object value = new Object();
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertThrows(RuntimeException.class, () -> entry.execute(TENANT, () -> {
                fixture.stageBatch();
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public int getOrder() {
                        return Ordered.LOWEST_PRECEDENCE - 1;
                    }

                    @Override
                    public void beforeCommit(boolean readOnly) {
                        TransactionSynchronizationManager.bindResource(key, value);
                    }
                });
                return "discard";
            }));
            assertOnlyCallerResource(key, value);
        } finally {
            releaseCallerResource(key, value);
        }
        fixture.assertRolledBack();
        verify(fixture.erpJdbc.statement, times(1)).executeBatch();
        fixture.assertOnlyErpBorrowed(1);
    }

    /** late factory 替换必须拒绝提交；Spring 吞掉 beforeCompletion 异常并不构成事务保护。 */
    @Test
    void lateFactoryReplacementAfterRealMybatisFlushCannotCommitEvenIfBeforeCompletionExceptionIsSwallowed()
        throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        SqlSession foreignSession = mock(SqlSession.class);
        SqlSessionHolder foreign = new SqlSessionHolder(foreignSession, ExecutorType.SIMPLE, null);
        foreign.setSynchronizedWithTransaction(true);
        AtomicBoolean callbackFinished = new AtomicBoolean();
        AtomicBoolean realFlushObserved = new AtomicBoolean();
        AtomicBoolean lateReplacement = new AtomicBoolean();
        AtomicBoolean beforeCompletionThrew = new AtomicBoolean();
        String returned = null;
        RuntimeException rejection = null;
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            try {
                returned = entry.execute(TENANT, () -> {
                    fixture.stageBatch();
                    SqlSessionHolder original = fixture.sessionHolder();
                    assertEquals(DefaultSqlSession.class, original.getSqlSession().getClass());
                    assertSame(fixture.configuration, original.getSqlSession().getConfiguration());
                    assertFalse(fixture.events.contains("erp.flush"), "callback 只排队，不提前伪造 late 阶段 flush");
                    fixture.observeCompletion();
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public int getOrder() {
                            return Ordered.LOWEST_PRECEDENCE - 1;
                        }

                        @Override
                        public void beforeCommit(boolean readOnly) {
                            assertTrue(callbackFinished.get(), "必须在正常 callback 返回及 prepare 核验之后触发");
                            assertTrue(fixture.events.contains("erp.flush"), "必须已由真实 MyBatis beforeCommit flush");
                            assertEquals(0, fixture.erpJdbc.pending.get(), "批语句必须已实际送到非联网 JDBC");
                            assertDoesNotThrow(() -> verify(fixture.erpJdbc.statement, times(1)).executeBatch());
                            assertSame(original, fixture.sessionHolder());
                            realFlushObserved.set(true);
                            assertSame(original, TransactionSynchronizationManager.unbindResource(fixture.sessionFactory));
                            TransactionSynchronizationManager.bindResource(fixture.sessionFactory, foreign);
                            fixture.events.add("late.factory.replace");
                            lateReplacement.set(true);
                        }

                        @Override
                        public void beforeCompletion() {
                            if (lateReplacement.get()) {
                                beforeCompletionThrew.set(true);
                                fixture.events.add("late.beforeCompletion.throw");
                                throw new IllegalStateException("离线模拟 beforeCompletion 异常，不等于阻止提交");
                            }
                        }
                    });
                    callbackFinished.set(true);
                    return "must-not-return";
                });
            } catch (RuntimeException failure) {
                rejection = failure;
            }
            assertTrue(callbackFinished.get(), "不能以 callback/工厂/fixture 失败充当 late 反例");
            assertTrue(realFlushObserved.get(), "必须到达原生 MyBatis flush 后的同步阶段");
            assertTrue(lateReplacement.get(), "必须完成真实参与后的 factory 替换");
            assertTrue(beforeCompletionThrew.get(), "覆盖 Spring 会吞 beforeCompletion 异常的实际路径");
            assertBefore(fixture.events, "erp.flush", "late.factory.replace");
            assertBefore(fixture.events, "late.factory.replace", "late.beforeCompletion.throw");
            fixture.assertOnlyErpBorrowed(1);
            fixture.assertNoIdentityGuessing();
            String observedReturn = returned;
            RuntimeException observedRejection = rejection;
            assertAll("late factory violation 必须在真实 JDBC 提交前失败，而不是仅抛一个被 Spring 吞掉的异常",
                () -> assertNull(observedReturn, "不能返回已经执行完的 callback 值"),
                () -> assertNotNull(observedRejection, "入口必须报告拒绝，不能被 beforeCompletion 吞异常后成功"),
                () -> verify(fixture.erpJdbc.connection, never()).commit(),
                () -> verify(fixture.erpJdbc.connection, times(1)).rollback(),
                () -> verify(fixture.erpJdbc.connection, times(1)).close(),
                () -> assertTrue(fixture.erpJdbc.closed.get(), "自有物理连接必须清理完成"),
                () -> verify(fixture.erpJdbc.statement, times(1)).executeBatch(),
                () -> verify(foreignSession, never()).getConnection(),
                () -> verify(foreignSession, never()).close(),
                () -> assertTrue(fixture.events.contains("completion.1"), "必须实际完成 rollback 生命周期"),
                () -> assertOnlyCallerResource(fixture.sessionFactory, foreign));
        } finally {
            releaseCallerResource(fixture.sessionFactory, foreign);
        }
    }

    /** 同序回调先注册时，原生 MyBatis 清理前的最后一次资源替换仍必须阻止物理提交。 */
    @Test
    void factoryReplacementBetweenOwnedObservationAndNativeMybatisCleanupCannotCommit() throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        SqlSession foreignSession = mock(SqlSession.class);
        SqlSessionHolder foreign = new SqlSessionHolder(foreignSession, ExecutorType.SIMPLE, null);
        foreign.setSynchronizedWithTransaction(true);
        AtomicBoolean callbackFinished = new AtomicBoolean();
        AtomicBoolean nativeFlushObserved = new AtomicBoolean();
        AtomicBoolean replacementReached = new AtomicBoolean();
        String returned = null;
        RuntimeException rejection = null;
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            try {
                returned = entry.execute(TENANT, () -> {
                    /** 在真实会话前注册同序同步，确保替换发生在入口观察后、原生解绑前。 */
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public int getOrder() {
                            return org.springframework.jdbc.datasource.DataSourceUtils.CONNECTION_SYNCHRONIZATION_ORDER - 1;
                        }

                        @Override
                        public void beforeCompletion() {
                            assertTrue(callbackFinished.get(), "必须在正常回调和全部提交前校验之后触发");
                            assertTrue(fixture.events.contains("erp.flush"), "必须已经发生真实 MyBatis 提交前刷新");
                            assertEquals(0, fixture.erpJdbc.pending.get(), "语句已实际发送到离线 JDBC");
                            SqlSessionHolder original = fixture.sessionHolder();
                            assertEquals(DefaultSqlSession.class, original.getSqlSession().getClass());
                            assertSame(fixture.configuration, original.getSqlSession().getConfiguration());
                            nativeFlushObserved.set(true);
                            assertSame(original, TransactionSynchronizationManager.unbindResource(fixture.sessionFactory));
                            TransactionSynchronizationManager.bindResource(fixture.sessionFactory, foreign);
                            fixture.events.add("between.owned.and.mybatis.replace");
                            replacementReached.set(true);
                        }
                    });
                    fixture.stageBatch();
                    fixture.observeCompletion();
                    callbackFinished.set(true);
                    return "must-not-return";
                });
            } catch (RuntimeException failure) {
                rejection = failure;
            }
            assertTrue(callbackFinished.get(), "不得以初始化或回调失败替代目标阶段反例");
            assertTrue(nativeFlushObserved.get(), "必须到达原生刷新后的阶段");
            assertTrue(replacementReached.get(), "必须在原生清理前真实替换资源");
            assertBefore(fixture.events, "erp.flush", "between.owned.and.mybatis.replace");
            fixture.assertOnlyErpBorrowed(1);
            String observedReturn = returned;
            RuntimeException observedRejection = rejection;
            assertAll("提交前任何资源替换不能被原生清理抹去后当作正常解绑",
                () -> assertNull(observedReturn),
                () -> assertNotNull(observedRejection),
                () -> verify(fixture.erpJdbc.connection, never()).commit(),
                () -> verify(fixture.erpJdbc.connection, times(1)).rollback(),
                () -> verify(fixture.erpJdbc.connection, times(1)).close(),
                () -> assertTrue(fixture.erpJdbc.closed.get()),
                () -> verify(fixture.erpJdbc.statement, times(1)).executeBatch(),
                () -> verify(foreignSession, never()).getConnection(),
                () -> verify(foreignSession, never()).close(),
                () -> assertTrue(fixture.events.contains("completion.1")),
                () -> assertOnlyCallerResource(fixture.sessionFactory, foreign));
        } finally {
            releaseCallerResource(fixture.sessionFactory, foreign);
        }
    }

    /** 异常或待回滚分支也须保留原生清理前晚到的外来工厂引用，不得只保护正常提交路径。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lateForeignFactoryDuringCallbackFailureOrRollbackOnlySurvivesCleanup(boolean rollbackOnly) throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        SqlSession foreignSession = mock(SqlSession.class);
        SqlSessionHolder foreign = new SqlSessionHolder(foreignSession, ExecutorType.SIMPLE, null);
        foreign.setSynchronizedWithTransaction(true);
        AtomicBoolean callbackReached = new AtomicBoolean();
        AtomicBoolean replacementReached = new AtomicBoolean();
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertThrows(RuntimeException.class, () -> entry.execute(TENANT, () -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public int getOrder() {
                        return org.springframework.jdbc.datasource.DataSourceUtils.CONNECTION_SYNCHRONIZATION_ORDER - 1;
                    }

                    @Override
                    public void beforeCompletion() {
                        assertTrue(callbackReached.get(), "异常必须发生在真实会话参与之后");
                        assertFalse(fixture.events.contains("erp.flush"), "回调异常不能提前执行提交前刷新");
                        SqlSessionHolder original = fixture.sessionHolder();
                        assertEquals(DefaultSqlSession.class, original.getSqlSession().getClass());
                        assertSame(fixture.configuration, original.getSqlSession().getConfiguration());
                        assertSame(original, TransactionSynchronizationManager.unbindResource(fixture.sessionFactory));
                        TransactionSynchronizationManager.bindResource(fixture.sessionFactory, foreign);
                        replacementReached.set(true);
                    }
                });
                fixture.stageBatch();
                fixture.observeCompletion();
                callbackReached.set(true);
                if (rollbackOnly) {
                    fixture.holder(fixture.erp).setRollbackOnly();
                    return "must-not-return";
                }
                throw new IllegalStateException("离线模拟正常业务回调异常");
            }));
            assertTrue(callbackReached.get());
            assertTrue(replacementReached.get(), "必须到达原生回滚清理前的真实替换");
            assertAll("回滚成功不等于可以删除调用方资源",
                () -> verify(fixture.erpJdbc.connection, never()).commit(),
                () -> verify(fixture.erpJdbc.connection, times(1)).rollback(),
                () -> verify(fixture.erpJdbc.connection, times(1)).close(),
                () -> assertTrue(fixture.erpJdbc.closed.get()),
                () -> verify(fixture.erpJdbc.statement, never()).executeBatch(),
                () -> verify(foreignSession, never()).getConnection(),
                () -> verify(foreignSession, never()).close(),
                () -> assertTrue(fixture.events.contains("completion.1")),
                () -> assertOnlyCallerResource(fixture.sessionFactory, foreign));
        } finally {
            releaseCallerResource(fixture.sessionFactory, foreign);
        }
        fixture.assertOnlyErpBorrowed(1);
    }

    /** 回滚驱动故障不能覆盖回调的原始致命错误；不把关闭完成当作回滚已成功。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void callbackFatalIdentitySurvivesJdbcRollbackFailure(boolean virtualMachineError) throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        Error original = virtualMachineError ? new OutOfMemoryError("离线模拟致命对象，不耗尽内存") : new ThreadDeath();
        doThrow(new SQLException(PRIVATE_DIAGNOSTIC)).when(fixture.erpJdbc.connection).rollback();
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            Throwable observed = assertThrows(Throwable.class, () -> entry.execute(TENANT, () -> {
                fixture.stageBatch();
                throw original;
            }));
            assertAll("原对象传播与回滚成功是两个不同事实",
                () -> assertSame(original, observed),
                () -> assertNull(original.getCause()),
                () -> assertEquals(0, original.getSuppressed().length),
                () -> verify(fixture.erpJdbc.connection, never()).commit(),
                () -> verify(fixture.erpJdbc.connection, times(1)).rollback(),
                () -> verify(fixture.erpJdbc.connection, times(1)).close(),
                () -> assertTrue(fixture.erpJdbc.closed.get()),
                () -> verify(fixture.erpJdbc.statement, never()).executeBatch(),
                PuRequirementErpTransactionExecutorTest::assertCleanThread);
        }
        fixture.assertOnlyErpBorrowed(1);
    }

    @Test
    void unknownSessionIntroducedByCallbackIsNotAcceptedAsOwnedOrClosed() throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        SqlSession session = mock(SqlSession.class);
        SqlSessionHolder caller = new SqlSessionHolder(session, ExecutorType.SIMPLE, null);
        caller.setSynchronizedWithTransaction(true);
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertThrows(RuntimeException.class, () -> entry.execute(TENANT, () -> {
                TransactionSynchronizationManager.bindResource(fixture.sessionFactory, caller);
                return "discard";
            }));
            assertOnlyCallerResource(fixture.sessionFactory, caller);
            verify(session, never()).getConnection();
            verify(session, never()).close();
        } finally {
            releaseCallerResource(fixture.sessionFactory, caller);
        }
        fixture.assertRolledBack();
        fixture.assertOnlyErpBorrowed(1);
    }

    /** 先建立真实 MyBatis 同步并执行离线 JDBC，再替换 factory 绑定，覆盖原生解绑生命周期。 */
    @Test
    void replacedFactoryHolderAfterRealMybatisParticipationSurvivesRollbackCleanup() throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        SqlSession foreignSession = mock(SqlSession.class);
        SqlSessionHolder foreign = new SqlSessionHolder(foreignSession, ExecutorType.SIMPLE, null);
        foreign.setSynchronizedWithTransaction(true);
        AtomicBoolean realParticipation = new AtomicBoolean();
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertThrows(RuntimeException.class, () -> entry.execute(TENANT, () -> {
                fixture.stageBatch();
                assertEquals(1, fixture.template.flushStatements().size(), "真实 MyBatis 执行一次非联网 JDBC 批语句");
                SqlSessionHolder original = fixture.sessionHolder();
                assertEquals(DefaultSqlSession.class, original.getSqlSession().getClass());
                assertSame(fixture.configuration, original.getSqlSession().getConfiguration());
                assertSame(fixture.erpJdbc.connection, fixture.template.getConnection());
                assertEquals(1L, TransactionSynchronizationManager.getSynchronizations().stream()
                    .filter(synchronization -> synchronization.getClass().getEnclosingClass() == SqlSessionUtils.class
                        && "SqlSessionSynchronization".equals(synchronization.getClass().getSimpleName())).count(),
                    "必须由真实 SqlSessionTemplate 已经注册同步，不能仅手绑一个普通 holder");
                fixture.observeCompletion();
                realParticipation.set(true);
                assertSame(original, TransactionSynchronizationManager.unbindResource(fixture.sessionFactory));
                TransactionSynchronizationManager.bindResource(fixture.sessionFactory, foreign);
                return "discard";
            }));
            assertTrue(realParticipation.get(), "必须到达真实会话执行后的 factory 替换路径");
            fixture.assertRolledBack();
            verify(fixture.erpJdbc.statement, times(1)).executeBatch();
            assertTrue(fixture.erpJdbc.closed.get(), "自有物理连接必须实际关闭，而非仅调用 close");
            fixture.assertOnlyErpBorrowed(1);
            fixture.assertNoIdentityGuessing();
            verify(foreignSession, never()).getConnection();
            verify(foreignSession, never()).close();
            assertBefore(fixture.events, "erp.rollback", "completion.1");
            assertBefore(fixture.events, "completion.1", "erp.close");
            assertOnlyCallerResource(fixture.sessionFactory, foreign);
            assertSame(foreign, TransactionSynchronizationManager.getResource(fixture.sessionFactory),
                "原 MyBatis beforeCompletion/afterCompletion 不能删除外来 factory holder");
        } finally {
            releaseCallerResource(fixture.sessionFactory, foreign);
        }
    }

    @Test
    void jdbcBorrowFailureCannotLeakDiagnosticsOrInvokeCallback() throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        doThrow(new SQLException(PRIVATE_DIAGNOSTIC)).when(fixture.erp).getConnection();
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertNoDiagnosticLeak(fixture.assertRejectedBeforeCallback(entry, TENANT));
        }
        verify(fixture.erp, times(1)).getConnection();
        verify(fixture.master, never()).getConnection();
        verify(fixture.erpJdbc.connection, never()).commit();
    }

    @Test
    void realMybatisFlushFailureRollsBackWithoutLeakingJdbcDiagnostics() throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        fixture.erpJdbc.flushFailure = new SQLException(PRIVATE_DIAGNOSTIC);
        fixture.checkOwnedAliasesAtJdbcCompletion = true;
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertNoDiagnosticLeak(assertThrows(RuntimeException.class, () -> entry.execute(TENANT, () -> {
                fixture.stageBatch();
                return "discard";
            })));
        }
        fixture.assertRolledBack();
        verify(fixture.erpJdbc.statement, times(1)).executeBatch();
        fixture.assertOnlyErpBorrowed(1);
    }

    @Test
    void jdbcCommitFailureCannotReturnCallbackValueAndCleansResourcesWithoutLeakingDiagnostics()
        throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        fixture.erpJdbc.commitFailure = new SQLException(PRIVATE_DIAGNOSTIC);
        fixture.checkOwnedAliasesAtJdbcCompletion = true;
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            assertNoDiagnosticLeak(assertThrows(RuntimeException.class, () -> entry.execute(TENANT, () -> {
                fixture.stageBatch();
                return "discard";
            })));
        }
        verify(fixture.erpJdbc.connection, times(1)).commit();
        verify(fixture.erpJdbc.connection, times(1)).close();
        fixture.assertOnlyErpBorrowed(1);
    }

    /** 合成语句只进入 Mockito JDBC，用于观察真实批处理，绝不送入数据库。 */
    public interface OfflineBatchMapper {
        @Update("UPDATE offline_p1_transaction_probe SET amount = amount")
        int stage();
    }

    /** 非 DelegatingDataSource 的未知包装；不能通过只追加该类黑名单掩盖同类问题。 */
    private static final class UnreviewedDataSourceWrapper extends AbstractDataSource {
        private final DataSource target;

        private UnreviewedDataSourceWrapper(DataSource target) {
            this.target = target;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return target.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return target.getConnection(username, password);
        }
    }

    /** 声称 real=ERP 但覆写借连接为 master 的 P6 子类，不得凭 instanceof 或包名放行。 */
    private static final class UnreviewedP6DataSource extends P6DataSource {
        private final DataSource forwarded;

        private UnreviewedP6DataSource(DataSource declared, DataSource forwarded) {
            super(declared);
            this.forwarded = forwarded;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return forwarded.getConnection();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return forwarded.getConnection(username, password);
        }
    }

    private enum InvalidP6Registration {
        UNKNOWN_WRAPPER, P6_SUBCLASS, UNKNOWN_P6_INNER, WRONG_INNER_POOL, SEATA, SHARED_ERP_POOL
    }

    private enum ExistingResource { MASTER, ERP, ROUTER, SQL_SESSION, UNKNOWN }

    private enum ExternalErpTransaction { PHYSICAL_ONLY, ROUTER_ONLY, EXTERNALLY_ALIASED }

    private enum ConfigurationDrift { PHYSICAL, REGISTRATION, ENVIRONMENT }

    private enum InvalidTopology {
        MISSING_ERP, ERP_GROUP, SHARED_PHYSICAL, ROUTER_AS_ERP, LAZY_ERP, TRANSACTION_AWARE_ERP,
        SEATA_ITEM, NO_ENVIRONMENT, NON_SPRING_TRANSACTION_FACTORY, OTHER_MYBATIS_DATASOURCE,
        MISSING_GUARD_MAPPER, MISSING_POOL_MAPPER
    }

    /** RED 缺类必须是断言失败；不让负例把缺类当作预期业务拒绝而通过。 */
    private static Class<?> executorType() {
        return assertDoesNotThrow(() -> Class.forName(EXECUTOR), "尚缺已批准的内部 ERP 物理事务入口：" + EXECUTOR);
    }

    /** 只反射已批准的小接口，不依赖缺失的生产类或测试专用生产后门。 */
    private record Entry(Object bean, Method method) {
        @SuppressWarnings("unchecked")
        <T> T execute(String tenantId, Supplier<T> body) {
            try {
                return (T) method.invoke(bean, tenantId, body);
            } catch (InvocationTargetException wrapper) {
                Throwable failure = wrapper.getCause();
                if (failure instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (failure instanceof Error error) {
                    throw error;
                }
                throw new AssertionError("入口不得泄漏未声明的受检异常", failure);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("无法调用 execute(String, Supplier) 接口", failure);
            }
        }
    }

    /**
     * 只建立内存注册表与非联网 JDBC。正例事务和 router 别名始终由待测入口建立。
     * 两个工厂 Bean 故意都不设 Primary，校验入口实际使用 sqlSessionFactory 命名配置。
     */
    private static final class Fixture {
        private final List<String> events = new ArrayList<>();
        private final List<String> readerTrace = new ArrayList<>();
        private final List<JdbcEndpoint> endpoints = new ArrayList<>();
        private final Deque<JdbcEndpoint> availableErpConnections = new ArrayDeque<>();
        private final JdbcEndpoint erpJdbc;
        private final JdbcEndpoint masterJdbc;
        private final DataSource erp = mock(HikariDataSource.class);
        private final DataSource master = mock(HikariDataSource.class);
        private final DynamicRoutingDataSource routing = new DynamicRoutingDataSource(List.of());
        private final Configuration configuration;
        private final SqlSessionFactory sessionFactory;
        private final SqlSessionTemplate template;
        private final PuRequirementErpTransactionQualification qualification;
        private final PuRequirementMaterialLockService guard = mock(PuRequirementMaterialLockService.class);
        private final PuRequirementCompetitionPoolMapper mapper;
        private boolean checkOwnedAliasesAtJdbcCompletion;

        Fixture() throws SQLException {
            this(null);
        }

        Fixture(InvalidTopology invalid) throws SQLException {
            erpJdbc = newEndpoint("erp");
            masterJdbc = newEndpoint("master");
            availableErpConnections.addLast(erpJdbc);
            when(erp.getConnection()).thenAnswer(ignored -> {
                events.add("erp.borrow");
                JdbcEndpoint borrowed = availableErpConnections.pollFirst();
                if (borrowed == null) {
                    throw new SQLException("离线夹具禁止额外借连接");
                }
                return borrowed.connection;
            });
            when(master.getConnection()).thenAnswer(ignored -> {
                events.add("master.borrow");
                return masterJdbc.connection;
            });
            routing.setPrimary("master");
            routing.setStrict(true);
            routing.addDataSource("master", master);
            routing.addDataSource("erp", erp);
            configuration = new Configuration(new Environment("offline", new SpringManagedTransactionFactory(), routing));
            if (invalid != InvalidTopology.MISSING_POOL_MAPPER) {
                configuration.addMapper(PuRequirementCompetitionPoolMapper.class);
            }
            if (invalid != InvalidTopology.MISSING_GUARD_MAPPER) {
                configuration.addMapper(PuRequirementMaterialGuardMapper.class);
            }
            configuration.addMapper(OfflineBatchMapper.class);
            sessionFactory = new DefaultSqlSessionFactory(configuration);
            template = new SqlSessionTemplate(sessionFactory, ExecutorType.BATCH);
            qualification = new PuRequirementErpTransactionQualification(sessionFactory);
            mapper = mock(PuRequirementCompetitionPoolMapper.class, invocation -> {
                String name = invocation.getMethod().getName();
                if (!name.startsWith("lock")) {
                    return Answers.RETURNS_DEFAULTS.answer(invocation);
                }
                assertEquals("erp", DynamicDataSourceContextHolder.peek(), "必须经过实际 @DS 代理");
                readerTrace.add(name);
                return List.of();
            });
            doAnswer(invocation -> {
                assertEquals("erp", DynamicDataSourceContextHolder.peek(), "guard 必须经过实际 @DS 代理");
                readerTrace.add("guard");
                return Map.of(MATERIAL, 0);
            }).when(guard).lock(anyString(), anyCollection());
            if (invalid != null) {
                invalidate(invalid);
            }
        }

        private void invalidate(InvalidTopology invalid) {
            switch (invalid) {
                case MISSING_ERP -> routing.getDataSources().remove("erp");
                case ERP_GROUP -> routing.addDataSource("erp_replica", mock(DataSource.class));
                case SHARED_PHYSICAL -> routing.getDataSources().put("master", erp);
                case ROUTER_AS_ERP -> routing.getDataSources().put("erp", routing);
                case LAZY_ERP -> routing.getDataSources().put("erp", new LazyConnectionDataSourceProxy(erp));
                case TRANSACTION_AWARE_ERP -> routing.getDataSources().put("erp", new TransactionAwareDataSourceProxy(erp));
                case SEATA_ITEM -> routing.getDataSources().put("erp",
                    new ItemDataSource("erp", erp, erp, false, true, SeataMode.AT));
                case NO_ENVIRONMENT -> configuration.setEnvironment(null);
                case NON_SPRING_TRANSACTION_FACTORY -> configuration.setEnvironment(
                    new Environment("offline", new JdbcTransactionFactory(), routing));
                case OTHER_MYBATIS_DATASOURCE -> configuration.setEnvironment(
                    new Environment("offline", new SpringManagedTransactionFactory(), master));
                case MISSING_GUARD_MAPPER, MISSING_POOL_MAPPER -> { }
            }
        }

        Entry entry() {
            Class<?> type = executorType();
            assertDoesNotThrow(() -> type.getConstructor(SqlSessionFactory.class), "约定 constructor(SqlSessionFactory)");
            Method execute = assertDoesNotThrow(() -> type.getMethod("execute", String.class, Supplier.class),
                "约定 execute(String tenantId, Supplier<T>)，不接受 scope 布尔或外部事务");
            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.registerBean("sqlSessionFactory", SqlSessionFactory.class, () -> sessionFactory);
                context.registerBean("unrelatedFactory", SqlSessionFactory.class, () -> new DefaultSqlSessionFactory(
                    new Configuration(new Environment("unrelated", new JdbcTransactionFactory(), master))));
                context.register(PuRequirementErpTransactionQualification.class, type);
                context.refresh();
                assertEquals(0, context.getBeansOfType(PlatformTransactionManager.class).size(),
                    "不能引入或替换全局 TM Bean");
                return new Entry(context.getBean(type), execute);
            }
        }

        JdbcEndpoint newEndpoint(String name) throws SQLException {
            JdbcEndpoint endpoint = new JdbcEndpoint(this, name);
            endpoints.add(endpoint);
            return endpoint;
        }

        void stageBatch() {
            template.getMapper(OfflineBatchMapper.class).stage();
            assertTrue(sessionHolder().isSynchronizedWithTransaction());
            assertSame(holder(erp).getConnection(), template.getConnection(), "真实 MyBatis 只复用入口已借物理连接");
        }

        SqlSessionHolder sessionHolder() {
            Object resource = TransactionSynchronizationManager.getResource(sessionFactory);
            assertTrue(resource instanceof SqlSessionHolder);
            return (SqlSessionHolder) resource;
        }

        ConnectionHolder holder(DataSource source) {
            Object resource = TransactionSynchronizationManager.getResource(source);
            assertTrue(resource instanceof ConnectionHolder, "由实际事务管理器建立连接持有者");
            return (ConnectionHolder) resource;
        }

        void assertOwnedWritableTransaction() {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertNull(TransactionContext.getXID());
            ConnectionHolder physical = holder(erp);
            assertSame(physical, holder(routing));
            assertTrue(physical.isSynchronizedWithTransaction());
            assertFalse(physical.isRollbackOnly());
            assertEquals(2, TransactionSynchronizationManager.getResourceMap().size());
            assertFalse(assertDoesNotThrow(() -> physical.getConnection().getAutoCommit()));
            assertFalse(assertDoesNotThrow(() -> physical.getConnection().isReadOnly()));
        }

        PuRequirementCompetitionPoolReader proxiedReader() {
            PuRequirementCompetitionPoolReader reader = new PuRequirementCompetitionPoolReader(mapper, guard, qualification);
            TransactionInterceptor transactions = new TransactionInterceptor();
            transactions.setTransactionManager(new DataSourceTransactionManager(routing));
            transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
            ProxyFactory proxy = new ProxyFactory(reader);
            proxy.setProxyTargetClass(true);
            proxy.addAdvisor(new DynamicDataSourceAnnotationAdvisor(
                new DynamicDataSourceAnnotationInterceptor(true, new DsSpelExpressionProcessor()), DS.class));
            proxy.addAdvice(transactions);
            return (PuRequirementCompetitionPoolReader) proxy.getProxy();
        }

        RuntimeException assertRejectedBeforeCallback(Entry entry, String requested) {
            AtomicBoolean called = new AtomicBoolean();
            RuntimeException failure = assertThrows(RuntimeException.class, () -> entry.execute(requested, () -> {
                called.set(true);
                return "must-not-run";
            }));
            assertFalse(called.get(), "拒绝必须先于 callback 内可能存在的业务写入");
            verifyNoInteractions(guard, mapper);
            return failure;
        }

        void assertRejectedBeforeBorrow(Entry entry, String requested) throws SQLException {
            assertTrue(assertRejectedBeforeCallback(entry, requested) instanceof ServiceException,
                "入口前置拒绝应为固定业务错误");
            assertNoBorrow();
        }

        void assertNoBorrow() throws SQLException {
            verify(erp, never()).getConnection();
            verify(master, never()).getConnection();
            assertFalse(events.contains("erp.prepare"));
            assertFalse(events.contains("master.prepare"));
        }

        void assertOnlyErpBorrowed(int count) throws SQLException {
            verify(erp, times(count)).getConnection();
            verify(master, never()).getConnection();
        }

        void assertRolledBack() throws SQLException {
            verify(erpJdbc.connection, never()).commit();
            verify(erpJdbc.connection, times(1)).rollback();
            verify(erpJdbc.connection, times(1)).close();
        }

        void assertNoIdentityGuessing() throws SQLException {
            for (JdbcEndpoint endpoint : endpoints) {
                verify(endpoint.connection, never()).getCatalog();
                verify(endpoint.connection, never()).getMetaData();
            }
        }

        void observeCompletion() {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    events.add("completion." + status);
                }
            });
        }

        void atJdbcCompletion(Connection connection) {
            if (checkOwnedAliasesAtJdbcCompletion) {
                assertSame(connection, holder(erp).getConnection());
                assertSame(holder(erp), holder(routing), "路由别名保留到实际 JDBC commit/rollback 完成");
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            }
        }

        void atJdbcClose() {
            if (checkOwnedAliasesAtJdbcCompletion) {
                assertNull(TransactionSynchronizationManager.getResource(routing), "实际连接关闭前解绑自有路由别名");
                assertNull(TransactionSynchronizationManager.getResource(erp));
                assertNull(TransactionSynchronizationManager.getResource(sessionFactory));
            }
        }
    }

    /** 模拟 JDBC 的可变状态与批语句，不模拟 Spring 或 MyBatis 事务实现。 */
    private static final class JdbcEndpoint {
        private final Connection connection = mock(Connection.class);
        private final PreparedStatement statement = mock(PreparedStatement.class);
        private final AtomicBoolean autoCommit = new AtomicBoolean(true);
        private final AtomicBoolean readOnly = new AtomicBoolean(false);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final AtomicInteger pending = new AtomicInteger();
        private SQLException flushFailure;
        private SQLException commitFailure;

        JdbcEndpoint(Fixture fixture, String name) throws SQLException {
            when(connection.getAutoCommit()).thenAnswer(ignored -> autoCommit.get());
            when(connection.isReadOnly()).thenAnswer(ignored -> readOnly.get());
            when(connection.isClosed()).thenAnswer(ignored -> closed.get());
            doAnswer(invocation -> {
                autoCommit.set(invocation.getArgument(0));
                return null;
            }).when(connection).setAutoCommit(anyBoolean());
            doAnswer(invocation -> {
                readOnly.set(invocation.getArgument(0));
                return null;
            }).when(connection).setReadOnly(anyBoolean());
            when(connection.prepareStatement(anyString())).thenAnswer(ignored -> {
                fixture.events.add(name + ".prepare");
                return statement;
            });
            when(statement.getConnection()).thenReturn(connection);
            doAnswer(ignored -> {
                fixture.events.add(name + ".addBatch");
                pending.incrementAndGet();
                return null;
            }).when(statement).addBatch();
            when(statement.executeBatch()).thenAnswer(ignored -> {
                fixture.events.add(name + ".flush");
                if (flushFailure != null) {
                    throw flushFailure;
                }
                int[] counts = new int[pending.getAndSet(0)];
                Arrays.fill(counts, 1);
                return counts;
            });
            doAnswer(ignored -> {
                fixture.atJdbcCompletion(connection);
                fixture.events.add(name + ".commit");
                if (commitFailure != null) {
                    throw commitFailure;
                }
                return null;
            }).when(connection).commit();
            doAnswer(ignored -> {
                fixture.atJdbcCompletion(connection);
                fixture.events.add(name + ".rollback");
                return null;
            }).when(connection).rollback();
            doAnswer(ignored -> {
                fixture.atJdbcClose();
                fixture.events.add(name + ".close");
                closed.set(true);
                return null;
            }).when(connection).close();
        }
    }

    /**
     * 先独立核对回滚、解绑及下一次提交，最后核对致命错误对象恒等传播。
     * 用 assertThrows(Throwable.class) 安全捕获模拟对象，以免当前包装行为提前中断生命周期
     * 断言；最终 assertSame 严格拒绝 ServiceException 或任何替代/包装对象，不弱化传播要求。
     */
    private static void assertFatalRollbackAndReuse(Error fatal) throws SQLException {
        Fixture fixture = new Fixture();
        Entry entry = fixture.entry();
        JdbcEndpoint next = fixture.newEndpoint("erp-after-fatal");
        fixture.availableErpConnections.addLast(next);
        fixture.checkOwnedAliasesAtJdbcCompletion = true;
        AtomicBoolean callbackReachedFatal = new AtomicBoolean();
        Throwable propagated;
        try (MockedStatic<TenantHelper> ignored = tenant(true, TENANT)) {
            propagated = assertThrows(Throwable.class, () -> entry.execute(TENANT, () -> {
                fixture.stageBatch();
                fixture.observeCompletion();
                callbackReachedFatal.set(true);
                throw fatal;
            }));
            assertTrue(callbackReachedFatal.get(), "必须实际到达模拟致命错误，而不是工厂或夹具失败");
            fixture.assertRolledBack();
            verify(fixture.erpJdbc.statement, never()).executeBatch();
            fixture.assertOnlyErpBorrowed(1);
            assertBefore(fixture.events, "erp.rollback", "completion.1");
            assertBefore(fixture.events, "completion.1", "erp.close");
            assertCleanThread();

            assertEquals("after-fatal", entry.execute(TENANT, () -> {
                fixture.assertOwnedWritableTransaction();
                assertSame(next.connection, fixture.holder(fixture.erp).getConnection());
                fixture.stageBatch();
                assertSame(next.connection, fixture.template.getConnection());
                return "after-fatal";
            }));
            assertCleanThread();
        }
        fixture.assertRolledBack();
        verify(next.statement, times(1)).executeBatch();
        verify(next.connection, times(1)).commit();
        verify(next.connection, never()).rollback();
        verify(next.connection, times(1)).close();
        fixture.assertOnlyErpBorrowed(2);
        fixture.assertNoIdentityGuessing();
        assertBefore(fixture.events, "erp.close", "erp-after-fatal.prepare");
        assertBefore(fixture.events, "erp-after-fatal.flush", "erp-after-fatal.commit");
        assertBefore(fixture.events, "erp-after-fatal.commit", "erp-after-fatal.close");
        assertSame(fatal, propagated, "致命 Error 必须在 rollback/cleanup 后原对象传播，不能包装为业务异常");
    }

    private static MockedStatic<TenantHelper> tenant(boolean enabled, String current) {
        MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::isEnable).thenReturn(enabled);
        tenant.when(TenantHelper::getTenantId).thenReturn(current);
        return tenant;
    }

    private static void assertBefore(List<String> trace, String first, String second) {
        int firstIndex = trace.indexOf(first);
        int secondIndex = trace.indexOf(second);
        assertTrue(firstIndex >= 0 && secondIndex > firstIndex, "生命周期顺序必须为 " + first + " → " + second + "：" + trace);
    }

    private static void assertOnlyCallerResource(Object key, Object value) {
        assertEquals(Map.of(key, value), TransactionSynchronizationManager.getResourceMap(),
            "只清理入口拥有的资源，不覆盖或移除未知调用方资源");
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
    }

    /** 调用方夹具自行移除自己的反例资源；不清空线程，也不掩盖入口误删资源的前置断言。 */
    private static void releaseCallerResource(Object key, Object expected) {
        if (TransactionSynchronizationManager.hasResource(key)) {
            assertSame(expected, TransactionSynchronizationManager.unbindResource(key));
        }
    }

    private static void assertNoDiagnosticLeak(Throwable failure) {
        StringWriter rendered = new StringWriter();
        failure.printStackTrace(new PrintWriter(rendered));
        assertFalse(rendered.toString().contains(PRIVATE_DIAGNOSTIC),
            "对外异常、cause 或 suppressed 中不能泄漏底层 SQL/凭证哨兵");
    }

    private static void assertCleanThread() {
        assertAll("用例必须独立且不泄漏线程资源；不强制 clear 来掩盖入口清理缺陷",
            () -> assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty()),
            () -> assertFalse(TransactionSynchronizationManager.isActualTransactionActive()),
            () -> assertFalse(TransactionSynchronizationManager.isSynchronizationActive()),
            () -> assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly()),
            () -> assertNull(DynamicDataSourceContextHolder.peek()),
            () -> assertNull(TransactionContext.getXID()));
    }
}
