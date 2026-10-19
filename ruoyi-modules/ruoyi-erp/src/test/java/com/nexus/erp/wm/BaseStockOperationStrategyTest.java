package com.nexus.erp.wm;

import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.strategy.BaseStockOperationStrategy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BaseStockOperationStrategyTest {
    private final WmMaterialStockTxMapper mapper = mock(WmMaterialStockTxMapper.class);
    private final Probe strategy = new Probe(mapper);

    @Test
    void copiesExactSerialIdentityAndPreservesStockSnapshot() {
        WmMaterialStock stock = new WmMaterialStock();
        stock.setStockId(1L);
        stock.setMaterialId(10L);
        stock.setBatchId(20L);
        stock.setBatchCode("B001");
        stock.setSnId(9007199254740993L);
        stock.setSnCode("SN001");
        stock.setStockQuantity(BigDecimal.TEN);
        Date now = new Date();
        WmMaterialStockTx tx = strategy.build(stock, now);
        assertEquals(stock.getSnId(), tx.getSnId());
        assertEquals(stock.getSnCode(), tx.getSnCode());
        assertEquals(1L, tx.getMaterialStockId());
        assertEquals(10L, tx.getMaterialId());
        assertEquals(20L, tx.getBatchId());
        assertEquals("B001", tx.getBatchCode());
        assertEquals(-1, tx.getTxFlag());
        assertEquals(BigDecimal.ONE, tx.getTxQuantity());
        assertEquals(now, tx.getTxDate());
        assertEquals(BigDecimal.TEN, stock.getStockQuantity());
        verifyNoInteractions(mapper);
    }

    @Test
    void nonSerialStockKeepsNullSerialIdentity() {
        WmMaterialStockTx tx = strategy.build(new WmMaterialStock(), new Date());
        assertNull(tx.getSnId());
        assertNull(tx.getSnCode());
        verifyNoInteractions(mapper);
    }

    private static class Probe extends BaseStockOperationStrategy {
        Probe(WmMaterialStockTxMapper mapper) { super(mapper); }
        @Override public StockOperationTypeEnum getSupportedType() { return null; }
        @Override public void execute(Object... args) { throw new UnsupportedOperationException(); }
        WmMaterialStockTx build(WmMaterialStock stock, Date now) {
            return buildBaseTxFromStock(stock, "ISSUE", -1, BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("9"), now);
        }
    }
}
