package com.nexus.report.engine;

import com.nexus.common.report.*;
import com.nexus.report.domain.*;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;

/** 有界单增长区域分页器，固定区域合并不能跨越明细区域。 */
public final class DocumentLayoutEngine {
    /** 保存当前 {@code BINDING} 值。 */
    private static final Pattern BINDING = Pattern.compile("\\{\\{([A-Za-z_][A-Za-z0-9_]*)}}");

    /** PageBuilder，负责当前模块对应的契约或处理职责。 */
    private static final class PageBuilder {
        /** 保存当前 {@code cells} 值。 */
        final List<RenderPlan.Cell> cells = new ArrayList<>();

        /** 保存当前 {@code cursor} 值。 */
        double cursor;

        /** 保存当前 {@code first} 值。 */
        boolean first;

        /**
         * 初始化PageBuilder并保存所需依赖或不可变值。
         *
         * @param first 当前操作的 {@code first} 输入
         * @param cursor 当前操作的 {@code cursor} 输入
         */
        PageBuilder(boolean first, double cursor) {
            this.first = first;
            this.cursor = cursor;
        }
    }

    /**
     * 使用固定字体计算页面坐标和断行。
     *
     * @param report 已编译报表
     * @param data 精确数据结果
     * @param parameters 按契约解析的输入参数
     * @param font 可信私有字体路径
     * @return 当前处理返回的固定结果
     */
    public RenderPlan layout(
            CompiledReport report,
            ReportCalculator.CalculatedData data,
            Map<String, Object> parameters,
            Path font) {
        return layout(report, data, parameters, font, null);
    }

