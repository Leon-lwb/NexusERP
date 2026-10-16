package com.nexus.ai.engineering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.ai.engineering.domain.*;
import com.nexus.ai.engineering.service.*;
import com.nexus.common.ai.engineering.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

class EngineeringArtifactUploadTest {
    static class MemoryStorage implements EngineeringStoragePort {
        final Map<Long, byte[]> files = new ConcurrentHashMap<>();

        public EngineeringArtifactStored write(EngineeringArtifactWrite write) throws IOException {
            files.put(write.artifactId(), Files.readAllBytes(write.stagedPath()));
            return new EngineeringArtifactStored(
                    write.artifactId(),
                    "private-engineering",
                    "engineering/"
                            + write.actor().tenantId()
                            + "/"
                            + write.actor().userId()
                            + "/"
                            + write.taskId()
                            + "/"
                            + write.artifactId(),
                    write.size(),
                    write.sha256());
        }

        public InputStream read(EngineeringArtifactStored stored) {
            return new ByteArrayInputStream(files.get(stored.ossId()));
        }

        public void deleteUnconfirmed(EngineeringArtifactStored stored) {
            files.remove(stored.ossId());
        }
    }

    static class Uploads extends EngineeringTaskLifecycleTest.Tasks {
        final MemoryStorage storage = new MemoryStorage();
        final EngineeringArtifactUploadService uploads =
                new EngineeringArtifactUploadService(
                        repository, tasks, devices, ephemeral, storage, properties, clock);
        final EngineeringDeviceService.Credentials credentials = pair();
        final EngineeringDevicePrincipal principal = online(credentials);
        final EngineeringTaskClaim claim;

        Uploads() {
            tasks.createDraft(actor, command(credentials.deviceId(), "upload"));
            claim = tasks.claim(principal);
        }

        EngineeringUploadRequest request(String path, byte[] bytes) {
            return new EngineeringUploadRequest(
                    claim.attempt(),
                    claim.leaseToken(),
                    claim.revision(),
                    EngineeringArtifactType.PCB_MODEL_OBJ,
                    "model-1",
                    path,
                    sha(bytes),
                    bytes.length,
                    "text/plain",
                    "board.obj");
        }

        EngineeringArtifactUploadService.Ticket ticket(String path, byte[] bytes) {
            return uploads.ticket(principal, Long.valueOf(claim.taskId()), request(path, bytes));
        }

        EngineeringArtifactUploadService.Uploaded upload(String path, byte[] bytes) {
            var ticket = ticket(path, bytes);
            return uploads.upload(
                    principal,
                    Long.valueOf(claim.taskId()),
                    ticket.uploadTicket(),
                    new MockMultipartFile("file", "board.obj", "text/plain", bytes));
        }
    }

    static String sha(byte[] bytes) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void uploadIsHashCheckedPrivateAndIdempotent() {
        var f = new Uploads();
        byte[] obj = "v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n".getBytes();
        var first = f.upload("model/board.obj", obj);
        var again = f.ticket("model/board.obj", obj);
        assertTrue(again.alreadyConfirmed());
        assertEquals(first.artifactId(), again.artifactId());
        assertEquals(1, f.storage.files.size());
        assertThrows(
                EngineeringException.class,
                () -> f.ticket("model/board.obj", "v 0 1 0".getBytes()));
    }

    @Test
    void wrongDeviceTaskOrHashCannotConfirm() {
        var f = new Uploads();
        byte[] obj = "v 0 0 0\n".getBytes();
        var ticket = f.ticket("model/board.obj", obj);
        var another = f.pair();
        var other = f.online(another);
        assertThrows(
                EngineeringException.class,
                () ->
                        f.uploads.upload(
                                other,
                                Long.valueOf(f.claim.taskId()),
                                ticket.uploadTicket(),
                                new MockMultipartFile("file", obj)));
        var hash = f.ticket("model/board.obj", obj);
        assertEquals(
                "UPLOAD_HASH_MISMATCH",
                assertThrows(
                                EngineeringException.class,
                                () ->
                                        f.uploads.upload(
                                                f.principal,
                                                Long.valueOf(f.claim.taskId()),
                                                hash.uploadTicket(),
                                                new MockMultipartFile("file", "other".getBytes())))
                        .code());
        assertTrue(f.storage.files.isEmpty());
    }

