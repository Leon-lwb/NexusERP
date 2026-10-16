package com.nexus.ai.engineering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.common.ai.engineering.*;

import org.junit.jupiter.api.Test;

import java.util.Set;

class EngineeringArtifactAccessTest {
    @Test
    void everyHistoricalReadRechecksCurrentOwnerAndPermission() throws Exception {
        var f = new EngineeringArtifactManifestTest.Artifacts();
        var uploaded = f.upload("model/board.obj", "v 0 0 0\n".getBytes());
        try (var content = f.artifacts.open(f.actor, Long.valueOf(uploaded.artifactId())).input()) {
            assertEquals("v 0 0 0\n", new String(content.readAllBytes()));
        }
        assertThrows(
                EngineeringException.class,
                () ->
                        f.artifacts.open(
                                new EngineeringActor("000002", 7L, f.actor.permissions()),
                                Long.valueOf(uploaded.artifactId())));
        assertThrows(EngineeringException.class, () -> f.artifacts.open(f.actor, 99L));
        when(f.identity.resolve("000001", 7L))
                .thenReturn(
                        new EngineeringOwnerStatus(
                                true, true, "password-state", Set.of("ai:engineering:query")));
        assertThrows(
                EngineeringException.class,
                () -> f.artifacts.open(f.actor, Long.valueOf(uploaded.artifactId())));
    }

    @Test
    void confirmedArtifactsRemainReadableAfterCancelButOldUploadsDoNot() throws Exception {
        var f = new EngineeringArtifactManifestTest.Artifacts();
        var uploaded = f.upload("model/board.obj", "v 0 0 0\n".getBytes());
        f.tasks.cancel(f.actor, Long.valueOf(f.claim.taskId()));
        assertEquals(
                1,
                f.artifacts.manifest(f.actor, Long.valueOf(f.claim.taskId())).artifacts().size());
        try (var stream = f.artifacts.open(f.actor, Long.valueOf(uploaded.artifactId())).input()) {
            assertTrue(stream.readAllBytes().length > 0);
        }
        assertThrows(
                EngineeringException.class,
                () -> f.ticket("model/new.obj", "v 0 0 0\n".getBytes()));
    }
}
