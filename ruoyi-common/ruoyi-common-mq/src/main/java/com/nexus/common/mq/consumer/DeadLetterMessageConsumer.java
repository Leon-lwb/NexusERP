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
            log.error("收到死信消息: messageId={}, exchange={}, routingKey={}", messageId, exchange, routingKey);

            DeadLetterRecord record = new DeadLetterRecord();
            record.setMessageId(messageId);
            record.setExchange(exchange);
            record.setRoutingKey(routingKey);
            record.setMessageBody(body);
            record.setStatus("PENDING");
            record.setCreateTime(new Date());
            deadLetterRecordMapper.insert(record);

            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("死信消息处理失败: {}", e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
