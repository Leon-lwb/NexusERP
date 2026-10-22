package com.nexus.report.pivot;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportDsl;
import com.nexus.report.engine.*;

import java.util.*;
import java.util.stream.Collectors;

/** 把交叉结果投影为三种导出共同消费的精确二维表。 */
public final class PivotProjection {
    /**
     * 投影列和数据保持同一次快照聚合结果。
     *
     * @param dsl 不可变的报表定义
     * @param data 同一固定快照投影出的数据
     */
    public record Projection(ReportDsl dsl, ReportCalculator.CalculatedData data) {}

    /**
     * 创建受控列名；所有业务数值保持TypedValue精确字符串。
     *
     * @param source 当前契约的source配置或状态
     * @param result 当前契约的result配置或状态
     * @return 当前受控处理的固定结果
     */
    public Projection project(ReportDsl source, AggregationExecutor.Result result) {
        var columns = new ArrayList<ReportDsl.Column>();
        var definition = source.pivot();
        int axisCount = Math.max(1, definition.rowDimensions().size());
        for (int i = 0; i < axisCount; i++)
            columns.add(
                    new ReportDsl.Column(
                            "axis_" + i,
                            definition.rowDimensions().isEmpty()
                                    ? "分类"
                                    : definition.rowDimensions().get(i),
                            80,
                            0,
                            null,
                            null));
        for (int c = 0; c < result.columns().size(); c++)
            for (int m = 0; m < definition.measures().size(); m++) {
                var measure = definition.measures().get(m);
                String label =
                        result.columns().get(c).stream()
                                .map(value -> value.value() == null ? "（空）" : value.displayText())
                                .collect(Collectors.joining(" / "));
                columns.add(
                        new ReportDsl.Column(
                                "cell_" + c + "_" + m,
                                (label.isEmpty() ? "" : label + " / ") + measure.label(),
                                80,
                                measure.scale(),
                                null,
                                null));
            }
        for (int m = 0; m < definition.measures().size(); m++)
            columns.add(
                    new ReportDsl.Column(
                            "total_" + m,
                            "合计 / " + definition.measures().get(m).label(),
                            80,
                            definition.measures().get(m).scale(),
                            null,
                            null));
        var rows = new ArrayList<Map<String, TypedValue>>();
        for (var sourceRow : result.rows()) {
            var row = new LinkedHashMap<String, TypedValue>();
            for (int i = 0; i < axisCount; i++)
                row.put(
                        "axis_" + i,
                        i < sourceRow.keys().size()
                                ? sourceRow.keys().get(i)
                                : TypedValue.of(
                                        ValueType.STRING, sourceRow.subtotal() ? "小计" : ""));
            for (int c = 0; c < sourceRow.cells().size(); c++)
                for (int m = 0; m < definition.measures().size(); m++)
                    row.put("cell_" + c + "_" + m, sourceRow.cells().get(c).values().get(m));
            for (int m = 0; m < sourceRow.total().size(); m++)
                row.put("total_" + m, sourceRow.total().get(m));
            rows.add(row);
        }
        /** 将每个安全聚合分区总计作为普通精确行，所有导出复用同一数据。 */
        for (var total : result.totals()) {
            var row = new LinkedHashMap<String, TypedValue>();
            for (int i = 0; i < axisCount; i++) {
                String dimension =
                        definition.rowDimensions().isEmpty()
                                ? null
                                : definition.rowDimensions().get(i);
                var partition = total.partition().get(dimension);
                row.put(
                        "axis_" + i,
                        partition != null
                                ? partition
                                : TypedValue.of(ValueType.STRING, i == 0 ? "总计" : ""));
            }
            if (row.values().stream().noneMatch(value -> "总计".equals(value.value()))) {
                var first = row.get("axis_0");
                row.put(
                        "axis_0",
                        TypedValue.of(
                                ValueType.STRING,
                                "总计 / " + (first.value() == null ? "（空）" : first.displayText())));
            }
            for (int c = 0; c < total.cells().size(); c++)
                for (int m = 0; m < definition.measures().size(); m++)
                    row.put("cell_" + c + "_" + m, total.cells().get(c).values().get(m));
            for (int m = 0; m < total.values().size(); m++)
                row.put("total_" + m, total.values().get(m));
            rows.add(row);
        }
        var dsl =
                new ReportDsl(
                        1,
                        source.code(),
                        source.title(),
                        ReportDsl.Kind.TABLE,
                        source.datasetCode(),
                        source.datasetVersionId(),
                        columns,
                        source.parameters(),
                        source.page(),
                        List.of(),
                        List.of(),
                        List.of(),
                        true);
        return new Projection(dsl, new ReportCalculator.CalculatedData(rows, List.of()));
    }
}
