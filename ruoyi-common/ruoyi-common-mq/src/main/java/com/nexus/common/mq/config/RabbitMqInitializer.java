package com.nexus.common.mq.config;

import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 交换机、队列及绑定关系声明器
 *
 * @author Nexus
 */
@RequiredArgsConstructor
@Configuration
public class RabbitMqInitializer {

    private final AmqpAdmin amqpAdmin;
    private final RabbitMqProperties properties;

    /** 应用启动时声明所有交换机、队列和绑定关系 */
    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            return;
        }
        declareExchanges();
        declareQueuesAndBindings();
    }

    /** 声明4个交换机: direct/topic/delay/dlx */
    private void declareExchanges() {
        amqpAdmin.declareExchange(new DirectExchange(ExchangeConstants.DIRECT_EXCHANGE, true, false));
        amqpAdmin.declareExchange(new TopicExchange(ExchangeConstants.TOPIC_EXCHANGE, true, false));
        amqpAdmin.declareExchange(new DirectExchange(ExchangeConstants.DELAY_EXCHANGE, true, false));
        amqpAdmin.declareExchange(new DirectExchange(ExchangeConstants.DLX_EXCHANGE, true, false));
    }

    /** 声明所有业务队列及绑定关系 */
    private void declareQueuesAndBindings() {
        // 使用TopicExchange绑定带通配符(#)的路由键，DirectExchange只支持精确匹配不支持通配符
        // 通道定性(MQ-01, 见 doc/audit/2026-09-20-fix-leftover.md): STOCK_OUT_QUEUE / VENDOR_EVALUATION_QUEUE /
        // STOCK_ALERT_QUEUE / SUBCONTRACT_STOCK_QUEUE / DELAY_QUEUE / EINVOICE_ISSUE_RESULT_QUEUE 均为
        // 「预留/未启用」通道 —— 全仓无生产者(convertAndSend/ReliableMessageProducer)、无 @RabbitListener 消费者,
        // 此处声明仅为保持既有 broker 拓扑稳定并便于后续接入, 不承载任何业务链路。
        declareQueueWithDLX(QueueConstants.STOCK_IN_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "stock.in.#");
        declareQueueWithDLX(QueueConstants.STOCK_OUT_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "stock.out.#");
        declareQueueWithDLX(QueueConstants.PURCHASE_RECEIVE_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "purchase.receive.#");
        declareQueueWithDLX(QueueConstants.PRODUCTION_COMPLETE_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "production.complete.#");
        declareQueueWithDLX(QueueConstants.VENDOR_EVALUATION_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "vendor.evaluation.#");
        declareQueueWithDLX(QueueConstants.SUBCONTRACT_STOCK_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "subcontract.stock.#");
        declareQueueWithDLX(QueueConstants.REWORK_STOCK_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "rework.stock.#");
        declareQueueWithDLX(QueueConstants.MES_WORKORDER_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "mes.workorder.#");

        // MES -> ERP 队列（Phase 3 使用，提前声明）
        declareQueueWithDLX(QueueConstants.ERP_WORKORDER_COMPLETED_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "erp.workorder.completed.#");
        declareQueueWithDLX(QueueConstants.ERP_IQC_COMPLETED_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "erp.iqc.completed.#");
        declareQueueWithDLX(QueueConstants.ERP_OQC_COMPLETED_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "erp.oqc.completed.#");
        declareQueueWithDLX(QueueConstants.ERP_IPQC_COMPLETED_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "erp.ipqc.completed.#");

        // ERP -> MES 队列：ERP 收货审批通过触发 MES 自动创建 IQC
        declareQueueWithDLX(QueueConstants.MES_IQC_CREATE_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "mes.iqc.#");
        // ERP -> MES 队列：ERP 销售出库审批通过触发 MES 自动创建 OQC
        declareQueueWithDLX(QueueConstants.MES_OQC_CREATE_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "mes.oqc.#");
        // ERP -> MES 队列：ERP 委外发料下达触发 MES 批量生成 PCBA SN
        declareQueueWithDLX(QueueConstants.MES_SUBCONTRACT_SN_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "mes.subcontract.#");

        declareQueueWithDLX(QueueConstants.STOCK_ALERT_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "stock.alert.#");

        // MES 设备状态变更队列(设备状态变更广播, 供排产/工单模块异步感知)
        declareQueueWithDLX(QueueConstants.MES_EQUIPMENT_STATUS_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "mes.equipment.#");

        // GL 财务过账队列(业务单据事件 -> AccountingPoster 异步制单, fin.gl)
        declareQueueWithDLX(QueueConstants.GL_POSTING_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "gl.posting.#");
        // 支付收款通知队列(pay -> ERP, 跨库, F1 AR/AP 用)
        declareQueueWithDLX(QueueConstants.PAY_RECEIVED_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "pay.received.#");
        // 电票开具结果队列(电子发票平台回调, F7 用)
        declareQueueWithDLX(QueueConstants.EINVOICE_ISSUE_RESULT_QUEUE, ExchangeConstants.TOPIC_EXCHANGE, "einvoice.issue.result.#");

        declareDelayQueue();
        declareDlxQueue();
    }

    /**
     * 声明带死信配置的队列及绑定
     *
     * @param queueName      队列名称
     * @param exchange       交换机名称
     * @param routingPattern 路由模式
     */
    private void declareQueueWithDLX(String queueName, String exchange, String routingPattern) {
        Map<String, Object> args = new HashMap<>(4);
        args.put("x-dead-letter-exchange", ExchangeConstants.DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", QueueConstants.DLX_QUEUE);
        Queue queue = QueueBuilder.durable(queueName).withArguments(args).build();
        amqpAdmin.declareQueue(queue);
        Binding binding = new Binding(queueName, Binding.DestinationType.QUEUE, exchange, routingPattern, null);
        amqpAdmin.declareBinding(binding);
    }

    /** 声明延迟队列 */
    private void declareDelayQueue() {
        Map<String, Object> args = new HashMap<>(4);
        args.put("x-dead-letter-exchange", ExchangeConstants.DIRECT_EXCHANGE);
        args.put("x-dead-letter-routing-key", RoutingKeyConstants.DELAY_ORDER_TIMEOUT);
        Queue delayQueue = QueueBuilder.durable(QueueConstants.DELAY_QUEUE).withArguments(args).build();
        amqpAdmin.declareQueue(delayQueue);
        Binding binding = new Binding(QueueConstants.DELAY_QUEUE, Binding.DestinationType.QUEUE,
            ExchangeConstants.DELAY_EXCHANGE, "", null);
        amqpAdmin.declareBinding(binding);
    }

    /** 声明死信队列 */
    private void declareDlxQueue() {
        Queue dlxQueue = QueueBuilder.durable(QueueConstants.DLX_QUEUE).build();
        amqpAdmin.declareQueue(dlxQueue);
        Binding binding = new Binding(QueueConstants.DLX_QUEUE, Binding.DestinationType.QUEUE,
            ExchangeConstants.DLX_EXCHANGE, QueueConstants.DLX_QUEUE, null);
        amqpAdmin.declareBinding(binding);
    }
}
