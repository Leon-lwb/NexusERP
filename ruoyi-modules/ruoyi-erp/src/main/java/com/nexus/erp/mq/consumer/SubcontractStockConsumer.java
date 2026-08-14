package com.nexus.erp.mq.consumer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.erp.mq.message.SubcontractStockMessage;
import com.nexus.erp.prd.domain.PrdSubcontract;
import com.nexus.erp.prd.service.IPrdSubcontractService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 委外库存消费者，异步更新委外订单状态
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
@DS("erp")
public class SubcontractStockConsumer extends BaseMessageConsumer<SubcontractStockMessage> {

    private final IPrdSubcontractService subcontractService;

    /**
     * 获取消息类型
     *
     * @return SubcontractStockMessage类型
     */
    @Override
    protected Class<SubcontractStockMessage> getMessageClass() {
        return SubcontractStockMessage.class;
    }

    /**
     * 根据操作类型更新委外订单状态: ISSUE→IN_PROGRESS, RECEIVE→COMPLETED
     *
     * @param message 委外库存消息
     */
    @Override
    protected void doProcess(SubcontractStockMessage message) {
        log.info("处理委外库存消息: subcontractId={}, operationType={}", message.getSubcontractId(), message.getOperationType());
        PrdSubcontract subcontract = subcontractService.getById(message.getSubcontractId());
        if (subcontract == null) {
            log.warn("委外订单不存在: subcontractId={}", message.getSubcontractId());
            return;
        }
        String currentStatus = subcontract.getStatus();
        if ("ISSUE".equals(message.getOperationType())) {
            if (!"RELEASED".equals(currentStatus) && !"PENDING".equals(currentStatus)) {
                log.warn("委外订单[{}]当前状态[{}]不允许发料", subcontract.getSubcontractNo(), currentStatus);
                return;
            }
            subcontract.setStatus("IN_PROGRESS");
        } else if ("RECEIVE".equals(message.getOperationType())) {
            if (!"IN_PROGRESS".equals(currentStatus)) {
                log.warn("委外订单[{}]当前状态[{}]不允许收货", subcontract.getSubcontractNo(), currentStatus);
                return;
            }
            subcontract.setStatus("COMPLETED");
        }
        subcontractService.updateById(subcontract);
    }

    /**
     * 监听委外库存队列消息
     *
     * @param message     委外库存消息
     * @param channel     RabbitMQ通道
     * @param amqpMessage AMQP消息
     * @throws Exception 异常
     */
    @RabbitListener(queues = QueueConstants.SUBCONTRACT_STOCK_QUEUE)
    public void onMessage(SubcontractStockMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
