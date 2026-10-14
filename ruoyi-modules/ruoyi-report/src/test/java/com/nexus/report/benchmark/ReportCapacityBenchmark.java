package com.nexus.report.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.domain.ReportDsl;
import com.nexus.report.engine.*;
import com.nexus.report.export.*;
import com.nexus.report.runtime.*;
import com.nexus.report.storage.*;
import com.nexus.report.version.*;

import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipFile;

/**
 * Standalone, synthetic-only capacity probe; never opens application configuration or a business
 * database.
 */
public class ReportCapacityBenchmark {
    public static void main(String[] args) throws Exception {
        int rows = Integer.parseInt(args[0]);
        boolean document = args[1].equals("DOCUMENT");
        Path root = Path.of(args[2]);
        var actor = new ReportActor("BENCH", 1, 1, true, List.of());
        var json = new ObjectMapper();
        var fields = new ArrayList<DatasetField>();
        var columns = new ArrayList<ReportDsl.Column>();
        for (int i = 0; i < 10; i++) {
            fields.add(
                    new DatasetField(
                            "value" + i, "字段" + i, i % 2 == 0 ? ValueType.ID : ValueType.DECIMAL));
            columns.add(
                    new ReportDsl.Column(
                            "value" + i,
                            "字段" + i,
                            80,
                            4,
                            null,
                            new ReportDsl.Style(6, false, "LEFT", null, null)));
        }
        var descriptor =
                new DatasetDescriptor(
                        "BENCH",
                        "合成基准",
                        1,
                        "report:test",
                        document,
                        fields,
                        List.of(),
                        fields.stream().map(DatasetField::name).toList());
        var provider =
                new ReportDatasetProvider() {
                    public DatasetDescriptor descriptor() {
                        return descriptor;
                    }

                    public DatasetResult query(ReportExecutionContext c, DatasetQuery q) {
                        return new DatasetResult(List.of(row(0)), rows, Instant.now());
                    }

                    public void stream(
                            ReportExecutionContext c,
                            DatasetQuery q,
                            java.util.function.Consumer<Map<String, Object>> consume) {
                        for (int i = 0; i < rows; i++) {
                            c.cancellation().check();
                            consume.accept(row(i));
                        }
                    }

                    private Map<String, Object> row(int number) {
                        var value = new LinkedHashMap<String, Object>();
                        for (int i = 0; i < 10; i++)
                            value.put(
                                    "value" + i,
                                    i % 2 == 0
                                            ? Long.toString(Long.MAX_VALUE - number)
                                            : new java.math.BigDecimal("9007199254740993.005"));
                        return value;
                    }
                };
        var dsl =
                new ReportDsl(
                        1,
                        "BENCH",
                        "合成容量样本",
                        document ? ReportDsl.Kind.DOCUMENT : ReportDsl.Kind.TABLE,
                        "BENCH",
                        "1",
                        columns,
                        List.of(),
                        new ReportDsl.Page(841.89, 1190.55, 20, 20, 20, 20, 20),
                        List.of(),
                        List.of(),
                        List.of(new ReportDsl.Summary(null, "COUNT_ROWS", List.of(), "行数", 0)),
                        true);
        var compiler = new ReportCompiler(json);
        var executor = new V1ReportEngineExecutor(compiler);
        var compiled = executor.compile(dsl, descriptor, actor);
        var cancellation = new ReportCancellation();
        long started = System.nanoTime();
        try (var artifacts = new LocalReportArtifactStore(root, 200L * 1024 * 1024)) {
            var snapshots = new ReportSnapshotStore(artifacts, json);
            var snapshot =
                    snapshots.create(
                            compiled, provider, actor, Map.of(), cancellation, 100000, executor);
            if (snapshot.rows() != rows) throw new IllegalStateException("snapshot_count");
            var exporter = new ReportSpreadsheetExporter(artifacts, "BENCH", 200L * 1024 * 1024);
            Path data = root.resolve("data.xlsx");
            try (var output = Files.newOutputStream(data)) {
                exporter.data(
                        dsl,
                        consumer ->
                                snapshots.each(
                                        "BENCH", snapshot.reference(), consumer, cancellation),
                        output,
                        cancellation);
            }
            long spreadsheetRows = 0;
            try (var archive = new ZipFile(data.toFile());
                    var input =
                            archive.getInputStream(archive.getEntry("xl/worksheets/sheet1.xml"))) {
                var xml = javax.xml.stream.XMLInputFactory.newFactory();
                xml.setProperty(javax.xml.stream.XMLInputFactory.SUPPORT_DTD, false);
                xml.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
                var reader = xml.createXMLStreamReader(input);
                while (reader.hasNext())
                    if (reader.next() == javax.xml.stream.XMLStreamConstants.START_ELEMENT
                            && reader.getLocalName().equals("row")) spreadsheetRows++;
                reader.close();
            }
            if (spreadsheetRows != rows + 1) throw new IllegalStateException("xlsx_row_count");
            int pages = 0;
            if (document) {
                var plans = new ReportRenderPlanStore(artifacts, json);
                var typed = snapshots.page("BENCH", snapshot.reference(), 0, 5000);
                var stored =
                        plans.create(
                                "BENCH",
                                "benchmark",
                                snapshot.checksum(),
                                executor.engineVersion(),
                                FontSupport.find(null),
                                cancellation,
                                font ->
                                        executor.layout(
                                                compiled,
                                                new ReportCalculator.CalculatedData(
                                                        typed, snapshot.summaries()),
                                                Map.of(),
                                                font));
                pages = stored.plan().pages().size();
                Path pdf = root.resolve("document.pdf");
                plans.withFont(
                        "BENCH",
                        stored.reference(),
                        stored.checksum(),
                        "benchmark",
                        snapshot.checksum(),
                        executor.engineVersion(),
                        rows,
                        cancellation,
                        font -> {
                            try (var output = Files.newOutputStream(pdf)) {
                                new ReportPdfExporter()
                                        .write(stored.plan(), output, font, cancellation);
                            } catch (java.io.IOException e) {
                                throw new IllegalStateException(e);
                            }
                        });
                try (var output = Files.newOutputStream(root.resolve("layout.xlsx"))) {
                    exporter.layout(stored.plan(), output, cancellation);
                }
            }
            long temporary;
            try (var paths = Files.walk(root)) {
                temporary =
                        paths.filter(p -> p.getFileName().toString().startsWith("pending-"))
                                .count();
            }
            if (temporary != 0) throw new IllegalStateException("temporary_leak");
            long peakHeap =
                    ManagementFactory.getMemoryPoolMXBeans().stream()
                            .filter(p -> p.getType() == java.lang.management.MemoryType.HEAP)
                            .mapToLong(p -> p.getPeakUsage().getUsed())
                            .sum();
            System.out.println(
                    json.writeValueAsString(
                            Map.of(
                                    "mode",
                                    args[1],
                                    "rows",
                                    rows,
                                    "columns",
                                    10,
                                    "pages",
                                    pages,
                                    "durationMs",
                                    (System.nanoTime() - started) / 1000000,
                                    "dataBytes",
                                    Files.size(data),
                                    "peakHeapBytes",
                                    peakHeap,
                                    "pendingFiles",
                                    temporary,
                                    "java",
                                    System.getProperty("java.version"),
                                    "maxHeapBytes",
                                    Runtime.getRuntime().maxMemory())));
        }
    }
}
