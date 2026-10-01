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
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper;
import com.nexus.erp.pu.mapper.PuRequirementMaterialGuardMapper;
import com.nexus.erp.pu.service.PuRequirementCompetitionPoolReader;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.pu.service.PuRequirementMaterialLockService;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mybatis.spring.SqlSessionHolder;
import org.mybatis.spring.transaction.SpringManagedTransaction;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 直接针对真实 reader 及真实事务资格校验的离线回归，不模拟或绕过生产资格判断。
 * 两个物理数据源与 JDBC 连接仅为非联网模拟；事务开启、连接持有者绑定与清理由真实
 * Spring 本地事务管理器执行，测试事务最终回滚。代理测试使用真实动态数据源切面及 MANDATORY 事务切面。
 * 正例显式将真实物理 ERP 事务的同一个持有者映射到 MyBatis 路由资源键，模拟已证明
 * 同连接的入口；这不是现有应用已具备该入口的证据，也不在生产代码中补绑或借新连接。
 * 所有拒绝断言均调用真实 reader，并独立检查 guard 与 mapper 没有被调用。
 */
@Tag("dev")
class PuRequirementErpTransactionQualificationTest {

    private static final String TENANT = "t-100";
    private static final Long MATERIAL = 139L;
    private static final LocalDate WINDOW_END = LocalDate.of(2026, 9, 30);

