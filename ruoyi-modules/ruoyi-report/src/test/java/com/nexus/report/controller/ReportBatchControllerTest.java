package com.nexus.report.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;

import com.nexus.common.report.ReportActor;
import com.nexus.report.batch.ReportBatchPdfService;
import com.nexus.report.batch.ReportBatchService;
import com.nexus.report.domain.ReportFault;

import jakarta.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.*;
import java.util.*;

/** 批次PDF接口验证权限声明、响应头、输出前检查及异常回收。 */
class ReportBatchControllerTest {
    /** 当前请求身份入口。 */
    private final ReportRequestActor actors = mock(ReportRequestActor.class);

    /** 原有批次操作。 */
    private final ReportBatchService batches = mock(ReportBatchService.class);

    /** 私有PDF合并服务。 */
    private final ReportBatchPdfService pdfs = mock(ReportBatchPdfService.class);

    /** 当前请求的固定主体。 */
    private final ReportActor actor = new ReportActor("T", 7, 10, true, List.of());

    /** 路由必须同时声明三个权限，不能只验证任意一个。 */
    @Test
    void endpointRequiresEveryPermission() throws Exception {
        var method = endpoint();
        assertArrayEquals(new String[] {"/{id}/pdf"}, method.getAnnotation(GetMapping.class).value());
        var permission = method.getAnnotation(SaCheckPermission.class);
        assertEquals(Set.of("report:batch:list", "report:runtime:preview", "report:runtime:export"), Set.of(permission.value()));
        assertEquals(SaMode.AND, permission.mode());
    }

    /** 合并结束并通过再次复核后才设置下载响应头。 */
    @Test
    void pdfHeadersAndLifecycleArePrivateAndBounded() throws Exception {
        var prepared = mock(ReportBatchPdfService.Prepared.class);
        when(actors.current()).thenReturn(actor);
        when(pdfs.merge(actor, "123")).thenReturn(prepared);
        when(prepared.bytes()).thenReturn(3L);
        doAnswer(
                        call -> {
                            ((OutputStream) call.getArgument(0)).write(new byte[] {1, 2, 3});
                            return null;
                        })
                .when(prepared)
                .write(any());
        var response = new MockHttpServletResponse();
        invoke(controller(), response);
        assertEquals("application/pdf", response.getContentType());
        assertEquals("no-store, private", response.getHeader("Cache-Control"));
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
        assertEquals("attachment; filename=\"nexus-report-batch-123.pdf\"", response.getHeader("Content-Disposition"));
        assertEquals("3", response.getHeader("Content-Length"));
        assertArrayEquals(new byte[] {1, 2, 3}, response.getContentAsByteArray());
        var order = inOrder(pdfs, prepared);
        order.verify(pdfs).merge(actor, "123");
        order.verify(prepared).verify();
        order.verify(prepared).bytes();
        order.verify(prepared).write(any());
        order.verify(prepared).close();
    }

    /** 合并之后撤权时不能设置文件头或发送任何内容，但仍释放文件。 */
    @Test
    void revocationBeforeHeadersClosesPreparedFile() throws Exception {
        var prepared = mock(ReportBatchPdfService.Prepared.class);
        when(actors.current()).thenReturn(actor);
        when(pdfs.merge(actor, "123")).thenReturn(prepared);
        doThrow(ReportFault.denied()).when(prepared).verify();
        var response = new MockHttpServletResponse();
        assertThrows(ReportFault.class, () -> invoke(controller(), response));
        assertNull(response.getContentType());
        assertEquals(0, response.getContentAsByteArray().length);
        verify(prepared).close();
        verify(prepared, never()).write(any());
    }

    /** 交付断连时控制器也必须完成关闭，不能吞掉网络异常。 */
    @Test
    void disconnectClosesPreparedFileAndPreservesIoFailure() throws Exception {
        var prepared = mock(ReportBatchPdfService.Prepared.class);
        when(actors.current()).thenReturn(actor);
        when(pdfs.merge(actor, "123")).thenReturn(prepared);
        doThrow(new IOException("断连")).when(prepared).write(any());
        assertThrows(IOException.class, () -> invoke(controller(), new MockHttpServletResponse()));
        verify(prepared).close();
    }

    /**
     * 通过反射在功能缺失时取得可运行的红灯，而不是编译失败。
     *
     * @return 批次PDF端点方法
     */
    private static Method endpoint() throws NoSuchMethodException {
        return ReportBatchController.class.getDeclaredMethod("pdf", String.class, HttpServletResponse.class);
    }

    /**
     * 装配控制器，兼容红灯阶段尚未加入的新依赖。
     *
     * @return 当前测试的控制器
     */
    private ReportBatchController controller() throws ReflectiveOperationException {
        return ReportBatchController.class.getConstructor(ReportRequestActor.class, ReportBatchService.class, ReportBatchPdfService.class)
                .newInstance(actors, batches, pdfs);
    }

    /**
     * 展开反射异常，以便测试验证真实HTTP处理异常类型。
     *
     * @param controller 控制器实例
     * @param response 测试响应
     */
    private static void invoke(ReportBatchController controller, HttpServletResponse response) throws Exception {
        try { endpoint().invoke(controller, "123", response); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            throw failure;
        }
    }
}
