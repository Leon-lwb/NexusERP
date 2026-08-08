package com.nexus.ai.flow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.ai.flow.state.FlowTemplateRenderer;
import com.nexus.common.mail.utils.MailUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 邮件发送节点执行器
 * <p>复用 ruoyi-common-mail 的 {@link MailUtils}（配置文件账户发送文本/HTML 邮件），
 * 收件人/标题/正文支持 {{}} 模板渲染</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
public class MailNodeProvider implements FlowNodeProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getNodeType() {
        return "mail";
    }

    @Override
    public String getDisplayName() {
        return "邮件发送";
    }

    @Override
    public List<NodeParamSchema> getParamSchemas() {
        return List.of(
            new NodeParamSchema("to", "收件人 (逗号分隔)", "string", "", true),
            new NodeParamSchema("subject", "主题", "string", "", true),
            new NodeParamSchema("content", "正文", "textarea", "", true),
            new NodeParamSchema("isHtml", "HTML 格式", "boolean", "false", false)
        );
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config == null || config.get("to") == null || config.get("to").asText().isBlank()) {
            throw new IllegalArgumentException("邮件节点缺少 to 配置");
        }
        if (config.get("subject") == null || config.get("subject").asText().isBlank()) {
            throw new IllegalArgumentException("邮件节点缺少 subject 配置");
        }
        if (config.get("content") == null || config.get("content").asText().isBlank()) {
            throw new IllegalArgumentException("邮件节点缺少 content 配置");
        }
    }

    @Override
    public JsonNode execute(FlowExecutionContext ctx) {
        JsonNode config = ctx.getConfig();
        String label = "节点 " + ctx.getNodeName();

        String to = FlowTemplateRenderer.render(config.get("to").asText(), ctx.getState(), label + " 的 to");
        String subject = FlowTemplateRenderer.render(config.get("subject").asText(), ctx.getState(), label + " 的 subject");
        String content = FlowTemplateRenderer.render(config.get("content").asText(), ctx.getState(), label + " 的 content");
        boolean isHtml = config.has("isHtml") && config.get("isHtml").asBoolean(false);

        // MailUtils 支持逗号/分号分隔多收件人
        String result = isHtml ? MailUtils.sendHtml(to, subject, content)
                               : MailUtils.sendText(to, subject, content);
        log.info("[Flow] 邮件节点 {} 发送成功: to={}, subject={}", ctx.getNodeName(), to, subject);

        ObjectNode output = MAPPER.createObjectNode();
        output.put("sent", true);
        output.put("to", to);
        output.put("subject", subject);
        if (result != null && !result.isBlank()) {
            output.put("messageId", result);
        }
        return output;
    }
}