    @Test
    void concurrentUploadsCannotExceedTaskQuota() throws Exception {
        var f = new Uploads();
        f.properties.setMaxFiles(1);
        byte[] obj = "v 0 0 0\n".getBytes();
        var one = f.ticket("model/one.obj", obj);
        var two = f.ticket("model/two.obj", obj);
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Callable<Boolean> a =
                    () -> {
                        start.await();
                        try {
                            f.uploads.upload(
                                    f.principal,
                                    Long.valueOf(f.claim.taskId()),
                                    one.uploadTicket(),
                                    new MockMultipartFile("file", obj));
                            return true;
                        } catch (EngineeringException e) {
                            return false;
                        }
                    };
            Callable<Boolean> b =
                    () -> {
                        start.await();
                        try {
                            f.uploads.upload(
                                    f.principal,
                                    Long.valueOf(f.claim.taskId()),
                                    two.uploadTicket(),
                                    new MockMultipartFile("file", obj));
                            return true;
                        } catch (EngineeringException e) {
                            return false;
                        }
                    };
            var first = pool.submit(a);
            var second = pool.submit(b);
            start.countDown();
            assertNotEquals(first.get(), second.get());
            assertEquals(1, f.storage.files.size());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void cancellationAfterStorageWriteLeavesOnlyReclaimableUnconfirmedObject() {
        var f = new Uploads();
        var storage = spy(f.storage);
        try {
            doAnswer(
                            i -> {
                                var result = i.callRealMethod();
                                f.tasks.cancel(f.actor, Long.valueOf(f.claim.taskId()));
                                return result;
                            })
                    .when(storage)
                    .write(any());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        var uploads =
                new EngineeringArtifactUploadService(
                        f.repository,
                        f.tasks,
                        f.devices,
                        f.ephemeral,
                        storage,
                        f.properties,
                        f.clock);
        byte[] obj = "v 0 0 0\n".getBytes();
        var ticket =
                uploads.ticket(
                        f.principal,
                        Long.valueOf(f.claim.taskId()),
                        f.request("model/board.obj", obj));
        assertThrows(
                EngineeringException.class,
                () ->
                        uploads.upload(
                                f.principal,
                                Long.valueOf(f.claim.taskId()),
                                ticket.uploadTicket(),
                                new MockMultipartFile("file", obj)));
        assertEquals(
                0,
                f.repository.count(
                        "select count(*) from ai_engineering_artifact where status='CONFIRMED'"));
        assertEquals(
                0,
                f.repository
                        .task(f.actor.tenantId(), f.actor.userId(), Long.valueOf(f.claim.taskId()))
                        .getReservedFiles());
    }

    @Test
    void traversalExternalResourcesAndUntrustedObjScriptsAreRejected() {
        var f = new Uploads();
        byte[] valid = "v 0 0 0\n".getBytes();
        for (String path :
                List.of(
                        "../board.obj",
                        "C:/board.obj",
                        "/board.obj",
                        "//server/board.obj",
                        "model/../board.obj",
                        "model/%2e%2e/board.obj"))
            assertThrows(EngineeringException.class, () -> f.ticket(path, valid));
        for (String text :
                List.of(
                        "mtllib https://evil.invalid/material.mtl\n",
                        "call script.obj\n",
                        "mtllib ../escape.mtl\n"))
            assertThrows(
                    EngineeringException.class, () -> f.upload("model/board.obj", text.getBytes()));
    }
}
