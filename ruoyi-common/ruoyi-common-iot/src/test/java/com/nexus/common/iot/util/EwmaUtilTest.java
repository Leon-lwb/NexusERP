package com.nexus.common.iot.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * EWMA 统计工具单元测试
 *
 * @author nexus
 */
@Tag("dev")
class EwmaUtilTest {

    private static final BigDecimal TOL = new BigDecimal("0.5");

    /**
     * 均值收敛：稳态序列(全部 100)的均值应收敛到 100(误差 < 0.01)
     */
    @Test
    void testMeanConvergence() {
        EwmaUtil.EwmaState state = null;
        for (int i = 0; i < 50; i++) {
            state = EwmaUtil.update(state, BigDecimal.valueOf(100), 0.3);
        }
        BigDecimal diff = BigDecimal.valueOf(100).subtract(state.mean()).abs();
        assertTrue(diff.compareTo(new BigDecimal("0.01")) <= 0, "均值应收敛到 100, 实际: " + state.mean());
    }

    /**
     * 方差收敛：±2 随机抖动的稳态序列, 标准差应接近 2 左右量级(非零且有限)
     */
    @Test
    void testVarianceConvergence() {
        Random random = new Random(42);
        EwmaUtil.EwmaState state = null;
        for (int i = 0; i < 200; i++) {
            double x = 100 + (random.nextDouble() - 0.5) * 4;
            state = EwmaUtil.update(state, BigDecimal.valueOf(x), 0.3);
        }
        BigDecimal std = state.std();
        assertTrue(std.compareTo(BigDecimal.valueOf(0.5)) > 0, "标准差应大于 0.5, 实际: " + std);
        assertTrue(std.compareTo(BigDecimal.valueOf(5)) < 0, "标准差应小于 5, 实际: " + std);
    }

    /**
     * z-score 触发：稳态(100±1)后突增到 120, z 应远超 3
     */
    @Test
    void testZScoreTriggersOnSpike() {
        EwmaUtil.EwmaState state = null;
        for (int i = 0; i < 50; i++) {
            state = EwmaUtil.update(state, BigDecimal.valueOf(100 + (i % 2)), 0.3);
        }
        BigDecimal z = EwmaUtil.zScore(state, BigDecimal.valueOf(120));
        assertTrue(z.compareTo(BigDecimal.valueOf(3)) > 0, "突增点 z 应大于 3, 实际: " + z);
    }

    /**
     * 预热期不判定：count 不足 warmup 时 warmedUp 返回 false
     */
    @Test
    void testWarmupGate() {
        EwmaUtil.EwmaState state = EwmaUtil.init(BigDecimal.TEN);
        assertFalse(EwmaUtil.warmedUp(state, 20));
        // init 后 count=1, 再 18 次更新 → count=19 仍未达标
        for (int i = 0; i < 18; i++) {
            state = EwmaUtil.update(state, BigDecimal.TEN, 0.3);
        }
        assertFalse(EwmaUtil.warmedUp(state, 20));
        state = EwmaUtil.update(state, BigDecimal.TEN, 0.3);
        assertTrue(EwmaUtil.warmedUp(state, 20));
    }

    /**
     * 未初始化状态 z-score 为 0, 不产生误报
     */
    @Test
    void testNullStateSafe() {
        assertEquals(0, EwmaUtil.zScore(null, BigDecimal.valueOf(100)).compareTo(BigDecimal.ZERO));
        assertFalse(EwmaUtil.warmedUp(null, 1));
    }
}
