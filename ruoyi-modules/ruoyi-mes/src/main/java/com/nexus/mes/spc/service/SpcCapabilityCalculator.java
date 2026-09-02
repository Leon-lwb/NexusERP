package com.nexus.mes.spc.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * SPC 过程能力指数计算器（纯静态工具，可单测）
 * <p>
 * 计量型数据（X_BAR_R / X_BAR_S）的过程能力分析：
 * <pre>
 *   σ_within(R法)  = R̄ / d2(n)
 *   σ_within(S法)  = S̄ / c4(n)
 *   Cp  = (USL − LSL) / (6σ)
 *   Cpk = min(USL − μ, μ − LSL) / (3σ)      其中 μ = 组均值总平均 X̿
 * </pre>
 * 等级: A+(Cpk≥1.67 特优) / A(≥1.33 充分) / B(≥1.00 勉强) / C(<1.00 不足)。
 *
 * @author nexus
 */
public final class SpcCapabilityCalculator {

    /**
     * d2 修偏常数(极差法, n=2..10)
     */
    private static final double[] D2 = {0, 0, 1.128, 1.693, 2.059, 2.326, 2.534, 2.704, 2.847, 2.970, 3.078};

    /**
     * c4 修偏常数(标准差法, n=2..10)
     */
    private static final double[] C4 = {0, 0, 0.7979, 0.8862, 0.9213, 0.9400, 0.9515, 0.9594, 0.9650, 0.9693, 0.9727};

    private SpcCapabilityCalculator() {
    }

    /**
     * 过程能力结果
     *
     * @param cp  过程能力指数
     * @param cpk 修正过程能力指数
     * @param sigmaWithin 组内标准差估计
     * @param grade 能力等级 A+/A/B/C
     */
    public record Capability(BigDecimal cp, BigDecimal cpk, BigDecimal sigmaWithin, String grade) {
    }

    /**
     * 极差法(R̄/d2)计算过程能力
     *
     * @param grandMean  组均值总平均 X̿
     * @param rBarMean   平均极差 R̄
     * @param sampleSize 子组大小 n(2-10)
     * @param usl        规格上限
     * @param lsl        规格下限
     * @return 能力结果; 入参非法返回 null
     */
    public static Capability calcByRange(BigDecimal grandMean, BigDecimal rBarMean, int sampleSize,
                                         BigDecimal usl, BigDecimal lsl) {
        double d2 = d2(sampleSize);
        if (d2 <= 0) {
            return null;
        }
        BigDecimal sigma = rBarMean.divide(BigDecimal.valueOf(d2), 10, RoundingMode.HALF_UP);
        return calc(grandMean, sigma, usl, lsl);
    }

    /**
     * 标准差法(S̄/c4)计算过程能力
     *
     * @param grandMean 组均值总平均 X̿
     * @param sBarMean  平均组内标准差 S̄
     * @param sampleSize 子组大小 n(2-10)
     * @param usl       规格上限
     * @param lsl       规格下限
     * @return 能力结果; 入参非法返回 null
     */
    public static Capability calcByStd(BigDecimal grandMean, BigDecimal sBarMean, int sampleSize,
                                       BigDecimal usl, BigDecimal lsl) {
        double c4 = c4(sampleSize);
        if (c4 <= 0) {
            return null;
        }
        BigDecimal sigma = sBarMean.divide(BigDecimal.valueOf(c4), 10, RoundingMode.HALF_UP);
        return calc(grandMean, sigma, usl, lsl);
    }

    /**
     * 通用能力计算: Cp/Cpk + 等级
     */
    private static Capability calc(BigDecimal grandMean, BigDecimal sigma, BigDecimal usl, BigDecimal lsl) {
        if (grandMean == null || sigma == null || usl == null || lsl == null
            || sigma.compareTo(BigDecimal.ZERO) <= 0 || usl.compareTo(lsl) <= 0) {
            return null;
        }
        BigDecimal sixSigma = sigma.multiply(BigDecimal.valueOf(6), MathContext.DECIMAL64);
        BigDecimal threeSigma = sigma.multiply(BigDecimal.valueOf(3), MathContext.DECIMAL64);
        BigDecimal cp = usl.subtract(lsl).divide(sixSigma, 4, RoundingMode.HALF_UP);
        BigDecimal upperSide = usl.subtract(grandMean).divide(threeSigma, 4, RoundingMode.HALF_UP);
        BigDecimal lowerSide = grandMean.subtract(lsl).divide(threeSigma, 4, RoundingMode.HALF_UP);
        BigDecimal cpk = upperSide.min(lowerSide).setScale(4, RoundingMode.HALF_UP);
        return new Capability(cp, cpk, sigma.setScale(6, RoundingMode.HALF_UP), grade(cpk));
    }

    /**
     * 能力等级: A+(≥1.67 特优) / A(≥1.33 充分) / B(≥1.00 勉强) / C(<1.00 不足)
     */
    public static String grade(BigDecimal cpk) {
        if (cpk == null) {
            return null;
        }
        double v = cpk.doubleValue();
        if (v >= 1.67) {
            return "A+";
        }
        if (v >= 1.33) {
            return "A";
        }
        if (v >= 1.0) {
            return "B";
        }
        return "C";
    }

    private static double d2(int n) {
        return (n >= 2 && n <= 10) ? D2[n] : -1;
    }

    private static double c4(int n) {
        return (n >= 2 && n <= 10) ? C4[n] : -1;
    }
}
