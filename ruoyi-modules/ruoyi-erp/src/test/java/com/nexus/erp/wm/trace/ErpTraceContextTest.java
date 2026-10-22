package com.nexus.erp.wm.trace;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.tenant.helper.TenantHelper;
import com.zaxxer.hikari.HikariDataSource;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.*;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.*;
import java.util.List;
import java.util.Map;

class ErpTraceContextTest {
    DynamicRoutingDataSource routing;
    Connection connection;
    ErpTraceContext context;

    @BeforeEach
    void setup() throws Exception {
        routing = mock(DynamicRoutingDataSource.class);
        var pool = mock(HikariDataSource.class);
        when(pool.getJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/erp?x=y");
        when(routing.getDataSources()).thenReturn(Map.of("erp", pool));
        connection = mock(Connection.class);
        var metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:mysql://127.0.0.1:3306/erp?x=y");
        when(connection.getCatalog()).thenReturn("erp");
        var configuration = new Configuration();
        configuration.setEnvironment(
                new Environment("test", new JdbcTransactionFactory(), routing));
        var factory = mock(SqlSessionFactory.class);
        when(factory.getConfiguration()).thenReturn(configuration);
        context = new ErpTraceContext(factory);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        TransactionSynchronizationManager.bindResource(routing, new ConnectionHolder(connection));
        DynamicDataSourceContextHolder.push("erp");
    }

    @AfterEach
    void clear() {
        TransactionSynchronizationManager.unbindResourceIfPossible(routing);
        TransactionSynchronizationManager.clear();
        DynamicDataSourceContextHolder.clear();
    }

    @Test
    void aRouteMarkerDoesNotAuthorizeAWrongPhysicalDatabase() throws Exception {
        when(connection.getCatalog()).thenReturn("mes");
        assertThrows(RuntimeException.class, context::requireConnection);
    }

    @Test
    void matchingPhysicalConnectionIsAcceptedWithoutBorrowingAnotherConnection() {
        assertDoesNotThrow(context::requireConnection);
    }

    @Test
    void autocommitOrReadOnlyConnectionsCannotRegisterFacts() throws Exception {
        when(connection.getAutoCommit()).thenReturn(true);
        assertThrows(RuntimeException.class, context::requireConnection);
        when(connection.getAutoCommit()).thenReturn(false);
        when(connection.isReadOnly()).thenReturn(true);
        assertThrows(RuntimeException.class, context::requireConnection);
    }

    @Test
    void aTenantWithNulPaddingCannotAliasARealTenantInLegacyBinaryJoins() {
        try (var tenants = mockStatic(TenantHelper.class)) {
            tenants.when(TenantHelper::isEnable).thenReturn(true);
            for (String tenant :
                    List.of("000001" + Character.toString(0), "T1", " 000001", "000001 ")) {
                tenants.when(TenantHelper::getTenantId).thenReturn(tenant);
                assertThrows(RuntimeException.class, context::tenant);
            }
            tenants.when(TenantHelper::getTenantId).thenReturn("000001");
            assertEquals("000001", context.tenant());
        }
    }
}
