package com.nexus.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.ai.domain.entity.AiMessage;
import com.nexus.common.ai.domain.entity.AiSession;
import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.ai.mapper.AiSessionMapper;
import com.nexus.common.core.domain.R;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.ai.service.facade.AiServiceFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 聊天接口
 * <p>前端通过 nexus-erp 已有的 SSE 连接接收流式响应</p>
 *
 * @author NexusERP
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/chat")
public class AiChatController {

    private final AiServiceFacade aiServiceFacade;
    private final AiSessionMapper aiSessionMapper;
    private final AiMessageMapper aiMessageMapper;

    /**
     * 发送消息
     * <p>消息发送后，AI 回复通过 SSE 推送到前端</p>
     *
     * @param request 聊天请求
     * @return 操作结果（含 sessionId）
     */
    @PostMapping("/send")
    public R<Long> send(@RequestBody AiChatRequest request) {
        Long sessionId = aiServiceFacade.chat(request);
        return R.ok(sessionId);
    }

    /**
     * 查询聊天历史
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    @GetMapping("/history/{sessionId}")
    public R<List<AiMessage>> history(@PathVariable Long sessionId) {
        List<AiMessage> messages = aiMessageMapper.selectList(
            new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getSessionId, sessionId)
                .orderByAsc(AiMessage::getCreateTime)
        );
        return R.ok(messages);
    }

    /**
     * 查询当前用户的会话列表
     *
     * @return 会话列表
     */
    @GetMapping("/sessions")
    public R<List<AiSession>> sessions() {
        Long userId = LoginHelper.getUserId();
        List<AiSession> sessions = aiSessionMapper.selectList(
            new LambdaQueryWrapper<AiSession>()
                .eq(AiSession::getUserId, userId)
                .orderByDesc(AiSession::getCreateTime)
        );
        return R.ok(sessions);
    }
}
