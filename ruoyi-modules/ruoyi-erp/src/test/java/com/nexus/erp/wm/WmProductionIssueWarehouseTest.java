package com.nexus.erp.wm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementDetailMapper;
import com.nexus.erp.prd.mapper.PrdMaterialRequirementMapper;
import com.nexus.erp.prd.mapper.PrdWorkOrderMapper;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.mapper.*;
import com.nexus.erp.wm.service.IWmStockOperationService;
import com.nexus.erp.wm.service.impl.WmProductionIssueServiceImpl;
import com.nexus.system.strategy.AutoCodeUtil;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class WmProductionIssueWarehouseTest {
    @Mock WmProductionIssueMapper baseMapper;
    @Mock WmProductionIssueLineMapper lineMapper;
    @Mock WmMaterialStockMapper stockMapper;
    @Mock WmWarehouseMapper warehouseMapper;
    @Mock IWmStockOperationService stockOperationService;
    @Mock AutoCodeUtil autoCodeUtil;
    @Mock PrdWorkOrderMapper workOrderMapper;
    @Mock PrdMaterialRequirementMapper requirementMapper;
    @Mock PrdMaterialRequirementDetailMapper detailMapper;
    @InjectMocks WmProductionIssueServiceImpl service;

    @BeforeAll static void init() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WmProductionIssue.class);
        TableInfoHelper.initTableInfo(assistant, WmProductionIssueLine.class);
        TableInfoHelper.initTableInfo(assistant, WmMaterialStock.class);
    }

    private WmProductionIssueLine prepare(Long headWarehouse, Long lineWarehouse) {
        WmProductionIssue issue = new WmProductionIssue();
        issue.setIssueId(1L);
        issue.setStatus("CREATED");
        issue.setWarehouseId(headWarehouse);
        when(baseMapper.selectById(1L)).thenReturn(issue);
        when(baseMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        WmProductionIssueLine line = new WmProductionIssueLine();
        line.setIssueId(1L);
        line.setLineId(2L);
        line.setMaterialId(100L);
        line.setMaterialName("component");
        line.setRequiredQty(BigDecimal.TEN);
        line.setCurrentQty(BigDecimal.ONE);
        line.setWarehouseId(lineWarehouse);
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(line));
        return line;
    }

    private void enabledWarehouse(long id) {
        WmWarehouse warehouse = new WmWarehouse();
        warehouse.setWarehouseId(id);
        warehouse.setWarehouseCode("RAW" + id);
        warehouse.setWarehouseName("原料仓");
        warehouse.setStatus(1L);
        when(warehouseMapper.selectById(id)).thenReturn(warehouse);
    }

    @Test void absentWarehouseFailsBeforeAnyStockPosting() {
        prepare(null, null);
        RuntimeException error = assertThrows(RuntimeException.class, () -> service.execute(1L));
        assertTrue(error.getMessage().contains("仓库"), error.getMessage());
        verifyNoInteractions(stockOperationService, stockMapper, warehouseMapper);
        verify(baseMapper, never()).updateById(any(WmProductionIssue.class));
    }

    @Test void missingOrDisabledWarehouseFailsBeforeAnyStockPosting() {
        prepare(55L, null);
        WmWarehouse disabled = new WmWarehouse();
        disabled.setStatus(0L);
        when(warehouseMapper.selectById(55L)).thenReturn(disabled);
        RuntimeException error = assertThrows(RuntimeException.class, () -> service.execute(1L));
        assertTrue(error.getMessage().contains("仓库"), error.getMessage());
        verifyNoInteractions(stockOperationService, stockMapper);
        verify(baseMapper, never()).updateById(any(WmProductionIssue.class));
    }

    @Test void headWarehouseFallbackScopesAtpAndPosting() {
        WmProductionIssueLine line = prepare(55L, null);
        enabledWarehouse(55L);
        stock(55L, new BigDecimal("4"), BigDecimal.ZERO);
        /** 正控真实写回必须成功，不能依赖未设返回值的默认零行。 */
        when(lineMapper.updateById(any(WmProductionIssueLine.class))).thenReturn(1);
        when(baseMapper.updateById(any(WmProductionIssue.class))).thenReturn(1);
        service.execute(1L);
        assertEquals(55L, line.getWarehouseId());
        assertEquals("RAW55", line.getWarehouseCode());
        assertEquals("原料仓", line.getWarehouseName());
        verifyWarehouseScopedAtp(55L);
        verify(stockOperationService).postProductionIssue(same(line), any());
    }

    @Test void lineWarehouseOverridesHeadAndReservedStockCannotSatisfyAtp() {
        prepare(55L, 66L);
        enabledWarehouse(66L);
        stock(66L, new BigDecimal("10"), BigDecimal.TEN);
        RuntimeException error = assertThrows(RuntimeException.class, () -> service.execute(1L));
        assertTrue(error.getMessage().contains("库存不足"), error.getMessage());
        verifyWarehouseScopedAtp(66L);
        verify(warehouseMapper).selectById(66L);
        verify(warehouseMapper, never()).selectById(55L);
        verifyNoInteractions(stockOperationService);
        verify(baseMapper, never()).updateById(any(WmProductionIssue.class));
    }

    @Test void lineWarehouseOverridePostsOnlyAfterItsOwnAtp() {
        WmProductionIssueLine line = prepare(55L, 66L);
        enabledWarehouse(66L);
        stock(66L, BigDecimal.ONE, BigDecimal.ZERO);
        /** 正控真实写回必须成功，不能依赖未设返回值的默认零行。 */
        when(lineMapper.updateById(any(WmProductionIssueLine.class))).thenReturn(1);
        when(baseMapper.updateById(any(WmProductionIssue.class))).thenReturn(1);
        service.execute(1L);
        verifyWarehouseScopedAtp(66L);
        verify(stockOperationService).postProductionIssue(same(line), any());
        assertEquals(66L, line.getWarehouseId());
    }

    @Test void laterLineWithUnknownWarehouseDoesNotPostEarlierValidLine() {
        WmProductionIssueLine first = prepare(55L, null);
        enabledWarehouse(55L);
        stock(55L, BigDecimal.ONE, BigDecimal.ZERO);
        WmProductionIssueLine later = new WmProductionIssueLine();
        later.setIssueId(1L);
        later.setLineId(3L);
        later.setMaterialId(101L);
        later.setMaterialName("second component");
        later.setCurrentQty(BigDecimal.ONE);
        later.setWarehouseId(77L);
        reset(lineMapper);
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, later));
        RuntimeException error = assertThrows(RuntimeException.class, () -> service.execute(1L));
        assertTrue(error.getMessage().contains("仓库"), error.getMessage());
        verifyNoInteractions(stockOperationService);
        verify(baseMapper, never()).updateById(any(WmProductionIssue.class));
    }

    @Test void combinedDemandInSameWarehouseFailsBeforePostingAnyLine() {
        WmProductionIssueLine first = prepare(55L, null);
        WmProductionIssueLine second = new WmProductionIssueLine();
        second.setIssueId(1L);
        second.setLineId(3L);
        second.setMaterialId(100L);
        second.setMaterialName("component");
        second.setCurrentQty(BigDecimal.ONE);
        second.setWarehouseId(55L);
        reset(lineMapper);
        when(lineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, second));
        enabledWarehouse(55L);
        stock(55L, BigDecimal.ONE, BigDecimal.ZERO);

        RuntimeException error = assertThrows(RuntimeException.class, () -> service.execute(1L));
        assertTrue(error.getMessage().contains("库存不足"), error.getMessage());
        verifyNoInteractions(stockOperationService);
        verify(baseMapper, never()).updateById(any(WmProductionIssue.class));
    }

    private void stock(long warehouseId, BigDecimal quantity, BigDecimal reserved) {
        WmMaterialStock stock = new WmMaterialStock();
        stock.setWarehouseId(warehouseId);
        stock.setStockQuantity(quantity);
        stock.setReservedQuantity(reserved);
        when(stockMapper.selectList(any(Wrapper.class))).thenReturn(List.of(stock));
    }

    private void verifyWarehouseScopedAtp(long warehouseId) {
        @SuppressWarnings("unchecked") ArgumentCaptor<Wrapper<WmMaterialStock>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(stockMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("warehouse_id"), sql);
        assertTrue(
                ((AbstractWrapper<?, ?, ?>) captor.getValue())
                        .getParamNameValuePairs()
                        .containsValue(warehouseId),
                sql);
    }
}
