package com.nexus.web.service;

import com.nexus.common.trace.TracePage;
import com.nexus.mes.integration.trace.MesIdentityTraceReader;
import com.nexus.web.service.MaterialIdentityTraceService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** A legal WIP component assignment commits between the current two read-port calls. */
class RecallAllocationHandoffIndependentReviewTest {
    @Test void anAllocationCommittingBetweenExactAndWorkOrderReadsMustNotDisappearFromRecall() {
        try(var f=new BatchRecallBoundaryIndependentReviewTest.Fixture()) {
            // Independent source batch23 is fully issued to live WO8. Its serial output1001 is WIP;
            // ERP production batch22 has only been authorized, never received or sold.
            f.erp.jdbc.update("INSERT INTO wm_batch(batch_id,tenant_id,batch_code,material_id,material_code,warehouse_id,quantity,received_qty,issued_qty,status,source_doc_type,source_doc_id,source_doc_no,del_flag) VALUES(23,'000001','RAW23',1,'M1',10,1,1,1,'ACTIVE','WM_MATERIAL_RECEIVE',62,'RECEIVE62','0'),(22,'000001','PRODUCT22',3,'M3',10,1,0,0,'ACTIVE','PRD_WORK_ORDER',8,'WO8','0')");
            f.erp.jdbc.update("INSERT INTO wm_sn VALUES(1001,'000001',22,'0')");
            f.erp.jdbc.update("INSERT INTO wm_material_stock_tx(tx_id,tenant_id,source_doc_type,source_doc_id,source_doc_line_id,tx_type,material_stock_id,batch_id,tx_flag,tx_quantity,unit_cost,tx_amount,tracking_version,del_flag) VALUES(400,'000001','WM_MATERIAL_RECEIVE',62,63,'RECEIVE_IN',301,23,1,1,3,3,1,'0'),(401,'000001','WM_PRODUCTION_ISSUE',80,81,'PRODUCTION_ISSUE',301,23,-1,1,3,3,1,'0')");
            f.mes.update("INSERT INTO lt_lot VALUES(13,'000001','ISSUED23',1,1,'CONSUMED',NULL,NULL,'0')");
            f.mes.update("INSERT INTO lt_lot_allocation VALUES(301,'000001',13,1,23,1,'PRODUCTION_ISSUE',80,81,1)");
            f.mes.update("INSERT INTO lt_wo_consumption VALUES(102,'000001',8,1,13,301,1,'0')");
            f.mes.update("INSERT INTO pro_sn VALUES(1001,'000001',8,'0')");
            assertEquals(List.of(),f.mesReader.affectedByBatch("000001",23L,0,500).rows());
            assertEquals(8L,f.mesReader.unallocatedByBatch("000001",23L,0,500).rows().get(0).mesWoId());
            var port=spy(f.mesReader);var committed=new AtomicBoolean();
            doAnswer(call->{
                assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                assertEquals(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ,org.springframework.transaction.support.TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
                @SuppressWarnings("unchecked") TracePage<Long> before=(TracePage<Long>)call.callRealMethod();
                if(committed.compareAndSet(false,true)) {
                    independent(f).executeWithoutResult(tx->
                        f.mes.update("INSERT INTO lt_sn_consumption VALUES(1002,'000001',1001,1,13,1,102,'CONSUME')"));
                }
                return before;
            }).when(port).affectedByBatch("000001",23L,0,500);
            var transaction=new org.springframework.transaction.interceptor.TransactionInterceptor();transaction.setTransactionManager(new DataSourceTransactionManager(Objects.requireNonNull(f.mes.getDataSource())));transaction.setTransactionAttributeSource(new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource());
            var proxy=new org.springframework.aop.framework.ProxyFactory(port);proxy.setProxyTargetClass(true);proxy.addAdvice(transaction);
            var result=new MaterialIdentityTraceService(List.of(f.erp.reader,(MesIdentityTraceReader)proxy.getProxy())).recall(23L,0,500,1);
            assertTrue(committed.get());assertEquals(List.of(1001L),f.mesReader.affectedByBatch("000001",23L,0,500).rows());
            assertTrue(f.mesReader.unallocatedByBatch("000001",23L,0,500).rows().isEmpty());
            @SuppressWarnings("unchecked") var rows=(List<Map<String,Object>>)result.get("rows");var scopes=(TracePage<?>)result.get("scopeImpacts");
            assertEquals(true,result.get("truncatedByDepth"),"The one-subject test leaves further product expansion explicitly bounded");
            assertTrue(rows.stream().anyMatch(row->"1001".equals(row.get("identityId"))) || !scopes.rows().isEmpty(),
                "recall returned neither actual SN1001 nor conservative WO8 after a lawful allocation committed between its two reads");
        }
    }
    private static TransactionTemplate independent(BatchRecallBoundaryIndependentReviewTest.Fixture fixture){
        var tx=new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(fixture.mes.getDataSource())));tx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);return tx;
    }

}
