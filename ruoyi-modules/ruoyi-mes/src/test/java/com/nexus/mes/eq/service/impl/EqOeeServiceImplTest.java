package com.nexus.mes.eq.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.common.mapper.MesSysTenantMapper;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.domain.EqMaintenanceRecord;
import com.nexus.mes.eq.domain.EqOeeRecord;
import com.nexus.mes.eq.domain.EqRepair;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.mapper.EqMaintenanceRecordMapper;
import com.nexus.mes.eq.mapper.EqOeeRecordMapper;
import com.nexus.mes.eq.mapper.EqRepairMapper;
import com.nexus.mes.pro.mapper.ProFlowCardStepMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OEE 聚合口径单测（MES-EQ-13）。
 * <p>核心断言（修复前必失败）：历史脏数据（负停机时长）导致 run &gt; planned 时，
 * 落库的 availability / oee 必须被钳制到 1，而不是出现 &gt; 1 的"超产"值。</p>
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class EqOeeServiceImplTest {

    @Mock
    private EqOeeRecordMapper baseMapper;
    @Mock
    private EqEquipmentMapper equipmentMapper;
    @Mock
    private EqRepairMapper repairMapper;
    @Mock
    private EqMaintenanceRecordMapper maintenanceRecordMapper;
    @Mock
    private ProFlowCardStepMapper flowCardStepMapper;
    @Mock
    private MesSysTenantMapper mesSysTenantMapper;
    @InjectMocks
    private EqOeeServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqOeeRecord.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqEquipment.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqRepair.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqMaintenanceRecord.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(EqOeeRecord.class);
        TableInfoHelper.remove(EqEquipment.class);
        TableInfoHelper.remove(EqRepair.class);
        TableInfoHelper.remove(EqMaintenanceRecord.class);
    }

    @Test
    void aggregateClampsAvailabilityAndOeeToOne() {
        EqEquipment eq = new EqEquipment();
        eq.setEquipmentId(1L);
        eq.setEquipmentCode("EQ-001");
        eq.setEquipmentName("测试设备");
        eq.setPlannedMinutesPerDay(new BigDecimal("100"));
        when(equipmentMapper.selectList(any())).thenReturn(List.of(eq));

        // 历史脏数据: 负停机时长 → 停机分钟为负 → run = 100 - (-12000) = 12100 > planned 100
        EqRepair legacyNegative = new EqRepair();
        legacyNegative.setEquipmentId(1L);
        legacyNegative.setStatus("COMPLETED");
        legacyNegative.setDowntimeHours(new BigDecimal("-200"));
        when(repairMapper.selectList(any())).thenReturn(List.of(legacyNegative));
        when(repairMapper.selectCount(any())).thenReturn(0L);
        when(maintenanceRecordMapper.selectList(any())).thenReturn(List.of());
        when(baseMapper.selectOne(any())).thenReturn(null);

        int processed;
        try (MockedStatic<TenantHelper> tenant = mockStatic(TenantHelper.class)) {
            tenant.when(() -> TenantHelper.dynamic(anyString(), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
            processed = service.aggregate(new Date());
        }
        assertEquals(1, processed);

        ArgumentCaptor<EqOeeRecord> captor = ArgumentCaptor.forClass(EqOeeRecord.class);
        verify(baseMapper).insert(captor.capture());
        EqOeeRecord saved = captor.getValue();
        // 修复前: availability = 121.0000, oee = 121.0000
        assertEquals(0, BigDecimal.ONE.compareTo(saved.getAvailability()),
            "availability 应被钳制到 1, 实际 " + saved.getAvailability());
        assertEquals(0, BigDecimal.ONE.compareTo(saved.getOee()),
            "oee 应被钳制到 1, 实际 " + saved.getOee());
    }

    @Test
    void clamp01KeepsNullAndBoundsValues() {
        assertNull(EqOeeServiceImpl.clamp01(null));
        assertEquals(0, BigDecimal.ZERO.compareTo(EqOeeServiceImpl.clamp01(new BigDecimal("-0.5"))));
        assertEquals(0, BigDecimal.ONE.compareTo(EqOeeServiceImpl.clamp01(new BigDecimal("1.5"))));
        assertEquals(0, new BigDecimal("0.75").compareTo(EqOeeServiceImpl.clamp01(new BigDecimal("0.75"))));
        assertTrue(EqOeeServiceImpl.clamp01(new BigDecimal("0.9999")).compareTo(BigDecimal.ONE) <= 0);
    }
}
