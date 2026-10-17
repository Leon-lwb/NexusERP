package com.nexus.report.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.nexus.common.report.*;
import com.nexus.report.controller.ReportExceptionHandler;
import com.nexus.report.controller.ReportRequestActor;
import com.nexus.report.controller.ReportRuntimeController;
import com.nexus.report.domain.ReportRecords.Version;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** 预期发布版本在数据查询和运行持久化之前校验的真实运行回归。 */
class ReportRuntimeExpectedVersionTest {
    /** 每个用例独立使用的私有快照目录。 */
    @TempDir Path directory;

    /** 版本冲突必须返回稳定错误信封，且实时与快照运行均无副作用。 */
    @ParameterizedTest
    @ValueSource(strings = {"LIVE", "SNAPSHOT"})
    void changedVersionIsRejectedBeforeQueriesRunsAndSnapshots(String mode) throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var response = request(fixture, fixture.owner, mode, Map.of("expectedVersionId", "1"));
            assertAll(
                    () -> assertFailure(fixture, response, 409, "REPORT_VERSION_CHANGED"),
                    () -> assertNoExecution(fixture));
        }
    }

    /** 版本一致时创建绑定该完整字符串标识的真实快照运行。 */
    @Test
    void matchingVersionCreatesSnapshotBoundToTheRequestedVersion() throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var version = published(fixture);
            var response =
                    request(
                            fixture,
                            fixture.owner,
                            "SNAPSHOT",
                            Map.of("expectedVersionId", version.id()));
            assertEquals(200, response.getStatus(), response.getContentAsString());
            var data = fixture.json.readTree(response.getContentAsString()).get("data");
            assertEquals(version.id(), data.get("version").asText());
            var run = fixture.store.run("A", data.get("id").asText()).orElseThrow();
            assertEquals(version.id(), run.version());
            assertEquals("SUCCEEDED", run.state());
            assertNotNull(run.snapshot());
            assertFalse(fixture.provider.queries.isEmpty());
            assertTrue(fixture.committedFiles() > 0);
        }
    }

    /** 老客户端省略预期版本时仍按原有语义运行当前发布版本。 */
    @Test
    void omittedVersionRetainsTheExistingRequestContract() throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var version = published(fixture);
            var response = request(fixture, fixture.owner, "LIVE", Map.of());
            assertEquals(200, response.getStatus(), response.getContentAsString());
            assertEquals(
                    version.id(),
                    fixture.json.readTree(response.getContentAsString())
                            .get("data")
                            .get("version")
                            .asText());
        }
    }

    /** 显式空值与字段省略保持兼容，但不把空白字符串视为省略。 */
    @Test
    void nullVersionRetainsTheExistingRequestContract() throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var fields = new HashMap<String, Object>();
            fields.put("expectedVersionId", null);
            var response = request(fixture, fixture.owner, "LIVE", fields);
            assertEquals(200, response.getStatus(), response.getContentAsString());
        }
    }

    /** Java旧四参数入口继续创建当前发布版本，不要求调用方改签名。 */
    @Test
    void originalFourArgumentServiceMethodRemainsAvailable() {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var version = published(fixture);
            var run =
                    fixture.runtime.create(
                            fixture.owner, fixture.definition.id(), "LIVE", Map.of());
            assertEquals(version.id(), run.version());
            assertEquals("SUCCEEDED", run.state());
        }
    }

    /** 空串、普通空格与其他空白均在执行前返回参数错误。 */
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n", "\u2003"})
    void blankVersionIsRejectedWithoutExecution(String expectedVersion) throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var response =
                    request(
                            fixture,
                            fixture.owner,
                            "SNAPSHOT",
                            Map.of("expectedVersionId", expectedVersion));
            assertAll(
                    () -> assertFailure(fixture, response, 400, "INVALID_TEMPLATE"),
                    () -> assertNoExecution(fixture));
        }
    }

    /** 固定版本标识按原字符串比较，不自动去除空格后接受。 */
    @Test
    void versionIdentifierIsComparedWithoutTrimming() throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var response =
                    request(
                            fixture,
                            fixture.owner,
                            "SNAPSHOT",
                            Map.of("expectedVersionId", " " + published(fixture).id() + " "));
            assertAll(
                    () -> assertFailure(fixture, response, 409, "REPORT_VERSION_CHANGED"),
                    () -> assertNoExecution(fixture));
        }
    }

    /** 其他租户的有效身份不能借版本冲突错误探知本租户模板。 */
    @Test
    void anotherTenantCannotReachTheVersionComparison() throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var other = new ReportActor("B", 9, 10, true, List.of());
            fixture.actors.put(other.userId(), other);
            var response = request(fixture, other, "SNAPSHOT", Map.of("expectedVersionId", "1"));
            assertEquals(404, response.getStatus(), response.getContentAsString());
            assertNoExecution(fixture);
        }
    }

    /** 资源VIEW撤销时先拒绝访问，不返回版本是否变化。 */
    @Test
    void revokedViewIsCheckedBeforeVersionMismatch() throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            fixture.revokeReportView();
            var response =
                    request(fixture, fixture.viewer, "SNAPSHOT", Map.of("expectedVersionId", "1"));
            assertFailure(fixture, response, 403, "SCOPE_DENIED");
            assertNoExecution(fixture);
        }
    }

    /** 重新解析后缺少preview权限的身份优先拒绝，即使预期版本为空。 */
    @Test
    void currentPreviewPermissionIsCheckedBeforeBlankVersion() throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            fixture.actors.put(
                    fixture.owner.userId(),
                    RuntimeReviewFixture.actor(7, Set.of("wm:stock:list"), ReportScope.all(7)));
            var response =
                    request(fixture, fixture.owner, "SNAPSHOT", Map.of("expectedVersionId", ""));
            assertFailure(fixture, response, 403, "SCOPE_DENIED");
            assertNoExecution(fixture);
        }
    }

    /** 已禁用账户不能使用请求中保存的旧身份执行版本检查或查询。 */
    @Test
    void disabledAccountIsRejectedBeforeVersionMismatch() throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            fixture.actors.remove(fixture.owner.userId());
            var response =
                    request(fixture, fixture.owner, "SNAPSHOT", Map.of("expectedVersionId", "1"));
            assertFailure(fixture, response, 403, "SCOPE_DENIED");
            assertNoExecution(fixture);
        }
    }

    /** 检查通过后即使快照生成期间重新发布，本次运行仍绑定已解析的旧版本。 */
    @Test
    void publicationDuringSnapshotDoesNotReplaceTheResolvedImmutableVersion() throws Exception {
        try (var fixture = new RuntimeReviewFixture(directory)) {
            var version = published(fixture);
            var replacement = new AtomicReference<Version>();
            fixture.provider.streamOverride =
                    (context, consumer) -> {
                        var draft =
                                fixture.store
                                        .definition("A", fixture.definition.id())
                                        .orElseThrow();
                        replacement.set(
                                fixture.definitions.publish(
                                        fixture.owner, draft.id(), draft.revision()));
                        context.cancellation().check();
                        consumer.accept(fixture.provider.row());
                    };
            var response =
                    request(
                            fixture,
                            fixture.owner,
                            "SNAPSHOT",
                            Map.of("expectedVersionId", version.id()));
            assertEquals(200, response.getStatus(), response.getContentAsString());
            assertNotNull(replacement.get());
            assertNotEquals(version.id(), replacement.get().id());
            var data = fixture.json.readTree(response.getContentAsString()).get("data");
            var run = fixture.store.run("A", data.get("id").asText()).orElseThrow();
            assertEquals(version.id(), run.version());
            assertNotNull(run.snapshot());
            assertEquals(replacement.get().id(), published(fixture).id());
        }
    }

    /**
     * 通过真实运行控制器提交JSON，同时保留当前请求携带的身份快照。
     *
     * @param fixture 隔离运行夹具
     * @param actor 请求中携带的原身份
     * @param mode 运行模式
     * @param fields 可选版本字段
     * @return 实际HTTP响应
     */
    private MockHttpServletResponse request(
            RuntimeReviewFixture fixture,
            ReportActor actor,
            String mode,
            Map<String, Object> fields)
            throws Exception {
        var actors = mock(ReportRequestActor.class);
        when(actors.current()).thenReturn(actor);
        var mvc =
                MockMvcBuilders.standaloneSetup(
                                new ReportRuntimeController(
                                        actors, fixture.runtime, fixture.artifacts))
                        .setControllerAdvice(new ReportExceptionHandler())
                        .build();
        var body = new LinkedHashMap<String, Object>();
        body.put("reportId", fixture.definition.id());
        body.put("mode", mode);
        body.put("parameters", Map.of());
        body.putAll(fields);
        return mvc.perform(
                        post("/report/runtime/runs")
                                .contentType("application/json")
                                .content(fixture.json.writeValueAsBytes(body)))
                .andReturn()
                .getResponse();
    }

    /**
     * 读取当前可访问的不可变发布版本。
     *
     * @param fixture 隔离运行夹具
     * @return 当前发布版本
     */
    private Version published(RuntimeReviewFixture fixture) {
        return fixture.definitions.published(
                fixture.owner, fixture.definition.id(), "VIEW", "report:runtime:preview");
    }

    /**
     * 校验现有错误信封保留HTTP状态和业务错误编码。
     *
     * @param fixture 隔离运行夹具
     * @param response 实际HTTP响应
     * @param status 预期HTTP状态
     * @param code 预期业务错误编码
     */
    private void assertFailure(
            RuntimeReviewFixture fixture,
            MockHttpServletResponse response,
            int status,
            String code)
            throws Exception {
        assertEquals(status, response.getStatus(), response.getContentAsString());
        var body = fixture.json.readTree(response.getContentAsString());
        assertTrue(body.has("code"), response.getContentAsString());
        assertTrue(body.has("errorCode"), response.getContentAsString());
        assertEquals(status, body.get("code").asInt());
        assertEquals(code, body.get("errorCode").asText());
        assertTrue(body.get("msg").isTextual());
    }

    /**
     * 查询记录、运行表和私有存储同时为空才视为执行前拒绝。
     *
     * @param fixture 隔离运行夹具
     */
    private void assertNoExecution(RuntimeReviewFixture fixture) {
        assertAll(
                () -> assertTrue(fixture.provider.queries.isEmpty(), "提供者不得查询"),
                () ->
                        assertEquals(
                                0L,
                                fixture.jdbc.queryForObject(
                                        "SELECT COUNT(*) FROM rpt_run", Long.class),
                                "不得创建运行或快照引用"),
                () -> assertEquals(0, fixture.committedFiles(), "不得创建私有快照或计划"));
    }
}
