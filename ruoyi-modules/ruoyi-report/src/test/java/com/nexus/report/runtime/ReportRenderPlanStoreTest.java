package com.nexus.report.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.report.domain.*;
import com.nexus.report.engine.*;
import com.nexus.report.storage.LocalReportArtifactStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

class ReportRenderPlanStoreTest {
    @TempDir Path directory;
    private LocalReportArtifactStore artifacts() { return new LocalReportArtifactStore(directory, 100L * 1024 * 1024); }
    private RenderPlan plan() {
        var cell = new RenderPlan.Cell("DETAIL", 0, 0, 1, 1, 10, 20, 100, 30, List.of("900719925474099312345"),
            ReportDsl.Style.normal(), TypedValue.of(ValueType.ID, "900719925474099312345"), 0);
        return new RenderPlan(1, 1, "Font", List.of(new RenderPlan.Page(1, 595.28, 841.89, List.of(cell))), List.of(), List.of());
    }
    private ReportRenderPlanStore.Artifact create(ReportRenderPlanStore store) {
        return store.create("A", "run1", "snapshot", "1.0.0", FontSupport.find(null), new ReportCancellation(), path -> plan());
    }
    private long pending() throws Exception {
        try (var files = Files.walk(directory)) { return files.filter(p -> p.getFileName().toString().startsWith("pending-")).count(); }
    }
    @Test void storedCoordinatesAndExactTypedValuesRoundTrip() throws Exception {
        try (var artifacts = artifacts()) {
            var store = new ReportRenderPlanStore(artifacts, new ObjectMapper()); var created = create(store);
            assertEquals(plan(), store.read("A", created.reference(), created.checksum(), "run1", "snapshot", "1.0.0", 1, new ReportCancellation()));
            assertEquals(0, pending());
        }
    }
    @Test void pdfFontBytesAreCapturedAndTemporaryFilesDisappearAfterDelivery() throws Exception {
        try (var artifacts = artifacts()) {
            var store = new ReportRenderPlanStore(artifacts, new ObjectMapper()); var created = create(store);
            byte[] expected = Files.readAllBytes(FontSupport.find(null));
            store.withFont("A", created.reference(), created.checksum(), "run1", "snapshot", "1.0.0", 1, new ReportCancellation(), font -> {
                try { assertArrayEquals(expected, Files.readAllBytes(font)); } catch (Exception error) { throw new IllegalStateException(error); }
            });
            assertEquals(0, pending());
        }
    }
    @Test void artifactIdentityBindsRunSnapshotEngineAndRowCount() {
        try (var artifacts = artifacts()) {
            var store = new ReportRenderPlanStore(artifacts, new ObjectMapper()); var created = create(store);
            for (String[] identity : List.of(new String[]{"other", "snapshot", "1.0.0"}, new String[]{"run1", "changed", "1.0.0"}, new String[]{"run1", "snapshot", "2.0.0"})) {
                assertEquals("PLAN_INVALID", assertThrows(ReportFault.class, () -> store.read("A", created.reference(), created.checksum(), identity[0], identity[1], identity[2], 1, new ReportCancellation())).code());
            }
            assertThrows(ReportFault.class, () -> store.read("A", created.reference(), created.checksum(), "run1", "snapshot", "1.0.0", 2, new ReportCancellation()));
            assertThrows(ReportFault.class, () -> store.read("A", created.reference(), "altered", "run1", "snapshot", "1.0.0", 1, new ReportCancellation()));
        }
    }
    @Test void measuredFontHasNoDependenceOnAFileNameExtension() throws Exception {
        byte[] source = Files.readAllBytes(FontSupport.find(null));
        if (!Arrays.equals(Arrays.copyOf(source, 4), new byte[]{'t','t','c','f'})) {
            var ttf = ByteBuffer.wrap(source); int tables = Short.toUnsignedInt(ttf.getShort(4));
            for (int table = 0; table < tables; table++) { int offset = 12 + table * 16 + 8; ttf.putInt(offset, ttf.getInt(offset) + 16); }
            var collection = ByteBuffer.allocate(source.length + 16); collection.putInt(0x74746366).putInt(0x00010000).putInt(1).putInt(16).put(source); source = collection.array();
        }
        Path opaque = directory.resolve("captured-font.plan.tmp"); Files.write(opaque, source);
        try (var metrics = new TextMetrics(opaque)) { assertTrue(metrics.width("中文123", 12) > 0); }
    }
    @Test void exporterFailureStillDiscardsItsExtractedPrivateFont() throws Exception {
        try (var artifacts = artifacts()) {
            var store = new ReportRenderPlanStore(artifacts, new ObjectMapper()); var created = create(store);
            assertThrows(IllegalStateException.class, () -> store.withFont("A", created.reference(), created.checksum(), "run1", "snapshot", "1.0.0", 1, new ReportCancellation(), font -> { throw new IllegalStateException("export failed"); }));
            assertEquals(0, pending()); assertTrue(artifacts.exists("A", created.reference()));
        }
    }
    @Test void cancelledMeasurementPublishesNoObjectOrTemporaryFont() throws Exception {
        try (var artifacts = artifacts()) {
            var store = new ReportRenderPlanStore(artifacts, new ObjectMapper()); var cancellation = new ReportCancellation();
            assertThrows(DatasetAccessException.class, () -> store.create("A", "run1", "snapshot", "1.0.0", FontSupport.find(null), cancellation, font -> { cancellation.cancel("CANCELLED"); return plan(); }));
            assertEquals(0, pending());
            try (var files = Files.list(directory.resolve("A"))) { assertEquals(0, files.count()); }
        }
    }
    @Test void archivesRejectUnexpectedEntriesEvenWithAValidObjectChecksum() throws Exception {
        try (var artifacts = artifacts()) {
            var store = new ReportRenderPlanStore(artifacts, new ObjectMapper()); var created = create(store);
            Path temp = artifacts.temporary("A", "plan");
            try (var existing = new ZipFile(artifacts.read("A", created.reference()).toFile()); var zip = new ZipOutputStream(artifacts.output("A", temp))) {
                for (String name : List.of("plan.json", "font.bin")) { zip.putNextEntry(new ZipEntry(name)); try (var input = existing.getInputStream(existing.getEntry(name))) { input.transferTo(zip); } zip.closeEntry(); }
                zip.putNextEntry(new ZipEntry("../outside")); zip.write(1); zip.closeEntry();
            }
            var invalid = artifacts.commit("A", temp, "plan");
            assertEquals("PLAN_INVALID", assertThrows(ReportFault.class, () -> store.read("A", invalid, artifacts.checksum("A", invalid), "run1", "snapshot", "1.0.0", 1, new ReportCancellation())).code());
        }
    }

