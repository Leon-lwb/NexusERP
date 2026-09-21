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
 * <p>通道状态(MQ-01): 该队列无生产者(仅 RabbitMqInitializer 声明), 属预留/废弃通道, 保留监听仅为兼容
 * 既有 broker 拓扑; 委外收货完工的权威口径是
 * {@code PrdSubcontractClosureServiceImpl.accumulateActualQty} 的达量判定(ERP-PRD-05)。
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
     * 根据操作类型更新委外订单状态: ISSUE→IN_PROGRESS; RECEIVE 不再改状态(交达量判定, 见方法内注释)。
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
            // 口径统一(ERP-PRD-05): 委外「达量完工」的权威判定是
            // PrdSubcontractClosureServiceImpl.accumulateActualQty(累计实收 >= 计划量才置 COMPLETED)。
            // 原先「有收货即 COMPLETED」会让部分收货的委外单提前完工、尾量无处登记。
            // 该队列当前亦无生产者(仅 RabbitMqInitializer 声明), 故此处不再改状态, 仅告警留痕。
            log.warn("委外收货状态推进已统一由达量判定(accumulateActualQty)负责, 忽略队列消息: subcontractNo={}, status={}",
                subcontract.getSubcontractNo(), currentStatus);
            return;
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
