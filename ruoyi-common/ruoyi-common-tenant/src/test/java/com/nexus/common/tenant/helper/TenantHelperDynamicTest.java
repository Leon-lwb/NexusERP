package com.nexus.common.tenant.helper;

import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

@Tag("local")
@Tag("dev")
@Tag("prod")
class TenantHelperDynamicTest {

    @Test
    void nestedRunnableRestoresOuterTenantAndDoesNotLeak() {
        withTenantEnabled(() -> {
            TenantHelper.dynamic("TENANT-A", (Runnable) () -> {
                assertEquals("TENANT-A", TenantHelper.getDynamic());
                TenantHelper.dynamic("TENANT-B", (Runnable) () ->
                    assertEquals("TENANT-B", TenantHelper.getDynamic()));
                assertEquals("TENANT-A", TenantHelper.getDynamic());
            });
            assertNull(TenantHelper.getDynamic());
        });
    }

    @Test
    void nestedSupplierRestoresOuterTenantAndReturnsResult() {
        withTenantEnabled(() -> {
            String result = TenantHelper.dynamic("TENANT-A", () -> {
                assertEquals("TENANT-A", TenantHelper.getDynamic());
                String inner = TenantHelper.dynamic("TENANT-B", () -> TenantHelper.getDynamic());
                assertEquals("TENANT-B", inner);
                assertEquals("TENANT-A", TenantHelper.getDynamic());
                return TenantHelper.getDynamic();
            });
            assertEquals("TENANT-A", result);
            assertNull(TenantHelper.getDynamic());
        });
    }

    @Test
    void innerRunnableFailureRestoresOuterTenant() {
        withTenantEnabled(() -> {
            IllegalStateException failure = new IllegalStateException("inner failure");
            TenantHelper.dynamic("TENANT-A", (Runnable) () -> {
                assertEquals("TENANT-A", TenantHelper.getDynamic());
                assertSame(failure, assertThrows(IllegalStateException.class, () ->
                    TenantHelper.dynamic("TENANT-B", (Runnable) () -> {
                        assertEquals("TENANT-B", TenantHelper.getDynamic());
                        throw failure;
                    })));
                assertEquals("TENANT-A", TenantHelper.getDynamic());
            });
            assertNull(TenantHelper.getDynamic());
        });
    }

    @Test
    void outerSupplierFailureDoesNotLeakTenant() {
        withTenantEnabled(() -> {
            IllegalStateException failure = new IllegalStateException("outer failure");
            assertSame(failure, assertThrows(IllegalStateException.class, () ->
                TenantHelper.dynamic("TENANT-A", () -> {
                    assertEquals("TENANT-A", TenantHelper.getDynamic());
                    throw failure;
                })));
            assertNull(TenantHelper.getDynamic());
        });
    }

    @Test
    void runnableRestoresExistingThreadTenantAfterNormalExit() {
        withTenantEnabled(() -> {
            TenantHelper.setDynamic("EXISTING");
            TenantHelper.dynamic("TENANT-A", (Runnable) () ->
                assertEquals("TENANT-A", TenantHelper.getDynamic()));
            assertEquals("EXISTING", TenantHelper.getDynamic());
        });
    }

    @Test
    void supplierRestoresExistingThreadTenantAfterException() {
        withTenantEnabled(() -> {
            TenantHelper.setDynamic("EXISTING");
            IllegalStateException failure = new IllegalStateException("supplier failure");
            assertSame(failure, assertThrows(IllegalStateException.class, () ->
                TenantHelper.dynamic("TENANT-B", () -> {
                    assertEquals("TENANT-B", TenantHelper.getDynamic());
                    throw failure;
                })));
            assertEquals("EXISTING", TenantHelper.getDynamic());
        });
    }

    private void withTenantEnabled(Runnable assertions) {
        // getProperty/getBean are inherited from SpringUtil; mock the declaring class.
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class);
             MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
             MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            try {
                assertions.run();
            } finally {
                // Remove the thread context set by this test, including on assertion failures.
                TenantHelper.clearDynamic();
            }
            redis.verify(() -> RedisUtils.deleteObject(anyString()), never());
        }
    }
}
