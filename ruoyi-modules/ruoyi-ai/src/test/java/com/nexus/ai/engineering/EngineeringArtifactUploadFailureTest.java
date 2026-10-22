package com.nexus.ai.engineering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.ai.engineering.service.*;
import com.nexus.common.ai.engineering.*;
import com.nexus.common.tenant.helper.TenantHelper;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

class EngineeringArtifactUploadFailureTest {
    @Test
    void objectWrittenButDatabaseConfirmationFailureIsReclaimableAndQuotaReleased()
            throws Exception {
        var f = new EngineeringArtifactUploadTest.Uploads();
        var repository = spy(f.repository);
        doAnswer(
                        i -> {
                            if (i.getArgument(0, String.class)
                                    .startsWith(
                                            "update ai_engineering_artifact set"
                                                    + " status='CONFIRMED'"))
                                throw new DataAccessResourceFailureException("模拟确认失败");
                            return i.callRealMethod();
                        })
                .when(repository)
                .update(anyString(), any(Object[].class));
        var storage = spy(f.storage);
        var uploads =
                new EngineeringArtifactUploadService(
                        repository,
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
        assertEquals(1, storage.files.size());
        assertEquals(
                0,
                f.repository.count(
                        "select count(*) from ai_engineering_artifact where status='CONFIRMED'"));
        assertEquals(
                0,
                f.repository
                        .task(f.actor.tenantId(), f.actor.userId(), Long.valueOf(f.claim.taskId()))
                        .getReservedFiles());
        AtomicReference<String> currentTenant = new AtomicReference<>();
        doAnswer(
                        i -> {
                            assertEquals("000001", currentTenant.get());
                            return i.callRealMethod();
                        })
                .when(storage)
                .deleteUnconfirmed(any());
        try (var scope = mockStatic(TenantHelper.class)) {
            scope.when(() -> TenantHelper.dynamic(anyString(), any(Supplier.class)))
                    .thenAnswer(
                            i -> {
                                currentTenant.set(i.getArgument(0));
                                try {
                                    return i.getArgument(1, Supplier.class).get();
                                } finally {
                                    currentTenant.set(null);
                                }
                            });
            assertEquals(1, uploads.cleanupUnconfirmed());
        }
        assertTrue(storage.files.isEmpty());
        assertEquals(0, f.repository.count("select count(*) from ai_engineering_artifact"));
    }

    @Test
    void expiredTicketAndWrongAttemptAreRejectedBeforeStorage() {
        var f = new EngineeringArtifactUploadTest.Uploads();
        byte[] obj = "v 0 0 0\n".getBytes();
        var ticket = f.ticket("model/board.obj", obj);
        var data = EngineeringJson.tree(f.ephemeral.consume("upload", ticket.uploadTicket()));
        ((ObjectNode) data).put("expiresAt", "2020-01-01T00:00:00Z");
        f.ephemeral.put("upload", ticket.uploadTicket(), data.toString(), Duration.ofMinutes(5));
        assertThrows(
                EngineeringException.class,
                () ->
                        f.uploads.upload(
                                f.principal,
                                Long.valueOf(f.claim.taskId()),
                                ticket.uploadTicket(),
                                new MockMultipartFile("file", obj)));
        var old = f.ticket("model/board.obj", obj);
        f.repository.update(
                "update ai_engineering_task set attempt=attempt+1 where task_id=?",
                Long.valueOf(f.claim.taskId()));
        assertThrows(
                EngineeringException.class,
                () ->
                        f.uploads.upload(
                                f.principal,
                                Long.valueOf(f.claim.taskId()),
                                old.uploadTicket(),
                                new MockMultipartFile("file", obj)));
        assertTrue(f.storage.files.isEmpty());
    }

    @Test
    void byteQuotaAndSingleFileLimitsAreEnforced() {
        var f = new EngineeringArtifactUploadTest.Uploads();
        byte[] obj = "v 0 0 0\n".getBytes();
        f.properties.setMaxTaskBytes(obj.length);
        f.upload("model/one.obj", obj);
        assertThrows(EngineeringException.class, () -> f.upload("model/two.obj", obj));
        f.properties.setMaxArtifactBytes(obj.length - 1);
        assertThrows(EngineeringException.class, () -> f.ticket("model/large.obj", obj));
        assertEquals(1, f.storage.files.size());
    }

    @Test
    void cleaningLeaseRecoversCrashesBeforeAndAfterObjectDeletion() throws Exception {
        for (boolean alreadyDeleted : new boolean[] {false, true}) {
            var f = new EngineeringArtifactUploadTest.Uploads();
            var storage = spy(f.storage);
            doAnswer(
                            invocation -> {
                                invocation.callRealMethod();
                                throw new IOException("模拟对象写入后失败");
                            })
                    .when(storage)
                    .write(any());
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
            // 持久化状态后崩溃，分别覆盖对象尚在和对象已删两种边界。
            f.repository.update(
                    "update ai_engineering_artifact set"
                        + " status='CLEANING',cleanup_generation=1,cleanup_expires_at='2020-01-01'");
            if (alreadyDeleted) storage.files.clear();
            try (var scope = mockStatic(TenantHelper.class)) {
                scope.when(() -> TenantHelper.dynamic(anyString(), any(Supplier.class)))
                        .thenAnswer(invocation -> invocation.getArgument(1, Supplier.class).get());
                assertEquals(1, uploads.cleanupUnconfirmed());
            }
            assertTrue(storage.files.isEmpty());
            assertEquals(0, f.repository.count("select count(*) from ai_engineering_artifact"));
        }
    }
}
