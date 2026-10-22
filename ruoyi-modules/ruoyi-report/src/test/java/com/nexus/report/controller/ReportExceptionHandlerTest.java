package com.nexus.report.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.*;
import com.nexus.common.satoken.handler.SaTokenExceptionHandler;
import com.nexus.common.web.handler.GlobalExceptionHandler;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.runtime.ReportRuntimeService;
import com.nexus.report.storage.ReportArtifactStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.sql.SQLException;
import java.util.List;

/** 报表异常Handler测试，负责当前模块对应的契约或处理职责。 */
class ReportExceptionHandlerTest {
    /** 保存当前 {@code actors} 值。 */
    final ReportRequestActor actors = mock(ReportRequestActor.class);

    /** 保存当前 {@code runtime} 值。 */
    final ReportRuntimeService runtime = mock(ReportRuntimeService.class);

    /** 保存当前 {@code artifacts} 值。 */
    final ReportArtifactStore artifacts = mock(ReportArtifactStore.class);

    /** 待处理定义文本。 */
    final ObjectMapper json = new ObjectMapper();

    /** 保存当前 {@code mvc} 值。 */
    MockMvc mvc;

    /** 验证 {@code setup} 场景下的结果与边界条件。 */
    @BeforeEach
    void setup() {
        when(actors.current()).thenReturn(new ReportActor("T", 2, 10, true, List.of()));
        mvc =
                MockMvcBuilders.standaloneSetup(
                                new ReportRuntimeController(actors, runtime, artifacts))
                        .setControllerAdvice(
                                new ReportExceptionHandler(),
                                new GlobalExceptionHandler(),
                                new SaTokenExceptionHandler())
                        .build();
    }

    /** 验证 {@code jdbcFailureNeverReturnsSqlCredentialsOrBusinessValues} 场景下的结果与边界条件。 */
    @Test
    void jdbcFailureNeverReturnsSqlCredentialsOrBusinessValues() throws Exception {
        var sensitive = "SELECT secret_amount FROM private_table WHERE password='example-secret'";
        when(runtime.create(any(), anyString(), anyString(), any()))
                .thenThrow(
                        new BadSqlGrammarException(
                                "query", sensitive, new SQLException(sensitive)));
        var response = request();
        assertEquals(500, response.getStatus());
        var body = json.readTree(response.getContentAsString());
        assertEquals("REPORT_EXECUTION_FAILED", body.get("errorCode").asText());
        assertFalse(response.getContentAsString().contains("private_table"));
        assertFalse(response.getContentAsString().contains("example-secret"));
    }

    /** 验证 {@code datasetDenialRetainsSafeCodeAndStatusWithoutItsExceptionMessage} 场景下的结果与边界条件。 */
    @Test
    void datasetDenialRetainsSafeCodeAndStatusWithoutItsExceptionMessage() throws Exception {
        when(runtime.create(any(), anyString(), anyString(), any()))
                .thenThrow(new DatasetAccessException("FIELD_DENIED", "private sample and SQL"));
        var response = request();
        assertEquals(403, response.getStatus());
        assertEquals(
                "FIELD_DENIED",
                json.readTree(response.getContentAsString()).get("errorCode").asText());
        assertFalse(response.getContentAsString().contains("private sample"));
    }

    /** 验证 {@code knownReportFaultRetainsClientSafeStatusAndMessage} 场景下的结果与边界条件。 */
    @Test
    void knownReportFaultRetainsClientSafeStatusAndMessage() throws Exception {
        when(runtime.create(any(), anyString(), anyString(), any()))
                .thenThrow(new ReportFault("SNAPSHOT_EXPIRED", "报表运行已过期", 410));
        var response = request();
        assertEquals(410, response.getStatus());
        assertEquals(
                "SNAPSHOT_EXPIRED",
                json.readTree(response.getContentAsString()).get("errorCode").asText());
    }

    /** 验证 {@code malformedJsonReturnsAClientErrorWithoutEchoingPayload} 场景下的结果与边界条件。 */
    @Test
    void malformedJsonReturnsAClientErrorWithoutEchoingPayload() throws Exception {
        var response =
                mvc.perform(
                                post("/report/runtime/runs")
                                        .contentType("application/json")
                                        .content("{\"reportId\":\"private-value\","))
                        .andReturn()
                        .getResponse();
        assertEquals(400, response.getStatus());
        assertEquals(
                "INVALID_PARAMETER",
                json.readTree(response.getContentAsString()).get("errorCode").asText());
        assertFalse(response.getContentAsString().contains("private-value"));
        verifyNoInteractions(runtime);
    }

    /** 验证 {@code permissionFailureKeepsSaTokenPriorityOverReportGenericHandler} 场景下的结果与边界条件。 */
    @Test
    void permissionFailureKeepsSaTokenPriorityOverReportGenericHandler() throws Exception {
        when(actors.current()).thenThrow(new NotPermissionException("private:permission"));
        var response = request();
        var body = json.readTree(response.getContentAsString());
        assertEquals(403, body.get("code").asInt());
        assertFalse(response.getContentAsString().contains("private:permission"));
        assertFalse(body.has("errorCode"));
    }

    /** 验证 {@code frozenTokenKeepsSaTokenPriorityAndNeverEchoesToken} 场景下的结果与边界条件。 */
    @Test
    void frozenTokenKeepsSaTokenPriorityAndNeverEchoesToken() throws Exception {
        when(actors.current())
                .thenThrow(
                        new NotLoginException(
                                "token 已被冻结：private-jwt-example",
                                "test",
                                NotLoginException.TOKEN_FREEZE));
        var response = request();
        var body = json.readTree(response.getContentAsString());
        assertEquals(401, body.get("code").asInt());
        assertFalse(response.getContentAsString().contains("private-jwt-example"));
        assertFalse(body.has("errorCode"));
    }

    /**
     * 验证 {@code request} 场景下的结果与边界条件。
     *
     * @return 当前处理返回的固定结果
     */
    private MockHttpServletResponse request() throws Exception {
        return mvc.perform(
                        post("/report/runtime/runs")
                                .contentType("application/json")
                                .content(
                                        "{\"reportId\":\"1\",\"mode\":\"LIVE\",\"parameters\":{}}"))
                .andReturn()
                .getResponse();
    }

    /** 非结构化参数异常只返回固定提示，仍不泄露内部异常或提交的业务值。 */
    @Test
    void invalidParametersReturnUsefulSafeMessageWithoutRawDetails() throws Exception {
        when(runtime.create(any(), anyString(), anyString(), any()))
                .thenThrow(
                        new DatasetAccessException(
                                "INVALID_PARAMETER", "private SQL and submitted-value"));
        var response = request();
        var body = json.readTree(response.getContentAsString());
        assertEquals(400, response.getStatus());
        assertEquals("INVALID_PARAMETER", body.get("errorCode").asText());
        assertTrue(body.get("msg").asText().contains("筛选条件"));
        assertFalse(response.getContentAsString().contains("private SQL"));
        assertFalse(response.getContentAsString().contains("submitted-value"));
    }
}
