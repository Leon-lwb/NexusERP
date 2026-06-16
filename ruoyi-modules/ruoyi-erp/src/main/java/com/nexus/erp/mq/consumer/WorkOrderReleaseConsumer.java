package com.nexus.erp.mq.consumer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.message.WorkOrderReleaseMessage;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 工单下达消费者（占位实现）
 * <p>
 * MES模块搭建后替换为真实逻辑：创建MES工单、分配工序、生成流转卡等
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnProperty(name = "mes.placeholder-consumer.enabled", havingValue = "true", matchIfMissing = false)
@DS("erp")
public class WorkOrderReleaseConsumer extends BaseMessageConsumer<WorkOrderReleaseMessage> {

    @Override
    protected Class<WorkOrderReleaseMessage> getMessageClass() {
        return WorkOrderReleaseMessage.class;
    }

    @Override
    protected void doProcess(WorkOrderReleaseMessage message) {
        // 占位实现：MES模块搭建后替换为真实逻辑
        log.info("收到工单下达消息: woId={}, woNo={}, materialCode={}, plannedQty={}",
            message.getWoId(), message.getWoNo(), message.getMaterialCode(), message.getPlannedQty());
        log.info("MES模块尚未搭建，工单下达消息暂不处理（占位消费者）");
    }

    @RabbitListener(queues = QueueConstants.MES_WORKORDER_QUEUE)
    public void onMessage(WorkOrderReleaseMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
