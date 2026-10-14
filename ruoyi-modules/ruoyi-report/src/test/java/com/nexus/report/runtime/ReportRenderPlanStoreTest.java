package com.nexus.report.runtime;

import static org.junit.jupiter.api.Assertions.*;

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

/** 报表渲染计划存储测试，负责当前模块对应的契约或处理职责。 */
class ReportRenderPlanStoreTest {
    /** 测试私有目录。 */
    @TempDir Path directory;

    /**
     * 验证 {@code artifacts} 场景下的结果与边界条件。
     *
     * @return 当前处理返回的固定结果
     */
    private LocalReportArtifactStore artifacts() {
        return new LocalReportArtifactStore(directory, 100L * 1024 * 1024);
    }

    /**
     * 验证 {@code plan} 场景下的结果与边界条件。
     *
     * @return 当前处理返回的固定结果
     */
    private RenderPlan plan() {
        var cell =
                new RenderPlan.Cell(
                        "DETAIL",
                        0,
                        0,
                        1,
                        1,
                        10,
                        20,
                        100,
                        30,
                        List.of("900719925474099312345"),
                        ReportDsl.Style.normal(),
                        TypedValue.of(ValueType.ID, "900719925474099312345"),
                        0);
        return new RenderPlan(
                1,
                1,
                "Font",
                List.of(new RenderPlan.Page(1, 595.28, 841.89, List.of(cell))),
                List.of(),
                List.of());
    }

    /**
     * 创建当前租户有权限的资源。
     *
     * @param store 租户化元数据或私有存储
     * @return 当前处理返回的固定结果
     */
    private ReportRenderPlanStore.Artifact create(ReportRenderPlanStore store) {
        return store.create(
                "A",
                "run1",
                "snapshot",
                "1.0.0",
                FontSupport.find(null),
                new ReportCancellation(),
                path -> plan());
    }

    /**
     * 验证 {@code pending} 场景下的结果与边界条件。
     *
     * @return 当前处理返回的固定结果
     */
    private long pending() throws Exception {
        try (var files = Files.walk(directory)) {
            return files.filter(p -> p.getFileName().toString().startsWith("pending-")).count();
        }
    }

    /** 验证 {@code storedCoordinatesAndExactTypedValuesRoundTrip} 场景下的结果与边界条件。 */
    @Test
    void storedCoordinatesAndExactTypedValuesRoundTrip() throws Exception {
        try (var artifacts = artifacts()) {
            var store = new ReportRenderPlanStore(artifacts, new ObjectMapper());
            var created = create(store);
            assertEquals(
                    plan(),
                    store.read(
                            "A",
                            created.reference(),
                            created.checksum(),
                            "run1",
                            "snapshot",
                            "1.0.0",
                            1,
                            new ReportCancellation()));
            assertEquals(0, pending());
        }
    }

    /** 验证 {@code pdfFontBytesAreCapturedAndTemporaryFilesDisappearAfterDelivery} 场景下的结果与边界条件。 */
    @Test
    void pdfFontBytesAreCapturedAndTemporaryFilesDisappearAfterDelivery() throws Exception {
        try (var artifacts = artifacts()) {
            var store = new ReportRenderPlanStore(artifacts, new ObjectMapper());
            var created = create(store);
            byte[] expected = Files.readAllBytes(FontSupport.find(null));
            store.withFont(
                    "A",
                    created.reference(),
                    created.checksum(),
                    "run1",
                    "snapshot",
                    "1.0.0",
                    1,
                    new ReportCancellation(),
                    font -> {
                        try {
                            assertArrayEquals(expected, Files.readAllBytes(font));
                        } catch (Exception error) {
                            throw new IllegalStateException(error);
                        }
                    });
            assertEquals(0, pending());
        }
    }