    @Test void oversizedSerializedPlansKeepTheCapacityErrorAndLeaveNoArtifacts() throws Exception {
        try (var artifacts = artifacts()) {
            var store = new ReportRenderPlanStore(artifacts, new ObjectMapper());
            var cell = plan().pages().get(0).cells().get(0);
            var oversized = new RenderPlan(1, 1, "Font", List.of(new RenderPlan.Page(1, 595, 842, Collections.nCopies(150000, cell))), List.of(), List.of());
            assertEquals("CAPACITY_EXCEEDED", assertThrows(ReportFault.class,
                () -> store.create("A", "run1", "snapshot", "1.0.0", FontSupport.find(null), new ReportCancellation(), font -> oversized)).code());
            assertEquals(0, pending());
            try (var files = Files.list(directory.resolve("A"))) { assertEquals(0, files.count()); }
        }
    }
    @Test void cancellationDuringSerializationKeepsItsOriginalError() throws Exception {
        try (var artifacts = artifacts()) {
            var cancellation = new ReportCancellation();
            var module = new com.fasterxml.jackson.databind.module.SimpleModule();
            module.addSerializer(RenderPlan.class, new com.fasterxml.jackson.databind.JsonSerializer<RenderPlan>() {
                @Override public void serialize(RenderPlan value, com.fasterxml.jackson.core.JsonGenerator generator,
                    com.fasterxml.jackson.databind.SerializerProvider provider) throws java.io.IOException {
                    cancellation.cancel("CANCELLED");
                    generator.writeString("data".repeat(10000));
                }
            });
            var store = new ReportRenderPlanStore(artifacts, new ObjectMapper().registerModule(module));
            assertEquals("CANCELLED", assertThrows(DatasetAccessException.class,
                () -> store.create("A", "run1", "snapshot", "1.0.0", FontSupport.find(null), cancellation, font -> plan())).code());
            assertEquals(0, pending());
            try (var files = Files.list(directory.resolve("A"))) { assertEquals(0, files.count()); }
        }
    }
}
