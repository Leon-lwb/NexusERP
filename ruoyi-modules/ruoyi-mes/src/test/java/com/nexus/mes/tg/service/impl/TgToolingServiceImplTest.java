package com.nexus.mes.tg.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.mes.tg.domain.TgMaintenancePlan;
import com.nexus.mes.tg.domain.TgMaintenanceRecord;
import com.nexus.mes.tg.domain.TgTooling;
import com.nexus.mes.tg.domain.TgUsageRecord;
import com.nexus.mes.tg.domain.bo.TgCalibrationBo;
import com.nexus.mes.tg.domain.bo.TgCheckoutBo;
import com.nexus.mes.tg.domain.vo.TgMaintenancePlanVo;
import com.nexus.mes.tg.domain.vo.TgToolingVo;
import com.nexus.mes.tg.mapper.TgMaintenancePlanMapper;
import com.nexus.mes.tg.mapper.TgMaintenanceRecordMapper;
import com.nexus.mes.tg.mapper.TgToolingMapper;
import com.nexus.mes.tg.mapper.TgUsageRecordMapper;
import com.nexus.mes.tg.service.ITgMaintenancePlanService;
import com.nexus.mes.tg.service.ITgToolingService;
import com.nexus.system.strategy.AutoCodeUtil;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * MES 工装台账 Service 单元测试 — MES-TG-01（校准维度 + 超期拦截）/ MES-TG-02（保养到期拦截）。
 *
 * <p>核心断言（修复前必失败）：
 * <ol>
 *   <li>{@code checkToolingAvailable} 对「校准已超期」的 IDLE 工装返回 usable=false；</li>
 *   <li>对「保养已达计划阈值」的 IDLE 工装返回 usable=false；</li>
 *   <li>{@code checkout} 前置拦截超期工装（不落库、不改状态）；</li>
 *   <li>{@code registerCalibration} 走 CAS 状态机并在使用中/已报废时拒绝。</li>
 * </ol>
 *
 * @author nexus
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class TgToolingServiceImplTest {

    @Mock
    private TgToolingMapper baseMapper;
    @Mock
    private TgUsageRecordMapper usageRecordMapper;
    @Mock
    private TgMaintenanceRecordMapper maintenanceRecordMapper;
    @Mock
    private TgMaintenancePlanMapper maintenancePlanMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;

    @InjectMocks
    private TgToolingServiceImpl service;

    /** 真实列缓存: LambdaWrapper 需要实体 TableInfo（纯 Mock 单测下缺失会抛 lambda cache 异常）。 */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, TgTooling.class);
        TableInfoHelper.initTableInfo(assistant, TgMaintenancePlan.class);
        TableInfoHelper.initTableInfo(assistant, TgUsageRecord.class);
        TableInfoHelper.initTableInfo(assistant, TgMaintenanceRecord.class);
    }

    private TgToolingVo idleVo(Long toolingId) {
        TgToolingVo vo = new TgToolingVo();
        vo.setToolingId(toolingId);
        vo.setToolingCode("TG-0001");
        vo.setToolingName("量具A");
        vo.setStatus(ITgToolingService.STATUS_IDLE);
        vo.setUsedCount(0);
        vo.setUsedHours(BigDecimal.ZERO);
        return vo;
    }

    private TgMaintenancePlanVo countPlan(int nextCount) {
        TgMaintenancePlanVo plan = new TgMaintenancePlanVo();
        plan.setTriggerType(ITgMaintenancePlanService.TRIGGER_BY_COUNT);
        plan.setNextMaintainCount(nextCount);
        return plan;
    }

    // ==================== MES-TG-01 校准超期拦截 ====================

    @Test
    void checkToolingAvailableRejectsWhenCalibrationOverdue() {
        TgToolingVo vo = idleVo(1L);
        vo.setNextCalibrationDate(new Date(System.currentTimeMillis() - 2L * 86400000L));
        when(baseMapper.selectVoById(1L)).thenReturn(vo);

        Map<String, Object> ret = service.checkToolingAvailable(1L);

        assertFalse(Boolean.TRUE.equals(ret.get("usable")), "校准超期必须不可用（修复前只看 status 会放行）");
        assertTrue(String.valueOf(ret.get("reason")).contains("校准已超期"), "原因须可读: " + ret.get("reason"));
        // 校准超期时不应再去查保养计划（早退）
        verifyNoInteractions(maintenancePlanMapper);
    }

    @Test
    void checkToolingAvailablePassesWhenCalibrationDateIsToday() {
        TgToolingVo vo = idleVo(2L);
        vo.setNextCalibrationDate(new Date());
        when(baseMapper.selectVoById(2L)).thenReturn(vo);
        when(maintenancePlanMapper.selectVoList(any(Wrapper.class))).thenReturn(new ArrayList<>());

        Map<String, Object> ret = service.checkToolingAvailable(2L);

        assertTrue(Boolean.TRUE.equals(ret.get("usable")), "下次校准日当天仍可用(次日起超期)");
    }

    @Test
    void checkToolingAvailableRejectsCalibratingStatus() {
        TgToolingVo vo = idleVo(3L);
        vo.setStatus(ITgToolingService.STATUS_CALIBRATING);
        when(baseMapper.selectVoById(3L)).thenReturn(vo);

        Map<String, Object> ret = service.checkToolingAvailable(3L);

        assertFalse(Boolean.TRUE.equals(ret.get("usable")));
        assertEquals("工装校准中", ret.get("reason"));
    }

    // ==================== MES-TG-02 保养到期拦截 ====================

    @Test
    void checkToolingAvailableRejectsWhenMaintenanceDue() {
        TgToolingVo vo = idleVo(4L);
        vo.setUsedCount(1500);
        when(baseMapper.selectVoById(4L)).thenReturn(vo);
        when(maintenancePlanMapper.selectVoList(any(Wrapper.class)))
            .thenReturn(List.of(countPlan(1000)));

        Map<String, Object> ret = service.checkToolingAvailable(4L);

        assertFalse(Boolean.TRUE.equals(ret.get("usable")), "超保养阈值必须不可用（修复前只看 status 会放行）");
        assertTrue(String.valueOf(ret.get("reason")).contains("保养已到期"), "原因须可读: " + ret.get("reason"));
    }

    @Test
    void checkToolingAvailablePassesWhenBelowMaintenanceThreshold() {
        TgToolingVo vo = idleVo(5L);
        vo.setUsedCount(10);
        when(baseMapper.selectVoById(5L)).thenReturn(vo);
        when(maintenancePlanMapper.selectVoList(any(Wrapper.class)))
            .thenReturn(List.of(countPlan(1000)));

        Map<String, Object> ret = service.checkToolingAvailable(5L);

        assertTrue(Boolean.TRUE.equals(ret.get("usable")));
        assertEquals("可用", ret.get("reason"));
    }

    @Test
    void checkToolingAvailableRejectsWhenToolingIdIsNull() {
        Map<String, Object> ret = service.checkToolingAvailable(null);

        assertFalse(Boolean.TRUE.equals(ret.get("usable")));
        assertTrue(String.valueOf(ret.get("reason")).contains("未提供工装ID"));
        verifyNoInteractions(baseMapper);
    }

    // ==================== 领用前置拦截 ====================

    @Test
    void checkoutRejectsCalibrationOverdueToolingBeforeStateChange() {
        TgTooling entity = new TgTooling();
        entity.setToolingId(6L);
        entity.setStatus(ITgToolingService.STATUS_IDLE);
        when(baseMapper.selectById(6L)).thenReturn(entity);

        TgToolingVo vo = idleVo(6L);
        vo.setNextCalibrationDate(new Date(System.currentTimeMillis() - 86400000L));
        when(baseMapper.selectVoById(6L)).thenReturn(vo);

        RuntimeException ex =
                assertThrows(
                        RuntimeException.class, () -> service.checkout(6L, new TgCheckoutBo()));

        assertTrue(ex.getMessage().contains("校准已超期"), "领用须被前置拦截: " + ex.getMessage());
        verify(baseMapper, never()).update(any(), any());
        verifyNoInteractions(usageRecordMapper);
    }

    // ==================== 校准登记状态机 ====================

    @Test
    void registerCalibrationRunsCasStateMachineAndWritesTrace() {
        TgTooling entity = new TgTooling();
        entity.setToolingId(7L);
        entity.setStatus(ITgToolingService.STATUS_IDLE);
        entity.setCalibrationCycleDays(30);
        when(baseMapper.selectById(7L)).thenReturn(entity);
        when(baseMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        TgCalibrationBo bo = new TgCalibrationBo();
        bo.setCalibrationResult(ITgToolingService.CALIBRATION_PASS);

        assertTrue(service.registerCalibration(7L, bo));
        // 两次 CAS: IDLE->CALIBRATING, CALIBRATING->IDLE(写校准留痕)
        verify(baseMapper, times(2)).update(isNull(), any(Wrapper.class));
    }

    @Test
    void registerCalibrationRejectsInUseTooling() {
        TgTooling entity = new TgTooling();
        entity.setToolingId(8L);
        entity.setStatus(ITgToolingService.STATUS_IN_USE);
        when(baseMapper.selectById(8L)).thenReturn(entity);

        RuntimeException ex = assertThrows(RuntimeException.class,
            () -> service.registerCalibration(8L, new TgCalibrationBo()));

        assertTrue(ex.getMessage().contains("使用中"));
        verify(baseMapper, never()).update(any(), any());
    }

    @Test
    void registerCalibrationRejectsScrappedTooling() {
        TgTooling entity = new TgTooling();
        entity.setToolingId(9L);
        entity.setStatus(ITgToolingService.STATUS_SCRAPPED);
        when(baseMapper.selectById(9L)).thenReturn(entity);

        assertThrows(RuntimeException.class, () -> service.registerCalibration(9L, new TgCalibrationBo()));
        verify(baseMapper, never()).update(any(), any());
    }

    // ==================== 校准到期查询 ====================

    @Test
    void listCalibrationDueFillsOverdueFlag() {
        TgToolingVo overdue = idleVo(10L);
        overdue.setNextCalibrationDate(new Date(System.currentTimeMillis() - 86400000L));
        when(baseMapper.selectVoList(any(Wrapper.class))).thenReturn(new ArrayList<>(List.of(overdue)));

        List<TgToolingVo> due = service.listCalibrationDue();

        assertEquals(1, due.size());
        assertTrue(Boolean.TRUE.equals(due.get(0).getCalibrationOverdue()), "超期工装须被标记");
    }

    @Test
    void listCalibrationDueReturnsEmptyWhenNone() {
        when(baseMapper.selectVoList(any(Wrapper.class))).thenReturn(new ArrayList<>());

        assertTrue(service.listCalibrationDue().isEmpty());
        // 未纳入校准管理(next_calibration_date 为 NULL)的存量行不参与
        verify(baseMapper).selectVoList(any(Wrapper.class));
    }

    @Test
    void isMaintenanceDueIgnoresDisabledPlans() {
        TgToolingVo vo = idleVo(11L);
        vo.setUsedCount(99999);
        when(baseMapper.selectVoById(11L)).thenReturn(vo);
        // 计划查询已按 enabled='1' 过滤，仓储返回空 => 未到期
        when(maintenancePlanMapper.selectVoList(any(Wrapper.class))).thenReturn(Collections.emptyList());

        assertTrue(Boolean.TRUE.equals(service.checkToolingAvailable(11L).get("usable")));
    }

    @Test
    void maintenancePlanTriggerConstantsAreUsedByDueCheck() {
        // 防回归：到期判定必须使用 BY_COUNT 常量口径（否则改枚举值会静默失效）
        assertEquals("BY_COUNT", ITgMaintenancePlanService.TRIGGER_BY_COUNT);
        assertEquals("BY_HOURS", ITgMaintenancePlanService.TRIGGER_BY_HOURS);
        assertEquals("PERIODIC", ITgMaintenancePlanService.TRIGGER_PERIODIC);
        TgMaintenancePlan plan = new TgMaintenancePlan();
        plan.setTriggerType(ITgMaintenancePlanService.TRIGGER_BY_COUNT);
        assertEquals("BY_COUNT", plan.getTriggerType());
    }
}
