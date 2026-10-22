package com.nexus.ai.engineering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.ai.engineering.domain.*;
import com.nexus.ai.engineering.service.*;
import com.nexus.common.ai.engineering.*;
import com.nexus.common.ai.mapper.AiSessionMapper;
import com.nexus.common.tenant.helper.TenantHelper;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Supplier;

class EngineeringTaskLifecycleTest {
    static class Tasks extends EngineeringFixture {
        final AiSessionMapper sessions = mock(AiSessionMapper.class);
        final EngineeringTemplateCatalog catalog = new EngineeringTemplateCatalog();
        final EngineeringTaskNotifier notifier = mock(EngineeringTaskNotifier.class);
        final EngineeringTaskService tasks =
                new EngineeringTaskService(
                        repository, devices, access, catalog, properties, clock, sessions, notifier);

        EngineeringTaskCommand command(String device, String key) {
            return new EngineeringTaskCommand(
                    "nexus-engineering/1",
                    Long.valueOf(device),
                    EngineeringAction.CHECK_CONNECTION,
                    key,
                    null,
                    null,
                    null,
                    null,
                    null,
                    Map.of());
        }

        EngineeringDevicePrincipal online(EngineeringDeviceService.Credentials c) {
            var principal = devices.authenticate(c.deviceToken());
            devices.heartbeat(
                    principal,
                    new EngineeringDeviceService.Heartbeat(
                            "nexus-engineering/1",
                            "1",
                            "4.1.60",
                            "3",
                            Set.of("CHECK_CONNECTION"),
                            Map.of(),
                            true));
            return principal;
        }
    }

    @Test
    void idempotencyIsOwnerScopedAndDifferentPayloadConflicts() {
        var f = new Tasks();
        var c = f.pair();
        var task = f.tasks.createDraft(f.actor, f.command(c.deviceId(), "same"));
        assertEquals(
                task.taskId(),
                f.tasks.createDraft(f.actor, f.command(c.deviceId(), "same")).taskId());
        var second = f.pair();
        var ex =
                assertThrows(
                        EngineeringException.class,
                        () -> f.tasks.createDraft(f.actor, f.command(second.deviceId(), "same")));
        assertEquals("IDEMPOTENCY_CONFLICT", ex.code());
    }

    @Test
    void unreviewedTemplatesAndUntrustedParametersDoNotCreateDrafts() {
        var f = new Tasks();
        var c = f.pair();
        var command =
                new EngineeringTaskCommand(
                        "nexus-engineering/1",
                        Long.valueOf(c.deviceId()),
                        EngineeringAction.CREATE_PCB_DRAFT,
                        "draft",
                        null,
                        "unreviewed",
                        "1",
                        "a".repeat(64),
                        null,
                        Map.of("boardWidthMm", "50", "boardHeightMm", "35", "layers", 2));
        assertEquals(
                "TEMPLATE_UNAVAILABLE",
                assertThrows(
                                EngineeringException.class,
                                () -> f.tasks.createDraft(f.actor, command))
                        .code());
        var unsafe =
                new EngineeringTaskCommand(
                        "nexus-engineering/1",
                        Long.valueOf(c.deviceId()),
                        EngineeringAction.CHECK_CONNECTION,
                        "script",
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of("script", "danger"));
        assertThrows(EngineeringException.class, () -> f.tasks.createDraft(f.actor, unsafe));
        assertEquals("temperature-humidity-demo", f.catalog.list().get(0).templateId());
    }

    @Test
    void eventRetryIsReceiptStableAndDifferentDigestIsConflict() {
        var f = new Tasks();
        var c = f.pair();
        var p = f.online(c);
        var task = f.tasks.createDraft(f.actor, f.command(c.deviceId(), "events"));
        var claim = f.tasks.claim(p);
        var event =
                new EngineeringTaskEvent(
                        claim.attempt(),
                        claim.leaseToken(),
                        claim.revision(),
                        1,
                        EngineeringStage.CHECKING,
                        "检查中",
                        false,
                        null);
        var first = f.tasks.event(p, Long.valueOf(task.taskId()), event);
        assertEquals(first, f.tasks.event(p, Long.valueOf(task.taskId()), event));
        assertThrows(
                EngineeringException.class,
                () ->
                        f.tasks.event(
                                p,
                                Long.valueOf(task.taskId()),
                                new EngineeringTaskEvent(
                                        claim.attempt(),
                                        claim.leaseToken(),
                                        claim.revision(),
                                        1,
                                        EngineeringStage.CHECKING,
                                        "另一内容",
                                        false,
                                        null)));
        assertThrows(
                EngineeringException.class,
                () ->
                        f.tasks.renew(
                                p,
                                Long.valueOf(task.taskId()),
                                new EngineeringLease(
                                        claim.attempt(), claim.leaseToken(), claim.revision())));
        assertEquals(1, f.tasks.detail(f.actor, Long.valueOf(task.taskId())).events().size());
    }

