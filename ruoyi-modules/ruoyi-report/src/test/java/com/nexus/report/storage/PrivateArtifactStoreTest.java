package com.nexus.report.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.report.domain.ReportFault;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** 私有产物存储测试，负责当前模块对应的契约或处理职责。 */
class PrivateArtifactStoreTest {
    /** 测试私有目录。 */
    @TempDir Path directory;

    /** 验证 {@code eachObjectIsPrivateToItsTenantAndKeysCannotTraverse} 场景下的结果与边界条件。 */
    @Test
    void eachObjectIsPrivateToItsTenantAndKeysCannotTraverse() {
        var store = new LocalReportArtifactStore(directory, 1024);
        var file = store.temporary("A", "rows");
        try (var output = store.output("A", file)) {
            output.write("synthetic".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        String reference = store.commit("A", file, "rows");
        assertTrue(Files.isRegularFile(store.read("A", reference)));
        assertThrows(ReportFault.class, () -> store.read("B", reference));
        assertThrows(ReportFault.class, () -> store.read("A", "../../application-secret.yml"));
        assertThrows(ReportFault.class, () -> store.temporary("../B", "rows"));
        store.delete("A", reference);
        assertFalse(store.exists("A", reference));
    }

    /**
     * 验证 {@code quotaStopsWritingBeforeTheDiskCanFillAndForeignPathsAreNeverCommitted} 场景下的结果与边界条件。
     */
    @Test
    void quotaStopsWritingBeforeTheDiskCanFillAndForeignPathsAreNeverCommitted() {
        var store = new LocalReportArtifactStore(directory, 16);
        var file = store.temporary("A", "rows");
        try (var output = store.output("A", file)) {
            assertThrows(ReportFault.class, () -> output.write(new byte[17]));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        assertThrows(ReportFault.class, () -> store.commit("B", file, "rows"));
        assertThrows(ReportFault.class, () -> store.output("A", directory.resolve("outside.txt")));
    }

    /** 验证 {@code checksumChangesWhenContentChanges} 场景下的结果与边界条件。 */
    @Test
    void checksumChangesWhenContentChanges() {
        var store = new LocalReportArtifactStore(directory, 1024);
        var file = store.temporary("A", "rows");
        try {
            Files.writeString(file, "first");
            String key = store.commit("A", file, "rows");
            String checksum = store.checksum("A", key);
            Files.writeString(store.read("A", key), "second");
            assertNotEquals(checksum, store.checksum("A", key));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
