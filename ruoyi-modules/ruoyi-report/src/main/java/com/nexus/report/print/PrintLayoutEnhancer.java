package com.nexus.report.print;

import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.engine.*;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** 第二版分组、重复页脚与媒体都落入同一份服务器坐标计划。 */
public final class PrintLayoutEnhancer {
    /**
     * 原始明细与合成打印行的映射，逻辑行数不包含标题和小计。
     *
     * @param rows 精确类型的数据行
     * @param bands 当前契约的bands配置或状态
     * @param sourceIndices 当前契约的sourceIndices配置或状态
     */
    private record Grouped(
            List<Map<String, TypedValue>> rows, List<String> bands, List<Integer> sourceIndices) {}

    /**
     * 从固定数据生成受控打印计划；PNG只读取编译时固定的私有资源。
     *
     * @param report 当前契约的report配置或状态
     * @param data 同一固定快照投影出的数据
     * @param parameters 固定参数契约
     * @param font 固定私有字体引用
     * @return 当前受控处理的固定结果
     */
    public RenderPlan layout(
            CompiledReport report,
            ReportCalculator.CalculatedData data,
            Map<String, Object> parameters,
            Path font) {
        var dsl = report.dsl();
        var print = dsl.print();
        if (print == null) {
            var base = new DocumentLayoutEngine().layout(report, data, parameters, font);
            return new RenderPlan(
                    2,
                    data.rows().size(),
                    base.fontName(),
                    base.pages(),
                    base.columns(),
                    base.summaries());
        }
        var paper = new PrintPlanner().paper(dsl);
        var grouped = group(report, data.rows());
        double footerHeight =
                print.repeatFooter()
                        ? dsl.footerRows().stream().mapToDouble(ReportDsl.BandRow::height).sum()
                        : 0;
        var reserved =
                new ReportDsl.Page(
                        paper.width(),
                        paper.height(),
                        paper.marginTop(),
                        paper.marginRight(),
                        paper.marginBottom() + footerHeight,
                        paper.marginLeft(),
                        paper.rowHeight());
        var projected =
                new ReportDsl(
                        1,
                        dsl.code(),
                        dsl.title(),
                        dsl.kind(),
                        dsl.datasetCode(),
                        dsl.datasetVersionId(),
                        dsl.columns(),
                        dsl.parameters(),
                        reserved,
                        fixCount(dsl.headerRows(), data.rows().size()),
                        print.repeatFooter()
                                ? List.of()
                                : fixCount(dsl.footerRows(), data.rows().size()),
                        dsl.summaries(),
                        dsl.repeatHeader());
        var compiled =
                new CompiledReport(
                        projected,
                        report.descriptor(),
                        report.requiredFields(),
                        report.expressions(),
                        report.computedOrder(),
                        report.scopes());
        var fixedBindings = new LinkedHashMap<String, TypedValue>();
        if (!data.rows().isEmpty()) fixedBindings.putAll(data.rows().get(0));
        fixedBindings.put("row_count", TypedValue.of(ValueType.INTEGER, data.rows().size()));
        var base =
                new DocumentLayoutEngine()
                        .layout(
                                compiled,
                                new ReportCalculator.CalculatedData(
                                        grouped.rows(), data.summaries()),
                                parameters,
                                font,
                                fixedBindings);
        var rasters = new LinkedHashMap<String, RenderPlan.Raster>();
        var media = new ArrayList<RenderPlan.Media>();
        for (var item : print.media()) {
            String png;
            if (item.kind().equals("IMAGE")) {
                png = report.assetRasters().get(item.assetVersionId());
                if (png == null) throw new ReportFault("ASSET_NOT_FOUND", "编译计划没有固定图片", 409);
            } else {
                var value = data.rows().isEmpty() ? null : data.rows().get(0).get(item.field());
                if (value == null || value.value() == null) throw ReportFault.invalid("条码业务字段为空");
                png =
                        Base64.getEncoder()
                                .encodeToString(
                                        new BarcodeRaster()
                                                .render(
                                                        item.kind(),
                                                        value.value(),
                                                        Math.max(
                                                                64,
                                                                (int) Math.round(item.width() * 4)),
                                                        Math.max(
                                                                64,
                                                                (int)
                                                                        Math.round(
                                                                                item.height()
                                                                                        * 4))));
            }
            String checksum = checksum(Base64.getDecoder().decode(png));
            rasters.putIfAbsent(checksum, new RenderPlan.Raster(png, checksum));
            media.add(
                    new RenderPlan.Media(
                            checksum, item.x(), item.y(), item.width(), item.height()));
        }
        var pages = new ArrayList<RenderPlan.Page>();
        try (var metrics = new TextMetrics(font)) {
            for (var page : base.pages()) {
                var cells = new ArrayList<RenderPlan.Cell>();
                for (var cell : page.cells()) {
                    if (cell.band().equals("DETAIL")
                            && cell.rowIndex() >= 0
                            && cell.rowIndex() < grouped.bands().size()) {
                        String band = grouped.bands().get(cell.rowIndex());
                        var style =
                                band.equals("DETAIL")
                                        ? cell.style()
                                        : new ReportDsl.Style(
                                                cell.style().fontSize(),
                                                true,
                                                cell.style().align(),
                                                cell.style().color(),
                                                "#f3f4f6");
                        cells.add(
                                new RenderPlan.Cell(
                                        band,
                                        grouped.sourceIndices().get(cell.rowIndex()),
                                        cell.column(),
                                        cell.colSpan(),
                                        cell.rowSpan(),
                                        cell.x(),
                                        cell.y(),
                                        cell.width(),
                                        cell.height(),
                                        cell.lines(),
                                        style,
                                        cell.value(),
                                        cell.scale()));
                    } else if (cell.band().equals("PAGE_NUMBER") && print.repeatFooter())
                        cells.add(
                                new RenderPlan.Cell(
                                        cell.band(),
                                        cell.rowIndex(),
                                        cell.column(),
                                        cell.colSpan(),
                                        cell.rowSpan(),
                                        cell.x(),
                                        paper.height() - paper.marginBottom() - 18,
                                        cell.width(),
                                        cell.height(),
                                        cell.lines(),
                                        cell.style(),
                                        cell.value(),
                                        cell.scale()));
                    else cells.add(cell);
                }
                if (print.repeatFooter())
                    footer(
                            cells,
                            dsl,
                            data,
                            parameters,
                            paper,
                            page.number(),
                            base.pages().size(),
                            metrics);
                var placed = new ArrayList<RenderPlan.Media>();
                for (int i = 0; i < media.size(); i++)
                    if (page.number() == 1 || print.media().get(i).repeat())
                        placed.add(media.get(i));
                pages.add(
                        new RenderPlan.Page(
                                page.number(), page.width(), page.height(), cells, placed));
            }
        }
        return new RenderPlan(
                2,
                data.rows().size(),
                base.fontName(),
                pages,
                base.columns(),
                base.summaries(),
                rasters);
    }

