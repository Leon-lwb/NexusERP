package com.nexus.erp.bd;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 单位核验只读主体：纯合成登录数据，无业务连接、事务或实际数量写入。 */
class BdQuantityConfirmationContextTest {
    private static Class<?> requiredClass(String suffix) {
        try { return Class.forName("com.nexus.erp.bd." + suffix); }
        catch (ClassNotFoundException missing) { return fail("缺少独立数量上下文：" + suffix); }
    }

    private static Object invoke(Object service, String name) {
        try { return service.getClass().getMethod(name).invoke(service); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static Object field(Object value, String name) { return invoke(value, name); }

    @Test
    void endpointUsesOnlyMeasureQueryAndExactNativePrefix() throws Exception {
        Class<?> controller = requiredClass("controller.BdQuantityConfirmationContextController");
        assertArrayEquals(new String[]{"/erp/bd/measure"}, controller.getAnnotation(RequestMapping.class).value());
        Method endpoint = controller.getMethod("quantityContext");
        assertArrayEquals(new String[]{"/quantity-context"}, endpoint.getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[]{"bd:measure:query"}, endpoint.getAnnotation(SaCheckPermission.class).value());
        assertEquals(1, controller.getDeclaredMethods().length, "独立控制器不提供数量写入口");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void queryOnlySubjectCanReadAndEditIsMerelyAHint(boolean allowed) throws Exception {
        try (Fixture f = new Fixture()) {
            f.token.when(() -> StpUtil.hasPermission("bd:measure:edit")).thenReturn(allowed);
            Object result = invoke(f.service, "currentContext");
            assertEquals("000000", field(result, "tenantId"));
            assertEquals(9_007_199_254_740_993L, field(result, "userId"));
            assertEquals(allowed, field(result, "confirmAllowed"));
            f.token.verify(StpUtil::checkLogin);
            f.token.verify(() -> StpUtil.checkPermission("bd:measure:query"));
            f.token.verify(() -> StpUtil.checkPermission("bd:measure:edit"), never());
            f.token.verify(() -> StpUtil.hasPermission("pu:requisition:query"), never());
            assertEquals(0, f.service.getClass().getDeclaredFields().length,
                "只读上下文不得注入 Mapper/DataSource/业务事务以取得连接");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"DISABLED", "NULL_TENANT", "BAD_TENANT", "LONG_TENANT", "MISMATCH",
        "NULL_LOGIN_TENANT", "NULL_USER", "BAD_USER", "BLANK_NAME", "LONG_NAME", "BAD_DEPT"})
    void damagedLoginMetadataFailsClosedBeforeReadingWriteHint(String mode) throws Exception {
        try (Fixture f = new Fixture()) {
            switch (mode) {
                case "DISABLED" -> f.tenant.when(TenantHelper::isEnable).thenReturn(false);
                case "NULL_TENANT" -> f.tenant.when(TenantHelper::getTenantId).thenReturn(null);
                case "BAD_TENANT" -> f.tenant.when(TenantHelper::getTenantId).thenReturn("not-tenant");
                case "LONG_TENANT" -> f.tenant.when(TenantHelper::getTenantId).thenReturn("1".repeat(21));
                case "MISMATCH" -> f.login.when(LoginHelper::getTenantId).thenReturn("000001");
                case "NULL_LOGIN_TENANT" -> f.login.when(LoginHelper::getTenantId).thenReturn(null);
                case "NULL_USER" -> f.login.when(LoginHelper::getUserId).thenReturn(null);
                case "BAD_USER" -> f.login.when(LoginHelper::getUserId).thenReturn(0L);
                case "BLANK_NAME" -> f.login.when(LoginHelper::getUsername).thenReturn(" ");
                case "LONG_NAME" -> f.login.when(LoginHelper::getUsername).thenReturn("人".repeat(65));
                case "BAD_DEPT" -> f.login.when(LoginHelper::getDeptId).thenReturn(0L);
                default -> fail("未知测试模式");
            }
            assertThrows(ServiceException.class, () -> invoke(f.service, "currentContext"));
            f.token.verify(() -> StpUtil.hasPermission("bd:measure:edit"), never());
        }
    }

    @Test
    void directServiceCannotBypassQueryPermission() throws Exception {
        try (Fixture f = new Fixture()) {
            RuntimeException denied = new RuntimeException("合成权限拒绝");
            f.token.when(() -> StpUtil.checkPermission("bd:measure:query")).thenThrow(denied);
            assertSame(denied, assertThrows(RuntimeException.class, () -> invoke(f.service, "currentContext")));
            f.token.verify(() -> StpUtil.hasPermission("bd:measure:edit"), never());
        }
    }

    @Test
    void controllerReturnsStandardEnvelopeAndPositiveDepartmentIsAccepted() throws Exception {
        try (Fixture f = new Fixture()) {
            f.login.when(LoginHelper::getDeptId).thenReturn(12L);
            Class<?> controller = requiredClass("controller.BdQuantityConfirmationContextController");
            Object instance = controller.getConstructors()[0].newInstance(f.service);
            R<?> envelope = assertInstanceOf(R.class, invoke(instance, "quantityContext"));
            assertEquals(200, envelope.getCode());
            assertEquals("000000", field(envelope.getData(), "tenantId"));
            assertEquals(Arrays.asList("tenantId", "userId", "confirmAllowed"),
                Arrays.stream(envelope.getData().getClass().getRecordComponents()).map(c -> c.getName()).toList());
        }
    }

    /** 静态替身限定当前线程；仅模拟登录元数据，不替换 BdCatalogConfirmationSupport。 */
    private static final class Fixture implements AutoCloseable {
        final MockedStatic<StpUtil> token;
        final MockedStatic<LoginHelper> login;
        final MockedStatic<TenantHelper> tenant;
        final Object service;
        Fixture() throws Exception {
            service = requiredClass("service.BdQuantityConfirmationContextService").getConstructor().newInstance();
            token = mockStatic(StpUtil.class);
            login = mockStatic(LoginHelper.class);
            tenant = mockStatic(TenantHelper.class);
            tenant.when(TenantHelper::isEnable).thenReturn(true);
            tenant.when(TenantHelper::getTenantId).thenReturn("000000");
            login.when(LoginHelper::getTenantId).thenReturn("000000");
            login.when(LoginHelper::getUserId).thenReturn(9_007_199_254_740_993L);
            login.when(LoginHelper::getUsername).thenReturn("合成数量核验人");
            login.when(LoginHelper::getDeptId).thenReturn(null);
        }
        public void close() { tenant.close(); login.close(); token.close(); }
    }
}
