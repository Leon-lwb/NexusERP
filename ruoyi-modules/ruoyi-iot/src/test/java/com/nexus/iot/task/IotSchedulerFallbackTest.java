package com.nexus.iot.task;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.nexus.common.redis.utils.SchedulerFallbackUtils;
import com.nexus.iot.device.service.IIotDeviceDataService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * IoT 定时任务本地兜底测试（IOT-TASK-01）。
 *
 * <p>覆盖“修复前会失败、修复后会通过”的核心断言：
 * <ol>
 *   <li>{@code snail-job.enabled=false} 时兜底方法真的会执行既有执行体；</li>
 *   <li>{@code snail-job.enabled=true} 时兜底方法不执行执行体（不与 SnailJob 双跑），且不取锁；</li>
 *   <li>多实例抢锁失败（{@code LockTemplate.lock} 返回 null）时静默跳过，不重复执行；</li>
 *   <li>三个 IoT 任务的 {@code @Scheduled} cron 与 {@code sj_job} 种子一致
 *       （心跳每 5 分钟、Stream 裁剪每小时、数据清理每天 02:00）。</li>
 * </ol>
 *
 * @author nexus
 */
class IotSchedulerFallbackTest {

    private static void setSnailJobEnabled(Object target, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField("snailJobEnabled");
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static void assertFallbackCron(Class<?> taskType, String expectedCron) throws Exception {
        Method method = taskType.getMethod("scheduledExecute");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertNotNull(scheduled, taskType.getSimpleName() + " 必须提供 @Scheduled 本地兜底方法 scheduledExecute()");
        assertEquals(expectedCron, scheduled.cron(),
            taskType.getSimpleName() + " 的兜底 cron 必须与 sj_job 种子一致");
    }

    @Test
    void localFallbackEnabledIsInverseOfSnailJobEnabled() {
        assertTrue(SchedulerFallbackUtils.localFallbackEnabled(false), "snail-job.enabled=false 时本地兜底应生效");
        assertFalse(SchedulerFallbackUtils.localFallbackEnabled(true), "snail-job.enabled=true 时本地兜底应让位");
    }

    @Test
    void fallbackExecutesJobWhenSnailJobDisabled() throws Exception {
        IIotDeviceDataService dataService = mock(IIotDeviceDataService.class);
        IotDataCleanupTask task = new IotDataCleanupTask(dataService);
        setSnailJobEnabled(task, false);
        LockTemplate lockTemplate = mock(LockTemplate.class);
        LockInfo lockInfo = mock(LockInfo.class);
        when(lockTemplate.lock(anyString(), anyLong(), anyLong())).thenReturn(lockInfo);

        try (MockedStatic<SpringUtil> springUtils = mockStatic(SpringUtil.class)) {
            springUtils.when(() -> SpringUtil.getBean(LockTemplate.class)).thenReturn(lockTemplate);
            task.scheduledExecute();
        }

        verify(dataService, times(1)).deleteBefore(any(Date.class));
        verify(lockTemplate, times(1)).releaseLock(lockInfo);
    }

    @Test
    void fallbackSkipsJobWhenSnailJobEnabled() throws Exception {
        IIotDeviceDataService dataService = mock(IIotDeviceDataService.class);
        IotDataCleanupTask task = new IotDataCleanupTask(dataService);
        setSnailJobEnabled(task, true);
        LockTemplate lockTemplate = mock(LockTemplate.class);

        try (MockedStatic<SpringUtil> springUtils = mockStatic(SpringUtil.class)) {
            springUtils.when(() -> SpringUtil.getBean(LockTemplate.class)).thenReturn(lockTemplate);
            task.scheduledExecute();
        }

        verifyNoInteractions(dataService);
        verifyNoInteractions(lockTemplate);
    }

    @Test
    void fallbackSkipsJobWhenLockHeldByAnotherInstance() throws Exception {
        IIotDeviceDataService dataService = mock(IIotDeviceDataService.class);
        IotDataCleanupTask task = new IotDataCleanupTask(dataService);
        setSnailJobEnabled(task, false);
        LockTemplate lockTemplate = mock(LockTemplate.class);
        when(lockTemplate.lock(anyString(), anyLong(), anyLong())).thenReturn(null);

        try (MockedStatic<SpringUtil> springUtils = mockStatic(SpringUtil.class)) {
            springUtils.when(() -> SpringUtil.getBean(LockTemplate.class)).thenReturn(lockTemplate);
            task.scheduledExecute();
        }

        verifyNoInteractions(dataService);
        verify(lockTemplate, times(0)).releaseLock(any(LockInfo.class));
    }

    @Test
    void iotTasksExposeExpectedFallbackCron() throws Exception {
        assertFallbackCron(IotDataCleanupTask.class, "0 0 2 * * ?");
        assertFallbackCron(IotDeviceHeartbeatTask.class, "0 */5 * * * ?");
        assertFallbackCron(IotStreamTrimTask.class, "0 0 * * * ?");
    }
}
