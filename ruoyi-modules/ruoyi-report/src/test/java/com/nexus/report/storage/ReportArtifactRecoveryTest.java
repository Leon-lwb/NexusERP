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
}
