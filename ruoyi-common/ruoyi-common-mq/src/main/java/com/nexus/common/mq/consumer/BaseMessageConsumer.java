package com.nexus.common.mq.consumer;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
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
    /** 幂等状态: 已成功消费 */
    private static final String STATE_DONE = "DONE";
    /** 幂等状态: 正在消费(占位) */
    private static final String STATE_PENDING = "PENDING";

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
     * <p>消息携带 tenantId 时按 6 位零填充格式化 —— {@code 0} 即系统租户 {@code "000000"}
     * ({@code BaseMessage.tenantId} 为 {@code Long}, 无法保留前导零, 故 0 是默认租户的合法编码)。
     * <b>只有 {@code null}(生产端从未 setTenantId)才算“缺失”</b>：缺失时不再静默降级为
     * {@code "000000"}(系统租户)，先交由 {@link #resolveMissingTenantId} 回退解析，
     * 仍无法解析则抛 {@link ServiceException}，由 {@link #processMessage} 捕获后重投/进死信
     * (fail-closed) —— 修复前缺租户会落到系统租户，导致跨模块写库数据错位且桥接查 ERP 主数据全空
     * (MES-MQ-01)。</p>
     * <p>子类可覆盖以自定义租户解析逻辑，返回 {@code null} 表示不切换租户。</p>
     *
     * @param message 消息对象
     * @return 租户 ID 字符串，{@code null} 表示不切换租户
     */
    protected String resolveTenantId(T message) {
        Long tid = message.getTenantId();
        if (tid != null) {
            // 显式携带即视为权威值(0 -> "000000"): 避免默认租户消息被误判为缺失而进死信
            return String.format("%06d", tid);
        }
        String fallback = resolveMissingTenantId(message);
        if (StringUtils.isNotBlank(fallback)) {
            log.warn("消息未携带 tenantId, 已按回退策略解析: messageId={}, tenantId={}",
                message.getMessageId(), fallback);
            return fallback;
        }
        throw new ServiceException(String.format(
            "消息缺少租户信息且无法回退解析, 拒绝以系统租户(000000)消费: messageId=%s, messageType=%s",
            message.getMessageId(), message.getClass().getSimpleName()));
    }

    /**
     * 消息缺失 tenantId 时的回退解析(如按业务单号反查真实租户)。
     * <p>返回 {@code null} 或空串表示无法解析，将由 {@link #resolveTenantId} 显式失败(fail-closed)。</p>
     *
     * @param message 消息对象
     * @return 回退解析出的租户 ID，无法解析时返回 {@code null}
     */
    protected String resolveMissingTenantId(T message) {
        return null;
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
            // 消费幂等(原子): 处理前 setObjectIfAbsent 占位, 抢占失败即视为"已在消费/已消费"。
            // 原实现 hasKey -> 业务处理 -> 事后 setCacheObject 非原子, 双实例并发可建双工单(MES-MQ-02)
            if (!tryMarkConsuming(messageId)) {
                log.info("消息已在消费中或已消费，跳过: messageId={}", messageId);
                channel.basicAck(deliveryTag, false);
                return;
            }
            log.info("开始消费消息: messageId={}, sourceModule={}", messageId, message.getSourceModule());

            try {
                // 从消息恢复租户上下文，包裹 doProcess 执行
                String tenantId = resolveTenantId(message);
                if (tenantId != null) {
                    TenantHelper.dynamic(tenantId, () -> doProcess(message));
                } else {
                    doProcess(message);
                }
            } catch (RuntimeException e) {
                // 处理失败释放占位键, 否则重投在占位窗口内会被误判为"已消费"
                releaseConsuming(messageId);
                throw e;
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
     * 尝试以原子方式占位"正在消费"
     * <p>已置 DONE 或已被其他实例占位时返回 {@code false}(视为重复消费)。</p>
     *
     * @param messageId 消息ID
     * @return 是否抢占成功(即本次为唯一消费者)
     */
    private boolean tryMarkConsuming(String messageId) {
        String key = CONSUMED_KEY_PREFIX + messageId;
        Object state = RedisUtils.getCacheObject(key);
        if (STATE_DONE.equals(state)) {
            return false;
        }
        // 占位窗口短于 DONE 窗口: 实例崩溃后占位可自行过期, 重投仍能重新消费
        return RedisUtils.setObjectIfAbsent(key, STATE_PENDING, Duration.ofMinutes(30));
    }

    /**
     * 标记消息已消费
     *
     * @param messageId 消息ID
     */
    private void markConsumed(String messageId) {
        String key = CONSUMED_KEY_PREFIX + messageId;
        RedisUtils.setCacheObject(key, STATE_DONE, Duration.ofHours(24));
    }

    /**
     * 释放消费占位(业务处理失败时调用, 允许重投重新消费)
     *
     * @param messageId 消息ID
     */
    private void releaseConsuming(String messageId) {
        RedisUtils.deleteObject(CONSUMED_KEY_PREFIX + messageId);
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