    /**
     * 以类型化复合键分组，保持每组内部原始顺序。
     *
     * @param report 当前契约的report配置或状态
     * @param rows 精确类型的数据行
     * @return 当前受控处理的固定结果
     */
    private Grouped group(CompiledReport report, List<Map<String, TypedValue>> rows) {
        var fields = report.dsl().print().groupBy();
        if (fields.isEmpty())
            return new Grouped(
                    rows,
                    Collections.nCopies(rows.size(), "DETAIL"),
                    java.util.stream.IntStream.range(0, rows.size()).boxed().toList());
        var buckets = new LinkedHashMap<List<TypedValue>, List<Integer>>();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            buckets.computeIfAbsent(
                            fields.stream().map(row::get).toList(), key -> new ArrayList<>())
                    .add(i);
        }
        var output = new ArrayList<Map<String, TypedValue>>();
        var bands = new ArrayList<String>();
        var indices = new ArrayList<Integer>();
        for (var bucket : buckets.entrySet()) {
            var header = blank(report);
            var parts = new ArrayList<String>();
            for (int i = 0; i < fields.size(); i++)
                parts.add(
                        report.descriptor().field(fields.get(i)).label()
                                + "："
                                + (bucket.getKey().get(i).value() == null
                                        ? "（空）"
                                        : bucket.getKey().get(i).displayText()));
            header.put(
                    report.dsl().columns().get(0).field(),
                    TypedValue.of(ValueType.STRING, String.join(" / ", parts)));
            output.add(header);
            bands.add("GROUP_HEADER");
            indices.add(-1);
            var summary = new ReportCalculator.SummaryAccumulator(report);
            for (int index : bucket.getValue()) {
                output.add(rows.get(index));
                bands.add("DETAIL");
                indices.add(index);
                summary.accept(rows.get(index));
            }
            if (!report.dsl().summaries().isEmpty()) {
                var subtotal = blank(report);
                var values = summary.finish();
                String text =
                        "分组小计："
                                + values.stream()
                                        .map(
                                                value -> {
                                                    String keys =
                                                            value.group().entrySet().stream()
                                                                    .map(
                                                                            entry ->
                                                                                    entry.getKey()
                                                                                            + "="
                                                                                            + (entry.getValue()
                                                                                                                    .value()
                                                                                                            == null
                                                                                                    ? "（空）"
                                                                                                    : entry.getValue()
                                                                                                            .displayText()))
                                                                    .collect(
                                                                            java.util.stream
                                                                                    .Collectors
                                                                                    .joining("，"));
                                                    return value.label()
                                                            + (keys.isEmpty()
                                                                    ? ""
                                                                    : " [" + keys + "]")
                                                            + "："
                                                            + (value.value().value() == null
                                                                    ? "（空）"
                                                                    : value.value().value());
                                                })
                                        .collect(java.util.stream.Collectors.joining("；"));
                subtotal.put(
                        report.dsl().columns().get(0).field(),
                        TypedValue.of(ValueType.STRING, text));
                output.add(subtotal);
                bands.add("GROUP_SUBTOTAL");
                indices.add(-1);
            }
        }
        return new Grouped(output, bands, indices);
    }

    /**
     * 合成打印行只承载展示文本，不改变业务快照。
     *
     * @param report 当前契约的report配置或状态
     * @return 当前受控处理的固定结果
     */
    private Map<String, TypedValue> blank(CompiledReport report) {
        var row = new LinkedHashMap<String, TypedValue>();
        for (var column : report.dsl().columns())
            row.put(column.field(), TypedValue.of(ValueType.STRING, ""));
        return row;
    }

    /**
     * 合成行不影响固定区的逻辑行数绑定。
     *
     * @param bands 当前契约的bands配置或状态
     * @param count 当前契约的count配置或状态
     * @return 当前受控处理的固定结果
     */
    private List<ReportDsl.BandRow> fixCount(List<ReportDsl.BandRow> bands, int count) {
        return bands.stream()
                .map(
                        row ->
                                new ReportDsl.BandRow(
                                        row.height(),
                                        row.cells().stream()
                                                .map(
                                                        cell ->
                                                                new ReportDsl.Cell(
                                                                        cell.column(),
                                                                        cell.colSpan(),
                                                                        cell.rowSpan(),
                                                                        "row_count"
                                                                                        .equals(
                                                                                                cell
                                                                                                        .field())
                                                                                ? Integer.toString(
                                                                                        count)
                                                                                : cell.text()
                                                                                                == null
                                                                                        ? null
                                                                                        : cell.text()
                                                                                                .replace(
                                                                                                        "{{row_count}}",
                                                                                                        Integer
                                                                                                                .toString(
                                                                                                                        count)),
                                                                        "row_count"
                                                                                        .equals(
                                                                                                cell
                                                                                                        .field())
                                                                                ? null
                                                                                : cell.field(),
                                                                        cell.style()))
                                                .toList()))
                .toList();
    }

    /**
     * 重复页脚预留空间后，按固定字体生成每页相同规则的坐标和页码。
     *
     * @param output 当前契约的output配置或状态
     * @param dsl 不可变的报表定义
     * @param data 同一固定快照投影出的数据
     * @param parameters 固定参数契约
     * @param paper 当前契约的paper配置或状态
     * @param page 当前契约的page配置或状态
     * @param pages 当前契约的pages配置或状态
     * @param metrics 当前契约的metrics配置或状态
     */
    private void footer(
            List<RenderPlan.Cell> output,
            ReportDsl dsl,
            ReportCalculator.CalculatedData data,
            Map<String, Object> parameters,
            ReportDsl.Page paper,
            int page,
            int pages,
            TextMetrics metrics) {
        double height = dsl.footerRows().stream().mapToDouble(ReportDsl.BandRow::height).sum();
        double y = paper.height() - paper.marginBottom() - 20 - height;
        double[] widths = dsl.columns().stream().mapToDouble(ReportDsl.Column::width).toArray();
        var values = new LinkedHashMap<String, String>();
        if (!data.rows().isEmpty())
            data.rows()
                    .get(0)
                    .forEach(
                            (key, value) ->
                                    values.put(key, value.displayText()));
        parameters.forEach((key, value) -> values.put("p_" + key, Objects.toString(value, "")));
        values.put("row_count", Integer.toString(data.rows().size()));
        values.put("page_no", Integer.toString(page));
        values.put("page_count", Integer.toString(pages));
        for (int r = 0; r < dsl.footerRows().size(); r++) {
            var row = dsl.footerRows().get(r);
            for (var cell : row.cells()) {
                double x = paper.marginLeft();
                for (int c = 0; c < cell.column(); c++) x += widths[c];
                double w = 0;
                for (int c = cell.column(); c < cell.column() + cell.colSpan(); c++) w += widths[c];
                double h = 0;
                for (int rr = r; rr < r + cell.rowSpan(); rr++)
                    h += dsl.footerRows().get(rr).height();
                String text =
                        cell.field() == null
                                ? Objects.toString(cell.text(), "")
                                : values.getOrDefault(cell.field(), "");
                var pattern = Pattern.compile("\\{\\{([A-Za-z_][A-Za-z0-9_]*)}}");
                var matcher = pattern.matcher(text);
                var bound = new StringBuffer();
                while (matcher.find())
                    matcher.appendReplacement(
                            bound,
                            Matcher.quoteReplacement(values.getOrDefault(matcher.group(1), "")));
                matcher.appendTail(bound);
                var lines = metrics.wrap(bound.toString(), w - 8, cell.style().fontSize());
                if (lines.size() * cell.style().fontSize() * 1.2 + 8 > h)
                    throw new ReportFault("FOOTER_TOO_TALL", "重复页脚文字超过固定区域", 409);
                output.add(
                        new RenderPlan.Cell(
                                "FOOTER",
                                r,
                                cell.column(),
                                cell.colSpan(),
                                cell.rowSpan(),
                                x,
                                y,
                                w,
                                h,
                                lines,
                                cell.style(),
                                TypedValue.of(ValueType.STRING, bound.toString()),
                                0));
            }
            y += row.height();
        }
    }

    /**
     * 对实际PNG字节计算摘要，所有输出共享同一图片。
     *
     * @param bytes 保留或实际占用的字节数
     * @return 当前受控处理的固定结果
     */
    private static String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
