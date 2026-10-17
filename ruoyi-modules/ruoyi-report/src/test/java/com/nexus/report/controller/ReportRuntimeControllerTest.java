package com.nexus.report.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.report.ReportActor;
import com.nexus.report.domain.ReportFault;
import com.nexus.report.domain.ReportRecords.*;
import com.nexus.report.runtime.ReportRuntimeService;
import com.nexus.report.storage.ReportArtifactStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** 报表运行接口测试，负责当前模块对应的契约或处理职责。 */
class ReportRuntimeControllerTest {
    /** 测试私有目录。 */
    @TempDir Path directory;

    /** 保存当前 {@code actors} 值。 */
    final ReportRequestActor actors = mock(ReportRequestActor.class);

    /** 保存当前 {@code runtime} 值。 */
    final ReportRuntimeService runtime = mock(ReportRuntimeService.class);

    /** 保存当前 {@code artifacts} 值。 */
    final ReportArtifactStore artifacts = mock(ReportArtifactStore.class);

    /** 当前服务端身份。 */
    final ReportActor actor = new ReportActor("T", 2, 10, true, List.of());

    /** 保存当前 {@code controller} 值。 */
    final ReportRuntimeController controller =
            new ReportRuntimeController(actors, runtime, artifacts);

    /** 验证 {@code setup} 场景下的结果与边界条件。 */
    @BeforeEach
    void setup() {
        when(actors.current()).thenReturn(actor);
    }

    /** 请求中的完整预期版本应传到新增服务重载，不能在JSON绑定时被忽略。 */
    @Test
    void createForwardsExpectedVersionFromJsonToTheService() throws Exception {
        var result = run();
        var observedRuntime =
                mock(
                        ReportRuntimeService.class,
                        invocation ->
                                invocation.getMethod().getName().equals("create")
                                        ? result
                                        : RETURNS_DEFAULTS.answer(invocation));
        var json = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        var body =
                json.readValue(
                        """
                        {"reportId":"3","mode":"LIVE","parameters":{"orderId":"9007199254740993"},
                         "expectedVersionId":"9007199254740995"}
                        """,
                        ReportRuntimeController.RunRequest.class);
        var endpoint = new ReportRuntimeController(actors, observedRuntime, artifacts);
        assertEquals("1", endpoint.create(body).getData().id());
        var invocations = new ArrayList<>(mockingDetails(observedRuntime).getInvocations());
        assertEquals(1, invocations.size());
        assertArrayEquals(
                new Object[] {
                    actor,
                    "3",
                    "LIVE",
                    Map.of("orderId", "9007199254740993"),
                    "9007199254740995"
                },
                invocations.get(0).getArguments());
    }

    /** Java旧三参数请求构造和无预期版本的四参数服务调用保持兼容。 */
    @Test
    void originalThreeArgumentRequestConstructorRetainsLegacyDispatch() {
        var body = new ReportRuntimeController.RunRequest("3", "LIVE", Map.of());
        when(runtime.create(actor, "3", "LIVE", Map.of())).thenReturn(run());
        assertEquals("1", controller.create(body).getData().id());
        verify(runtime).create(actor, "3", "LIVE", Map.of());
        verifyNoMoreInteractions(runtime);
    }

    /**
     * 构造不含私有路径的成功运行，供创建请求的接口契约验证使用。
     *
     * @return 固定的成功运行
     */
    private Run run() {
        var time = Instant.now();
        return new Run(
                "1", "T", 2, "3", "4", "LIVE", "SUCCEEDED", "scope", "{}", null, null, 0,
                time, time.plusSeconds(60), null);
    }

    /** 验证 {@code runtimeAndExportViewsNeverSerializeArtifactPathsOrLeaseMetadata} 场景下的结果与边界条件。 */
    @Test
    void runtimeAndExportViewsNeverSerializeArtifactPathsOrLeaseMetadata() throws Exception {
        var time = Instant.now();
        var run =
                new Run(
                        "1",
                        "T",
                        2,
                        "3",
                        "4",
                        "SNAPSHOT",
                        "SUCCEEDED",
                        "private-parameters",
                        "scope-hash",
                        "C:/private/snapshot.rows",
                        "C:/private/layout.plan",
                        1,
                        time,
                        time.plusSeconds(60),
                        null);
        var job =
                new ExportJob(
                        "5",
                        "T",
                        2,
                        "1",
                        "PDF",
                        "request-key",
                        "request-hash",
                        "SUCCEEDED",
                        1,
                        "private-owner",
                        time.plusSeconds(30),
                        false,
                        "C:/private/export.pdf",
                        3L,
                        time.plusSeconds(60),
                        null);
        var json = new ObjectMapper().findAndRegisterModules();
        var runJson = json.valueToTree(ReportRuntimeController.view(run));
        var jobJson = json.valueToTree(ReportRuntimeController.view(job));
        for (String field :
                List.of("snapshot", "plan", "parameters", "scopeHash", "tenant", "actor"))
            assertFalse(runJson.has(field));
        for (String field :
                List.of(
                        "file",
                        "leaseOwner",
                        "leaseUntil",
                        "requestKey",
                        "requestHash",
                        "tenant",
                        "actor")) assertFalse(jobJson.has(field));
        assertFalse(runJson.toString().contains("C:/private"));
        assertFalse(jobJson.toString().contains("C:/private"));
    }

