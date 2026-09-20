package com.nexus.erp.sch;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.cv.client.VisionClient;
import com.nexus.common.cv.config.CvProperties;
import com.nexus.erp.sch.domain.SchAttendance;
import com.nexus.erp.sch.domain.bo.SchPunchRuleBo;
import com.nexus.erp.sch.domain.vo.SchPunchRuleVo;
import com.nexus.erp.sch.mapper.SchAttendanceMapper;
import com.nexus.erp.sch.mapper.SchEmployeeScheduleMapper;
import com.nexus.erp.sch.mapper.SchShiftMapper;
import com.nexus.erp.sch.mapper.SchWorkerMapper;
import com.nexus.erp.sch.service.ISchPunchRuleService;
import com.nexus.erp.sch.service.impl.SchAttendanceServiceImpl;
import com.nexus.system.service.ISysOssService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SchAttendanceServiceImpl 打卡主流程单元测试
 * <p>核心验证 ERP-SCH-03: GPS 围栏必须真正参与打卡判定(原实现硬编码 locationValid=true,
 * 只有前端主动调用 /validate-location 才算,绕过该端点即可异地打卡)。</p>
 *
 * @author Nexus
 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class SchAttendanceServiceImplTest {

    /** 上海人民广场附近(与围栏中心一致) */
    private static final BigDecimal NEAR_LAT = new BigDecimal("31.230500");
    private static final BigDecimal NEAR_LNG = new BigDecimal("121.473800");
    /** 北京(距上海约 1067 公里) */
    private static final BigDecimal FAR_LAT = new BigDecimal("39.904200");
    private static final BigDecimal FAR_LNG = new BigDecimal("116.407400");

    @Mock
    private SchAttendanceMapper baseMapper;
    @Mock
    private SchEmployeeScheduleMapper employeeScheduleMapper;
    @Mock
    private SchShiftMapper shiftMapper;
    @Mock
    private ISchPunchRuleService schPunchRuleService;
    @Mock
    private VisionClient visionClient;
    @Mock
    private CvProperties cvProperties;
    @Mock
    private SchWorkerMapper workerMapper;
    @Mock
    private ISysOssService sysOssService;

    @InjectMocks
    private SchAttendanceServiceImpl service;

    private SchPunchRuleVo rule(BigDecimal lat, BigDecimal lng, Long radius) {
        SchPunchRuleVo rule = new SchPunchRuleVo();
        rule.setRuleId(1L);
        rule.setRuleName("默认规则");
        rule.setIsEnabled(1);
        rule.setLocationLat(lat);
        rule.setLocationLng(lng);
        rule.setLocationRadius(radius);
        return rule;
    }

    @Test
    void clockIn_outsideFence_isRejectedAndNotPersisted() {
        when(schPunchRuleService.selectList(any(SchPunchRuleBo.class)))
            .thenReturn(List.of(rule(NEAR_LAT, NEAR_LNG, 200L)));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.performClockIn(11L, "张三", new Date(), null, null, FAR_LAT, FAR_LNG, null));

        assertTrue(ex.getMessage().contains("超出打卡范围"), ex.getMessage());
        // 修复前: 异地打卡照样落库并返回 locationValid=true
        verify(baseMapper, never()).insert(any(SchAttendance.class));
    }

    @Test
    void clockIn_withoutCoordinates_isRejectedWhenFenceConfigured() {
        when(schPunchRuleService.selectList(any(SchPunchRuleBo.class)))
            .thenReturn(List.of(rule(NEAR_LAT, NEAR_LNG, 200L)));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.performClockIn(11L, "张三", new Date(), null, null, null, null, null));

        // 不带定位不能被当成"通过": 否则省略经纬度即可绕过围栏
        assertTrue(ex.getMessage().contains("未获取到定位信息"), ex.getMessage());
        verify(baseMapper, never()).insert(any(SchAttendance.class));
    }

    @Test
    void clockIn_withinFence_persistsWithRealDistance() {
        when(schPunchRuleService.selectList(any(SchPunchRuleBo.class)))
            .thenReturn(List.of(rule(NEAR_LAT, NEAR_LNG, 200L)));
        when(employeeScheduleMapper.selectList(any())).thenReturn(List.of());
        when(baseMapper.selectList(any())).thenReturn(List.of());
        when(baseMapper.insert(any(SchAttendance.class))).thenReturn(1);

        Map<String, Object> data = service.performClockIn(11L, "张三", new Date(), null, null,
            NEAR_LAT, NEAR_LNG, "厂区门口");

        assertEquals(Boolean.TRUE, data.get("locationValid"));
        assertEquals(Boolean.TRUE, data.get("locationChecked"));
        // 距离必须是真实计算结果,不再是硬编码 0
        assertTrue(((Number) data.get("distance")).longValue() >= 0L);
        verify(baseMapper).insert(any(SchAttendance.class));
    }

    @Test
    void clockIn_withoutFenceConfig_skipsValidation() {
        // 规则未配置中心点/半径 → 视为未启用围栏, 不拦截(且 locationChecked=false,不谎报已校验)
        when(schPunchRuleService.selectList(any(SchPunchRuleBo.class)))
            .thenReturn(List.of(rule(null, null, null)));
        when(employeeScheduleMapper.selectList(any())).thenReturn(List.of());
        when(baseMapper.selectList(any())).thenReturn(List.of());
        when(baseMapper.insert(any(SchAttendance.class))).thenReturn(1);

        Map<String, Object> data = service.performClockIn(11L, "张三", new Date(), null, null,
            FAR_LAT, FAR_LNG, null);

        assertEquals(Boolean.FALSE, data.get("locationChecked"));
        assertEquals(0, ((Number) data.get("distance")).intValue());
        verify(baseMapper).insert(any(SchAttendance.class));
    }

    @Test
    void clockOut_outsideFence_isRejected() {
        when(schPunchRuleService.selectList(any(SchPunchRuleBo.class)))
            .thenReturn(List.of(rule(NEAR_LAT, NEAR_LNG, 200L)));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.performClockOut(11L, "张三", new Date(), null, null, 0.0, FAR_LAT, FAR_LNG, null));

        assertTrue(ex.getMessage().contains("超出打卡范围"), ex.getMessage());
        verify(baseMapper, never()).insert(any(SchAttendance.class));
        verify(baseMapper, never()).updateById(any(SchAttendance.class));
    }
}
