package com.nexus.report.storage;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;

/** 报表产物恢复测试，负责当前模块对应的契约或处理职责。 */
class ReportArtifactRecoveryTest {
    /** 测试私有目录。 */
    @TempDir Path directory;

    /**
     * 完成 {@code age} 对应的报表产物恢复测试处理。
     *
     * @param file 当前操作的 {@code file} 输入
     */
    private void age(Path file) throws Exception {
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(172800)));
    }

    /** 验证 {@code crashLeftoversAreReapedButRegisteredAndRecentArtifactsSurvive} 场景下的结果与边界条件。 */
    @Test
    void crashLeftoversAreReapedButRegisteredAndRecentArtifactsSurvive() throws Exception {
        var store = new LocalReportArtifactStore(directory, 1024 * 1024);
        var pending = store.temporary("A", "rows");
        age(pending);
        var temporary = store.temporary("A", "pdf");
        var orphan = store.commit("A", temporary, "pdf");
        age(store.read("A", orphan));
        var registered = store.commit("A", store.temporary("A", "xlsx"), "xlsx");
        age(store.read("A", registered));
        var recent = store.temporary("A", "rows");
        Set<String> checked = new HashSet<>();
        store.reap(
                Instant.now().minusSeconds(86400),
                (tenant, ref) -> {
                    assertEquals("A", tenant);
                    checked.add(ref);
                    return ref.equals(registered);
                });
        assertFalse(Files.exists(pending));
        assertFalse(store.exists("A", orphan));
        assertTrue(store.exists("A", registered));
        assertTrue(Files.exists(recent));
        assertEquals(Set.of(orphan, registered), checked);
    }

    /** 验证 {@code unrecognizedFilesAndTenantFoldersAreNeverReaped} 场景下的结果与边界条件。 */
    @Test
    void unrecognizedFilesAndTenantFoldersAreNeverReaped() throws Exception {
        var store = new LocalReportArtifactStore(directory, 1024 * 1024);
        store.temporary("A", "rows");
        var unrelated = directory.resolve("A/notes.txt");
        Files.writeString(unrelated, "unrelated");
        age(unrelated);
        var invalidTenant = directory.resolve("invalid.tenant");
        Files.createDirectories(invalidTenant);
        var outside = invalidTenant.resolve(UUID.randomUUID() + ".pdf");
        Files.writeString(outside, "unrelated");
        age(outside);
        store.reap(Instant.now().minusSeconds(86400), (tenant, ref) -> false);
        assertTrue(Files.exists(unrelated));
        assertTrue(Files.exists(outside));
    }

    /** 验证 {@code databaseFailureRetainsAgedUnregisteredCandidatesForTheNextAttempt} 场景下的结果与边界条件。 */
    @Test
    void databaseFailureRetainsAgedUnregisteredCandidatesForTheNextAttempt() throws Exception {
        var store = new LocalReportArtifactStore(directory, 1024 * 1024);
        var reference = store.commit("A", store.temporary("A", "pdf"), "pdf");
        age(store.read("A", reference));
        assertThrows(
                IllegalStateException.class,
                () ->
                        store.reap(
                                Instant.now().minusSeconds(86400),
                                (tenant, ref) -> {
                                    throw new IllegalStateException("database unavailable");
                                }));
        assertTrue(store.exists("A", reference));
        store.reap(Instant.now().minusSeconds(86400), (tenant, ref) -> false);
        assertFalse(store.exists("A", reference));
    }

    /**
     * 验证 {@code boundedPassesEventuallyReachOrphansBehindMoreThanAThousandRetainedObjects}
     * 场景下的结果与边界条件。
     */
    @Test
    void boundedPassesEventuallyReachOrphansBehindMoreThanAThousandRetainedObjects()
            throws Exception {
        var store = new LocalReportArtifactStore(directory, 1024 * 1024);
        Set<String> references = new HashSet<>();
        for (int i = 0; i < 1100; i++) {
            String ref = store.commit("A", store.temporary("A", "plan"), "plan");
            age(store.read("A", ref));
            references.add(ref);
        }
        Set<String> visited = new HashSet<>();
        store.reap(
                Instant.now().minusSeconds(86400),
                (tenant, ref) -> {
                    visited.add(ref);
                    return true;
                });
        assertTrue(visited.size() <= 1000, "one pass must remain bounded");
        String orphan =
                references.stream().filter(ref -> !visited.contains(ref)).findFirst().orElseThrow();
        for (int i = 0; i < 3; i++)
            store.reap(
                    Instant.now().minusSeconds(86400),
                    (tenant, ref) -> {
                        visited.add(ref);
                        return !ref.equals(orphan);
                    });
        assertEquals(1100, visited.size(), "the cursor must advance past retained objects");
        assertFalse(store.exists("A", orphan));
        assertEquals(1099, references.stream().filter(ref -> store.exists("A", ref)).count());
        store.close();
    }

    /** 验证 {@code boundedPassesAdvanceAcrossTenantFolders} 场景下的结果与边界条件。 */
    @Test
    void boundedPassesAdvanceAcrossTenantFolders() throws Exception {
        var store = new LocalReportArtifactStore(directory, 1024 * 1024);
        Set<String> expected = new HashSet<>();
        for (String tenant : List.of("A", "B"))
            for (int i = 0; i < 600; i++) {
                var ref = store.commit(tenant, store.temporary(tenant, "rows"), "rows");
                age(store.read(tenant, ref));
                expected.add(tenant + "/" + ref);
            }
        Set<String> visited = new HashSet<>();
        for (int i = 0; i < 3; i++)
            store.reap(
                    Instant.now().minusSeconds(86400),
                    (tenant, ref) -> {
                        visited.add(tenant + "/" + ref);
                        return true;
                    });
        assertEquals(expected.size(), visited.size());
        assertTrue(visited.containsAll(expected));
        store.close();
    }

    /** 验证 {@code shutdownReleasesAnUnfinishedCursorAndANewPassCanStart} 场景下的结果与边界条件。 */
    @Test
    void shutdownReleasesAnUnfinishedCursorAndANewPassCanStart() throws Exception {
        var store = new LocalReportArtifactStore(directory, 1024 * 1024);
        for (int i = 0; i < 1001; i++) {
            var ref = store.commit("A", store.temporary("A", "rows"), "rows");
            age(store.read("A", ref));
        }
        Set<String> visited = new HashSet<>();
        store.reap(
                Instant.now().minusSeconds(86400),
                (tenant, ref) -> {
                    visited.add(ref);
                    return true;
                });
        assertTrue(visited.size() < 1001);
        store.close();
        visited.clear();
        store.reap(
                Instant.now().minusSeconds(86400),
                (tenant, ref) -> {
                    visited.add(ref);
                    return true;
                });
        assertEquals(
                999, visited.size(), "close must reset progress before the first restarted batch");
        store.reap(
                Instant.now().minusSeconds(86400),
                (tenant, ref) -> {
                    visited.add(ref);
                    return true;
                });
        assertEquals(1001, visited.size());
        store.close();
    }

    /**
     * 完成 {@code aDisappearingTenantDirectoryDoesNotResetProgressForLaterTenants} 对应的报表产物恢复测试处理。
     *
     * @param change 当前操作的 {@code change} 输入
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"MISSING", "REPLACED"})
    void aDisappearingTenantDirectoryDoesNotResetProgressForLaterTenants(String change)
            throws Exception {
        var store = new LocalReportArtifactStore(directory, 1024 * 1024);
        store.temporary("A", "rows");
        var reference = store.commit("B", store.temporary("B", "rows"), "rows");
        age(store.read("B", reference));
        var folders = new TrackedDirectory(List.of(directory.resolve("A"), directory.resolve("B")));
        Set<String> seen = new HashSet<>();
        try (var files =
                org.mockito.Mockito.mockStatic(
                        Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            files.when(() -> Files.newDirectoryStream(directory)).thenReturn(folders);
            files.when(() -> Files.newDirectoryStream(directory.resolve("A")))
                    .thenThrow(
                            change.equals("MISSING")
                                    ? new NoSuchFileException("A")
                                    : new NotDirectoryException("A"));
            assertDoesNotThrow(
                    () ->
                            store.reap(
                                    Instant.now().minusSeconds(86400),
                                    (tenant, ref) -> {
                                        seen.add(tenant + "/" + ref);
                                        return true;
                                    }));
            assertEquals(Set.of("B/" + reference), seen);
            assertTrue(folders.closed);
        } finally {
            store.close();
        }
    }

    /** 验证 {@code closeReleasesBothDirectoryStreamsBeforeAnotherBatch} 场景下的结果与边界条件。 */
    @Test
    void closeReleasesBothDirectoryStreamsBeforeAnotherBatch() throws Exception {
        var store = new LocalReportArtifactStore(directory, 1024 * 1024);
        Path candidate = store.temporary("A", "rows");
        age(candidate);
        var folders = new TrackedDirectory(List.of(directory.resolve("A")));
        var files = new TrackedDirectory(Collections.nCopies(1001, candidate));
        try (var system =
                org.mockito.Mockito.mockStatic(
                        Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            system.when(() -> Files.newDirectoryStream(directory)).thenReturn(folders);
            system.when(() -> Files.newDirectoryStream(directory.resolve("A"))).thenReturn(files);
            store.reap(Instant.now().minusSeconds(86400), (tenant, ref) -> true);
            assertFalse(files.closed);
            assertFalse(folders.closed);
            store.close();
            assertTrue(files.closed);
            assertTrue(folders.closed);
        } finally {
            store.close();
        }
    }

    /** TrackedDirectory，负责当前模块对应的契约或处理职责。 */
    private static final class TrackedDirectory implements DirectoryStream<Path> {
        /** 保存当前 {@code entries} 值。 */
        private final List<Path> entries;

        /** 保存当前 {@code closed} 值。 */
        boolean closed;

        /**
         * 初始化TrackedDirectory并保存所需依赖或不可变值。
         *
         * @param entries 当前操作的 {@code entries} 输入
         */
        TrackedDirectory(List<Path> entries) {
            this.entries = entries;
        }

        /**
         * 验证 {@code iterator} 场景下的结果与边界条件。
         *
         * @return 当前处理返回的固定结果
         */
        public Iterator<Path> iterator() {
            return entries.iterator();
        }

        /** 释放当前对象持有的文件或扫描句柄。 */
        public void close() {
            closed = true;
        }
    }
}
