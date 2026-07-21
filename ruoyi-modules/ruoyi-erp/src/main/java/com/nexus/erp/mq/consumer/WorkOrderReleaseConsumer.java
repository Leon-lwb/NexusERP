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

    /**
     * 返回本消费者处理的消息类型，即工单下达消息类。
     *
     * @return 工单下达消息的 Class 对象
     */
    @Override
    protected Class<WorkOrderReleaseMessage> getMessageClass() {
        return WorkOrderReleaseMessage.class;
    }

    /**
     * 处理工单下达消息（当前为占位实现，等待 MES 模块接入）。
     *
     * @param message 工单下达消息，包含工单ID、工单号、物料编码、计划数量等
     */
    @Override
    protected void doProcess(WorkOrderReleaseMessage message) {
        // 占位实现：MES模块搭建后替换为真实逻辑
        log.info("收到工单下达消息: woId={}, woNo={}, materialCode={}, plannedQty={}",
            message.getWoId(), message.getWoNo(), message.getMaterialCode(), message.getPlannedQty());
        log.info("MES模块尚未搭建，工单下达消息暂不处理（占位消费者）");
    }

    /**
     * RabbitMQ 监听入口，接收 MES 工单下达队列消息并交由基类统一处理（ACK/NACK）。
     *
     * @param message    工单下达消息体
     * @param channel    RabbitMQ 通道，用于手动确认
     * @param amqpMessage 原始 AMQP 消息
     * @throws Exception 处理或确认异常时抛出
     */
    @RabbitListener(queues = QueueConstants.MES_WORKORDER_QUEUE)
    public void onMessage(WorkOrderReleaseMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