    /**
     * 第二版合成打印行时，固定区仍绑定原始业务行及逻辑行数。
     *
     * @param report 当前契约的report配置或状态
     * @param data 同一固定快照投影出的数据
     * @param parameters 固定参数契约
     * @param font 固定私有字体引用
     * @param fixedBindings 当前契约的fixedBindings配置或状态
     * @return 当前受控处理的固定结果
     */
    public RenderPlan layout(
            CompiledReport report,
            ReportCalculator.CalculatedData data,
            Map<String, Object> parameters,
            Path font,
            Map<String, TypedValue> fixedBindings) {
        if (data.rows().size() > 5000) throw ReportFault.capacity();
        try (var metrics = new TextMetrics(font)) {
            var dsl = report.dsl();
            var paper = dsl.page();
            int columns = dsl.columns().size();
            double total = dsl.columns().stream().mapToDouble(ReportDsl.Column::width).sum();
            double ratio =
                    dsl.kind() == ReportDsl.Kind.TABLE
                            ? Math.min(1, paper.usableWidth() / total)
                            : 1;
            double[] widths = new double[columns], xs = new double[columns];
            double left = paper.marginLeft();
            for (int c = 0; c < columns; c++) {
                xs[c] = left;
                widths[c] = dsl.columns().get(c).width() * ratio;
                left += widths[c];
            }
            double bodyWidth = Arrays.stream(widths).sum();
            var titleStyle = new ReportDsl.Style(16, true, "CENTER", "#111827", null);
            var titleLines = metrics.wrap(dsl.title(), bodyWidth - 8, titleStyle.fontSize());
            double titleHeight = titleLines.size() * titleStyle.fontSize() * 1.2 + 8;
            double staticHeader =
                    dsl.headerRows().stream().mapToDouble(ReportDsl.BandRow::height).sum();
            double columnHeight = 24;
            for (int column = 0; column < columns; column++) {
                var headerLines =
                        metrics.wrap(dsl.columns().get(column).label(), widths[column] - 8, 10);
                columnHeight = Math.max(columnHeight, headerLines.size() * 12 + 8);
            }
            double top = paper.marginTop() + titleHeight + staticHeader;
            double bottom = paper.height() - paper.marginBottom() - 20;
            if (top + columnHeight + paper.rowHeight() > bottom)
                throw ReportFault.invalid("纸张页眉没有留下足够明细空间");
            List<PageBuilder> builders = new ArrayList<>();
            PageBuilder page = newPage(builders, true, top + columnHeight);
            for (int r = 0; r < data.rows().size(); r++) {
                var row = data.rows().get(r);
                List<List<String>> lines = new ArrayList<>();
                List<TypedValue> values = new ArrayList<>();
                double height = paper.rowHeight();
                for (int c = 0; c < columns; c++) {
                    var column = dsl.columns().get(c);
                    if (!row.containsKey(column.field()))
                        throw new ReportFault(
                                "MISSING_RESULT_FIELD", "渲染缺少必需字段: " + column.field());
                    TypedValue value = row.get(column.field());
                    values.add(value);
                    var text =
                            metrics.wrap(
                                    ReportCalculator.display(value, column.scale()),
                                    widths[c] - 8,
                                    column.style().fontSize());
                    lines.add(text);
                    height = Math.max(height, text.size() * column.style().fontSize() * 1.2 + 8);
                }
                double freshTop = top + (dsl.repeatHeader() ? columnHeight : 0);
                if (height > bottom - freshTop)
                    throw new ReportFault("ROW_TOO_TALL", "明细行超过整页可打印高度，请增加纸张或缩短内容");
                if (page.cursor + height > bottom) page = newPage(builders, false, freshTop);
                if (builders.size() > 200) throw ReportFault.capacity();
                for (int c = 0; c < columns; c++)
                    page.cells.add(
                            new RenderPlan.Cell(
                                    "DETAIL",
                                    r,
                                    c,
                                    1,
                                    1,
                                    xs[c],
                                    page.cursor,
                                    widths[c],
                                    height,
                                    lines.get(c),
                                    dsl.columns().get(c).style(),
                                    values.get(c),
                                    dsl.columns().get(c).scale()));
                page.cursor += height;
            }
            if (data.rows().isEmpty()) {
                page.cells.add(
                        new RenderPlan.Cell(
                                "EMPTY",
                                -1,
                                0,
                                columns,
                                1,
                                paper.marginLeft(),
                                page.cursor,
                                bodyWidth,
                                paper.rowHeight(),
                                List.of("暂无数据"),
                                ReportDsl.Style.normal(),
                                new TypedValue(ValueType.STRING, "暂无数据"),
                                0));
                page.cursor += paper.rowHeight();
            }
            for (var summary : data.summaries()) {
                String group =
                        summary.group().entrySet().stream()
                                .map(
                                        entry ->
                                                entry.getKey()
                                                        + "="
                                                        + (entry.getValue().value() == null
                                                                ? "（空）"
                                                                : entry.getValue().displayText()))
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("");
                String text =
                        (summary.label() == null ? "汇总" : summary.label())
                                + (group.isEmpty() ? "" : " [" + group + "]")
                                + "："
                                + (summary.value().value() == null
                                        ? "（空）"
                                        : summary.value().value());
                var lines = metrics.wrap(text, bodyWidth - 8, 10);
                double height = Math.max(24, lines.size() * 12 + 8);
                double freshTop = top + (dsl.repeatHeader() ? columnHeight : 0);
                if (height > bottom - freshTop)
                    throw new ReportFault("ROW_TOO_TALL", "汇总行超过整页可打印高度");
                if (page.cursor + height > bottom) page = newPage(builders, false, freshTop);
                page.cells.add(
                        new RenderPlan.Cell(
                                "SUMMARY",
                                -1,
                                0,
                                columns,
                                1,
                                paper.marginLeft(),
                                page.cursor,
                                bodyWidth,
                                height,
                                lines,
                                ReportDsl.Style.normal(),
                                new TypedValue(ValueType.STRING, text),
                                0));
                page.cursor += height;
            }
            double footerHeight =
                    dsl.footerRows().stream().mapToDouble(ReportDsl.BandRow::height).sum();
            double freshTop = top + (dsl.repeatHeader() ? columnHeight : 0);
            if (footerHeight > bottom - freshTop) throw ReportFault.invalid("固定尾区超过整页可打印高度");
            if (page.cursor + footerHeight > bottom) page = newPage(builders, false, freshTop);
            if (builders.size() > 200) throw ReportFault.capacity();
            Map<String, TypedValue> bindings = new LinkedHashMap<>();
            if (!data.rows().isEmpty()) bindings.putAll(data.rows().get(0));
            for (var parameter : report.descriptor().parameters())
                bindings.put(
                        "p_" + parameter.name(),
                        TypedValue.of(parameter.type(), parameters.get(parameter.name())));
            bindings.put("row_count", TypedValue.of(ValueType.INTEGER, data.rows().size()));
            if (fixedBindings != null) bindings.putAll(fixedBindings);
            bindings.put("page_count", TypedValue.of(ValueType.INTEGER, builders.size()));
            List<RenderPlan.Page> pages = new ArrayList<>();
            for (int p = 0; p < builders.size(); p++) {
                var builder = builders.get(p);
                bindings.put("page_no", TypedValue.of(ValueType.INTEGER, p + 1));
                builder.cells.add(
                        new RenderPlan.Cell(
                                "TITLE",
                                -1,
                                0,
                                columns,
                                1,
                                paper.marginLeft(),
                                paper.marginTop(),
                                bodyWidth,
                                titleHeight,
                                titleLines,
                                titleStyle,
                                new TypedValue(ValueType.STRING, dsl.title()),
                                0));
                fixed(
                        dsl.headerRows(),
                        "HEADER",
                        paper.marginTop() + titleHeight,
                        widths,
                        xs,
                        bindings,
                        metrics,
                        builder.cells);
                if (p == 0 || dsl.repeatHeader())
                    for (int c = 0; c < columns; c++) {
                        var style = new ReportDsl.Style(10, true, "CENTER", "#111827", "#f3f4f6");
                        var lines = metrics.wrap(dsl.columns().get(c).label(), widths[c] - 8, 10);
                        builder.cells.add(
                                new RenderPlan.Cell(
                                        "COLUMN_HEADER",
                                        -1,
                                        c,
                                        1,
                                        1,
                                        xs[c],
                                        top,
                                        widths[c],
                                        columnHeight,
                                        lines,
                                        style,
                                        new TypedValue(
                                                ValueType.STRING, dsl.columns().get(c).label()),
                                        0));
                    }
                if (p == builders.size() - 1)
                    fixed(
                            dsl.footerRows(),
                            "FOOTER",
                            builder.cursor,
                            widths,
                            xs,
                            bindings,
                            metrics,
                            builder.cells);
                String number = "第 " + (p + 1) + " / " + builders.size() + " 页";
                builder.cells.add(
                        new RenderPlan.Cell(
                                "PAGE_NUMBER",
                                -1,
                                0,
                                columns,
                                1,
                                paper.marginLeft(),
                                bottom + 2,
                                bodyWidth,
                                18,
                                List.of(number),
                                new ReportDsl.Style(10, false, "RIGHT", null, null),
                                new TypedValue(ValueType.STRING, number),
                                0));
                builder.cells.sort(
                        Comparator.comparingDouble(RenderPlan.Cell::y)
                                .thenComparingDouble(RenderPlan.Cell::x));
                pages.add(new RenderPlan.Page(p + 1, paper.width(), paper.height(), builder.cells));
            }
            return new RenderPlan(
                    1,
                    data.rows().size(),
                    metrics.fontName(),
                    pages,
                    dsl.columns(),
                    data.summaries());
        }
    }

