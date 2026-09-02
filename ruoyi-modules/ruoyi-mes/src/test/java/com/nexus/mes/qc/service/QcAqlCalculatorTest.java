package com.nexus.mes.qc.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * QcAqlCalculator 抽样量计算单元测试: 典型批量档 + 边界。
 */
class QcAqlCalculatorTest {

    @Test
    void typicalLotSizes() {
        // 2~8 -> A(2); 91~150 -> F(20); 3201~10000 -> L(200)
        assertEquals(2, QcAqlCalculator.calcSampleQty(BigDecimal.valueOf(5)).intValue());
        assertEquals(20, QcAqlCalculator.calcSampleQty(BigDecimal.valueOf(100)).intValue());
        assertEquals(200, QcAqlCalculator.calcSampleQty(BigDecimal.valueOf(5000)).intValue());
    }

    @Test
    void batchBoundarySteps() {
        // 8(档A) / 9(档B) / 15(档B) / 16(档C) / 50(档D) / 51(档E)
        assertEquals(2, QcAqlCalculator.calcSampleQty(BigDecimal.valueOf(8)).intValue());
        assertEquals(3, QcAqlCalculator.calcSampleQty(BigDecimal.valueOf(9)).intValue());
        assertEquals(3, QcAqlCalculator.calcSampleQty(BigDecimal.valueOf(15)).intValue());
        assertEquals(5, QcAqlCalculator.calcSampleQty(BigDecimal.valueOf(16)).intValue());
        assertEquals(8, QcAqlCalculator.calcSampleQty(BigDecimal.valueOf(50)).intValue());
        assertEquals(13, QcAqlCalculator.calcSampleQty(BigDecimal.valueOf(51)).intValue());
    }

    @Test
    void tinyAndInvalidLots() {
        assertEquals(0, QcAqlCalculator.calcSampleQty(BigDecimal.ZERO).intValue());
        assertEquals(0, QcAqlCalculator.calcSampleQty(null).intValue());
        assertEquals(1, QcAqlCalculator.calcSampleQty(BigDecimal.ONE).intValue());
    }

    @Test
    void sampleCodeLetters() {
        assertEquals("A", QcAqlCalculator.sampleCodeLetter(BigDecimal.valueOf(5)));
        assertEquals("H", QcAqlCalculator.sampleCodeLetter(BigDecimal.valueOf(300)));
        assertEquals("K", QcAqlCalculator.sampleCodeLetter(BigDecimal.valueOf(2000)));
    }
}