    /**
     * 验证 {@code downloadStreamsOnlyTheAuthorizedTenantsFileAndUsesSafeAttachmentName} 场景下的结果与边界条件。
     */
    @Test
    void downloadStreamsOnlyTheAuthorizedTenantsFileAndUsesSafeAttachmentName() throws Exception {
        var file = directory.resolve("private-export.pdf");
        Files.write(file, new byte[] {1, 2, 3});
        var job = job("SUCCEEDED", Instant.now().plusSeconds(60));
        when(runtime.job(actor, "untrusted-request-id")).thenReturn(job);
        var released = new java.util.concurrent.atomic.AtomicBoolean();
        when(artifacts.materialize("T", "private-file-reference"))
                .thenReturn(
                        new com.nexus.report.storage.ReportArtifactStore.Materialized(
                                file, () -> released.set(true)));
        var response = new MockHttpServletResponse();
        controller.file("untrusted-request-id", response);
        assertArrayEquals(new byte[] {1, 2, 3}, response.getContentAsByteArray());
        assertEquals("application/pdf", response.getContentType());
        assertEquals(
                "attachment; filename=\"nexus-report-5.pdf\"",
                response.getHeader("Content-Disposition"));
        assertEquals("no-store, private", response.getHeader("Cache-Control"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("3", response.getHeader("Content-Length"));
        assertFalse(response.getHeader("Content-Disposition").contains(file.toString()));
        var order = inOrder(runtime, artifacts);
        order.verify(runtime).job(actor, "untrusted-request-id");
        order.verify(artifacts).materialize("T", "private-file-reference");
        assertTrue(released.get(), "下载结束应释放私有物化生命周期");
    }

    /** 物化期间撤权必须在发送响应前阻断，且仍释放暂存资源。 */
    @Test
    void revocationDuringMaterializationReleasesFileWithoutSendingBytes() throws Exception {
        var file = directory.resolve("revoked.pdf");
        Files.write(file, new byte[] {1, 2, 3});
        var released = new java.util.concurrent.atomic.AtomicBoolean();
        when(runtime.job(actor, "5"))
                .thenReturn(job("SUCCEEDED", Instant.now().plusSeconds(60)))
                .thenThrow(ReportFault.denied());
        when(artifacts.materialize("T", "private-file-reference"))
                .thenReturn(
                        new com.nexus.report.storage.ReportArtifactStore.Materialized(
                                file, () -> released.set(true)));
        var response = new MockHttpServletResponse();
        assertThrows(ReportFault.class, () -> controller.file("5", response));
        assertEquals(0, response.getContentAsByteArray().length);
        assertTrue(released.get());
    }

    /** 验证 {@code permissionRevocationBlocksFileRead} 场景下的结果与边界条件。 */
    @Test
    void permissionRevocationBlocksFileRead() {
        when(runtime.job(actor, "5")).thenThrow(ReportFault.denied());
        assertThrows(ReportFault.class, () -> controller.file("5", new MockHttpServletResponse()));
        verifyNoInteractions(artifacts);
    }

    /** 验证 {@code expiryBlocksFileRead} 场景下的结果与边界条件。 */
    @Test
    void expiryBlocksFileRead() {
        when(runtime.job(actor, "5")).thenReturn(job("SUCCEEDED", Instant.now().minusSeconds(1)));
        assertEquals(
                "SNAPSHOT_EXPIRED",
                assertThrows(
                                ReportFault.class,
                                () -> controller.file("5", new MockHttpServletResponse()))
                        .code());
        verifyNoInteractions(artifacts);
    }

    /** 验证 {@code cancelledOrRunningJobCannotBeDownloaded} 场景下的结果与边界条件。 */
    @Test
    void cancelledOrRunningJobCannotBeDownloaded() {
        for (String state : List.of("CANCEL_REQUESTED", "CANCELLED", "RUNNING")) {
            when(runtime.job(actor, "5")).thenReturn(job(state, Instant.now().plusSeconds(60)));
            assertEquals(
                    "RUN_NOT_READY",
                    assertThrows(
                                    ReportFault.class,
                                    () -> controller.file("5", new MockHttpServletResponse()))
                            .code());
        }
        verifyNoInteractions(artifacts);
    }

    /**
     * 完成 {@code job} 对应的报表运行接口测试处理。
     *
     * @param state 目标状态
     * @param expires 资源到期时点
     * @return 当前处理返回的固定结果
     */
    private ExportJob job(String state, Instant expires) {
        return new ExportJob(
                "5",
                "T",
                2,
                "1",
                "PDF",
                "key",
                "hash",
                state,
                1,
                "owner",
                Instant.now().plusSeconds(30),
                false,
                "private-file-reference",
                3L,
                expires,
                null);
    }
}
