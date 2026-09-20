package com.nexus.mes.eq.task;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.nexus.mes.dc.task.IotDataArchiveTask;
import com.nexus.mes.eq.service.IEqOeeService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MES 定时任务本地兜底测试（MES-EQ-01 / MES-EQ-02 / IOT-MES-01）。
 *
 * <p>断言 {@code snail-job.enabled=false} 时兜底会执行既有执行体、{@code =true} 时让位不执行，
 * 抢锁失败时静默跳过；并校验 5 个 MES 任务的兜底 cron 与 {@code sj_job} 种子一致
 * （其中 {@code oeeAggregateTask}/{@code equipmentHealthTask} 的任务定义由 MES-EQ-02 补种）。</p>
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class EqSchedulerFallbackTest {

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
    void oeeFallbackExecutesAggregateWhenSnailJobDisabled() throws Exception {
        IEqOeeService oeeService = mock(IEqOeeService.class);
        when(oeeService.aggregate(any())).thenReturn(3);
        EqOeeAggregateTask task = new EqOeeAggregateTask(oeeService);
        setSnailJobEnabled(task, false);
        LockTemplate lockTemplate = mock(LockTemplate.class);
        LockInfo lockInfo = mock(LockInfo.class);
        when(lockTemplate.lock(anyString(), anyLong(), anyLong())).thenReturn(lockInfo);

        try (MockedStatic<SpringUtil> springUtils = mockStatic(SpringUtil.class)) {
            springUtils.when(() -> SpringUtil.getBean(LockTemplate.class)).thenReturn(lockTemplate);
            task.scheduledExecute();
        }

        // jobExecute 以 date=null 表示“聚合昨天”，故此处必须用 isNull()（any(Date.class) 不匹配 null）
        verify(oeeService, times(1)).aggregate(isNull());
        verify(lockTemplate, times(1)).releaseLock(lockInfo);
    }

    @Test
    void oeeFallbackSkipsAggregateWhenSnailJobEnabled() throws Exception {
        IEqOeeService oeeService = mock(IEqOeeService.class);
        EqOeeAggregateTask task = new EqOeeAggregateTask(oeeService);
        setSnailJobEnabled(task, true);
        LockTemplate lockTemplate = mock(LockTemplate.class);

        try (MockedStatic<SpringUtil> springUtils = mockStatic(SpringUtil.class)) {
            springUtils.when(() -> SpringUtil.getBean(LockTemplate.class)).thenReturn(lockTemplate);
            task.scheduledExecute();
        }

        verifyNoInteractions(oeeService);
        verifyNoInteractions(lockTemplate);
    }

    @Test
    void oeeFallbackSkipsAggregateWhenLockHeldByAnotherInstance() throws Exception {
        IEqOeeService oeeService = mock(IEqOeeService.class);
        EqOeeAggregateTask task = new EqOeeAggregateTask(oeeService);
        setSnailJobEnabled(task, false);
        LockTemplate lockTemplate = mock(LockTemplate.class);
        when(lockTemplate.lock(anyString(), anyLong(), anyLong())).thenReturn(null);

        try (MockedStatic<SpringUtil> springUtils = mockStatic(SpringUtil.class)) {
            springUtils.when(() -> SpringUtil.getBean(LockTemplate.class)).thenReturn(lockTemplate);
            task.scheduledExecute();
        }

        verifyNoInteractions(oeeService);
        verify(lockTemplate, times(0)).releaseLock(any(LockInfo.class));
    }

    @Test
    void mesTasksExposeExpectedFallbackCron() throws Exception {
        assertFallbackCron(EqOeeAggregateTask.class, "0 0 1 * * ?");
        assertFallbackCron(EquipmentHealthTask.class, "0 0 7 * * ?");
        assertFallbackCron(MaintenanceDueTask.class, "0 0 8 * * ?");
        assertFallbackCron(EquipmentStatusSyncTask.class, "7 */1 * * * ?");
        assertFallbackCron(IotDataArchiveTask.class, "0 */5 * * * ?");
    }
}
