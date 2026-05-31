package com.nexus.erp.mq.consumer;

import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.erp.mq.message.PurchaseReceiveWritebackMessage;
import com.nexus.erp.wm.service.IWmMaterialReceiveService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 采购收货回写消费者，异步回写采购订单已收货数量
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PurchaseReceiveWritebackConsumer extends BaseMessageConsumer<PurchaseReceiveWritebackMessage> {

    private final IWmMaterialReceiveService materialReceiveService;

    /**
     * 获取消息类型
     *
     * @return PurchaseReceiveWritebackMessage类型
     */
    @Override
    protected Class<PurchaseReceiveWritebackMessage> getMessageClass() {
        return PurchaseReceiveWritebackMessage.class;
    }

    /**
     * 回写采购订单已收货数量
     *
     * @param message 回写消息
     */
    @Override
    protected void doProcess(PurchaseReceiveWritebackMessage message) {
        log.info("处理采购收货回写消息: receiveId={}", message.getReceiveId());
        materialReceiveService.writeBackPurchaseOrderReceivedQty(message.getReceiveId());
    }

    /**
     * 监听采购回写队列消息
     *
     * @param message     回写消息
     * @param channel     RabbitMQ通道
     * @param amqpMessage AMQP消息
     * @throws Exception 异常
     */
    @RabbitListener(queues = QueueConstants.PURCHASE_RECEIVE_QUEUE)
    public void onMessage(PurchaseReceiveWritebackMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
