package com.nexus.common.mq.consumer;

import com.nexus.common.mq.domain.BaseMessage;
import com.nexus.common.redis.utils.RedisUtils;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 消息消费者基类，提供幂等消费、手动ACK和重试机制
 *
 * @param <T> 消息类型
 * @author Nexus
 */
@Slf4j
public abstract class BaseMessageConsumer<T extends BaseMessage> {

    /** 消费幂等键前缀 */
    private static final String CONSUMED_KEY_PREFIX = "mq:consumed:";

    /**
     * 处理业务逻辑，子类实现
     *
     * @param message 消息对象
     */
    protected abstract void doProcess(T message);

    /**
     * 获取消息类型Class对象
     *
     * @return 消息类型
     */
    protected abstract Class<T> getMessageClass();

    /**
     * 消息消费模板方法，包含幂等检查、业务处理、ACK/NACK
     *
     * @param message     消息对象
     * @param channel     RabbitMQ通道
     * @param amqpMessage AMQP消息
     * @throws IOException IO异常
     */
    public void processMessage(T message, Channel channel, Message amqpMessage) throws IOException {
        long deliveryTag = amqpMessage.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageId();
        try {
            if (isConsumed(messageId)) {
                log.info("消息已消费，跳过: messageId={}", messageId);
                channel.basicAck(deliveryTag, false);
                return;
            }
            log.info("开始消费消息: messageId={}, sourceModule={}", messageId, message.getSourceModule());
            doProcess(message);
            markConsumed(messageId);
            channel.basicAck(deliveryTag, false);
            log.info("消息消费成功: messageId={}", messageId);
        } catch (Exception e) {
            log.error("消息消费失败: messageId={}, error={}", messageId, e.getMessage(), e);
            int retryCount = getRetryCountFromHeaders(amqpMessage);
            int maxRetry = message.getMaxRetry() != null ? message.getMaxRetry() : 3;
            if (retryCount >= maxRetry) {
                log.warn("消息超过最大重试次数，进入死信: messageId={}, retryCount={}", messageId, retryCount);
                channel.basicNack(deliveryTag, false, false);
            } else {
                channel.basicNack(deliveryTag, false, true);
            }
        }
    }

    /**
     * 检查消息是否已被消费
     *
     * @param messageId 消息ID
     * @return 是否已消费
     */
    private boolean isConsumed(String messageId) {
        String key = CONSUMED_KEY_PREFIX + messageId;
        return Boolean.TRUE.equals(RedisUtils.hasKey(key));
    }

    /**
     * 标记消息已消费
     *
     * @param messageId 消息ID
     */
    private void markConsumed(String messageId) {
        String key = CONSUMED_KEY_PREFIX + messageId;
        RedisUtils.setCacheObject(key, "1", Duration.ofHours(24));
    }

    /**
     * 从AMQP消息头中获取x-death重试次数
     *
     * @param amqpMessage AMQP消息
     * @return 重试次数
     */
    private int getRetryCountFromHeaders(Message amqpMessage) {
        List<Map<String, ?>> xDeath = amqpMessage.getMessageProperties().getXDeathHeader();
        if (xDeath == null || xDeath.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Map<String, ?> death : xDeath) {
            Object countObj = death.get("count");
            if (countObj instanceof Long) {
                count += ((Long) countObj).intValue();
            } else if (countObj instanceof Integer) {
                count += (Integer) countObj;
            }
        }
        return count;
    }
}
