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
 * 消息对账定时任务
 * <p>旧版本误扫 SENT(已成功投递)记录重发——既反序列化抽象 BaseMessage 必然失败, 又构成无意义的
 * 重复投递(本项目无消费确认回执, SENT 即正常终态)。已修正为对"发送滞留"的对账兜底:
 * 扫描 SENDING 滞留超过5分钟的记录, 类型化反序列化后重投, 与
 * {@link MessageCompensationTask}(1分钟窗口) 构成双任务冗余保障——
 * 两者均按 (id, status=SENDING, retry_count) CAS 认领, 多实例/多任务并发下仅一方生效, 不会重复投递。</p>
 * <p>SENT 记录不做任何处理: 已成功到达 broker, 消费端由 BaseMessageConsumer 按 messageId 幂等。</p>
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ReconciliationTask {

    /** 滞留判定阈值: 发送后5分钟仍为 SENDING 视为发送滞留 */
    private static final long STUCK_THRESHOLD_MS = 300000L;

    private final MessageRecordMapper messageRecordMapper;
    private final ReliableMessageProducer messageProducer;
    private final ObjectMapper objectMapper;

    /** 执行对账扫描 */
    @JobExecutor(name = "mqReconciliationTask")
    public void execute() {
        Date threshold = new Date(System.currentTimeMillis() - STUCK_THRESHOLD_MS);
        LambdaQueryWrapper<MessageRecord> lqw = new LambdaQueryWrapper<>();
        lqw.eq(MessageRecord::getStatus, "SENDING");
        lqw.lt(MessageRecord::getCreateTime, threshold);
        List<MessageRecord> stuckRecords = messageRecordMapper.selectList(lqw);
        if (stuckRecords == null || stuckRecords.isEmpty()) {
            return;
        }
        log.warn("对账发现 {} 条发送滞留消息(SENDING超过5分钟)", stuckRecords.size());
        for (MessageRecord record : stuckRecords) {
            try {
                reconcile(record);
            } catch (Exception e) {
                log.error("对账重发消息失败: messageId={}, error={}", record.getMessageId(), e.getMessage());
            }
        }
    }

    /** 单条对账: 超限终态 -> 缺类型终态 -> 原子认领 -> 类型化反序列化重投 */
    private void reconcile(MessageRecord record) {
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
                log.warn("对账: 消息滞留超过最大重试次数, 标记失败: messageId={}", record.getMessageId());
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
            log.warn("对账: 消息记录缺少消息类型, 无法重投, 标记失败: messageId={}", record.getMessageId());
            return;
        }
        // 原子认领: retryCount 条件自增, 与 MessageCompensationTask / 多实例并发下仅一方生效
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
            log.error("对账: 消息类型不存在, 重投失败: messageId={}, messageType={}", record.getMessageId(), record.getMessageType());
            return;
        }
        BaseMessage message;
        try {
            message = (BaseMessage) objectMapper.readValue(record.getMessageBody(), clazz);
        } catch (Exception e) {
            log.error("对账: 消息体反序列化失败: messageId={}, messageType={}, error={}",
                record.getMessageId(), record.getMessageType(), e.getMessage());
            return;
        }
        // 重投走完整可靠发送链路: send 内部按 messageId 幂等更新记录状态(成功后原记录置 SENT)
        messageProducer.send(record.getExchange(), record.getRoutingKey(), message);
        log.info("对账重投滞留消息: messageId={}, retryCount={}", record.getMessageId(), retryCount + 1);
    }
}
