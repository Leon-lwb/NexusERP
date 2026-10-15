package com.nexus.report.export;

import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.engine.RenderPlan;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

/** 显式字符串单元格阻止公式注入，超出 Excel 精度的值保留为精确文本。 */
public final class ReportSpreadsheetExporter {
    /** 保存当前 {@code artifacts} 值。 */
    private final com.nexus.report.storage.ReportArtifactStore artifacts;

    /** 当前租户标识。 */
    private final String tenant;

    /** 保存当前 {@code temporaryBudget} 值。 */
    private final long temporaryBudget;

    /** 初始化报表电子表格Exporter并保存所需依赖或不可变值。 */
    public ReportSpreadsheetExporter() {
        this(null, "standalone", 200L * 1024 * 1024);
    }

    /**
     * 初始化报表电子表格Exporter并保存所需依赖或不可变值。
     *
     * @param artifacts 当前操作的 {@code artifacts} 输入
     * @param tenant 当前租户标识
     * @param temporaryBudget 当前操作的 {@code temporaryBudget} 输入
     */
    public ReportSpreadsheetExporter(
            com.nexus.report.storage.ReportArtifactStore artifacts,
            String tenant,
            long temporaryBudget) {
        this.artifacts = artifacts;
        this.tenant = tenant;
        this.temporaryBudget = temporaryBudget;
    }

    /** RowReader，负责当前模块对应的契约或处理职责。 */
    @FunctionalInterface
    public interface RowReader {
        /**
         * 读取并检查当前租户私有产物。
         *
         * @param consumer 当前操作的 {@code consumer} 输入
         */
        void read(Consumer<Map<String, TypedValue>> consumer);
    }

    /**
     * 写入精确明细表格。
     *
     * @param dsl 报表定义
     * @param reader 当前操作的 {@code reader} 输入
     * @param output 受控输出流
     * @param cancellation 执行取消信号
     */
    public void data(
            ReportDsl dsl, RowReader reader, OutputStream output, ReportCancellation cancellation) {
        cancellation.check();
        var staging =
                artifacts != null
                        ? artifacts
                        : new com.nexus.report.storage.LocalReportArtifactStore(
                                java.nio.file.Path.of(
                                        System.getProperty("java.io.tmpdir"),
                                        "nexus-report-private"),
                                temporaryBudget);
        var workbook = new ReportPrivateWorkbook(staging, tenant, temporaryBudget, cancellation);
        try {
            var sheet = workbook.createSheet("明细");
            sheet.createFreezePane(0, 1);
            var header = sheet.createRow(0);
            for (int c = 0; c < dsl.columns().size(); c++) {
                header.createCell(c, CellType.STRING).setCellValue(dsl.columns().get(c).label());
                sheet.setColumnWidth(
                        c,
                        Math.min(
                                255 * 256,
                                Math.max(
                                        12 * 256,
                                        (int) (dsl.columns().get(c).width() / 5.25 * 256))));
            }
            Map<Integer, CellStyle> formats = new HashMap<>();
            int[] index = {1};
            reader.read(
                    values -> {
                        cancellation.check();
                        if (index[0] > 100000) throw ReportFault.capacity();
                        var row = sheet.createRow(index[0]++);
                        for (int c = 0; c < dsl.columns().size(); c++) {
                            var column = dsl.columns().get(c);
                            var cell = row.createCell(c);
                            var value = values.get(column.field());
                            setValue(cell, value);
                            if (cell.getCellType() == CellType.NUMERIC)
                                cell.setCellStyle(
                                        formats.computeIfAbsent(
                                                column.scale(),
                                                scale -> {
                                                    var style = workbook.createCellStyle();
                                                    style.setDataFormat(
                                                            workbook.createDataFormat()
                                                                    .getFormat(
                                                                            numberFormat(scale)));
                                                    return style;
                                                }));
                        }
                    });
            cancellation.check();
            workbook.writeAvoidingTempFiles(output);
        } catch (IOException e) {
            throw new ReportFault("XLSX_EXPORT_FAILED", "Excel 生成失败", 500);
        } finally {
            workbook.prepareRelease();
            try {
                workbook.close();
            } catch (IOException | RuntimeException ignored) {
            }
            try {
                workbook.dispose();
            } catch (RuntimeException ignored) {
            } finally {
                workbook.release();
            }
        }
    }

