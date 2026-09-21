package com.nexus.erp.wm.service.impl;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 出库成本结转 单元测试（审计 ERP-WM-01 回归）
 * <p><b>修复前会失败</b>：{@code issueStockCost} 先 {@code selectById} 再拿「扣减后」的余量校验
 * {@code outQty > qty}，而 4 处出库策略都是「先 deductStock 再结转成本」，于是单批次扣减量超过
 * 扣减后余量（整批出库必然命中）就抛「出库数量超过库存数量」并整单回滚 —— 出库功能整体不可用。</p>
 * <p>修复后：成本结转改为原子相对扣减（{@code WmMaterialStockMapper.issueStockCost}），
 * 不再自行校验余量；数量充足性由 {@code deductStock*} 的守卫唯一负责。</p>
 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class MovingAverageValuationServiceImplTest {

    @Mock
    private WmMaterialStockMapper baseMapper;

    @InjectMocks
    private MovingAverageValuationServiceImpl service;

    /** 整批出库：在库 100 全部出库 → 扣减后余 0，修复前 100 > 0 必然抛错 */
    @Test
    void issueStockCost_wholeBatchIssue_noLongerRejectedByPostDeductionBalance() {
        WmMaterialStock afterDeduct = stockRow(1L, BigDecimal.ZERO, new BigDecimal("12.5"), BigDecimal.ZERO);
        when(baseMapper.selectById(1L)).thenReturn(afterDeduct);

        BigDecimal unitCost = service.issueStockCost(1L, new BigDecimal("100"));

        assertEquals(0, new BigDecimal("12.5").compareTo(unitCost), "应返回该行当前移动平均单位成本");
        verify(baseMapper).issueStockCost(1L, new BigDecimal("100"));
    }

    /** 超过半批出库：在库 100 出库 60 → 扣减后余 40，修复前 60 > 40 抛错 */
    @Test
    void issueStockCost_overHalfBatchIssue_noLongerRejected() {
        WmMaterialStock afterDeduct = stockRow(1L, new BigDecimal("40"), new BigDecimal("8"), new BigDecimal("320"));
        when(baseMapper.selectById(1L)).thenReturn(afterDeduct);

        BigDecimal unitCost = service.issueStockCost(1L, new BigDecimal("60"));

        assertEquals(0, new BigDecimal("8").compareTo(unitCost));
        verify(baseMapper).issueStockCost(1L, new BigDecimal("60"));
    }

    /** 成本结转不再做数量校验，但库存行不存在仍需显式报错 */
    @Test
    void issueStockCost_missingStock_throws() {
        when(baseMapper.selectById(404L)).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.issueStockCost(404L, new BigDecimal("1")));
    }

    /** 非正数出库量仍被拒绝（避免 0 元流水与负数结转） */
    @Test
    void issueStockCost_nonPositiveQty_throws() {
        assertThrows(ServiceException.class, () -> service.issueStockCost(1L, BigDecimal.ZERO));
        assertThrows(ServiceException.class, () -> service.issueStockCost(1L, null));
    }

    private WmMaterialStock stockRow(Long stockId, BigDecimal qty, BigDecimal unitCost, BigDecimal totalCost) {
        WmMaterialStock stock = new WmMaterialStock();
        stock.setStockId(stockId);
        stock.setStockQuantity(qty);
        stock.setUnitCost(unitCost);
        stock.setTotalCost(totalCost);
        return stock;
    }
}
