package com.nexus.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.ai.domain.entity.AiMessage;
import com.nexus.common.ai.domain.entity.AiSession;
import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.ai.service.IAiSessionService;
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
    private final AiMessageMapper aiMessageMapper;
    private final IAiSessionService aiSessionService;

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
        if (sessionId == null) {
            return R.fail("未找到可用的 AI 模型，请先配置模型");
        }
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
        // 校验会话归属：只能查看自己的会话消息
        Long userId = LoginHelper.getUserId();
        AiSession session = aiSessionService.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            return R.fail("无权访问该会话");
        }
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
        List<AiSession> sessions = aiSessionService.selectByUserId(userId);
        return R.ok(sessions);
    }

    /**
     * 删除会话（级联删除消息）
     *
     * @param ids 会话ID数组
     * @return 操作结果
     */
    @DeleteMapping("/session/{ids}")
    public R<Void> deleteSession(@PathVariable Long[] ids) {
        // 校验会话归属：只能删除自己的会话
        Long userId = LoginHelper.getUserId();
        for (Long id : ids) {
            AiSession session = aiSessionService.selectById(id);
            if (session == null || !session.getUserId().equals(userId)) {
                return R.fail("无权删除该会话");
            }
        }
        aiSessionService.deleteByIds(ids);
        return R.ok();
    }

    /**
     * 重命名会话
     *
     * @param session 会话信息（含 id 和 title）
     * @return 操作结果
     */
    @PutMapping("/session")
    public R<Void> renameSession(@RequestBody AiSession session) {
        if (session.getId() == null || session.getTitle() == null || session.getTitle().isBlank()) {
            return R.fail("参数错误：会话ID和标题不能为空");
        }
        // 校验会话归属：只能重命名自己的会话
        Long userId = LoginHelper.getUserId();
        AiSession existing = aiSessionService.selectById(session.getId());
        if (existing == null || !existing.getUserId().equals(userId)) {
            return R.fail("无权修改该会话");
        }
        aiSessionService.updateTitle(session.getId(), session.getTitle());
        return R.ok();
    }

}
