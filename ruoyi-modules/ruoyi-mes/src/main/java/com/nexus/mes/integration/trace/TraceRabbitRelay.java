package com.nexus.mes.integration.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.mq.constant.*;
import com.nexus.common.trace.MaterialTraceEvent;
import com.nexus.common.trace.port.TraceOutboxPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 领取与确认分别使用短来源事务，网络传输在业务行锁之外执行。
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "nexus.trace.relay.enabled", havingValue = "true")
public class TraceRabbitRelay {
    private final List<TraceOutboxPort> sources;
    private final ObjectMapper json;
    private final RabbitTemplate transport;
    private final String owner = UUID.randomUUID().toString();

    public TraceRabbitRelay(
            List<TraceOutboxPort> sources, ObjectMapper json, ConnectionFactory connectionFactory) {
        this.sources = List.copyOf(sources);
        this.json = json;
        this.transport = new RabbitTemplate(connectionFactory);
        this.transport.setMandatory(true);
    }

    @Scheduled(fixedDelayString = "${nexus.trace.relay.poll-ms:2000}")
    public void relay() {
        for (var source : sources) {
            try {
                for (var event : source.claim(owner, 20, 120)) {
                    publish(source, event);
                }
            } catch (RuntimeException e) {
                log.warn(
                        "追溯事件领取失败，模块={}，错误类型={}",
                        source.sourceModule(),
                        e.getClass().getSimpleName());
            }
        }
    }

    private void publish(TraceOutboxPort source, MaterialTraceEvent event) {
        try {
            if (!source.sourceModule().equals(event.sourceModule())) {
                throw new IllegalArgumentException("事件来源与存储不一致");
            }
            var properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setContentEncoding("UTF-8");
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setMessageId(event.eventId());
            properties.setType("material.trace.v1");
            var correlation = new CorrelationData(event.eventId());
            String routing =
                    source.sourceModule().equals("ERP")
                            ? RoutingKeyConstants.TRACE_TO_MES
                            : RoutingKeyConstants.TRACE_TO_ERP;
            transport.send(
                    ExchangeConstants.TRACE_EXCHANGE,
                    routing,
                    new Message(json.writeValueAsBytes(event), properties),
                    correlation);
            correlation
                    .getFuture()
                    .orTimeout(10, TimeUnit.SECONDS)
                    .whenComplete(
                            (confirmation, error) -> {
                                try {
                                    if (error != null
                                            || confirmation == null
                                            || !confirmation.isAck()
                                            || correlation.getReturned() != null) {
                                        source.retry(
                                                event.eventId(), owner, "BROKER_NOT_CONFIRMED");
                                    } else {
                                        source.brokerAccepted(event.eventId(), owner);
                                    }
                                } catch (RuntimeException e) {
                                    log.warn(
                                            "追溯发送确认未写回，事件={}，错误类型={}",
                                            event.eventId(),
                                            e.getClass().getSimpleName());
                                }
                            });
        } catch (Exception e) {
            source.retry(event.eventId(), owner, "PUBLISH_FAILED");
        }
    }
}
