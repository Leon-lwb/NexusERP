package com.nexus.report.export;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.domain.ReportDsl;
import com.nexus.report.engine.*;
import com.nexus.report.extension.ReportExtensionSchema;
import com.nexus.report.runtime.ReportSnapshotIndexService;
import com.nexus.report.runtime.ReportSnapshotStore;
import com.nexus.report.storage.LocalReportArtifactStore;
import com.nexus.report.version.*;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** 两代引擎及两种快照读取路径统一时间展示，PDF和Excel不暴露ISO时间原文。 */
class ReportTimeExportTest {
    @TempDir Path directory;

    @ParameterizedTest
    @CsvSource({"1,false", "1,true", "2,false", "2,true"})
    void snapshotTimesReachPreviewPdfAndExcelWithoutChangingRawValues(int schema, boolean indexed) throws Exception {
        String rawTime = "2026-10-17T16:30:45.123456789Z";
        String displayTime = "2026-10-18 00:30:45";
        var json = new ObjectMapper();
        var descriptor = new DatasetDescriptor("TIME_TEST", "时间回归", 1, "biz:list", true,
                List.of(new DatasetField("processedAt", "处理时间", ValueType.DATETIME),
                        new DatasetField("orderDate", "单据日期", ValueType.DATE),
                        new DatasetField("id", "标识", ValueType.ID)), List.of(), List.of("processedAt", "orderDate", "id"));
        var provider = new ReportDatasetProvider() {
            public DatasetDescriptor descriptor() { return descriptor; }
            public DatasetResult query(ReportExecutionContext context, DatasetQuery query) { throw new UnsupportedOperationException(); }
            public void stream(ReportExecutionContext context, DatasetQuery query, Consumer<Map<String, Object>> consumer) {
                consumer.accept(Map.of("processedAt", rawTime, "orderDate", "2026-10-17", "id", "9223372036854775807"));
            }
        };
        var actor = new ReportActor("TENANT_A", 1, 10, true, List.of());
        var dsl = new ReportDsl(schema, "TIME_REPORT", "时间打印回归", ReportDsl.Kind.DOCUMENT,
                descriptor.code(), "1", List.of(new ReportDsl.Column("processedAt", "处理时间", 210, 0, null, null),
                        new ReportDsl.Column("orderDate", "单据日期", 100, 0, null, null),
                        new ReportDsl.Column("id", "标识", 180, 0, null, null)), List.of(), null,
                List.of(new ReportDsl.BandRow(32, List.of(new ReportDsl.Cell(0, 3, 1, "时间：{{processedAt}}", null, null)))),
                List.of(new ReportDsl.BandRow(32, List.of(new ReportDsl.Cell(0, 3, 1, null, "processedAt", null)))),
                List.of(new ReportDsl.Summary(null, "COUNT_ROWS", List.of("processedAt"), "分组条数", 0)), true);
        var compiler = new ReportCompiler(json);
        ReportEngineExecutor executor = schema == 1 ? new V1ReportEngineExecutor(compiler) : new V2ReportEngineExecutor(compiler);
        var compiled = executor.compile(dsl, descriptor, actor);
        var artifacts = new LocalReportArtifactStore(directory, 20 * 1024 * 1024);
        var snapshots = new ReportSnapshotStore(artifacts, json);
        if (indexed) {
            var source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:time_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
            var jdbc = new JdbcTemplate(source);
            ReportExtensionSchema.apply(jdbc);
            snapshots.indexes(new ReportSnapshotIndexService(jdbc, artifacts, json));
        }
        var snapshot = snapshots.create(compiled, provider, actor, Map.of(), new ReportCancellation(), 100000, executor);
        var rows = snapshots.page(actor.tenantId(), snapshot.reference(), 0, 20);
        assertEquals(rawTime, rows.get(0).get("processedAt").value());
        assertEquals("2026-10-17", rows.get(0).get("orderDate").value());
        assertEquals(rawTime, snapshot.summaries().get(0).group().get("processedAt").value());
        var font = FontSupport.find(null);
        var plan = executor.layout(compiled, new ReportCalculator.CalculatedData(rows, snapshot.summaries()), Map.of(), font);
        assertTrue(plan.pages().stream().flatMap(page -> page.cells().stream())
                .filter(cell -> cell.band().equals("DETAIL") && cell.column() == 0)
                .allMatch(cell -> String.join("", cell.lines()).equals(displayTime)));
        var pdfBytes = new ByteArrayOutputStream();
        new ReportPdfExporter().write(plan, pdfBytes, font, new ReportCancellation());
        try (var pdf = Loader.loadPDF(pdfBytes.toByteArray())) {
            var text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("时间：" + displayTime));
            assertTrue(text.contains("2026-10-17"));
            assertTrue(text.contains("9223372036854775807"));
            assertFalse(text.contains(rawTime));
        }
        var dataBytes = new ByteArrayOutputStream();
        new ReportSpreadsheetExporter().data(dsl, consumer -> rows.forEach(consumer), dataBytes, new ReportCancellation());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(dataBytes.toByteArray()))) {
            assertEquals(displayTime, workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("2026-10-17", workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
            assertEquals("9223372036854775807", workbook.getSheetAt(0).getRow(1).getCell(2).getStringCellValue());
        }
        var layoutBytes = new ByteArrayOutputStream();
        new ReportSpreadsheetExporter().layout(plan, layoutBytes, new ReportCancellation());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(layoutBytes.toByteArray()))) {
            var cells = new ArrayList<String>();
            for (var sheet : workbook) for (var row : sheet) for (var cell : row)
                if (cell.getCellType() == CellType.STRING) cells.add(cell.getStringCellValue());
            assertTrue(cells.contains(displayTime));
            assertTrue(cells.contains("时间：" + displayTime));
            assertTrue(cells.stream().noneMatch(value -> value.contains(rawTime)));
        }
    }
}
