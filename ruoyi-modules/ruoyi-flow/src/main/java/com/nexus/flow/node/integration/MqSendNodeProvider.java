package com.nexus.flow.node.integration;
import com.nexus.flow.node.FlowNodeProvider;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.node.NodeParamSchema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.flow.state.FlowTemplateRenderer;
import com.nexus.common.mq.constant.ExchangeConstants;
import com.nexus.common.mq.constant.RoutingKeyConstants;
import com.nexus.common.mq.producer.ReliableMessageProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MQ 消息发送节点
 * <p>把渲染后的 payload 以 FlowMqMessage 投递到 ERP 消息总线（ExchangeConstants.TOPIC_EXCHANGE），
 * 复用 ReliableMessageProducer 可靠投递（落库+补偿）。路由键从 RoutingKeyConstants 反射生成下拉。</p>
 *
 * @author NexusERP
 */
@Component
@RequiredArgsConstructor
public class MqSendNodeProvider implements FlowNodeProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ReliableMessageProducer messageProducer;

    @Override
    public String getNodeType() {
        return "mq_send";
    }

    @Override
    public String getDisplayName() {
        return "MQ 消息发送";
    }

    @Override
    public List<NodeParamSchema> getParamSchemas() {
        // 路由键下拉：反射 RoutingKeyConstants 的静态字符串常量
        List<Map<String, String>> options = new ArrayList<>();
        for (Field f : RoutingKeyConstants.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                try {
                    String value = (String) f.get(null);
                    options.add(Map.of("name", f.getName() + "（" + value + "）", "value", value));
                } catch (IllegalAccessException ignore) {
                    // 忽略
                }
            }
        }
        NodeParamSchema routingKey = new NodeParamSchema("routingKey", "路由键", "options", "", true);
        routingKey.setOptions(options);
        return List.of(
            routingKey,
            new NodeParamSchema("payload", "消息体 (JSON 模板)", "textarea", "", true),
            new NodeParamSchema("exchange", "交换机", "string", ExchangeConstants.TOPIC_EXCHANGE, false)
        );
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config == null || config.get("routingKey") == null || config.get("routingKey").asText().isBlank()) {
            throw new IllegalArgumentException("MQ 节点缺少 routingKey 配置");
        }
        if (config.get("payload") == null || config.get("payload").asText().isBlank()) {
            throw new IllegalArgumentException("MQ 节点缺少 payload 配置");
        }
    }

    @Override
    public JsonNode execute(FlowExecutionContext ctx) throws Exception {
        JsonNode config = ctx.getConfig();
        String routingKey = config.get("routingKey").asText();
        String exchange = config.has("exchange") && !config.get("exchange").asText().isBlank()
            ? config.get("exchange").asText() : ExchangeConstants.TOPIC_EXCHANGE;
        String label = "节点 " + ctx.getNodeName();

        // 渲染 payload（支持 {{}} 变量引用）
        String payload = FlowTemplateRenderer.render(config.get("payload").asText(), ctx.getState(), label + " 的 payload");

        // 组装通用消息（messageId/traceId/timestamp 自动填充；租户在消费端从消息恢复）
        FlowMqMessage message = new FlowMqMessage();
        message.setMessageId(UUID.randomUUID().toString().replace("-", ""));
        message.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        message.setTimestamp(System.currentTimeMillis());
        message.setSourceModule("ai-flow");
        message.setPayload(payload);
        message.setFlowInstanceId(ctx.getInstanceId());
        message.setFlowNodeId(ctx.getNodeId());

        // 可靠投递（ReliableMessageProducer 内部落库 + 失败补偿）
        messageProducer.send(exchange, routingKey, message);

        ObjectNode output = MAPPER.createObjectNode();
        output.put("success", true);
        output.put("exchange", exchange);
        output.put("routingKey", routingKey);
        output.put("messageId", message.getMessageId());
        return output;
    }
}
