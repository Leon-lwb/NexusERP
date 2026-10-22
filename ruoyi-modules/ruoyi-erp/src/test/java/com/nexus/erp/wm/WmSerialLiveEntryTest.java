package com.nexus.erp.wm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.erp.pu.domain.*;
import com.nexus.erp.pu.service.impl.PuPurchaseReturnServiceImpl;
import com.nexus.erp.so.domain.*;
import com.nexus.erp.so.service.impl.SoReturnServiceImpl;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.domain.bo.WmMaterialStockBo;
import com.nexus.erp.wm.domain.bo.WmMaterialStockTxBo;
import com.nexus.erp.wm.mapper.*;
import com.nexus.erp.wm.service.*;
import com.nexus.erp.wm.service.impl.*;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

class WmSerialLiveEntryTest {
    @BeforeAll
    static void metadata() {
        var a = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        for (var type :
                List.of(
                        WmSalesIssue.class,
                        WmSalesIssueLine.class,
                        WmMaterialStock.class,
                        WmMaterialStockTx.class)) {
            TableInfoHelper.initTableInfo(a, type);
        }
    }

    @Test
    void completeSalesIssueDispatchesSerialPostingAfterItsRealSourceGuard() {
        var service = mock(WmSalesIssueServiceImpl.class, CALLS_REAL_METHODS);
        var headers = mock(WmSalesIssueMapper.class);
        var lines = mock(WmSalesIssueLineMapper.class);
        var stocks = mock(WmMaterialStockMapper.class);
        var txs = mock(WmMaterialStockTxMapper.class);
        var guard = mock(SalesIssueSourceGuard.class);
        var serial = mock(WmSerialInventoryService.class);
        ReflectionTestUtils.setField(service, "baseMapper", headers);
        ReflectionTestUtils.setField(service, "lineMapper", lines);
        ReflectionTestUtils.setField(service, "wmMaterialStockMapper", stocks);
        ReflectionTestUtils.setField(service, "stockTxMapper", txs);
        ReflectionTestUtils.setField(service, "sourceGuard", guard);
        ReflectionTestUtils.setField(service, "serialInventory", serial);
        WmSalesIssue head = new WmSalesIssue();
        head.setIssueId(1L);
        head.setTenantId("t1");
        head.setStatus("CONFIRMED");
        head.setWarehouseId(30L);
        WmSalesIssueLine line = new WmSalesIssueLine();
        line.setIssueId(1L);
        line.setLineId(2L);
        line.setMaterialId(20L);
        line.setQuantityIssue(BigDecimal.ONE);
        when(headers.selectById(1L)).thenReturn(head);
        when(headers.update(isNull(), any())).thenReturn(1);
        when(headers.updateById(any(WmSalesIssue.class))).thenReturn(1);
        when(txs.selectCount(any())).thenReturn(0L);
        when(lines.selectList(any())).thenReturn(List.of(line));
        when(guard.validateAndLock(head, List.of(line))).thenReturn(Map.of());
        when(serial.post(any())).thenReturn(true);
        when(serial.consumedReservation(any())).thenReturn(BigDecimal.ZERO);
        assertEquals(1, service.completeSalesIssue(1L));
        var order = inOrder(guard, serial);
        order.verify(guard).validateAndLock(head, List.of(line));
        order.verify(serial).post(any());
        verifyNoInteractions(stocks);
        assertEquals("COMPLETED", head.getStatus());
    }

    @Test
    void salesReturnActualWriterUsesSerialEffects() {
        var service = mock(SoReturnServiceImpl.class, CALLS_REAL_METHODS);
        var serial = mock(WmSerialInventoryService.class);
        ReflectionTestUtils.setField(service, "serialInventory", serial);
        when(serial.post(any())).thenReturn(true);
        SoReturn head = new SoReturn();
        head.setReturnId(1L);
        head.setWarehouseId(30L);
        SoReturnLine line = new SoReturnLine();
        line.setLineId(2L);
        line.setMaterialId(20L);
        line.setReturnQuantity(BigDecimal.ONE);
        ReflectionTestUtils.invokeMethod(service, "postReturnStock", head, line, new Date());
        verify(serial)
                .post(
                        argThat(
                                r ->
                                        r.sourceType().equals("SO_RETURN")
                                                && r.direction() == 1
                                                && r.sourceLineId() == 2L));
    }

    @Test
    void purchaseReturnActualWriterUsesSerialEffects() {
        var service = mock(PuPurchaseReturnServiceImpl.class, CALLS_REAL_METHODS);
        var serial = mock(WmSerialInventoryService.class);
        ReflectionTestUtils.setField(service, "serialInventory", serial);
        when(serial.post(any())).thenReturn(true);
        PuPurchaseReturn head = new PuPurchaseReturn();
        head.setReturnId(1L);
        PuPurchaseReturnLine line = new PuPurchaseReturnLine();
        line.setLineId(2L);
        line.setMaterialId(20L);
        line.setReturnQuantity(BigDecimal.ONE);
        ReflectionTestUtils.invokeMethod(service, "postReturnOut", head, line, new Date());
        verify(serial)
                .post(
                        argThat(
                                r ->
                                        r.sourceType().equals("PU_PURCHASE_RETURN")
                                                && r.direction() == -1));
    }

    @Test
    void genericInventoryCrudCannotCreateOrRewritePostedFacts() {
        var stock = new WmMaterialStockServiceImpl(null, null);
        var tx = new WmMaterialStockTxServiceImpl();
        assertThrows(RuntimeException.class, () -> stock.insertByBo(new WmMaterialStockBo()));
        assertThrows(RuntimeException.class, () -> stock.updateByBo(new WmMaterialStockBo()));
        assertThrows(RuntimeException.class, () -> stock.deleteByIds(new Long[] {1L}));
        assertThrows(RuntimeException.class, () -> tx.insertByBo(new WmMaterialStockTxBo()));
        assertThrows(RuntimeException.class, () -> tx.updateByBo(new WmMaterialStockTxBo()));
        assertThrows(RuntimeException.class, () -> tx.deleteByIds(new Long[] {1L}));
    }
}
