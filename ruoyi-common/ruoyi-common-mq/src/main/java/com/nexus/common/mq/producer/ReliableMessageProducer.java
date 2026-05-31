package com.nexus.common.mq.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.mq.domain.BaseMessage;
import com.nexus.common.mq.domain.MessageRecord;
import com.nexus.common.mq.domain.MessageRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Date;
import java.util.UUID;

/**
 * 可靠消息生产者，支持消息确认和延迟投递
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
public class ReliableMessageProducer {

    private final RabbitTemplate rabbitTemplate;
    private final MessageRecordMapper messageRecordMapper;
    private final ObjectMapper objectMapper;

    /**
     * 发送消息到指定交换机和路由键
     *
     * @param exchange   交换机名称
     * @param routingKey 路由键
     * @param message    消息对象
     */
    public void send(String exchange, String routingKey, BaseMessage message) {
        fillMessageDefaults(message);
        if (message.getMessageId() == null) {
            message.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        }
        CorrelationData correlationData = new CorrelationData(message.getMessageId());
        rabbitTemplate.convertAndSend(exchange, routingKey, message, msg -> {
            msg.getMessageProperties().setMessageId(message.getMessageId());
            msg.getMessageProperties().setHeader("x-tenant-id", message.getTenantId());
            msg.getMessageProperties().setHeader("x-source-module", message.getSourceModule());
            msg.getMessageProperties().setHeader("x-trace-id", message.getTraceId());
            return msg;
        }, correlationData);
        log.info("消息已发送: exchange={}, routingKey={}, messageId={}", exchange, routingKey, message.getMessageId());
        recordMessage(exchange, routingKey, message);
    }

    /**
     * 发送延迟消息
     *
     * @param exchange    交换机名称
     * @param routingKey  路由键
     * @param message     消息对象
     * @param delayMillis 延迟时间(毫秒)
     */
    public void sendDelay(String exchange, String routingKey, BaseMessage message, long delayMillis) {
        fillMessageDefaults(message);
        if (message.getMessageId() == null) {
            message.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        }
        CorrelationData correlationData = new CorrelationData(message.getMessageId());
        rabbitTemplate.convertAndSend(exchange, routingKey, message, msg -> {
            msg.getMessageProperties().setMessageId(message.getMessageId());
            msg.getMessageProperties().setHeader("x-tenant-id", message.getTenantId());
            msg.getMessageProperties().setHeader("x-source-module", message.getSourceModule());
            msg.getMessageProperties().setExpiration(String.valueOf(delayMillis));
            return msg;
        }, correlationData);
        log.info("延迟消息已发送: exchange={}, routingKey={}, messageId={}, delay={}ms", exchange, routingKey, message.getMessageId(), delayMillis);
        recordMessage(exchange, routingKey, message);
    }

    /**
     * 填充消息默认值
     *
     * @param message 消息对象
     */
    private void fillMessageDefaults(BaseMessage message) {
        if (message.getTimestamp() == null) {
            message.setTimestamp(System.currentTimeMillis());
        }
        if (message.getMaxRetry() == null) {
            message.setMaxRetry(3);
        }
        if (message.getRetryCount() == null) {
            message.setRetryCount(0);
        }
    }

    private void recordMessage(String exchange, String routingKey, BaseMessage message) {
        try {
            MessageRecord record = new MessageRecord();
            record.setMessageId(message.getMessageId());
            record.setExchange(exchange);
            record.setRoutingKey(routingKey);
            record.setMessageBody(objectMapper.writeValueAsString(message));
            record.setStatus("SENT");
            record.setRetryCount(0);
            record.setMaxRetry(message.getMaxRetry() != null ? message.getMaxRetry() : 3);
            record.setCreateTime(new Date());
            messageRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("消息记录保存失败: messageId={}, error={}", message.getMessageId(), e.getMessage());
        }
    }
}
