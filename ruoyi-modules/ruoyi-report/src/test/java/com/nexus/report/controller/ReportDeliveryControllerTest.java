package com.nexus.report.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.ReportActor;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.subscription.ReportDeliveryService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

/** 领取接口必须在data返回完整任务标识，不能被字符串消息重载吞掉。 */
class ReportDeliveryControllerTest {
    @ParameterizedTest
    @ValueSource(strings = {"1", "9223372036854775807"})
    void claimReturnsJobIdAsStringData(String job) throws Exception {
        var actors = mock(ReportRequestActor.class);
        var deliveries = mock(ReportDeliveryService.class);
        var actor = new ReportActor("A", 1, 10, true, List.of());
        when(actors.current()).thenReturn(actor);
        when(deliveries.claim(actor, "123")).thenReturn(job);
        var mvc = MockMvcBuilders.standaloneSetup(new ReportDeliveryController(actors, deliveries))
                .setControllerAdvice(new ReportExceptionHandler()).build();
        var response = mvc.perform(post("/report/deliveries/123/claim")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        var body = new ObjectMapper().readTree(response.getContentAsByteArray());
        assertEquals(200, body.path("code").asInt());
        assertTrue(body.path("data").isTextual());
        assertEquals(job, body.path("data").textValue());
        assertNotEquals(job, body.path("msg").textValue());
        verify(deliveries).claim(actor, "123");
    }

    @Test
    void claimKeepsServicePermissionFailure() throws Exception {
        var actors = mock(ReportRequestActor.class);
        var deliveries = mock(ReportDeliveryService.class);
        var actor = new ReportActor("A", 1, 10, true, List.of());
        when(actors.current()).thenReturn(actor);
        when(deliveries.claim(actor, "123")).thenThrow(ReportFault.denied());
        var mvc = MockMvcBuilders.standaloneSetup(new ReportDeliveryController(actors, deliveries))
                .setControllerAdvice(new ReportExceptionHandler()).build();
        var response = mvc.perform(post("/report/deliveries/123/claim")).andReturn().getResponse();
        assertEquals(403, response.getStatus());
        assertEquals("SCOPE_DENIED", new ObjectMapper().readTree(response.getContentAsByteArray()).path("errorCode").textValue());
    }
}
