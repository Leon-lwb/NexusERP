package com.nexus.ai.engineering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.ai.engineering.service.*;
import com.nexus.ai.service.sse.AiSseMessageHelper;
import com.nexus.ai.tool.EasyedaEngineeringTool;
import com.nexus.common.ai.engineering.*;
import com.nexus.common.tenant.helper.TenantHelper;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

class EngineeringToolContextTest {
    @Test
    void contextIsClearedAfterFailureAndDoesNotLeakToNextExecutorTask() throws Exception {
        var actor = new EngineeringActor("000001", 7L, Set.of());
        var ctx = new EngineeringToolContext.Context(actor, 8L, 9L, "request-one");
        var executor = Executors.newSingleThreadExecutor();
        try {
            assertEquals(
                    "000001",
                    executor.submit(
                                    () -> {
                                        try (var scope = EngineeringToolContext.open(ctx)) {
                                            return EngineeringToolContext.current()
                                                    .actor()
                                                    .tenantId();
                                        }
                                    })
                            .get());
            assertTrue(
                    executor.submit(
                                    () -> {
                                        try {
                                            EngineeringToolContext.current();
                                            return false;
                                        } catch (EngineeringException e) {
                                            return true;
                                        }
                                    })
                            .get());
            assertThrows(
                    IllegalStateException.class,
                    () -> {
                        try (var scope = EngineeringToolContext.open(ctx)) {
                            throw new IllegalStateException("test");
                        }
                    });
            assertThrows(EngineeringException.class, EngineeringToolContext::current);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void toolCreatesActualOwnerBoundReceiptAndDoesNotWaitForLocalExecution() {
        var f = new EngineeringTaskLifecycleTest.Tasks();
        var c = f.pair();
        var tool = new EasyedaEngineeringTool(f.tasks, f.catalog);
        var ctx =
                new EngineeringToolContext.Context(
                        f.actor, Long.valueOf(c.deviceId()), null, "request-two");
        try (var tenant = mockStatic(TenantHelper.class);
                var sse = mockStatic(AiSseMessageHelper.class)) {
            tenant.when(() -> TenantHelper.dynamic(anyString(), any(Supplier.class)))
                    .thenAnswer(i -> i.getArgument(1, Supplier.class).get());
            var first = EngineeringJson.tree(tool.withContext(ctx).easyeda_check_connection());
            var second = EngineeringJson.tree(tool.withContext(ctx).easyeda_check_connection());
            assertEquals("ACCEPTED", first.path("status").asText());
            assertEquals("WAITING_DEVICE", first.path("state").asText());
            assertEquals(first.path("taskId").asText(), second.path("taskId").asText());
            var row =
                    f.repository.task(
                            f.actor.tenantId(),
                            f.actor.userId(),
                            Long.valueOf(first.path("taskId").asText()));
            assertEquals(Long.valueOf(c.deviceId()), row.getDeviceId());
            assertEquals("request-two", row.getRequestId());
            assertEquals(1, f.repository.count("select count(*) from ai_engineering_task"));
            assertEquals(1, ctx.receipts().size());
            var factory =
                    new EngineeringAgentFactory(
                            f.properties, f.access, f.repository, f.sessions, tool, f.catalog);
            String answer = factory.verifiedResult(ctx, "预览：/ai/engineering/task/123");
            assertTrue(
                    answer.contains(
                            "](/ai/engineering/task/" + first.path("taskId").asText() + ")"));
            assertFalse(answer.contains("/ai/engineering/task/123)"));
        }
    }

    @Test
    void missingContextOrInputCannotCreateTask() {
        var f = new EngineeringTaskLifecycleTest.Tasks();
        var tool = new EasyedaEngineeringTool(f.tasks, f.catalog);
        assertEquals(
                "REJECTED",
                EngineeringJson.tree(tool.easyeda_check_connection()).path("status").asText());
        var c = f.pair();
        var ctx =
                new EngineeringToolContext.Context(
                        f.actor, Long.valueOf(c.deviceId()), null, "request-three");
        assertEquals(
                "NEEDS_INPUT",
                EngineeringJson.tree(tool.withContext(ctx).easyeda_open_project(null))
                        .path("status")
                        .asText());
        assertEquals(0, f.repository.count("select count(*) from ai_engineering_task"));
    }
}
