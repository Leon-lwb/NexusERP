package com.nexus.report.storage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ReportArtifactRecoveryTest {
    @TempDir Path directory;
    private void age(Path file)throws Exception{Files.setLastModifiedTime(file,FileTime.from(Instant.now().minusSeconds(172800)));}
    @Test void crashLeftoversAreReapedButRegisteredAndRecentArtifactsSurvive()throws Exception{
        var store=new LocalReportArtifactStore(directory,1024*1024);
        var pending=store.temporary("A","rows");age(pending);
        var temporary=store.temporary("A","pdf");var orphan=store.commit("A",temporary,"pdf");age(store.read("A",orphan));
        var registered=store.commit("A",store.temporary("A","xlsx"),"xlsx");age(store.read("A",registered));
        var recent=store.temporary("A","rows");
        Set<String> checked=new HashSet<>();store.reap(Instant.now().minusSeconds(86400),(tenant,ref)->{assertEquals("A",tenant);checked.add(ref);return ref.equals(registered);});
        assertFalse(Files.exists(pending));assertFalse(store.exists("A",orphan));assertTrue(store.exists("A",registered));assertTrue(Files.exists(recent));assertEquals(Set.of(orphan,registered),checked);
    }
    @Test void unrecognizedFilesAndTenantFoldersAreNeverReaped()throws Exception{
        var store=new LocalReportArtifactStore(directory,1024*1024);store.temporary("A","rows");var unrelated=directory.resolve("A/notes.txt");Files.writeString(unrelated,"unrelated");age(unrelated);var invalidTenant=directory.resolve("invalid.tenant");Files.createDirectories(invalidTenant);var outside=invalidTenant.resolve(UUID.randomUUID()+".pdf");Files.writeString(outside,"unrelated");age(outside);
        store.reap(Instant.now().minusSeconds(86400),(tenant,ref)->false);assertTrue(Files.exists(unrelated));assertTrue(Files.exists(outside));
    }
    @Test void databaseFailureRetainsAgedUnregisteredCandidatesForTheNextAttempt()throws Exception{
        var store=new LocalReportArtifactStore(directory,1024*1024);var reference=store.commit("A",store.temporary("A","pdf"),"pdf");age(store.read("A",reference));
        assertThrows(IllegalStateException.class,()->store.reap(Instant.now().minusSeconds(86400),(tenant,ref)->{throw new IllegalStateException("database unavailable");}));assertTrue(store.exists("A",reference));store.reap(Instant.now().minusSeconds(86400),(tenant,ref)->false);assertFalse(store.exists("A",reference));
    }
    @Test void boundedPassesEventuallyReachOrphansBehindMoreThanAThousandRetainedObjects() throws Exception {
        var store = new LocalReportArtifactStore(directory, 1024 * 1024);
        Set<String> references = new HashSet<>();
        for (int i = 0; i < 1100; i++) {
            String ref = store.commit("A", store.temporary("A", "plan"), "plan");
            age(store.read("A", ref)); references.add(ref);
        }
        Set<String> visited = new HashSet<>();
        store.reap(Instant.now().minusSeconds(86400), (tenant, ref) -> { visited.add(ref); return true; });
        assertTrue(visited.size() <= 1000, "one pass must remain bounded");
        String orphan = references.stream().filter(ref -> !visited.contains(ref)).findFirst().orElseThrow();
        for (int i = 0; i < 3; i++) store.reap(Instant.now().minusSeconds(86400), (tenant, ref) -> { visited.add(ref); return !ref.equals(orphan); });
        assertEquals(1100, visited.size(), "the cursor must advance past retained objects");
        assertFalse(store.exists("A", orphan));
        assertEquals(1099, references.stream().filter(ref -> store.exists("A", ref)).count());
        store.close();
    }
    @Test void boundedPassesAdvanceAcrossTenantFolders() throws Exception {
        var store = new LocalReportArtifactStore(directory, 1024 * 1024);
        Set<String> expected = new HashSet<>();
        for (String tenant : List.of("A", "B")) for (int i = 0; i < 600; i++) {
            var ref = store.commit(tenant, store.temporary(tenant, "rows"), "rows");
            age(store.read(tenant, ref)); expected.add(tenant + "/" + ref);
        }
        Set<String> visited = new HashSet<>();
        for (int i = 0; i < 3; i++) store.reap(Instant.now().minusSeconds(86400), (tenant, ref) -> { visited.add(tenant + "/" + ref); return true; });
        assertEquals(expected.size(), visited.size());
        assertTrue(visited.containsAll(expected));
        store.close();
    }
    @Test void shutdownReleasesAnUnfinishedCursorAndANewPassCanStart() throws Exception {
        var store = new LocalReportArtifactStore(directory, 1024 * 1024);
        for (int i = 0; i < 1001; i++) {
            var ref = store.commit("A", store.temporary("A", "rows"), "rows"); age(store.read("A", ref));
        }
        Set<String> visited = new HashSet<>();
        store.reap(Instant.now().minusSeconds(86400), (tenant, ref) -> { visited.add(ref); return true; });
        assertTrue(visited.size() < 1001); store.close(); visited.clear();
        store.reap(Instant.now().minusSeconds(86400), (tenant, ref) -> { visited.add(ref); return true; });
        assertEquals(999, visited.size(), "close must reset progress before the first restarted batch");
        store.reap(Instant.now().minusSeconds(86400), (tenant, ref) -> { visited.add(ref); return true; });
        assertEquals(1001, visited.size()); store.close();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"MISSING", "REPLACED"})
    void aDisappearingTenantDirectoryDoesNotResetProgressForLaterTenants(String change) throws Exception {
        var store = new LocalReportArtifactStore(directory, 1024 * 1024);
        store.temporary("A", "rows"); var reference = store.commit("B", store.temporary("B", "rows"), "rows"); age(store.read("B", reference));
        var folders = new TrackedDirectory(List.of(directory.resolve("A"), directory.resolve("B")));
        Set<String> seen = new HashSet<>();
        try (var files = org.mockito.Mockito.mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            files.when(() -> Files.newDirectoryStream(directory)).thenReturn(folders);
            files.when(() -> Files.newDirectoryStream(directory.resolve("A"))).thenThrow(
                change.equals("MISSING") ? new NoSuchFileException("A") : new NotDirectoryException("A"));
            assertDoesNotThrow(() -> store.reap(Instant.now().minusSeconds(86400), (tenant, ref) -> { seen.add(tenant + "/" + ref); return true; }));
            assertEquals(Set.of("B/" + reference), seen); assertTrue(folders.closed);
        } finally { store.close(); }
    }
    @Test void closeReleasesBothDirectoryStreamsBeforeAnotherBatch() throws Exception {
        var store = new LocalReportArtifactStore(directory, 1024 * 1024);
        Path candidate = store.temporary("A", "rows"); age(candidate);
        var folders = new TrackedDirectory(List.of(directory.resolve("A")));
        var files = new TrackedDirectory(Collections.nCopies(1001, candidate));
        try (var system = org.mockito.Mockito.mockStatic(Files.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            system.when(() -> Files.newDirectoryStream(directory)).thenReturn(folders);
            system.when(() -> Files.newDirectoryStream(directory.resolve("A"))).thenReturn(files);
            store.reap(Instant.now().minusSeconds(86400), (tenant, ref) -> true);
            assertFalse(files.closed); assertFalse(folders.closed);
            store.close(); assertTrue(files.closed); assertTrue(folders.closed);
        } finally { store.close(); }
    }
    private static final class TrackedDirectory implements DirectoryStream<Path> {
        private final List<Path> entries; boolean closed;
        TrackedDirectory(List<Path> entries) { this.entries = entries; }
        public Iterator<Path> iterator() { return entries.iterator(); }
        public void close() { closed = true; }
    }
}
