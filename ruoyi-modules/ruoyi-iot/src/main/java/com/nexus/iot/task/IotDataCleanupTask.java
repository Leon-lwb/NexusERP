package com.nexus.iot.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.RequiredArgsConstructor;
import com.nexus.iot.device.service.IIotDeviceDataService;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;

/**
 * IoT 设备数据清理任务：删除采集时间超过 90 天的 iot_device_data 记录。
 * <p>
 * SnailJob 后台 ruoyi_group 建任务: executor=iotDataCleanupTask, 建议 cron 0 0 2 * * ? (每天凌晨2:00).
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
@JobExecutor(name = "iotDataCleanupTask")
public class IotDataCleanupTask {

    /**
     * 本地兜底调度开关：SnailJob 客户端启用（{@code snail-job.enabled=true}）时本地兜底让位，
     * 避免同一任务被 SnailJob 与 {@code @Scheduled} 双重调度。
     */
    @org.springframework.beans.factory.annotation.Value("${snail-job.enabled:false}")
    private boolean snailJobEnabled;

    /**
     * 本地 {@code @Scheduled} 兜底入口（IOT-TASK-01）。
     * <p>生产环境 {@code snail-job.enabled=false} 时 SnailJob 客户端不启动、不会回调
     * {@link #jobExecute}，由本方法兜底；cron 与 {@code sj_job} 种子一致（每天 02:00）。
     * Lock4j 按任务名加分布式锁，多实例下仅一个节点执行，抢锁失败静默跳过。</p>
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 2 * * ?")
    public void scheduledExecute() {
        com.nexus.common.redis.utils.SchedulerFallbackUtils.runIfFallbackEnabled(
            snailJobEnabled, "iotDataCleanupTask", 1800000L, () -> jobExecute(null));
    }

    /** 设备数据业务服务，提供按时间删除能力。 */
    private final IIotDeviceDataService deviceDataService;

    /** 数据保留天数，超过则清理。 */
    private static final int RETENTION_DAYS = 90;

    /**
     * 清理采集时间早于当前时间 {@code RETENTION_DAYS} 天的历史设备数据。
     *
     * @param jobArgs SnailJob 任务参数（本任务未使用）
     * @return 执行结果，含删除条数
     */
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("开始执行IoT设备数据清理任务，保留天数={}", RETENTION_DAYS);
        try {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DAY_OF_MONTH, -RETENTION_DAYS);
            Date deadline = cal.getTime();
            int deleted = deviceDataService.deleteBefore(deadline);
            SnailJobLog.REMOTE.info("IoT数据清理完成：deadline={}, 删除{}条", deadline, deleted);
            return ExecuteResult.success("数据清理完成，删除" + deleted + "条");
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("IoT数据清理任务执行失败: {}", e.getMessage());
            return ExecuteResult.failure("数据清理失败: " + e.getMessage());
        }
    }
}
