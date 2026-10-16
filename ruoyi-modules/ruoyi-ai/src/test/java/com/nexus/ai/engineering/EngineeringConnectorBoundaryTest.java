package com.nexus.ai.engineering;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.ai.engineering.controller.*;
import com.nexus.ai.engineering.security.*;
import com.nexus.common.tenant.helper.TenantHelper;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EngineeringConnectorBoundaryTest {
    @Test
    void missingFilterOrForgedHeadersCannotInvokeConnector() throws Exception {
        var f = new EngineeringFixture();
        var controller = new EngineeringConnectorAuthController(f.devices);
        var bare =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new EngineeringExceptionHandler())
                        .build();
        bare.perform(
                        post("/ai/engineering/connector/heartbeat")
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isUnauthorized());
        var mvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new EngineeringExceptionHandler())
                        .addFilters(
                                new EngineeringConnectorFilter(
                                        new EngineeringDeviceAuthenticator(f.devices),
                                        new ObjectMapper().findAndRegisterModules()))
                        .build();
        mvc.perform(
                        post("/ai/engineering/connector/heartbeat")
                                .contentType("application/json")
                                .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/ai/engineering/connector/heartbeat")
                                .header("X-Nexus-Device-Token", "forged")
                                .contentType("application/json")
                                .content("{\"tenantId\":\"000002\"}"))
                .andExpect(status().isUnauthorized());
        var c = f.pair();
        try (var tenant = mockStatic(TenantHelper.class)) {
            tenant.when(() -> TenantHelper.dynamic(anyString(), any(Runnable.class)))
                    .thenAnswer(
                            i -> {
                                i.getArgument(1, Runnable.class).run();
                                return null;
                            });
            mvc.perform(
                            post("/ai/engineering/connector/heartbeat")
                                    .header("X-Nexus-Device-Token", c.deviceToken())
                                    .contentType("application/json")
                                    .content(
                                            "{\"protocolVersion\":\"nexus-engineering/1\",\"connectorVersion\":\"1.0\",\"capabilities\":[\"CHECK_CONNECTION\"],\"editorAvailable\":false,\"tenantId\":\"000002\",\"userId\":\"99\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.deviceId").value(c.deviceId()))
                    .andExpect(jsonPath("$.data.accessHash").doesNotExist());
        }
    }
}
