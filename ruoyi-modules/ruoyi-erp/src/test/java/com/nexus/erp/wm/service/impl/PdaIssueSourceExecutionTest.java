package com.nexus.erp.wm.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmProductionIssue;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.bo.pda.PdaPickBo;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueMapper;
import com.nexus.erp.wm.mapper.WmWarehouseMapper;
import com.nexus.erp.wm.service.IInventoryValuationService;
import com.nexus.erp.wm.strategy.StockOperationStrategyFactory;
import com.nexus.erp.wm.strategy.impl.ProductionIssueStrategy;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

/** 真实 PDA、领料执行器和库存策略串联；只模拟映射器边界，不执行 SQL 或连接数据库。 */
@Tag("dev")
@Tag("local")
class PdaIssueSourceExecutionTest {
    private final PdaIssueServiceImplTest fixture = new PdaIssueServiceImplTest();
    private final WmProductionIssueMapper headMapper = mock(WmProductionIssueMapper.class);
    private final WmMaterialStockMapper stockMapper = mock(WmMaterialStockMapper.class);
    private final WmWarehouseMapper warehouseMapper = mock(WmWarehouseMapper.class);
    private final WmMaterialStockTxMapper txMapper = mock(WmMaterialStockTxMapper.class);
    private final WmBatchMapper batchMapper = mock(WmBatchMapper.class);
    private final IInventoryValuationService valuation = mock(IInventoryValuationService.class);
    private PdaIssueServiceImpl service;

