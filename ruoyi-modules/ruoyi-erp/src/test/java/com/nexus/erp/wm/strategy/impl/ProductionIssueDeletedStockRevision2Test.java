package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.service.IInventoryValuationService;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** R8 独立反例：实际选行与原子扣减均须拒绝已删库存，无数据库连接。 */
@ExtendWith(MockitoExtension.class)
class ProductionIssueDeletedStockRevision2Test {
    @Mock WmMaterialStockMapper stockMapper;
    @Mock WmMaterialStockTxMapper txMapper;
    @Mock IInventoryValuationService valuationService;
    @Mock WmBatchMapper batchMapper;

    @BeforeAll static void init() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WmMaterialStock.class);
    }

    @Test void actualFefoSelectCannotIncludeDeletedEarlyExpiryStock() {
        when(stockMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        WmProductionIssueLine line = new WmProductionIssueLine();
        line.setMaterialId(23L);
        line.setWarehouseId(7L);
        line.setQuantity(BigDecimal.ONE);
        assertThrows(RuntimeException.class,
            () -> new ProductionIssueStrategy(txMapper, stockMapper, valuationService, batchMapper).execute(line, new Date()),
            "R8：无有效库存应失败而不能扣已删早效期库存");
        ArgumentCaptor<LambdaQueryWrapper<WmMaterialStock>> captured = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(stockMapper).selectList(captured.capture());
        assertTrue(captured.getValue().getSqlSegment().contains("del_flag"), "R8：实际 FEFO 选行必须显式限定未删");
        assertTrue(captured.getValue().getParamNameValuePairs().containsValue("0"), "R8：只能使用有效库存行");
        verifyNoInteractions(valuationService, txMapper, batchMapper);
    }

    @Test void atomicDeductionMustGuardAgainstDeletionAfterRead() throws Exception {
        var method = WmMaterialStockMapper.class.getMethod("deductStockAvailable", Long.class, BigDecimal.class, Integer.class);
        String sql = String.join(" ", method.getAnnotation(Update.class).value());
        assertTrue(sql.contains("del_flag = '0'"), "R8：读取后删除竞态必须在原子扣减再次守卫");
        assertTrue(sql.contains("reserved_quantity IS NOT NULL"), "R8：未知预留不能在原子扣减中兜底为零");
    }
}
