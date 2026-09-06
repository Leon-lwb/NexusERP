package com.nexus.common.mq.consumer;

import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.mq.domain.DeadLetterRecord;
import com.nexus.common.mq.domain.DeadLetterRecordMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 死信队列消费者，将死信消息持久化到数据库
 *
 * @author Nexus
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class DeadLetterMessageConsumer {

    private final DeadLetterRecordMapper deadLetterRecordMapper;

    /**
     * 处理死信消息，记录到mq_dead_letter_record表
     *
     * @param message AMQP消息
     * @param channel RabbitMQ通道
     * @throws IOException IO异常
     */
    @RabbitListener(queues = QueueConstants.DLX_QUEUE)
    public void handleDeadLetter(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            String messageId = message.getMessageProperties().getMessageId();
            String exchange = message.getMessageProperties().getReceivedExchange();
            String routingKey = message.getMessageProperties().getReceivedRoutingKey();
            String body = new String(message.getBody());
            // __TypeId__ 头由 Jackson2JsonMessageConverter 写入, 记录后供死信管理人工重投反序列化
            String messageType = message.getMessageProperties().getHeader("__TypeId__");
            String deathReason = extractDeathReason(message);
            log.error("收到死信消息: messageId={}, exchange={}, routingKey={}, reason={}",
                messageId, exchange, routingKey, deathReason);

            DeadLetterRecord record = new DeadLetterRecord();
            record.setMessageId(messageId);
            record.setExchange(exchange);
            record.setRoutingKey(routingKey);
            record.setMessageBody(body);
            record.setMessageType(messageType);
            record.setErrorMessage(deathReason);
            record.setStatus("PENDING");
            record.setCreateTime(new Date());
            deadLetterRecordMapper.insert(record);

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("死信消息处理失败: {}", e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /** 提取 x-death 头中的死信原因(rejected/expired/maxlen), 无则返回空 */
    private String extractDeathReason(Message message) {
        List<Map<String, ?>> xDeath = message.getMessageProperties().getHeader("x-death");
        if (xDeath == null || xDeath.isEmpty()) {
            return null;
        }
        Object reason = xDeath.get(0).get("reason");
        return reason == null ? null : reason.toString();
    }
}
