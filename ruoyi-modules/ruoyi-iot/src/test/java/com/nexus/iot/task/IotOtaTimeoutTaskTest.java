package com.nexus.iot.task;

import com.nexus.common.redis.utils.SchedulerFallbackUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.ota.service.IIotOtaTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * IoT OTA 升级超时兜底任务单元测试。
 * <p>
 * 覆盖：执行体跨租户扫描并把超时窗口传给 service、异常不抛出（返回 failure）、
 * {@code @Scheduled} 兜底走 {@link SchedulerFallbackUtils}（开关 + Lock4j）而非直接执行。
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class IotOtaTimeoutTaskTest {

    private IIotOtaTaskService otaTaskService;
    private IotOtaTimeoutTask task;

    private MockedStatic<TenantHelper> tenantMock;
    private MockedStatic<SchedulerFallbackUtils> fallbackMock;

    @BeforeEach
    void setUp() {
        otaTaskService = mock(IIotOtaTaskService.class);
        task = new IotOtaTimeoutTask(otaTaskService);
        tenantMock = mockStatic(TenantHelper.class);
        tenantMock.when(() -> TenantHelper.ignore(any(Supplier.class)))
            .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        fallbackMock = mockStatic(SchedulerFallbackUtils.class);
    }

    @AfterEach
    void tearDown() {
        fallbackMock.close();
        tenantMock.close();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** 执行体：以"now - 超时窗口"作为 deadline 调用 service，并返回成功结果。 */
    @Test
    void jobExecute_marksTimeoutWithDeadline() {
        when(otaTaskService.markTimeout(any(Date.class))).thenReturn(3);

        assertNotNull(task.jobExecute(null));

        ArgumentCaptor<Date> captor = ArgumentCaptor.forClass(Date.class);
        verify(otaTaskService).markTimeout(captor.capture());
        assertTrue(captor.getValue().before(new Date()), "deadline 必须是过去时间（now - 超时窗口）");
    }

    /** 执行体：service 异常被吞掉并返回 failure（不把异常抛给调度器）。 */
    @Test
    void jobExecute_serviceThrows_returnsFailure() {
        when(otaTaskService.markTimeout(any(Date.class))).thenThrow(new RuntimeException("db down"));

        assertNotNull(task.jobExecute(null));

        verify(otaTaskService).markTimeout(any(Date.class));
    }

    /** 本地兜底：把开关值与任务名交给 SchedulerFallbackUtils（由其做 Lock4j 加锁）。 */
    @Test
    void scheduledExecute_delegatesToFallbackWithSwitchAndTaskName() throws Exception {
        setField(task, "snailJobEnabled", Boolean.TRUE);
        fallbackMock.when(() -> SchedulerFallbackUtils.runIfFallbackEnabled(anyBoolean(), anyString(), anyLong(),
            any(Runnable.class))).thenReturn(false);

        task.scheduledExecute();

        fallbackMock.verify(() -> SchedulerFallbackUtils.runIfFallbackEnabled(eq(true),
            eq(IotOtaTimeoutTask.TASK_NAME), eq(300000L), any(Runnable.class)));
        verify(otaTaskService, never()).markTimeout(any(Date.class));
    }

    /** 本地兜底：开关关闭时执行体被真正调用（SnailJob 未启用的生产环境路径）。 */
    @Test
    void scheduledExecute_fallbackEnabled_runsBody() throws Exception {
        setField(task, "snailJobEnabled", Boolean.FALSE);
        when(otaTaskService.markTimeout(any(Date.class))).thenReturn(1);
        fallbackMock.when(() -> SchedulerFallbackUtils.runIfFallbackEnabled(anyBoolean(), anyString(), anyLong(),
                any(Runnable.class)))
            .thenAnswer(inv -> {
                ((Runnable) inv.getArgument(3)).run();
                return true;
            });

        task.scheduledExecute();

        verify(otaTaskService).markTimeout(any(Date.class));
    }
}
