package com.nexus.mes.eq.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.iot.service.IotDeviceDataQueryService;
import com.nexus.mes.eq.domain.EqEquipment;
import com.nexus.mes.eq.domain.EqHealthRecord;
import com.nexus.mes.eq.domain.EqRepair;
import com.nexus.mes.eq.mapper.EqEquipmentMapper;
import com.nexus.mes.eq.mapper.EqHealthRecordMapper;
import com.nexus.mes.eq.mapper.EqRepairMapper;
import com.nexus.mes.pro.service.IProAndonService;
import com.nexus.mes.testsupport.SpringStaticBootstrap;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 设备健康评分（预测性维护）单测。
 * <ul>
 *   <li>MES-EQ-15: 同设备同日重评先 CAS 更新, 影响 0 行才插入（消除同设备同日多条）;</li>
 *   <li>MES-EQ-16: LOW 级安灯 24h 内同设备只推一次（限频键 mes:eq:health:andon:{tenant}:{eq}）;</li>
 *   <li>MES-EQ-17: 向下突变（骤降）与向上突变同等计为异常点。</li>
 * </ul>
 * 限频获取已抽为可覆盖的 {@code tryAcquireAndonThrottle}，因此无需静态打桩 {@code RedisUtils}；
 * Environment 用 {@link StandardEnvironment}（无配置 → 走 EwmaUtil 默认 λ/预热/z 阈值）。
 */
