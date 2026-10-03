package com.nexus.mes.common.mq.consumer;

import cn.hutool.extra.spring.SpringUtil;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mq.message.ReceiveApprovedMessage;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.mes.qc.domain.vo.QcIqcVo;
import com.nexus.mes.qc.service.QcReceiptIqcBirthService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 实际投递入口回归；仅替换原生写服务及 Redis/Channel 边界，不连接任何外部服务。 */
class IqcAutoCreateConsumerDeliveryTest {
    private final QcReceiptIqcBirthService birth = mock(QcReceiptIqcBirthService.class);
    private final Channel channel = mock(Channel.class);
    private final IqcAutoCreateConsumer consumer = new IqcAutoCreateConsumer(birth);
    private final AtomicReference<String> context = new AtomicReference<>("000999");
    private MockedStatic<TenantHelper> tenant;
    private MockedStatic<RedisUtils> redis;
    private ReceiveApprovedMessage message;
    private Message delivery;

    @BeforeEach void arrange() {
        tenant = mockStatic(TenantHelper.class);
        // 静态工具初始化只注入客户端替身，不创建 Redisson 连接或 Spring 应用。
        try (var spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getBean(RedissonClient.class)).thenReturn(mock(RedissonClient.class));
            redis = mockStatic(RedisUtils.class);
        }
        tenant.when(TenantHelper::getTenantId).thenAnswer(i -> context.get());
        tenant.when(() -> TenantHelper.dynamic(anyString(), any(Runnable.class))).thenAnswer(i -> {
            String previous = context.getAndSet(i.getArgument(0));
            try { i.<Runnable>getArgument(1).run(); }
            finally { context.set(previous); }
            return null;
        });
        message = new ReceiveApprovedMessage();
        message.setMessageId("原生出生投递-101-201"); message.setTenantId(7L);
        message.setReceiveId(101L); message.setMaterialId(201L); message.setMaxRetry(3);
        MessageProperties properties = new MessageProperties(); properties.setDeliveryTag(81L);
        delivery = new Message(new byte[0], properties);
        QcIqcVo original = new QcIqcVo(); original.setIqcId(501L);
        when(birth.create("000007", 101L, 201L)).thenReturn(original);
    }

    @AfterEach void closeBoundaries() {
        try { if (redis != null) redis.close(); }
        finally { if (tenant != null) tenant.close(); }
    }

    private void cached(String state) {
        redis.when(() -> RedisUtils.getCacheObject("mq:consumed:" + message.getMessageId())).thenReturn(state);
        redis.when(() -> RedisUtils.setObjectIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(false);
    }

    @ParameterizedTest @ValueSource(strings = {"PENDING", "DONE"})
    void cachedStateCannotAcknowledgeUncommittedOrUnprovenNativeBirth(String state) throws Exception {
        cached(state);
        when(birth.create("000007", 101L, 201L)).thenThrow(new ServiceException("IQC_BIRTH_MES_WRITE_FAILED"));
        consumer.onMessage(message, channel, delivery);
        verify(birth).create("000007", 101L, 201L);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel).basicNack(81L, false, false);
        // 来源预检失败沿用隔离策略，不进入旧占位确认，也不删除其他实例的占位。
        redis.verifyNoInteractions();
        assertThat(context.get()).isEqualTo("000999");
    }

    @ParameterizedTest @ValueSource(strings = {"PENDING", "DONE"})
    void cachedStateMayAcknowledgeOnlyAfterNativeCreationOrSameBirthRecovery(String state) throws Exception {
        cached(state);
        consumer.onMessage(message, channel, delivery);
        var order = inOrder(birth, channel);
        order.verify(birth).create("000007", 101L, 201L);
        order.verify(channel).basicAck(81L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verifyNoMoreInteractions(birth);
        assertThat(context.get()).isEqualTo("000999");
    }

    @Test void uncachedDeliveryRechecksTheSamePermanentIdentityInDoProcess() throws Exception {
        redis.when(() -> RedisUtils.setObjectIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
        consumer.onMessage(message, channel, delivery);
        var order = inOrder(birth, channel);
        order.verify(birth, times(2)).create("000007", 101L, 201L);
        order.verify(channel).basicAck(81L, false);
        verifyNoMoreInteractions(birth);
        assertThat(context.get()).isEqualTo("000999");
    }

    @Test void missingTenantCannotHideBehindADoneCacheEntry() throws Exception {
        cached("DONE"); message.setTenantId(null);
        consumer.onMessage(message, channel, delivery);
        verifyNoInteractions(birth);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel).basicNack(81L, false, false);
        redis.verifyNoInteractions();
    }

    @Test void differentPermanentBindingIsIsolatedEvenWhenCacheSaysDone() throws Exception {
        cached("DONE");
        when(birth.create("000007", 101L, 201L)).thenThrow(new ServiceException("IQC_BIRTH_BINDING_INVALID"));
        consumer.onMessage(message, channel, delivery);
        verify(birth).create("000007", 101L, 201L);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel).basicNack(81L, false, false);
        redis.verifyNoInteractions();
    }

    @Test void untrustedPayloadFactsNeverReachNativePersistenceOnActualDelivery() throws Exception {
        cached("PENDING"); message.setQuantity(new BigDecimal("-999999999999.123456789"));
        message.setMaterialCode("伪造物料"); message.setMaterialName("伪造名称");
        message.setVendorId(999L); message.setVendorName("伪造供应商");
        consumer.onMessage(message, channel, delivery);
        verify(birth).create("000007", 101L, 201L); verifyNoMoreInteractions(birth);
        verify(channel).basicAck(81L, false);
    }

    @Test void secondNativeCheckFailureKeepsTheExistingBaseRetryPolicy() throws Exception {
        redis.when(() -> RedisUtils.setObjectIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
        redis.when(() -> RedisUtils.incrAtomicValue("mq:retry:" + message.getMessageId())).thenReturn(1L);
        when(birth.create("000007", 101L, 201L)).thenReturn(new QcIqcVo())
            .thenThrow(new ServiceException("IQC_BIRTH_BINDING_INVALID"));
        consumer.onMessage(message, channel, delivery);
        verify(birth, times(2)).create("000007", 101L, 201L);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel).basicNack(81L, false, true);
        redis.verify(() -> RedisUtils.deleteObject("mq:consumed:" + message.getMessageId()));
        assertThat(context.get()).isEqualTo("000999");
    }
}