package com.nexus.mes.pro.service;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.nexus.common.core.exception.ServiceException;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 只验证已绑定连接元信息，不连接真实数据库或借用新连接补资格。 */
class MesProgressTransactionGuardTest {
    DynamicRoutingDataSource routing;
    HikariDataSource pool;
    Connection connection;
    DatabaseMetaData metadata;
    MesProgressTransactionGuard guard;
    @BeforeEach
    void setup() throws Exception {
        routing=mock(DynamicRoutingDataSource.class);pool=mock(HikariDataSource.class);connection=mock(Connection.class);metadata=mock(DatabaseMetaData.class);
        when(routing.getDataSources()).thenReturn(Map.of("mes",pool));when(pool.getJdbcUrl()).thenReturn("jdbc:mysql://127.0.0.1:3306/ry-mes?useUnicode=true");
        when(connection.getMetaData()).thenReturn(metadata);when(metadata.getURL()).thenReturn("jdbc:mysql://127.0.0.1:3306/ry-mes");when(connection.getCatalog()).thenReturn("ry-mes");
        var config=new Configuration();config.setEnvironment(new Environment("local",new SpringManagedTransactionFactory(),routing));var factory=mock(SqlSessionFactory.class);when(factory.getConfiguration()).thenReturn(config);guard=new MesProgressTransactionGuard(factory);
        TransactionSynchronizationManager.setActualTransactionActive(true);TransactionSynchronizationManager.bindResource(routing,new ConnectionHolder(connection));
    }
    @AfterEach void cleanup(){TransactionSynchronizationManager.unbindResourceIfPossible(routing);TransactionSynchronizationManager.clear();}
    @Test void actualWritableMesConnectionPasses(){assertDoesNotThrow(guard::requireMesConnection);}
    @Test void sameUrlButChangedCatalogIsRejected() throws Exception {
        when(connection.getCatalog()).thenReturn("ry-erp");assertThrows(ServiceException.class,guard::requireMesConnection,"URL不变也不能把已切换的数据库当MES");
    }
    @Test void erpBoundConnectionIsRejected() throws Exception {when(metadata.getURL()).thenReturn("jdbc:mysql://127.0.0.1:3306/ry-erp");assertThrows(ServiceException.class,guard::requireMesConnection);}
    @Test void readOnlyOrAutoCommitIsRejected() throws Exception {when(connection.isReadOnly()).thenReturn(true);assertThrows(ServiceException.class,guard::requireMesConnection);when(connection.isReadOnly()).thenReturn(false);when(connection.getAutoCommit()).thenReturn(true);assertThrows(ServiceException.class,guard::requireMesConnection);}
    @Test void missingBoundConnectionDoesNotBorrowNewOne() throws Exception {TransactionSynchronizationManager.unbindResource(routing);assertThrows(ServiceException.class,guard::requireMesConnection);verify(pool,never()).getConnection();}
}
