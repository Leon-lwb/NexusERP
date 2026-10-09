package com.nexus.erp.pu;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.ErpPurchaseSourceCertificationService;
import com.nexus.erp.pu.mapper.*;
import com.nexus.erp.pu.service.*;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** query独立主体及只读SQL的先行RED；反射只用于在尚无新生产类型时保留可运行测试。 */
class PuCertifiedPurchaseOrderReadTest {
    @Test void contextUsesRealActorAndDoesNotRequireAnyWritePermissionOrBusinessIo() throws Exception {
        try (var f = new Fixture()) {
            Object c = f.context();
            assertEquals("000000", value(c, "tenantId"));
            assertEquals(901L, value(c, "userId"));
            for (String flag : new String[]{"termsAddAllowed", "termsConfirmAllowed", "approveAllowed", "closeAllowed", "cancelAllowed", "priceVisible"}) {
                assertEquals(false, value(c, flag));
            }
            f.stp.verify(() -> StpUtil.checkPermission("pu:order:query"));
            f.stp.verify(() -> StpUtil.checkPermission("pu:order:terms:add"), never());
            f.stp.verify(() -> StpUtil.checkPermission("pu:order:approve"), never());
            f.noBusinessIo();
        }
    }

    @Test void approvalHintRequiresBothApproveAndPrice() throws Exception {
        try (var f = new Fixture()) {
            f.stp.when(() -> StpUtil.hasPermission("pu:order:approve")).thenReturn(true);
            assertEquals(false, value(f.context(), "approveAllowed"));
            f.stp.when(() -> StpUtil.hasPermission("pu:order:price")).thenReturn(true);
            assertEquals(true, value(f.context(), "approveAllowed"));
            assertEquals(true, value(f.context(), "priceVisible"));
            f.noBusinessIo();
        }
    }

    @Test void termsHintsAreSeparateAndPriceIsVisibleForActualTermsActor() throws Exception {
        try (var f = new Fixture()) {
            f.stp.when(() -> StpUtil.hasPermission("pu:order:terms:confirm")).thenReturn(true);
            Object c = f.context();
            assertEquals(false, value(c, "termsAddAllowed"));
            assertEquals(true, value(c, "termsConfirmAllowed"));
            assertEquals(true, value(c, "priceVisible"));
            assertEquals(false, value(c, "approveAllowed"));
            f.noBusinessIo();
        }
    }

    @ParameterizedTest @NullSource @ValueSource(longs = {0, -1})
    void unknownActorCannotGetContext(Long userId) throws Exception {
        try (var f = new Fixture()) {
            f.login.when(LoginHelper::getUserId).thenReturn(userId);
            assertThrows(ServiceException.class, f::context);
            f.noBusinessIo();
        }
    }

    @Test void disabledTenantCannotGetContext() throws Exception {
        try (var f = new Fixture()) {
            f.tenant.when(TenantHelper::isEnable).thenReturn(false);
            assertThrows(ServiceException.class, f::context);
            f.noBusinessIo();
        }
    }

    @Test void dynamicTenantCannotMasqueradeAsLoginTenant() throws Exception {
        try (var f = new Fixture()) {
            f.tenant.when(TenantHelper::getTenantId).thenReturn("999999");
            assertThrows(ServiceException.class, f::context);
            f.noBusinessIo();
        }
    }

    @Test void queryPermissionDenialPrecedesAnyBusinessIo() throws Exception {
        try (var f = new Fixture()) {
            f.stp.when(() -> StpUtil.checkPermission("pu:order:query")).thenThrow(new ServiceException("denied"));
            assertThrows(ServiceException.class, f::context);
            f.noBusinessIo();
        }
    }

