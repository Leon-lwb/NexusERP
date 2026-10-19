package com.nexus.erp.wm;

import com.nexus.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** INACTIVE is metadata on an existing identity; it must not invalidate an original receipt or authorize a new one. */
class WmInactiveBatchReceiptReplayTest {
    @BeforeAll static void metadata() { WmBatchProductionReceiptTest.metadata(); }

    @Test void postedBatchOnlyGroupReplaysAfterDisablingAndSellingWithoutRecreditingStock() {
        var f=fixture();var first=f.post();
        f.stock.setStockQuantity(BigDecimal.ZERO);f.stock.setWarehouseId(90L);f.stock.setLocationId(99L);
        f.jdbc.update("INSERT INTO wm_material_stock_tx(tx_id,tenant_id,source_doc_type,source_doc_id,tx_type,tx_flag,batch_id,tx_quantity,del_flag) VALUES(502,'t1','WM_SALES_ISSUE',77,'SALES_ISSUE',-1,50,5,'0')");
        f.jdbc.update("UPDATE wm_batch SET status='INACTIVE',issued_qty=5");
        clearInvocations(f.stockMapper,f.txMapper,f.batchMapper,f.orders,f.backflush);
        var replay=f.post();assertTrue(replay.replay());assertEquals(first.receiptId(),replay.receiptId());assertEquals(first.postingGroupId(),replay.postingGroupId());
        verifyNoInteractions(f.stockMapper,f.txMapper,f.batchMapper,f.orders,f.backflush);
        assertEquals(0,f.stock.getStockQuantity().signum());
        assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM wm_trace_posting_group",Integer.class));
        assertEquals(2,f.jdbc.queryForObject("SELECT COUNT(*) FROM wm_material_stock_tx",Integer.class));
    }

    @Test void disablingAnOldUnversionedReceiptBatchPreservesItsOriginalProof() {
        var f=fixture();f.completeWorkOrder();f.receipt.setBatchId(50L);f.receipt.setBatchCode("B50");
        f.tx.execute(status->{f.receiptService.execute(5L);return null;});
        f.jdbc.update("UPDATE wm_batch SET status='INACTIVE'");clearInvocations(f.stockMapper,f.txMapper,f.batchMapper);
        assertDoesNotThrow(()->f.guard.verifyExecuted(f.wo,new BigDecimal("5"),f.receipt));
        assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM wm_trace_posting_group",Integer.class));
        assertEquals(1,f.jdbc.queryForObject("SELECT COUNT(*) FROM wm_material_stock_tx",Integer.class));
        verifyNoInteractions(f.txMapper,f.batchMapper);
        f.stock.setLocationId(99L);
        assertThrows(ServiceException.class,()->f.guard.verifyExecuted(f.wo,new BigDecimal("5"),f.receipt));
    }

    @Test void disablingAnUnpostedBatchStillRejectsFirstReceiptEffects() {
        var f=fixture();f.jdbc.update("UPDATE wm_batch SET status='INACTIVE'");
        assertThrows(ServiceException.class,f::post);
        verifyNoInteractions(f.stockMapper,f.txMapper,f.batchMapper);
        assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM wm_trace_posting_group",Integer.class));
        assertEquals(0,f.jdbc.queryForObject("SELECT COUNT(*) FROM wm_material_stock_tx",Integer.class));
    }
    private static WmBatchProductionReceiptTest.Fixture fixture() {
        var f=new WmBatchProductionReceiptTest.Fixture();
        // The shared legacy fixture hardcodes ACTIVE. This status regression must read the actual row.
        when(f.guardMapper.lockBatch("t1",50L)).thenAnswer(call->f.jdbc.queryForObject(
            "SELECT * FROM wm_batch WHERE tenant_id='t1' AND batch_id=50 FOR UPDATE",
            org.springframework.jdbc.core.BeanPropertyRowMapper.newInstance(com.nexus.erp.wm.domain.WmBatch.class)));
        return f;
    }

}
