package com.nexus.report.export;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.domain.ReportDsl;
import com.nexus.report.engine.*;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.util.*;

/** 验证窄列中文表头自动换行后，预览计划与实际PDF仍共享分页。 */
class ReportWrappedHeaderExportTest {
    @ParameterizedTest
    @EnumSource(value = ReportDsl.Kind.class, names = {"TABLE", "DOCUMENT"})
    void wrappedHeadersGrowBeforePaginationAndExport(ReportDsl.Kind kind) throws Exception {
        var descriptor =
                new DatasetDescriptor(
                        "WRAPPED", "换行测试", 1, "report:test", false,
                        List.of(
                                new DatasetField("code", "供应商编码及采购部门", ValueType.STRING),
                                new DatasetField("name", "供应商名称", ValueType.STRING)),
                        List.of(), List.of("code", "name"));
        var dsl =
                new ReportDsl(
                        1, "WRAPPED_HEADER", "中文表头回归", kind, "WRAPPED", "1",
                        List.of(
                                new ReportDsl.Column("code", "供应商编码及采购部门", 60, 0, null, null),
                                new ReportDsl.Column("name", "供应商名称", 200, 0, null, null)),
                        List.of(), new ReportDsl.Page(300, 200, 10, 10, 10, 10, 24),
                        List.of(), List.of(), List.of(), true);
        var compiled =
                new ReportCompiler(new ObjectMapper())
                        .compile(dsl, descriptor, new ReportActor("A", 1, 10, true, List.of()));
        var rows = new ArrayList<Map<String, TypedValue>>();
        for (int index = 0; index < 12; index++)
            rows.add(
                    Map.of(
                            "code", TypedValue.of(ValueType.STRING, "T" + index),
                            "name", TypedValue.of(ValueType.STRING, "合成供应商")));
        var plan =
                new DocumentLayoutEngine()
                        .layout(
                                compiled,
                                new ReportCalculator.CalculatedData(rows, List.of()),
                                Map.of(), FontSupport.find(null));
        assertTrue(plan.pages().size() > 1);
        int records = 0;
        for (var page : plan.pages()) {
            var headers =
                    page.cells().stream()
                            .filter(cell -> cell.band().equals("COLUMN_HEADER"))
                            .toList();
            assertEquals(2, headers.size());
            double headerBottom = 0;
            for (var header : headers) {
                assertTrue(header.height() >= header.lines().size() * 12 + 8);
                headerBottom = Math.max(headerBottom, header.y() + header.height());
            }
            for (var detail :
                    page.cells().stream().filter(cell -> cell.band().equals("DETAIL")).toList()) {
                assertTrue(detail.y() >= headerBottom);
                assertTrue(detail.y() + detail.height() <= page.height() - 30);
                records++;
            }
        }
        assertEquals(24, records);
        var output = new ByteArrayOutputStream();
        new ReportPdfExporter()
                .write(plan, output, FontSupport.find(null), new ReportCancellation());
        try (var document = Loader.loadPDF(output.toByteArray())) {
            assertEquals(plan.pages().size(), document.getNumberOfPages());
            assertTrue(new PDFTextStripper().getText(document).contains("合成供应商"));
        }
    }
}
