package com.nexus.mes.spc.service;

import com.nexus.mes.spc.service.SpcCapabilityCalculator.Capability;
import com.nexus.mes.spc.service.SpcCapabilityCalculator.LongTermCapability;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPC 过程能力计算器单元测试
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class SpcCapabilityCalculatorTest {

    private static final BigDecimal TOL = new BigDecimal("0.02");

    /**
     * 极差法手算基准: X̿=10.0, R̄=0.05, n=5(d2=2.326), USL=10.05, LSL=9.95
     * σ=0.05/2.326≈0.021492, Cp=0.1/(6σ)≈0.7766, Cpk=min(0.05,0.05)/(3σ)≈0.7766 → C
     */
    @Test
    void testCalcByRangeHandCheck() {
        Capability cap = SpcCapabilityCalculator.calcByRange(
            BigDecimal.valueOf(10.0), BigDecimal.valueOf(0.05), 5,
            BigDecimal.valueOf(10.05), BigDecimal.valueOf(9.95));
        assertNotNull(cap);
        assertTrue(cap.cp().subtract(BigDecimal.valueOf(0.7766)).abs().compareTo(TOL) < 0, "Cp=" + cap.cp());
        assertTrue(cap.cpk().subtract(BigDecimal.valueOf(0.7766)).abs().compareTo(TOL) < 0, "Cpk=" + cap.cpk());
        assertEquals("C", cap.grade());
    }

    /**
     * 偏心分布: 均值右偏(X̿=10.02), Cpk 应小于 Cp
     */
    @Test
    void testCpkLessThanCpWhenOffCenter() {
        Capability cap = SpcCapabilityCalculator.calcByRange(
            BigDecimal.valueOf(10.02), BigDecimal.valueOf(0.05), 5,
            BigDecimal.valueOf(10.05), BigDecimal.valueOf(9.95));
        assertNotNull(cap);
        assertTrue(cap.cpk().compareTo(cap.cp()) < 0, "偏心时 Cpk 应小于 Cp, cp=" + cap.cp() + " cpk=" + cap.cpk());
        // 上侧能力 = (10.05-10.02)/(3σ) ≈ 0.466
        assertTrue(cap.cpk().subtract(BigDecimal.valueOf(0.466)).abs().compareTo(TOL) < 0, "Cpk=" + cap.cpk());
    }

    /**
     * 标准差法: X̿=10.0, S̄=0.05, n=5(c4=0.94), 与极差法同规格
     * σ=0.05/0.94≈0.05319, Cp=0.1/(6σ)≈0.3136
     */
    @Test
    void testCalcByStd() {
        Capability cap = SpcCapabilityCalculator.calcByStd(
            BigDecimal.valueOf(10.0), BigDecimal.valueOf(0.05), 5,
            BigDecimal.valueOf(10.05), BigDecimal.valueOf(9.95));
        assertNotNull(cap);
        assertTrue(cap.cp().subtract(BigDecimal.valueOf(0.3136)).abs().compareTo(TOL) < 0, "Cp=" + cap.cp());
        assertEquals("C", cap.grade());
    }

    /**
     * 等级分档: Cpk 1.67→A+, 1.33→A, 1.0→B, 0.9→C
     */
    @Test
    void testGradeBands() {
        assertEquals("A+", SpcCapabilityCalculator.grade(BigDecimal.valueOf(1.67)));
        assertEquals("A", SpcCapabilityCalculator.grade(BigDecimal.valueOf(1.5)));
        assertEquals("A", SpcCapabilityCalculator.grade(BigDecimal.valueOf(1.33)));
        assertEquals("B", SpcCapabilityCalculator.grade(BigDecimal.valueOf(1.2)));
        assertEquals("B", SpcCapabilityCalculator.grade(BigDecimal.valueOf(1.0)));
        assertEquals("C", SpcCapabilityCalculator.grade(BigDecimal.valueOf(0.9)));
        assertNull(SpcCapabilityCalculator.grade(null));
    }

    /**
     * 非法入参返回 null: 两侧规格均缺 / σ为0 / USL<=LSL / n越界
     */
    @Test
    void testInvalidInput() {
        Capability bothSpecNull = SpcCapabilityCalculator.calcByRange(
            BigDecimal.TEN, BigDecimal.ONE, 5, null, null);
        assertNull(bothSpecNull);
        Capability zeroRange = SpcCapabilityCalculator.calcByRange(
            BigDecimal.TEN, BigDecimal.ZERO, 5, BigDecimal.valueOf(11), BigDecimal.valueOf(9));
        assertNull(zeroRange);
        Capability badSpec = SpcCapabilityCalculator.calcByRange(
            BigDecimal.TEN, BigDecimal.ONE, 5, BigDecimal.valueOf(9), BigDecimal.valueOf(11));
        assertNull(badSpec);
        Capability badN = SpcCapabilityCalculator.calcByRange(
            BigDecimal.TEN, BigDecimal.ONE, 11, BigDecimal.valueOf(11), BigDecimal.valueOf(9));
        assertNull(badN);
    }

    /**
     * MES-SPC-02 单边规格(仅 USL): Cpk = Cpu = (USL − μ)/3σ, Cp 为 null。
     * <p>μ=10.0, R̄=0.05, n=5(d2=2.326) → σ=0.0214961307, 3σ=0.0644883921,
     * Cpu=0.05/0.0644883921≈0.7753 → 等级 C。</p>
     */
    @Test
    void testOneSidedCpu() {
        Capability cap = SpcCapabilityCalculator.calcByRange(
            BigDecimal.valueOf(10.0), BigDecimal.valueOf(0.05), 5,
            BigDecimal.valueOf(10.05), null);
        assertNotNull(cap, "仅 USL 时应支持单边能力计算");
        assertNull(cap.cp(), "单边规格无 Cp");
        assertTrue(cap.cpk().subtract(BigDecimal.valueOf(0.7753)).abs().compareTo(TOL) < 0, "Cpu=" + cap.cpk());
        assertEquals("C", cap.grade());
    }

    /**
     * MES-SPC-02 单边规格(仅 LSL): Cpk = Cpl = (μ − LSL)/3σ。
     */
    @Test
    void testOneSidedCpl() {
        Capability cap = SpcCapabilityCalculator.calcByRange(
            BigDecimal.valueOf(10.0), BigDecimal.valueOf(0.05), 5,
            null, BigDecimal.valueOf(9.95));
        assertNotNull(cap, "仅 LSL 时应支持单边能力计算");
        assertNull(cap.cp());
        assertTrue(cap.cpk().subtract(BigDecimal.valueOf(0.7753)).abs().compareTo(TOL) < 0, "Cpl=" + cap.cpk());
        assertEquals("C", cap.grade());
    }

    /**
     * MES-SPC-02 长期过程性能(整体样本标准差 s): μ=10.0, s=0.05, USL=10.05, LSL=9.95
     * Pp = 0.1/(6s) = 0.3333, Ppk = 0.05/(3s) = 0.3333。
     */
    @Test
    void testCalcByStdLongTerm() {
        LongTermCapability cap = SpcCapabilityCalculator.calcByStdLongTerm(
            BigDecimal.valueOf(10.0), BigDecimal.valueOf(0.05),
            BigDecimal.valueOf(10.05), BigDecimal.valueOf(9.95));
        assertNotNull(cap);
        assertTrue(cap.pp().subtract(BigDecimal.valueOf(0.3333)).abs().compareTo(TOL) < 0, "Pp=" + cap.pp());
        assertTrue(cap.ppk().subtract(BigDecimal.valueOf(0.3333)).abs().compareTo(TOL) < 0, "Ppk=" + cap.ppk());
        assertEquals(new BigDecimal("0.050000"), cap.sigmaLongTerm());
        assertEquals("C", cap.grade());
        // 单边长期性能
        LongTermCapability oneSided = SpcCapabilityCalculator.calcByStdLongTerm(
            BigDecimal.valueOf(10.0), BigDecimal.valueOf(0.05), null, BigDecimal.valueOf(9.95));
        assertNotNull(oneSided);
        assertNull(oneSided.pp());
        assertTrue(oneSided.ppk().subtract(BigDecimal.valueOf(0.3333)).abs().compareTo(TOL) < 0);
        // σ 非法 / 两侧规格均缺 → null
        assertNull(SpcCapabilityCalculator.calcByStdLongTerm(BigDecimal.TEN, BigDecimal.ZERO,
            BigDecimal.valueOf(11), BigDecimal.valueOf(9)));
        assertNull(SpcCapabilityCalculator.calcByStdLongTerm(BigDecimal.TEN, BigDecimal.ONE, null, null));
    }

    /**
     * 整体样本标准差 s(n−1): [2,4,4,4,5,5,7,9] → √(32/7) = 2.138090
     */
    @Test
    void testSampleStdDev() {
        BigDecimal s = SpcCapabilityCalculator.sampleStdDev(
            List.of(BigDecimal.valueOf(2), BigDecimal.valueOf(4), BigDecimal.valueOf(4), BigDecimal.valueOf(4),
                BigDecimal.valueOf(5), BigDecimal.valueOf(5), BigDecimal.valueOf(7), BigDecimal.valueOf(9)));
        assertEquals(new BigDecimal("2.138090"), s);
        assertNull(SpcCapabilityCalculator.sampleStdDev(List.of(BigDecimal.ONE)));
        assertNull(SpcCapabilityCalculator.sampleStdDev(null));
    }
}
