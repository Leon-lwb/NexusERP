package com.nexus.erp.prd;

import com.nexus.erp.prd.util.BomExplosionCalculator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BOM 展开共用口径单元测试（ERP-PRD-01）。
 * <p>回归点：两条入口此前一条不算损耗率、需求日期按「天」倒排，另一条算损耗率 + CEILING、按「小时」倒排。</p>
 */
@Tag("dev")
class BomExplosionCalculatorTest {

    /** 含损耗率时向上取整：10 × 1.05 = 10.5 → 11（此前入口 A 直接得 10.5） */
    @Test
    void calculateChildQuantity_withLossRate_ceils() {
        BigDecimal result = BomExplosionCalculator.calculateChildQuantity(
            new BigDecimal("10"), BigDecimal.ONE, new BigDecimal("5"));
        assertEquals(0, new BigDecimal("11").compareTo(result), "实际=" + result);
    }

    /** 无损耗率时保持精确乘积 */
    @Test
    void calculateChildQuantity_withoutLossRate_keepsExact() {
        BigDecimal result = BomExplosionCalculator.calculateChildQuantity(
            new BigDecimal("10"), new BigDecimal("2.5"), null);
        assertEquals(0, new BigDecimal("25.0").compareTo(result), "实际=" + result);
    }

    /** 参数缺失返回 0，不抛 NPE */
    @Test
    void calculateChildQuantity_nullArgs_returnsZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
            BomExplosionCalculator.calculateChildQuantity(null, BigDecimal.ONE, null)));
        assertEquals(0, BigDecimal.ZERO.compareTo(
            BomExplosionCalculator.calculateChildQuantity(BigDecimal.ONE, null, null)));
    }

    /** 提前期按「小时」倒排：父层需求日期 - 24h */
    @Test
    void resolveRequiredDate_offsetsByHours() {
        Date parent = new Date(1_700_000_000_000L);
        Date result = BomExplosionCalculator.resolveRequiredDate(parent, 24);
        assertEquals(parent.getTime() - 24L * 3600L * 1000L, result.getTime());
    }

    /** 提前期为空/0 → 返回父层需求日期本身 */
    @Test
    void resolveRequiredDate_noLeadTime_returnsParentDate() {
        Date parent = new Date(1_700_000_000_000L);
        assertEquals(parent.getTime(), BomExplosionCalculator.resolveRequiredDate(parent, null).getTime());
        assertEquals(parent.getTime(), BomExplosionCalculator.resolveRequiredDate(parent, 0).getTime());
    }

    /** 无父层需求日期 → 以当前时间为基准，且必须是倒排（早于当前时间） */
    @Test
    void resolveRequiredDate_noParentDate_baselineNow() {
        long before = System.currentTimeMillis();
        Date result = BomExplosionCalculator.resolveRequiredDate(null, 1);
        assertTrue(result.getTime() < before, "应基于当前时间倒排，实际=" + result.getTime());
        assertTrue(result.getTime() > before - 2L * 3600L * 1000L, "偏移应为 1 小时");
    }
}
