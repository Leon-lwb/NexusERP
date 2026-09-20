package com.nexus.erp.sch;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.cv.client.VisionClient;
import com.nexus.common.cv.config.CvProperties;
import com.nexus.erp.sch.domain.SchAttendance;
import com.nexus.erp.sch.mapper.SchAttendanceMapper;
import com.nexus.erp.sch.service.ISchAttendanceService;
import com.nexus.erp.sch.task.FaceVerifyBackfillTask;
import com.nexus.system.service.ISysOssService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FaceVerifyBackfillTask 降级补跑防饿死单元测试
 * <p>核心验证 ERP-SCH-07: 无存证照片的 NONE 记录不再每轮被派发校验、且尝试次数达上限后
 * 退出候选集;候选集按 face_verify_time 最旧优先,新记录不会被同一批旧记录饿死。</p>
 *
 * @author Nexus
 */
@Tag("dev")
@ExtendWith(MockitoExtension.class)
class FaceVerifyBackfillTaskTest {

    @Mock
    private SchAttendanceMapper attendanceMapper;
    @Mock
    private ISchAttendanceService schAttendanceService;
    @Mock
    private ISysOssService sysOssService;
    @Mock
    private VisionClient visionClient;
    @Mock
    private CvProperties cvProperties;

    @InjectMocks
    private FaceVerifyBackfillTask task;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 只有在生成 SQL 片段时才解析 lambda → 需要真实列缓存
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SchAttendance.class);
    }

    private SchAttendance noneRecord(Long id, Integer attempts) {
        SchAttendance record = new SchAttendance();
        record.setAttendanceId(id);
        record.setWorkerId(11L);
        record.setTenantId("000000");
        record.setFaceVerifyStatus("NONE");
        record.setFaceVerifyTime(new Date());
        record.setFaceVerifyAttempts(attempts);
        // 无存证照片: 降级期间未上传/存证失败
        record.setFaceImageOssId(null);
        return record;
    }

    @Test
    void backfill_recordWithoutPhoto_isCountedOnceAndNotDispatched() {
        when(cvProperties.isEnabled()).thenReturn(true);
        when(visionClient.isAvailable()).thenReturn(true);
        when(attendanceMapper.selectList(any())).thenReturn(List.of(noneRecord(1L, 0)));

        task.backfill();

        // 不再派发校验(原实现每轮都派发并刷新 face_verify_time,导致永久占用配额)
        verify(schAttendanceService, never()).backfillFaceVerify(any(), any(), any(), any());
        verify(schAttendanceService, never()).asyncFaceVerify(any(), any(), any(), any(), any(Boolean.class));

        ArgumentCaptor<SchAttendance> captor = ArgumentCaptor.forClass(SchAttendance.class);
        verify(attendanceMapper).updateById(captor.capture());
        SchAttendance update = captor.getValue();
        assertEquals(1, update.getFaceVerifyAttempts());
        assertNotNull(update.getFaceVerifyLastAttemptTime());
        // 不刷新补跑窗口时间: 该记录才能自然滚出 24h 候选窗口
        assertNull(update.getFaceVerifyTime());
    }

    @Test
    @SuppressWarnings("rawtypes")
    void backfillQuery_ordersOldestFirstAndCapsAttempts() {
        when(cvProperties.isEnabled()).thenReturn(true);
        when(visionClient.isAvailable()).thenReturn(true);
        when(attendanceMapper.selectList(any())).thenReturn(List.of());

        task.backfill();

        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(attendanceMapper).selectList(captor.capture());
        String sql = ((LambdaQueryWrapper<?>) captor.getValue()).getSqlSegment();
        assertTrue(sql.contains("face_verify_attempts"), sql);
        assertTrue(sql.toUpperCase().contains("ORDER BY"), sql);
        assertTrue(sql.toUpperCase().contains("FACE_VERIFY_TIME"), sql);
    }

    @Test
    void backfill_recordWithUnreadablePhoto_countsAttemptInsteadOfRetryingEveryRound() {
        when(cvProperties.isEnabled()).thenReturn(true);
        when(visionClient.isAvailable()).thenReturn(true);
        SchAttendance record = noneRecord(2L, 0);
        // 有 ossId 但 sys_oss 无对应文件(存证失败/被清理) → 取图失败
        record.setFaceImageOssId(1234L);
        when(attendanceMapper.selectList(any())).thenReturn(List.of(record));
        when(sysOssService.getById(1234L)).thenReturn(null);

        task.backfill();

        // 取图失败的记录同样不派发校验,只计一次尝试(原实现每轮都会派发并刷新 face_verify_time)
        verify(schAttendanceService, never()).backfillFaceVerify(any(), any(), any(), any());
        ArgumentCaptor<SchAttendance> captor = ArgumentCaptor.forClass(SchAttendance.class);
        verify(attendanceMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getFaceVerifyAttempts());
        assertNotNull(captor.getValue().getFaceVerifyLastAttemptTime());
        assertNull(captor.getValue().getFaceVerifyTime());
    }

    @Test
    void backfill_skippedWhenVisionUnavailable() {
        when(cvProperties.isEnabled()).thenReturn(true);
        when(visionClient.isAvailable()).thenReturn(false);

        task.backfill();

        verify(attendanceMapper, never()).selectList(any());
    }

    @Test
    void backfill_skippedWhenVisionDisabled() {
        when(cvProperties.isEnabled()).thenReturn(false);

        task.backfill();

        verify(attendanceMapper, never()).selectList(any());
    }
}
