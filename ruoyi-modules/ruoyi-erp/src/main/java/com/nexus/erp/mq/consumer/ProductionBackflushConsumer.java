package com.nexus.erp.mq.consumer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.erp.mq.message.ProductionCompleteMessage;
import com.nexus.erp.prd.service.ProductionCompletionPostingService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 生产完工消费者，异步执行倒冲领料并触发成品入库
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
@DS("erp")
public class ProductionBackflushConsumer extends BaseMessageConsumer<ProductionCompleteMessage> {

    private final ProductionCompletionPostingService postingService;

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
        // 独立服务代理先完成同 ERP 事务并提交，异常回滚后交消费模板 NACK。
        // 业务幂等只认可真实单据和库存流水，不提前占用 Redis 成功键。
        postingService.postCompletion(message);
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
