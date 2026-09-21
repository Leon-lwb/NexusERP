package com.nexus.mes.qc.service;

import com.nexus.mes.qc.domain.QcInspectDetail;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * QcJudgeEngine 判定引擎单元测试: 各 standardType 边界值、CRITICAL 一票否决、PARTIAL、定性、多值实测。
 */
class QcJudgeEngineTest {

    private QcInspectDetail detail(String dataType, String standardType) {
        QcInspectDetail detail = new QcInspectDetail();
        detail.setDataType(dataType);
        detail.setStandardType(standardType);
        detail.setCriticality("MAJOR");
        return detail;
    }

    // ============ 明细级: LSL_USL ============

    @Test
    void lslUslBoundaryInclusive() {
        QcInspectDetail d = detail("QUANTITATIVE", "LSL_USL");
        d.setLslValue(new BigDecimal("2.9"));
        d.setUslValue(new BigDecimal("3.1"));

        d.setMeasuredValue("2.9");
        assertEquals(QcJudgeEngine.JUDGE_PASS, QcJudgeEngine.judgeDetail(d));
        d.setMeasuredValue("3.1");
        assertEquals(QcJudgeEngine.JUDGE_PASS, QcJudgeEngine.judgeDetail(d));
        d.setMeasuredValue("3.05");
        assertEquals(QcJudgeEngine.JUDGE_PASS, QcJudgeEngine.judgeDetail(d));
        d.setMeasuredValue("2.89");
        assertEquals(QcJudgeEngine.JUDGE_FAIL, QcJudgeEngine.judgeDetail(d));
        d.setMeasuredValue("3.11");
        assertEquals(QcJudgeEngine.JUDGE_FAIL, QcJudgeEngine.judgeDetail(d));
    }

    // ============ 明细级: 单边 ============

    @Test
    void lslOnlyAndUslOnly() {
        QcInspectDetail lsl = detail("QUANTITATIVE", "LSL_ONLY");
        lsl.setLslValue(new BigDecimal("310"));
        lsl.setMeasuredValue("310");
        assertEquals(QcJudgeEngine.JUDGE_PASS, QcJudgeEngine.judgeDetail(lsl));
        lsl.setMeasuredValue("309.99");
        assertEquals(QcJudgeEngine.JUDGE_FAIL, QcJudgeEngine.judgeDetail(lsl));

        QcInspectDetail usl = detail("QUANTITATIVE", "USL_ONLY");
        usl.setUslValue(new BigDecimal("0.05"));
        usl.setMeasuredValue("0.05");
        assertEquals(QcJudgeEngine.JUDGE_PASS, QcJudgeEngine.judgeDetail(usl));
        usl.setMeasuredValue("0.06");
        assertEquals(QcJudgeEngine.JUDGE_FAIL, QcJudgeEngine.judgeDetail(usl));
    }

    // ============ 明细级: NOMINAL ± 公差 ============

    @Test
    void nominalWithTolerance() {
        QcInspectDetail d = detail("QUANTITATIVE", "NOMINAL");
        d.setNominalValue(new BigDecimal("10"));
        d.setTolUpper(new BigDecimal("0.05"));
        d.setTolLower(new BigDecimal("0.05"));

        d.setMeasuredValue("10.05");
        assertEquals(QcJudgeEngine.JUDGE_PASS, QcJudgeEngine.judgeDetail(d));
        d.setMeasuredValue("9.95");
        assertEquals(QcJudgeEngine.JUDGE_PASS, QcJudgeEngine.judgeDetail(d));
        d.setMeasuredValue("10.06");
        assertEquals(QcJudgeEngine.JUDGE_FAIL, QcJudgeEngine.judgeDetail(d));
        d.setMeasuredValue("9.94");
        assertEquals(QcJudgeEngine.JUDGE_FAIL, QcJudgeEngine.judgeDetail(d));
    }

    @Test
    void asymmetricTolerance() {
        QcInspectDetail d = detail("QUANTITATIVE", "NOMINAL");
        d.setNominalValue(new BigDecimal("10"));
        d.setTolUpper(new BigDecimal("0.2"));
        d.setTolLower(new BigDecimal("0.1"));
        d.setMeasuredValue("10.15");
        assertEquals(QcJudgeEngine.JUDGE_PASS, QcJudgeEngine.judgeDetail(d));
        d.setMeasuredValue("9.88"); // 低于 10-0.1=9.9
        assertEquals(QcJudgeEngine.JUDGE_FAIL, QcJudgeEngine.judgeDetail(d));
    }

    // ============ 明细级: 定性 ============

    @Test
    void qualitativePassWords() {
        QcInspectDetail d = detail("QUALITATIVE", null);
        d.setMeasuredValue("OK");
        assertEquals(QcJudgeEngine.JUDGE_PASS, QcJudgeEngine.judgeDetail(d));
        d.setMeasuredValue("合格");
        assertEquals(QcJudgeEngine.JUDGE_PASS, QcJudgeEngine.judgeDetail(d));
        d.setMeasuredValue("NG");
        assertEquals(QcJudgeEngine.JUDGE_FAIL, QcJudgeEngine.judgeDetail(d));
    }

    // ============ 明细级: 多值实测 / 异常输入 ============

