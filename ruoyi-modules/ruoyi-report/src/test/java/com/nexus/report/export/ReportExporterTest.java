package com.nexus.report.export;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.engine.*;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.ZipFile;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;

/** 报表Exporter测试，负责当前模块对应的契约或处理职责。 */
class ReportExporterTest {
    /**
     * 验证 {@code dsl} 场景下的结果与边界条件。
     *
     * @return 当前处理返回的固定结果
     */
    private ReportDsl dsl() {
        return new ReportDsl(
                1,
                "TEST_REPORT",
                "中文报表",
                ReportDsl.Kind.DOCUMENT,
                "TEST",
                "1",
                List.of(
                        new ReportDsl.Column("id", "ID", 100, 0, null, null),
                        new ReportDsl.Column("label", "编码", 200, 0, null, null),
                        new ReportDsl.Column("amount", "金额", 200, 2, null, null)),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                true);
    }

    /**
     * 验证 {@code row} 场景下的结果与边界条件。
     *
     * @return 当前处理返回的固定结果
     */
    private Map<String, TypedValue> row() {
        return Map.of(
                "id",
                TypedValue.of(ValueType.ID, Long.MAX_VALUE),
                "label",
                TypedValue.of(ValueType.STRING, "=SUM(1+1)"),
                "amount",
                TypedValue.of(ValueType.DECIMAL, new BigDecimal("0.1")));
    }

