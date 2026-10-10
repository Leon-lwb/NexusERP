package com.nexus.mes.eq.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.domain.EqRepair;
import com.nexus.mes.eq.domain.bo.EqRepairCompleteBo;
import com.nexus.mes.eq.domain.bo.EqRepairSpareBo;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.mapper.EqRepairMapper;
import com.nexus.mes.eq.service.IEqEquipmentService;
import com.nexus.mes.eq.service.IEqRepairSpareService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 维修单作废/撤销（MES-EQ-06）单测。
 * <p>
 * 核心断言（修复前必失败）：
 * <ul>
 *   <li>作废成功后必须调用 resetToNormalIfNoActiveRepair 释放设备状态，否则误报修会让设备永久 FAULT；</li>
 *   <li>作废用 CAS 限定 REPORTED/REPAIRING，影响 0 行必须抛异常且不联动设备状态。</li>
 * </ul>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class EqRepairServiceImplTest {

    @Mock
    private EqRepairMapper baseMapper;
    @Mock
    private EqEquipmentMapper equipmentMapper;
    @Mock
    private IEqEquipmentService equipmentService;
    @Mock
    private IEqRepairSpareService repairSpareService;
    @InjectMocks
    private EqRepairServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // 初始化真实 TableInfo/列缓存，让 MyBatis-Plus lambda 在纯单测中正常解析
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqRepair.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqEquipment.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(EqRepair.class);
        TableInfoHelper.remove(EqEquipment.class);
    }

    @Test
    void cancelRepairReleasesEquipmentWhenNoOtherActiveRepair() {
        EqRepair repair = new EqRepair();
        repair.setRepairId(1L);
        repair.setEquipmentId(10L);
        repair.setStatus("REPORTED");
        when(baseMapper.selectById(1L)).thenReturn(repair);
        when(baseMapper.update(isNull(), any())).thenReturn(1);
        when(equipmentService.resetToNormalIfNoActiveRepair(10L)).thenReturn(true);

        service.cancelRepair(1L, "误报修");

        // 修复前：无 cancelRepair 方法，设备永远存在活跃报修 → 永不恢复
        verify(equipmentService).resetToNormalIfNoActiveRepair(10L);
        verify(baseMapper).update(isNull(), any());
    }

    @Test
    void cancelRepairRejectsWhenCasAffectsZeroRows() {
        EqRepair repair = new EqRepair();
        repair.setRepairId(1L);
        repair.setEquipmentId(10L);
        repair.setStatus("COMPLETED");
        when(baseMapper.selectById(1L)).thenReturn(repair);
        // CAS 未命中（并发下已被完工/已作废）
        when(baseMapper.update(isNull(), any())).thenReturn(0);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.cancelRepair(1L, "误报修"));

        assertTrue(exception.getMessage().contains("仅已报修/维修中可作废"));
        // 状态未变更时不得联动设备状态
        verifyNoInteractions(equipmentService);
    }

    @Test
    void cancelRepairRejectsMissingRepair() {
        when(baseMapper.selectById(1L)).thenReturn(null);

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.cancelRepair(1L, null));

        assertTrue(exception.getMessage().contains("维修单不存在"));
        verify(equipmentService, never()).resetToNormalIfNoActiveRepair(any());
    }

    @Test
    void statusSummaryExposesCancelledCountAndKeepsActiveDefinition() {
        when(baseMapper.selectCount(any())).thenReturn(2L, 3L, 4L, 5L);

        var summary = service.statusSummary();

        assertEquals(2L, summary.get("reported"));
        assertEquals(3L, summary.get("repairing"));
        assertEquals(4L, summary.get("completed"));
        assertEquals(5L, summary.get("cancelled"));
        // 活跃报修 = 会阻止设备恢复 NORMAL 的单据，不含已作废
        assertEquals(5L, summary.get("active"));
        assertEquals(14L, summary.get("total"));
    }

    // ==================== MES-EQ-13: 负停机/负成本必须在服务端被拒 ====================

    /** 完工时的新增领用必须发生在 REPAIRING 状态，不能先完工再调用领用守卫。 */
    @Test
    void completeWithSparesConsumesWhileRepairIsStillRepairing() {
        EqRepair repair = new EqRepair();repair.setRepairId(1L);repair.setEquipmentId(10L);
        repair.setStatus("REPAIRING");
        when(baseMapper.selectById(1L)).thenReturn(repair);
        when(baseMapper.update(isNull(),any())).thenAnswer(call -> {repair.setStatus("COMPLETED");return 1;});
        doAnswer(call -> {
            if (!"REPAIRING".equals(repair.getStatus())) {
                throw new ServiceException("维修单不在维修中状态, 无法领用备件");
            }
            return List.of();
        }).when(repairSpareService).batchAdd(any());
        EqRepairSpareBo spare = new EqRepairSpareBo();spare.setSpareId(2L);spare.setQuantity(BigDecimal.ONE);
        EqRepairCompleteBo bo = new EqRepairCompleteBo();bo.setSpares(List.of(spare));
        service.completeRepair(1L,bo);
        assertEquals(1L,spare.getRepairId());
        assertEquals("COMPLETED",repair.getStatus());
        verify(repairSpareService).batchAdd(any());
    }

    @Test
    void completeRepairRejectsNegativeDowntimeHours() {
        when(baseMapper.selectById(1L)).thenReturn(repairingRepair());

        EqRepairCompleteBo bo = new EqRepairCompleteBo();
        bo.setDowntimeHours(new BigDecimal("-1"));

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.completeRepair(1L, bo));

        // 修复前：无校验，负停机直接落库 → OEE 的 run > planned → availability/oee > 1
        assertTrue(exception.getMessage().contains("停机时长不能为负"));
        verify(baseMapper, never()).update(isNull(), any());
        verifyNoInteractions(equipmentService);
    }

    @Test
    void completeRepairRejectsNegativeRepairCost() {
        when(baseMapper.selectById(1L)).thenReturn(repairingRepair());

        EqRepairCompleteBo bo = new EqRepairCompleteBo();
        bo.setRepairCost(new BigDecimal("-0.01"));

        ServiceException exception = assertThrows(ServiceException.class,
            () -> service.completeRepair(1L, bo));

        assertTrue(exception.getMessage().contains("维修成本不能为负"));
        verify(baseMapper, never()).update(isNull(), any());
    }

    @Test
    void completeRepairAllowsZeroDowntimeAndCost() {
        when(baseMapper.selectById(1L)).thenReturn(repairingRepair());
        when(baseMapper.update(isNull(), any())).thenReturn(1);
        when(equipmentService.resetToNormalIfNoActiveRepair(10L)).thenReturn(true);

        EqRepairCompleteBo bo = new EqRepairCompleteBo();
        bo.setDowntimeHours(BigDecimal.ZERO);
        bo.setRepairCost(BigDecimal.ZERO);

        service.completeRepair(1L, bo);

        // 0 合法(非负)，不得被误拒
        verify(baseMapper).update(isNull(), any());
    }

    private EqRepair repairingRepair() {
        EqRepair repair = new EqRepair();
        repair.setRepairId(1L);
        repair.setEquipmentId(10L);
        repair.setStatus("REPAIRING");
        return repair;
    }
}
