package com.nexus.ai.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 流式事件推送工具
 * <p>
 * 将 Agent 执行过程中的事件（工具调用开始/完成、思考过程等）
 * 通过 OutputChannel 推送，最终由 SSE 端点消费并推送到前端。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
public class AgentStreamHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 推送工具调用开始事件
     */
    public static void sendToolStart(OutputChannel channel, String toolName, String arguments) {
        if (channel == null) return;
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "TOOL_START");
            event.put("toolName", toolName);
            event.put("arguments", truncate(arguments, 200));
            channel.send(MAPPER.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("[AgentStream] 推送工具开始事件失败: {}", e.getMessage());
        }
    }

    /**
     * 推送工具调用完成事件
     */
    public static void sendToolEnd(OutputChannel channel, String toolName, long durationMs, String resultSummary) {
        if (channel == null) return;
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "TOOL_END");
            event.put("toolName", toolName);
            event.put("duration", durationMs);
            event.put("resultSummary", truncate(resultSummary, 300));
            channel.send(MAPPER.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("[AgentStream] 推送工具完成事件失败: {}", e.getMessage());
        }
    }

    /**
     * 推送 Agent 思考过程
     */
    public static void sendThinking(OutputChannel channel, String agentName, String message) {
        if (channel == null) return;
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "THINKING");
            event.put("agentName", agentName);
            event.put("message", truncate(message, 500));
            channel.send(MAPPER.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("[AgentStream] 推送思考事件失败: {}", e.getMessage());
        }
    }

    /**
     * 推送 Agent 错误事件
     */
    public static void sendError(OutputChannel channel, String agentName, String error) {
        if (channel == null) return;
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "ERROR");
            event.put("agentName", agentName);
            event.put("error", error);
            channel.send(MAPPER.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("[AgentStream] 推送错误事件失败: {}", e.getMessage());
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
