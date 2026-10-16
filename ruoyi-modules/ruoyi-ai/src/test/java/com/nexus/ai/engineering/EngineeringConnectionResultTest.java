package com.nexus.ai.engineering;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.nexus.ai.engineering.controller.*;
import com.nexus.ai.engineering.domain.EngineeringLease;
import com.nexus.ai.engineering.security.EngineeringConnectorFilter;
import com.nexus.ai.engineering.service.*;
import com.nexus.ai.service.sse.AiSseMessageHelper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;

class EngineeringConnectionResultTest {
    private MockMvc mvc(EngineeringArtifactManifestTest.Artifacts f) {
        return mvc(f, f.artifacts);
    }

    private MockMvc mvc(EngineeringArtifactManifestTest.Artifacts f, EngineeringArtifactService artifacts) {
        return MockMvcBuilders.standaloneSetup(new EngineeringConnectorArtifactController(f.uploads, artifacts))
                .setControllerAdvice(new EngineeringExceptionHandler()).build();
    }

    private Map<String, Object> request(EngineeringArtifactManifestTest.Artifacts f, Map<String, Object> diagnostic) {
        var body = new LinkedHashMap<String, Object>();
        body.put("attempt", f.claim.attempt());
        body.put("leaseToken", f.claim.leaseToken());
        body.put("expectedRevision", f.claim.revision());
        if (diagnostic != null) body.put("diagnostic", diagnostic);
        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> complete(MockMvc mvc, EngineeringArtifactManifestTest.Artifacts f,
            Map<String, Object> diagnostic, int status) throws Exception {
        var response = mvc.perform(post("/ai/engineering/connector/tasks/{id}/complete", f.claim.taskId())
                .requestAttr(EngineeringConnectorFilter.PRINCIPAL, f.principal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(EngineeringJson.write(request(f, diagnostic))))
                .andReturn().getResponse();
        assertEquals(status, response.getStatus(), response.getContentAsString());
        return (Map<String, Object>) EngineeringJson.read(response.getContentAsString(), Map.class).get("data");
    }

    private Map<String, Object> connected() {
        return Map.of("connected", true, "cliVersion", "4.1.60.198f38ab", "editorVersion", "4.1.60", "transportMode", "CLI_COMPAT");
    }

    @Test
    void connectionDiagnosticSurvivesCompletionAndLaterTaskRead() throws Exception {
        var f = new EngineeringArtifactManifestTest.Artifacts();
        var input = new LinkedHashMap<>(connected());
        input.put("privatePath", "SYNTHETIC_PRIVATE_PATH");
        input.put("deviceToken", "SYNTHETIC_DEVICE_CREDENTIAL");
        var result = complete(mvc(f), f, input, 200);
        assertEquals("SUCCEEDED", result.get("state"));
        assertEquals(connected(), result.get("connectionDiagnostic"), "completed checks must return bounded diagnostics");
        var persisted = EngineeringJson.read(EngineeringJson.write(f.tasks.detail(f.actor, Long.valueOf(f.claim.taskId()))), Map.class);
        assertEquals(connected(), persisted.get("connectionDiagnostic"));
        var journal = f.tasks.ownedTask(f.actor, Long.valueOf(f.claim.taskId())).getStepJournal();
        assertFalse(journal.contains("SYNTHETIC_PRIVATE_PATH"));
        assertFalse(journal.contains("SYNTHETIC_DEVICE_CREDENTIAL"));
        assertFalse(journal.contains(f.claim.leaseToken()));
    }

    @Test
    void disconnectedDiagnosticCannotBeReportedAsSuccess() throws Exception {
        var f = new EngineeringArtifactManifestTest.Artifacts();
        var result = complete(mvc(f), f, Map.of("connected", false, "transportMode", "CLI_COMPAT"), 200);
        assertEquals("FAILED", result.get("state"));
        assertEquals("EDITOR_UNAVAILABLE", result.get("errorCode"));
        assertEquals(false, ((Map<?, ?>) result.get("connectionDiagnostic")).get("connected"));
    }

    @Test
    void unsafeVersionTextDoesNotChangeTheTask() throws Exception {
        var f = new EngineeringArtifactManifestTest.Artifacts();
        complete(mvc(f), f, Map.of("connected", true, "cliVersion", "../../private/key"), 422);
        assertEquals("RUNNING", f.tasks.detail(f.actor, Long.valueOf(f.claim.taskId())).state());
    }

    @Test
    void pcbActionsDoNotAcceptConnectionDiagnostics() throws Exception {
        var f = new EngineeringArtifactManifestTest.Artifacts();
        f.draft();
        complete(mvc(f), f, connected(), 422);
        assertEquals("RUNNING", f.tasks.detail(f.actor, Long.valueOf(f.claim.taskId())).state());
    }

    @Test
    void legacyChecksRemainReadableWithoutInventedDiagnosticDetails() throws Exception {
        var f = new EngineeringArtifactManifestTest.Artifacts();
        var result = complete(mvc(f), f, null, 200);
        assertEquals("SUCCEEDED", result.get("state"));
        assertNull(result.get("connectionDiagnostic"));
    }

    @Test
    void completionNotificationSeesCommittedStateAndIsNotRepeatedForStaleCompletion() throws Exception {
        var f = new EngineeringArtifactManifestTest.Artifacts();
        var tasks = new EngineeringTaskService(f.repository, f.devices, f.access, f.catalog, f.properties, f.clock, f.sessions, new EngineeringTaskNotifier());
        var artifacts = new EngineeringArtifactService(f.repository, tasks, f.devices, f.access, f.storage);
        try (var events = mockStatic(AiSseMessageHelper.class)) {
            events.when(() -> AiSseMessageHelper.sendEngineeringTask(anyLong(), any())).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "SSE must follow the commit");
                assertEquals("SUCCEEDED", tasks.detail(f.actor, Long.valueOf(f.claim.taskId())).state());
                return null;
            });
            complete(mvc(f, artifacts), f, null, 200);
            events.verify(() -> AiSseMessageHelper.sendEngineeringTask(eq(f.actor.userId()), argThat(task -> task.taskId().equals(f.claim.taskId()) && task.state().equals("SUCCEEDED"))));
            complete(mvc(f, artifacts), f, null, 409);
            events.verifyNoMoreInteractions();
        }
    }

    @Test
    void notificationFailureDoesNotLoseTheCommittedResult() throws Exception {
        var f = new EngineeringArtifactManifestTest.Artifacts();
        var tasks = new EngineeringTaskService(f.repository, f.devices, f.access, f.catalog, f.properties, f.clock, f.sessions, new EngineeringTaskNotifier());
        var artifacts = new EngineeringArtifactService(f.repository, tasks, f.devices, f.access, f.storage);
        try (var events = mockStatic(AiSseMessageHelper.class)) {
            events.when(() -> AiSseMessageHelper.sendEngineeringTask(anyLong(), any())).thenThrow(new IllegalStateException("synthetic event failure"));
            complete(mvc(f, artifacts), f, null, 200);
            assertEquals("SUCCEEDED", tasks.detail(f.actor, Long.valueOf(f.claim.taskId())).state());
            events.verify(() -> AiSseMessageHelper.sendEngineeringTask(eq(f.actor.userId()), any()));
        }
    }

    @Test
    void nestedCompletionWaitsForTheOuterCommit() {
        var f = new EngineeringArtifactManifestTest.Artifacts();
        var tasks = new EngineeringTaskService(f.repository, f.devices, f.access, f.catalog, f.properties, f.clock, f.sessions, new EngineeringTaskNotifier());
        var artifacts = new EngineeringArtifactService(f.repository, tasks, f.devices, f.access, f.storage);
        try (var events = mockStatic(AiSseMessageHelper.class)) {
            f.repository.transaction(() -> {
                var done = artifacts.complete(f.principal, Long.valueOf(f.claim.taskId()),
                        new EngineeringLease(f.claim.attempt(), f.claim.leaseToken(), f.claim.revision()));
                assertEquals("SUCCEEDED", done.state());
                events.verifyNoInteractions();
                return null;
            });
            events.verify(() -> AiSseMessageHelper.sendEngineeringTask(eq(f.actor.userId()), any()));
        }
    }

    @Test
    void rolledBackOuterTransactionDoesNotSendACompletionNotification() {
        var f = new EngineeringArtifactManifestTest.Artifacts();
        var tasks = new EngineeringTaskService(f.repository, f.devices, f.access, f.catalog, f.properties, f.clock, f.sessions, new EngineeringTaskNotifier());
        var artifacts = new EngineeringArtifactService(f.repository, tasks, f.devices, f.access, f.storage);
        try (var events = mockStatic(AiSseMessageHelper.class)) {
            assertThrows(IllegalStateException.class, () -> f.repository.transaction(() -> {
                artifacts.complete(f.principal, Long.valueOf(f.claim.taskId()),
                        new EngineeringLease(f.claim.attempt(), f.claim.leaseToken(), f.claim.revision()));
                throw new IllegalStateException("synthetic rollback");
            }));
            events.verifyNoInteractions();
            assertEquals("RUNNING", f.tasks.detail(f.actor, Long.valueOf(f.claim.taskId())).state());
        }
    }
}
