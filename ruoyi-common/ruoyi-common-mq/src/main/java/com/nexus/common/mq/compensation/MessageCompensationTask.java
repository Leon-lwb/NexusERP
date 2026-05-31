package com.nexus.common.mq.compensation;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.mq.domain.MessageRecord;
import com.nexus.common.mq.domain.MessageRecordMapper;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 消息补偿定时任务，扫描超时未确认的消息并重新投递
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class MessageCompensationTask {

    private final MessageRecordMapper messageRecordMapper;
    private final ReliableMessageProducer messageProducer;
    private final ObjectMapper objectMapper;

    /** 执行消息补偿扫描 */
    @JobExecutor(name = "mqMessageCompensationTask")
    public void execute() {
        Date threshold = new Date(System.currentTimeMillis() - 60000L);
        LambdaQueryWrapper<MessageRecord> lqw = new LambdaQueryWrapper<>();
        lqw.eq(MessageRecord::getStatus, "SENDING");
        lqw.lt(MessageRecord::getCreateTime, threshold);
        List<MessageRecord> pendingRecords = messageRecordMapper.selectList(lqw);
        if (pendingRecords == null || pendingRecords.isEmpty()) {
            return;
        }
        log.info("扫描到 {} 条待补偿消息", pendingRecords.size());
        for (MessageRecord record : pendingRecords) {
            try {
                int retryCount = record.getRetryCount() != null ? record.getRetryCount() : 0;
                int maxRetry = record.getMaxRetry() != null ? record.getMaxRetry() : 3;
                if (retryCount >= maxRetry) {
                    record.setStatus("FAILED");
                    record.setUpdateTime(new Date());
                    messageRecordMapper.updateById(record);
                    log.warn("消息补偿超过最大重试次数，标记为失败: messageId={}", record.getMessageId());
                    continue;
                }
                com.nexus.common.mq.domain.BaseMessage message = objectMapper.readValue(
                    record.getMessageBody(), com.nexus.common.mq.domain.BaseMessage.class);
                messageProducer.send(record.getExchange(), record.getRoutingKey(), message);
                record.setRetryCount(retryCount + 1);
                record.setUpdateTime(new Date());
                messageRecordMapper.updateById(record);
                log.info("重新投递消息: messageId={}, retryCount={}", record.getMessageId(), retryCount + 1);
            } catch (Exception e) {
                log.error("消息补偿失败: messageId={}, error={}", record.getMessageId(), e.getMessage());
            }
        }
    }
}
