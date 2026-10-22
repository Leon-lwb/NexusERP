package com.nexus.erp.wm.service.mq;

import static org.junit.jupiter.api.Assertions.*;

import com.rabbitmq.client.Channel;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** 仅涉及 AOP 和 AMQP 消息；不启动连接工厂、消息代理、数据库或服务。 */
@Tag("dev")
class MesSpareConsumerConfigurationTest {
    /** Rabbit 重试拦截器包装容器委托（Channel、Message），而非仅接收 Map 的业务方法。 */
    public interface ContainerInvocation {
        void invokeListener(Channel channel, Message message);
    }
    @Test
    void exhaustedAuditOutageRetriesThenPreservesMessageForRequeue() {
        AtomicInteger attempts = new AtomicInteger();
        ContainerInvocation target = (channel, message) -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("audit temporarily unavailable");
        };
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new MesSpareConsumerConfiguration().retryAdvice());
        ContainerInvocation listener = (ContainerInvocation) proxy.getProxy();
        assertThrows(ImmediateRequeueAmqpException.class, () -> listener.invokeListener(null, new Message(new byte[0])));
        assertEquals(3, attempts.get());
    }

    @Test
    void successfulAuditIsNotRepeatedAndMayCompleteNormally() {
        AtomicInteger attempts = new AtomicInteger();
        ContainerInvocation target = (channel, message) -> attempts.incrementAndGet();
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new MesSpareConsumerConfiguration().retryAdvice());
        ContainerInvocation listener = (ContainerInvocation) proxy.getProxy();
        assertDoesNotThrow(() -> listener.invokeListener(null, new Message(new byte[0])));
        assertEquals(1, attempts.get());
    }

    @Test
    void listenerFactoryIsNamedAndScopedWithoutReplacingGlobalFactory() throws Exception {
        var method =
                MesSpareConsumerConfiguration.class.getMethod(
                        "containerFactory",
                        ConnectionFactory.class,
                        MessageConverter.class,
                        RetryOperationsInterceptor.class);
        assertTrue(List.of(method.getAnnotation(Bean.class).value()).contains("mesSpareRabbitListenerContainerFactory"));
        assertFalse(List.of(method.getAnnotation(Bean.class).value()).contains("rabbitListenerContainerFactory"));
    }
}