package com.nexus.report.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.domain.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

/** 报表计算器测试，负责当前模块对应的契约或处理职责。 */
class ReportCalculatorTest {
    /** 当前服务端身份。 */
    private final ReportActor actor = new ReportActor("A", 1, 10, true, List.of());

    /** 保存当前 {@code dataset} 值。 */
    private final DatasetDescriptor dataset =
            new DatasetDescriptor(
                    "TEST",
                    "测试",
                    1,
                    "wm:stock:list",
                    false,
                    List.of(
                            new DatasetField(
                                    "quantity", "数量", ValueType.DECIMAL, null, List.of("unit")),
                            new DatasetField("unit", "单位", ValueType.STRING)),
                    List.of(),
                    List.of("quantity", "unit"));

    /**
     * 验证 {@code compiled} 场景下的结果与边界条件。
     *
     * @return 当前处理返回的固定结果
     */
    private CompiledReport compiled() {
        var dsl =
                new ReportDsl(
                        1,
                        "TEST_REPORT",
                        "测试",
                        ReportDsl.Kind.TABLE,
                        "TEST",
                        "1",
                        List.of(
                                new ReportDsl.Column("quantity", "数量", 100, 4, null, null),
                                new ReportDsl.Column(
                                        "doubleQty", "计算数量", 100, 4, "quantity*2", null)),
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of(
                                new ReportDsl.Summary("quantity", "SUM", List.of("unit"), "数量", 4),
                                new ReportDsl.Summary(null, "COUNT_ROWS", List.of(), "条数", 0)),
                        true);
        return new ReportCompiler(new ObjectMapper()).compile(dsl, dataset, actor);
    }

    /** 验证 {@code calculationsKeepExactValuesAndSummariesRetainTheirUnitPartition} 场景下的结果与边界条件。 */
    @Test
    void calculationsKeepExactValuesAndSummariesRetainTheirUnitPartition() {
        var data =
                new ReportCalculator()
                        .calculate(
                                compiled(),
                                List.of(
                                        Map.of("quantity", new BigDecimal("0.1"), "unit", "KG"),
                                        Map.of("quantity", new BigDecimal("0.2"), "unit", "KG"),
                                        Map.of("quantity", new BigDecimal("1"), "unit", "PCS")),
                                Map.of());
        assertEquals("0.2", data.rows().get(0).get("doubleQty").value());
        assertEquals("0.3000", data.summaries().get(0).value().value());
        assertEquals("KG", data.summaries().get(0).group().get("unit").value());
        assertEquals("1.0000", data.summaries().get(1).value().value());
        assertEquals("3", data.summaries().get(2).value().value());
    }

    /** 验证 {@code nullDoesNotBecomeAFalseBusinessZero} 场景下的结果与边界条件。 */
    @Test
    void nullDoesNotBecomeAFalseBusinessZero() {
        var row = new HashMap<String, Object>();
        row.put("quantity", null);
        row.put("unit", "KG");
        var data = new ReportCalculator().calculate(compiled(), List.of(row), Map.of());
        assertNull(data.rows().get(0).get("doubleQty").value());
        assertNull(data.summaries().get(0).value().value());
    }

    /** 验证 {@code boundedAggregateFormulaIsComputedAcrossAllRows} 场景下的结果与边界条件。 */
    @Test
    void boundedAggregateFormulaIsComputedAcrossAllRows() {
        var dsl =
                new ReportDsl(
                        1,
                        "TEST_REPORT",
                        "测试",
                        ReportDsl.Kind.TABLE,
                        "TEST",
                        "1",
                        List.of(new ReportDsl.Column("allQty", "总量", 100, 4, "SUM(score)", null)),
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        true);
        var metric =
                new DatasetDescriptor(
                        "TEST",
                        "无量纲测试",
                        1,
                        "wm:stock:list",
                        false,
                        List.of(new DatasetField("score", "分值", ValueType.DECIMAL)),
                        List.of(),
                        List.of("score"));
        var compiled = new ReportCompiler(new ObjectMapper()).compile(dsl, metric, actor);
        var result =
                new ReportCalculator()
                        .calculate(
                                compiled,
                                List.of(
                                        Map.of("score", new BigDecimal("0.1")),
                                        Map.of("score", new BigDecimal("0.2"))),
                                Map.of());
        assertEquals("0.3", result.rows().get(0).get("allQty").value());
        assertEquals("0.3", result.rows().get(1).get("allQty").value());
    }
}
