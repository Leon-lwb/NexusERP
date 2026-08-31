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

    /** 声明通用延迟/死信队列；业务队列由各业务模块自行声明 */
    private void declareQueuesAndBindings() {
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
