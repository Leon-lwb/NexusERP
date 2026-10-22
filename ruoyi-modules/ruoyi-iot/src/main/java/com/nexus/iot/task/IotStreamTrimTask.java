package com.nexus.iot.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.redis.utils.SchedulerFallbackUtils;
import com.nexus.iot.pipeline.IotPipelineProperties;

import lombok.RequiredArgsConstructor;

import org.redisson.api.RStream;
import org.redisson.api.stream.StreamTrimArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * IoT Redis Stream 修剪任务：控制 {@code iot:data:stream} 长度不超过 MAXLEN，防止内存无限增长。
 * <p>
 * 使用 Redisson {@code XTRIM MAXLEN ~100000}（近似修剪，Redis 高效回收）。
 * Stream 是环形缓冲层，已 ACK 消息被修剪不影响数据（已落 MySQL）。
 * <p>
 * SnailJob 后台 ruoyi_group 建任务: executor=iotStreamTrimTask, 建议 cron 0 0 * * * ? (每小时).
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
@JobExecutor(name = "iotStreamTrimTask")
public class IotStreamTrimTask {

    /**
     * 本地兜底调度开关：SnailJob 客户端启用（{@code snail-job.enabled=true}）时本地兜底让位， 避免同一任务被 SnailJob 与
     * {@code @Scheduled} 双重调度。
     */
    @Value("${snail-job.enabled:false}")
    private boolean snailJobEnabled;

    /**
     * 本地 {@code @Scheduled} 兜底入口（IOT-TASK-01）。
     *
     * <p>生产环境 {@code snail-job.enabled=false} 时 SnailJob 客户端不启动、不会回调 {@link
     * #jobExecute}，由本方法兜底；cron 与 IOT-TASK-02 修正后的 {@code sj_job} 种子一致 （每小时；Stream 不裁剪会先把 Redis
     * 撑爆，属“不做就宕机”项）。 Lock4j 按任务名加分布式锁，多实例下仅一个节点执行，抢锁失败静默跳过。
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void scheduledExecute() {
        SchedulerFallbackUtils.runIfFallbackEnabled(
                snailJobEnabled, "iotStreamTrimTask", 300000L, () -> jobExecute(null));
    }

    /** 管道配置属性，提供 Stream Key。 */
    private final IotPipelineProperties properties;

    /** Stream 最大保留长度，超过则近似修剪。 */
    private static final long MAX_LEN = 100000L;

    /**
     * 执行 Stream 修剪：{@code XTRIM MAXLEN ~MAX_LEN} 近似回收，控制内存。
     *
     * @param jobArgs SnailJob 任务参数（本任务未使用）
     * @return 执行结果，含修剪条数
     */
    public ExecuteResult jobExecute(JobArgs jobArgs) {
        SnailJobLog.REMOTE.info("开始执行IoT Redis Stream修剪任务，stream={}, MAXLEN={}", properties.getStreamKey(), MAX_LEN);
        try {
            RStream<Object, Object> stream = RedisUtils.getClient().getStream(properties.getStreamKey());
            long trimmed = stream.trim(StreamTrimArgs.maxLen((int) MAX_LEN).noLimit());
            SnailJobLog.REMOTE.info("IoT Stream修剪完成：trimmed={}", trimmed);
            return ExecuteResult.success("Stream修剪完成，trimmed=" + trimmed);
        } catch (Exception e) {
            SnailJobLog.REMOTE.error("IoT Stream修剪任务执行失败: {}", e.getMessage());
            return ExecuteResult.failure("Stream修剪失败: " + e.getMessage());
        }
    }
}