    @Test
    void multiValueMeasuredAllInStandard() {
        QcInspectDetail d = detail("QUANTITATIVE", "LSL_USL");
        d.setLslValue(new BigDecimal("2.9"));
        d.setUslValue(new BigDecimal("3.1"));
        d.setMeasuredValue("3.02, 2.95 3.10");
        assertEquals(QcJudgeEngine.JUDGE_PASS, QcJudgeEngine.judgeDetail(d));
        d.setMeasuredValue("3.02, 2.89");
        assertEquals(QcJudgeEngine.JUDGE_FAIL, QcJudgeEngine.judgeDetail(d));
    }

    @Test
    void missingOrInvalidMeasuredFails() {
        QcInspectDetail d = detail("QUANTITATIVE", "LSL_USL");
        d.setLslValue(new BigDecimal("2.9"));
        d.setUslValue(new BigDecimal("3.1"));
        d.setMeasuredValue(null);
        assertEquals(QcJudgeEngine.JUDGE_FAIL, QcJudgeEngine.judgeDetail(d));
        d.setMeasuredValue("abc");
        assertEquals(QcJudgeEngine.JUDGE_FAIL, QcJudgeEngine.judgeDetail(d));
    }

    // ============ 单级汇总 ============

    @Test
    void allPassQualified() {
        List<QcInspectDetail> details = new ArrayList<>();
        details.add(judged("MAJOR", "PASS"));
        details.add(judged("CRITICAL", "PASS"));
        assertEquals(QcJudgeEngine.RESULT_QUALIFIED, QcJudgeEngine.judgeOverall(details));
    }

    @Test
    void criticalFailIsUnqualified() {
        List<QcInspectDetail> details = new ArrayList<>();
        details.add(judged("MAJOR", "PASS"));
        details.add(judged("CRITICAL", "FAIL"));
        assertEquals(QcJudgeEngine.RESULT_UNQUALIFIED, QcJudgeEngine.judgeOverall(details));
    }

    @Test
    void onlyMinorFailIsPartial() {
        List<QcInspectDetail> details = new ArrayList<>();
        details.add(judged("MAJOR", "PASS"));
        details.add(judged("MINOR", "FAIL"));
        assertEquals(QcJudgeEngine.RESULT_PARTIAL, QcJudgeEngine.judgeOverall(details));
    }

    @Test
    void allPendingReturnsNullForManualFallback() {
        List<QcInspectDetail> details = new ArrayList<>();
        details.add(judged("MAJOR", "PENDING"));
        assertEquals(null, QcJudgeEngine.judgeOverall(details));
        assertEquals(null, QcJudgeEngine.judgeOverall(null));
    }

    @Test
    void pendingSkippedAndMixedJudged() {
        List<QcInspectDetail> details = new ArrayList<>();
        details.add(judged("MAJOR", "PENDING"));
        details.add(judged("CRITICAL", "PASS"));
        assertEquals(QcJudgeEngine.RESULT_QUALIFIED, QcJudgeEngine.judgeOverall(details));
    }

    // ============ MES-QC-06: NOMINAL 三值 null 必须 FAIL 而不是 NPE ============

    @Test
    void nominalWithMissingNominalValueFailsWithoutNpe() {
        QcInspectDetail d = detail("QUANTITATIVE", "NOMINAL");
        d.setTolUpper(new BigDecimal("0.1"));
        d.setTolLower(new BigDecimal("0.1"));
        d.setMeasuredValue("10.0");

        assertEquals(QcJudgeEngine.JUDGE_FAIL, QcJudgeEngine.judgeDetail(d), "名义值缺失必须 FAIL 而非 NPE");
    }

    @Test
    void nominalWithMissingTolerancesFailsWithoutNpe() {
        QcInspectDetail d = detail("QUANTITATIVE", "NOMINAL");
        d.setNominalValue(new BigDecimal("10"));
        d.setMeasuredValue("10.0");
        assertEquals(QcJudgeEngine.JUDGE_FAIL, QcJudgeEngine.judgeDetail(d), "上下公差均缺失必须 FAIL 而非 NPE");

        d.setTolUpper(new BigDecimal("0.1"));
        d.setMeasuredValue("10.0");
        assertEquals(QcJudgeEngine.JUDGE_FAIL, QcJudgeEngine.judgeDetail(d), "下公差缺失必须 FAIL 而非 NPE");

        d.setTolUpper(null);
        d.setTolLower(new BigDecimal("0.1"));
        d.setMeasuredValue("10.0");
        assertEquals(QcJudgeEngine.JUDGE_FAIL, QcJudgeEngine.judgeDetail(d), "上公差缺失必须 FAIL 而非 NPE");
    }

    @Test
    void nominalWithAllThreeValuesStillJudges() {
        QcInspectDetail d = detail("QUANTITATIVE", "NOMINAL");
        d.setNominalValue(new BigDecimal("10"));
        d.setTolUpper(new BigDecimal("0.1"));
        d.setTolLower(new BigDecimal("0.1"));
        d.setMeasuredValue("10.0");

        assertEquals(QcJudgeEngine.JUDGE_PASS, QcJudgeEngine.judgeDetail(d));
    }

    private QcInspectDetail judged(String criticality, String result) {
        QcInspectDetail d = new QcInspectDetail();
        d.setCriticality(criticality);
        d.setJudgeResult(result);
        return d;
    }
}
