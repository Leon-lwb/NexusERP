package com.nexus.mes.integration.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.mq.constant.QueueConstants;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.common.trace.*;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Objects;

/**
 * 消费跨模块追溯事件，目标业务与接收记录提交后再确认消息传输。
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "nexus.trace.relay.enabled", havingValue = "true")
public class TraceRabbitConsumer {
    private final ObjectMapper json;
    private final TraceDeliveryCoordinator coordinator;

    @RabbitListener(queues = QueueConstants.TRACE_ERP_QUEUE)
    public void erp(Message message, Channel channel) throws IOException {
        receive("ERP", message, channel);
    }

    @RabbitListener(queues = QueueConstants.TRACE_MES_QUEUE)
    public void mes(Message message, Channel channel) throws IOException {
        receive("MES", message, channel);
    }

    void receive(String target, Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            if (message.getBody().length > 4 * 1024 * 1024) {
                throw new IllegalArgumentException("追溯载荷超限");
            }
            MaterialTraceEvent event = json.readValue(message.getBody(), MaterialTraceEvent.class);
            if (!event.tenantId().matches("[0-9]{6,20}")
                    || !Objects.equals(
                            event.eventId(), message.getMessageProperties().getMessageId())) {
                throw new IllegalArgumentException("追溯消息头与身份不一致");
            }
            TraceDeliveryResult result =
                    TenantHelper.dynamic(
                            event.tenantId(), () -> coordinator.deliver(target, event));
            // 等待状态先持久化；来源事件保持未确认，并持续重发直至目标成功应用。
            if (!result.applied() && !"WAITING".equals(result.status())) {
                throw new IllegalStateException("未知接收结果");
            }
        } catch (Exception e) {
            log.warn("追溯事件进入待处理队列，目标={}，错误类型={}", target, e.getClass().getSimpleName());
            channel.basicReject(tag, false);
            return;
        }
        // 接收代理方法已完成事务提交后，才确认消息传输成功。
        channel.basicAck(tag, false);
    }
}
