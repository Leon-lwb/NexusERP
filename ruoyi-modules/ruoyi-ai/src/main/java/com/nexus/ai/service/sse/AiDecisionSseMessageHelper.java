package com.nexus.ai.service.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.decision.dto.DecisionOutcome;
import com.nexus.common.sse.dto.SseMessageDto;
import com.nexus.common.sse.utils.SseMessageUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于已有分布式 SSE 通道发送单次运行有序决策事件。
 */
@Service
public class AiDecisionSseMessageHelper {
    private final ObjectMapper mapper;
    private final java.util.function.Consumer<SseMessageDto> publisher;
    @Autowired
    public AiDecisionSseMessageHelper(ObjectMapper mapper) {
        this(mapper, SseMessageUtils::publishMessage);
    }
    AiDecisionSseMessageHelper(ObjectMapper mapper, java.util.function.Consumer<SseMessageDto> publisher) {
        this.mapper = mapper; this.publisher = publisher;
    }
    private final Map<Long, AtomicLong> sequences = new ConcurrentHashMap<>();

    public void sendStarted(Long userId, Long runId) {
        sequences.put(runId, new AtomicLong());
        send(userId, runId, "AI_DECISION_STARTED", 0, Map.of());
    }
    public void sendNodeStart(Long userId, Long runId, long seq, String nodeCode, String nodeName) {
        send(userId, runId, "AI_DECISION_NODE_START", seq, Map.of("nodeCode", nodeCode, "nodeName", nodeName));
    }
    public void sendNodeEnd(Long userId, Long runId, long seq, String nodeCode, String nodeName,
                            String status, Long durationMs, String summary) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("nodeCode", nodeCode); fields.put("nodeName", nodeName);
        fields.put("status", status); fields.put("durationMs", durationMs); fields.put("summary", summary);
        send(userId, runId, "AI_DECISION_NODE_END", seq, fields);
    }
    public void sendWarning(Long userId, Long runId, String code, String message) {
        send(userId, runId, "AI_DECISION_WARNING", 0, Map.of("code", code, "message", message));
    }
    public void sendReady(Long userId, Long runId, String riskLevel, String recommended, int actionCount) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("riskLevel", riskLevel); fields.put("recommendedScenarioCode", recommended);
        fields.put("actionCount", actionCount);
        send(userId, runId, "AI_DECISION_READY", 0, fields);
    }
    public void sendApprovalStatus(Long userId, Long runId, String status) {
        send(userId, runId, "AI_DECISION_APPROVAL", 0, Map.of("status", status));
    }
    public void sendActionStatus(Long userId, Long runId, Long actionId, String status) {
        send(userId, runId, "AI_DECISION_ACTION", 0,
            Map.of("actionId", String.valueOf(actionId), "status", status));
    }
    public void sendOutcome(Long userId, Long runId, DecisionOutcome outcome) {
        send(userId, runId, "AI_DECISION_OUTCOME", 0, Map.of("outcome", outcome));
    }
    public void sendDone(Long userId, Long runId, String status) {
        try { send(userId, runId, "AI_DECISION_DONE", 0, Map.of("status", status)); }
        finally { sequences.remove(runId); }
    }
    public void sendError(Long userId, Long runId, String errorCode, String message) {
        try { send(userId, runId, "AI_DECISION_ERROR", 0,
            Map.of("errorCode", errorCode, "message", sanitize(message))); }
        finally { sequences.remove(runId); }
    }

    private void send(Long userId, Long runId, String type, long proposedSequence, Map<String, ?> data) {
        if (userId == null || userId <= 0 || runId == null || runId <= 0) return;
        long seq = sequences.computeIfAbsent(runId, ignored -> new AtomicLong())
            .updateAndGet(current -> Math.max(current + 1, proposedSequence));
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type", type); body.put("runId", String.valueOf(runId)); body.put("seq", seq);
            body.putAll(data);
            SseMessageDto dto = new SseMessageDto();
            dto.setUserIds(List.of(userId)); dto.setMessage(mapper.writeValueAsString(body));
            publisher.accept(dto);
        } catch (Exception ignored) { /* SSE 缺失不得回滚已提交的状态。 */ }
    }

    private String sanitize(String message) {
        if (message == null) return "决策处理失败";
        String redacted = message.replaceAll("(?i)(?:bearer\\s+\\S+|api[_-]?key\\s*[:=]\\s*\\S+|jdbc:(?:mysql|postgresql):\\S+)", "[REDACTED]");
        return redacted.length() <= 500 ? redacted : redacted.substring(0, 500);
    }
}
