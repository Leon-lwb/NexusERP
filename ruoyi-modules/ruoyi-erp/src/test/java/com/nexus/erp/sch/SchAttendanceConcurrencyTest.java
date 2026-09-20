package com.nexus.erp.sch;

import com.nexus.common.cv.client.VisionClient;
import com.nexus.common.cv.config.CvProperties;
import com.nexus.erp.sch.domain.SchAttendance;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 打卡并发写冲突单元测试（ERP-SCH-09）
 * <p>修复前：{@code performClockIn} 先查后写，并发连点第二个请求撞
 * {@code uk_worker_date(tenant_id, worker_id, record_date)} 唯一键 → 直接冒泡 500。
 * 修复后：捕获 {@link DuplicateKeyException}，按"记录已存在"合并覆盖，返回正常打卡结果。</p>
 *
 * @author Nexus
 */
@Tag("dev")
@Tag("local")
@ExtendWith(MockitoExtension.class)
class SchAttendanceConcurrencyTest {

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

    private SchAttendanceServiceImpl service() {
        return new SchAttendanceServiceImpl(baseMapper, employeeScheduleMapper, shiftMapper,
            schPunchRuleService, visionClient, cvProperties, workerMapper, sysOssService);
    }

    @Test
    void clockIn_duplicateKey_isMergedInsteadOfThrowing500() {
        // 未配置围栏规则；当日无排班 → 状态 normal
        when(schPunchRuleService.selectList(any())).thenReturn(List.of());
        when(employeeScheduleMapper.selectList(any())).thenReturn(List.of());

        SchAttendance concurrent = new SchAttendance();
        concurrent.setAttendanceId(888L);
        // 第一次查为空(判定走插入) → 插入撞唯一键 → 第二次查已有并发写入的记录
        when(baseMapper.selectList(any())).thenReturn(List.of(), List.of(concurrent));
        when(baseMapper.insert(any(SchAttendance.class)))
            .thenThrow(new DuplicateKeyException("Duplicate entry for key 'uk_worker_date'"));
        when(baseMapper.updateById(any(SchAttendance.class))).thenReturn(1);

        Date now = new Date();
        Map<String, Object> result = service().performClockIn(1L, "张三", now, "dev-1", "闸机", null, null, null, null);

        assertNotNull(result);
        assertEquals(888L, result.get("attendanceId"));
        // 冲突路径必须走"合并更新"而不是继续插入/抛错
        verify(baseMapper).updateById(any(SchAttendance.class));
    }
}
