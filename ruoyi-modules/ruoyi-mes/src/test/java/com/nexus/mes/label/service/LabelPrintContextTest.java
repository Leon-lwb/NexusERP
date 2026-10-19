package com.nexus.mes.label.service;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 使用真实事务验证数据源、租户及多工作线程的领取隔离。
 */
class LabelPrintContextTest extends LabelDurableTestSupport {
    @Test
    void restoresDatasourceAndTenantWhenPersistenceFails() {
        DynamicDataSourceContextHolder.push("erp");
        jdbc.execute("DROP TABLE mes_label_print_task");
        assertThrows(
                Exception.class,
                () -> service.send(record("TEST"), "FROZEN", StandardCharsets.UTF_8));
        assertEquals("erp", DynamicDataSourceContextHolder.peek());
        assertEquals("000001", TenantHelper.getTenantId());
        assertEquals(0, sends.get());
    }

    @Test
    void anotherTenantCannotReadOrClaimOriginalTask() {
        var task = store.create(recordWithPayload("TEST", "FROZEN"), "scoped", false);
        TenantHelper.dynamic(
                "000002",
                () -> {
                    assertNull(store.find(task.getTaskId()));
                    assertNull(service.findLog(task.getTaskId()));
                    assertNull(store.claim(task.getTaskId(), "other-tenant", 120));
                });
        assertEquals("000001", TenantHelper.getTenantId());
        assertEquals("PENDING", store.find(task.getTaskId()).getStatus());
    }

    @Test
    void rejectsAutomaticTaskInAnErpBoundTransaction() {
        DynamicDataSourceContextHolder.push("erp");
        assertThrows(
                Exception.class,
                () ->
                        business.executeWithoutResult(
                                tx ->
                                        service.submit(
                                                record("AUTO"), "FROZEN", StandardCharsets.UTF_8)));
        assertEquals(0, count("mes_label_print_task"));
        assertEquals("erp", DynamicDataSourceContextHolder.peek());
    }

    @Test
    void twoWorkersCanOnlySendOneCommittedTaskOnce() throws Exception {
        var task = store.create(recordWithPayload("TEST", "FROZEN"), "race", false);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> runWorker(task.getTaskId()));
            var second = pool.submit(() -> runWorker(task.getTaskId()));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, sends.get());
        assertEquals(1, store.find(task.getTaskId()).getAttemptCount());
        assertEquals("SENT", store.find(task.getTaskId()).getStatus());
    }

    private void runWorker(Long id) {
        try (MockedStatic<SpringUtil> props = mockStatic(SpringUtil.class);
                MockedStatic<LoginHelper> auth = mockStatic(LoginHelper.class)) {
            props.when(() -> SpringUtil.getProperty("tenant.enable")).thenReturn("true");
            auth.when(LoginHelper::isLogin).thenReturn(false);
            TenantHelper.dynamic("000001", () -> dispatcher.dispatch(id));
            assertNull(TenantHelper.getDynamic());
            assertNull(DynamicDataSourceContextHolder.peek());
        }
    }
}