    @BeforeAll static void initMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WmProductionIssue.class);
        TableInfoHelper.initTableInfo(assistant, WmProductionIssueLine.class);
        TableInfoHelper.initTableInfo(assistant, WmMaterialStock.class);
    }
    @BeforeEach void setUp() throws Exception {
        fixture.setUp(); fixture.stock.setVersion(1);
        when(headMapper.selectById(PdaIssueServiceImplTest.ISSUE_ID)).thenReturn(fixture.head);
        when(headMapper.update(isNull(), any())).thenReturn(1);
        when(headMapper.updateById(any(WmProductionIssue.class))).thenReturn(1);
        when(fixture.lineMapper.selectList(any())).thenReturn(List.of(fixture.target));
        when(warehouseMapper.selectById(PdaIssueServiceImplTest.WAREHOUSE_ID)).thenReturn(fixture.warehouse);
        when(stockMapper.selectList(any(Wrapper.class))).thenReturn(List.of(fixture.stock));
        when(stockMapper.deductStockAvailable(eq(fixture.stock.getStockId()), any(BigDecimal.class), eq(1))).thenReturn(1);
        when(valuation.issueStockCost(eq(fixture.stock.getStockId()), any(BigDecimal.class))).thenReturn(BigDecimal.ONE);
        when(batchMapper.increaseIssuedQty(anyLong(), anyLong(), anyString(), any(BigDecimal.class))).thenReturn(1);
        when(txMapper.insert(any(WmMaterialStockTx.class))).thenReturn(1);
        ProductionIssueStrategy strategy = new ProductionIssueStrategy(txMapper, stockMapper, valuation, batchMapper);
        WmStockOperationServiceImpl operations = new WmStockOperationServiceImpl(new StockOperationStrategyFactory(List.of(strategy)), strategy, null);
        WmProductionIssueServiceImpl executor = new WmProductionIssueServiceImpl(headMapper, fixture.lineMapper, stockMapper,
            warehouseMapper, operations, null, null, null, null, null);
        service = new PdaIssueServiceImpl(executor, fixture.lineMapper, fixture.configService, fixture.sourceMapper,
            new PdaIssueSourceGuard(fixture.sourceMapper, fixture.sourceDataSource));
    }
    @AfterEach void tearDown() { fixture.tearDown(); }

    @Test void scannedQcIdentityIsExactlyTheRealDeductedAndLoggedBatch() {
        assertEquals(Boolean.TRUE, service.pick(PdaIssueServiceImplTest.ISSUE_ID, fixture.bo()).getOk(), "唯一真实来源应能通过既有完整扣库链");
        var query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(stockMapper, atLeastOnce().description("预检和真实策略均须查询绑定后的库存范围")).selectList(query.capture());
        for (LambdaQueryWrapper<?> wrapper : query.getAllValues()) {
            String sql = wrapper.getSqlSegment();
            var parameters = wrapper.getParamNameValuePairs();
            assertTrue(sql.contains("batch_id ="), "真实预检和实际扣库都必须限制批次标识");
            assertTrue(sql.contains("location_id ="), "真实预检和实际扣库都必须限制库位标识");
            assertTrue(parameters.containsValue(PdaIssueServiceImplTest.BATCH_ID), "执行查询必须包含被质检的真实批次标识");
            assertTrue(parameters.containsValue(PdaIssueServiceImplTest.LOCATION_ID), "执行查询必须包含解析出的真实库位标识");
        }
        verify(stockMapper, description("只扣减来源门禁解析出的真实库存行"))
            .deductStockAvailable(fixture.stock.getStockId(), new BigDecimal("2"), 1);
        verify(batchMapper, description("批次累计必须就是质检门禁核过的真实批次"))
            .increaseIssuedQty(PdaIssueServiceImplTest.BATCH_ID, PdaIssueServiceImplTest.MATERIAL_ID,
                PdaIssueServiceImplTest.TENANT, new BigDecimal("2"));
        var transaction = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(txMapper, description("真实策略应写一条对应来源的流水")).insert(transaction.capture());
        assertEquals(PdaIssueServiceImplTest.BATCH_ID, transaction.getValue().getBatchId(), "流水必须追溯被质检的真实批次");
        assertEquals(PdaIssueServiceImplTest.LOCATION_ID, transaction.getValue().getLocationId(), "流水必须追溯实际库位");
        assertEquals(PdaIssueServiceImplTest.WAREHOUSE_ID, transaction.getValue().getWarehouseId(), "流水必须追溯有效头仓库");
        assertEquals(new BigDecimal("2"), fixture.target.getIssuedQty(), "既有引擎仍累计已领数量");
        assertEquals(BigDecimal.ZERO, fixture.target.getCurrentQty(), "既有引擎仍在过账后清零本次量");
    }
    @Test void mismatchedScanCannotReachAnyRealStockOrBatchWrite() {
        fixture.target.setBatchId(701L); fixture.target.setBatchCode("批次A");
        assertEquals(Boolean.FALSE, service.pick(PdaIssueServiceImplTest.ISSUE_ID, fixture.bo()).getOk(), "A 分配加 B 扫码必须在真实链前拒绝");
        verifyNoInteractions(stockMapper, batchMapper, txMapper, valuation);
        verify(fixture.lineMapper, never().description("身份不符不得写行")).updateById(any(WmProductionIssueLine.class));
        verify(headMapper, never().description("身份不符不得抢占执行状态")).update(any(), any());
    }
    @Test void realNonBatchNullSourceDoesNotInventBatchOrUpdateBatchTotals() {
        fixture.material.setIsBatchManage(0L); fixture.stock.setBatchId(null); fixture.stock.setBatchCode(null);
        PdaPickBo request = fixture.bo(); request.setBatchCode(null);
        assertEquals(Boolean.TRUE, service.pick(PdaIssueServiceImplTest.ISSUE_ID, request).getOk(), "真实无批次库存可沿原链扣减");
        verifyNoInteractions(batchMapper);
        var transaction = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(txMapper, description("非批次出库仍须保留实际库存流水")).insert(transaction.capture());
        assertNull(transaction.getValue().getBatchId(), "非批次流水不能伪造批次标识");
        assertNull(fixture.target.getBatchId(), "领料行仍保留真实非批次空标识");
    }
}
