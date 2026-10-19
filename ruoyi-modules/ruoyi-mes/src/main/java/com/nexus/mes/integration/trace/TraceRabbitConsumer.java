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

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name="nexus.trace.relay.enabled",havingValue="true")
public class TraceRabbitConsumer {
    private final ObjectMapper json;
    private final TraceDeliveryCoordinator coordinator;
    @RabbitListener(queues=QueueConstants.TRACE_ERP_QUEUE)
    public void erp(Message message,Channel channel) throws IOException { receive("ERP",message,channel); }
    @RabbitListener(queues=QueueConstants.TRACE_MES_QUEUE)
    public void mes(Message message,Channel channel) throws IOException { receive("MES",message,channel); }
    void receive(String target,Message message,Channel channel) throws IOException {
        long tag=message.getMessageProperties().getDeliveryTag();
        try {
            if(message.getBody().length>4*1024*1024) throw new IllegalArgumentException("追溯载荷超限");
            MaterialTraceEvent event=json.readValue(message.getBody(),MaterialTraceEvent.class);
            if(!event.tenantId().matches("[0-9]{6,20}") || !Objects.equals(event.eventId(),message.getMessageProperties().getMessageId()))
                throw new IllegalArgumentException("追溯消息头与身份不一致");
            TraceDeliveryResult result=TenantHelper.dynamic(event.tenantId(),()->coordinator.deliver(target,event));
            // WAITING is durably recorded. The source outbox remains unacknowledged and republishes until applied.
            if(!result.applied() && !"WAITING".equals(result.status())) throw new IllegalStateException("未知接收结果");
        } catch(Exception e) {
            log.warn("追溯事件进入待处理队列，目标={}，错误类型={}",target,e.getClass().getSimpleName());
            channel.basicReject(tag,false);return;
        }
        // The proxied inbox has committed before the broker acknowledgement.
        channel.basicAck(tag,false);
    }
}
