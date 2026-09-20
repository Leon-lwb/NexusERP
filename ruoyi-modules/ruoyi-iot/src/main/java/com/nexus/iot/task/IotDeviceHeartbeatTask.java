package com.nexus.iot.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import lombok.RequiredArgsConstructor;
import com.nexus.iot.device.domain.IotDevice;
import com.nexus.iot.device.service.IIotDeviceService;
import com.nexus.iot.mqtt.IotDeviceStatusCache;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * IoT 设备心跳检测任务：扫描最后在线时间早于 15 分钟仍标记 ONLINE 的设备，置为 OFFLINE。
 * <p>
 * SnailJob：executor=iotDeviceHeartbeatTask，建议 cron=0 * /5 * * * ?（每 5 分钟）。
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
@JobExecutor(name = "iotDeviceHeartbeatTask")
public class IotDeviceHeartbeatTask {

    /**
     * 本地兜底调度开关：SnailJob 客户端启用（{@code snail-job.enabled=true}）时本地兜底让位，
     * 避免同一任务被 SnailJob 与 {@code @Scheduled} 双重调度。
     */
    @org.springframework.beans.factory.annotation.Value("${snail-job.enabled:false}")
    private boolean snailJobEnabled;

    /**
     * 本地 {@code @Scheduled} 兜底入口（IOT-TASK-01）。
     * <p>生产环境 {@code snail-job.enabled=false} 时 SnailJob 客户端不启动、不会回调
     * {@link #jobExecute}，由本方法兜底；cron 与 IOT-TASK-02 修正后的 {@code sj_job} 种子一致
     * （每 5 分钟，与 {@link #TIMEOUT_MINUTES} 的 15 分钟阈值对齐）。
     * Lock4j 按任务名加分布式锁，多实例下仅一个节点执行，抢锁失败静默跳过。</p>
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 */5 * * * ?")
    public void scheduledExecute() {
        com.nexus.common.redis.utils.SchedulerFallbackUtils.runIfFallbackEnabled(
            snailJobEnabled, "iotDeviceHeartbeatTask", 300000L, () -> jobExecute(null));
    }

    /** 设备业务服务，提供超时在线设备查询与状态更新。 */
    private final IIotDeviceService deviceService;
    /** 设备在线状态 Redis 缓存。 */
    private final IotDeviceStatusCache statusCache;

    /** 心跳超时阈值（分钟），超过则判定为异常掉线。 */
    private static final int TIMEOUT_MINUTES = 15;

    /**
     * 执行心跳检测：将超时在线设备置为 OFFLINE 并刷新缓存。
     *
     * @param jobArgs SnailJob 任务参数（本任务未使用）
     * @return 执行结果，含置离线设备数
     */
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("IoT heartbeat check start, timeout={}min", TIMEOUT_MINUTES);
        try {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, -TIMEOUT_MINUTES);
            Date deadline = cal.getTime();
            List<IotDevice> staleDevices = deviceService.selectStaleOnlineDevices(deadline);
            int offlineCount = 0;
            for (IotDevice device : staleDevices) {
                deviceService.updateConnectionStatus(device.getDeviceId(), false);
                statusCache.markOffline(device);
                offlineCount++;
            }
            SnailJobLog.REMOTE.info("IoT heartbeat done: {} stale devices set OFFLINE", offlineCount);
            return ExecuteResult.success("Heartbeat done, offline=" + offlineCount);
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("IoT heartbeat task failed: {}", e.getMessage());
            return ExecuteResult.failure("Heartbeat failed: " + e.getMessage());
        }
    }
}
