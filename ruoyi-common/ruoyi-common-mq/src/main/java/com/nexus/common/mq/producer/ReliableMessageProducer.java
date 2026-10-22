package com.nexus.common.mq.producer;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.domain.BaseMessage;
import com.nexus.common.mq.domain.MessageRecord;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 可靠消息生产者，支持消息确认和延迟投递
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@DS("master")
public class ReliableMessageProducer {

    private final RabbitTemplate rabbitTemplate;
    private final MessageRecordService messageRecordService;
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
        // 先记录消息为 SENDING 状态，再发送 MQ；发送成功后更新为 SENT
        recordMessage(exchange, routingKey, message, "SENDING");
        CorrelationData correlationData = new CorrelationData(message.getMessageId());
        rabbitTemplate.convertAndSend(exchange, routingKey, message, msg -> {
            msg.getMessageProperties().setMessageId(message.getMessageId());
            msg.getMessageProperties().setHeader("x-tenant-id", message.getTenantId());
            msg.getMessageProperties().setHeader("x-source-module", message.getSourceModule());
            msg.getMessageProperties().setHeader("x-trace-id", message.getTraceId());
            return msg;
        }, correlationData);
        updateMessageStatus(message.getMessageId(), "SENT");
        log.info("消息已发送: exchange={}, routingKey={}, messageId={}", exchange, routingKey, message.getMessageId());
    }

    /**
     * 新精确采购通知：主库真实持久化、收到关联确认（ACK）且未被退回后，才返回已接受。
     * 调用方已提交 ERP；失败保留 ERP 待通知，绝不将日志或裸 convertAndSend 当作可靠接受。
     * 旧 send 行为保留，避免未经回归改变所有业务消息协议。
     */
    public void sendDurable(String exchange, String routingKey, BaseMessage message) {
        if (message == null
                || exchange == null
                || exchange.isBlank()
                || routingKey == null
                || routingKey.isBlank()
                || TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.getResourceMap().isEmpty()) {
            throw new ServiceException("可靠通知必须在业务事务提交后发送");
        }
        try {
            var factory=rabbitTemplate.getConnectionFactory();
            if(factory==null || !factory.isPublisherConfirms() || factory.isSimplePublisherConfirms() || !factory.isPublisherReturns()) {
                throw new ServiceException("可靠通知确认/退回配置尚未验证");
            }
            fillMessageDefaults(message);
            if (message.getMessageId() == null) message.setMessageId(UUID.randomUUID().toString().replace("-", ""));
            MessageRecord record = new MessageRecord();
            record.setMessageId(message.getMessageId()); record.setExchange(exchange); record.setRoutingKey(routingKey);
            record.setMessageBody(objectMapper.writeValueAsString(message)); record.setMessageType(message.getClass().getName());
            record.setStatus("SENDING"); record.setRetryCount(0); record.setMaxRetry(message.getMaxRetry()); record.setCreateTime(new Date());
            messageRecordService.saveSendingStrict(record);
            CorrelationData correlation = new CorrelationData(message.getMessageId());
            rabbitTemplate.convertAndSend(
                    exchange,
                    routingKey,
                    message,
                    raw -> {
                        if (!Boolean.TRUE.equals(rabbitTemplate.isMandatoryFor(raw))) {
                            throw new ServiceException("可靠通知必须启用退回路由验证");
                        }
                        raw.getMessageProperties().setMessageId(message.getMessageId());
                        raw.getMessageProperties().setHeader("x-tenant-id", message.getTenantId());
                        raw.getMessageProperties()
                                .setHeader("x-source-module", message.getSourceModule());
                        raw.getMessageProperties().setHeader("x-trace-id", message.getTraceId());
                        return raw;
                    },
                    correlation);
            CorrelationData.Confirm confirmation = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (confirmation == null || !confirmation.isAck() || correlation.getReturned() != null) {
                throw new ServiceException("可靠通知未获得路由确认");
            }
            messageRecordService.updateStatusStrict(message.getMessageId(), "SENT");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ServiceException("可靠通知等待已中断，可恢复重试");
        } catch (Exception failure) {
            throw new ServiceException("可靠通知尚未确认，可恢复重试");
        }
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
        recordMessage(exchange, routingKey, message, "SENDING");
        CorrelationData correlationData = new CorrelationData(message.getMessageId());
        rabbitTemplate.convertAndSend(exchange, routingKey, message, msg -> {
            msg.getMessageProperties().setMessageId(message.getMessageId());
            msg.getMessageProperties().setHeader("x-tenant-id", message.getTenantId());
            msg.getMessageProperties().setHeader("x-source-module", message.getSourceModule());
            msg.getMessageProperties().setExpiration(String.valueOf(delayMillis));
            return msg;
        }, correlationData);
        updateMessageStatus(message.getMessageId(), "SENT");
        log.info("延迟消息已发送: exchange={}, routingKey={}, messageId={}, delay={}ms", exchange, routingKey, message.getMessageId(), delayMillis);
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

    private void recordMessage(String exchange, String routingKey, BaseMessage message, String status) {
        try {
            MessageRecord record = new MessageRecord();
            record.setMessageId(message.getMessageId());
            record.setExchange(exchange);
            record.setRoutingKey(routingKey);
            record.setMessageBody(objectMapper.writeValueAsString(message));
            // 记录消息类型全限定名: 补偿任务重投时按此类型化反序列化(抽象基类无法反序列化)
            record.setMessageType(message.getClass().getName());
            record.setStatus(status);
            record.setRetryCount(0);
            record.setMaxRetry(message.getMaxRetry() != null ? message.getMaxRetry() : 3);
            record.setCreateTime(new Date());
            messageRecordService.saveSending(record);
        } catch (Exception e) {
            log.error("消息记录保存失败: messageId={}, error={}", message.getMessageId(), e.getMessage());
        }
    }

    private void updateMessageStatus(String messageId, String status) {
        try {
            messageRecordService.updateStatus(messageId, status);
        } catch (Exception e) {
            log.error("消息状态更新失败: messageId={}, status={}, error={}", messageId, status, e.getMessage());
        }
    }
}
