package com.nexus.erp.mq.consumer;

import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.erp.mq.message.ProductionCompleteMessage;
import com.nexus.erp.prd.service.impl.PrdBackflushServiceImpl;
import com.nexus.erp.wm.service.IWmStockOperationService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 生产完工消费者，异步执行倒冲领料并触发成品入库
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ProductionBackflushConsumer extends BaseMessageConsumer<ProductionCompleteMessage> {

    private final PrdBackflushServiceImpl backflushService;
    private final ReliableMessageProducer messageProducer;
    private final IWmStockOperationService stockOperationService;

    /**
     * 获取消息类型
     *
     * @return ProductionCompleteMessage类型
     */
    @Override
    protected Class<ProductionCompleteMessage> getMessageClass() {
        return ProductionCompleteMessage.class;
    }

    /**
     * 执行倒冲领料并触发成品入库
     *
     * @param message 完工消息
     */
    @Override
    protected void doProcess(ProductionCompleteMessage message) {
        log.info("处理完工倒冲消息: woId={}, completionQty={}", message.getWoId(), message.getCompletionQty());
        backflushService.backflushOnCompletion(message.getWoId(), message.getCompletionQty());
        stockOperationService.postProductionCompletion(
            message.getWoId(), message.getMaterialId(), message.getMaterialCode(), message.getMaterialName(),
            message.getCompletionQty(), message.getWorkshopId(), null, new Date());
        log.info("完工入库过账完成: woId={}", message.getWoId());
    }

    /**
     * 监听生产完工队列消息
     *
     * @param message     完工消息
     * @param channel     RabbitMQ通道
     * @param amqpMessage AMQP消息
     * @throws Exception 异常
     */
    @RabbitListener(queues = QueueConstants.PRODUCTION_COMPLETE_QUEUE)
    public void onMessage(ProductionCompleteMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
