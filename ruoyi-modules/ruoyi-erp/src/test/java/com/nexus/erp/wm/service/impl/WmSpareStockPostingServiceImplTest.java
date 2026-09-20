package com.nexus.erp.wm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.erp.wm.mapper.WmMaterialStockTxMapper;
import com.nexus.erp.wm.service.IWmSpareStockPostingService;
import com.nexus.erp.wm.service.mq.MesSpareErpMessage;
import com.nexus.erp.wm.service.mq.WmSpareStockDedupGuard;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MES 备件 → ERP 仓储过账服务单测（MES-EQ-04）。
 * <p>核心断言：① 重复消息幂等（Redis 占位命中 / 流水已存在都不再动库存）；
 * ② 出库只按「可用量 = 在库 − 已预留」扣减，不扣穿他人预留；③ 可用量不足时整单回滚（不写流水、释放幂等占位）；
 * ④ 入库新建库存行必须 {@code version = 0}。</p>
 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class WmSpareStockPostingServiceImplTest {

    @Mock
    private WmMaterialStockMapper stockMapper;
    @Mock
    private WmMaterialStockTxMapper stockTxMapper;
    @Mock
    private WmSpareStockDedupGuard dedupGuard;
    @InjectMocks
    private WmSpareStockPostingServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WmMaterialStock.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WmMaterialStockTx.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(WmMaterialStock.class);
        TableInfoHelper.remove(WmMaterialStockTx.class);
    }

    @Test
    void duplicateMessageHitsRedisGuardAndDoesNotTouchStock() {
        when(dedupGuard.tryMark("EQSP:000000:1")).thenReturn(false);
        when(stockMapper.selectList(any())).thenReturn(List.of(stock(10L, "10", "0", 0L, 1)));

        IWmSpareStockPostingService.WmSpareStockPostingResult result =
            service.postSpareStock(message("EQSP:000000:1", "OUT", "3"));

        assertTrue(result.duplicated());
        verify(stockMapper, never()).deductStockAvailable(any(), any(), any());
        verify(stockMapper, never()).addStock(any(), any(), any());
        verify(stockTxMapper, never()).insert(any(WmMaterialStockTx.class));
    }

    @Test
    void lateRedeliveryIsIdempotentByLedgerLookup() {
        when(dedupGuard.tryMark(anyString())).thenReturn(true);
        when(stockTxMapper.selectCount(any())).thenReturn(1L);
        when(stockMapper.selectList(any())).thenReturn(List.of(stock(10L, "10", "0", 0L, 1)));

        IWmSpareStockPostingService.WmSpareStockPostingResult result =
            service.postSpareStock(message("EQSP:000000:2", "OUT", "3"));

        assertTrue(result.duplicated());
        verify(stockMapper, never()).deductStockAvailable(any(), any(), any());
        verify(stockTxMapper, never()).insert(any(WmMaterialStockTx.class));
    }

    @Test
    void outboundDeductsAvailableQuantityOnlyAndWritesLedger() {
        // 在库 10, 已预留 8 → 可用 2; 领用 2 只扣可用量, 不扣穿预留
        WmMaterialStock row = stock(5L, "10", "8", 0L, 3);
        when(dedupGuard.tryMark(anyString())).thenReturn(true);
        when(stockTxMapper.selectCount(any())).thenReturn(0L);
        when(stockMapper.selectList(any())).thenReturn(List.of(row));
        when(stockMapper.selectById(5L)).thenReturn(row);
        when(stockMapper.deductStockAvailable(eq(5L), eq(new BigDecimal("2")), eq(3))).thenReturn(1);

        IWmSpareStockPostingService.WmSpareStockPostingResult result =
            service.postSpareStock(message("EQSP:000000:3", "REPAIR_USE", "2"));

        assertFalse(result.duplicated());
        verify(stockMapper).deductStockAvailable(5L, new BigDecimal("2"), 3);
        ArgumentCaptor<WmMaterialStockTx> captor = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(stockTxMapper).insert(captor.capture());
        WmMaterialStockTx tx = captor.getValue();
        assertEquals("MES_SPARE_OUT", tx.getTxType());
        assertEquals(Integer.valueOf(0), tx.getTxFlag());
        assertEquals("EQSP:000000:3", tx.getSourceDocCode());
        assertEquals("MES_SPARE", tx.getSourceDocType());
        assertEquals(0, new BigDecimal("10").compareTo(tx.getBeforeQuantity()));
        assertEquals(0, new BigDecimal("8").compareTo(tx.getAfterQuantity()));
    }

    @Test
    void outboundRejectsWhenErpAvailableInsufficientAndRollsBack() {
        // 在库 10, 已预留 9 → 可用 1, 领用 5 必须整单拒绝
        WmMaterialStock row = stock(5L, "10", "9", 0L, 1);
        when(dedupGuard.tryMark(anyString())).thenReturn(true);
        when(stockTxMapper.selectCount(any())).thenReturn(0L);
        when(stockMapper.selectList(any())).thenReturn(List.of(row));
        when(stockMapper.selectById(5L)).thenReturn(row);
        when(stockMapper.deductStockAvailable(eq(5L), eq(new BigDecimal("1")), eq(1))).thenReturn(1);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.postSpareStock(message("EQSP:000000:4", "OUT", "5")));

        assertTrue(exception.getMessage().contains("可用库存不足"));
        verify(stockTxMapper, never()).insert(any(WmMaterialStockTx.class));
        // 事务回滚必须释放幂等占位, 否则重投被误判为已过账而永久丢失
        verify(dedupGuard).release("EQSP:000000:4");
    }

    @Test
    void outboundRejectsWhenNoAvailableRowAtAll() {
        when(dedupGuard.tryMark(anyString())).thenReturn(true);
        when(stockTxMapper.selectCount(any())).thenReturn(0L);
        when(stockMapper.selectList(any())).thenReturn(Collections.emptyList());

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.postSpareStock(message("EQSP:000000:5", "OUT", "1")));

        assertTrue(exception.getMessage().contains("无可用量"));
        verify(dedupGuard).release("EQSP:000000:5");
    }

    @Test
    void inboundCreatesStockRowWithVersionZero() {
        when(dedupGuard.tryMark(anyString())).thenReturn(true);
        when(stockTxMapper.selectCount(any())).thenReturn(0L);
        when(stockMapper.selectList(any())).thenReturn(Collections.emptyList());

        service.postSpareStock(message("EQSP:000000:6", "IN", "4"));

        ArgumentCaptor<WmMaterialStock> captor = ArgumentCaptor.forClass(WmMaterialStock.class);
        verify(stockMapper).insert(captor.capture());
        WmMaterialStock created = captor.getValue();
        // 新建库存行必须显式 version=0, 否则乐观锁失效
        assertEquals(Integer.valueOf(0), created.getVersion());
        assertEquals(0, new BigDecimal("4").compareTo(created.getStockQuantity()));
        assertEquals(Long.valueOf(0L), created.getFrozenFlag());
        ArgumentCaptor<WmMaterialStockTx> txCaptor = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(stockTxMapper).insert(txCaptor.capture());
        assertEquals("MES_SPARE_IN", txCaptor.getValue().getTxType());
        assertEquals(Integer.valueOf(1), txCaptor.getValue().getTxFlag());
    }

    @Test
    void returnTransactionIsPostedAsInbound() {
        WmMaterialStock row = stock(7L, "10", "0", 0L, 2);
        when(dedupGuard.tryMark(anyString())).thenReturn(true);
        when(stockTxMapper.selectCount(any())).thenReturn(0L);
        when(stockMapper.selectList(any())).thenReturn(List.of(row));
        when(stockMapper.selectById(7L)).thenReturn(row);
        when(stockMapper.addStock(eq(7L), eq(new BigDecimal("2")), eq(2))).thenReturn(1);

        service.postSpareStock(message("EQSP:000000:7", "RETURN", "2"));

        verify(stockMapper).addStock(7L, new BigDecimal("2"), 2);
        ArgumentCaptor<WmMaterialStockTx> captor = ArgumentCaptor.forClass(WmMaterialStockTx.class);
        verify(stockTxMapper).insert(captor.capture());
        assertEquals("MES_SPARE_IN", captor.getValue().getTxType());
        assertEquals(0, new BigDecimal("12").compareTo(captor.getValue().getAfterQuantity()));
    }

    private WmMaterialStock stock(Long stockId, String stockQty, String reservedQty, Long frozenFlag, Integer version) {
        WmMaterialStock stock = new WmMaterialStock();
        stock.setStockId(stockId);
        stock.setMaterialId(1L);
        stock.setWarehouseId(2L);
        stock.setMaterialCode("M-1");
        stock.setMaterialName("物料1");
        stock.setStockQuantity(new BigDecimal(stockQty));
        stock.setReservedQuantity(new BigDecimal(reservedQty));
        stock.setFrozenFlag(frozenFlag);
        stock.setVersion(version);
        stock.setUnitCost(BigDecimal.ZERO);
        stock.setTotalCost(BigDecimal.ZERO);
        return stock;
    }

    private MesSpareErpMessage message(String bizKey, String txnType, String qty) {
        MesSpareErpMessage message = new MesSpareErpMessage();
        message.setBizType(MesSpareErpMessage.BIZ_STOCK);
        message.setTenantId("000000");
        message.setBizKey(bizKey);
        message.setSpareId(100L);
        message.setSpareCode("SP-001");
        message.setSpareName("bearing");
        message.setMaterialId(1L);
        message.setWarehouseId(2L);
        message.setTransactionType(txnType);
        message.setQuantity(new BigDecimal(qty));
        message.setRefType("REPAIR");
        message.setRefId(200L);
        return message;
    }
}
