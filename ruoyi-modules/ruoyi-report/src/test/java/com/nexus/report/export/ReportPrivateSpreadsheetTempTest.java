package com.nexus.report.export;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.common.report.*;
import com.nexus.report.domain.ReportDsl;
import com.nexus.report.storage.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.*;

class ReportPrivateSpreadsheetTempTest {
    @TempDir Path directory;

    @Test
    void sxssfWritesOnlyToPrivateArtifactsAndCleansAfterSuccessOrCancellation() throws Exception {
        var artifacts = new LocalReportArtifactStore(directory, 10 * 1024 * 1024);
        var exporter = new ReportSpreadsheetExporter(artifacts, "A", 10 * 1024 * 1024);
        var dsl =
                new ReportDsl(
                        1,
                        "TEST",
                        "合成",
                        ReportDsl.Kind.TABLE,
                        "TEST",
                        "1",
                        List.of(new ReportDsl.Column("id", "ID", 100, 0, null, null)),
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        true);
        var output = new ByteArrayOutputStream();
        exporter.data(
                dsl,
                consume -> {
                    try (var paths = Files.list(directory.resolve("A"))) {
                        assertTrue(
                                paths.anyMatch(
                                        p -> p.getFileName().toString().endsWith(".xlsx.tmp")));
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    }
                    for (int i = 0; i < 1000; i++)
                        consume.accept(
                                Map.of("id", TypedValue.of(ValueType.ID, "9223372036854775807")));
                },
                output,
                new ReportCancellation());
        assertTrue(output.size() > 1000);
        try (var files = Files.list(directory.resolve("A"))) {
            assertEquals(0, files.count());
        }
        var cancellation = new ReportCancellation();
        assertThrows(
                DatasetAccessException.class,
                () ->
                        exporter.data(
                                dsl,
                                consume -> {
                                    cancellation.cancel("CANCELLED");
                                    consume.accept(Map.of("id", TypedValue.of(ValueType.ID, "1")));
                                },
                                new ByteArrayOutputStream(),
                                cancellation));
        try (var files = Files.list(directory.resolve("A"))) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void cancellationWhileCreatingGzipWriterClosesTheRawFileStream() throws Exception {
        var cancellation = new ReportCancellation();
        var artifacts =
                org.mockito.Mockito.spy(new LocalReportArtifactStore(directory, 10 * 1024 * 1024));
        org.mockito.Mockito.doAnswer(
                        call -> {
                            Path file = (Path) call.callRealMethod();
                            cancellation.cancel("CANCELLED");
                            return file;
                        })
                .when(artifacts)
                .temporary("A", "xlsx");
        var exporter = new ReportSpreadsheetExporter(artifacts, "A", 10 * 1024 * 1024);
        var dsl =
                new ReportDsl(
                        1,
                        "TEST",
                        "合成",
                        ReportDsl.Kind.TABLE,
                        "TEST",
                        "1",
                        List.of(new ReportDsl.Column("id", "ID", 100, 0, null, null)),
                        List.of(),
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        true);
        assertThrows(
                DatasetAccessException.class,
                () ->
                        exporter.data(
                                dsl, consumer -> {}, new ByteArrayOutputStream(), cancellation));
        try (var paths = Files.list(directory.resolve("A"))) {
            assertEquals(0, paths.count());
        }
    }
}
