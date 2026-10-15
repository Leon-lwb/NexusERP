package com.nexus.report.print;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.engine.*;
import com.nexus.report.export.*;
import com.nexus.report.version.*;

import org.apache.pdfbox.Loader;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;

/** 分组、页脚、纸张方向及三格式固定坐标回归。 */
class ReportGroupedPrintTest {
    /** 执行groupTitlesAndSubtotalsDoNotChangeLogicalCountAndMediaExportsMatch对应的受控处理。 */
    @Test
    void groupTitlesAndSubtotalsDoNotChangeLogicalCountAndMediaExportsMatch() throws Exception {
        var compiler = new ReportCompiler(new ObjectMapper());
        var engine = new V2ReportEngineExecutor(compiler);
        var dataset =
                new DatasetDescriptor(
                        "PRINT_TEST",
                        "打印",
                        1,
                        "print:list",
                        true,
                        List.of(
                                new DatasetField("code", "编码", ValueType.STRING),
                                new DatasetField(
                                        "amount", "金额", ValueType.DECIMAL, null, List.of("code"))),
                        List.of(),
                        List.of("code", "amount"));
        var page = new ReportDsl.Page(400, 300, 20, 20, 20, 20, 24);
        var footer =
                new ReportDsl.BandRow(
                        24,
                        List.of(
                                new ReportDsl.Cell(
                                        0,
                                        2,
                                        1,
                                        "第{{page_no}}/{{page_count}}页 共{{row_count}}条",
                                        null,
                                        null)));
        var print =
                new PrintDefinition(
                        "LANDSCAPE",
                        List.of("code"),
                        true,
                        List.of(
                                new PrintDefinition.Media(
                                        "QR", null, "code", 220, 50, 60, 60, true)));
        var dsl =
                new ReportDsl(
                        2,
                        "PRINT_TEST",
                        "分组打印",
                        ReportDsl.Kind.DOCUMENT,
                        "PRINT_TEST",
                        "1",
                        List.of(
                                new ReportDsl.Column("code", "编码", 110, 0, null, null),
                                new ReportDsl.Column("amount", "金额", 110, 3, null, null)),
                        List.of(),
                        page,
                        List.of(new ReportDsl.BandRow(80, List.of())),
                        List.of(footer),
                        List.of(new ReportDsl.Summary("amount", "SUM", List.of("code"), "金额", 3)),
                        true,
                        null,
                        print);
        var actor = new ReportActor("A", 1, 10, true, List.of());
        var compiled = engine.compile(dsl, dataset, actor);
        var source = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 20; i++)
            source.add(
                    Map.of("code", i < 10 ? "PO-001" : "PO-002", "amount", "9007199254740993.005"));
        var data = engine.calculate(compiled, source, Map.of());
        var plan = engine.layout(compiled, data, Map.of(), FontSupport.find(null));
        assertEquals(20, plan.rowCount());
        assertTrue(plan.pages().size() > 1);
        assertEquals(300, plan.pages().get(0).width());
        assertEquals(400, plan.pages().get(0).height());
        assertEquals(1, plan.rasters().size());
        assertEquals(
                2,
                plan.pages().stream()
                        .flatMap(p -> p.cells().stream())
                        .filter(c -> c.band().equals("GROUP_HEADER") && c.column() == 0)
                        .count());
        for (var p : plan.pages()) {
            assertFalse(p.media().isEmpty());
            assertTrue(
                    p.cells().stream()
                            .anyMatch(
                                    c ->
                                            c.band().equals("FOOTER")
                                                    && String.join("", c.lines())
                                                            .contains("共20条")));
        }
        var pdf = new ByteArrayOutputStream();
        new ReportPdfExporter().write(plan, pdf, FontSupport.find(null), new ReportCancellation());
        try (var document = Loader.loadPDF(pdf.toByteArray())) {
            assertEquals(plan.pages().size(), document.getNumberOfPages());
            assertTrue(document.getPage(0).getResources().getXObjectNames().iterator().hasNext());
        }
        var xlsx = new ByteArrayOutputStream();
        new ReportSpreadsheetExporter().layout(plan, xlsx, new ReportCancellation());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx.toByteArray()))) {
            assertEquals(plan.pages().size(), workbook.getNumberOfSheets());
            assertFalse(workbook.getAllPictures().isEmpty());
            assertArrayEquals(
                    Base64.getDecoder().decode(plan.rasters().values().iterator().next().png()),
                    workbook.getAllPictures().get(0).getData());
        }
    }

    /** 固定区绑定原始单据，小计按每条摘要的分区身份展示。 */
    @Test
    void groupPrintingKeepsOriginalBindingsAndDoesNotMisassignMultipleSummaries() {
        var engine = new V2ReportEngineExecutor(new ReportCompiler(new ObjectMapper()));
        var fields =
                List.of(
                        new DatasetField("code", "物料", ValueType.STRING),
                        new DatasetField("unit", "单位", ValueType.STRING),
                        new DatasetField("currency", "币种", ValueType.STRING),
                        new DatasetField("lineId", "行", ValueType.ID),
                        new DatasetField("orderNo", "单号", ValueType.STRING),
                        new DatasetField("headerTotal", "单据金额", ValueType.DECIMAL),
                        new DatasetField(
                                "amount", "金额", ValueType.DECIMAL, null, List.of("currency")),
                        new DatasetField(
                                "quantity",
                                "数量",
                                ValueType.DECIMAL,
                                null,
                                List.of("code", "unit")));
        var dataset =
                new DatasetDescriptor(
                        "PRINT_PARTS",
                        "分组",
                        1,
                        "print:list",
                        true,
                        fields,
                        List.of(),
                        List.of("code", "amount", "quantity"));
        var dsl =
                new ReportDsl(
                        2,
                        "PRINT_PARTS",
                        "分组",
                        ReportDsl.Kind.DOCUMENT,
                        "PRINT_PARTS",
                        "1",
                        List.of(
                                new ReportDsl.Column("code", "物料", 140, 0, null, null),
                                new ReportDsl.Column("amount", "金额", 140, 0, null, null),
                                new ReportDsl.Column("quantity", "数量", 140, 0, null, null)),
                        List.of(),
                        ReportDsl.Page.a4(),
                        List.of(
                                new ReportDsl.BandRow(
                                        24,
                                        List.of(
                                                new ReportDsl.Cell(
                                                        0, 3, 1, "单号 {{orderNo}}", null, null)))),
                        List.of(
                                new ReportDsl.BandRow(
                                        24,
                                        List.of(
                                                new ReportDsl.Cell(
                                                        0,
                                                        3,
                                                        1,
                                                        "单据金额 {{headerTotal}}",
                                                        null,
                                                        null)))),
                        List.of(
                                new ReportDsl.Summary(
                                        "amount", "SUM", List.of("currency", "lineId"), "金额分区", 0),
                                new ReportDsl.Summary(
                                        "quantity", "SUM", List.of("code", "unit"), "数量分区", 0)),
                        true,
                        null,
                        new PrintDefinition(
                                "PORTRAIT", List.of("code", "unit", "currency"), false, List.of()));
        var actor = new ReportActor("A", 1, 10, true, List.of());
        var compiled = engine.compile(dsl, dataset, actor);
        var source =
                List.<Map<String, Object>>of(
                        Map.of(
                                "code",
                                "P",
                                "unit",
                                "件",
                                "currency",
                                "CNY",
                                "lineId",
                                "1",
                                "orderNo",
                                "PO-HEADER",
                                "headerTotal",
                                "99.005",
                                "amount",
                                "10",
                                "quantity",
                                "1"),
                        Map.of(
                                "code",
                                "P",
                                "unit",
                                "件",
                                "currency",
                                "CNY",
                                "lineId",
                                "2",
                                "orderNo",
                                "PO-HEADER",
                                "headerTotal",
                                "99.005",
                                "amount",
                                "20",
                                "quantity",
                                "2"));
        var plan =
                engine.layout(
                        compiled,
                        engine.calculate(compiled, source, Map.of()),
                        Map.of(),
                        FontSupport.find(null));
        var cells = plan.pages().stream().flatMap(page -> page.cells().stream()).toList();
        assertTrue(
                cells.stream()
                        .filter(cell -> cell.band().equals("HEADER"))
                        .anyMatch(cell -> String.join("", cell.lines()).contains("PO-HEADER")));
        assertTrue(
                cells.stream()
                        .filter(cell -> cell.band().equals("FOOTER"))
                        .anyMatch(cell -> String.join("", cell.lines()).contains("99.005")));
        String subtotal =
                cells.stream()
                        .filter(cell -> cell.band().equals("GROUP_SUBTOTAL"))
                        .map(cell -> String.join("", cell.lines()))
                        .collect(java.util.stream.Collectors.joining(" "));
        assertTrue(subtotal.contains("数量分区"));
        assertTrue(subtotal.contains("：3"));
    }
}
