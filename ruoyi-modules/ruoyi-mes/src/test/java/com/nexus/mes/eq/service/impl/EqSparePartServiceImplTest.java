package com.nexus.mes.eq.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.mes.eq.domain.EqSparePart;
import com.nexus.mes.eq.domain.EqSpareStockLog;
import com.nexus.mes.eq.domain.bo.EqSpareStockBo;
import com.nexus.mes.eq.domain.vo.EqSparePartVo;
import com.nexus.mes.eq.mapper.EqSparePartMapper;
import com.nexus.mes.eq.mapper.EqSpareStockLogMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 备件出入库单测。
 * <p>MES-EQ-14: 流水的 before_qty/after_qty 必须取原子 SQL 落库后的回读值,
 * 而非 Java 先算值, 否则并发出入库下流水与 eq_spare_part.current_stock 不一致。</p>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class EqSparePartServiceImplTest {

    @Mock
    private EqSparePartMapper sparePartMapper;
    @Mock
    private EqSpareStockLogMapper stockLogMapper;
    @InjectMocks
    private EqSparePartServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqSparePart.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqSpareStockLog.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(EqSparePart.class);
        TableInfoHelper.remove(EqSpareStockLog.class);
    }

    @Test
    void stockTransactionOutDeductsFromCurrentStockWithOversellGuard() {
        // stockTransaction 现按 selectVoById 读当前库存余额, 更新走 setSql 原子加减+防超卖守卫
        EqSparePartVo spare = new EqSparePartVo();
        spare.setSpareId(1L);
        spare.setSpareCode("SP-001");
        spare.setSpareName("bearing");
        spare.setCurrentStock(new BigDecimal("10"));
        when(sparePartMapper.selectVoById(1L)).thenReturn(spare);
        when(sparePartMapper.update(any(), any())).thenReturn(1);

        EqSpareStockBo bo = new EqSpareStockBo();
        bo.setSpareId(1L);
        bo.setTransactionType("OUT");
        bo.setQuantity(new BigDecimal("3"));

        BigDecimal balance = service.stockTransaction(bo);

        assertEquals(0, new BigDecimal("7").compareTo(balance));
        verify(sparePartMapper).selectVoById(1L);
    }

    @Test
    void stockTransactionWritesBackReadAfterQtyInsteadOfComputedValue() {
        EqSparePartVo spare = new EqSparePartVo();
        spare.setSpareId(1L);
        spare.setSpareCode("SP-001");
        spare.setSpareName("bearing");
        spare.setCurrentStock(new BigDecimal("10"));
        when(sparePartMapper.selectVoById(1L)).thenReturn(spare);
        when(sparePartMapper.update(any(), any())).thenReturn(1);
        // 并发场景: 本事务原子扣减 3 后, 实际库存已是 5(另一笔出库先扣了 2)
        EqSparePart actual = new EqSparePart();
        actual.setSpareId(1L);
        actual.setCurrentStock(new BigDecimal("5"));
        when(sparePartMapper.selectById(1L)).thenReturn(actual);

        EqSpareStockBo bo = new EqSpareStockBo();
        bo.setSpareId(1L);
        bo.setTransactionType("OUT");
        bo.setQuantity(new BigDecimal("3"));

        BigDecimal balance = service.stockTransaction(bo);

        // 修复前: Java 先算 before/after(10/7), 与实际库存 5 不符
        assertEquals(0, new BigDecimal("5").compareTo(balance));
        ArgumentCaptor<EqSpareStockLog> captor = ArgumentCaptor.forClass(EqSpareStockLog.class);
        verify(stockLogMapper).insert(captor.capture());
        assertEquals(0, new BigDecimal("5").compareTo(captor.getValue().getAfterQty()));
        // 出库: before = after + qty → 流水自洽(after - before == -qty)
        assertEquals(0, new BigDecimal("8").compareTo(captor.getValue().getBeforeQty()));
    }

    @Test
    void stockTransactionFallsBackToComputedQtyWhenReadBackMissing() {
        EqSparePartVo spare = new EqSparePartVo();
        spare.setSpareId(1L);
        spare.setSpareCode("SP-001");
        spare.setCurrentStock(new BigDecimal("10"));
        when(sparePartMapper.selectVoById(1L)).thenReturn(spare);
        when(sparePartMapper.update(any(), any())).thenReturn(1);
        when(sparePartMapper.selectById(1L)).thenReturn(null);

        EqSpareStockBo bo = new EqSpareStockBo();
        bo.setSpareId(1L);
        bo.setTransactionType("IN");
        bo.setQuantity(new BigDecimal("2"));

        BigDecimal balance = service.stockTransaction(bo);

        assertEquals(0, new BigDecimal("12").compareTo(balance));
        ArgumentCaptor<EqSpareStockLog> captor = ArgumentCaptor.forClass(EqSpareStockLog.class);
        verify(stockLogMapper).insert(captor.capture());
        assertEquals(0, new BigDecimal("12").compareTo(captor.getValue().getAfterQty()));
        assertEquals(0, new BigDecimal("10").compareTo(captor.getValue().getBeforeQty()));
    }
}