    /** 验证 {@code dataExcelPreservesLongIdsAndNeverTurnsStringsIntoFormulas} 场景下的结果与边界条件。 */
    @Test
    void dataExcelPreservesLongIdsAndNeverTurnsStringsIntoFormulas() throws Exception {
        var out = new ByteArrayOutputStream();
        new ReportSpreadsheetExporter()
                .data(dsl(), consumer -> consumer.accept(row()), out, new ReportCancellation());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals(CellType.STRING, sheet.getRow(1).getCell(0).getCellType());
            assertEquals(
                    Long.toString(Long.MAX_VALUE), sheet.getRow(1).getCell(0).getStringCellValue());
            assertEquals(CellType.STRING, sheet.getRow(1).getCell(1).getCellType());
            assertEquals("=SUM(1+1)", sheet.getRow(1).getCell(1).getStringCellValue());
            assertEquals(0.1, sheet.getRow(1).getCell(2).getNumericCellValue(), 0.0000001);
        }
    }

    /**
     * 验证 {@code plan} 场景下的结果与边界条件。
     *
     * @return 当前处理返回的固定结果
     */
    private RenderPlan plan() {
        var descriptor =
                new DatasetDescriptor(
                        "TEST",
                        "测试",
                        1,
                        "wm:stock:list",
                        false,
                        List.of(
                                new DatasetField("id", "ID", ValueType.ID),
                                new DatasetField("label", "编码", ValueType.STRING),
                                new DatasetField("amount", "金额", ValueType.DECIMAL)),
                        List.of(),
                        List.of("id", "label", "amount"));
        var compiled =
                new ReportCompiler(new ObjectMapper())
                        .compile(dsl(), descriptor, new ReportActor("A", 1, 10, true, List.of()));
        return new DocumentLayoutEngine()
                .layout(
                        compiled,
                        new ReportCalculator.CalculatedData(List.of(row()), List.of()),
                        Map.of(),
                        FontSupport.find(null));
    }

    /** 验证 {@code pdfUsesTheSamePagesAndEmbedsReadableChinese} 场景下的结果与边界条件。 */
    @Test
    void pdfUsesTheSamePagesAndEmbedsReadableChinese() throws Exception {
        var plan = plan();
        var out = new ByteArrayOutputStream();
        new ReportPdfExporter().write(plan, out, FontSupport.find(null), new ReportCancellation());
        try (var document = Loader.loadPDF(out.toByteArray())) {
            assertEquals(plan.pages().size(), document.getNumberOfPages());
            String text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("中文报表"));
            assertTrue(text.contains("0.10"));
            assertTrue(text.contains("=SUM(1+1)"));
        }
    }

    /** 验证 {@code styledExcelKeepsLayoutMergesAndTextSemantics} 场景下的结果与边界条件。 */
    @Test
    void styledExcelKeepsLayoutMergesAndTextSemantics() throws Exception {
        var out = new ByteArrayOutputStream();
        new ReportSpreadsheetExporter().layout(plan(), out, new ReportCancellation());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            assertTrue(workbook.getSheetAt(0).getNumMergedRegions() > 0);
            boolean found = false;
            for (var row : workbook.getSheetAt(0))
                for (var cell : row)
                    if (cell.getCellType() == CellType.STRING
                            && cell.getStringCellValue().equals("=SUM(1+1)")) found = true;
            assertTrue(found);
        }
    }

    /**
     * 验证 {@code displayedPlan} 场景下的结果与边界条件。
     *
     * @return 当前处理返回的固定结果
     */
    private RenderPlan displayedPlan() {
        var style = new ReportDsl.Style(10.5, false, "LEFT", null, null);
        var cells =
                List.of(
                        new RenderPlan.Cell(
                                "DETAIL",
                                0,
                                0,
                                1,
                                1,
                                36,
                                36,
                                300,
                                30,
                                List.of("1234567890123456.13"),
                                style,
                                new TypedValue(ValueType.DECIMAL, "1234567890123456.125"),
                                2),
                        new RenderPlan.Cell(
                                "DETAIL",
                                1,
                                0,
                                1,
                                1,
                                36,
                                66,
                                300,
                                30,
                                List.of("2.68"),
                                style,
                                new TypedValue(ValueType.DECIMAL, "2.675"),
                                2),
                        new RenderPlan.Cell(
                                "DETAIL",
                                2,
                                0,
                                1,
                                1,
                                36,
                                96,
                                300,
                                48,
                                List.of("=SUM(1+1)", "第二行"),
                                style,
                                new TypedValue(ValueType.STRING, "=SUM(1+1)第二行"),
                                0),
                        new RenderPlan.Cell(
                                "DETAIL",
                                3,
                                0,
                                1,
                                1,
                                36,
                                144,
                                300,
                                30,
                                List.of("0.00"),
                                style,
                                new TypedValue(ValueType.DECIMAL, "0.00000000000000001"),
                                2));
        return new RenderPlan(
                1,
                4,
                "test",
                List.of(new RenderPlan.Page(1, 595.28, 841.89, cells)),
                List.of(),
                List.of());
    }

    /** 验证 {@code styledExcelUsesPlannedRoundingAndLineBreaksRatherThanRawValues} 场景下的结果与边界条件。 */
    @Test
    void styledExcelUsesPlannedRoundingAndLineBreaksRatherThanRawValues() throws Exception {
        var out = new ByteArrayOutputStream();
        new ReportSpreadsheetExporter().layout(displayedPlan(), out, new ReportCancellation());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            var values = new ArrayList<Cell>();
            for (var row : sheet) for (var cell : row) values.add(cell);
            assertTrue(
                    values.stream()
                            .anyMatch(
                                    c ->
                                            c.getCellType() == CellType.STRING
                                                    && c.getStringCellValue()
                                                            .equals("1234567890123456.13")));
            assertTrue(
                    values.stream()
                            .anyMatch(
                                    c ->
                                            c.getCellType() == CellType.NUMERIC
                                                    && c.getNumericCellValue() == 2.68));
            assertTrue(
                    values.stream()
                            .anyMatch(
                                    c ->
                                            c.getCellType() == CellType.STRING
                                                    && c.getStringCellValue()
                                                            .equals("=SUM(1+1)\n第二行")));
            assertTrue(
                    values.stream()
                            .filter(c -> c.getCellType() == CellType.NUMERIC)
                            .anyMatch(c -> c.getNumericCellValue() == 0.0));
            for (var cell : values) assertNotEquals(CellType.FORMULA, cell.getCellType());
            var font = workbook.getFontAt(values.get(0).getCellStyle().getFontIndex());
            assertEquals(10.5, font.getFontHeight() / 20.0, 0.01);
            assertEquals(displayedPlan().fontName(), font.getFontName());
        }
    }

    /** 验证 {@code styledExcelPreservesThePageOriginAndPaperExtent} 场景下的结果与边界条件。 */
    @Test
    void styledExcelPreservesThePageOriginAndPaperExtent() throws Exception {
        var out = new ByteArrayOutputStream();
        new ReportSpreadsheetExporter().layout(displayedPlan(), out, new ReportCancellation());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals(36, sheet.getRow(0).getHeightInPoints(), 0.01);
            assertNull(sheet.getRow(0).getCell(0));
            assertTrue(sheet.getColumnWidth(0) > 0);
            assertNotNull(workbook.getPrintArea(0));
            assertEquals(0, sheet.getMargin(Sheet.TopMargin), 0.0001);
            assertEquals(0, sheet.getMargin(Sheet.LeftMargin), 0.0001);
            double height = 0;
            for (var row : sheet) {
                assertTrue(row.getHeightInPoints() <= 409);
                height += row.getHeightInPoints();
            }
            assertEquals(841.89, height, 0.1);
        }
    }

    /** 验证 {@code pdfKeepsPlannedPrecisionAndExplicitLineBreaks} 场景下的结果与边界条件。 */
    @Test
    void pdfKeepsPlannedPrecisionAndExplicitLineBreaks() throws Exception {
        var out = new ByteArrayOutputStream();
        new ReportPdfExporter()
                .write(displayedPlan(), out, FontSupport.find(null), new ReportCancellation());
        try (var document = Loader.loadPDF(out.toByteArray())) {
            var text = new PDFTextStripper().getText(document);
            assertTrue(text.contains("1234567890123456.13"));
            assertFalse(text.contains("1234567890123456.125"));
            assertTrue(text.contains("2.68"));
            assertTrue(text.contains("第二行"));
        }
    }

    /** 验证 {@code dataExcelKeepsDecimalsAboveExcelPrecisionAsExactText} 场景下的结果与边界条件。 */
    @Test
    void dataExcelKeepsDecimalsAboveExcelPrecisionAsExactText() throws Exception {
        var values =
                Map.of(
                        "id",
                        TypedValue.of(ValueType.ID, Long.MAX_VALUE),
                        "label",
                        TypedValue.of(ValueType.STRING, "@SUM(1+1)"),
                        "amount",
                        TypedValue.of(ValueType.DECIMAL, new BigDecimal("1234567890123456.125")));
        var out = new ByteArrayOutputStream();
        new ReportSpreadsheetExporter()
                .data(dsl(), consumer -> consumer.accept(values), out, new ReportCancellation());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
            var cell = workbook.getSheetAt(0).getRow(1).getCell(2);
            assertEquals(CellType.STRING, cell.getCellType());
            assertEquals("1234567890123456.125", cell.getStringCellValue());
        }
    }

    /**
     * 验证 {@code streamsOneHundredThousandRowsIntoAValidXlsxWithoutMaterializingAWorkbook}
     * 场景下的结果与边界条件。
     */
    @Test
    void streamsOneHundredThousandRowsIntoAValidXlsxWithoutMaterializingAWorkbook()
            throws Exception {
        var file = Files.createTempFile("report-stream-", ".xlsx");
        try {
            try (var output = Files.newOutputStream(file)) {
                new ReportSpreadsheetExporter()
                        .data(
                                dsl(),
                                consumer -> {
                                    var sample = row();
                                    for (int i = 0; i < 100000; i++) consumer.accept(sample);
                                },
                                output,
                                new ReportCancellation());
            }
            int rows = 0;
            try (var zip = new ZipFile(file.toFile())) {
                var entry = zip.getEntry("xl/worksheets/sheet1.xml");
                assertNotNull(entry);
                try (var input = zip.getInputStream(entry)) {
                    var factory = XMLInputFactory.newFactory();
                    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
                    factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
                    var xml = factory.createXMLStreamReader(input);
                    while (xml.hasNext()) {
                        int event = xml.next();
                        if (event == XMLStreamConstants.START_ELEMENT
                                && xml.getLocalName().equals("row")) rows++;
                    }
                    xml.close();
                }
            }
            assertEquals(100001, rows);
            assertTrue(Files.size(file) > 1000);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** 验证 {@code cancellationDuringStreamingStopsBeforeTheNextRow} 场景下的结果与边界条件。 */
    @Test
    void cancellationDuringStreamingStopsBeforeTheNextRow() {
        var cancellation = new ReportCancellation();
        int[] consumed = {0};
        assertThrows(
                DatasetAccessException.class,
                () ->
                        new ReportSpreadsheetExporter()
                                .data(
                                        dsl(),
                                        consumer -> {
                                            for (int i = 0; i < 100; i++) {
                                                consumer.accept(row());
                                                if (++consumed[0] == 3)
                                                    cancellation.cancel("CANCELLED");
                                            }
                                        },
                                        new ByteArrayOutputStream(),
                                        cancellation));
        assertEquals(3, consumed[0]);
    }
}
