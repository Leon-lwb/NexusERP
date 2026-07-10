package com.nexus.erp.mq.consumer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.consumer.BaseMessageConsumer;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.mq.message.ProductionCompleteMessage;
import com.nexus.erp.prd.service.impl.PrdBackflushServiceImpl;
import com.nexus.erp.wm.domain.vo.PutawayLocation;
import com.nexus.erp.wm.service.IPutawayStrategyService;
import com.nexus.erp.wm.service.IWmProductionRecptService;
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
@DS("erp")
public class ProductionBackflushConsumer extends BaseMessageConsumer<ProductionCompleteMessage> {

    private final PrdBackflushServiceImpl backflushService;
    private final ReliableMessageProducer messageProducer;
    private final IWmProductionRecptService productionRecptService;
    private final IPutawayStrategyService putawayStrategyService;

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
        // 规则驱动解析成品入库仓库与库位（替换原车间ID当仓库的写法）
        PutawayLocation putaway = putawayStrategyService.resolvePutaway(message.getMaterialId(), message.getCompletionQty());
        // 生成生产入库单据并过账（保留流程单据痕迹，避免直接改库存导致单据缺失）
        productionRecptService.createAndExecute(
            message.getWoId(), message.getWoNo(), message.getMaterialId(), message.getMaterialCode(), message.getMaterialName(),
            null, message.getCompletionQty(),
            putaway.getWarehouseId(), putaway.getWarehouseCode(), putaway.getWarehouseName(),
            putaway.getLocationId(), putaway.getLocationCode(), putaway.getLocationName(),
            new Date());
        log.info("完工入库过账完成: woId={}, 仓库={}, 库位={}", message.getWoId(), putaway.getWarehouseCode(), putaway.getLocationCode());
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
        // MQ消费者线程无登录态，需从消息恢复租户上下文
        String tenantId = "000000";
        if (message.getTenantId() != null && !Long.valueOf(0L).equals(message.getTenantId())) {
            tenantId = String.format("%06d", message.getTenantId());
        }
        TenantHelper.dynamic(tenantId, () -> {
            try {
                processMessage(message, channel, amqpMessage);
            } catch (Exception e) {
                log.error("处理完工倒冲消息异常: woId={}", message.getWoId(), e);
            }
        });
    }
}
