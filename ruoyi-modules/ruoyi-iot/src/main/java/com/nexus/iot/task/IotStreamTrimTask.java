package com.nexus.iot.task;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.aizuda.snailjob.client.job.core.dto.JobArgs;
import com.aizuda.snailjob.common.log.SnailJobLog;
import com.aizuda.snailjob.model.dto.ExecuteResult;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.iot.pipeline.IotPipelineProperties;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RStream;
import org.redisson.api.stream.StreamTrimArgs;
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

    private final IotPipelineProperties properties;

    private static final long MAX_LEN = 100000L;

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
