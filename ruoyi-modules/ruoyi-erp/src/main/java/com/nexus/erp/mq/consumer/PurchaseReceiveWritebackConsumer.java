package com.nexus.erp.mq.consumer;

import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.erp.mq.message.PurchaseReceiveWritebackMessage;
import com.nexus.erp.pu.service.IPuPurchaseReceiveWritebackService;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.mapper.WmMaterialReceiveMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 采购收货回写消费者，异步回写采购订单已收货数量
 * <p>
 * Q-003 重构后：不再调用 IWmMaterialReceiveService.writeBackPurchaseOrderReceivedQty，
 * 改为调用 PuPurchaseReceiveWritebackService（采购模块自行承担回写逻辑）。
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class PurchaseReceiveWritebackConsumer extends BaseMessageConsumer<PurchaseReceiveWritebackMessage> {

    private final IPuPurchaseReceiveWritebackService purchaseReceiveWritebackService;
    private final WmMaterialReceiveMapper materialReceiveMapper;

    /**
     * 获取消息类型
     *
     * @return 消息类型
     */
    @Override
    protected Class<PurchaseReceiveWritebackMessage> getMessageClass() {
        return PurchaseReceiveWritebackMessage.class;
    }

    /**
     * 处理采购收货回写消息，根据收货ID查询入库单并回写采购订单已收货数量
     *
     * @param message 采购收货回写消息
     */
    @Override
    protected void doProcess(PurchaseReceiveWritebackMessage message) {
        Long receiveId = message.getReceiveId();
        log.info("处理采购收货回写消息: receiveId={}", receiveId);
        WmMaterialReceive receive = materialReceiveMapper.selectById(receiveId);
        if (receive == null) {
            log.warn("入库单不存在: receiveId={}", receiveId);
            return;
        }
        purchaseReceiveWritebackService.writeBackReceivedQty(receiveId, receive.getPurId());
    }

    /**
     * 监听采购收货队列，接收并处理消息
     *
     * @param message     采购收货回写消息
     * @param channel     RabbitMQ通道
     * @param amqpMessage AMQP原始消息
     * @throws Exception 处理异常
     */
    @RabbitListener(queues = QueueConstants.PURCHASE_RECEIVE_QUEUE)
    public void onMessage(PurchaseReceiveWritebackMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
