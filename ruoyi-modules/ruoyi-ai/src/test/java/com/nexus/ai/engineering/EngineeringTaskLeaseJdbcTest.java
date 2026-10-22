package com.nexus.ai.engineering;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.ai.engineering.domain.EngineeringLease;
import com.nexus.ai.engineering.domain.EngineeringTaskEvent;
import com.nexus.ai.engineering.service.EngineeringTaskService;
import com.nexus.common.ai.engineering.EngineeringException;
import com.nexus.common.ai.engineering.EngineeringStage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.Set;
import java.util.concurrent.*;

class EngineeringTaskLeaseJdbcTest {
    @Test
    void concurrentClaimsOnlyOneTaskRunsAndOtherTaskKeepsWaiting() throws Exception {
        var f = new EngineeringTaskLifecycleTest.Tasks();
        var c = f.pair();
        var principal = f.online(c);
        f.tasks.createDraft(f.actor, f.command(c.deviceId(), "one"));
        f.tasks.createDraft(f.actor, f.command(c.deviceId(), "two"));
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Callable<Boolean> worker =
                    () -> {
                        start.await();
                        return f.tasks.claim(principal) != null;
                    };
            var a = executor.submit(worker);
            var b = executor.submit(worker);
            start.countDown();
            assertNotEquals(a.get(), b.get());
            var rows = f.tasks.list(f.actor, null, 1, 10);
            assertEquals(
                    1, rows.getRows().stream().filter(t -> t.state().equals("RUNNING")).count());
            assertEquals(
                    1,
                    rows.getRows().stream()
                            .filter(t -> t.state().equals("WAITING_DEVICE"))
                            .count());
            assertNotNull(
                    f.repository
                            .device(f.actor.tenantId(), f.actor.userId(), principal.deviceId())
                            .getBusyTaskId());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedTaskCasRollsBackAlreadyClaimedDeviceSlot() throws Exception {
        var f = new EngineeringTaskLifecycleTest.Tasks();
        var c = f.pair();
        var principal = f.online(c);
        var task = f.tasks.createDraft(f.actor, f.command(c.deviceId(), "rollback"));
        var repository = Mockito.spy(f.repository);
        var entered = new CountDownLatch(1);
        var canceled = new CountDownLatch(1);
        Mockito.doAnswer(
                        invocation -> {
                            if (invocation
                                    .getArgument(0, String.class)
                                    .startsWith("update ai_engineering_task set state='RUNNING'")) {
                                entered.countDown();
                                assertTrue(canceled.await(5, TimeUnit.SECONDS));
                            }
                            return invocation.callRealMethod();
                        })
                .when(repository)
                .update(ArgumentMatchers.anyString(), ArgumentMatchers.any(Object[].class));
        var tasks =
                new EngineeringTaskService(
                        repository,
                        f.devices,
                        f.access,
                        f.catalog,
                        f.properties,
                        f.clock,
                        f.sessions,
                        f.notifier);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var result = executor.submit(() -> tasks.claim(principal));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            f.tasks.cancel(f.actor, Long.valueOf(task.taskId()));
            canceled.countDown();
            var error =
                    assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
            assertInstanceOf(EngineeringException.class, error.getCause());
            assertNull(
                    f.repository
                            .device(f.actor.tenantId(), f.actor.userId(), principal.deviceId())
                            .getBusyTaskId());
            assertEquals("CANCELED", f.tasks.detail(f.actor, Long.valueOf(task.taskId())).state());
        } finally {
            canceled.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void cancelAndCompleteRaceHasOnlyOneIrreversibleTerminalState() throws Exception {
        var f = new EngineeringArtifactManifestTest.Artifacts();
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        long id = Long.parseLong(f.claim.taskId());
        try {
            var complete =
                    executor.submit(
                            () -> {
                                start.await();
                                try {
                                    return f.artifacts
                                            .complete(
                                                    f.principal,
                                                    id,
                                                    new EngineeringLease(
                                                            f.claim.attempt(),
                                                            f.claim.leaseToken(),
                                                            f.claim.revision()))
                                            .state();
                                } catch (EngineeringException e) {
                                    return "CONFLICT";
                                }
                            });
            var cancel =
                    executor.submit(
                            () -> {
                                start.await();
                                return f.tasks.cancel(f.actor, id);
                            });
            start.countDown();
            complete.get(5, TimeUnit.SECONDS);
            cancel.get(5, TimeUnit.SECONDS);
            var current = f.tasks.detail(f.actor, id);
            if (current.state().equals("RUNNING"))
                f.tasks.event(
                        f.principal,
                        id,
                        new EngineeringTaskEvent(
                                f.claim.attempt(),
                                f.claim.leaseToken(),
                                current.revision(),
                                1,
                                EngineeringStage.FINALIZING,
                                "取消已确认",
                                true,
                                null));
            var terminal = f.tasks.detail(f.actor, id);
            assertTrue(Set.of("SUCCEEDED", "CANCELED").contains(terminal.state()));
            assertNull(
                    f.repository
                            .device(f.actor.tenantId(), f.actor.userId(), f.principal.deviceId())
                            .getBusyTaskId());
            assertThrows(
                    EngineeringException.class,
                    () ->
                            f.artifacts.complete(
                                    f.principal,
                                    id,
                                    new EngineeringLease(
                                            f.claim.attempt(),
                                            f.claim.leaseToken(),
                                            terminal.revision())));
            assertEquals(terminal.state(), f.tasks.detail(f.actor, id).state());
        } finally {
            executor.shutdownNow();
        }
    }
}
