package com.nexus.erp.mq.consumer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.erp.mq.message.PurchaseReceiveWritebackMessage;
import com.nexus.erp.mq.message.StockInMessage;
import com.nexus.erp.wm.service.IWmStockOperationService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 入库消息消费者，按来源类型分发处理入库过账逻辑
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
@DS("erp")
public class StockInConsumer extends BaseMessageConsumer<StockInMessage> {

    private final IWmStockOperationService stockOperationService;
    private final ReliableMessageProducer messageProducer;

    /**
     * 获取消息类型
     *
     * @return StockInMessage类型
     */
    @Override
    protected Class<StockInMessage> getMessageClass() {
        return StockInMessage.class;
    }

    /**
     * 处理入库消息，按sourceType分发: PURCHASE→采购入库+回写, PRODUCTION→生产入库, REWORK→返工入库
     *
     * @param message 入库消息
     */
    @Override
    protected void doProcess(StockInMessage message) {
        String sourceType = message.getSourceType();
        log.info("处理入库消息: receiveId={}, sourceType={}", message.getReceiveId(), sourceType);
        if ("PURCHASE".equals(sourceType)) {
            stockOperationService.postMaterialReceive(message.getReceiveId());
            PurchaseReceiveWritebackMessage writebackMsg = new PurchaseReceiveWritebackMessage();
            writebackMsg.setReceiveId(message.getReceiveId());
            writebackMsg.setSourceModule("wm");
            writebackMsg.setTenantId(message.getTenantId());
            // 同步设置 tenantIdStr（String），否则消费端取不到租户降级为无租户上下文执行，回写脱离租户隔离
            if (message.getTenantId() != null && !Long.valueOf(0L).equals(message.getTenantId())) {
                writebackMsg.setTenantIdStr(String.format("%06d", message.getTenantId()));
            } else {
                writebackMsg.setTenantIdStr("000000");
            }
            messageProducer.send(ExchangeConstants.TOPIC_EXCHANGE,
                RoutingKeyConstants.PURCHASE_RECEIVE_WRITEBACK, writebackMsg);
        } else {
            log.warn("不支持的入库来源类型: sourceType={}, receiveId={}", sourceType, message.getReceiveId());
        }
    }

    /**
     * 监听入库队列消息
     *
     * @param message     入库消息
     * @param channel     RabbitMQ通道
     * @param amqpMessage AMQP消息
     * @throws Exception 异常
     */
    @RabbitListener(queues = QueueConstants.STOCK_IN_QUEUE)
    public void onMessage(StockInMessage message, Channel channel, Message amqpMessage) throws Exception {
        processMessage(message, channel, amqpMessage);
    }
}
