package com.nexus.common.mq.consumer;

import com.nexus.common.mq.domain.BaseMessage;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;

import java.io.IOException;
import java.time.Duration;

/**
 * 消息消费者基类，提供幂等消费、手动ACK和重试机制
 *
 * @param <T> 消息类型
 * @author Nexus
 */
public abstract class BaseMessageConsumer<T extends BaseMessage> {

    /** 日志(protected, 供子类直接使用) */
    protected static final Logger log = LoggerFactory.getLogger(BaseMessageConsumer.class);

    /** 消费幂等键前缀 */
    private static final String CONSUMED_KEY_PREFIX = "mq:consumed:";
    /** 消费重试计数键前缀 */
    private static final String RETRY_KEY_PREFIX = "mq:retry:";

    /**
     * 处理业务逻辑，子类实现。
     * <p>执行时已自动恢复租户上下文（由 {@link #resolveTenantId} 提供 tenantId）。</p>
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
     * 解析消息所属租户 ID。
     * <p>默认按 {@code BaseMessage.getTenantId()} 格式化为 6 位零填充字符串，
     * tenantId 为 null 或 0 时降级为 {@code "000000"}（系统租户）。</p>
     * <p>子类可覆盖以自定义租户解析逻辑，返回 {@code null} 表示不切换租户。</p>
     *
     * @param message 消息对象
     * @return 租户 ID 字符串，{@code null} 表示不切换租户
     */
    protected String resolveTenantId(T message) {
        Long tid = message.getTenantId();
        return (tid != null && tid != 0L) ? String.format("%06d", tid) : "000000";
    }

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

            // 从消息恢复租户上下文，包裹 doProcess 执行
            String tenantId = resolveTenantId(message);
            if (tenantId != null) {
                TenantHelper.dynamic(tenantId, () -> doProcess(message));
            } else {
                doProcess(message);
            }

            markConsumed(messageId);
            clearRetryCount(messageId);
            channel.basicAck(deliveryTag, false);
            log.info("消息消费成功: messageId={}", messageId);
        } catch (Exception e) {
            log.error("消息消费失败: messageId={}, error={}", messageId, e.getMessage(), e);
            int maxRetry = message.getMaxRetry() != null ? message.getMaxRetry() : 3;
            // 基于Redis计数器统计连续失败次数: basicNack(requeue=true) 不经过死信交换机,
            // x-death 头永远不会被Broker填充, 故无法据此判断重试次数(原实现恒为0导致无限循环)
            long retryCount = incrementRetryCount(messageId);
            if (retryCount >= maxRetry) {
                log.warn("消息连续失败达到最大重试次数，进入死信: messageId={}, retryCount={}", messageId, retryCount);
                channel.basicNack(deliveryTag, false, false);
            } else {
                log.warn("消息将重新入队重试: messageId={}, retryCount={}/{}", messageId, retryCount, maxRetry);
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
     * 递增消息重试次数(基于Redis原子计数器)
     * <p>
     * 替代 x-death 头方案: {@code basicNack(requeue=true)} 重新入队不经过死信交换机,
     * x-death 头不会被Broker填充, 无法据此统计重试次数。改用 Redis 计数器按 messageId 累计失败次数。
     *
     * @param messageId 消息ID
     * @return 当前累计失败次数(含本次)
     */
    private long incrementRetryCount(String messageId) {
        String key = RETRY_KEY_PREFIX + messageId;
        long count = RedisUtils.incrAtomicValue(key);
        RedisUtils.expire(key, Duration.ofHours(24));
        return count;
    }

    /**
     * 清除消息重试计数(消费成功后调用)
     *
     * @param messageId 消息ID
     */
    private void clearRetryCount(String messageId) {
        RedisUtils.deleteObject(RETRY_KEY_PREFIX + messageId);
    }
}
