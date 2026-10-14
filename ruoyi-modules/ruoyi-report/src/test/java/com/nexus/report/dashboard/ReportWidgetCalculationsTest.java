package com.nexus.report.dashboard;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportFault;

import org.junit.jupiter.api.Test;

import java.util.*;

/** 仪表盘精确指标、变化率和受控投影回归。 */
class ReportWidgetCalculationsTest {
    /**
     * 合成金额字段及币种维度。
     *
     * @return 当前受控处理的固定结果
     */
    private DatasetField field() {
        return new DatasetField("amount", "金额", ValueType.DECIMAL, null, List.of("currency"));
    }

    /**
     * 构造精确值，绝不使用double存储业务数值。
     *
     * @param category 当前契约的category配置或状态
     * @param currency 当前契约的currency配置或状态
     * @param amount 当前契约的amount配置或状态
     * @return 当前受控处理的固定结果
     */
    private Map<String, TypedValue> row(String category, String currency, String amount) {
        return Map.of(
                "category",
                TypedValue.of(ValueType.STRING, category),
                "currency",
                TypedValue.of(ValueType.STRING, currency),
                "amount",
                TypedValue.of(ValueType.DECIMAL, amount));
    }

    /** 执行metricsKeepExactValuesAndZeroComparisonIsUndefined对应的受控处理。 */
    @Test
    void metricsKeepExactValuesAndZeroComparisonIsUndefined() {
        var engine = new WidgetCalculations();
        var rows = List.of(row("A", "CNY", "9007199254740993.005"), row("B", "CNY", "0.005"));
        assertEquals("9007199254740993.010", engine.metric(field(), "SUM", rows).value());
        assertNull(
                engine.rate(
                                TypedValue.of(ValueType.DECIMAL, "20"),
                                TypedValue.of(ValueType.DECIMAL, "0"))
                        .value());
        assertEquals(
                "25.000000",
                engine.rate(
                                TypedValue.of(ValueType.DECIMAL, "25"),
                                TypedValue.of(ValueType.DECIMAL, "20"))
                        .value());
    }

    /** 执行mixedCurrenciesAreRejectedAndProjectionNeverChangesExactTooltip对应的受控处理。 */
    @Test
    void mixedCurrenciesAreRejectedAndProjectionNeverChangesExactTooltip() {
        var engine = new WidgetCalculations();
        assertThrows(
                ReportFault.class,
                () ->
                        engine.metric(
                                field(),
                                "SUM",
                                List.of(row("A", "CNY", "1"), row("B", "USD", "2"))));
        var chart =
                engine.chart(
                        "category",
                        field(),
                        "SUM",
                        List.of(row("A", "CNY", "9007199254740993.005"), row("B", "CNY", "1")));
        assertEquals("9007199254740993.005", chart.points().get(0).exact().value());
        assertEquals(1.0, chart.points().get(0).coordinate());
        assertEquals("9007199254740993.005", chart.scale());
    }

    /** 执行averageUsesEveryNonNullRecord对应的受控处理。 */
    @Test
    void averageUsesEveryNonNullRecord() {
        var rows = List.of(row("A", "CNY", "10"), row("A", "CNY", "2"), row("B", "CNY", "3"));
        assertEquals(
                "5.000000000000", new WidgetCalculations().metric(field(), "AVG", rows).value());
    }

    /** 比较两个期间之前，币种分区必须一致。 */
    @Test
    void comparisonRejectsDifferentCurrencyPartitions() {
        var calculations = new WidgetCalculations();
        assertThrows(
                ReportFault.class,
                () ->
                        calculations.comparable(
                                field(),
                                "SUM",
                                List.of(row("A", "CNY", "20")),
                                List.of(row("A", "USD", "10"))));
    }
}
