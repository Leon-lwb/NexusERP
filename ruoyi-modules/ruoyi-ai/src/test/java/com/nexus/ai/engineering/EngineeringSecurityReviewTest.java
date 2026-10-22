package com.nexus.ai.engineering;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.nexus.ai.engineering.domain.*;
import com.nexus.ai.engineering.service.*;
import com.nexus.common.ai.domain.entity.*;
import com.nexus.common.ai.engineering.*;
import com.nexus.common.web.config.JacksonConfig;

import org.junit.jupiter.api.Test;

import java.util.*;

class EngineeringSecurityReviewTest {
    @Test
    void multilineControlCharactersCannotBypassRelativePathValidation() {
        for (String path :
                List.of(
                        "model/a\nb\nc.obj",
                        "model/a\r\nb.obj",
                        "model/a\u007fb.obj",
                        "model/a\u202eb.obj"))
            assertThrows(EngineeringException.class, () -> EngineeringPaths.validate(path));
    }

    @Test
    void internalCredentialsAndStorageLocatorsAreNeverSerializedEvenAccidentally()
            throws Exception {
        var json =
                new ObjectMapper()
                        .findAndRegisterModules()
                        .registerModule(new JacksonConfig().bigNumberModule());
        var device = new AiEngineeringDevice();
        device.setAccessHash("test-access-hash");
        device.setRefreshHash("test-refresh-hash");
        device.setCredentialFingerprint("test-fingerprint");
        var task = new AiEngineeringTask();
        task.setTaskId(9223372036854775806L);
        task.setLeaseHash("test-lease-hash");
        var artifact = new AiEngineeringArtifact();
        artifact.setObjectKey("engineering/internal");
        artifact.setService("private-engineering");
        artifact.setOssId(123L);
        assertFalse(json.writeValueAsString(device).contains("test-"));
        assertFalse(json.writeValueAsString(task).contains("test-lease"));
        assertTrue(json.writeValueAsString(task).contains("\"9223372036854775806\""));
        String value = json.writeValueAsString(artifact);
        assertFalse(value.contains("engineering/internal"));
        assertFalse(value.contains("private-engineering"));
        assertFalse(value.contains("ossId"));
    }

    @Test
    void requestBoundPrincipalCannotContinueAfterAccessCredentialExpires() {
        var f = new EngineeringTaskLifecycleTest.Tasks();
        var c = f.pair();
        var principal = f.online(c);
        var task = f.tasks.createDraft(f.actor, f.command(c.deviceId(), "expiry"));
        var claim = f.tasks.claim(principal);
        f.repository.update(
                "update ai_engineering_device set access_expires_at='2020-01-01' where device_id=?",
                principal.deviceId());
        assertThrows(
                EngineeringException.class,
                () ->
                        f.tasks.renew(
                                principal,
                                Long.valueOf(task.taskId()),
                                new EngineeringLease(
                                        claim.attempt(), claim.leaseToken(), claim.revision())));
    }

    @Test
    void taskViewContainsTemplateDefaultsAndNavigablePreviewRoute() {
        var f = new EngineeringTaskLifecycleTest.Tasks();
        var c = f.pair();
        var template = f.catalog.list().get(0);
        var task =
                f.tasks.createDraft(
                        f.actor,
                        new EngineeringTaskCommand(
                                "nexus-engineering/1",
                                Long.valueOf(c.deviceId()),
                                EngineeringAction.CREATE_PCB_DRAFT,
                                "dto",
                                null,
                                template.templateId(),
                                template.templateVersion(),
                                template.templateSha256(),
                                null,
                                Map.of()));
        var json = EngineeringJson.tree(EngineeringJson.write(task));
        assertEquals(template.templateId(), json.path("templateId").asText());
        assertEquals("50", json.path("parameters").path("boardWidthMm").asText());
        assertEquals("/ai/engineering/task/" + task.taskId(), task.previewPath());
    }

    @Test
    void compatibilityTransportDiagnosticsArePreservedInHeartbeat() throws Exception {
        var f = new EngineeringFixture();
        var c = f.pair();
        var principal = f.devices.authenticate(c.deviceToken());
        var request =
                new ObjectMapper()
                        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        .readValue(
                                "{\"protocolVersion\":\"nexus-engineering/1\",\"capabilities\":[\"CHECK_CONNECTION\"],\"editorAvailable\":false,\"transportMode\":\"CLI_COMPAT\",\"diagnosticCode\":\"MCP_NOT_SUPPORTED\",\"diagnosticMessage\":\"使用受控CLI兼容模式\"}",
                                EngineeringDeviceService.Heartbeat.class);
        var view = f.devices.heartbeat(principal, request);
        assertEquals(
                "CLI_COMPAT",
                EngineeringJson.tree(EngineeringJson.write(view.capabilities()))
                        .path("transportMode")
                        .asText());
    }
}
