package com.nexus.ai.flow.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.ai.flow.state.FlowTemplateRenderer;
import com.nexus.common.core.event.UserNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 站内通知节点执行器
 * <p>发布 {@link UserNotificationEvent}（ruoyi-im 模块监听落库并实时推送），
 * 用于"定时巡检 → LLM 摘要 → 通知负责人"等流程收尾场景</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyNodeProvider implements FlowNodeProvider {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public String getNodeType() {
        return "notify";
    }

    @Override
    public String getDisplayName() {
        return "站内通知";
    }

    @Override
    public List<NodeParamSchema> getParamSchemas() {
        NodeParamSchema category = new NodeParamSchema("category", "通知分类", "options", "system", false);
        category.setOptions(List.of(
            Map.of("name", "系统通知", "value", "system"),
            Map.of("name", "工作流通知", "value", "workflow_notice"),
            Map.of("name", "流程编排通知", "value", "ai_flow_notice")
        ));
        return List.of(
            new NodeParamSchema("userId", "接收用户ID", "string", "", true),
            new NodeParamSchema("title", "通知标题", "string", "", true),
            new NodeParamSchema("content", "通知内容", "textarea", "", true),
            category
        );
    }

    @Override
    public void validateConfig(JsonNode config) {
        if (config == null || config.get("userId") == null || config.get("userId").asText().isBlank()) {
            throw new IllegalArgumentException("通知节点缺少 userId 配置");
        }
        if (config.get("title") == null || config.get("title").asText().isBlank()) {
            throw new IllegalArgumentException("通知节点缺少 title 配置");
        }
        if (config.get("content") == null || config.get("content").asText().isBlank()) {
            throw new IllegalArgumentException("通知节点缺少 content 配置");
        }
    }

    @Override
    public JsonNode execute(FlowExecutionContext ctx) {
        JsonNode config = ctx.getConfig();
        String label = "节点 " + ctx.getNodeName();

        // 渲染模板变量（userId 支持 {{input.xxx}} 动态指定接收人）
        String userIdStr = FlowTemplateRenderer.render(config.get("userId").asText(), ctx.getState(),
            label + " 的 userId");
        String title = FlowTemplateRenderer.render(config.get("title").asText(), ctx.getState(),
            label + " 的 title");
        String content = FlowTemplateRenderer.render(config.get("content").asText(), ctx.getState(),
            label + " 的 content");
        String category = config.has("category") && !config.get("category").isNull()
            ? config.get("category").asText() : "system";

        Long targetUserId;
        try {
            targetUserId = Long.parseLong(userIdStr.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("通知节点 userId 不是合法数字: " + userIdStr);
        }

        // 发布事件（im 模块监听落库并推送）
        // 租户经 ctx 传递（节点在 ForkJoinPool 线程执行，AiRequestContext ThreadLocal 不可达；
        // node-test 单节点测试无 ctx 租户时兜底 AiRequestContext）。null 不 set —— 监听器跳过租户切换
        UserNotificationEvent event = new UserNotificationEvent();
        String tenantId = ctx.getTenantId();
        if (tenantId == null) {
            Long ctxTenant = AiRequestContext.getTenantId();
            if (ctxTenant != null) {
                tenantId = String.valueOf(ctxTenant);
            }
        }
        if (tenantId != null) {
            event.setTenantId(tenantId);
        }
        event.setTargetUserId(targetUserId);
        event.setCategory(category);
        event.setRefType("ai_flow");
        event.setRefId(String.valueOf(ctx.getInstanceId()));
        event.setTitle(title);
        event.setContent(content);
        eventPublisher.publishEvent(event);
        log.info("[Flow] 通知节点 {} 已发送给用户 {}: {}", ctx.getNodeName(), targetUserId, title);

        ObjectNode output = MAPPER.createObjectNode();
        output.put("sent", true);
        output.put("targetUserId", targetUserId);
        output.put("title", title);
        return output;
    }
}
