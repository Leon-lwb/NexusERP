package com.nexus.flow.node.notify;
import com.nexus.flow.node.FlowNodeProvider;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.node.NodeParamSchema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.flow.state.FlowTemplateRenderer;
import lombok.extern.slf4j.Slf4j;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * 短信发送节点执行器
 * <p>复用 sms4j {@link SmsFactory}（配置在 application.yml 的 sms 段，如 config1），
 * 手机号/模板参数支持 {{}} 模板渲染</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
public class SmsNodeProvider implements FlowNodeProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getNodeType() {
        return "sms";
    }

    @Override
    public String getDisplayName() {
        return "短信发送";
    }

    @Override
    public List<NodeParamSchema> getParamSchemas() {
        NodeParamSchema params = new NodeParamSchema("params", "模板参数", "json", "{}", false);
        params.setEditor("keyvalue");
        return List.of(
            new NodeParamSchema("phones", "手机号 (逗号分隔)", "string", "", true),
            new NodeParamSchema("templateId", "模板ID", "string", "", true),
            params,
            new NodeParamSchema("configId", "短信配置", "string", "config1", false)
        );
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config == null || config.get("phones") == null || config.get("phones").asText().isBlank()) {
            throw new IllegalArgumentException("短信节点缺少 phones 配置");
        }
        if (config.get("templateId") == null || config.get("templateId").asText().isBlank()) {
            throw new IllegalArgumentException("短信节点缺少 templateId 配置");
        }
    }

    @Override
    public JsonNode execute(FlowExecutionContext ctx) {
        JsonNode config = ctx.getConfig();
        String label = "节点 " + ctx.getNodeName();

        String phones = FlowTemplateRenderer.render(config.get("phones").asText(), ctx.getState(), label + " 的 phones");
        String templateId = FlowTemplateRenderer.render(config.get("templateId").asText(), ctx.getState(), label + " 的 templateId");
        String configId = config.has("configId") && !config.get("configId").isNull()
            ? FlowTemplateRenderer.render(config.get("configId").asText(), ctx.getState(), label + " 的 configId")
            : "config1";

        // 模板参数（JSON 对象，值支持 {{}} 渲染）
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        JsonNode paramsNode = config.get("params");
        if (paramsNode != null && paramsNode.isObject()) {
            var it = paramsNode.fields();
            while (it.hasNext()) {
                var entry = it.next();
                params.put(entry.getKey(), FlowTemplateRenderer.render(
                    entry.getValue() == null || entry.getValue().isNull() ? "" : entry.getValue().asText(),
                    ctx.getState(), label + " 的 params." + entry.getKey()));
            }
        }

        // 发送（sms4j）
        SmsBlend smsBlend = SmsFactory.getSmsBlend(configId);
        if (smsBlend == null) {
            throw new IllegalArgumentException("短信配置不存在: " + configId + "（检查 application.yml 的 sms 配置）");
        }
        SmsResponse smsResponse = smsBlend.sendMessage(phones, templateId, params);
        log.info("[Flow] 短信节点 {} 发送: phones={}, templateId={}, success={}",
            ctx.getNodeName(), phones, templateId, smsResponse != null && smsResponse.isSuccess());

        ObjectNode output = MAPPER.createObjectNode();
        output.put("sent", smsResponse != null && smsResponse.isSuccess());
        output.put("phones", phones);
        output.put("templateId", templateId);
        output.put("configId", configId);
        if (smsResponse != null) {
            // 厂商原返回体（成功时含 bizId/code/message 等，字段随厂商而异）
            if (smsResponse.getData() != null) {
                Object data = smsResponse.getData();
                if (data instanceof String s && !s.isBlank()) {
                    output.put("data", s);
                } else if (data instanceof Number || data instanceof Boolean) {
                    output.put("data", String.valueOf(data));
                } else {
                    try {
                        output.set("data", MAPPER.valueToTree(data));
                    } catch (Exception ignore) {
                        output.put("data", String.valueOf(data));
                    }
                }
            }
        }
        return output;
    }
}
