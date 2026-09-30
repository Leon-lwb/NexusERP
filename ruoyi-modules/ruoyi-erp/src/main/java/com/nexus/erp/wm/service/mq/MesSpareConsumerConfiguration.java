package com.nexus.erp.wm.service.mq;

import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

/**
 * 此仅接收 Map 的消费者不能使用共享的 MANUAL 确认工厂。
 * 审计不可用时先按退避策略重试，再将消息代理中的原始消息重新入队；
 * 不得通过拒绝且不重新入队，或假定存在死信路由来丢弃消息。
 */
@Configuration(proxyBeanMethods = false)
public class MesSpareConsumerConfiguration {

    @Bean("mesSpareConsumerRetryAdvice")
    public RetryOperationsInterceptor retryAdvice() {
        return RetryInterceptorBuilder.stateless()
            .maxAttempts(3)
            .backOffOptions(1_000L, 2.0, 5_000L)
            .recoverer((message, failure) -> {
                throw new ImmediateRequeueAmqpException("MES备件消息暂不可安全处理，原消息须保留重投，不得静默丢弃", failure);
            })
            .build();
    }

    @Bean("mesSpareRabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory containerFactory(
        ConnectionFactory connectionFactory, MessageConverter messageConverter,
        @Qualifier("mesSpareConsumerRetryAdvice") RetryOperationsInterceptor retryAdvice) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setPrefetchCount(10);
        factory.setAdviceChain(retryAdvice);
        return factory;
    }
}