    @Test void contextAndDetailRoutesUseQueryOnlyAndDoNotReplaceWriteRoutes() throws Exception {
        Class<?> controller = Class.forName("com.nexus.erp.pu.controller.PuCertifiedPurchaseOrderReadController");
        assertArrayEquals(new String[]{"/erp/pu/order/certified"}, controller.getAnnotation(RequestMapping.class).value());
        Method context = controller.getMethod("context");
        Method detail = controller.getMethod("detail", Long.class);
        assertArrayEquals(new String[]{"/workflow-context"}, context.getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[]{"/{id}/workflow-detail"}, detail.getAnnotation(GetMapping.class).value());
        for (Method m : new Method[]{context, detail}) {
            assertArrayEquals(new String[]{"pu:order:query"}, m.getAnnotation(SaCheckPermission.class).value());
        }
    }

    @Test void nativeReadSqlIsTenantBoundNonLockingAndDoesNotInventNullVersion() throws Exception {
        Class<?> mapper = Class.forName("com.nexus.erp.pu.mapper.PuCertifiedPurchaseOrderReadMapper");
        for (String name : new String[]{"readHead", "readQuotes", "readConfirmations"}) {
            Method m = mapper.getMethod(name, String.class, Long.class);
            String sql = String.join(" ", m.getAnnotation(Select.class).value()).toUpperCase();
            assertTrue(sql.contains("TENANT_ID") && sql.contains("#{TENANTID}"));
            assertTrue(sql.contains("#{ID}"));
            assertFalse(sql.contains("FOR UPDATE"));
            assertFalse(sql.contains("INSERT ") || sql.contains("DELETE ") || sql.contains("UPDATE "));
            assertFalse(m.getAnnotation(Options.class).useCache());
            if (name.equals("readHead")) {
                assertTrue(sql.contains("CERTIFIED_VERSION"));
                assertFalse(sql.contains("IFNULL") || sql.contains("COALESCE"));
            }
        }
    }

    @Test void detailSwitchesToActualErpDatasourceAtPublicBoundary() throws Exception {
        Class<?> service = Class.forName("com.nexus.erp.pu.service.PuCertifiedPurchaseOrderReadService");
        Method detail = service.getMethod("readDetail", Long.class);
        DS ds = detail.getAnnotation(DS.class);
        if (ds == null) ds = service.getAnnotation(DS.class);
        assertNotNull(ds);
        assertEquals("erp", ds.value());
    }

    private static Object value(Object record, String name) throws Exception {
        return record.getClass().getMethod(name).invoke(record);
    }

    private static final class Fixture implements AutoCloseable {
        final MockedStatic<StpUtil> stp = mockStatic(StpUtil.class);
        final MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
        final MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class);
        final PuRequirementErpTransactionExecutor executor = mock(PuRequirementErpTransactionExecutor.class);
        final PuRequirementErpTransactionQualification qualification = mock(PuRequirementErpTransactionQualification.class);
        final PuRequirementMaterialLockService locks = mock(PuRequirementMaterialLockService.class);
        final PuCertifiedPurchaseOrderWorkflowMapper mapper = mock(PuCertifiedPurchaseOrderWorkflowMapper.class);
        final PuCertifiedPurchaseLifecycleMapper sources = mock(PuCertifiedPurchaseLifecycleMapper.class);
        final PuCertifiedPurchaseLifecycleService lifecycle = mock(PuCertifiedPurchaseLifecycleService.class);
        final ErpPurchaseSourceCertificationService certification = mock(ErpPurchaseSourceCertificationService.class);
        final PuCertifiedPurchaseOrderWorkflowService service;
        Fixture() {
            tenant.when(TenantHelper::isEnable).thenReturn(true);
            tenant.when(TenantHelper::getTenantId).thenReturn("000000");
            login.when(LoginHelper::getTenantId).thenReturn("000000");
            login.when(LoginHelper::getUserId).thenReturn(901L);
            service = new PuCertifiedPurchaseOrderWorkflowService(executor, qualification, locks, mapper, sources, lifecycle, certification);
        }
        Object context() throws Exception {
            try { return service.getClass().getMethod("currentWorkflowContext").invoke(service); }
            catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException r) throw r;
                throw e;
            }
        }
        void noBusinessIo() { verifyNoInteractions(executor, qualification, locks, mapper, sources, lifecycle, certification); }
        @Override public void close() { tenant.close(); login.close(); stp.close(); }
    }
}
