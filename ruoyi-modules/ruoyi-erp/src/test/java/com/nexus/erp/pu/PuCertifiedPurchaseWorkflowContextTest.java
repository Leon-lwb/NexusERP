package com.nexus.erp.pu;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService;
import com.nexus.erp.pu.controller.PuCertifiedPurchaseWorkflowController;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseLifecycleMapper;
import com.nexus.erp.pu.mapper.PuCertifiedPurchaseWorkflowMapper;
import com.nexus.erp.pu.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 认证生命周期只读主体契约：查询权限与创建权限分离，提示不得触碰库存或业务状态机。
 * <p>身份、权限及业务端口全部为隔离替身，不连接数据库、Redis或真实认证服务。</p>
 */
class PuCertifiedPurchaseWorkflowContextTest {
    /** 通过反射发现尚不存在的契约，RED必须是缺少行为的断言而不是编译或类加载错误。 */
    private static Method requiredMethod(Class<?> owner, String name) {
        try {
            return owner.getMethod(name);
        } catch (NoSuchMethodException missing) {
            return fail("尚未接入认证生命周期当前主体只读契约: " + name);
        }
    }

    /** 不把反射包装器当作原权限/身份异常，保持直接服务调用的失败对象。 */
    private static Object invoke(Method method, Object target) {
        try {
            return method.invoke(target);
        } catch (InvocationTargetException wrapped) {
            if (wrapped.getCause() instanceof RuntimeException failure) throw failure;
            if (wrapped.getCause() instanceof Error failure) throw failure;
            return fail("只读契约不得引入新的受检失败类型");
        } catch (ReflectiveOperationException failure) {
            return fail("只读契约必须能够正常调用");
        }
    }

    private static Object field(Object value, String name) {
        assertNotNull(value, "主体返回不得为NULL");
        return invoke(requiredMethod(value.getClass(), name), value);
    }

