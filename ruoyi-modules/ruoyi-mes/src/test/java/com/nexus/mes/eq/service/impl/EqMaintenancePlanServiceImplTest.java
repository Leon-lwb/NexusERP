package com.nexus.mes.eq.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.mes.eq.domain.EqMaintenancePlan;
import com.nexus.mes.eq.domain.EqMaintenanceRecord;
import com.nexus.mes.eq.domain.bo.EqMaintenancePlanBo;
import com.nexus.mes.eq.domain.bo.EqMaintenanceRecordBo;
import com.nexus.mes.eq.domain.vo.EqEquipmentVo;
import com.nexus.mes.eq.domain.vo.EqMaintenancePlanVo;
import com.nexus.mes.eq.domain.vo.EqMaintenanceRecordVo;
import com.nexus.mes.eq.mapper.EqMaintenancePlanMapper;
import com.nexus.mes.eq.mapper.EqMaintenanceRecordMapper;
import com.nexus.mes.eq.service.IEqEquipmentService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class EqMaintenancePlanServiceImplTest {

    @Mock
    private EqMaintenancePlanMapper baseMapper;
    @Mock
    private EqMaintenanceRecordMapper maintenanceRecordMapper;
    @Mock
    private IEqEquipmentService equipmentService;
    @InjectMocks
    private EqMaintenancePlanServiceImpl service;

    @Captor
    private ArgumentCaptor<EqMaintenancePlan> planCaptor;
    @Captor
    private ArgumentCaptor<EqMaintenanceRecord> recordCaptor;

    private MockedStatic<LoginHelper> loginHelperMock;

    @BeforeAll
    static void initTableInfo() {
        // 初始化真实 TableInfo/列缓存, 让 MyBatis-Plus lambda 在纯单测中正常解析
        // (旧做法 mockStatic(LambdaUtils) 未 stub extract() 会返回 null meta, 服务新增 lambda 调用即 NPE)
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqMaintenancePlan.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqMaintenanceRecord.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(EqMaintenancePlan.class);
        TableInfoHelper.remove(EqMaintenanceRecord.class);
    }

    @BeforeEach
    void setUp() {
        loginHelperMock = mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(1L);
        loginHelperMock.when(LoginHelper::getUsername).thenReturn("tester");
    }

    @AfterEach
    void tearDown() {
        if (loginHelperMock != null) loginHelperMock.close();
    }

    // ==================== 下次保养日期计算 ====================

    @Test
    void insertByBoCalculatesNextDateFromCycleDays() {
        when(baseMapper.selectVoById(any())).thenReturn(new EqMaintenancePlanVo());

        EqMaintenancePlanBo bo = new EqMaintenancePlanBo();
        bo.setPlanName("月度保养");
        bo.setEquipmentId(1L);
        bo.setMaintenanceType("MONTHLY");
        bo.setCycleDays(30);
        bo.setContent("测试内容");
        bo.setResponsibleId(1L);
        bo.setResponsibleName("张三");

        service.insertByBo(bo);

        verify(baseMapper).insert(planCaptor.capture());
        EqMaintenancePlan captured = planCaptor.getValue();
        assertEquals("MONTHLY", captured.getMaintenanceType());
        assertEquals(30, captured.getCycleDays());
        assertNotNull(captured.getNextDate());
        assertEquals("ACTIVE", captured.getStatus());
    }

    @Test
    void insertByBoDefaultsCycleDaysFromMaintenanceType() {
        when(baseMapper.selectVoById(any())).thenReturn(new EqMaintenancePlanVo());

        // DAILY=1, WEEKLY=7, MONTHLY=30, QUARTERLY=90, YEARLY=365
        assertDefaultCycleDays("DAILY", 1);
        assertDefaultCycleDays("WEEKLY", 7);
        assertDefaultCycleDays("MONTHLY", 30);
        assertDefaultCycleDays("QUARTERLY", 90);
        assertDefaultCycleDays("YEARLY", 365);
    }

    private void assertDefaultCycleDays(String type, int expectedDays) {
        EqMaintenancePlanBo bo = new EqMaintenancePlanBo();
        bo.setPlanName("测试");
        bo.setEquipmentId(1L);
        bo.setMaintenanceType(type);
        bo.setCycleDays(null);  // 空时按类型默认

        service.insertByBo(bo);

        verify(baseMapper, atLeastOnce()).insert(planCaptor.capture());
        EqMaintenancePlan captured = planCaptor.getValue();
        assertEquals(expectedDays, captured.getCycleDays());
    }

    @Test
    void insertByBoUsesProvidedNextDateWhenSet() {
        when(baseMapper.selectVoById(any())).thenReturn(new EqMaintenancePlanVo());

        Date futureDate = java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(60));
        EqMaintenancePlanBo bo = new EqMaintenancePlanBo();
        bo.setPlanName("测试");
        bo.setEquipmentId(1L);
        bo.setMaintenanceType("MONTHLY");
        bo.setNextDate(futureDate);

        service.insertByBo(bo);

        verify(baseMapper).insert(planCaptor.capture());
        EqMaintenancePlan captured = planCaptor.getValue();
        assertEquals(futureDate, captured.getNextDate());
    }

    @Test
    void insertByBoDefaultsStatusToActive() {
        when(baseMapper.selectVoById(any())).thenReturn(new EqMaintenancePlanVo());

        EqMaintenancePlanBo bo = new EqMaintenancePlanBo();
        bo.setPlanName("测试");
        bo.setEquipmentId(1L);
        bo.setMaintenanceType("DAILY");

        service.insertByBo(bo);

        verify(baseMapper).insert(planCaptor.capture());
        assertEquals("ACTIVE", planCaptor.getValue().getStatus());
    }

    @Test
    void insertByBoReturnsNullCycleDaysForUnknownType() {
        when(baseMapper.selectVoById(any())).thenReturn(new EqMaintenancePlanVo());

        EqMaintenancePlanBo bo = new EqMaintenancePlanBo();
        bo.setPlanName("测试");
        bo.setEquipmentId(1L);
        bo.setMaintenanceType("UNKNOWN");

        service.insertByBo(bo);

        verify(baseMapper).insert(planCaptor.capture());
        assertNull(planCaptor.getValue().getCycleDays());
        assertNull(planCaptor.getValue().getNextDate());
    }

    // ==================== updateByBo ====================

    @Test
    void updateByBoRejectsNullPlanId() {
        EqMaintenancePlanBo bo = new EqMaintenancePlanBo();

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.updateByBo(bo));
        assertTrue(exception.getMessage().contains("计划ID不能为空"));
    }

    @Test
    void updateByBoRejectsNonexistentPlan() {
        when(baseMapper.selectById(1L)).thenReturn(null);

        EqMaintenancePlanBo bo = new EqMaintenancePlanBo();
        bo.setPlanId(1L);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.updateByBo(bo));
        assertTrue(exception.getMessage().contains("保养计划不存在"));
    }

    @Test
    void updateByBoRecalculatesNextDateWithNewCycleDays() {
        EqMaintenancePlan existing = new EqMaintenancePlan();
        existing.setPlanId(1L);
        existing.setMaintenanceType("MONTHLY");
        existing.setCycleDays(30);
        when(baseMapper.selectById(1L)).thenReturn(existing);
        when(baseMapper.selectVoById(1L)).thenReturn(new EqMaintenancePlanVo());

        EqMaintenancePlanBo bo = new EqMaintenancePlanBo();
        bo.setPlanId(1L);
        bo.setPlanName("更新测试");
        bo.setEquipmentId(1L);
        bo.setMaintenanceType("WEEKLY");
        bo.setCycleDays(null);  // 触发默认7天

        service.updateByBo(bo);

        verify(baseMapper).updateById(planCaptor.capture());
        EqMaintenancePlan updated = planCaptor.getValue();
        assertEquals(7, updated.getCycleDays());
        assertNotNull(updated.getNextDate());
    }

    // ==================== completePlan ====================

    @Test
    void completePlanRejectsNonexistentPlan() {
        when(baseMapper.selectById(1L)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.completePlan(1L, new EqMaintenanceRecordBo()));
        assertTrue(exception.getMessage().contains("不存在"));
    }

    /**
     * MES-EQ-07 核心断言：CAS 影响 0 行（并发/重复提交）时必须抛异常，且**不得**写入第二条保养记录。
     * 修复前 completePlan 是读-改-写：两个线程都通过 ACTIVE 校验 → 两条 eq_maintenance_record
     * 且 next_date 被推进两次。
     */
    @Test
    void completePlanRejectsWhenCasAffectsZeroRowsAndWritesNoRecord() {
        EqMaintenancePlan plan = new EqMaintenancePlan();
        plan.setPlanId(1L);
        plan.setEquipmentId(10L);
        plan.setMaintenanceType("MONTHLY");
        plan.setCycleDays(30);
        plan.setStatus("ACTIVE");
        when(baseMapper.selectById(1L)).thenReturn(plan);
        // 条件更新 next_date + status='ACTIVE' 未命中 → 说明已被其他请求完成
        when(baseMapper.update(isNull(), any())).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.completePlan(1L, new EqMaintenanceRecordBo()));

        assertTrue(exception.getMessage().contains("状态已变更"));
        // 关键：CAS 失败后一条保养记录都不能写
        verify(maintenanceRecordMapper, never()).insert(any(EqMaintenanceRecord.class));
        verify(equipmentService, never()).updateStatus(any(), any());
    }

    @Test
    void completePlanRejectsNonActivePlan() {
        EqMaintenancePlan plan = new EqMaintenancePlan();
        plan.setPlanId(1L);
        plan.setStatus("INACTIVE");
        when(baseMapper.selectById(1L)).thenReturn(plan);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.completePlan(1L, new EqMaintenanceRecordBo()));
        assertTrue(exception.getMessage().contains("仅启用状态"));
    }

    @Test
    void completePlanCreatesRecordAndAdvancesNextDate() {
        EqMaintenancePlan plan = new EqMaintenancePlan();
        plan.setPlanId(1L);
        plan.setEquipmentId(10L);
        plan.setMaintenanceType("MONTHLY");
        plan.setCycleDays(30);
        plan.setStatus("ACTIVE");
        when(baseMapper.selectById(1L)).thenReturn(plan);
        when(baseMapper.update(isNull(), any())).thenReturn(1);
        when(maintenanceRecordMapper.insert(any(EqMaintenanceRecord.class))).thenReturn(1);
        EqMaintenanceRecordVo recordVo = new EqMaintenanceRecordVo();
        recordVo.setRecordId(100L);
        recordVo.setEquipmentId(10L);
        when(maintenanceRecordMapper.selectVoById(any())).thenReturn(recordVo);
        EqEquipmentVo equipmentVo = new EqEquipmentVo();
        equipmentVo.setEquipmentCode("EQ-001");
        equipmentVo.setEquipmentName("测试设备");
        when(equipmentService.selectById(10L)).thenReturn(equipmentVo);

        EqMaintenanceRecordBo bo = new EqMaintenanceRecordBo();
        bo.setContent("完成保养");
        bo.setResult("NORMAL");

        EqMaintenanceRecordVo result = service.completePlan(1L, bo);

        // Verify record created
        verify(maintenanceRecordMapper).insert(recordCaptor.capture());
        EqMaintenanceRecord record = recordCaptor.getValue();
        assertEquals(1L, record.getPlanId());
        assertEquals(10L, record.getEquipmentId());
        assertEquals("tester", record.getExecutorName());
        assertEquals("NORMAL", record.getResult());
        assertEquals("完成保养", record.getContent());
        assertNotNull(record.getExecuteTime());

        // Verify nextDate advanced (CAS: next_date + status='ACTIVE' 同一条条件更新)
        verify(baseMapper).update(isNull(), any());

        // Verify result
        assertNotNull(result);
        assertEquals("EQ-001", result.getEquipmentCode());
        assertEquals("测试设备", result.getEquipmentName());
    }

    @Test
    void completePlanDefaultsCycleDaysWhenNull() {
        EqMaintenancePlan plan = new EqMaintenancePlan();
        plan.setPlanId(1L);
        plan.setEquipmentId(10L);
        plan.setMaintenanceType("MONTHLY");
        plan.setCycleDays(null);
        plan.setStatus("ACTIVE");
        when(baseMapper.selectById(1L)).thenReturn(plan);
        when(baseMapper.update(isNull(), any())).thenReturn(1);
        when(maintenanceRecordMapper.insert(any(EqMaintenanceRecord.class))).thenReturn(1);
        EqMaintenanceRecordVo recordVo = new EqMaintenanceRecordVo();
        recordVo.setRecordId(100L);
        recordVo.setEquipmentId(10L);
        when(maintenanceRecordMapper.selectVoById(any())).thenReturn(recordVo);

        EqMaintenanceRecordBo bo = new EqMaintenanceRecordBo();

        service.completePlan(1L, bo);

        // 30 days default for MONTHLY
        verify(baseMapper).update(isNull(), any());
    }

    @Test
    void completePlanWithAbnormalResultSetsEquipmentMaintenance() {
        EqMaintenancePlan plan = new EqMaintenancePlan();
        plan.setPlanId(1L);
        plan.setEquipmentId(10L);
        plan.setMaintenanceType("MONTHLY");
        plan.setCycleDays(30);
        plan.setStatus("ACTIVE");
        when(baseMapper.selectById(1L)).thenReturn(plan);
        when(baseMapper.update(isNull(), any())).thenReturn(1);
        when(maintenanceRecordMapper.insert(any(EqMaintenanceRecord.class))).thenReturn(1);
        EqMaintenanceRecordVo recordVo = new EqMaintenanceRecordVo();
        recordVo.setRecordId(100L);
        recordVo.setEquipmentId(10L);
        when(maintenanceRecordMapper.selectVoById(any())).thenReturn(recordVo);

        EqMaintenanceRecordBo bo = new EqMaintenanceRecordBo();
        bo.setResult("ABNORMAL");

        service.completePlan(1L, bo);

        verify(equipmentService).updateStatus(10L, "MAINTENANCE");
        // 异常结果不得结束保养
        verify(equipmentService, never()).finishMaintenance(any(), any());
    }

    /**
     * MES-EQ-05 核心断言：保养完成且结果正常时，若设备因上次保养异常处于 MAINTENANCE，
     * 必须走 finishMaintenance 收口（此前没有任何清除点 → 永久卡"保养中"）。
     */
    @Test
    void completePlanWithNormalResultReleasesMaintenance() {
        EqMaintenancePlan plan = new EqMaintenancePlan();
        plan.setPlanId(1L);
        plan.setEquipmentId(10L);
        plan.setMaintenanceType("MONTHLY");
        plan.setCycleDays(30);
        plan.setStatus("ACTIVE");
        when(baseMapper.selectById(1L)).thenReturn(plan);
        when(baseMapper.update(isNull(), any())).thenReturn(1);
        when(maintenanceRecordMapper.insert(any(EqMaintenanceRecord.class))).thenReturn(1);
        EqMaintenanceRecordVo recordVo = new EqMaintenanceRecordVo();
        recordVo.setRecordId(100L);
        recordVo.setEquipmentId(10L);
        when(maintenanceRecordMapper.selectVoById(any())).thenReturn(recordVo);

        EqMaintenanceRecordBo bo = new EqMaintenanceRecordBo();
        bo.setResult("NORMAL");

        service.completePlan(1L, bo);

        verify(equipmentService).finishMaintenance(10L, "保养完成且结果正常");
    }

    @Test
    void completePlanDefaultsResultToNormal() {
        EqMaintenancePlan plan = new EqMaintenancePlan();
        plan.setPlanId(1L);
        plan.setEquipmentId(10L);
        plan.setMaintenanceType("MONTHLY");
        plan.setCycleDays(30);
        plan.setStatus("ACTIVE");
        when(baseMapper.selectById(1L)).thenReturn(plan);
        when(baseMapper.update(isNull(), any())).thenReturn(1);
        when(maintenanceRecordMapper.insert(any(EqMaintenanceRecord.class))).thenReturn(1);
        EqMaintenanceRecordVo recordVo = new EqMaintenanceRecordVo();
        recordVo.setRecordId(100L);
        recordVo.setEquipmentId(10L);
        when(maintenanceRecordMapper.selectVoById(any())).thenReturn(recordVo);

        service.completePlan(1L, null);

        verify(maintenanceRecordMapper).insert(recordCaptor.capture());
        assertEquals("NORMAL", recordCaptor.getValue().getResult());
    }

    // ==================== deleteByIds ====================

    @Test
    void deleteByIdsReturnsFalseForNull() {
        Boolean result = service.deleteByIds(null);
        assertFalse(result);
    }

    @Test
    void deleteByIdsReturnsFalseForEmpty() {
        Boolean result = service.deleteByIds(new Long[]{});
        assertFalse(result);
    }

    @Test
    void deleteByIdsDeletesSuccessfully() {
        when(baseMapper.deleteByIds(anyList())).thenReturn(1);

        Boolean result = service.deleteByIds(new Long[]{1L});

        assertTrue(result);
    }
}