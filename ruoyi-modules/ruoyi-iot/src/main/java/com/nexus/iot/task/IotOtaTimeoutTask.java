package com.nexus.iot.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.RequiredArgsConstructor;
import com.nexus.common.redis.utils.SchedulerFallbackUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.iot.ota.service.IIotOtaTaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * IoT OTA 升级超时兜底任务（OTA MVP 第 7 项）。
 * <p>
 * 扫描 {@code iot_ota_task} 中 {@code SENT}/{@code UPGRADING} 且下发时间早于
 * {@code now - nexus.iot.ota.timeout-minutes} 的任务，条件更新为 {@code TIMEOUT}，
 * 避免"设备刷机失败/掉线后任务永久挂在进行中"。
 * <p>
 * <b>调度方式（与 B1 批次一致）</b>：
 * <ul>
 *   <li>SnailJob：{@code executor=iotOtaTimeoutTask}，cron {@code 0 *&#47;5 * * * ?}（每 5 分钟）；</li>
 *   <li>本地兜底：{@code snail-job.enabled=false}（生产默认）时由 {@link #scheduledExecute()} 执行，
 *       经 {@link SchedulerFallbackUtils} 做开关判断 + Lock4j 分布式锁（多实例仅一个节点执行）。</li>
 * </ul>
 * <b>跨租户</b>：超时扫描是平台级运维动作，需覆盖全部租户，故以 {@link TenantHelper#ignore} 包裹，
 * 绕开 MyBatis-Plus 多租户条件（否则无登录上下文时会因 tenant_id 为空而扫不到任何行）。
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
@JobExecutor(name = "iotOtaTimeoutTask")
public class IotOtaTimeoutTask {

    /** 任务名（与 SnailJob executor / sj_job.executor_info 一致）。 */
    public static final String TASK_NAME = "iotOtaTimeoutTask";

    /**
     * 本地兜底调度开关：SnailJob 客户端启用（{@code snail-job.enabled=true}）时本地兜底让位。
     */
    @Value("${snail-job.enabled:false}")
    private boolean snailJobEnabled;

    /**
     * 升级超时窗口（分钟）：下发后超过该时长仍未回执即判定 TIMEOUT。
     */
    @Value("${nexus.iot.ota.timeout-minutes:60}")
    private long timeoutMinutes = 60L;

    private final IIotOtaTaskService otaTaskService;

    /**
     * 本地 {@code @Scheduled} 兜底入口（每 5 分钟，与 SnailJob cron 一致）。
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void scheduledExecute() {
        SchedulerFallbackUtils.runIfFallbackEnabled(snailJobEnabled, TASK_NAME, 300000L,
            () -> jobExecute(null));
    }

    /**
     * 执行超时扫描。
     *
     * @param jobArgs SnailJob 任务参数（本任务未使用）
     * @return 执行结果，含被置为 TIMEOUT 的任务数
     */
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("IoT OTA timeout check start, timeout={}min", timeoutMinutes);
        try {
            Date deadline = new Date(System.currentTimeMillis() - timeoutMinutes * 60_000L);
            int count = TenantHelper.ignore(() -> otaTaskService.markTimeout(deadline));
            SnailJobLog.REMOTE.info("IoT OTA timeout done: {} task(s) marked TIMEOUT", count);
            return ExecuteResult.success("OTA timeout done, timeout=" + count);
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("IoT OTA timeout task failed: {}", e.getMessage());
            return ExecuteResult.failure("OTA timeout failed: " + e.getMessage());
        }
    }
}