@Tag("local")
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class EqHealthServiceImplTest {

    @Mock
    private EqEquipmentMapper equipmentMapper;
    @Mock
    private EqHealthRecordMapper healthRecordMapper;
    @Mock
    private EqRepairMapper repairMapper;
    @Mock
    private IotDeviceDataQueryService iotDeviceDataQueryService;
    @Mock
    private IProAndonService andonService;

    private EqHealthServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // 必须在本类首次触碰 EqHealthServiceImpl(内部调用 JsonUtils.toJsonString) 之前完成静态预热:
        // JsonUtils/MapstructUtils 的静态初始化依赖 Spring 容器, 纯 Mockito 环境下直接触碰会让
        // <clinit> 抛 ExceptionInInitializerError, 且失败的类在本 JVM 内永久不可恢复 ——
        // 后续同 JVM 的测试类会连锁报 NoClassDefFoundError。
        SpringStaticBootstrap.ensureActive();
        // 初始化真实 TableInfo/列缓存, 让 MyBatis-Plus lambda 在纯单测中正常解析
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqHealthRecord.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqEquipment.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EqRepair.class);
    }

    @AfterAll
    static void clearTableInfo() {
        TableInfoHelper.remove(EqHealthRecord.class);
        TableInfoHelper.remove(EqEquipment.class);
        TableInfoHelper.remove(EqRepair.class);
    }

    @BeforeEach
    void setUp() {
        service = spy(new EqHealthServiceImpl(equipmentMapper, healthRecordMapper, repairMapper,
            iotDeviceDataQueryService, andonService, new StandardEnvironment()));
    }

    // ==================== MES-EQ-16: 限频键 ====================

    @Test
    void andonThrottleKeyFollowsTenantEquipmentPattern() {
        assertEquals("mes:eq:health:andon:000000:10", EqHealthServiceImpl.andonThrottleKey("000000", 10L));
        assertEquals("mes:eq:health:andon::10", EqHealthServiceImpl.andonThrottleKey(null, 10L));
    }

    // ==================== MES-EQ-15: 同日重评不新增 ====================

    @Test
    void evaluateOneUpdatesInPlaceWhenSameDayRecordExists() {
        stubLowGrade();
        // CAS 命中(当日已有记录) → 影响 1 行
        when(healthRecordMapper.update(any(), any())).thenReturn(1);
        doReturn(false).when(service).tryAcquireAndonThrottle(any(), any());

        service.evaluateOne(iotEquipment());

        verify(healthRecordMapper).update(any(), any());
        verify(healthRecordMapper, never()).insert(any(EqHealthRecord.class));
    }

    @Test
    void evaluateOneInsertsWhenNoSameDayRecord() {
        stubLowGrade();
        // CAS 未命中(当日无记录) → 影响 0 行, 才插入
        when(healthRecordMapper.update(any(), any())).thenReturn(0);
        // 安灯分支是否走到取决于最终评分等级, 用 lenient 允许该桩未被使用
        // (若被使用则必须走桩, 避免 spy 调用真实 RedisUtils)
        lenient().doReturn(false).when(service).tryAcquireAndonThrottle(any(), any());

        service.evaluateOne(iotEquipment());

        verify(healthRecordMapper).insert(any(EqHealthRecord.class));
    }

    // ==================== MES-EQ-16: 安灯限频 ====================

    @Test
    void evaluateOneSkipsAndonWhenThrottled() {
        stubLowGrade();
        when(healthRecordMapper.update(any(), any())).thenReturn(1);
        // 24h 内已推送过 → 未取得许可
        doReturn(false).when(service).tryAcquireAndonThrottle(any(), any());

        service.evaluateOne(iotEquipment());

        verifyNoInteractions(andonService);
    }

    @Test
    void evaluateOneTriggersAndonWhenThrottleAcquired() {
        stubLowGrade();
        when(healthRecordMapper.update(any(), any())).thenReturn(1);
        doReturn(true).when(service).tryAcquireAndonThrottle(any(), any());

        service.evaluateOne(iotEquipment());

        verify(andonService).callAndon(any(), any(), any(), eq(IProAndonService.TYPE_EQUIPMENT), anyString());
    }

    // ==================== MES-EQ-17: 向下突变也告警 ====================

    @Test
    void evaluateOneCountsDownwardShiftAsAnomaly() {
        when(iotDeviceDataQueryService.getHistoryData(any(), any(), any())).thenReturn(downwardSeries());
        when(repairMapper.selectCount(any())).thenReturn(0L, 0L);
        when(healthRecordMapper.selectOne(any())).thenReturn(null);
        when(healthRecordMapper.update(any(), any())).thenReturn(0);
        // 安灯分支是否走到取决于最终评分等级, 用 lenient 允许该桩未被使用
        // (若被使用则必须走桩, 避免 spy 调用真实 RedisUtils)
        lenient().doReturn(false).when(service).tryAcquireAndonThrottle(any(), any());

        service.evaluateOne(iotEquipment());

        ArgumentCaptor<EqHealthRecord> captor = ArgumentCaptor.forClass(EqHealthRecord.class);
        verify(healthRecordMapper).insert(captor.capture());
        Integer anomalies = captor.getValue().getAnomalyPoints();
        assertTrue(anomalies != null && anomalies > 0,
            "向下突变(骤降)也应计为异常点, 实际 anomalyPoints=" + anomalies);
    }

    // ==================== 测试辅助 ====================

    private EqEquipment iotEquipment() {
        EqEquipment eq = new EqEquipment();
        eq.setEquipmentId(10L);
        eq.setEquipmentCode("EQ-001");
        eq.setEquipmentName("测试设备");
        eq.setIotDeviceId(100L);
        return eq;
    }

    /** LOW 级场景: 无 IoT 数据 + 未闭环维修 5 笔(扣 50 分) → score=50(LOW), 前次评分为空 */
    private void stubLowGrade() {
        when(iotDeviceDataQueryService.getHistoryData(any(), any(), any())).thenReturn(List.of());
        when(repairMapper.selectCount(any())).thenReturn(0L, 5L);
        when(healthRecordMapper.selectOne(any())).thenReturn(null);
    }

    /** 前 40 点稳定在 100, 后 20 点骤降到 10（纯向下突变） */
    private List<Map<String, Object>> downwardSeries() {
        List<Map<String, Object>> series = new ArrayList<>();
        long base = System.currentTimeMillis() - 3_600_000L;
        for (int i = 0; i < 60; i++) {
            Map<String, Object> row = new HashMap<>(4);
            row.put("propertyCode", "TEMP");
            row.put("propertyName", "温度");
            row.put("numericValue", i < 40 ? new BigDecimal("100") : new BigDecimal("10"));
            row.put("collectTime", new Date(base + i * 1000L));
            series.add(row);
        }
        return series;
    }
}
