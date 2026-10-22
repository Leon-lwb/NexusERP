package com.nexus.ai.engineering.service;

import static com.nexus.common.ai.engineering.EngineeringException.require;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.ai.agent.EngineeringAgent;
import com.nexus.ai.context.AiToolInvocationGuard;
import com.nexus.ai.engineering.config.EngineeringProperties;
import com.nexus.ai.engineering.security.EngineeringAccess;
import com.nexus.ai.tool.EasyedaEngineeringTool;
import com.nexus.common.ai.domain.entity.AiSession;
import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.ai.engineering.*;
import com.nexus.common.ai.mapper.AiSessionMapper;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/** 工程角色工厂，先复核设备、会话和权限，再构造只含受控动作的请求专用 Agent。 */
@Component
@RequiredArgsConstructor
@DS("master")
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class EngineeringAgentFactory {
    private final EngineeringProperties properties;
    private final EngineeringAccess access;
    private final EngineeringRepository repository;
    private final AiSessionMapper sessions;
    private final EasyedaEngineeringTool tool;
    private final EngineeringTemplateCatalog catalog;

    /** 仅显式选设备且具有工程操作意图时请求工程能力，普通库存查询保持原路由。 */
    public boolean requested(AiChatRequest request) {
        if (!properties.isEnabled() || request == null || request.getEngineeringDeviceId() == null)
            return false;
        if ("chat".equalsIgnoreCase(request.getMode()) || "rag".equalsIgnoreCase(request.getMode()))
            return false;
        String text = Objects.toString(request.getContent(), "").toLowerCase(Locale.ROOT);
        boolean domain = text.matches("(?s).*(pcb|eda|drc|电路|工程|采集板|温湿度|传感器|板宽|板高|板层).*");
        boolean operation =
                text.matches(
                        "(?s).*(生成|创建|设计|画|做|打开|导出|检查|运行|提交|改为|调整|check|create|open|export|submit).*");
        return domain && operation || text.contains("检查连接") || text.contains("打开项目");
    }

    /** 捕获服务端身份和会话，缺设备不自动选择，伪造会话或已失效授权立即拒绝。 */
    public EngineeringToolContext.Context capture(
            AiChatRequest request, Long userId, Long sessionId, String tenantId) {
        if (!requested(request)) return null;
        var actor =
                access.requireActor(
                        new EngineeringActor(tenantId, userId, Set.of()),
                        "ai:engineering:task",
                        true);
        access.requireActor(actor, "ai:engineering:device", false);
        var device = repository.device(tenantId, userId, request.getEngineeringDeviceId());
        require(
                device != null
                        && "ACTIVE".equals(device.getStatus())
                        && Objects.equals(
                                device.getCredentialFingerprint(),
                                access.owner(actor).credentialFingerprint()),
                403,
                "DEVICE_REVOKED",
                "请选择当前用户有效的工程设备");
        var session =
                sessions.selectOne(
                        Wrappers.<AiSession>lambdaQuery()
                                .eq(AiSession::getId, sessionId)
                                .eq(AiSession::getTenantId, tenantId)
                                .eq(AiSession::getUserId, userId));
        require(
                session != null
                        && Objects.equals(session.getId(), sessionId)
                        && Objects.equals(session.getTenantId(), tenantId)
                        && Objects.equals(session.getUserId(), userId),
                403,
                "SESSION_FORBIDDEN",
                "会话不存在或不属于当前用户");
        String requestId = request.getEngineeringRequestId();
        if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();
        require(requestId.matches("[A-Za-z0-9_-]{1,128}"), 422, "INVALID_REQUEST", "工程请求标识格式无效");
        return new EngineeringToolContext.Context(
                actor, request.getEngineeringDeviceId(), sessionId, requestId);
    }

    /** 只给工程角色挂六个受控动作，模板目录来自 classpath 已审元数据。 */
    public EngineeringAgent build(ChatModel model, EngineeringToolContext.Context context) {
        access.requireActor(context.actor(), "ai:engineering:task", true);
        return AgenticServices.agentBuilder(EngineeringAgent.class)
                .chatModel(model)
                .tools(AiToolInvocationGuard.wrap(tool.withContext(context)))
                .systemMessage(
                        EngineeringAgent.INSTRUCTIONS
                                + "\n已审核模板目录：\n"
                                + EngineeringJson.write(catalog.list()))
                .maxToolCallingRoundTrips(6)
                .build();
    }

    /**
     * 根据本次真实服务回执产生确定性工程答复，不使用模型措辞作为成功证明。
     *
     * @param context 已认证上下文及实际任务回执
     * @param modelText 原始模型文本，仅保留调用兼容，不用于生成工程成功或链接
     * @return 无回执时固定安全说明；有回执时仅列真实任务、状态与详情链接
     */
    public String verifiedResult(EngineeringToolContext.Context context, String modelText) {
        var receipts = context.receipts();
        if (receipts.isEmpty()) return "尚未创建真实工程任务，不能确认设计已经完成或提供预览。请核对已配对设备、已审核模板和必要参数后重试。";
        var text = new StringBuilder("已派发本地工程任务。请通过任务详情查看实际执行状态与产物；草稿须工程师审查，不能直接投产。\n");
        for (var receipt : receipts)
            text.append("\n- [查看任务 ")
                    .append(receipt.taskId())
                    .append("](/ai/engineering/task/")
                    .append(receipt.taskId())
                    .append(")：")
                    .append(receipt.state())
                    .append("（")
                    .append(receipt.action())
                    .append("）");
        return text.toString();
    }
}
