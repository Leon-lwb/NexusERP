package com.nexus.mes.qc.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AqlSampler 抽样检验单元测试: 字码行样本量(原 QcAqlCalculator 口径) + 完整方案锚点。
 * <p>
 * 锚点来自 GB/T 2828.1 公开表(AqlSampler 类注释所列交叉验证锚点)。
 */
class AqlSamplerTest {

    @Test
    void letterSampleSizeTypicalLots() {
        // 2~8 -> A(2); 91~150 -> F(20); 3201~10000 -> L(200)
        assertEquals(2, AqlSampler.letterSampleSize(5, "II"));
        assertEquals(20, AqlSampler.letterSampleSize(100, "II"));
        assertEquals(200, AqlSampler.letterSampleSize(5000, "II"));
    }

    @Test
    void letterSampleSizeBatchBoundaries() {
        // 8(档A) / 9(档B) / 15(档B) / 16(档C) / 50(档D) / 51(档E)
        assertEquals(2, AqlSampler.letterSampleSize(8, "II"));
        assertEquals(3, AqlSampler.letterSampleSize(9, "II"));
        assertEquals(3, AqlSampler.letterSampleSize(15, "II"));
        assertEquals(5, AqlSampler.letterSampleSize(16, "II"));
        assertEquals(8, AqlSampler.letterSampleSize(50, "II"));
        assertEquals(13, AqlSampler.letterSampleSize(51, "II"));
    }

    @Test
    void letterSampleSizeRejectsTinyLot() {
        assertThrows(IllegalArgumentException.class, () -> AqlSampler.letterSampleSize(1, "II"));
        // 极小批(批量<2)由调用方按业务口径处理(见 QcInspectDetailServiceImpl.suggestSampleQty)
    }

    @Test
    void codeLetterAnchors() {
        assertEquals('A', AqlSampler.codeLetter(5, "II"));
        assertEquals('H', AqlSampler.codeLetter(300, "II"));
        assertEquals('K', AqlSampler.codeLetter(2000, "II"));
        assertEquals('M', AqlSampler.codeLetter(13000, "II"));
        // 水平 I/III 各错开一档: 1201~3200 档 II=K, I=H, III=L
        assertEquals('H', AqlSampler.codeLetter(2000, "I"));
        assertEquals('L', AqlSampler.codeLetter(2000, "III"));
    }

    @Test
    void planAcReAnchors() {
        // 公开锚点: K@AQL2.5→7/8、M@2.5→14/15、K@4.0→10/11、M@4.0→21/22
        AqlSampler.AqlPlan k25 = AqlSampler.plan(2000, "II", 2.5);
        assertEquals(7, k25.getAcceptQty());
        assertEquals(8, k25.getRejectQty());
        assertEquals(125, k25.getSampleSize());
        AqlSampler.AqlPlan m25 = AqlSampler.plan(13000, "II", 2.5);
        assertEquals(14, m25.getAcceptQty());
        assertEquals(15, m25.getRejectQty());
        AqlSampler.AqlPlan k40 = AqlSampler.plan(2000, "II", 4.0);
        assertEquals(10, k40.getAcceptQty());
        assertEquals(11, k40.getRejectQty());
        AqlSampler.AqlPlan m40 = AqlSampler.plan(13000, "II", 4.0);
        assertEquals(21, m40.getAcceptQty());
        assertEquals(22, m40.getRejectQty());
    }

    @Test
    void planWholeLotTruncation() {
        // 小批量+宽松AQL: 向下箭头取起始字码方案(AQL1.0 起始字码 G=32), 样本量截断为整批 8
        AqlSampler.AqlPlan plan = AqlSampler.plan(8, "II", 1.0);
        assertEquals(8, plan.getSampleSize());
        assertEquals(32, plan.getStandardSampleSize());
        assertTrue(plan.isWholeLot());
        assertEquals(0, plan.getAcceptQty());
        assertEquals(1, plan.getRejectQty());
    }

    @Test
    void judgeByDefectQty() {
        // lot 2000 / II / AQL1.0 → 字码K, Ac=3/Re=4
        assertEquals("QUALIFIED", AqlSampler.judge(2000, "II", 1.0, 3));
        assertEquals("UNQUALIFIED", AqlSampler.judge(2000, "II", 1.0, 4));
        assertFalse(AqlSampler.isSupportedAql(0.5));
        assertTrue(AqlSampler.isSupportedAql(1.0));
    }
}
