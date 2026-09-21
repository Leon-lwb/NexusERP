package com.nexus.erp.sch.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.cv.client.VisionClient;
import com.nexus.common.cv.config.CvProperties;
import com.nexus.common.oss.factory.OssFactory;
import com.nexus.erp.sch.domain.SchAttendance;
import com.nexus.erp.sch.mapper.SchAttendanceMapper;
import com.nexus.erp.sch.service.ISchAttendanceService;
import com.nexus.system.domain.vo.SysOssVo;
import com.nexus.system.service.ISysOssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.baomidou.dynamic.datasource.annotation.DS;

import java.io.InputStream;
import java.util.Date;
import java.util.List;

/**
 * 打卡人脸验证降级补跑任务
 * <p><b>降级安全闭环</b>:vision 故障期间打卡校验全部落 NONE,若无人处理等于静默关闭校验。
 * 本任务每小时扫描:</p>
 * <p>1. 服务可用性检测:连续 {@code nexus.vision.health-fail-threshold} 次健康检查失败在
 * {@link VisionClient#isAvailable()} 内触发告警(日志级别,生产可扩展飞书/钉钉 webhook);恢复后自动重置。</p>
 * <p>2. NONE 记录批量补跑:对 24h 内 face_verify_status=NONE 且已有存证照片的打卡记录重新调
 * vision 1:1 校验并回写,避免"降级=静默关闭校验"。</p>
 *
 * @author nexus
 * @since 2026-08-02
 */
@Slf4j
@Component
@RequiredArgsConstructor
@DS("erp")
public class FaceVerifyBackfillTask {

    private final SchAttendanceMapper attendanceMapper;
    private final ISchAttendanceService schAttendanceService;
    private final ISysOssService sysOssService;
    private final VisionClient visionClient;
    private final CvProperties cvProperties;

    /** 补跑窗口:仅处理 24h 内 NONE 记录,避免历史积压 */
    private static final long BACKFILL_WINDOW_MS = 24L * 3600 * 1000;

    /** 每轮最大处理条数,避免单轮耗时过长占用调度线程 */
    private static final int MAX_BATCH = 200;

    /** 单条记录最大补跑尝试次数:超过后不再进入候选集,避免无照片记录永久占用每轮配额 */
    private static final int MAX_ATTEMPTS = 3;

    /**
     * 每小时执行:健康检测 + NONE 补跑
     */
    @Scheduled(cron = "0 17 * * * ?")
    public void backfill() {
        if (!cvProperties.isEnabled()) {
            return;
        }
        if (visionClient.isAvailable()) {
            backfillNoneRecords();
        } else {
            log.warn("[cv] 降级补跑任务:vision 服务不可用,跳过本轮 NONE 补跑,待服务恢复(告警见 isAvailable)");
        }
    }

    /**
     * 扫描 24h 内 NONE 记录逐条补跑
     * <p>sch_attendance 在 erp 库,调度线程无 @DS 上下文,显式切 erp 数据源。</p>
     * <p><b>防饿死(ERP-SCH-07)</b>:最旧优先 + 尝试次数上限——原实现无排序、无计数、
     * 每轮还会刷新 {@code face_verify_time},导致无存证照片的 NONE 记录永远落在 24h 窗口内,
     * 每轮占满 200 条配额,真正待补跑的新记录排不进来。</p>
     */
    private void backfillNoneRecords() {
        DynamicDataSourceContextHolder.push("erp");
        try {
            Date since = new Date(System.currentTimeMillis() - BACKFILL_WINDOW_MS);
            LambdaQueryWrapper<SchAttendance> wrapper = Wrappers.lambdaQuery();
            wrapper.eq(SchAttendance::getFaceVerifyStatus, "NONE");
            wrapper.ge(SchAttendance::getFaceVerifyTime, since);
            // 尝试次数上限:无照片/取图失败的记录达到上限后退出候选集
            wrapper.and(w -> w.isNull(SchAttendance::getFaceVerifyAttempts)
                .or().lt(SchAttendance::getFaceVerifyAttempts, MAX_ATTEMPTS));
            // 最旧优先:新产生的待补跑记录不会被同一批旧记录反复饿死
            wrapper.orderByAsc(SchAttendance::getFaceVerifyTime);
            wrapper.last("LIMIT " + MAX_BATCH);
            List<SchAttendance> list = attendanceMapper.selectList(wrapper);
            if (CollUtil.isEmpty(list)) {
                return;
            }
            log.info("[cv] 降级补跑:发现 {} 条 NONE 记录待补跑", list.size());
            for (SchAttendance attendance : list) {
                retryVerify(attendance);
            }
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    /**
     * 单条补跑:从 MinIO 取回存证照片重新校验
     * <p>复用 {@code backfillFaceVerify}(cvExecutor 异步执行,不占调度线程);
     * 复用已有 ossId,避免重复存证。</p>
     * <p>无存证照片时<b>不再派发</b>校验,只累计一次尝试次数——原实现每轮都派发并刷新
     * {@code face_verify_time},等于把这类记录永久锁在候选集里。</p>
     */
    private void retryVerify(SchAttendance attendance) {
        byte[] imageBytes = loadFaceImage(attendance);
        if (imageBytes == null || imageBytes.length == 0) {
            // 无存证照片(降级期间未上传/存证失败): 计一次尝试,达上限后不再占用配额
            log.warn("[cv] 补跑跳过:无存证照片, attendanceId={}, 已尝试 {} 次",
                attendance.getAttendanceId(), currentAttempts(attendance) + 1);
            markAttempt(attendance);
            return;
        }
        schAttendanceService.backfillFaceVerify(
            attendance.getAttendanceId(),
            attendance.getWorkerId(),
            attendance.getTenantId(),
            imageBytes
        );
    }

    /**
     * 累计一次补跑尝试(attempts+1 + last_attempt_time),不刷新 face_verify_time
     */
    private void markAttempt(SchAttendance attendance) {
        SchAttendance update = new SchAttendance();
        update.setAttendanceId(attendance.getAttendanceId());
        update.setFaceVerifyAttempts(currentAttempts(attendance) + 1);
        update.setFaceVerifyLastAttemptTime(new Date());
        attendanceMapper.updateById(update);
    }

    private int currentAttempts(SchAttendance attendance) {
        return attendance.getFaceVerifyAttempts() == null ? 0 : attendance.getFaceVerifyAttempts();
    }

    /**
     * 从 MinIO 取回存证照片字节
     * <p>sys_oss 表在 master 库(ry-vue),补跑调度线程无 @DS 上下文,显式切主数据源。</p>
     */
    private byte[] loadFaceImage(SchAttendance attendance) {
        if (attendance.getFaceImageOssId() == null) {
            return null;
        }
        try {
            DynamicDataSourceContextHolder.push("master");
            try {
                SysOssVo ossVo = sysOssService.getById(attendance.getFaceImageOssId());
                if (ossVo == null || ossVo.getFileName() == null) {
                    log.warn("[cv] 补跑:ossId={} 无文件记录, attendanceId={}",
                        attendance.getFaceImageOssId(), attendance.getAttendanceId());
                    return null;
                }
                try (InputStream in = OssFactory.instance(ossVo.getService())
                    .getObjectContent(ossVo.getFileName())) {
                    return IoUtil.readBytes(in);
                }
            } finally {
                DynamicDataSourceContextHolder.poll();
            }
        } catch (Exception e) {
            log.warn("[cv] 补跑取回照片失败, attendanceId={}, ossId={}: {}",
                attendance.getAttendanceId(), attendance.getFaceImageOssId(), e.getMessage());
            return null;
        }
    }
}
