package com.nexus.mes.qc.service;

import java.math.BigDecimal;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * AQL 抽样量计算器（GB/T 2828.1 常用表字面量, 纯函数可单测）
 * <p>
 * 批量区间 × 检验水平 → 样本量字码 → 字码对应样本量 n。
 * 简化口径: 正常检验、一般检验水平 II, 样本量取字码表
 * A=2, B=3, C=5, D=8, E=13, F=20, G=32, H=50, J=80, K=125, L=200, M=315, N=500, P=800, Q=1250, R=2000。
 * 未做 Ac/Re 与放宽/加严转换（判定由 QcJudgeEngine 的项目级标准完成, 此处只提示应抽多少）。
 * </p>
 */
public final class QcAqlCalculator {

    /** 批量下限 → 样本量字码(检验水平 II, GB/T 2828.1 表1) */
    private static final NavigableMap<Long, String> LOT_TO_CODE = new TreeMap<>();
    /** 样本量字码 → 样本量(GB/T 2828.1 表2-A 正常检验字码行) */
    private static final java.util.Map<String, Integer> CODE_TO_N = java.util.Map.ofEntries(
        java.util.Map.entry("A", 2), java.util.Map.entry("B", 3), java.util.Map.entry("C", 5),
        java.util.Map.entry("D", 8), java.util.Map.entry("E", 13), java.util.Map.entry("F", 20),
        java.util.Map.entry("G", 32), java.util.Map.entry("H", 50), java.util.Map.entry("J", 80),
        java.util.Map.entry("K", 125), java.util.Map.entry("L", 200), java.util.Map.entry("M", 315),
        java.util.Map.entry("N", 500), java.util.Map.entry("P", 800), java.util.Map.entry("Q", 1250),
        java.util.Map.entry("R", 2000)
    );

    static {
        LOT_TO_CODE.put(2L, "A");       // 2~8
        LOT_TO_CODE.put(9L, "B");       // 9~15
        LOT_TO_CODE.put(16L, "C");      // 16~25
        LOT_TO_CODE.put(26L, "D");      // 26~50
        LOT_TO_CODE.put(51L, "E");      // 51~90
        LOT_TO_CODE.put(91L, "F");      // 91~150
        LOT_TO_CODE.put(151L, "G");     // 151~280
        LOT_TO_CODE.put(281L, "H");     // 281~500
        LOT_TO_CODE.put(501L, "J");     // 501~1200
        LOT_TO_CODE.put(1201L, "K");    // 1201~3200
        LOT_TO_CODE.put(3201L, "L");    // 3201~10000
        LOT_TO_CODE.put(10001L, "M");   // 10001~35000
        LOT_TO_CODE.put(35001L, "N");   // 35001~150000
        LOT_TO_CODE.put(150001L, "P");  // 150001~500000
        LOT_TO_CODE.put(500001L, "Q");  // 500001~以上取Q(超大批按Q近似)
    }

    private QcAqlCalculator() {
    }

    /**
     * 计算样本量(检验水平 II, 正常检验)
     *
     * @param lotQty 批量
     * @return 建议抽样数量(批量小于2时按批量本身)
     */
    public static BigDecimal calcSampleQty(BigDecimal lotQty) {
        if (lotQty == null || lotQty.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        long lot = lotQty.longValue();
        if (lot < 2) {
            return BigDecimal.valueOf(lot);
        }
        String code = LOT_TO_CODE.floorEntry(lot).getValue();
        return BigDecimal.valueOf(CODE_TO_N.get(code));
    }

    /**
     * 样本量字码(检验水平 II)
     */
    public static String sampleCodeLetter(BigDecimal lotQty) {
        if (lotQty == null || lotQty.signum() <= 0) {
            return null;
        }
        long lot = lotQty.longValue();
        if (lot < 2) {
            return "A";
        }
        return LOT_TO_CODE.floorEntry(lot).getValue();
    }
}
