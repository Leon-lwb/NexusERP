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
 *   Cp  = (USL − LSL) / (6σ)                 双边规格才有
 *   Cpk = min(USL − μ, μ − LSL) / (3σ)      双边; 仅 USL → Cpu; 仅 LSL → Cpl
 *   Pp  = (USL − LSL) / (6s)                 s = 整体样本标准差(n−1)
 *   Ppk = min(USL − μ, μ − LSL) / (3s)
 * </pre>
 * 等级: A+(Cpk≥1.67 特优) / A(≥1.33 充分) / B(≥1.00 勉强) / C(&lt;1.00 不足)。
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
     * @param cp  过程能力指数(单边规格时为 null)
     * @param cpk 修正过程能力指数(单边规格时即 Cpl 或 Cpu)
     * @param sigmaWithin 组内标准差估计
     * @param grade 能力等级 A+/A/B/C
     */
    public record Capability(BigDecimal cp, BigDecimal cpk, BigDecimal sigmaWithin, String grade) {
    }

    /**
     * 过程性能结果(长期, 用整体样本标准差 s(n-1))
     *
     * @param pp 过程性能指数(单边规格时为 null)
     * @param ppk 修正过程性能指数(单边规格时即 Ppl 或 Ppu)
     * @param sigmaLongTerm 长期(整体)样本标准差 s
     * @param grade 性能等级 A+/A/B/C(按 ppk 分档)
     */
    public record LongTermCapability(BigDecimal pp, BigDecimal ppk, BigDecimal sigmaLongTerm, String grade) {
    }

    /** Cp/Cpk(或 Pp/Ppk) 计算中间结果 */
    private record Indices(BigDecimal cp, BigDecimal cpk) {
    }

    /**
     * 极差法(R̄/d2)计算过程能力
     *
     * @param grandMean  组均值总平均 X̿
     * @param rBarMean   平均极差 R̄
     * @param sampleSize 子组大小 n(2-10)
     * @param usl        规格上限(可为 null, 表示单边无上限)
     * @param lsl        规格下限(可为 null, 表示单边无下限)
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
     * @param usl       规格上限(可为 null, 表示单边无上限)
     * @param lsl       规格下限(可为 null, 表示单边无下限)
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
     * 通用能力计算: Cp/Cpk + 等级(支持单边规格)
     * <p>USL/LSL 均空 → null；仅 USL → Cpk=Cpu=(USL−μ)/3σ；仅 LSL → Cpk=Cpl=(μ−LSL)/3σ；
     * 双边 → Cpk=min(Cpu,Cpl)、Cp=(USL−LSL)/6σ。</p>
     */
    private static Capability calc(BigDecimal grandMean, BigDecimal sigma, BigDecimal usl, BigDecimal lsl) {
        Indices idx = indices(grandMean, sigma, usl, lsl);
        if (idx == null) {
            return null;
        }
        return new Capability(idx.cp(), idx.cpk(), sigma.setScale(6, RoundingMode.HALF_UP), grade(idx.cpk()));
    }

    /**
     * 长期过程性能 Pp/Ppk 计算(用整体样本标准差 s, 自由度 n−1)
     *
     * @param grandMean  总体均值 μ(单值时即样本均值)
     * @param overallStd 整体样本标准差 s(n−1)
     * @param usl        规格上限(可为空, 单边)
     * @param lsl        规格下限(可为空, 单边)
     * @return 长期性能结果; 入参非法(σ≤0 / 两侧规格均空)返回 null
     */
    public static LongTermCapability calcByStdLongTerm(BigDecimal grandMean, BigDecimal overallStd,
                                                       BigDecimal usl, BigDecimal lsl) {
        Indices idx = indices(grandMean, overallStd, usl, lsl);
        if (idx == null) {
            return null;
        }
        return new LongTermCapability(idx.cp(), idx.cpk(), overallStd.setScale(6, RoundingMode.HALF_UP), grade(idx.cpk()));
    }

    /**
     * 样本标准差 s = √(Σ(xi−x̄)²/(n−1))(整体/长期波动, 与组内 σ 区分)
     *
     * @param values 样本序列
     * @return 样本标准差; 样本数 &lt; 2 返回 null
     */
    public static BigDecimal sampleStdDev(java.util.List<BigDecimal> values) {
        if (values == null || values.size() < 2) {
            return null;
        }
        int n = values.size();
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            sum = sum.add(v);
        }
        BigDecimal mean = sum.divide(BigDecimal.valueOf(n), 10, RoundingMode.HALF_UP);
        BigDecimal sqSum = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            sqSum = sqSum.add(v.subtract(mean).pow(2));
        }
        double variance = sqSum.divide(BigDecimal.valueOf(n - 1L), 10, RoundingMode.HALF_UP).doubleValue();
        return BigDecimal.valueOf(Math.sqrt(variance)).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Cp/Cpk 通用计算(含单边规格)
     */
    private static Indices indices(BigDecimal grandMean, BigDecimal sigma, BigDecimal usl, BigDecimal lsl) {
        if (grandMean == null || sigma == null || sigma.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        if (usl == null && lsl == null) {
            return null;
        }
        BigDecimal threeSigma = sigma.multiply(BigDecimal.valueOf(3), MathContext.DECIMAL64);
        BigDecimal cp = null;
        if (usl != null && lsl != null) {
            if (usl.compareTo(lsl) <= 0) {
                return null;
            }
            cp = usl.subtract(lsl).divide(sigma.multiply(BigDecimal.valueOf(6), MathContext.DECIMAL64), 4, RoundingMode.HALF_UP);
        }
        BigDecimal cpk;
        if (usl != null && lsl != null) {
            BigDecimal upperSide = usl.subtract(grandMean).divide(threeSigma, 4, RoundingMode.HALF_UP);
            BigDecimal lowerSide = grandMean.subtract(lsl).divide(threeSigma, 4, RoundingMode.HALF_UP);
            cpk = upperSide.min(lowerSide);
        } else if (usl != null) {
            // 仅规格上限: Cpu
            cpk = usl.subtract(grandMean).divide(threeSigma, 4, RoundingMode.HALF_UP);
        } else {
            // 仅规格下限: Cpl
            cpk = grandMean.subtract(lsl).divide(threeSigma, 4, RoundingMode.HALF_UP);
        }
        return new Indices(cp, cpk.setScale(4, RoundingMode.HALF_UP));
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
