package com.nexus.flow.node.notify;
import com.nexus.flow.node.FlowNodeProvider;
import com.nexus.flow.node.FlowExecutionContext;
import com.nexus.flow.node.NodeParamSchema;
import com.nexus.flow.node.integration.HttpNodeProvider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.flow.state.FlowTemplateRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * IM 群机器人通知节点执行器（企业微信/钉钉/飞书 webhook）
 * <p>向机器人 webhook URL POST 消息（markdown 或文本）。
 * 复用 {@link HttpNodeProvider#sanitizeUrl} 的 SSRF 防护口径（webhook URL 必须为公网 http/https）</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
public class ImWebhookNodeProvider implements FlowNodeProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getNodeType() {
        return "im_webhook";
    }

    @Override
    public String getDisplayName() {
        return "IM 群机器人";
    }

    @Override
    public List<NodeParamSchema> getParamSchemas() {
        NodeParamSchema channel = new NodeParamSchema("channel", "平台", "options", "wecom", true);
        channel.setOptions(List.of(
            Map.of("name", "企业微信", "value", "wecom"),
            Map.of("name", "钉钉", "value", "dingtalk"),
            Map.of("name", "飞书", "value", "feishu")
        ));
        return List.of(
            channel,
            new NodeParamSchema("webhookUrl", "机器人 Webhook URL", "string", "", true),
            new NodeParamSchema("content", "消息内容", "textarea", "", true),
            new NodeParamSchema("timeoutSeconds", "超时(秒)", "number", "10", false)
        );
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config == null || config.get("webhookUrl") == null || config.get("webhookUrl").asText().isBlank()) {
            throw new IllegalArgumentException("IM 机器人节点缺少 webhookUrl 配置");
        }
        if (config.get("content") == null || config.get("content").asText().isBlank()) {
            throw new IllegalArgumentException("IM 机器人节点缺少 content 配置");
        }
    }

    @Override
    public JsonNode execute(FlowExecutionContext ctx) throws Exception {
        JsonNode config = ctx.getConfig();
        String label = "节点 " + ctx.getNodeName();

        String channel = config.has("channel") && !config.get("channel").isNull()
            ? config.get("channel").asText() : "wecom";
        String webhookUrl = FlowTemplateRenderer.render(config.get("webhookUrl").asText(), ctx.getState(),
            label + " 的 webhookUrl");
        String content = FlowTemplateRenderer.render(config.get("content").asText(), ctx.getState(),
            label + " 的 content");
        int timeoutSeconds = config.has("timeoutSeconds") && config.get("timeoutSeconds").canConvertToInt()
            ? Math.max(1, Math.min(config.get("timeoutSeconds").asInt(), 60)) : 10;

        // SSRF 防护（webhook 必须为公网地址）
        String safeUrl = HttpNodeProvider.sanitizeUrl(webhookUrl);
        if (safeUrl == null) {
            throw new IllegalArgumentException("webhookUrl 不合法或指向禁止访问的地址（仅允许 http/https 公网地址）");
        }

        // 按平台构造消息体
        ObjectNode payload = MAPPER.createObjectNode();
        switch (channel) {
            case "dingtalk" -> {
                // 钉钉 markdown 消息
                ObjectNode markdown = MAPPER.createObjectNode();
                markdown.put("title", "AI 流程通知");
                markdown.put("text", content);
                payload.put("msgtype", "markdown");
                payload.set("markdown", markdown);
            }
            case "feishu" -> {
                // 飞书 text 消息
                ObjectNode text = MAPPER.createObjectNode();
                text.put("text", content);
                payload.put("msg_type", "text");
                payload.set("content", text);
            }
            default -> {
                // 企业微信 markdown 消息
                ObjectNode markdown = MAPPER.createObjectNode();
                markdown.put("content", content);
                payload.put("msgtype", "markdown");
                payload.set("markdown", markdown);
            }
        }

        // POST 发送
        cn.hutool.http.HttpResponse response = cn.hutool.http.HttpRequest.post(safeUrl)
            .header("Content-Type", "application/json")
            .timeout(timeoutSeconds * 1000)
            .body(payload.toString(), "application/json")
            .execute();
        String responseBody = response.body();
        int status = response.getStatus();
        response.close();
        if (status >= 400) {
            throw new IllegalArgumentException("IM 机器人发送失败: HTTP " + status + " - " + responseBody);
        }
        log.info("[Flow] IM 机器人节点 {} 发送成功: channel={}, status={}", ctx.getNodeName(), channel, status);

        ObjectNode output = MAPPER.createObjectNode();
        output.put("sent", true);
        output.put("channel", channel);
        output.put("status", status);
        if (responseBody != null && !responseBody.isBlank()) {
            output.put("response", responseBody);
        }
        return output;
    }
}
