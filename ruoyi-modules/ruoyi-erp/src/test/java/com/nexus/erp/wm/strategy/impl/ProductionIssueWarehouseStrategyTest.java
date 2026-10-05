package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.service.IInventoryValuationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class ProductionIssueWarehouseStrategyTest {
    @Mock WmMaterialStockMapper stockMapper;
    @Mock WmMaterialStockTxMapper txMapper;
    @Mock IInventoryValuationService valuationService;
    @Mock WmBatchMapper batchMapper;

    @BeforeAll static void init() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WmMaterialStock.class);
    }

    private WmProductionIssueLine line(Long warehouseId) {
        WmProductionIssueLine line = new WmProductionIssueLine();
        line.setMaterialId(100L);
        line.setMaterialName("component");
        line.setWarehouseId(warehouseId);
        line.setQuantity(BigDecimal.ONE);
        return line;
    }

    @Test void fefoUsesExpiringBatchesBeforeUndatedStock() {
        when(stockMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());
        assertThrows(RuntimeException.class,
            () -> new ProductionIssueStrategy(txMapper, stockMapper, valuationService, batchMapper).execute(line(66L), new Date()));
        @SuppressWarnings("unchecked") ArgumentCaptor<Wrapper<WmMaterialStock>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(stockMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment().replaceAll("\\s+", " ").toLowerCase();
        assertTrue(sql.contains("order by expire_date is null asc, expire_date asc, recpt_date asc, stock_id asc"), sql);
        assertTrue(sql.contains("ifnull(reserved_quantity, 0)"), sql);
    }

    @Test void directStrategyCallWithoutWarehouseFailsClosed() {
        RuntimeException error = assertThrows(RuntimeException.class,
            () -> new ProductionIssueStrategy(txMapper, stockMapper, valuationService, batchMapper).execute(line(null), new Date()));
        assertTrue(error.getMessage().contains("仓库"), error.getMessage());
        verifyNoInteractions(stockMapper, txMapper);
    }

    @Test void strategyQueryIncludesWarehouseEvenWithoutBatchOrLocation() {
        when(stockMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());
        assertThrows(RuntimeException.class,
            () -> new ProductionIssueStrategy(txMapper, stockMapper, valuationService, batchMapper).execute(line(66L), new Date()));
        @SuppressWarnings("unchecked") ArgumentCaptor<Wrapper<WmMaterialStock>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(stockMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("warehouse_id"), sql);
        assertTrue(((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) captor.getValue()).getParamNameValuePairs().containsValue(66L), sql);
        verifyNoInteractions(txMapper);
    }
}