    @Test
    void routeIsReadonlyAndRequiresQueryNotAddPermission() {
        Method method = requiredMethod(PuCertifiedPurchaseWorkflowController.class, "workflowContext");
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[]{"/workflow-context"}, mapping.value());
        assertArrayEquals(new String[]{"pu:requisition:query"}, method.getAnnotation(SaCheckPermission.class).value());
        assertEquals(0, method.getParameterCount(), "不得允许客户端提供tenant/user/verified参数");
    }

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void queryRoleCanReadIndependentApprovalAndConversionHints(boolean approve, boolean convert) {
        try (Fixture f = new Fixture()) {
            f.token.when(() -> StpUtil.hasPermission("pu:requisition:approve")).thenReturn(approve);
            f.token.when(() -> StpUtil.hasPermission("pu:requisition:convert")).thenReturn(convert);
            Object context = invoke(requiredMethod(PuCertifiedPurchaseWorkflowService.class, "currentWorkflowContext"), f.service);
            assertEquals("000000", field(context, "tenantId"));
            assertEquals(9_007_199_254_740_993L, field(context, "userId"), "雪花身份保持Long，不能截断成JS数字");
            assertEquals(approve, field(context, "approveAllowed"));
            assertEquals(convert, field(context, "convertAllowed"));
            assertEquals(List.of("tenantId", "userId", "approveAllowed", "convertAllowed"),
                Arrays.stream(context.getClass().getRecordComponents()).map(component -> component.getName()).toList(),
                "只返回内存主体及权限提示，不返回姓名/部门/令牌或来源认证证明");
            f.token.verify(StpUtil::checkLogin);
            f.token.verify(() -> StpUtil.checkPermission("pu:requisition:query"));
            f.token.verify(() -> StpUtil.checkPermission("pu:requisition:add"), never());
            f.token.verify(() -> StpUtil.checkPermission("pu:requisition:approve"), never());
            f.token.verify(() -> StpUtil.checkPermission("pu:requisition:convert"), never());
            f.noBusinessIo();
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"DISABLED_TENANT", "TENANT_MISMATCH", "NULL_LOGIN_TENANT", "NULL_USER", "NONPOSITIVE_USER", "BLANK_NAME", "BAD_DEPT"})
    void unconfirmedPrincipalIsRejectedBeforePermissionHintsOrAnyBusinessIo(String mode) {
        try (Fixture f = new Fixture()) {
            switch (mode) {
                case "DISABLED_TENANT" -> f.tenant.when(TenantHelper::isEnable).thenReturn(false);
                case "TENANT_MISMATCH" -> f.login.when(LoginHelper::getTenantId).thenReturn("000001");
                case "NULL_LOGIN_TENANT" -> f.login.when(LoginHelper::getTenantId).thenReturn(null);
                case "NULL_USER" -> f.login.when(LoginHelper::getUserId).thenReturn(null);
                case "NONPOSITIVE_USER" -> f.login.when(LoginHelper::getUserId).thenReturn(0L);
                case "BLANK_NAME" -> f.login.when(LoginHelper::getUsername).thenReturn(" ");
                case "BAD_DEPT" -> f.login.when(LoginHelper::getDeptId).thenReturn(-1L);
                default -> fail("未知测试模式");
            }
            assertThrows(ServiceException.class, () -> invoke(
                requiredMethod(PuCertifiedPurchaseWorkflowService.class, "currentWorkflowContext"), f.service));
            f.token.verify(() -> StpUtil.hasPermission("pu:requisition:approve"), never());
            f.token.verify(() -> StpUtil.hasPermission("pu:requisition:convert"), never());
            f.noBusinessIo();
        }
    }

    @Test
    void directServiceReadStillRequiresCurrentQueryPermission() {
        try (Fixture f = new Fixture()) {
            RuntimeException denial = new RuntimeException("隔离权限拒绝");
            f.token.when(() -> StpUtil.checkPermission("pu:requisition:query")).thenThrow(denial);
            RuntimeException actual = assertThrows(RuntimeException.class, () -> invoke(
                requiredMethod(PuCertifiedPurchaseWorkflowService.class, "currentWorkflowContext"), f.service));
            assertSame(denial, actual, "不能把权限拒绝转为默认主体或写权限");
            f.token.verify(() -> StpUtil.hasPermission("pu:requisition:approve"), never());
            f.noBusinessIo();
        }
    }

    @Test
    void controllerReturnsNormalEnvelopeWithoutInvokingAStateTransition() {
        try (Fixture f = new Fixture()) {
            Object value = invoke(requiredMethod(PuCertifiedPurchaseWorkflowController.class, "workflowContext"),
                new PuCertifiedPurchaseWorkflowController(f.service));
            assertInstanceOf(R.class, value);
            R<?> response = (R<?>) value;
            assertEquals(200, response.getCode());
            assertEquals("000000", field(response.getData(), "tenantId"));
            assertEquals(9_007_199_254_740_993L, field(response.getData(), "userId"));
            f.noBusinessIo();
        }
    }

    /** 固定纯合成主体；所有静态替身均限定本测试线程并逆序关闭。 */
    private static final class Fixture implements AutoCloseable {
        private final PuRequirementErpTransactionExecutor executor = mock(PuRequirementErpTransactionExecutor.class);
        private final PuRequirementErpTransactionQualification qualification = mock(PuRequirementErpTransactionQualification.class);
        private final PuRequirementMaterialLockService locks = mock(PuRequirementMaterialLockService.class);
        private final PuCertifiedPurchaseWorkflowMapper mapper = mock(PuCertifiedPurchaseWorkflowMapper.class);
        private final PuCertifiedPurchaseLifecycleMapper sources = mock(PuCertifiedPurchaseLifecycleMapper.class);
        private final PuCertifiedPurchaseLifecycleService lifecycle = mock(PuCertifiedPurchaseLifecycleService.class);
        private final PuCertifiedPurchaseWorkflowNumberReservation numbers = mock(PuCertifiedPurchaseWorkflowNumberReservation.class);
        private final ErpPurchaseSourceCertificationService certification = mock(ErpPurchaseSourceCertificationService.class);
        private final PuCertifiedPurchaseWorkflowService service = new PuCertifiedPurchaseWorkflowService(
            executor, qualification, locks, mapper, sources, lifecycle, numbers, certification);
        private final MockedStatic<StpUtil> token = mockStatic(StpUtil.class);
        private final MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
        private final MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class);

        private Fixture() {
            tenant.when(TenantHelper::isEnable).thenReturn(true);
            tenant.when(TenantHelper::getTenantId).thenReturn("000000");
            login.when(LoginHelper::getTenantId).thenReturn("000000");
            login.when(LoginHelper::getUserId).thenReturn(9_007_199_254_740_993L);
            login.when(LoginHelper::getUsername).thenReturn("隔离采购操作员");
            login.when(LoginHelper::getDeptId).thenReturn(null);
        }

        private void noBusinessIo() {
            verifyNoInteractions(executor, qualification, locks, mapper, sources, lifecycle, numbers, certification);
        }

        @Override
        public void close() {
            tenant.close();
            login.close();
            token.close();
        }
    }
}
