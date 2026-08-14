package com.nexus.mes.common.mq.consumer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.message.WorkOrderReleaseMessage;
import com.nexus.mes.pro.service.IProWorkOrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 工单下达消费者（MES侧）
 * <p>
 * 消费ERP审批通过的工单下达消息，在MES中创建工单记录及工序步骤。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class WorkOrderReleaseConsumer extends BaseMessageConsumer<WorkOrderReleaseMessage> {

    private final IProWorkOrderService proWorkOrderService;

    @Override
    protected Class<WorkOrderReleaseMessage> getMessageClass() {
        return WorkOrderReleaseMessage.class;
    }

    @Override
    protected void doProcess(WorkOrderReleaseMessage message) {
        log.info("收到工单下达消息: woId={}, woNo={}, materialCode={}, plannedQty={}",
            message.getWoId(), message.getWoNo(), message.getMaterialCode(), message.getPlannedQty());

        proWorkOrderService.createFromErpMessage(message);
    }

    @RabbitListener(queues = QueueConstants.MES_WORKORDER_QUEUE)
    /**
     * onMessage 操作
     * @param message message
     * @param channel channel
     * @param amqpMessage amqpMessage
     */
    public void onMessage(WorkOrderReleaseMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
