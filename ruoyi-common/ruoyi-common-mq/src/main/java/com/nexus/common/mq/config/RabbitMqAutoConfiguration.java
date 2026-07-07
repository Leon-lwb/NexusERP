package com.nexus.common.mq.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.mq.domain.MessageRecordMapper;
import com.nexus.common.mq.producer.MessageRecordService;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * RabbitMQ 自动配置
 *
 * @author Nexus
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(RabbitMqProperties.class)
public class RabbitMqAutoConfiguration {

    /** JSON 消息转换器 */
    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitMQ 监听器容器工厂，配置手动ACK和预取数量
     *
     * @param connectionFactory 连接工厂
     * @param messageConverter  消息转换器
     * @return 监听器容器工厂
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10);
        return factory;
    }

    /**
     * 消息记录持久化服务
     * <p>
     * 通过 @DS("master") + REQUIRES_NEW 在 master 库独立事务中写入 mq_message_record，
     * 避免在 ERP/MES 业务事务内发送 MQ 时，因连接已绑定业务库而报"表不存在"。
     *
     * @param messageRecordMapper 消息记录Mapper
     * @return 消息记录服务实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MessageRecordService messageRecordService(MessageRecordMapper messageRecordMapper) {
        return new MessageRecordService(messageRecordMapper);
    }

    /**
     * 可靠消息生产者
     *
     * @param rabbitTemplate       Rabbit模板
     * @param messageRecordService 消息记录持久化服务
     * @param objectMapper         JSON对象映射器
     * @return 可靠消息生产者实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ReliableMessageProducer reliableMessageProducer(RabbitTemplate rabbitTemplate,
            MessageRecordService messageRecordService, ObjectMapper objectMapper) {
        return new ReliableMessageProducer(rabbitTemplate, messageRecordService, objectMapper);
    }

    /**
     * 自定义 RabbitTemplate，配置消息确认回调和退回回调
     *
     * @param connectionFactory 连接工厂
     * @param messageConverter  消息转换器
     * @return 配置好的RabbitTemplate
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setMandatory(true);

        rabbitTemplate.setConfirmCallback((CorrelationData correlationData, boolean ack, String cause) -> {
            if (ack) {
                log.debug("消息发送确认成功: {}", correlationData != null ? correlationData.getId() : "unknown");
            } else {
                log.error("消息发送确认失败: {}, 原因: {}", correlationData != null ? correlationData.getId() : "unknown", cause);
            }
        });

        rabbitTemplate.setReturnsCallback((ReturnedMessage returned) -> {
            log.error("消息被退回: exchange={}, routingKey={}, replyText={}, message={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText(),
                new String(returned.getMessage().getBody()));
        });

        return rabbitTemplate;
    }
}
