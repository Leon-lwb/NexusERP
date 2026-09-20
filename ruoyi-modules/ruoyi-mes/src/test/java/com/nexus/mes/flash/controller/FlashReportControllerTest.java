package com.nexus.mes.flash.controller;

import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.flash.domain.bo.FlashRecordBo;
import com.nexus.mes.flash.service.IFlashRecordService;
import com.nexus.mes.flash.service.ITestRecordService;
import com.nexus.mes.flash.service.impl.MesReportTenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FlashReportController 单元测试 — 免登录上报通道 P0/P1 修复回归
 * <p>覆盖: MES-FLASH-01 密钥未配置时 fail-closed 拒绝上报(不再依赖配置文件字面量)、
 * 密钥不匹配拒绝、密钥正确时按解析出的租户(MES-FLASH-10)落库调用。</p>
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
@Tag("local")
@Tag("dev")
class FlashReportControllerTest {

    @Mock
    private IFlashRecordService mesFlashRecordService;
    @Mock
    private ITestRecordService mesTestRecordService;
    @Mock
    private MesReportTenantResolver mesReportTenantResolver;
    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private FlashReportController controller;

    private void setApiKey(String value) throws Exception {
        Field field = FlashReportController.class.getDeclaredField("apiKey");
        field.setAccessible(true);
        field.set(controller, value);
    }

    @Test
    void blankApiKeyRejectsReportFailClosed() throws Exception {
        setApiKey("");
        ServiceException ex = assertThrows(ServiceException.class,
            () -> controller.reportFlash(null, List.of(new FlashRecordBo()), request));
        assertTrue(ex.getMessage().contains("未配置访问密钥"), ex.getMessage());
        verifyNoInteractions(mesFlashRecordService, mesReportTenantResolver);
    }

    @Test
    void blankApiKeyRejectsTestReportFailClosed() throws Exception {
        setApiKey(null);
        ServiceException ex = assertThrows(ServiceException.class,
            () -> controller.reportTest(null, List.of(), request));
        assertTrue(ex.getMessage().contains("未配置访问密钥"), ex.getMessage());
        verifyNoInteractions(mesTestRecordService, mesReportTenantResolver);
    }

    @Test
    void mismatchedApiKeyRejectsReport() throws Exception {
        setApiKey("CONFIGURED-KEY");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getRequestURI()).thenReturn("/mes/flash/report/flash/auto");

        ServiceException ex = assertThrows(ServiceException.class,
            () -> controller.reportFlash("WRONG-KEY", List.of(new FlashRecordBo()), request));
        assertTrue(ex.getMessage().contains("无效的API密钥"), ex.getMessage());
        verifyNoInteractions(mesFlashRecordService, mesReportTenantResolver);
    }

    @Test
    void validApiKeyResolvesDeviceTenantAndReports() throws Exception {
        setApiKey("CONFIGURED-KEY");
        when(mesReportTenantResolver.resolveFlashTenant(anyList())).thenReturn("000001");
        when(mesFlashRecordService.reportFlashBatch(anyList())).thenReturn(Map.of("success", 1));

        try (MockedStatic<TenantHelper> tenantMock = mockStatic(TenantHelper.class)) {
            tenantMock.when(() -> TenantHelper.dynamic(eq("000001"), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

            R<Map<String, Object>> response = controller.reportFlash("CONFIGURED-KEY",
                List.of(new FlashRecordBo()), request);
            assertNotNull(response);
        }
        verify(mesReportTenantResolver).resolveFlashTenant(anyList());
        verify(mesFlashRecordService).reportFlashBatch(anyList());
    }
}
