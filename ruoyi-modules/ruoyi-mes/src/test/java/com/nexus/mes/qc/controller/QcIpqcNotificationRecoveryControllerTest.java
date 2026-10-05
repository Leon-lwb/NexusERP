package com.nexus.mes.qc.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.mes.qc.service.IQcIpqcService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 恢复只接收原检验 ID，沿用检验权限；不接受请求方的质量事实。 */
class QcIpqcNotificationRecoveryControllerTest {
    @Test
    void recoveryEndpointUsesInspectPermissionAndPostWithOnlyOriginalId() {
        Method endpoint = endpoint();
        assertArrayEquals(new String[]{"/{ipqcId}/notification/recover"},
            endpoint.getAnnotation(PostMapping.class).value());
        assertArrayEquals(new String[]{"mes:qc:ipqc:inspect"},
            endpoint.getAnnotation(SaCheckPermission.class).value());
        assertEquals(1, endpoint.getParameterCount());
        assertEquals(Long.class, endpoint.getParameterTypes()[0]);
        assertFalse(endpoint.getParameters()[0].isAnnotationPresent(RequestBody.class));
        assertFalse(endpoint.getParameters()[0].isAnnotationPresent(RequestParam.class));
    }

    @Test
    void recoveryResponseDistinguishesEnqueuedFromConsumed() throws Exception {
        IQcIpqcService service = mock(IQcIpqcService.class, inv ->
            "recoverNotification".equals(inv.getMethod().getName()) ? "ENQUEUED" : RETURNS_DEFAULTS.answer(inv));
        R<?> result = (R<?>) endpoint().invoke(new QcIpqcController(service), 7L);
        assertEquals("ENQUEUED", result.getData());
        assertFalse(result.getMsg().contains("消费完成"));
        assertEquals(1, mockingDetails(service).getInvocations().size());
    }

    @Test
    void recoveryResponseForPureMesDoesNotClaimMessageWasSent() throws Exception {
        IQcIpqcService service = mock(IQcIpqcService.class, inv ->
            "recoverNotification".equals(inv.getMethod().getName()) ? "SKIPPED_NO_ERP" : RETURNS_DEFAULTS.answer(inv));
        R<?> result = (R<?>) endpoint().invoke(new QcIpqcController(service), 7L);
        assertEquals("SKIPPED_NO_ERP", result.getData());
        assertFalse(result.getMsg().contains("已发送"));
        assertEquals(1, mockingDetails(service).getInvocations().size());
    }

    @Test
    void recoveryFailureCannotBecomeSuccessfulApiResponse() {
        IllegalStateException failure = new IllegalStateException("模拟通知失败");
        IQcIpqcService service = mock(IQcIpqcService.class, inv -> {
            if ("recoverNotification".equals(inv.getMethod().getName())) throw failure;
            return RETURNS_DEFAULTS.answer(inv);
        });
        Method endpoint = endpoint();
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
            () -> endpoint.invoke(new QcIpqcController(service), 7L));
        assertSame(failure, thrown.getCause());
    }

    private static Method endpoint() {
        try {
            return QcIpqcController.class.getMethod("recoverNotification", Long.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("原已完成IPQC没有正常权限的POST通知恢复入口", e);
        }
    }
}
