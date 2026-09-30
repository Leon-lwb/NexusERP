package com.nexus.erp.wm.strategy.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.so.mapper.SoSalesOrderLineMapper;
import com.nexus.erp.so.service.ISoFulfillmentWritebackService;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.WmSalesIssue;
import com.nexus.erp.wm.domain.WmSalesIssueLine;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.mapper.WmSalesIssueLineMapper;
import com.nexus.erp.wm.mapper.WmSalesIssueMapper;
import com.nexus.erp.wm.service.IInventoryValuationService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 出库策略「可用量守卫」与「预留消耗记账」单元测试（审计 ERP-WM-02 / ERP-WM-03 回归）
 * <p>ERP-WM-02：修复前可用量只用于「选行」，扣减上限却按 {@code stock_quantity} 计算，
 * 生产领料/杂项出库/库位转储/手工销售出库可以扣穿其他单据的预留。</p>
 * <p>ERP-WM-03：修复前出库消耗预留只减 {@code reserved_quantity} 不写流水，
 * 使 {@code unreserve} 按历史 RESERVE 流水全额释放（释放到他人预留或抛异常阻断订单取消）。</p>
 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class WmIssueStrategyAvailableGuardTest {

    @Mock
    private WmMaterialStockMapper stockMapper;
    @Mock
    private WmMaterialStockTxMapper stockTxMapper;
    @Mock
    private IInventoryValuationService valuationService;
    @Mock
    private WmSalesIssueMapper salesIssueMapper;
    @Mock
    private WmSalesIssueLineMapper salesIssueLineMapper;
    @Mock
    private SoSalesOrderLineMapper salesOrderLineMapper;
    @Mock
    private ISoFulfillmentWritebackService fulfillmentWritebackService;

    @BeforeAll
    static void initTableInfo() {
        /** 真实 TableInfo/列缓存：让 MyBatis-Plus lambda 在纯单测（无 Spring 上下文）中能解析列名 */
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WmMaterialStock.class);
        TableInfoHelper.initTableInfo(assistant, WmMaterialStockTx.class);
        TableInfoHelper.initTableInfo(assistant, WmProductionIssueLine.class);
        TableInfoHelper.initTableInfo(assistant, WmSalesIssueLine.class);
        TableInfoHelper.initTableInfo(assistant, SoSalesOrderLine.class);
    }

    /**
     * ERP-WM-02：生产领料需求 70，库存两行分别为「在库 100 / 已预留 80（可用 20）」与
     * 「在库 50 / 已预留 0（可用 50）」。
     * <p>修复前扣减上限按账面量 → 从第一行扣 70（扣穿 80 的销售预留）；
     * 修复后按可用量 → 第一行只能扣 20、第二行扣 50，两行合计恰好满足需求。</p>
     */
    @Test
    void productionIssue_capsDeductionAtAvailableQuantity_notBookQuantity() {
        WmMaterialStock reservedRow = stockRow(9L, new BigDecimal("100"), new BigDecimal("80"), 5);
        WmMaterialStock freeRow = stockRow(10L, new BigDecimal("50"), BigDecimal.ZERO, 7);
        when(stockMapper.selectList(any())).thenReturn(List.of(reservedRow, freeRow));
        when(stockMapper.deductStockAvailable(anyLong(), any(), any())).thenReturn(1);
        when(valuationService.issueStockCost(anyLong(), any())).thenReturn(BigDecimal.TEN);

        ProductionIssueStrategy strategy =
            new ProductionIssueStrategy(stockTxMapper, stockMapper, valuationService);
        /** 需求 70 = 第一行可用量 20 + 第二行可用量 50 */
        strategy.execute(productionLine(new BigDecimal("70")), new Date());

        ArgumentCaptor<BigDecimal> firstQty = ArgumentCaptor.forClass(BigDecimal.class);
        verify(stockMapper).deductStockAvailable(eq(9L), firstQty.capture(), any());
        assertEquals(0, new BigDecimal("20").compareTo(firstQty.getValue()),
            "ERP-WM-02: 第一行只能扣可用量 20，不得扣穿 80 的预留");

        ArgumentCaptor<BigDecimal> secondQty = ArgumentCaptor.forClass(BigDecimal.class);
        verify(stockMapper).deductStockAvailable(eq(10L), secondQty.capture(), any());
        assertEquals(0, new BigDecimal("50").compareTo(secondQty.getValue()),
            "ERP-WM-02: 缺口应继续由第二行的可用量补齐");

        /** 修复前走的是账面量守卫的 deductStock，修复后必须全部走可用量守卫入口 */
        verify(stockMapper, never()).deductStock(anyLong(), any(), any());
    }

    /**
     * ERP-WM-02：手工销售出库（无 SO 行 → 无本单预留可消费）不得越过可用量。
     * <p>修复前会从「在库 100 / 已预留 80」的行上直接扣 50，把别人的预留扣走。</p>
     */
    @Test
    void manualSalesIssue_neverPiercesOtherDocumentsReservation() {
        WmMaterialStock reservedRow = stockRow(9L, new BigDecimal("100"), new BigDecimal("80"), 5);
        WmMaterialStock freeRow = stockRow(10L, new BigDecimal("50"), BigDecimal.ZERO, 7);
        when(salesIssueMapper.selectById(1L)).thenReturn(salesIssue(null, null));
        when(salesIssueLineMapper.selectList(any())).thenReturn(List.of(salesLine(new BigDecimal("50"), null)));
        when(stockMapper.selectList(any())).thenReturn(List.of(reservedRow, freeRow));
        when(stockMapper.deductStockAvailable(anyLong(), any(), any())).thenReturn(1);
        when(valuationService.issueStockCost(anyLong(), any())).thenReturn(BigDecimal.TEN);

        SalesOutStrategy strategy = new SalesOutStrategy(stockTxMapper, salesIssueMapper, salesIssueLineMapper,
            stockMapper, salesOrderLineMapper, fulfillmentWritebackService, valuationService);
        strategy.execute(1L);

        ArgumentCaptor<BigDecimal> qty = ArgumentCaptor.forClass(BigDecimal.class);
        verify(stockMapper).deductStockAvailable(eq(9L), qty.capture(), any());
        assertEquals(0, new BigDecimal("20").compareTo(qty.getValue()),
            "ERP-WM-02: 无 SO 预留的手工销售出库只能动用可用量 20");
        verify(stockMapper, never()).deductStockWithReserved(anyLong(), any(), any(), any());
    }

    /**
     * ERP-WM-03：销售出库消费 SO 预留时必须写一条 CONSUME_RESERVE 流水
     * （来源单据类型/ID 与被消耗的 RESERVE 流水一致），否则 unreserve 会全额释放他人预留。
     */
    @Test
    void salesIssue_consumingReservation_writesConsumeReserveLedger() {
        WmMaterialStock reservedRow = stockRow(9L, new BigDecimal("100"), new BigDecimal("100"), 5);
        WmSalesIssue issue = salesIssue(777L, "SO20260920001");
        WmSalesIssueLine line = salesLine(new BigDecimal("100"), 55L);
        SoSalesOrderLine soLine = new SoSalesOrderLine();
        soLine.setLineId(55L);
        soLine.setReservedQty(new BigDecimal("100"));

        when(salesIssueMapper.selectById(1L)).thenReturn(issue);
        when(salesIssueLineMapper.selectList(any())).thenReturn(List.of(line));
        when(salesOrderLineMapper.selectById(55L)).thenReturn(soLine);
        when(stockMapper.selectList(any())).thenReturn(List.of(reservedRow));
        when(stockMapper.deductStockWithReserved(eq(9L), any(), any(), any())).thenReturn(1);
        when(valuationService.issueStockCost(anyLong(), any())).thenReturn(BigDecimal.TEN);
        when(salesOrderLineMapper.update(isNull(), any())).thenReturn(1);

        SalesOutStrategy strategy = new SalesOutStrategy(stockTxMapper, salesIssueMapper, salesIssueLineMapper,
            stockMapper, salesOrderLineMapper, fulfillmentWritebackService, valuationService);
        strategy.execute(1L);

        ArgumentCaptor<WmMaterialStockTx> captor = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(stockTxMapper, times(2)).insert(captor.capture());
        WmMaterialStockTx consumeTx = captor.getAllValues().stream()
            .filter(tx -> "CONSUME_RESERVE".equals(tx.getTxType()))
            .findFirst()
            .orElse(null);
        assertNotNull(consumeTx, "ERP-WM-03: 出库消耗预留必须写 CONSUME_RESERVE 流水");
        assertEquals("SALES_ORDER", consumeTx.getSourceDocType(), "来源单据类型必须与 RESERVE 流水一致");
        assertEquals(777L, consumeTx.getSourceDocId(), "来源单据ID必须与被消耗的 RESERVE 流水一致");
        assertEquals(0, new BigDecimal("100").compareTo(consumeTx.getTxQuantity()));
        assertEquals(0, Integer.valueOf(0).compareTo(consumeTx.getTxFlag()), "预留消耗不改变在库数量, txFlag 应为 0");
        assertTrue(consumeTx.getRemark() != null && consumeTx.getRemark().contains("消耗预留"));
    }

    private WmProductionIssueLine productionLine(BigDecimal qty) {
        WmProductionIssueLine line = new WmProductionIssueLine();
        line.setIssueId(1L);
        line.setLineId(2L);
        line.setMaterialId(100L);
        line.setMaterialName("测试物料");
        line.setWarehouseId(5L);
        line.setQuantity(qty);
        return line;
    }

    private WmSalesIssue salesIssue(Long soId, String soNo) {
        WmSalesIssue issue = new WmSalesIssue();
        issue.setIssueId(1L);
        issue.setIssueCode("XSCK20260920001");
        issue.setWarehouseId(5L);
        issue.setIssueDate(new Date());
        issue.setSoId(soId);
        issue.setSoNo(soNo);
        return issue;
    }

    private WmSalesIssueLine salesLine(BigDecimal qty, Long sourceDocLineId) {
        WmSalesIssueLine line = new WmSalesIssueLine();
        line.setIssueId(1L);
        line.setLineId(2L);
        line.setMaterialId(100L);
        line.setMaterialName("测试物料");
        line.setQuantityIssue(qty);
        line.setSourceDocLineId(sourceDocLineId);
        return line;
    }

    private WmMaterialStock stockRow(Long stockId, BigDecimal qty, BigDecimal reserved, int version) {
        WmMaterialStock stock = new WmMaterialStock();
        stock.setStockId(stockId);
        stock.setMaterialId(100L);
        stock.setMaterialName("测试物料");
        stock.setStockQuantity(qty);
        stock.setReservedQuantity(reserved);
        stock.setFrozenFlag(0L);
        stock.setVersion(version);
        return stock;
    }
}
