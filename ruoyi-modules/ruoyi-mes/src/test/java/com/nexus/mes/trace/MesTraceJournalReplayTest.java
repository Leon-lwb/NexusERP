package com.nexus.mes.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 仅覆盖 journal 的只读重放边界，不触发 MES 业务投影。 */
class MesTraceJournalReplayTest {
    @Test void lookupRunsInReadonlyTransactionWithoutRequiringAWriteConnection(){
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:mes_replay_"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE mes_trace_inbox(event_id VARCHAR(64) PRIMARY KEY,tenant_id VARCHAR(20),source_module VARCHAR(16),event_type VARCHAR(64),aggregate_type VARCHAR(64),aggregate_id BIGINT,aggregate_version BIGINT,schema_version INT,payload CLOB,content_hash CHAR(64),status VARCHAR(24),result_payload CLOB)");
        var event=new MaterialTraceEvent("ERP_RESULT","000001","ERP","COMPLETION_MANIFEST_CANCELLATION_RESULT","COMPLETION_CANCELLATION_RESULT",11L,1,1,"{}",TraceHashes.sha256("{}"));
        jdbc.update("INSERT INTO mes_trace_inbox VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",event.eventId(),event.tenantId(),event.sourceModule(),event.eventType(),event.aggregateType(),event.aggregateId(),event.aggregateVersion(),event.schemaVersion(),event.payload(),event.contentHash(),"APPLIED","IMMUTABLE_RESULT");
        var context=mock(MesTraceContext.class);var checks=new AtomicInteger();
        doAnswer(call->{assertTrue(TransactionSynchronizationManager.isActualTransactionActive());assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());checks.incrementAndGet();return null;}).when(context).requireTenant(event.tenantId());
        var advice=new TransactionInterceptor();advice.setTransactionManager(new DataSourceTransactionManager(jdbc.getDataSource()));
        advice.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());advice.afterPropertiesSet();
        var factory=new ProxyFactory(new MesTraceJournalService(jdbc,new ObjectMapper(),context));factory.setProxyTargetClass(true);factory.addAdvice(advice);
        var journal=(MesTraceJournalService)factory.getProxy();
        assertEquals(new TraceDeliveryResult("APPLIED","IMMUTABLE_RESULT"),journal.findApplied(event));assertEquals(1,checks.get());
        verify(context,never()).requireWrite();verify(context,never()).requireConnection();assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mes_trace_inbox",Integer.class));
        jdbc.update("UPDATE mes_trace_inbox SET status='WAITING'");assertNull(journal.findApplied(event));
        jdbc.update("UPDATE mes_trace_inbox SET payload='tampered'");assertThrows(IllegalStateException.class,()->journal.findApplied(event));
        doThrow(new IllegalStateException("tenant rejected")).when(context).requireTenant(event.tenantId());assertThrows(IllegalStateException.class,()->journal.findApplied(event));
    }
}
