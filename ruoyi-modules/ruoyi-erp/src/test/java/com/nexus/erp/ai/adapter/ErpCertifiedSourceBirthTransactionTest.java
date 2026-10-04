package com.nexus.erp.ai.adapter;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.ai.adapter.mapper.ErpCertifiedSourceBirthMapper.*;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper;
import com.nexus.erp.pu.mapper.PuRequirementMaterialGuardMapper;
import com.nexus.erp.pu.service.*;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实专属 ERP 执行器及 Spring 事务生命周期，JDBC 为不联网替身；不是本地库验收。 */
class ErpCertifiedSourceBirthTransactionTest {
    @Test void actualDedicatedErpExecutorCommitsMaterialGuardAndBirthOnceOnSamePhysicalConnection() throws Exception {
        try(var physical=new Physical()){
            var result=physical.fixture.service.create(ErpCertifiedSourceBirthServiceTest.request());
            assertEquals(result.materialId(),physical.savedMaterial.materialId());
            assertEquals(result.materialId(),physical.savedBirth.materialId());
            assertEquals(result.materialId(),physical.savedGuard);
            assertEquals(1,physical.connections.size());verify(physical.connections.get(0)).commit();
            verify(physical.master,never()).getConnection();assertClean();
        }
    }
    @Test void rejectedBirthInsertRollsBackTheActualPhysicalTransactionIncludingMaterialAndGuard() throws Exception {
        try(var physical=new Physical()){
            physical.failBirth=true;assertThrows(ServiceException.class,()->physical.fixture.service.create(ErpCertifiedSourceBirthServiceTest.request()));
            assertNull(physical.savedMaterial);assertNull(physical.savedBirth);assertNull(physical.savedGuard);
            verify(physical.connections.get(0)).rollback();verify(physical.connections.get(0),never()).commit();assertClean();
        }
    }
    @Test void finalQualificationFailureAfterBothInsertsCannotLeakMaterialOrBirth() throws Exception {
        try(var physical=new Physical()){
            physical.failFinal=true;assertThrows(ServiceException.class,()->physical.fixture.service.create(ErpCertifiedSourceBirthServiceTest.request()));
            assertNull(physical.savedMaterial);assertNull(physical.savedBirth);assertNull(physical.savedGuard);
            verify(physical.connections.get(0)).rollback();assertClean();
        }
    }
    static void assertClean(){assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());assertFalse(TransactionSynchronizationManager.isActualTransactionActive());}
    static class Physical implements AutoCloseable {
        final ErpCertifiedSourceBirthServiceTest fixture=new ErpCertifiedSourceBirthServiceTest();
        final HikariDataSource erp=mock(HikariDataSource.class),master=mock(HikariDataSource.class);
        final List<Connection> connections=new ArrayList<>();
        NewMaterial pendingMaterial,savedMaterial;StoredBirth pendingBirth,savedBirth;Long pendingGuard,savedGuard;
        boolean failBirth,failFinal;
        Physical() throws Exception {
            fixture.setup();var routing=new DynamicRoutingDataSource(List.of());routing.setPrimary("master");routing.setStrict(true);
            routing.addDataSource("erp",erp);routing.addDataSource("master",master);
            var config=new Configuration(new Environment("task-a-offline",new SpringManagedTransactionFactory(),routing));
            config.addMapper(PuRequirementCompetitionPoolMapper.class);config.addMapper(PuRequirementMaterialGuardMapper.class);
            var factory=new DefaultSqlSessionFactory(config);var executor=new PuRequirementErpTransactionExecutor(factory);var qual=new PuRequirementErpTransactionQualification(factory);
            when(erp.getConnection()).thenAnswer(c->connection());
            when(fixture.transactions.executeRepeatableRead(eq("000000"),any())).thenAnswer(c->executor.executeRepeatableRead("000000",(Supplier<?>)c.getArgument(1)));
            doAnswer(c->{qual.requireSourceChainTransaction();if(failFinal&&pendingBirth!=null)throw new ServiceException("提交前归属拒绝");return null;}).when(fixture.qualification).requireSourceChainTransaction();
            when(fixture.mapper.insertMaterial(any())).thenAnswer(c->{qual.requireSourceChainTransaction();pendingMaterial=c.getArgument(0);return 1;});
            when(fixture.mapper.insertNewGuard(anyString(),anyLong())).thenAnswer(c->{qual.requireSourceChainTransaction();pendingGuard=c.getArgument(1);return 1;});
            when(fixture.mapper.insertBirth(any())).thenAnswer(c->{qual.requireSourceChainTransaction();if(failBirth)return 0;pendingBirth=c.getArgument(0);return 1;});
        }
        Connection connection() throws Exception {
            var c=mock(Connection.class);connections.add(c);var auto=new AtomicBoolean(true);var closed=new AtomicBoolean(false);var iso=new AtomicInteger(Connection.TRANSACTION_READ_COMMITTED);
            when(c.getAutoCommit()).thenAnswer(i->auto.get());doAnswer(i->{auto.set(i.getArgument(0));return null;}).when(c).setAutoCommit(anyBoolean());
            when(c.isClosed()).thenAnswer(i->closed.get());doAnswer(i->{closed.set(true);return null;}).when(c).close();
            when(c.getTransactionIsolation()).thenAnswer(i->iso.get());doAnswer(i->{iso.set(i.getArgument(0));return null;}).when(c).setTransactionIsolation(anyInt());
            doAnswer(i->{savedMaterial=pendingMaterial;savedBirth=pendingBirth;savedGuard=pendingGuard;pendingMaterial=null;pendingBirth=null;pendingGuard=null;return null;}).when(c).commit();
            doAnswer(i->{pendingMaterial=null;pendingBirth=null;pendingGuard=null;return null;}).when(c).rollback();return c;
        }
        public void close(){fixture.cleanup();}
    }
}