    /** 验证 {@code artifactIdentityBindsRunSnapshotEngineAndRowCount} 场景下的结果与边界条件。 */
    @Test
    void artifactIdentityBindsRunSnapshotEngineAndRowCount() {
        try (var artifacts = artifacts()) {
            var store = new ReportRenderPlanStore(artifacts, new ObjectMapper());
            var created = create(store);
            for (String[] identity :
                    List.of(
                            new String[] {"other", "snapshot", "1.0.0"},
                            new String[] {"run1", "changed", "1.0.0"},
                            new String[] {"run1", "snapshot", "2.0.0"})) {
                assertEquals(
                        "PLAN_INVALID",
                        assertThrows(
                                        ReportFault.class,
                                        () ->
                                                store.read(
                                                        "A",
                                                        created.reference(),
                                                        created.checksum(),
                                                        identity[0],
                                                        identity[1],
                                                        identity[2],
                                                        1,
                                                        new ReportCancellation()))
                                .code());
            }
            assertThrows(
                    ReportFault.class,
                    () ->
                            store.read(
                                    "A",
                                    created.reference(),
                                    created.checksum(),
                                    "run1",
                                    "snapshot",
                                    "1.0.0",
                                    2,
                                    new ReportCancellation()));
            assertThrows(
                    ReportFault.class,
                    () ->
                            store.read(
                                    "A",
                                    created.reference(),
                                    "altered",
                                    "run1",
                                    "snapshot",
                                    "1.0.0",
                                    1,
                                    new ReportCancellation()));
        }
    }

    /** 验证 {@code measuredFontHasNoDependenceOnAFileNameExtension} 场景下的结果与边界条件。 */
    @Test
    void measuredFontHasNoDependenceOnAFileNameExtension() throws Exception {
        byte[] source = Files.readAllBytes(FontSupport.find(null));
        if (!Arrays.equals(Arrays.copyOf(source, 4), new byte[] {'t', 't', 'c', 'f'})) {
            var ttf = ByteBuffer.wrap(source);
            int tables = Short.toUnsignedInt(ttf.getShort(4));
            for (int table = 0; table < tables; table++) {
                int offset = 12 + table * 16 + 8;
                ttf.putInt(offset, ttf.getInt(offset) + 16);
            }
            var collection = ByteBuffer.allocate(source.length + 16);
            collection.putInt(0x74746366).putInt(0x00010000).putInt(1).putInt(16).put(source);
            source = collection.array();
        }
        Path opaque = directory.resolve("captured-font.plan.tmp");
        Files.write(opaque, source);
        try (var metrics = new TextMetrics(opaque)) {
            assertTrue(metrics.width("中文123", 12) > 0);
        }
    }

