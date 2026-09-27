package com.nexus.common.tenant.helper;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaStorage;
import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.core.constant.GlobalConstants;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

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

    @Test
    void loggedInRunnableKeepsGlobalTenantAfterNormalExit() {
        withLoggedInGlobalTenant(() ->
            TenantHelper.dynamic("TEMP", (Runnable) () ->
                assertEquals("TEMP", TenantHelper.getDynamic())));
    }

    @Test
    void loggedInSupplierKeepsGlobalTenantAfterException() {
        withLoggedInGlobalTenant(() -> {
            IllegalStateException failure = new IllegalStateException("temporary tenant failed");
            assertSame(failure, assertThrows(IllegalStateException.class, () ->
                TenantHelper.dynamic("TEMP", () -> {
                    assertEquals("TEMP", TenantHelper.getDynamic());
                    throw failure;
                })));
        });
    }

    private void withLoggedInGlobalTenant(Runnable assertions) {
        long userId = 7L;
        String cacheKey = GlobalConstants.GLOBAL_REDIS_KEY + "dynamicTenant:" + userId;
        AtomicReference<String> redisValue = new AtomicReference<>();
        Map<String, Object> storageValues = new HashMap<>();
        SaStorage storage = mock(SaStorage.class);
        when(storage.getString(cacheKey)).thenAnswer(invocation -> (String) storageValues.get(cacheKey));
        when(storage.set(cacheKey, "GLOBAL")).thenAnswer(invocation -> {
            storageValues.put(cacheKey, "GLOBAL");
            return storage;
        });
        when(storage.delete(cacheKey)).thenAnswer(invocation -> {
            storageValues.remove(cacheKey);
            return storage;
        });

        // 模拟两个存储，不访问真实 Redis 或请求级 Sa-Token 存储。
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class);
             MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
             MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class);
             MockedStatic<SaHolder> holder = mockStatic(SaHolder.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(true);
            login.when(LoginHelper::getUserId).thenReturn(userId);
            holder.when(SaHolder::getStorage).thenReturn(storage);
            redis.when(() -> RedisUtils.setCacheObject(cacheKey, "GLOBAL")).thenAnswer(invocation -> {
                redisValue.set("GLOBAL");
                return null;
            });
            redis.when(() -> RedisUtils.getCacheObject(cacheKey)).thenAnswer(invocation -> redisValue.get());
            redis.when(() -> RedisUtils.deleteObject(cacheKey)).thenAnswer(invocation ->
                redisValue.getAndSet(null) != null);

            TenantHelper.setDynamic("GLOBAL", true);
            try {
                assertEquals("GLOBAL", redisValue.get());
                assertEquals("GLOBAL", storageValues.get(cacheKey));
                assertions.run();
                assertEquals("GLOBAL", TenantHelper.getDynamic());
                assertEquals("GLOBAL", redisValue.get());
                assertEquals("GLOBAL", storageValues.get(cacheKey));
                redis.verify(() -> RedisUtils.deleteObject(cacheKey), never());
            } finally {
                // 仅清理当前线程上下文；关闭模拟对象后丢弃请求存储。
                login.when(LoginHelper::isLogin).thenReturn(false);
                TenantHelper.clearDynamic();
                storageValues.clear();
                redisValue.set(null);
            }
        }
    }

    private void withTenantEnabled(Runnable assertions) {
        // getProperty/getBean 继承自 SpringUtil，需模拟实际声明类。
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class);
             MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class);
             MockedStatic<RedisUtils> redis = mockStatic(RedisUtils.class)) {
            spring.when(() -> SpringUtils.getProperty("tenant.enable")).thenReturn("true");
            login.when(LoginHelper::isLogin).thenReturn(false);
            try {
                assertions.run();
            } finally {
                // 清理测试设置的线程上下文，断言失败时同样执行。
                TenantHelper.clearDynamic();
            }
            redis.verify(() -> RedisUtils.deleteObject(anyString()), never());
        }
    }
}
