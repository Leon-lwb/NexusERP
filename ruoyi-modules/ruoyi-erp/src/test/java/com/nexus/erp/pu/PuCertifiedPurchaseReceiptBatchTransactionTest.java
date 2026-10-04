package com.nexus.erp.pu;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.pu.mapper.PuRequirementCompetitionPoolMapper;
import com.nexus.erp.pu.mapper.PuRequirementMaterialGuardMapper;
import com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.pu.service.PuCertifiedPurchaseLifecycleService.ReceiptCommand;
import com.nexus.erp.pu.domain.PuRequirementAllocationEvent;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.defaults.DefaultSqlSessionFactory;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.nexus.erp.pu.PuCertifiedPurchaseReceiptBatchServiceTest.*;

/** 使用真实ERP独占可重复读事务执行器与 Spring JDBC 提交/回滚；Mapper/JDBC 端点为离线替身，不构成数据库验收。 */
class PuCertifiedPurchaseReceiptBatchTransactionTest {
    @Test void finalLineFailureRollsBackOnePhysicalTransactionAndReturnsNoPartialBatch() throws Exception {
        try(var f=new Fixture()) {
            var db=new Physical(f);db.failLastLine=true;
            assertThrows(ServiceException.class,()->f.service.consumeReceipt(T,901L));
            assertEquals(0,db.permanent.size());assertEquals(0,db.pending.size());
            verify(db.connections.get(0)).rollback();verify(db.connections.get(0),never()).commit();
            assertClean();
        }
    }
    @Test void failureAfterLastMutationAtTerminalQualificationStillRollsBackAllLines() throws Exception {
        try(var f=new Fixture()) {
            var db=new Physical(f);db.failTerminalQualification=true;
            assertThrows(ServiceException.class,()->f.service.consumeReceipt(T,901L));
            assertEquals(0,db.permanent.size());assertEquals(0,db.pending.size());
            verify(f.lifecycle,times(2)).consumeReceiptLocked(any());verify(db.connections.get(0)).rollback();
            assertClean();
        }
    }
    @Test void successfulBatchHasExactlyOnePhysicalCommitAndNoMasterConnection() throws Exception {
        try(var f=new Fixture()) {
            var db=new Physical(f);var result=f.service.consumeReceipt(T,901L);
            assertTrue(result.certified());assertEquals(2,db.permanent.size());assertEquals(1,db.connections.size());
            verify(db.connections.get(0)).commit();verify(db.connections.get(0),never()).rollback();
            verify(db.master,never()).getConnection();assertClean();
        }
    }
    @Test void uncertainCommitRecoversOnlyByReReadingSameDocumentAndAllPermanentEvents() throws Exception {
        try(var f=new Fixture()) {
            var db=new Physical(f);db.commitOutcomeUnknown=true;
            assertThrows(ServiceException.class,()->f.service.consumeReceipt(T,901L));
            assertEquals(2,db.permanent.size(),"simulated DB committed but JDBC acknowledgement was lost");assertClean();
            db.commitOutcomeUnknown=false;
            var replay=f.service.consumeReceipt(T,901L);assertTrue(replay.replayed());assertEquals(2,replay.lines().size());
            assertEquals(2,db.permanent.size());assertEquals(2,db.connections.size());
            verify(f.mapper,times(2)).readReceiptHead(T,901L);verify(db.master,never()).getConnection();assertClean();
        }
    }
    @Test void preexistingAmbientResourcesCannotBeAcceptedAsDedicatedTransactionOwnership() throws Exception {
        try(var f=new Fixture()) {
            var db=new Physical(f);Object key=new Object(),caller=new Object();
            TransactionSynchronizationManager.bindResource(key,caller);
            try {
                assertThrows(ServiceException.class,()->f.service.consumeReceipt(T,901L));
                assertSame(caller,TransactionSynchronizationManager.getResource(key));assertTrue(db.connections.isEmpty());
                verifyNoInteractions(f.mapper);
            } finally {assertSame(caller,TransactionSynchronizationManager.unbindResource(key));}
            assertClean();
        }
    }
    @Test void permanentReadbackRejectsNoOwnedTransactionWithoutOpeningAnyConnection() throws Exception {
        try(var f=new Fixture()) {
            var db=new Physical(f);
            assertThrows(ServiceException.class,()->f.service.readCommittedReceiptLocked(T,901L,801L));
            assertTrue(db.connections.isEmpty());verifyNoInteractions(f.executor,f.mapper,f.lifecycle,f.locks);
            verify(db.master,never()).getConnection();assertClean();
        }
    }
    @Test void permanentReadbackJoinsOnlyTheCallerOwnedErpRepeatableReadConnection() throws Exception {
        try(var f=new Fixture()) {
            var db=new Physical(f);
            when(f.locks.lockExisting(T,List.of(201L))).thenReturn(Map.of(201L,5));
            for(var row:f.allocations) {
                row.setVersion(3);Long id=row.getOrderLineId()+8300;db.permanent.put(id,f.event(row,id));
            }
            var before=new LinkedHashMap<>(db.permanent);
            var result=f.executor.executeRepeatableRead(T,()->f.service.readCommittedReceiptLocked(T,901L,801L)).orElseThrow();
            assertTrue(result.replayed());assertEquals(2,result.lines().size());
            assertEquals(before,db.permanent);assertTrue(db.pending.isEmpty());assertEquals(1,db.connections.size());
            verify(f.executor,times(1)).executeRepeatableRead(eq(T),any());
            verify(f.locks).lockExisting(T,List.of(201L));verify(f.locks,never()).lock(any(),any());
            verify(f.lifecycle,times(2)).validateReceiptReadOnlyLocked(any());
            verify(f.lifecycle,never()).validateReceiptLocked(any());verify(f.lifecycle,never()).consumeReceiptLocked(any());
            verify(db.connections.get(0)).commit();verify(db.master,never()).getConnection();assertClean();
        }
    }
    @Test void ambientResourcesCannotBeBorrowedByPermanentReadback() throws Exception {
        try(var f=new Fixture()) {
            var db=new Physical(f);Object key=new Object(),caller=new Object();
            TransactionSynchronizationManager.bindResource(key,caller);
            try {
                assertThrows(ServiceException.class,()->f.service.readCommittedReceiptLocked(T,901L,801L));
                assertSame(caller,TransactionSynchronizationManager.getResource(key));assertTrue(db.connections.isEmpty());
                verifyNoInteractions(f.executor,f.mapper,f.lifecycle,f.locks);verify(db.master,never()).getConnection();
            } finally {assertSame(caller,TransactionSynchronizationManager.unbindResource(key));}
            assertClean();
        }
    }
    static void assertClean() {
        assertTrue(TransactionSynchronizationManager.getResourceMap().isEmpty());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
    }
    static class Physical {
        final HikariDataSource master=mock(HikariDataSource.class),erp=mock(HikariDataSource.class);
        final List<Connection> connections=new ArrayList<>();final Map<Long,PuRequirementAllocationEvent> pending=new LinkedHashMap<>(),permanent=new LinkedHashMap<>();
        boolean failLastLine,failTerminalQualification,commitOutcomeUnknown;
        Physical(Fixture f) throws SQLException {
            var routing=new DynamicRoutingDataSource(List.of());routing.setPrimary("master");routing.setStrict(true);
            routing.addDataSource("master",master);routing.addDataSource("erp",erp);
            var config=new Configuration(new Environment("offline-m-receipt",new SpringManagedTransactionFactory(),routing));
            config.addMapper(PuRequirementCompetitionPoolMapper.class);config.addMapper(PuRequirementMaterialGuardMapper.class);
            var factory=new DefaultSqlSessionFactory(config);var executor=new PuRequirementErpTransactionExecutor(factory);
            var qualification=new PuRequirementErpTransactionQualification(factory);
            when(erp.getConnection()).thenAnswer(call->connection(f));
            when(f.executor.executeRepeatableRead(eq(T),any())).thenAnswer(call->executor.executeRepeatableRead(T,(Supplier<?>)call.getArgument(1)));
            doAnswer(call->{qualification.requireSourceChainTransaction();if(failTerminalQualification&&pending.size()==2)throw new ServiceException("terminal source failure");return null;}).when(f.qualification).requireSourceChainTransaction();
            when(f.mapper.lockReceiptEvent(eq(T),anyString())).thenAnswer(call->permanent.values().stream().filter(e->e.getEventKey().equals(call.getArgument(1))).findFirst().orElse(null));
            doAnswer(call->{
                ReceiptCommand c=call.getArgument(0);qualification.requireSourceChainTransaction();
                var row=f.allocations.stream().filter(a->a.getAllocationKey().equals(c.allocationKey())).findFirst().orElseThrow();
                if(permanent.containsKey(c.receiveLineId()))return f.receipt(row,c.receiveLineId(),true);
                if(failLastLine&&c.receiveLineId().equals(9002L))throw new ServiceException("last line CAS failure");
                pending.put(c.receiveLineId(),f.event(row,c.receiveLineId()));return f.receipt(row,c.receiveLineId(),false);
            }).when(f.lifecycle).consumeReceiptLocked(any());
        }
        Connection connection(Fixture f) throws SQLException {
            Connection c=mock(Connection.class);connections.add(c);
            AtomicBoolean auto=new AtomicBoolean(true),readOnly=new AtomicBoolean(false),closed=new AtomicBoolean(false);
            AtomicInteger isolation=new AtomicInteger(Connection.TRANSACTION_READ_COMMITTED);
            when(c.getAutoCommit()).thenAnswer(call->auto.get());doAnswer(call->{auto.set(call.getArgument(0));return null;}).when(c).setAutoCommit(anyBoolean());
            when(c.isReadOnly()).thenAnswer(call->readOnly.get());doAnswer(call->{readOnly.set(call.getArgument(0));return null;}).when(c).setReadOnly(anyBoolean());
            when(c.isClosed()).thenAnswer(call->closed.get());doAnswer(call->{closed.set(true);return null;}).when(c).close();
            when(c.getTransactionIsolation()).thenAnswer(call->isolation.get());doAnswer(call->{isolation.set(call.getArgument(0));return null;}).when(c).setTransactionIsolation(anyInt());
            doAnswer(call->{
                permanent.putAll(pending);pending.clear();
                for(var row:f.allocations)row.setVersion(3);
                if(commitOutcomeUnknown)throw new SQLException("simulated unknown commit acknowledgement");return null;
            }).when(c).commit();
            doAnswer(call->{pending.clear();return null;}).when(c).rollback();return c;
        }
    }
}