    /** 验证 {@code exporterFailureStillDiscardsItsExtractedPrivateFont} 场景下的结果与边界条件。 */
    @Test
    void exporterFailureStillDiscardsItsExtractedPrivateFont() throws Exception {
        try (var artifacts = artifacts()) {
            var store = new ReportRenderPlanStore(artifacts, new ObjectMapper());
            var created = create(store);
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            store.withFont(
                                    "A",
                                    created.reference(),
                                    created.checksum(),
                                    "run1",
                                    "snapshot",
                                    "1.0.0",
                                    1,
                                    new ReportCancellation(),
                                    font -> {
                                        throw new IllegalStateException("export failed");
                                    }));
            assertEquals(0, pending());
            assertTrue(artifacts.exists("A", created.reference()));
        }
    }

    /** 验证 {@code cancelledMeasurementPublishesNoObjectOrTemporaryFont} 场景下的结果与边界条件。 */
    @Test
    void cancelledMeasurementPublishesNoObjectOrTemporaryFont() throws Exception {
        try (var artifacts = artifacts()) {
            var store = new ReportRenderPlanStore(artifacts, new ObjectMapper());
            var cancellation = new ReportCancellation();
            assertThrows(
                    DatasetAccessException.class,
                    () ->
                            store.create(
                                    "A",
                                    "run1",
                                    "snapshot",
                                    "1.0.0",
                                    FontSupport.find(null),
                                    cancellation,
                                    font -> {
                                        cancellation.cancel("CANCELLED");
                                        return plan();
                                    }));
            assertEquals(0, pending());
            try (var files = Files.list(directory.resolve("A"))) {
                /** 围栏目录保留；可读业务文件及暂存必须全部释放。 */
                assertEquals(0, files.filter(Files::isRegularFile).count());
            }
        }
    }

    /** 验证 {@code archivesRejectUnexpectedEntriesEvenWithAValidObjectChecksum} 场景下的结果与边界条件。 */
    @Test
    void archivesRejectUnexpectedEntriesEvenWithAValidObjectChecksum() throws Exception {
        try (var artifacts = artifacts()) {
            var store = new ReportRenderPlanStore(artifacts, new ObjectMapper());
            var created = create(store);
            Path temp = artifacts.temporary("A", "plan");
            try (var existing = new ZipFile(artifacts.read("A", created.reference()).toFile());
                    var zip = new ZipOutputStream(artifacts.output("A", temp))) {
                for (String name : List.of("plan.json", "font.bin")) {
                    zip.putNextEntry(new ZipEntry(name));
                    try (var input = existing.getInputStream(existing.getEntry(name))) {
                        input.transferTo(zip);
                    }
                    zip.closeEntry();
                }
                zip.putNextEntry(new ZipEntry("../outside"));
                zip.write(1);
                zip.closeEntry();
            }
            var invalid = artifacts.commit("A", temp, "plan");
            assertEquals(
                    "PLAN_INVALID",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            store.read(
                                                    "A",
                                                    invalid,
                                                    artifacts.checksum("A", invalid),
                                                    "run1",
                                                    "snapshot",
                                                    "1.0.0",
                                                    1,
                                                    new ReportCancellation()))
                            .code());
        }
    }

    /** 验证 {@code oversizedSerializedPlansKeepTheCapacityErrorAndLeaveNoArtifacts} 场景下的结果与边界条件。 */
    @Test
    void oversizedSerializedPlansKeepTheCapacityErrorAndLeaveNoArtifacts() throws Exception {
        try (var artifacts = artifacts()) {
            var store = new ReportRenderPlanStore(artifacts, new ObjectMapper());
            var cell = plan().pages().get(0).cells().get(0);
            var oversized =
                    new RenderPlan(
                            1,
                            1,
                            "Font",
                            List.of(
                                    new RenderPlan.Page(
                                            1, 595, 842, Collections.nCopies(150000, cell))),
                            List.of(),
                            List.of());
            assertEquals(
                    "CAPACITY_EXCEEDED",
                    assertThrows(
                                    ReportFault.class,
                                    () ->
                                            store.create(
                                                    "A",
                                                    "run1",
                                                    "snapshot",
                                                    "1.0.0",
                                                    FontSupport.find(null),
                                                    new ReportCancellation(),
                                                    font -> oversized))
                            .code());
            assertEquals(0, pending());
            try (var files = Files.list(directory.resolve("A"))) {
                /** 围栏目录保留；可读业务文件及暂存必须全部释放。 */
                assertEquals(0, files.filter(Files::isRegularFile).count());
            }
        }
    }

    /** 验证 {@code cancellationDuringSerializationKeepsItsOriginalError} 场景下的结果与边界条件。 */
    @Test
    void cancellationDuringSerializationKeepsItsOriginalError() throws Exception {
        try (var artifacts = artifacts()) {
            var cancellation = new ReportCancellation();
            var module = new com.fasterxml.jackson.databind.module.SimpleModule();
            module.addSerializer(
                    RenderPlan.class,
                    new com.fasterxml.jackson.databind.JsonSerializer<RenderPlan>() {
                        /**
                         * 完成 {@code serialize} 对应的报表处理。
                         *
                         * @param value 当前操作的 {@code value} 输入
                         * @param generator 当前操作的 {@code generator} 输入
                         * @param provider 当前操作的 {@code provider} 输入
                         */
                        @Override
                        public void serialize(
                                RenderPlan value,
                                com.fasterxml.jackson.core.JsonGenerator generator,
                                com.fasterxml.jackson.databind.SerializerProvider provider)
                                throws java.io.IOException {
                            cancellation.cancel("CANCELLED");
                            generator.writeString("data".repeat(10000));
                        }
                    });
            var store =
                    new ReportRenderPlanStore(artifacts, new ObjectMapper().registerModule(module));
            assertEquals(
                    "CANCELLED",
                    assertThrows(
                                    DatasetAccessException.class,
                                    () ->
                                            store.create(
                                                    "A",
                                                    "run1",
                                                    "snapshot",
                                                    "1.0.0",
                                                    FontSupport.find(null),
                                                    cancellation,
                                                    font -> plan()))
                            .code());
            assertEquals(0, pending());
            try (var files = Files.list(directory.resolve("A"))) {
                /** 围栏目录保留；可读业务文件及暂存必须全部释放。 */
                assertEquals(0, files.filter(Files::isRegularFile).count());
            }
        }
    }
}
