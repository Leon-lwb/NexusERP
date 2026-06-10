package com.nexus.ai.service.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.sse.dto.SseMessageDto;
import com.nexus.common.sse.utils.SseMessageUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI SSE 消息工具类
 * <p>
 * 将 AI 消息类型编码到 SseMessageDto.message 字段中（JSON 格式），
 * 前端解析 message JSON，根据 type 字段区分消息类型。
 * 使用 publishMessage 支持多节点部署（通过 Redis Pub/Sub 广播）。
 * </p>
 *
 * @author NexusERP
 */
@Slf4j
public class AiSseMessageHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** AI 回复内容（完整替换模式，用于 Agent 一次性结果） */
    public static final String TYPE_CONTENT = "AI_CONTENT";
    /** AI 回复内容增量（流式 Token，追加模式） */
    public static final String TYPE_CONTENT_INCREMENT = "AI_CONTENT_INCREMENT";
    /** AI 思考中 */
    public static final String TYPE_THINKING = "AI_THINKING";
    /** AI 回复完成 */
    public static final String TYPE_DONE = "AI_DONE";
    /** AI 出错 */
    public static final String TYPE_ERROR = "AI_ERROR";
    /** ECharts 图表配置 */
    public static final String TYPE_CHART = "AI_CHART";

    /**
     * 发送 AI 回复内容（完整替换模式，用于 Agent 一次性结果）
     *
     * @param userId  目标用户ID
     * @param content 回复内容
     */
    public static void sendContent(Long userId, String content) {
        send(userId, TYPE_CONTENT, content);
    }

    /**
     * 发送 AI 回复内容增量（流式 Token，追加模式）
     *
     * @param userId 目标用户ID
     * @param token  单个 Token 片段
     */
    public static void sendContentIncrement(Long userId, String token) {
        send(userId, TYPE_CONTENT_INCREMENT, token);
    }

    /**
     * 发送 AI 思考中状态
     *
     * @param userId 目标用户ID
     */
    public static void sendThinking(Long userId) {
        send(userId, TYPE_THINKING, "AI 正在思考...");
    }

    /**
     * 发送 AI 回复完成信号
     *
     * @param userId 目标用户ID
     */
    public static void sendDone(Long userId) {
        send(userId, TYPE_DONE, null);
    }

    /**
     * 发送 AI 错误信息
     *
     * @param userId 目标用户ID
     * @param error  错误信息
     */
    public static void sendError(Long userId, String error) {
        send(userId, TYPE_ERROR, error);
    }

    /**
     * 发送 ECharts 图表配置
     *
     * @param userId    目标用户ID
     * @param chartJson ECharts JSON 配置
     */
    public static void sendChart(Long userId, String chartJson) {
        send(userId, TYPE_CHART, chartJson);
    }

    /**
     * 统一发送方法，将消息编码为 JSON 格式并通过 Redis Pub/Sub 广播
     *
     * @param userId  目标用户ID
     * @param type    消息类型
     * @param content 消息内容（可为 null）
     */
    private static void send(Long userId, String type, String content) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", type);
            if (content != null) {
                payload.put("content", content);
            }
            String json = MAPPER.writeValueAsString(payload);

            SseMessageDto dto = new SseMessageDto();
            dto.setUserIds(List.of(userId));
            dto.setMessage(json);
            SseMessageUtils.publishMessage(dto);
        } catch (Exception e) {
            log.error("发送 AI SSE 消息失败, userId={}, type={}", userId, type, e);
        }
    }
}
