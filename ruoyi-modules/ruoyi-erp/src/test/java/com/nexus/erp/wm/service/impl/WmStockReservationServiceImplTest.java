package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预留释放（unreserve）净额化 单元测试（审计 ERP-WM-03 回归）
 * <p><b>修复前会失败</b>：{@code unreserve} 按历史 {@code RESERVE} 流水<b>全额</b>释放，
 * 而出库消耗预留时只减 {@code reserved_quantity} 不写流水，于是「已出库的订单再取消/关闭」
 * 会把同一库存行上其他单据的预留释放掉（可用量虚增/超卖），或在预留不足时抛异常阻断取消/关闭。</p>
 * <p>修复后按 {@code SUM(RESERVE) - SUM(CONSUME_RESERVE) - SUM(UNRESERVE)} 净额释放，且可重复调用（幂等）。</p>
 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class WmStockReservationServiceImplTest {

    @Mock
    private WmMaterialStockMapper stockMapper;
    @Mock
    private WmMaterialStockTxMapper txMapper;

    @InjectMocks
    private WmStockReservationServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WmMaterialStock.class);
        TableInfoHelper.initTableInfo(assistant, WmMaterialStockTx.class);
    }

    /** 预留 100 已全部随出库消耗 → 释放量必须为 0，且不得触碰库存行的 reserved_quantity */
    @Test
    void unreserve_afterFullConsumption_releasesNothing() {
        when(txMapper.selectList(any())).thenReturn(List.of(
            tx(9L, "RESERVE", new BigDecimal("100")),
            tx(9L, "CONSUME_RESERVE", new BigDecimal("100"))));

        BigDecimal released = service.unreserve("SALES_ORDER", 777L, null);

        assertEquals(0, BigDecimal.ZERO.compareTo(released), "ERP-WM-03: 已消耗完的预留不得再释放");
        verify(stockMapper, never()).releaseReserved(anyLong(), any(), any());
        verify(stockMapper, never()).selectById(any());
        verify(txMapper, never()).insert(any(WmMaterialStockTx.class));
    }

    /** 预留 100 已消耗 60 → 只释放剩余 40，并写一条 UNRESERVE 流水 */
    @Test
    void unreserve_afterPartialConsumption_releasesOnlyRemainder() {
        when(txMapper.selectList(any())).thenReturn(List.of(
            tx(9L, "RESERVE", new BigDecimal("100")),
            tx(9L, "CONSUME_RESERVE", new BigDecimal("60"))));
        WmMaterialStock stock = new WmMaterialStock();
        stock.setStockId(9L);
        stock.setStockQuantity(new BigDecimal("100"));
        stock.setReservedQuantity(new BigDecimal("40"));
        stock.setVersion(3);
        when(stockMapper.selectById(9L)).thenReturn(stock);
        when(stockMapper.releaseReserved(anyLong(), any(), any())).thenReturn(1);
        when(txMapper.insert(any(WmMaterialStockTx.class))).thenReturn(1);

        BigDecimal released = service.unreserve("SALES_ORDER", 777L, null);

        assertEquals(0, new BigDecimal("40").compareTo(released), "只释放未消耗的 40");

        ArgumentCaptor<BigDecimal> qty = ArgumentCaptor.forClass(BigDecimal.class);
        verify(stockMapper).releaseReserved(eq(9L), qty.capture(), eq(3));
        assertEquals(0, new BigDecimal("40").compareTo(qty.getValue()), "释放数量必须是净额 40 而非历史 100");

        ArgumentCaptor<WmMaterialStockTx> txCaptor = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(txMapper).insert(txCaptor.capture());
        assertEquals("UNRESERVE", txCaptor.getValue().getTxType());
    }

    /** 重复调用幂等：已释放过的单据再次 unreserve 不再重复释放 */
    @Test
    void unreserve_isIdempotent_whenAlreadyReleased() {
        when(txMapper.selectList(any())).thenReturn(List.of(
            tx(9L, "RESERVE", new BigDecimal("100")),
            tx(9L, "UNRESERVE", new BigDecimal("100"))));

        BigDecimal released = service.unreserve("SALES_ORDER", 777L, null);

        assertEquals(0, BigDecimal.ZERO.compareTo(released));
        verify(stockMapper, never()).releaseReserved(anyLong(), any(), any());
    }

    /** 多库存行各自按净额释放，互不影响（防止串行释放到其他库存行的预留） */
    @Test
    void unreserve_netPerStockRow() {
        when(txMapper.selectList(any())).thenReturn(List.of(
            tx(9L, "RESERVE", new BigDecimal("100")),
            tx(10L, "RESERVE", new BigDecimal("50")),
            tx(9L, "CONSUME_RESERVE", new BigDecimal("100"))));
        WmMaterialStock stock10 = new WmMaterialStock();
        stock10.setStockId(10L);
        stock10.setStockQuantity(new BigDecimal("50"));
        stock10.setReservedQuantity(new BigDecimal("50"));
        stock10.setVersion(1);
        when(stockMapper.selectById(10L)).thenReturn(stock10);
        when(stockMapper.releaseReserved(anyLong(), any(), any())).thenReturn(1);
        when(txMapper.insert(any(WmMaterialStockTx.class))).thenReturn(1);

        BigDecimal released = service.unreserve("SALES_ORDER", 777L, null);

        assertEquals(0, new BigDecimal("50").compareTo(released));
        verify(stockMapper, never()).selectById(9L);
        verify(stockMapper).releaseReserved(eq(10L), any(), eq(1));
    }

    private WmMaterialStockTx tx(Long stockId, String txType, BigDecimal qty) {
        WmMaterialStockTx tx = new WmMaterialStockTx();
        tx.setMaterialStockId(stockId);
        tx.setTxType(txType);
        tx.setTxQuantity(qty);
        return tx;
    }
}
