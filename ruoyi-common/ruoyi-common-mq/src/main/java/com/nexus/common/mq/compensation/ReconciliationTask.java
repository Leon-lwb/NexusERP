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

/** 对账定时任务，对比消息状态与业务状态，识别不一致数据并注册补偿
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ReconciliationTask {

    private final MessageRecordMapper messageRecordMapper;
    private final ReliableMessageProducer messageProducer;
    private final ObjectMapper objectMapper;

    /** 执行对账扫描，查找已发送超过5分钟仍未确认消费的消息 */
    @JobExecutor(name = "mqReconciliationTask")
    public void execute() {
        Date threshold = new Date(System.currentTimeMillis() - 300000L);
        LambdaQueryWrapper<MessageRecord> lqw = new LambdaQueryWrapper<>();
        lqw.eq(MessageRecord::getStatus, "SENT");
        lqw.lt(MessageRecord::getCreateTime, threshold);
        List<MessageRecord> suspiciousRecords = messageRecordMapper.selectList(lqw);
        if (suspiciousRecords == null || suspiciousRecords.isEmpty()) {
            return;
        }
        log.warn("对账发现 {} 条已发送但未确认消费的消息", suspiciousRecords.size());
        for (MessageRecord record : suspiciousRecords) {
            try {
                com.nexus.common.mq.domain.BaseMessage message = objectMapper.readValue(
                    record.getMessageBody(), com.nexus.common.mq.domain.BaseMessage.class);
                messageProducer.send(record.getExchange(), record.getRoutingKey(), message);
                log.info("对账重发消息成功: messageId={}", record.getMessageId());
            } catch (Exception e) {
                log.error("对账重发消息失败: messageId={}, error={}", record.getMessageId(), e.getMessage());
            }
        }
    }
}
