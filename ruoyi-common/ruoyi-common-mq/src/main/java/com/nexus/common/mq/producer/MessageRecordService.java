package com.nexus.common.mq.producer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nexus.common.mq.domain.MessageRecord;
import com.nexus.common.mq.domain.MessageRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消息记录持久化服务
 * <p>
 * mq_message_record 表位于 ry-vue(master)库。若在 @DS("erp")/("mes") 的业务事务中
 * 直接写该表，因数据库连接已绑定到业务库，类级 @DS("master") 无法生效，会报
 * "Table 'mq_message_record' doesn't exist"。
 * <p>
 * 此处显式 @DS("master") + REQUIRES_NEW：挂起调用方的业务事务，在 master 库开启新事务写入，
 * 确保 ERP/MES 业务事务内的 MQ 发送也能正确记录消息状态。
 * 跨库写入模式参考 {@code ProWorkOrderServiceImpl#afterFlowHandle}。
 *
 * @author Nexus
 */
@RequiredArgsConstructor
public class MessageRecordService {

    private final MessageRecordMapper messageRecordMapper;

    /**
     * 记录消息为指定状态(通常为 SENDING)
     *
     * @param record 消息记录
     */
    @DS("master")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void saveSending(MessageRecord record) {
        messageRecordMapper.insert(record);
    }

    /**
     * 更新消息状态(如 SENT)
     *
     * @param messageId 消息ID
     * @param status    目标状态
     */
    @DS("master")
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void updateStatus(String messageId, String status) {
        messageRecordMapper.update(null, new LambdaUpdateWrapper<MessageRecord>()
            .eq(MessageRecord::getMessageId, messageId)
            .set(MessageRecord::getStatus, status));
    }
}
