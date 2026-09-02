package com.nexus.mes.qc.service;

/**
 * GB/T 2828.1 计数抽样检验 简化版判定器（全系统唯一 AQL 实现）。
 * <p>
 * 覆盖:正常检验一次抽样 + 一般检验水平 I/II/III,支持 AQL 0.025~10.0(常规生产抽检区间)。
 * 严格版的全表/加严/放宽/多次抽样不在本工具范围内。
 * <p>
 * 消费方:
 * <ul>
 *   <li>PDA AQL 抽样试算 {@code AppPdaQcAqlController}（批量/水平/AQL → 完整方案+判定建议）；</li>
 *   <li>QC 检验明细建议抽样量 {@code QcInspectDetailServiceImpl}（{@link #letterSampleSize}，无 AQL 场景）。</li>
 * </ul>
 * <p>
 * 表结构说明(供复核):
 * <ul>
 *   <li>表 1(样本量字码): 一般检验水平 II 列恰为连续字母序列 A..Q,水平 I/III 各错开一档,
 *       已按公开锚点交叉验证(批量 13000/II→M、批量 1201~3200/II→K 等)。</li>
 *   <li>表 2-A(正常检验一次抽样主表): 判定数列 Ac/Re 固定为
 *       0/1, 1/2, 2/3, 3/4, 5/6, 7/8, 10/11, 14/15, 21/22 九档;
 *       每列自"0/1 起始字码"起沿字码序列逐档递进,同判定数沿对角线(字码+1、AQL-1)平移。
 *       已按公开锚点交叉验证(字码 K@AQL2.5→7/8、M@2.5→14/15、K@4.0→10/11、M@4.0→21/22)。</li>
 *   <li>箭头解析: 字码在起始字码之前(列内上方)视为向下箭头,取该列首个显式方案(起始字码方案);
 *       超出 21/22 档(列内下方)视为向上箭头,取该列最后一个显式方案。</li>
 *   <li>样本量 ≥ 批量时按标准整批检查: n 取批量,Ac/Re 不变。</li>
 * </ul>
 *
 * @author Nexus
 */
public final class AqlSampler {

    /** 抽样字码序列(标准跳过 I、O) */
    private static final char[] LETTERS = {'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'P', 'Q', 'R'};

    /** 各字码对应样本量(与 LETTERS 一一对应) */
    private static final int[] SAMPLE_SIZES = {2, 3, 5, 8, 13, 20, 32, 50, 80, 125, 200, 315, 500, 800, 1250, 2000};

    /** 主表判定数列 Ac/Re 九档 */
    private static final int[][] SEQ = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {5, 6}, {7, 8}, {10, 11}, {14, 15}, {21, 22}};

    /** 批量区间下界(升序,末行无上界);与 LEVEL_ROWS 三列字码索引一一对应 */
    private static final long[] LOT_LOWER = {2, 9, 16, 26, 51, 91, 151, 281, 501, 1201, 3201, 10001, 35001, 150001, 500001};

    /** 表 1: 各批量区间 [水平I, 水平II, 水平III] 的字码在 LETTERS 中的下标 */
    private static final int[][] LEVEL_ROWS = {
        {0, 0, 1},   // 2-8:        A A B
        {0, 1, 2},   // 9-15:       A B C
        {1, 2, 3},   // 16-25:      B C D
        {2, 3, 4},   // 26-50:      C D E
        {2, 4, 5},   // 51-90:      C E F
        {3, 5, 6},   // 91-150:     D F G
        {4, 6, 7},   // 151-280:    E G H
        {5, 7, 8},   // 281-500:    F H J
        {6, 8, 9},   // 501-1200:   G J K
        {7, 9, 10},  // 1201-3200:  H K L
        {8, 10, 11}, // 3201-10000: J L M
        {9, 11, 12}, // 10001-35000: K M N
        {10, 12, 13},// 35001-150000: L N P
        {11, 13, 14},// 150001-500000: M P Q
        {12, 14, 15} // 500001+:    N Q R
    };

    /** 支持的 AQL 值(升序) */
    public static final double[] SUPPORTED_AQLS = {0.025, 0.04, 0.065, 0.10, 0.15, 0.25, 0.40, 0.65, 1.0, 1.5, 2.5, 4.0, 6.5, 10.0};

    /** 表 2-A: 各 AQL 列"0/1 起始字码"下标(与 SUPPORTED_AQLS 一一对应;同判定数对角线平移的基准点) */
    private static final int[] AQL_START_IDX = {14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};

    private AqlSampler() {
    }

    /**
     * 查样本量字码(表 1)
     *
     * @param lotSize 批量(≥2)
     * @param level   检验水平 I/II/III
     * @return 字码 A~R
     */
    public static char codeLetter(long lotSize, String level) {
        int row = lotRow(lotSize);
        int col = levelColumn(level);
        return LETTERS[LEVEL_ROWS[row][col]];
    }

