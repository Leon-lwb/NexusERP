package com.nexus.erp.mq.consumer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.erp.mq.message.PurchaseReceiveWritebackMessage;
import com.nexus.erp.pu.service.IPuPurchaseReceiveWritebackService;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.service.IWmMaterialReceiveService;
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
@DS("erp")
public class PurchaseReceiveWritebackConsumer extends BaseMessageConsumer<PurchaseReceiveWritebackMessage> {

    private final IPuPurchaseReceiveWritebackService purchaseReceiveWritebackService;
    private final IWmMaterialReceiveService materialReceiveService;

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
        doWriteback(receiveId);
    }

    /**
     * 解析租户ID：使用 tenantIdStr 避免 Long 精度丢失（如 "000000" → 0 → "0" 不匹配数据库）
     */
    @Override
    protected String resolveTenantId(PurchaseReceiveWritebackMessage message) {
        String tid = message.getTenantIdStr();
        return tid != null && !tid.isEmpty() ? tid : "000000";
    }

    /**
     * 执行采购收货回写逻辑
     *
     * @param receiveId 入库单ID
     */
    private void doWriteback(Long receiveId) {
        WmMaterialReceive receive = materialReceiveService.getById(receiveId);
        if (receive == null) {
            log.warn("入库单不存在: receiveId={}", receiveId);
            return;
        }
        log.info("查询到入库单: receiveId={}, purId={}, status={}", receiveId, receive.getPurId(), receive.getStatus());
        purchaseReceiveWritebackService.writeBackReceivedQty(receiveId, receive.getPurId());
        log.info("采购收货回写完成: receiveId={}, purId={}", receiveId, receive.getPurId());
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
