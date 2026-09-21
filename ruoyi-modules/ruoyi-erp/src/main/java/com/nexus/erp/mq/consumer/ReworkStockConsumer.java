package com.nexus.erp.mq.consumer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.erp.mq.message.ReworkStockMessage;
import com.nexus.erp.prd.service.IPrdReworkOrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 返工库存消费者，异步更新返工订单状态
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
@DS("erp")
public class ReworkStockConsumer extends BaseMessageConsumer<ReworkStockMessage> {

    private final IPrdReworkOrderService reworkOrderService;

    /**
     * 获取消息类型
     *
     * @return ReworkStockMessage类型
     */
    @Override
    protected Class<ReworkStockMessage> getMessageClass() {
        return ReworkStockMessage.class;
    }

    /**
     * 根据操作类型更新返工订单状态
     * <p>ERP-PRD-04：此前两个分支都只 log.info，返工单状态永远停在 IN_PROGRESS，
     * 出入库过账后无法据此自动完工。现改为真正推进状态（条件更新，重复投递幂等）。</p>
     *
     * @param message 返工库存消息
     */
    @Override
    protected void doProcess(ReworkStockMessage message) {
        log.info("处理返工库存消息: reworkId={}, operationType={}", message.getReworkId(), message.getOperationType());
        reworkOrderService.advanceByStockOperation(
            message.getReworkId(), message.getOperationType(), message.getQuantity());
    }

    /**
     * 监听返工库存队列消息
     *
     * @param message     返工库存消息
     * @param channel     RabbitMQ通道
     * @param amqpMessage AMQP消息
     * @throws Exception 异常
     */
    @RabbitListener(queues = QueueConstants.REWORK_STOCK_QUEUE)
    public void onMessage(ReworkStockMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
