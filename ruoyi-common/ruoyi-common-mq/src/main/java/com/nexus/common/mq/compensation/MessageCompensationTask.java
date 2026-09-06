package com.nexus.common.mq.compensation;

import com.aizuda.snailjob.client.job.core.annotation.JobExecutor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mq.domain.BaseMessage;
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
 * <p>多实例安全: 重试前按 (id, status=SENDING, retry_count) 条件自增认领, 仅一方生效;
 * 重投经 ReliableMessageProducer.send 走完整可靠发送链路(重投成功后原记录按 messageId 置 SENT)。
 * 消息体按记录的 message_type 全限定名类型化反序列化(旧版本反序列化抽象 BaseMessage 必然失败, C1)。</p>
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
                compensate(record);
            } catch (Exception e) {
                log.error("消息补偿失败: messageId={}, error={}", record.getMessageId(), e.getMessage());
            }
        }
    }

    /** 单条补偿: 超限终态 -> 缺类型终态 -> 原子认领 -> 类型化反序列化重投 */
    private void compensate(MessageRecord record) {
        int retryCount = record.getRetryCount() != null ? record.getRetryCount() : 0;
        int maxRetry = record.getMaxRetry() != null ? record.getMaxRetry() : 3;
        if (retryCount >= maxRetry) {
            int rows = messageRecordMapper.update(null, Wrappers.<MessageRecord>lambdaUpdate()
                .set(MessageRecord::getStatus, "FAILED")
                .set(MessageRecord::getUpdateTime, new Date())
                .eq(MessageRecord::getMessageId, record.getMessageId())
                .eq(MessageRecord::getStatus, "SENDING")
                .eq(MessageRecord::getRetryCount, retryCount));
            if (rows > 0) {
                log.warn("消息补偿超过最大重试次数，标记为失败: messageId={}", record.getMessageId());
            }
            return;
        }
        if (StringUtils.isBlank(record.getMessageType())) {
            // 历史数据无类型信息, 无法类型化反序列化重投, 置终态避免无限扫描
            messageRecordMapper.update(null, Wrappers.<MessageRecord>lambdaUpdate()
                .set(MessageRecord::getStatus, "FAILED")
                .set(MessageRecord::getUpdateTime, new Date())
                .eq(MessageRecord::getId, record.getId())
                .eq(MessageRecord::getStatus, "SENDING"));
            log.warn("消息记录缺少消息类型, 无法补偿重投, 标记失败: messageId={}", record.getMessageId());
            return;
        }
        // 原子认领: retryCount 条件自增, 多实例部署下仅一方抢到, 防重复投递
        int claimed = messageRecordMapper.update(null, Wrappers.<MessageRecord>lambdaUpdate()
            .setSql("retry_count = retry_count + 1")
            .set(MessageRecord::getUpdateTime, new Date())
            .eq(MessageRecord::getId, record.getId())
            .eq(MessageRecord::getStatus, "SENDING")
            .eq(MessageRecord::getRetryCount, retryCount));
        if (claimed == 0) {
            return;
        }
        Class<?> clazz;
        try {
            clazz = Class.forName(record.getMessageType());
        } catch (ClassNotFoundException e) {
            log.error("消息类型不存在, 补偿失败: messageId={}, messageType={}", record.getMessageId(), record.getMessageType());
            return;
        }
        BaseMessage message;
        try {
            message = (BaseMessage) objectMapper.readValue(record.getMessageBody(), clazz);
        } catch (Exception e) {
            log.error("消息体反序列化失败, 补偿失败: messageId={}, messageType={}, error={}",
                record.getMessageId(), record.getMessageType(), e.getMessage());
            return;
        }
        // 重投走完整可靠发送链路: send 内部按 messageId 幂等更新记录状态(成功后原记录置 SENT)
        messageProducer.send(record.getExchange(), record.getRoutingKey(), message);
        log.info("重新投递消息: messageId={}, retryCount={}", record.getMessageId(), retryCount + 1);
    }
}
