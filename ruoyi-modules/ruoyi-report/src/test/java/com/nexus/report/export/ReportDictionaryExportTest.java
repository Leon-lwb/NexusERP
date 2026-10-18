package com.nexus.report.export;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.dataset.*;
import com.nexus.report.domain.ReportDsl;
import com.nexus.report.engine.*;
import com.nexus.report.runtime.ReportSnapshotStore;
import com.nexus.report.runtime.ReportSnapshotIndexService;
import com.nexus.report.extension.ReportExtensionSchema;
import com.nexus.report.storage.LocalReportArtifactStore;
import com.nexus.report.version.*;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

/** 验证两代引擎从固定名称快照生成同样的预览、PDF和两种Excel。 */
class ReportDictionaryExportTest {
    @TempDir Path directory;

    @ParameterizedTest
    @CsvSource({"1,false", "1,true", "2,false", "2,true"})
    void frozenNamesReachHeadersDetailsAndAllExportsWithoutChangingGrouping(int schema, boolean indexed) throws Exception {
        var json = new ObjectMapper();
        var descriptor = new DatasetDescriptor("DICTIONARY_TEST", "字典回归", 1, "biz:list", true,
                List.of(new DatasetField("code", "客户类型", ValueType.STRING),
                        new DatasetField("id", "标识", ValueType.ID),
                        new DatasetField("amount", "金额", ValueType.DECIMAL)), List.of(), List.of("code", "id", "amount"));
        var provider = new ReportDatasetProvider() {
            public DatasetDescriptor descriptor() { return descriptor; }
            public Map<String, String> displayDictionaries() { return Map.of("code", "bd_client_type"); }
            public DatasetResult query(ReportExecutionContext context, DatasetQuery query) { throw new UnsupportedOperationException(); }
            public void stream(ReportExecutionContext context, DatasetQuery query, Consumer<Map<String, Object>> consumer) {
                for (String code : List.of("ENTERPRISE", "PERSON")) consumer.accept(Map.of(
                        "code", code, "id", "9223372036854775807", "amount", "9007199254740993.005"));
            }
        };
        var mutable = new HashMap<>(Map.of("ENTERPRISE", "企业", "PERSON", "企业"));
        var labels = mock(ReportDictionaryLabels.class);
        var actor = new ReportActor("TENANT_A", 1, 10, true, List.of());
        when(labels.load(actor, Set.of("bd_client_type"))).thenReturn(Map.of("bd_client_type", mutable));
        var decorated = new ReportDictionaryDatasetProvider(provider, labels);
        var dsl = new ReportDsl(schema, "DICTIONARY_REPORT", "字典打印回归", ReportDsl.Kind.DOCUMENT,
                descriptor.code(), "1", List.of(new ReportDsl.Column("code", "客户类型", 90, 0, null, null),
                        new ReportDsl.Column("id", "标识", 200, 0, null, null),
                        new ReportDsl.Column("amount", "金额", 200, 2, null, null)), List.of(), null,
                List.of(new ReportDsl.BandRow(32, List.of(new ReportDsl.Cell(0, 3, 1, "客户类型：{{code}}", null, null)))),
                List.of(new ReportDsl.BandRow(32, List.of(new ReportDsl.Cell(0, 3, 1, null, "code", null)))),
                List.of(new ReportDsl.Summary(null, "COUNT_ROWS", List.of("code"), "分组条数", 0)), true);
        var compiler = new ReportCompiler(json);
        ReportEngineExecutor executor = schema == 1 ? new V1ReportEngineExecutor(compiler) : new V2ReportEngineExecutor(compiler);
        var compiled = executor.compile(dsl, descriptor, actor);
        var artifacts = new LocalReportArtifactStore(directory, 20 * 1024 * 1024);
        var snapshots = new ReportSnapshotStore(artifacts, json);
        if (indexed) {
            var source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:dictionary_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
            var jdbc = new JdbcTemplate(source);
            ReportExtensionSchema.apply(jdbc);
            snapshots.indexes(new ReportSnapshotIndexService(jdbc, artifacts, json));
        }
        var snapshot = snapshots.create(compiled, decorated, actor, Map.of(), new ReportCancellation(), 100000, executor);
        mutable.put("ENTERPRISE", "已修改的字典");
        var rows = snapshots.page(actor.tenantId(), snapshot.reference(), 0, 20);
        assertEquals("ENTERPRISE", rows.get(0).get("code").value());
        assertEquals("企业", rows.get(0).get("code").displayText());
        assertEquals(2, snapshot.summaries().size(), "相同名称的不同编码仍分别汇总");
        var data = new ReportCalculator.CalculatedData(rows, snapshot.summaries());
        var font = FontSupport.find(null);
        var plan = executor.layout(compiled, data, Map.of(), font);
        assertTrue(plan.pages().stream().flatMap(page -> page.cells().stream())
                .filter(cell -> cell.band().equals("DETAIL") && cell.column() == 0)
                .allMatch(cell -> String.join("", cell.lines()).equals("企业")));
        var pdfBytes = new ByteArrayOutputStream();
        new ReportPdfExporter().write(plan, pdfBytes, font, new ReportCancellation());
        try (var pdf = Loader.loadPDF(pdfBytes.toByteArray())) {
            var text = new PDFTextStripper().getText(pdf);
            assertTrue(text.contains("客户类型：企业"));
            assertTrue(text.contains("9223372036854775807"));
            assertTrue(text.contains("9007199254740993.01"));
            assertFalse(text.contains("ENTERPRISE"));
            assertFalse(text.contains("已修改的字典"));
        }
        var dataBytes = new ByteArrayOutputStream();
        new ReportSpreadsheetExporter().data(dsl, consumer -> rows.forEach(consumer), dataBytes, new ReportCancellation());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(dataBytes.toByteArray()))) {
            assertEquals("企业", workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue());
            assertEquals("9223372036854775807", workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
            assertEquals("9007199254740993.005", workbook.getSheetAt(0).getRow(1).getCell(2).getStringCellValue());
        }
        var layoutBytes = new ByteArrayOutputStream();
        new ReportSpreadsheetExporter().layout(plan, layoutBytes, new ReportCancellation());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(layoutBytes.toByteArray()))) {
            boolean labelled = false;
            for (var sheet : workbook) for (var row : sheet) for (var cell : row)
                if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().contains("客户类型：企业")) labelled = true;
            assertTrue(labelled);
        }
        verify(labels, times(1)).load(actor, Set.of("bd_client_type"));
    }
}
