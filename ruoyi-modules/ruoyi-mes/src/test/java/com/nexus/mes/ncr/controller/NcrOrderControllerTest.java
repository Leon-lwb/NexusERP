package com.nexus.mes.ncr.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.mes.ncr.service.INcrOrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NcrOrderController 签署处置端点单元测试 (4 tests)
 * <p>覆盖 P0-6 接入点: 控制器只做一次转发、失败原样透传、权限串复用既有种子、
 * 且不得绕过服务层自行拼两次调用(那样签署与处置就无法同事务收口)。</p>
 *
 * @author Nexus
 */
@ExtendWith(MockitoExtension.class)
class NcrOrderControllerTest {

    @Mock private INcrOrderService ncrOrderService;
    @InjectMocks private NcrOrderController controller;

    @Test
    @DisplayName("签署处置: 一次转发到服务层, 成功返回 R.ok")
    void disposeWithSignature_delegatesOnceAndReturnsOk() {
        when(ncrOrderService.disposeWithSignature(7L, "SCRAP", "备注", SignAuthMethod.PASSWORD, "admin123"))
            .thenReturn(true);

        R<Void> r = controller.disposeWithSignature(7L, "SCRAP", "备注", SignAuthMethod.PASSWORD, "admin123");

        assertEquals(200, r.getCode());
        verify(ncrOrderService, times(1))
            .disposeWithSignature(7L, "SCRAP", "备注", SignAuthMethod.PASSWORD, "admin123");
        // 控制器不得再补一次未签署的 dispose: 签署与处置必须由服务层同一事务收口
        verify(ncrOrderService, never()).dispose(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("签署处置: 服务层拒绝原样透传(LOGIN_ONLY 提示语不变)")
    void disposeWithSignature_serviceFailurePropagates() {
        when(ncrOrderService.disposeWithSignature(anyLong(), anyString(), any(), anyString(), anyString()))
            .thenThrow(new ServiceException("NCR 处置签署必须进行二级认证"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> controller.disposeWithSignature(1L, "SCRAP", null, SignAuthMethod.LOGIN_ONLY, "cred"));

        assertEquals("NCR 处置签署必须进行二级认证", ex.getMessage());
    }

    @Test
    @DisplayName("签署处置: 服务层返回 false 时不得给出成功响应")
    void disposeWithSignature_falseIsNotSuccess() {
        when(ncrOrderService.disposeWithSignature(anyLong(), anyString(), any(), anyString(), anyString()))
            .thenReturn(false);

        assertNotEquals(200, controller.disposeWithSignature(1L, "SCRAP", null, SignAuthMethod.PASSWORD, "c").getCode());
    }

    @Test
    @DisplayName("反射守卫: 复用已种子化的权限串 mes:ncr:order:dispose 且留操作日志")
    void disposeWithSignature_permissionAndLogGuards() throws NoSuchMethodException {
        Method m = NcrOrderController.class.getMethod("disposeWithSignature",
            Long.class, String.class, String.class, String.class, String.class);

        SaCheckPermission perm = m.getAnnotation(SaCheckPermission.class);
        assertNotNull(perm, "缺少 @SaCheckPermission 会让签署处置对任何登录用户开放");
        assertArrayEquals(new String[]{"mes:ncr:order:dispose"}, perm.value(),
            "必须复用既有的 mes:ncr:order:dispose(sys_menu 已种子化), 不得新造权限串");
        assertNotNull(m.getAnnotation(Log.class), "签署处置是授权动作, 必须留操作日志");
    }
}
