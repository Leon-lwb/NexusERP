package com.nexus.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nexus.common.ai.domain.entity.AiMessage;
import com.nexus.common.ai.domain.entity.AiSession;
import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.ai.mapper.AiMessageMapper;
import com.nexus.common.ai.service.IAiSessionService;
import com.nexus.common.core.domain.R;
import com.nexus.common.satoken.utils.LoginHelper;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nexus.ai.service.facade.AiServiceFacade;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 聊天接口
 * <p>前端通过 nexus-erp 已有的 SSE 连接接收流式响应</p>
 *
 * @author NexusERP
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/chat")
public class AiChatController {

    /** AI 对话门面，封装发送消息、流式回复与 SSE 推送的完整链路。 */
    private final AiServiceFacade aiServiceFacade;
    /** 聊天消息表 Mapper，负责消息读写。 */
    private final AiMessageMapper aiMessageMapper;
    /** 会话业务服务，负责会话查询、删除、重命名与归属校验。 */
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

    /**
     * 消息反馈（点赞/点踩）
     *
     * @param messageId 消息ID
     * @param feedback  反馈类型：LIKE / DISLIKE / NONE（取消反馈）
     * @return 操作结果
     */
    @PutMapping("/message/{messageId}/feedback")
    public R<Void> feedback(@PathVariable Long messageId, @RequestParam String feedback) {
        if (!"LIKE".equals(feedback) && !"DISLIKE".equals(feedback) && !"NONE".equals(feedback)) {
            return R.fail("参数错误：feedback 必须为 LIKE/DISLIKE/NONE");
        }
        // 校验消息归属：只能反馈自己会话中的消息
        Long userId = LoginHelper.getUserId();
        AiMessage message = aiMessageMapper.selectById(messageId);
        if (message == null) {
            return R.fail("消息不存在");
        }
        AiSession session = aiSessionService.selectById(message.getSessionId());
        if (session == null || !session.getUserId().equals(userId)) {
            return R.fail("无权操作该消息");
        }
        // 更新反馈（使用 UpdateWrapper 确保 null 值能写入数据库，绕过 NOT_NULL 策略）
        LambdaUpdateWrapper<AiMessage> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(AiMessage::getId, messageId)
            .set(AiMessage::getFeedback, "NONE".equals(feedback) ? null : feedback);
        aiMessageMapper.update(null, updateWrapper);
        return R.ok();
    }

    /**
     * 删除消息
     *
     * @param messageId 消息ID
     * @return 操作结果
     */
    @DeleteMapping("/message/{messageId}")
    public R<Void> deleteMessage(@PathVariable Long messageId) {
        // 校验消息归属：只能删除自己会话中的消息
        Long userId = LoginHelper.getUserId();
        AiMessage message = aiMessageMapper.selectById(messageId);
        if (message == null) {
            return R.fail("消息不存在");
        }
        AiSession session = aiSessionService.selectById(message.getSessionId());
        if (session == null || !session.getUserId().equals(userId)) {
            return R.fail("无权操作该消息");
        }
        aiMessageMapper.deleteById(messageId);
        return R.ok();
    }

    /**
     * 查询当前会话的 Token 用量
     * <p>返回会话中所有消息的 Token 用量总和，供前端显示进度条</p>
     *
     * @param sessionId 会话ID
     * @return Token 用量信息
     */
    @GetMapping("/token-usage/{sessionId}")
    public R<Map<String, Object>> getTokenUsage(@PathVariable Long sessionId) {
        Long userId = LoginHelper.getUserId();
        AiSession session = aiSessionService.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            return R.fail("无权访问该会话");
        }

        List<AiMessage> messages = aiMessageMapper.selectList(
            new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getSessionId, sessionId)
                .isNotNull(AiMessage::getTokenUsage)
        );

        int totalTokens = messages.stream()
            .filter(m -> m.getTokenUsage() != null)
            .mapToInt(m -> m.getTokenUsage().intValue())
            .sum();

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("sessionId", sessionId);
        result.put("totalTokens", totalTokens);
        result.put("messageCount", messages.size());
        return R.ok(result);
    }

    /**
     * 导出会话聊天记录为 Markdown 文件
     * <p>将指定会话的所有消息格式化为 Markdown 并下载</p>
     *
     * @param sessionId 会话ID
     * @param response  HTTP 响应
     */
    @SaCheckPermission("ai:chat:export")
    @GetMapping("/export/{sessionId}")
    public void exportSession(@PathVariable Long sessionId, HttpServletResponse response) {
        // 校验会话归属：只能导出自己的会话
        Long userId = LoginHelper.getUserId();
        AiSession session = aiSessionService.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            try {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\":500,\"msg\":\"无权访问该会话\"}");
                return;
            } catch (Exception ignored) {
                return;
            }
        }

        // 查询所有消息
        List<AiMessage> messages = aiMessageMapper.selectList(
            new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getSessionId, sessionId)
                .orderByAsc(AiMessage::getCreateTime)
        );

        // 格式化为 Markdown
        String markdown = formatSessionToMarkdown(session, messages);

        // 写入响应流
        try {
            response.setContentType("text/markdown;charset=utf-8");
            response.setHeader("Content-Disposition",
                "attachment;filename=" + java.net.URLEncoder.encode(
                    (session.getTitle() != null ? session.getTitle() : "AI对话") + ".md", "UTF-8"));
            response.getWriter().write(markdown);
            response.getWriter().flush();
        } catch (java.io.IOException e) {
            log.error("导出会话失败: sessionId={}", sessionId, e);
        }
    }

    /**
     * 将会话格式化为 Markdown
     *
     * @param session  会话信息
     * @param messages 消息列表
     * @return Markdown 文本
     */
    private String formatSessionToMarkdown(AiSession session, List<AiMessage> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(session.getTitle() != null ? session.getTitle() : "AI 对话记录").append("\n\n");
        sb.append("> 导出时间：").append(java.time.LocalDateTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        if (session.getModelName() != null) {
            sb.append("> 模型：").append(session.getModelName()).append("\n");
        }
        sb.append("\n---\n\n");

        for (AiMessage msg : messages) {
            String role = "USER".equals(msg.getRole()) ? "用户" : "AI";
            sb.append("### ").append(role).append("\n\n");
            sb.append(msg.getContent() != null ? msg.getContent() : "").append("\n\n");
            if (msg.getTokenUsage() != null) {
                sb.append("> Token 消耗：").append(msg.getTokenUsage()).append("\n\n");
            }
        }
        return sb.toString();
    }

}
