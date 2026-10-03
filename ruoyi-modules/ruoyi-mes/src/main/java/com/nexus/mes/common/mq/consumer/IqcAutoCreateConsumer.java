package com.nexus.mes.common.mq.consumer;

import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.message.ReceiveApprovedMessage;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.qc.service.QcReceiptIqcBirthService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 收货审批消息只提供租户、收货和物料身份。原单位、数量及名称必须重新从 ERP 永久出生
 * 登记取得；MES 创建与永久绑定由原生入口同事务完成，不用 Redis TTL 或裸唯一键冲突作认证。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class IqcAutoCreateConsumer extends BaseMessageConsumer<ReceiveApprovedMessage> {

    private final QcReceiptIqcBirthService birthService;

    @Override
    protected Class<ReceiveApprovedMessage> getMessageClass() {
        return ReceiveApprovedMessage.class;
    }

    @Override
    protected void doProcess(ReceiveApprovedMessage message) {
        birthService.create(TenantHelper.getTenantId(), message.getReceiveId(), message.getMaterialId());
        log.info("[IqcAutoCreate] 原生 IQC 创建或完整出生重放成功: receiveId={}, materialId={}",
            message.getReceiveId(), message.getMaterialId());
    }

    @RabbitListener(queues = QueueConstants.MES_IQC_CREATE_QUEUE)
    /**
     * onMessage 操作
     * @param message 收货审批消息
     * @param channel 消息确认通道
     * @param amqpMessage 原始投递元信息
     */
    public void onMessage(ReceiveApprovedMessage message, Channel channel, Message amqpMessage) throws Exception {
        try {
            // 原生创建或同 Birth 恢复必须先完成，Redis 的 DONE/PENDING 不能代替永久事实。
            String tenantId = resolveTenantId(message);
            TenantHelper.dynamic(tenantId, () -> doProcess(message));
        } catch (RuntimeException unprovenBirth) {
            // 沿用来源预检失败的隔离策略：不确认、不删除旧占位，修复后由运维重新投递。
            // 不将原生 SQL、来源主体或连接诊断带入消息日志。
            log.warn("IQC出生永久事实预检失败，消息隔离: messageId={}",
                message == null ? null : message.getMessageId());
            channel.basicNack(amqpMessage.getMessageProperties().getDeliveryTag(), false, false);
            return;
        }
        // 普通消费仍由基类处理；再次 create 必须核验同永久身份，而不是新增检验或模板。
        processMessage(message, channel, amqpMessage);
    }
}