    @AfterEach
    void transactionAndRoutingResourcesMustNotLeak() {
        assertAll("离线用例不得泄漏事务或路由上下文",
            () -> assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty()),
            () -> assertFalse(TransactionSynchronizationManager.isActualTransactionActive()),
            () -> assertFalse(TransactionSynchronizationManager.isSynchronizationActive()),
            () -> assertNull(DynamicDataSourceContextHolder.peek()),
            () -> assertNull(TransactionContext.getXID()));
    }

    @Test
    void masterAlreadyBoundIsRejectedThroughActualErpProxyBeforeGuardOrMapper() throws SQLException {
        Fixture fixture = new Fixture();
        fixture.inRouterTransaction("master", () -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertSame(fixture.masterConnection, fixture.holder(fixture.routing).getConnection());
            assertNull(TransactionSynchronizationManager.getResource(fixture.erp));
            fixture.assertDeniedBeforeDependencies(fixture.proxiedReader());
            assertSame(fixture.masterConnection, fixture.holder(fixture.routing).getConnection(),
                "进入 ERP 代理后，原 master 连接不应被替换或另开事务");
            return null;
        });
        verify(fixture.master, times(1)).getConnection();
        verify(fixture.erp, never()).getConnection();
        fixture.assertNoDatabaseIdentityGuessing();
    }

    @Test
    void actualFlagAndErpRouteWithoutAnyBoundConnectionAreRejectedBeforeDependencies() throws SQLException {
        Fixture fixture = new Fixture();
        /** 仅在反例伪造旧检查所信任的标记，正例从不以该标记自证事务。 */
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        DynamicDataSourceContextHolder.push("erp");
        try (MockedStatic<TenantHelper> ignored = Fixture.tenant()) {
            assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
            fixture.assertDeniedBeforeDependencies(fixture.reader());
            assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty(),
                "资格检查不得通过新取连接或补绑资源把未绑定反例变成合格事务");
        } finally {
            DynamicDataSourceContextHolder.poll();
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verify(fixture.master, never()).getConnection();
        verify(fixture.erp, never()).getConnection();
    }

    @Test
    void routerOnlyErpTransactionWithoutPhysicalResourceProofIsRejectedBeforeDependencies() throws SQLException {
        Fixture fixture = new Fixture();
        fixture.inRouterTransaction("erp", () -> {
            assertSame(fixture.erpConnection, fixture.holder(fixture.routing).getConnection());
            assertNull(TransactionSynchronizationManager.getResource(fixture.erp));
            fixture.assertDeniedBeforeDependencies(fixture.proxiedReader());
            return null;
        });
        verify(fixture.erp, times(1)).getConnection();
        fixture.assertNoDatabaseIdentityGuessing();
    }

    @Test
    void physicalErpTransactionWithoutMybatisResourceBindingIsRejectedBeforeDependencies() throws SQLException {
        Fixture fixture = new Fixture();
        fixture.inPhysicalErpTransaction(false, () -> {
            assertTrue(fixture.holder(fixture.erp).isSynchronizedWithTransaction());
            assertNull(TransactionSynchronizationManager.getResource(fixture.routing));
            fixture.assertDeniedBeforeDependencies(fixture.reader());
            assertNull(TransactionSynchronizationManager.getResource(fixture.routing),
                "资格校验不能替调用方创建 MyBatis 连接绑定");
            return null;
        });
        verify(fixture.erp, times(1)).getConnection();
    }

    @Test
    void readOnlyErpTransactionIsRejectedBeforeGuardOrMapper() throws SQLException {
        Fixture fixture = new Fixture();
        fixture.inBoundErpTransaction(true, () -> {
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            fixture.assertDeniedBeforeDependencies(fixture.proxiedReader());
            return null;
        });
    }

    @Test
    void driverReadOnlyConnectionIsRejectedEvenWhenSpringTransactionIsWritable() throws SQLException {
        Fixture fixture = new Fixture();
        fixture.inBoundErpTransaction(false, () -> {
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertDoesNotThrow(() -> fixture.erpConnection.setReadOnly(true));
            fixture.assertDeniedBeforeDependencies(fixture.proxiedReader());
            return null;
        });
    }

    @Test
    void autoCommitConnectionIsRejectedDespiteActualTransactionFlagAndBindings() throws SQLException {
        Fixture fixture = new Fixture();
        fixture.inBoundErpTransaction(false, () -> {
            assertDoesNotThrow(() -> fixture.erpConnection.setAutoCommit(true));
            try {
                fixture.assertDeniedBeforeDependencies(fixture.proxiedReader());
            } finally {
                assertDoesNotThrow(() -> fixture.erpConnection.setAutoCommit(false));
            }
            return null;
        });
    }

    @Test
    void closedConnectionIsRejectedBeforeGuardOrMapper() throws SQLException {
        Fixture fixture = new Fixture();
        fixture.inBoundErpTransaction(false, () -> {
            assertDoesNotThrow(() -> when(fixture.erpConnection.isClosed()).thenReturn(true));
            fixture.assertDeniedBeforeDependencies(fixture.proxiedReader());
            return null;
        });
    }

    @Test
    void unknownConnectionStateFailsClosedBeforeGuardOrMapper() throws SQLException {
        Fixture fixture = new Fixture();
        fixture.inBoundErpTransaction(false, () -> {
            assertDoesNotThrow(() -> when(fixture.erpConnection.getAutoCommit())
                .thenThrow(new SQLException("离线模拟连接状态不可核验")));
            try {
                fixture.assertDeniedBeforeDependencies(fixture.proxiedReader());
            } finally {
                assertDoesNotThrow(() -> doReturn(false).when(fixture.erpConnection).getAutoCommit());
            }
            return null;
        });
    }

    @Test
    void unsynchronizedHolderAliasDoesNotProveMybatisJoinedTheErpTransaction() throws SQLException {
        Fixture fixture = new Fixture();
        fixture.inBoundErpTransaction(false, () -> {
            TransactionSynchronizationManager.unbindResource(fixture.routing);
            ConnectionHolder unqualified = new ConnectionHolder(fixture.erpConnection);
            TransactionSynchronizationManager.bindResource(fixture.routing, unqualified);
            assertFalse(unqualified.isSynchronizedWithTransaction());
            fixture.assertDeniedBeforeDependencies(fixture.reader());
            return null;
        });
    }

    @Test
    void separateMasterAndErpLocalTransactionsAreNotAcceptedAsOneErpTransaction() throws SQLException {
        Fixture fixture = new Fixture();
        new TransactionTemplate(new DataSourceTransactionManager(fixture.master)).execute(status ->
            fixture.inBoundErpTransaction(false, () -> {
                assertTrue(fixture.holder(fixture.master).isSynchronizedWithTransaction());
                assertTrue(fixture.holder(fixture.erp).isSynchronizedWithTransaction());
                assertSame(fixture.masterConnection, fixture.holder(fixture.master).getConnection());
                assertSame(fixture.erpConnection, fixture.holder(fixture.routing).getConnection());
                fixture.assertDeniedBeforeDependencies(fixture.proxiedReader());
                return null;
            }));
        verify(fixture.master, times(1)).getConnection();
        verify(fixture.erp, times(1)).getConnection();
    }

    @Test
    void dynamicMultiDatasourceTransactionContextIsRejectedBeforeDependencies() throws SQLException {
        Fixture fixture = new Fixture();
        fixture.inBoundErpTransaction(false, () -> {
            TransactionContext.bind("offline-multi-datasource-transaction");
            try {
                fixture.assertDeniedBeforeDependencies(fixture.proxiedReader());
            } finally {
                TransactionContext.remove();
            }
            return null;
        });
    }

    @Test
    void missingExplicitErpRegistrationCannotUsePrimaryFallbackAsProof() throws SQLException {
        Fixture fixture = new Fixture();
        fixture.routing.getDataSources().remove("erp");
        fixture.routing.setStrict(false);
        fixture.inBoundErpTransaction(false, () -> {
            assertSame(fixture.master, fixture.routing.getDataSource("erp"),
                "非严格路由的回退结果不是显式 ERP 数据源身份");
            fixture.assertDeniedBeforeDependencies(fixture.reader());
            return null;
        });
        verify(fixture.master, never()).getConnection();
        verify(fixture.erp, times(1)).getConnection();
    }

    @Test
    void nonSpringManagedMybatisTransactionsAreRejectedBeforeDependencies() throws SQLException {
        Fixture fixture = new Fixture();
        fixture.configuration.setEnvironment(new Environment("offline-jdbc", new JdbcTransactionFactory(), fixture.routing));
        fixture.inBoundErpTransaction(false, () -> {
            fixture.assertDeniedBeforeDependencies(fixture.reader());
            return null;
        });
    }

    @Test
    void differentMybatisDatasourceIsRejectedDespiteErpAndRoutingBindings() throws SQLException {
        Fixture fixture = new Fixture();
        fixture.configuration.setEnvironment(new Environment("offline-master", new SpringManagedTransactionFactory(), fixture.master));
        fixture.inBoundErpTransaction(false, () -> {
            fixture.assertDeniedBeforeDependencies(fixture.reader());
            return null;
        });
        verify(fixture.master, never()).getConnection();
    }

    @Test
    void provenSamePhysicalErpAndMybatisConnectionKeepsGuardBeforeSourceReads() throws SQLException {
        Fixture fixture = new Fixture();
        fixture.inBoundErpTransaction(false, () -> {
            assertSame(fixture.holder(fixture.erp), fixture.holder(fixture.routing));
            SpringManagedTransaction mybatis = new SpringManagedTransaction(
                fixture.sessionFactory.getConfiguration().getEnvironment().getDataSource());
            try {
                assertSame(fixture.erpConnection, assertDoesNotThrow(mybatis::getConnection));
                assertNotNull(fixture.proxiedReader().materialPool(TENANT, MATERIAL, WINDOW_END));
            } finally {
                assertDoesNotThrow(mybatis::close);
            }
            assertEquals(List.of("guard", "lockRequirementDetails", "lockStocks", "lockOrderLines",
                "lockRequisitionLines", "lockAllocations"), fixture.trace);
            assertSame(fixture.erpConnection, fixture.holder(fixture.routing).getConnection());
            return null;
        });
        verify(fixture.erp, times(1)).getConnection();
        verify(fixture.master, never()).getConnection();
        fixture.assertNoDatabaseIdentityGuessing();
    }

    @Test
    void registeredItemDatasourceCanUseItsActuallyBoundPhysicalErpConnection() throws SQLException {
        Fixture fixture = new Fixture();
        fixture.routing.getDataSources().put("erp",
            new ItemDataSource("erp", fixture.erp, fixture.erp, false, false, SeataMode.AT));
        fixture.inBoundErpTransaction(false, () -> {
            assertNotNull(fixture.proxiedReader().materialPool(TENANT, MATERIAL, WINDOW_END));
            assertEquals("guard", fixture.trace.get(0));
            return null;
        });
        verify(fixture.erp, times(1)).getConnection();
        verify(fixture.master, never()).getConnection();
    }

    @Test
    void unknownSynchronizationResourceIsRejectedBeforeGuardOrMapper() throws SQLException {
        Fixture fixture = new Fixture();
        Object unknownKey = new Object();
        Object unknownValue = new Object();
        fixture.inBoundErpTransaction(false, () -> {
            /** 先证已有 ERP 夹具合格；新增普通资源是此反例唯一改变的资格条件。 */
            assertDoesNotThrow(() -> new PuRequirementErpTransactionQualification(fixture.sessionFactory)
                .requireWritableErpTransaction());
            TransactionSynchronizationManager.bindResource(unknownKey, unknownValue);
            try {
                assertSame(unknownValue, TransactionSynchronizationManager.getResource(unknownKey));
                fixture.assertDeniedBeforeDependencies(fixture.proxiedReader());
            } finally {
                assertSame(unknownValue, TransactionSynchronizationManager.unbindResource(unknownKey));
            }
            return null;
        });
        verify(fixture.erp, times(1)).getConnection();
        verify(fixture.master, never()).getConnection();
    }
    @Test
    void preboundMybatisSessionIsRejectedWithoutBorrowingItsConnection() throws SQLException {
        Fixture fixture = new Fixture();
        SqlSession session = mock(SqlSession.class);
        fixture.inBoundErpTransaction(false, () -> {
            SqlSessionHolder holder = new SqlSessionHolder(session, ExecutorType.SIMPLE, null);
            holder.setSynchronizedWithTransaction(true);
            TransactionSynchronizationManager.bindResource(fixture.sessionFactory, holder);
            try {
                fixture.assertDeniedBeforeDependencies(fixture.reader());
                verifyNoInteractions(session);
            } finally {
                TransactionSynchronizationManager.unbindResource(fixture.sessionFactory);
            }
            return null;
        });
        verify(fixture.erp, times(1)).getConnection();
        verify(fixture.master, never()).getConnection();
    }

    @Test
    void missingActualMapperRegistrationsRejectsBeforeDependencies() throws SQLException {
        Fixture fixture = new Fixture();
        SqlSessionFactory unmapped = new DefaultSqlSessionFactory(new Configuration(
            new Environment("offline-unmapped", new SpringManagedTransactionFactory(), fixture.routing)));
        fixture.inBoundErpTransaction(false, () -> {
            fixture.assertDeniedBeforeDependencies(new PuRequirementCompetitionPoolReader(fixture.mapper, fixture.guard,
                new PuRequirementErpTransactionQualification(unmapped)));
            return null;
        });
    }

    @Test
    void springCanAssembleReaderUsingOnlyTheExistingNamedSqlSessionFactory() throws SQLException {
        Fixture fixture = new Fixture();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("sqlSessionFactory", SqlSessionFactory.class, () -> fixture.sessionFactory);
            context.registerBean(PuRequirementCompetitionPoolMapper.class, () -> fixture.mapper);
            context.registerBean(PuRequirementMaterialLockService.class, () -> fixture.guard);
            context.register(PuRequirementErpTransactionQualification.class, PuRequirementCompetitionPoolReader.class);
            context.refresh();
            assertNotNull(context.getBean(PuRequirementErpTransactionQualification.class));
            PuRequirementCompetitionPoolReader reader = context.getBean(PuRequirementCompetitionPoolReader.class);
            fixture.inBoundErpTransaction(false, () -> {
                assertNotNull(reader.materialPool(TENANT, MATERIAL, WINDOW_END));
                assertEquals("guard", fixture.trace.get(0));
                return null;
            });
        }
    }
    /**
     * 仅为测试创建内存中的路由表、事务管理器与状态可观察的模拟连接。
     * 未启动 Spring Boot、连接池、容器、数据库或业务服务。
     */
    static final class Fixture {
        private final Connection masterConnection;
        private final Connection erpConnection;
        private final DataSource master;
        private final DataSource erp;
        private final DynamicRoutingDataSource routing = new DynamicRoutingDataSource(List.of());
        private final Configuration configuration;
        private final SqlSessionFactory sessionFactory;
        private final List<String> trace = new ArrayList<>();
        private final PuRequirementMaterialLockService guard = mock(PuRequirementMaterialLockService.class);
        private final PuRequirementCompetitionPoolMapper mapper;

        Fixture() throws SQLException {
            masterConnection = connection();
            erpConnection = connection();
            master = source(masterConnection);
            erp = source(erpConnection);
            routing.setPrimary("master");
            routing.setStrict(true);
            routing.addDataSource("master", master);
            routing.addDataSource("erp", erp);
            configuration = new Configuration(new Environment("offline", new SpringManagedTransactionFactory(), routing));
            configuration.addMapper(PuRequirementCompetitionPoolMapper.class);
            configuration.addMapper(PuRequirementMaterialGuardMapper.class);
            sessionFactory = new DefaultSqlSessionFactory(configuration);
            mapper = mock(PuRequirementCompetitionPoolMapper.class, invocation -> {
                String name = invocation.getMethod().getName();
                if (!name.startsWith("lock")) {
                    return Answers.RETURNS_DEFAULTS.answer(invocation);
                }
                trace.add(name);
                return List.of();
            });
            doAnswer(invocation -> {
                trace.add("guard");
                return Map.of(MATERIAL, 0);
            }).when(guard).lock(anyString(), anyCollection());
        }

        private PuRequirementCompetitionPoolReader reader() {
            return new PuRequirementCompetitionPoolReader(mapper, guard, new PuRequirementErpTransactionQualification(sessionFactory));
        }

        private PuRequirementCompetitionPoolReader proxiedReader() {
            TransactionInterceptor transactions = new TransactionInterceptor();
            transactions.setTransactionManager(new DataSourceTransactionManager(routing));
            transactions.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
            ProxyFactory proxy = new ProxyFactory(reader());
            proxy.setProxyTargetClass(true);
            proxy.addAdvisor(new DynamicDataSourceAnnotationAdvisor(
                new DynamicDataSourceAnnotationInterceptor(true, new DsSpelExpressionProcessor()), DS.class));
            proxy.addAdvice(transactions);
            return (PuRequirementCompetitionPoolReader) proxy.getProxy();
        }

        private <T> T inRouterTransaction(String route, Supplier<T> body) {
            DynamicDataSourceContextHolder.push(route);
            try (MockedStatic<TenantHelper> ignored = tenant()) {
                return new TransactionTemplate(new DataSourceTransactionManager(routing)).execute(status -> {
                    try {
                        return body.get();
                    } finally {
                        status.setRollbackOnly();
                    }
                });
            } finally {
                DynamicDataSourceContextHolder.poll();
            }
        }

        private <T> T inPhysicalErpTransaction(boolean readOnly, Supplier<T> body) {
            TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(erp));
            transaction.setReadOnly(readOnly);
            try (MockedStatic<TenantHelper> ignored = tenant()) {
                return transaction.execute(status -> {
                    try {
                        return body.get();
                    } finally {
                        status.setRollbackOnly();
                    }
                });
            }
        }

        /**
         * 映射真实事务管理器已经创建的同一持有者，不制造新的连接或事务活跃资格。
         * 该测试入口不是生产接线；未来调用方仍须独立提供等价的同连接绑定证据。
         */
        private <T> T inBoundErpTransaction(boolean readOnly, Supplier<T> body) {
            try (MockedStatic<TenantHelper> ignored = tenant()) {
                return inBoundErpTransactionWithoutTenant(readOnly, body);
            }
        }

        /** 共用真实事务夹具，不覆盖其他 reader 测试显式设定的租户反例。 */
        <T> T inBoundErpTransactionWithoutTenant(boolean readOnly, Supplier<T> body) {
            TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(erp));
            transaction.setReadOnly(readOnly);
            return transaction.execute(status -> {
                TransactionSynchronizationManager.bindResource(routing, holder(erp));
                try {
                    return body.get();
                } finally {
                    TransactionSynchronizationManager.unbindResource(routing);
                    status.setRollbackOnly();
                }
            });
        }

        SqlSessionFactory sqlSessionFactory() {
            return sessionFactory;
        }

        private ConnectionHolder holder(DataSource source) {
            Object resource = TransactionSynchronizationManager.getResource(source);
            assertTrue(resource instanceof ConnectionHolder, "必须是实际绑定的 JDBC 连接持有者");
            return (ConnectionHolder) resource;
        }

        private void assertDeniedBeforeDependencies(PuRequirementCompetitionPoolReader reader) {
            assertAll("不合格事务必须在任何 guard 或当前读之前失败关闭",
                () -> assertThrows(ServiceException.class, () -> reader.materialPool(TENANT, MATERIAL, WINDOW_END)),
                () -> assertEquals(List.of(), trace, "事务资格拒绝前不允许产生 guard 或 mapper 调用"),
                () -> verifyNoInteractions(guard, mapper));
        }

        private void assertNoDatabaseIdentityGuessing() throws SQLException {
            verify(masterConnection, never()).getCatalog();
            verify(erpConnection, never()).getCatalog();
            verify(masterConnection, never()).getMetaData();
            verify(erpConnection, never()).getMetaData();
        }

        private static DataSource source(Connection connection) throws SQLException {
            DataSource source = mock(DataSource.class);
            when(source.getConnection()).thenReturn(connection);
            return source;
        }

        private static Connection connection() throws SQLException {
            Connection connection = mock(Connection.class);
            AtomicBoolean autoCommit = new AtomicBoolean(true);
            AtomicBoolean readOnly = new AtomicBoolean(false);
            when(connection.getAutoCommit()).thenAnswer(ignored -> autoCommit.get());
            when(connection.isReadOnly()).thenAnswer(ignored -> readOnly.get());
            when(connection.isClosed()).thenReturn(false);
            /** 刻意让两库 catalog 相同；名称相同不能证明连接所属数据源相同。 */
            when(connection.getCatalog()).thenReturn("same-nonproof-catalog");
            doAnswer(invocation -> {
                autoCommit.set(invocation.getArgument(0));
                return null;
            }).when(connection).setAutoCommit(anyBoolean());
            doAnswer(invocation -> {
                readOnly.set(invocation.getArgument(0));
                return null;
            }).when(connection).setReadOnly(anyBoolean());
            return connection;
        }

        private static MockedStatic<TenantHelper> tenant() {
            MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class);
            tenant.when(TenantHelper::isEnable).thenReturn(true);
            tenant.when(TenantHelper::getTenantId).thenReturn(TENANT);
            return tenant;
        }

    }
}