    /**
     * 字码行样本量(表 1 字码直接对应, 不做 AQL 箭头修正与整批截断)
     * <p>
     * 供检验单明细"建议抽样量"等无需指定 AQL 的提示场景;
     * 需要 Ac/Re 判定的完整方案请用 {@link #plan}。
     *
     * @param lotSize 批量(≥2, 小批量由调用方按业务口径处理)
     * @param level   检验水平 I/II/III
     * @return 该档字码对应的标准样本量
     */
    public static int letterSampleSize(long lotSize, String level) {
        int row = lotRow(lotSize);
        int col = levelColumn(level);
        return SAMPLE_SIZES[LEVEL_ROWS[row][col]];
    }

    /**
     * 查一次抽样方案(表 2-A 正常检验)
     *
     * @param lotSize 批量(≥2)
     * @param level   检验水平 I/II/III
     * @param aql     AQL(0.025~10.0)
     * @return 方案(字码/样本量/接收数/拒收数;样本量可能被批量截断为整批检查)
     */
    public static AqlPlan plan(long lotSize, String level, double aql) {
        int row = lotRow(lotSize);
        int col = levelColumn(level);
        int letterIdx = LEVEL_ROWS[row][col];
        int startIdx = startIndexOf(aql);

        int n;
        int standardN;
        int[] acRe;
        if (letterIdx < startIdx) {
            // 向下箭头: 取该列首个显式方案(起始字码方案, 0/1)
            n = SAMPLE_SIZES[startIdx];
            standardN = n;
            acRe = SEQ[0];
        } else if (letterIdx - startIdx < SEQ.length) {
            n = SAMPLE_SIZES[letterIdx];
            standardN = n;
            acRe = SEQ[letterIdx - startIdx];
        } else {
            // 向上箭头: 取该列最后一个显式方案(21/22)
            n = SAMPLE_SIZES[startIdx + SEQ.length - 1];
            standardN = n;
            acRe = SEQ[SEQ.length - 1];
        }
        if (n >= lotSize) {
            n = (int) lotSize; // 标准规则: 样本量不小于批量时整批检查
        }
        return new AqlPlan(LETTERS[letterIdx], level, aql, n, standardN, acRe[0], acRe[1]);
    }

    /**
     * 按缺陷数判定
     *
     * @param defectQty 样本中缺陷(品)数
     * @return QUALIFIED(缺陷数 ≤ Ac) / UNQUALIFIED(缺陷数 ≥ Re)
     */
    public static String judge(long lotSize, String level, double aql, long defectQty) {
        AqlPlan p = plan(lotSize, level, aql);
        return defectQty <= p.getAcceptQty() ? "QUALIFIED" : "UNQUALIFIED";
    }

    /**
     * AQL 是否在简化表支持范围内
     */
    public static boolean isSupportedAql(double aql) {
        for (double s : SUPPORTED_AQLS) {
            if (Math.abs(s - aql) < 1e-9) {
                return true;
            }
        }
        return false;
    }

    private static int lotRow(long lotSize) {
        if (lotSize < 2) {
            throw new IllegalArgumentException("批量至少为 2 才适用抽样表");
        }
        for (int i = LOT_LOWER.length - 1; i >= 0; i--) {
            if (lotSize >= LOT_LOWER[i]) {
                return i;
            }
        }
        return 0;
    }

    private static int levelColumn(String level) {
        if ("I".equals(level)) {
            return 0;
        }
        if ("III".equals(level)) {
            return 2;
        }
        if ("II".equals(level)) {
            return 1;
        }
        throw new IllegalArgumentException("检验水平仅支持 I/II/III: " + level);
    }

    private static int startIndexOf(double aql) {
        for (int i = 0; i < SUPPORTED_AQLS.length; i++) {
            if (Math.abs(SUPPORTED_AQLS[i] - aql) < 1e-9) {
                return AQL_START_IDX[i];
            }
        }
        throw new IllegalArgumentException("AQL 超出简化抽样表支持范围(0.025~10): " + aql);
    }

    /**
     * 一次抽样方案视图
     */
    @lombok.Data
    public static class AqlPlan {
        /** 样本量字码 */
        private final char codeLetter;
        /** 检验水平 */
        private final String level;
        /** AQL */
        private final double aql;
        /** 样本量(已按批量截断) */
        private final int sampleSize;
        /** 标准样本量(未按批量截断) */
        private final int standardSampleSize;
        /** 接收数 Ac */
        private final int acceptQty;
        /** 拒收数 Re */
        private final int rejectQty;

        /** 是否整批检查(标准样本量大于实际样本量即已被批量截断) */
        public boolean isWholeLot() {
            return standardSampleSize > sampleSize;
        }
    }
}
