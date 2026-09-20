package com.nexus.mes.tg.task;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.nexus.mes.common.mapper.MesSysTenantMapper;
import com.nexus.mes.tg.domain.vo.TgToolingVo;
import com.nexus.mes.tg.service.ITgToolingService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 工装校准/保养到期提醒任务 单元测试 — MES-TG-05。
 *
 * <p>覆盖：{@code @Scheduled} 本地兜底存在且 cron 与 {@code sj_job} 种子一致；
 * {@code snail-job.enabled=true} 时让位不执行；抢锁失败时静默跳过；
 * 扫描按到期分级 + Redis 限频（修复前 tg 包下无任何 Task 类，本测试必失败）。</p>
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
class TgMaintenanceDueTaskTest {

    private static final String EXPECTED_CRON = "0 30 8 * * ?";

    private static void setSnailJobEnabled(Object target, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField("snailJobEnabled");
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static TgMaintenanceDueTask newTask(ITgToolingService toolingService,
                                                MesSysTenantMapper tenantMapper) {
        return new TgMaintenanceDueTask(toolingService, tenantMapper);
    }

    @Test
    void taskExposesScheduledFallbackWithSeedCron() throws Exception {
        Method method = TgMaintenanceDueTask.class.getMethod("scheduledExecute");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertNotNull(scheduled, "MES-TG-05: 必须提供 @Scheduled 本地兜底方法 scheduledExecute()");
        assertEquals(EXPECTED_CRON, scheduled.cron(), "兜底 cron 必须与 sj_job(biz_203) 种子一致");
    }

    @Test
    void fallbackSkipsWhenSnailJobEnabled() throws Exception {
        ITgToolingService toolingService = mock(ITgToolingService.class);
        MesSysTenantMapper tenantMapper = mock(MesSysTenantMapper.class);
        TgMaintenanceDueTask task = newTask(toolingService, tenantMapper);
        setSnailJobEnabled(task, true);
        LockTemplate lockTemplate = mock(LockTemplate.class);

        try (MockedStatic<SpringUtil> springUtil = mockStatic(SpringUtil.class)) {
            springUtil.when(() -> SpringUtil.getBean(LockTemplate.class)).thenReturn(lockTemplate);
            task.scheduledExecute();
        }

        verifyNoInteractions(toolingService);
        verifyNoInteractions(tenantMapper);
        verifyNoInteractions(lockTemplate);
    }

    @Test
    void fallbackSkipsWhenLockHeldByAnotherInstance() throws Exception {
        ITgToolingService toolingService = mock(ITgToolingService.class);
        MesSysTenantMapper tenantMapper = mock(MesSysTenantMapper.class);
        TgMaintenanceDueTask task = newTask(toolingService, tenantMapper);
        setSnailJobEnabled(task, false);
        LockTemplate lockTemplate = mock(LockTemplate.class);
        when(lockTemplate.lock(anyString(), anyLong(), anyLong())).thenReturn(null);

        try (MockedStatic<SpringUtil> springUtil = mockStatic(SpringUtil.class)) {
            springUtil.when(() -> SpringUtil.getBean(LockTemplate.class)).thenReturn(lockTemplate);
            task.scheduledExecute();
        }

        verifyNoInteractions(toolingService);
        verify(lockTemplate, times(0)).releaseLock(any(LockInfo.class));
    }

    @Test
    void scanAndNotifyCountsDueToolingsAndThrottles() {
        ITgToolingService toolingService = mock(ITgToolingService.class);
        MesSysTenantMapper tenantMapper = mock(MesSysTenantMapper.class);
        TgMaintenanceDueTask task = spy(newTask(toolingService, tenantMapper));

        TgToolingVo calibrationDue = new TgToolingVo();
        calibrationDue.setToolingId(1L);
        calibrationDue.setToolingCode("TG-1");
        calibrationDue.setToolingName("量具A");
        calibrationDue.setNextCalibrationDate(new Date(System.currentTimeMillis() - 9L * 86400000L));
        TgToolingVo maintenanceDue = new TgToolingVo();
        maintenanceDue.setToolingId(2L);
        maintenanceDue.setToolingCode("TG-2");
        maintenanceDue.setToolingName("夹具B");

        when(toolingService.listCalibrationDue()).thenReturn(List.of(calibrationDue));
        when(toolingService.listMaintenanceDue()).thenReturn(List.of(maintenanceDue));

        // 替换两个 I/O 边界（Redis 限频 / SSE 推送）——RedisUtils 是带静态初始化的工具类，
        // mockStatic 会因类初始化失败而报 "Cannot instrument class"，故改用接缝方法
        doReturn(true).when(task).tryThrottle(anyString(), anyString(), any(Duration.class));
        doNothing().when(task).publishSse(anyString(), any(TgToolingVo.class), anyString(), anyLong(), anyString());

        Integer processed = task.scanAndNotify();

        assertEquals(2, processed, "校准到期 + 保养到期各计一条");
        verify(task).tryThrottle(eq("mes:tg:calibration:due:rl:1"), eq("P2"), any(Duration.class));
        verify(task).tryThrottle(eq("mes:tg:maintenance:due:rl:2"), eq("P3"), any(Duration.class));
        verify(task, times(2)).publishSse(anyString(), any(TgToolingVo.class), anyString(), anyLong(), anyString());
    }

    @Test
    void scanAndNotifySkipsThrottledToolings() {
        ITgToolingService toolingService = mock(ITgToolingService.class);
        MesSysTenantMapper tenantMapper = mock(MesSysTenantMapper.class);
        TgMaintenanceDueTask task = spy(newTask(toolingService, tenantMapper));
        TgToolingVo vo = new TgToolingVo();
        vo.setToolingId(3L);
        when(toolingService.listCalibrationDue()).thenReturn(List.of(vo));
        when(toolingService.listMaintenanceDue()).thenReturn(List.of());
        doReturn(false).when(task).tryThrottle(anyString(), anyString(), any(Duration.class));

        assertEquals(0, task.scanAndNotify(), "限频窗口内不重复提醒");
        verify(task, never()).publishSse(anyString(), any(TgToolingVo.class), anyString(), anyLong(), anyString());
    }

    @Test
    void scanAndNotifyToleratesNullLists() {
        ITgToolingService toolingService = mock(ITgToolingService.class);
        MesSysTenantMapper tenantMapper = mock(MesSysTenantMapper.class);
        TgMaintenanceDueTask task = newTask(toolingService, tenantMapper);
        when(toolingService.listCalibrationDue()).thenReturn(null);
        when(toolingService.listMaintenanceDue()).thenReturn(null);

        assertEquals(0, task.scanAndNotify());
    }

    @Test
    void overdueLevelEscalatesWithDays() {
        assertEquals("P3", TgMaintenanceDueTask.overdueLevel(0));
        assertEquals("P3", TgMaintenanceDueTask.overdueLevel(7));
        assertEquals("P2", TgMaintenanceDueTask.overdueLevel(8));
        assertEquals("P2", TgMaintenanceDueTask.overdueLevel(30));
        assertEquals("P1", TgMaintenanceDueTask.overdueLevel(31));
    }

    @Test
    void throttleTtlShrinksAsLevelEscalates() {
        assertEquals(Duration.ofHours(24), TgMaintenanceDueTask.throttleTtl(1));
        assertEquals(Duration.ofHours(12), TgMaintenanceDueTask.throttleTtl(10));
        assertEquals(Duration.ofHours(6), TgMaintenanceDueTask.throttleTtl(40));
    }

    @Test
    void overdueDaysHandlesNullAndFuture() {
        assertEquals(0L, TgMaintenanceDueTask.overdueDays(null));
        assertEquals(0L, TgMaintenanceDueTask.overdueDays(new Date(System.currentTimeMillis() + 86400000L)));
        assertEquals(2L, TgMaintenanceDueTask.overdueDays(new Date(System.currentTimeMillis() - 2L * 86400000L)));
    }
}
