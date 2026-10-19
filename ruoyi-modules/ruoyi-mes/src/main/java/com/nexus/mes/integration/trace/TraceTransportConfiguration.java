package com.nexus.mes.integration.trace;

import com.nexus.common.mq.constant.*;
import com.nexus.common.trace.TraceDeliveryCoordinator;
import com.nexus.common.trace.port.*;
import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.List;

/**
 * 仅在两侧业务库结构和兼容消费者均已部署后启用。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "nexus.trace.relay.enabled", havingValue = "true")
public class TraceTransportConfiguration {
    @Bean
    public Declarables materialTraceTopology() {
        var exchange = new DirectExchange(ExchangeConstants.TRACE_EXCHANGE, true, false);
        var deadExchange = new DirectExchange(ExchangeConstants.TRACE_DEAD_EXCHANGE, true, false);
        var erp =
                QueueBuilder.durable(QueueConstants.TRACE_ERP_QUEUE)
                        .deadLetterExchange(ExchangeConstants.TRACE_DEAD_EXCHANGE)
                        .deadLetterRoutingKey(RoutingKeyConstants.TRACE_DEAD)
                        .build();
        var mes =
                QueueBuilder.durable(QueueConstants.TRACE_MES_QUEUE)
                        .deadLetterExchange(ExchangeConstants.TRACE_DEAD_EXCHANGE)
                        .deadLetterRoutingKey(RoutingKeyConstants.TRACE_DEAD)
                        .build();
        var dead = QueueBuilder.durable(QueueConstants.TRACE_DEAD_QUEUE).build();
        return new Declarables(
                exchange,
                deadExchange,
                erp,
                mes,
                dead,
                BindingBuilder.bind(erp).to(exchange).with(RoutingKeyConstants.TRACE_TO_ERP),
                BindingBuilder.bind(mes).to(exchange).with(RoutingKeyConstants.TRACE_TO_MES),
                BindingBuilder.bind(dead).to(deadExchange).with(RoutingKeyConstants.TRACE_DEAD));
    }

    @Bean
    public TraceDeliveryCoordinator traceDeliveryCoordinator(
            List<TraceInboxPort> inboxes,
            List<TraceEventHandler> handlers,
            List<TraceSourceVerificationPort> verifiers) {
        return new TraceDeliveryCoordinator(inboxes, handlers, verifiers);
    }
}
