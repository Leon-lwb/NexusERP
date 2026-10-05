package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.so.domain.SoSalesOrderHeader;
import com.nexus.erp.so.domain.SoSalesOrderLine;
import com.nexus.erp.wm.domain.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 实际销售出库入口回归；固定 SQL 和内存事务代理证据不等同物理 MySQL 验收。 */
class WmSalesIssueEntryGuardTest {
    @BeforeAll
    static void initTables() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (Class<?> type : List.of(WmSalesIssue.class, WmSalesIssueLine.class, WmMaterialStock.class,
            WmMaterialStockTx.class, SoSalesOrderHeader.class, SoSalesOrderLine.class)) {
            TableInfoHelper.initTableInfo(assistant, type);
        }
    }

    @Test
    void linkedOrderShipsAllTenWithActualCostAndBatchConservation() {
        var f = new SalesIssueEntryFixture();
        assertEquals(1, f.complete(1L));
        assertDecimal("0", f.stocks.get(9L).getStockQuantity());
        assertDecimal("0", f.stocks.get(9L).getTotalCost());
        assertDecimal("10", f.batch.getIssuedQty());
        assertDecimal("10", f.sources.get(21L).getDeliveredQty());
        assertDecimal("10", f.sources.get(21L).getFulfilledQty());
        var tx = salesTransactions(f).get(0);
        assertDecimal("3.89", tx.getUnitCost());
        assertEquals(new BigDecimal("38.9000"), tx.getTxAmount());
        assertEquals("COMPLETED", f.issues.get(1L).getStatus());
        verify(f.batchMapper).increaseIssuedQty(90L, 100L, SalesIssueEntryFixture.TENANT, new BigDecimal("10"));
        int writes = f.transactions.size();
        assertThrows(ServiceException.class, () -> f.complete(1L));
        assertEquals(writes, f.transactions.size(), "再次执行不得重复扣库或记账");
    }

    @Test
    void splitDetailsAggregateOneSourceAndConsumeTheSameLockedReservation() {
        var f = new SalesIssueEntryFixture();
        f.details.put(1L, new ArrayList<>(List.of(f.detail(1L, 101L, "4"), f.detail(1L, 102L, "6"))));
        f.sources.get(21L).setReservedQty(new BigDecimal("10"));
        f.stocks.get(9L).setReservedQuantity(new BigDecimal("10"));
        assertEquals(1, f.complete(1L));
        assertDecimal("10", f.sources.get(21L).getDeliveredQty());
        assertDecimal("0", f.sources.get(21L).getReservedQty());
        assertDecimal("0", f.stocks.get(9L).getReservedQuantity());
        assertDecimal("10", f.batch.getIssuedQty());
        assertEquals(2, salesTransactions(f).size());
        assertEquals(2, f.transactions.stream().filter(t -> "CONSUME_RESERVE".equals(t.getTxType())).count());
        verify(f.soLinesMapper, never()).selectById(any());
    }

    @Test
    void rejectsCombinedQuantityAboveOneSourcesRemainingBeforeAnyDeduction() {
        var f = new SalesIssueEntryFixture();
        f.details.put(1L, new ArrayList<>(List.of(f.detail(1L, 101L, "6"), f.detail(1L, 102L, "6"))));
        f.stocks.put(9L, f.stock(9L, "20", "3.89"));
        f.batch.setReceivedQty(new BigDecimal("20"));
        assertThrows(ServiceException.class, () -> f.complete(1L));
        assertFalse(f.events.contains("扣库"), "同一来源行必须先合计，而非逐行扣库后才发现超发");
        assertDecimal("20", f.stocks.get(9L).getStockQuantity());
        assertEquals("CONFIRMED", f.issues.get(1L).getStatus());
    }

    @ParameterizedTest(name = "扣库前门禁：{0}")
    @ValueSource(strings = {"订单未确认", "订单取消", "客户不符", "客户为空", "出库租户不符", "订单租户不符",
        "订单删除", "订单缺失", "来源缺失", "来源其他订单", "来源租户不符", "来源物料不符", "来源删除",
        "来源取消", "质检待检", "质检不合格", "质检为空", "已有发货超剩余", "数量为空", "数量零", "数量负数",
        "明细租户不符", "明细其他出库", "明细删除", "明细物料为空", "明细行号为空", "明细空对象"})
    void rejectsInvalidLinkedSourceAndEveryDetailBeforeDeduction(String scenario) {
        var f = new SalesIssueEntryFixture();
        var source = f.sources.get(21L);
        var invalid = f.detail(1L, 102L, "5");
        f.details.put(1L, new ArrayList<>(List.of(f.detail(1L, 101L, "5"), invalid)));
        switch (scenario) {
            case "订单未确认" -> f.header.setOrderStatus("DRAFT");
            case "订单取消" -> f.header.setOrderStatus("CANCELLED");
            case "客户不符" -> f.issues.get(1L).setClientId(3L);
            case "客户为空" -> f.issues.get(1L).setClientId(null);
            case "出库租户不符" -> f.issues.get(1L).setTenantId("其他租户");
            case "订单租户不符" -> f.header.setTenantId("其他租户");
            case "订单删除" -> f.header.setDelFlag("1");
            case "订单缺失" -> f.header.setSoId(999L);
            case "来源缺失" -> invalid.setSourceDocLineId(999L);
            case "来源其他订单" -> source.setSoId(8L);
            case "来源租户不符" -> source.setTenantId("其他租户");
            case "来源物料不符" -> source.setMaterialId(999L);
            case "来源删除" -> source.setDelFlag("1");
            case "来源取消" -> source.setLineStatus("CANCELLED");
            case "质检待检" -> source.setOqcStatus("PENDING");
            case "质检不合格" -> source.setOqcStatus("UNQUALIFIED");
            case "质检为空" -> source.setOqcStatus(null);
            case "已有发货超剩余" -> source.setDeliveredQty(BigDecimal.ONE);
            case "数量为空" -> invalid.setQuantityIssue(null);
            case "数量零" -> invalid.setQuantityIssue(BigDecimal.ZERO);
            case "数量负数" -> invalid.setQuantityIssue(BigDecimal.ONE.negate());
            case "明细租户不符" -> invalid.setTenantId("其他租户");
            case "明细其他出库" -> invalid.setIssueId(2L);
            case "明细删除" -> invalid.setDelFlag("1");
            case "明细物料为空" -> invalid.setMaterialId(null);
            case "明细行号为空" -> invalid.setLineId(null);
            case "明细空对象" -> f.details.get(1L).set(1, null);
            default -> throw new AssertionError("未定义门禁场景");
        }
        assertThrows(ServiceException.class, () -> f.complete(1L), scenario);
        assertFalse(f.events.contains("扣库"), "所有明细和来源必须在真正扣库之前完成校验：" + scenario);
        assertDecimal("10", f.stocks.get(9L).getStockQuantity());
        assertDecimal("0", f.batch.getIssuedQty());
        assertTrue(f.transactions.isEmpty());
        assertEquals("CONFIRMED", f.issues.get(1L).getStatus());
    }

    @Test
    void locksHeaderThenAllDistinctSourcesInIdOrderBeforeStockRead() {
        var f = new SalesIssueEntryFixture();
        f.sources.put(22L, f.source(22L, "10"));
        var laterId = f.detail(1L, 101L, "5");
        laterId.setSourceDocLineId(22L);
        f.details.put(1L, new ArrayList<>(List.of(laterId, f.detail(1L, 102L, "5"))));
        assertEquals(1, f.complete(1L));
        verify(f.sourceMapper).lockHeader(SalesIssueEntryFixture.TENANT, 7L);
        verify(f.sourceMapper).lockLines(SalesIssueEntryFixture.TENANT, 7L, List.of(21L, 22L));
        assertTrue(f.events.indexOf("锁头") < f.events.indexOf("锁行:[21, 22]"));
        assertTrue(f.events.indexOf("锁行:[21, 22]") < f.events.indexOf("查询库存"));
        verify(f.soLinesMapper, never()).selectById(any());
    }

    @Test
    void usesOrderedMinusAlreadyDeliveredForLegalRemainingShipment() {
        var f = new SalesIssueEntryFixture();
        f.sources.get(21L).setDeliveredQty(new BigDecimal("6"));
        f.sources.get(21L).setFulfilledQty(new BigDecimal("6"));
        f.details.get(1L).get(0).setQuantityIssue(new BigDecimal("4"));
        assertEquals(1, f.complete(1L));
        assertDecimal("10", f.sources.get(21L).getDeliveredQty());
        assertDecimal("4", f.batch.getIssuedQty());
    }

    @Test
    void rejectsOriginalNullDeliveredQuantityBeforeStockReadWithoutGuessingZero() {
        var f = new SalesIssueEntryFixture();
        var source = f.sources.get(21L);
        source.setDeliveredQty(null);
        // 原相对写回中空值加数量仍为空；只模拟该 SQL 边界，避免夹具误报 Java 空指针。
        doAnswer(call -> {
            BigDecimal beforeFulfilled = source.getFulfilledQty();
            f.manager.undo(() -> source.setFulfilledQty(beforeFulfilled));
            source.setFulfilledQty(beforeFulfilled.add(call.getArgument(1)));
            return null;
        }).when(f.writeback).addShippedQty(eq(21L), any());

        var error = assertThrows(ServiceException.class, () -> f.complete(1L));
        assertTrue(error.getMessage().contains("已发货数量为空"));
        assertFalse(f.events.contains("查询库存"), "空已发数量必须在真正查询和扣减库存前拒绝");
        assertNull(source.getDeliveredQty(), "不得猜零或修补历史来源行");
        assertDecimal("0", source.getFulfilledQty());
        assertDecimal("10", f.stocks.get(9L).getStockQuantity());
        assertDecimal("38.90", f.stocks.get(9L).getTotalCost());
        assertDecimal("0", f.batch.getIssuedQty());
        assertTrue(f.transactions.isEmpty());
        assertEquals("CONFIRMED", f.issues.get(1L).getStatus(), "拒绝必须回滚原事务认领状态");
        verifyNoInteractions(f.writeback);
    }

    @Test
    void actualValuationResultIsRoundedHalfUpToFourPlacesInSalesLedger() {
        var f = new SalesIssueEntryFixture();
        f.stocks.put(9L, f.stock(9L, "2", "1.23445"));
        f.details.get(1L).get(0).setQuantityIssue(BigDecimal.ONE);
        assertEquals(1, f.complete(1L));
        var tx = salesTransactions(f).get(0);
        assertEquals(new BigDecimal("1.23445"), tx.getUnitCost());
        assertEquals(new BigDecimal("1.2345"), tx.getTxAmount(), "必须使用实际计价返回值并按四位 HALF_UP 落账");
    }

    @ParameterizedTest(name = "原事务回滚：{0}")
    @ValueSource(strings = {"批次零行", "批次多行", "批次租户不符", "批次物料不符", "批次删除", "批次已发上限",
        "销售流水零行", "销售流水多行", "预留流水零行", "预留流水多行"})
    void failedBatchOrLedgerWriteRollsBackTheOriginalEntryTransaction(String scenario) {
        var f = new SalesIssueEntryFixture();
        f.sources.get(21L).setReservedQty(new BigDecimal("10"));
        f.stocks.get(9L).setReservedQuantity(new BigDecimal("10"));
        switch (scenario) {
            case "批次零行" -> f.batchRows = 0;
            case "批次多行" -> f.batchRows = 2;
            case "批次租户不符" -> f.batch.setTenantId("其他租户");
            case "批次物料不符" -> f.batch.setMaterialId(999L);
            case "批次删除" -> f.batch.setDelFlag("1");
            case "批次已发上限" -> f.batch.setReceivedQty(new BigDecimal("9"));
            case "销售流水零行" -> f.salesTxRows = 0;
            case "销售流水多行" -> f.salesTxRows = 2;
            case "预留流水零行" -> f.reserveTxRows = 0;
            case "预留流水多行" -> f.reserveTxRows = 2;
            default -> throw new AssertionError("未定义回滚场景");
        }
        assertThrows(ServiceException.class, () -> f.complete(1L));
        assertTrue(f.events.contains("扣库"), "夹具必须确实执行过扣库再触发失败");
        assertDecimal("10", f.stocks.get(9L).getStockQuantity());
        assertDecimal("10", f.stocks.get(9L).getReservedQuantity());
        assertDecimal("38.90", f.stocks.get(9L).getTotalCost());
        assertDecimal("0", f.batch.getIssuedQty());
        assertDecimal("0", f.sources.get(21L).getDeliveredQty());
        assertDecimal("10", f.sources.get(21L).getReservedQty());
        assertTrue(f.transactions.isEmpty(), "失败事务不得留下新流水");
        assertEquals("CONFIRMED", f.issues.get(1L).getStatus(), "认领状态必须随原事务撤销");
    }

    @Test
    void rejectsPreviouslyPostedButNotCompletedBeforeClaimingAndDoesNotRepairHistory() {
        var f = new SalesIssueEntryFixture();
        f.existingTxCount = 1L;
        var ex = assertThrows(ServiceException.class, () -> f.complete(1L));
        assertTrue(ex.getMessage().contains("对账"));
        assertFalse(f.events.contains("认领:1"), "半过账不应先置为执行中再假成功");
        assertFalse(f.events.contains("扣库"));
        assertEquals("CONFIRMED", f.issues.get(1L).getStatus());
        assertTrue(f.transactions.isEmpty());
    }

    @Test
    void independentLegacyIssueAndNullStockBatchRemainCompatible() {
        var f = new SalesIssueEntryFixture();
        f.issues.get(1L).setSoId(null);
        f.details.get(1L).get(0).setSourceDocLineId(null);
        f.stocks.get(9L).setBatchId(null);
        assertEquals(1, f.complete(1L));
        assertDecimal("0", f.stocks.get(9L).getStockQuantity());
        assertEquals(new BigDecimal("38.9000"), salesTransactions(f).get(0).getTxAmount());
        verifyNoInteractions(f.batchMapper, f.writeback);
    }

    @ParameterizedTest(name = "独立单拒绝：{0}")
    @ValueSource(strings = {"冒用来源", "非正明细", "无效明细"})
    void independentIssueCannotImpersonateSourceOrSkipInvalidDetails(String scenario) {
        var f = new SalesIssueEntryFixture();
        f.issues.get(1L).setSoId(null);
        if (!"冒用来源".equals(scenario)) f.details.get(1L).get(0).setSourceDocLineId(null);
        if ("非正明细".equals(scenario)) f.details.get(1L).get(0).setQuantityIssue(BigDecimal.ZERO);
        if ("无效明细".equals(scenario)) f.details.get(1L).get(0).setMaterialId(null);
        assertThrows(ServiceException.class, () -> f.complete(1L));
        assertFalse(f.events.contains("扣库"));
        assertEquals("CONFIRMED", f.issues.get(1L).getStatus());
    }

    @Test
    void concurrentDifferentIssuesOnSameOrderCannotOverShipEvenWithDifferentStockRows() throws Exception {
        var f = new SalesIssueEntryFixture();
        f.details.get(1L).get(0).setQuantityIssue(new BigDecimal("6"));
        f.addIssue(2L, "6").setWarehouseId(6L);
        var otherStock = f.stock(10L, "10", "3.89");
        otherStock.setWarehouseId(6L);
        f.stocks.put(10L, otherStock);
        f.batch.setReceivedQty(new BigDecimal("20"));
        f.entryBarrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one = executor.submit(() -> executeOrReject(f, 1L));
            var two = executor.submit(() -> executeOrReject(f, 2L));
            List<Boolean> results = List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(Boolean::booleanValue).count(), "同一订单应仅有一次六件发货通过，另一单须按最新剩余四件拒绝");
            assertDecimal("6", f.sources.get(21L).getDeliveredQty());
            assertDecimal("6", f.batch.getIssuedQty());
            assertEquals(1, salesTransactions(f).size());
            assertEquals(1, f.issues.values().stream().filter(i -> "CONFIRMED".equals(i.getStatus())).count());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static boolean executeOrReject(SalesIssueEntryFixture f, long id) {
        try { f.complete(id); return true; }
        catch (ServiceException ex) { return false; }
    }

    static List<WmMaterialStockTx> salesTransactions(SalesIssueEntryFixture f) {
        return f.transactions.stream().filter(tx -> "SALES_OUT".equals(tx.getTxType())).toList();
    }

    static void assertDecimal(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
