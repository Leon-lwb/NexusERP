package com.nexus.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;

import com.nexus.ai.service.facade.AiServiceFacade;
import com.nexus.ai.service.governance.*;
import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.core.domain.R;
import com.nexus.common.satoken.utils.LoginHelper;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 新聊天请求接口，客户端关联nonce与服务端执行ID保持独立。 */
@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class AiChatRequestController {
    private final AiServiceFacade facade;
    private final AiChatExecutionService executions;

    @SaCheckPermission("ai:assistant:send")
    @GetMapping("/capabilities")
    public R<Map<String, Object>> capabilities() {
        return R.ok(Map.of("persistentRequests", executions.enabled()));
    }

    @SaCheckPermission("ai:assistant:send")
    @PostMapping("/requests")
    public R<Map<String, Object>> create(@RequestBody(required = false) AiChatRequest request) {
        String content = request == null ? null : request.getContent();
        if (content == null
                || content.codePoints()
                        .allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)))
            return R.fail(400, "消息内容不能为空");
        if (content.length() > 2000) return R.fail(400, "消息内容不能超过2000个字符");
        if (!AiChatRequest.validClientRequestId(request.getClientRequestId()))
            return R.fail(400, "请求关联标识格式不正确");
        return R.ok(facade.chatRequest(request));
    }

    @SaCheckPermission("ai:assistant:query")
    @GetMapping("/requests/{requestId}")
    public R<Map<String, Object>> status(@PathVariable String requestId) {
        return R.ok(
                executions.status(LoginHelper.getTenantId(), LoginHelper.getUserId(), requestId));
    }

    @SaCheckPermission("ai:assistant:send")
    @PostMapping("/requests/{requestId}/cancel")
    public R<Map<String, Object>> cancel(@PathVariable String requestId) {
        return R.ok(
                executions.cancel(LoginHelper.getTenantId(), LoginHelper.getUserId(), requestId));
    }
}