    /**
     * 完成 {@code newPage} 对应的单据布局引擎处理。
     *
     * @param pages 当前操作的 {@code pages} 输入
     * @param first 当前操作的 {@code first} 输入
     * @param top 当前操作的 {@code top} 输入
     * @return 当前处理返回的固定结果
     */
    private static PageBuilder newPage(List<PageBuilder> pages, boolean first, double top) {
        if (pages.size() >= 200) throw ReportFault.capacity();
        PageBuilder page = new PageBuilder(first, top);
        pages.add(page);
        return page;
    }

    /**
     * 完成 {@code fixed} 对应的单据布局引擎处理。
     *
     * @param rows 待处理业务行
     * @param band 当前操作的 {@code band} 输入
     * @param top 当前操作的 {@code top} 输入
     * @param widths 当前操作的 {@code widths} 输入
     * @param xs 当前操作的 {@code xs} 输入
     * @param bindings 当前操作的 {@code bindings} 输入
     * @param metrics 当前操作的 {@code metrics} 输入
     * @param cells 当前操作的 {@code cells} 输入
     */
    private static void fixed(
            List<ReportDsl.BandRow> rows,
            String band,
            double top,
            double[] widths,
            double[] xs,
            Map<String, TypedValue> bindings,
            TextMetrics metrics,
            List<RenderPlan.Cell> cells) {
        double y = top;
        for (int r = 0; r < rows.size(); r++) {
            var row = rows.get(r);
            for (var cell : row.cells()) {
                double width = 0, height = 0;
                for (int c = cell.column(); c < cell.column() + cell.colSpan(); c++)
                    width += widths[c];
                for (int rr = r; rr < r + cell.rowSpan(); rr++) height += rows.get(rr).height();
                TypedValue raw =
                        cell.field() != null && !cell.field().isBlank()
                                ? bindings.get(cell.field())
                                : new TypedValue(
                                        ValueType.STRING,
                                        bind(cell.text() == null ? "" : cell.text(), bindings));
                var lines =
                        metrics.wrap(
                                raw == null ? "" : raw.displayText(),
                                width - 8,
                                cell.style().fontSize());
                if (lines.size() * cell.style().fontSize() * 1.2 + 8 > height)
                    throw new ReportFault("BAND_TOO_SHORT", "固定区文字超出声明行高，请增加行高或列宽");
                cells.add(
                        new RenderPlan.Cell(
                                band,
                                -1,
                                cell.column(),
                                cell.colSpan(),
                                cell.rowSpan(),
                                xs[cell.column()],
                                y,
                                width,
                                height,
                                lines,
                                cell.style(),
                                raw,
                                0));
            }
            y += row.height();
        }
    }

    /**
     * 绑定固定数据集及执行器并编译。
     *
     * @param text 当前操作的 {@code text} 输入
     * @param values 绑定值
     * @return 当前处理返回的固定结果
     */
    private static String bind(String text, Map<String, TypedValue> values) {
        var matcher = BINDING.matcher(text);
        var out = new StringBuffer();
        while (matcher.find()) {
            TypedValue value = values.get(matcher.group(1));
            matcher.appendReplacement(
                    out,
                    Matcher.quoteReplacement(
                            value == null ? "" : value.displayText()));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