    /**
     * 使用固定字体计算页面坐标和断行。
     *
     * @param plan 当前操作的 {@code plan} 输入
     * @param output 受控输出流
     * @param cancellation 执行取消信号
     */
    public void layout(RenderPlan plan, OutputStream output, ReportCancellation cancellation) {
        try (var workbook = new XSSFWorkbook()) {
            Map<String, CellStyle> styles = new HashMap<>();
            for (var page : plan.pages()) {
                cancellation.check();
                var sheet = workbook.createSheet("第" + page.number() + "页");
                var xs = new TreeSet<Double>();
                var ys = new TreeSet<Double>();
                xs.add(0d);
                xs.add(round(page.width()));
                ys.add(0d);
                ys.add(round(page.height()));
                for (var cell : page.cells()) {
                    xs.add(round(cell.x()));
                    xs.add(round(cell.x() + cell.width()));
                    ys.add(round(cell.y()));
                    ys.add(round(cell.y() + cell.height()));
                }
                /** Excel 对列宽和行高有上限，较大的坐标区间需要拆分。 */
                for (var media : page.media()) {
                    xs.add(round(media.x()));
                    xs.add(round(media.x() + media.width()));
                    ys.add(round(media.y()));
                    ys.add(round(media.y() + media.height()));
                }
                var x = grid(xs, 255 * 5.25);
                var y = grid(ys, 409);
                for (int c = 0; c < x.size() - 1; c++)
                    sheet.setColumnWidth(
                            c,
                            Math.min(
                                    255 * 256,
                                    Math.max(
                                            1,
                                            (int)
                                                    (Math.round(x.get(c + 1) / 5.25 * 256)
                                                            - Math.round(x.get(c) / 5.25 * 256)))));
                for (int r = 0; r < y.size() - 1; r++)
                    sheet.createRow(r)
                            .setHeightInPoints(
                                    (Math.round(y.get(r + 1) * 20) - Math.round(y.get(r) * 20))
                                            / 20f);
                for (var source : page.cells()) {
                    cancellation.check();
                    int c = x.indexOf(round(source.x())), r = y.indexOf(round(source.y()));
                    int endC = x.indexOf(round(source.x() + source.width())) - 1,
                            endR = y.indexOf(round(source.y() + source.height())) - 1;
                    var cell = sheet.getRow(r).createCell(c);
                    setLayoutValue(cell, source);
                    var style = source.style();
                    int scale =
                            source.value() != null && source.value().type() == ValueType.INTEGER
                                    ? 0
                                    : source.scale();
                    String key = style.toString() + ":" + scale;
                    cell.setCellStyle(
                            styles.computeIfAbsent(
                                    key, k -> style(workbook, style, scale, plan.fontName())));
                    if (endC > c || endR > r)
                        sheet.addMergedRegion(new CellRangeAddress(r, endR, c, endC));
                }
                if (!page.media().isEmpty()) {
                    var drawing = sheet.createDrawingPatriarch();
                    for (var media : page.media()) {
                        cancellation.check();
                        var raster = plan.rasters().get(media.resource());
                        if (raster == null) throw new ReportFault("PLAN_INVALID", "媒体资源缺失", 409);
                        int picture =
                                workbook.addPicture(
                                        java.util.Base64.getDecoder().decode(raster.png()),
                                        Workbook.PICTURE_TYPE_PNG);
                        var anchor =
                                new org.apache.poi.xssf.usermodel.XSSFClientAnchor(
                                        0,
                                        0,
                                        0,
                                        0,
                                        x.indexOf(round(media.x())),
                                        y.indexOf(round(media.y())),
                                        x.indexOf(round(media.x() + media.width())),
                                        y.indexOf(round(media.y() + media.height())));
                        anchor.setAnchorType(ClientAnchor.AnchorType.DONT_MOVE_AND_RESIZE);
                        drawing.createPicture(anchor, picture);
                    }
                }
                for (short margin :
                        new short[] {
                            Sheet.LeftMargin,
                            Sheet.RightMargin,
                            Sheet.TopMargin,
                            Sheet.BottomMargin,
                            Sheet.HeaderMargin,
                            Sheet.FooterMargin
                        }) sheet.setMargin(margin, 0);
                workbook.setPrintArea(
                        workbook.getSheetIndex(sheet), 0, x.size() - 2, 0, y.size() - 2);
                var print = sheet.getPrintSetup();
                print.setLandscape(page.width() > page.height());
                print.setPaperSize(PrintSetup.A4_PAPERSIZE);
                print.setFitWidth((short) 1);
                print.setFitHeight((short) 1);
                sheet.setFitToPage(true);
            }
            cancellation.check();
            workbook.write(output);
        } catch (IOException e) {
            throw new ReportFault("XLSX_EXPORT_FAILED", "Excel 生成失败", 500);
        }
    }