    @Test
    void cancellationRequiresRunningDeviceAcknowledgementAndReleasesBusySlot() {
        var f = new Tasks();
        var c = f.pair();
        var p = f.online(c);
        var task = f.tasks.createDraft(f.actor, f.command(c.deviceId(), "cancel"));
        var claim = f.tasks.claim(p);
        var pending = f.tasks.cancel(f.actor, Long.valueOf(task.taskId()));
        assertTrue(pending.cancelRequested());
        assertEquals("RUNNING", pending.state());
        var done =
                f.tasks.event(
                        p,
                        Long.valueOf(task.taskId()),
                        new EngineeringTaskEvent(
                                claim.attempt(),
                                claim.leaseToken(),
                                pending.revision(),
                                1,
                                EngineeringStage.FINALIZING,
                                "已停止",
                                true,
                                null));
        assertEquals("CANCELED", done.state());
        assertNull(
                f.repository
                        .device(f.actor.tenantId(), f.actor.userId(), p.deviceId())
                        .getBusyTaskId());
        assertThrows(
                EngineeringException.class,
                () ->
                        f.tasks.renew(
                                p,
                                Long.valueOf(task.taskId()),
                                new EngineeringLease(
                                        claim.attempt(), claim.leaseToken(), done.revision())));
    }

    @Test
    void expiredLeaseIsUncertainAndNeverRequeued() {
        var f = new Tasks();
        var c = f.pair();
        var p = f.online(c);
        var task = f.tasks.createDraft(f.actor, f.command(c.deviceId(), "expired"));
        var claim = f.tasks.claim(p);
        f.repository.update(
                "update ai_engineering_task set lease_expires_at='2020-01-01' where task_id=?",
                Long.valueOf(task.taskId()));
        assertThrows(
                EngineeringException.class,
                () ->
                        f.tasks.renew(
                                p,
                                Long.valueOf(task.taskId()),
                                new EngineeringLease(
                                        claim.attempt(), claim.leaseToken(), claim.revision())));
        try (var scope = mockStatic(TenantHelper.class)) {
            scope.when(() -> TenantHelper.dynamic(anyString(), any(Supplier.class)))
                    .thenAnswer(i -> i.getArgument(1, Supplier.class).get());
            assertEquals(1, f.tasks.maintain());
        }
        var failed = f.tasks.detail(f.actor, Long.valueOf(task.taskId()));
        assertEquals("FAILED", failed.state());
        assertEquals("EXECUTION_UNCERTAIN", failed.errorCode());
        assertNull(f.tasks.claim(p));
    }

    @Test
    void crossOwnerSessionOrParentIsRejected() {
        var f = new Tasks();
        var c = f.pair();
        var command =
                new EngineeringTaskCommand(
                        "nexus-engineering/1",
                        Long.valueOf(c.deviceId()),
                        EngineeringAction.CHECK_CONNECTION,
                        "session",
                        99L,
                        null,
                        null,
                        null,
                        null,
                        Map.of());
        assertThrows(EngineeringException.class, () -> f.tasks.createDraft(f.actor, command));
        var child =
                new EngineeringTaskCommand(
                        "nexus-engineering/1",
                        Long.valueOf(c.deviceId()),
                        EngineeringAction.OPEN_PROJECT,
                        "child",
                        null,
                        null,
                        null,
                        null,
                        99L,
                        Map.of());
        assertThrows(EngineeringException.class, () -> f.tasks.createDraft(f.actor, child));
    }

    @Test
    void idleEditorCanBeLaunchedByDeclaredDraftCapability() {
        var f = new Tasks();
        var c = f.pair();
        var principal = f.online(c);
        var task = f.tasks.createDraft(f.actor, f.command(c.deviceId(), "idle-editor"));
        f.repository.update(
                "update ai_engineering_task set action='CREATE_PCB_DRAFT' where task_id=?",
                Long.valueOf(task.taskId()));
        f.devices.heartbeat(
                principal,
                new EngineeringDeviceService.Heartbeat(
                        "nexus-engineering/1",
                        "1",
                        "4.1.60",
                        null,
                        Set.of("CREATE_PCB_DRAFT"),
                        Map.of(),
                        false));
        assertNotNull(f.tasks.claim(principal));
    }
}