    /**
     * 完成 {@code grid} 对应的报表电子表格Exporter处理。
     *
     * @param points 当前操作的 {@code points} 输入
     * @param maximum 当前操作的 {@code maximum} 输入
     * @return 当前处理返回的固定结果
     */
    private static List<Double> grid(NavigableSet<Double> points, double maximum) {
        var boundaries = new ArrayList<>(points);
        for (int i = 1; i < boundaries.size(); i++) {
            double start = boundaries.get(i - 1), gap = boundaries.get(i) - start;
            int pieces = (int) Math.ceil(gap / maximum);
            for (int p = 1; p < pieces; p++) points.add(round(start + gap * p / pieces));
        }
        return new ArrayList<>(points);
    }

    /**
     * 设置并校验布局值值。
     *
     * @param cell 当前操作的 {@code cell} 输入
     * @param source 来源定义或资源
     */
    private static void setLayoutValue(Cell cell, RenderPlan.Cell source) {
        String text = String.join("\n", source.lines());
        var value = source.value();
        /** 固定布局保留已确定的舍入与换行，明细表格导出保留未格式化原始值。 */
        if (value != null
                && source.lines().size() == 1
                && !text.isEmpty()
                && (value.type() == ValueType.DECIMAL || value.type() == ValueType.INTEGER)) {
            setValue(cell, new TypedValue(value.type(), text));
        } else cell.setCellValue(text);
    }

    /**
     * 按声明精度执行十进制舍入。
     *
     * @param v 当前操作的 {@code v} 输入
     * @return 当前处理返回的固定结果
     */
    private static double round(double v) {
        return Math.rint(v * 1000) / 1000;
    }

    /**
     * 完成 {@code numberFormat} 对应的报表电子表格Exporter处理。
     *
     * @param scale 当前操作的 {@code scale} 输入
     * @return 当前处理返回的固定结果
     */
    private static String numberFormat(int scale) {
        return scale == 0 ? "0" : "0." + "0".repeat(scale);
    }

    /**
     * 完成 {@code style} 对应的报表电子表格Exporter处理。
     *
     * @param workbook 当前操作的 {@code workbook} 输入
     * @param source 来源定义或资源
     * @param scale 当前操作的 {@code scale} 输入
     * @param fontName 当前操作的 {@code fontName} 输入
     * @return 当前处理返回的固定结果
     */
    private static XSSFCellStyle style(
            XSSFWorkbook workbook, ReportDsl.Style source, int scale, String fontName) {
        var style = workbook.createCellStyle();
        var font = workbook.createFont();
        font.setFontName(fontName);
        font.setFontHeight((short) Math.round(source.fontSize() * 20));
        font.setBold(source.bold());
        font.setColor(color(source.color()));
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.valueOf(source.align()));
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        style.setFillForegroundColor(color(source.background()));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setDataFormat(workbook.createDataFormat().getFormat(numberFormat(scale)));
        return style;
    }

    /**
     * 完成 {@code color} 对应的报表电子表格Exporter处理。
     *
     * @param hex 当前操作的 {@code hex} 输入
     * @return 当前处理返回的固定结果
     */
    private static XSSFColor color(String hex) {
        return new XSSFColor(java.awt.Color.decode(hex), new DefaultIndexedColorMap());
    }

    /**
     * 设置并校验值值。
     *
     * @param cell 当前操作的 {@code cell} 输入
     * @param value 当前操作的 {@code value} 输入
     */
    private static void setValue(Cell cell, TypedValue value) {
        if (value == null || value.value() == null) {
            cell.setBlank();
            return;
        }
        if (value.type() == ValueType.BOOLEAN) {
            cell.setCellValue(Boolean.parseBoolean(value.value()));
            return;
        }
        if (value.type() == ValueType.DECIMAL || value.type() == ValueType.INTEGER) {
            var number = new BigDecimal(value.value());
            double converted = number.doubleValue();
            if (number.precision() <= 15
                    && Double.isFinite(converted)
                    && BigDecimal.valueOf(converted).compareTo(number) == 0) {
                cell.setCellValue(converted);
                return;
            }
        }
        /** 字符串写入不生成公式，即使内容以等号、加号、减号或邮箱符号开头。 */
        cell.setCellValue(value.value());
    }
}
