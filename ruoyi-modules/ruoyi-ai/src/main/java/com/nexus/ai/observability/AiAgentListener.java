package com.nexus.ai.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI Agent 可观测性监听器
 * <p>记录 Agent 调用链路、Token 消耗统计、执行耗时监控</p>
 * <p>基于 ConcurrentHashMap + AtomicLong 实现线程安全的内存统计</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Slf4j
@Component
public class AiAgentListener {

    /** Agent 调用次数统计（key = agentName） */
    private final Map<String, AtomicLong> agentCallCount = new ConcurrentHashMap<>();

    /** Agent 执行耗时统计（key = agentName, value = 总耗时毫秒） */
    private final Map<String, AtomicLong> agentTotalTime = new ConcurrentHashMap<>();

    /** Tool 调用次数统计（key = toolName） */
    private final Map<String, AtomicLong> toolCallCount = new ConcurrentHashMap<>();

    /** Token 消耗统计（key = modelName, value = 总 token 数） */
    private final Map<String, AtomicLong> tokenUsage = new ConcurrentHashMap<>();

    /**
     * 记录 Agent 调用开始
     *
     * @param agentName Agent 名称
     * @param query     用户查询
     */
    public void onAgentStart(String agentName, String query) {
        agentCallCount.computeIfAbsent(agentName, k -> new AtomicLong(0)).incrementAndGet();
        log.info("[AiAgent] 开始调用: agent={}, query={}", agentName,
            query != null && query.length() > 100 ? query.substring(0, 100) + "..." : query);
    }

    /**
     * 记录 Agent 调用完成
     *
     * @param agentName    Agent 名称
     * @param durationMs   执行耗时（毫秒）
     * @param tokenConsumed 消耗的 Token 数
     */
    public void onAgentComplete(String agentName, long durationMs, long tokenConsumed) {
        agentTotalTime.computeIfAbsent(agentName, k -> new AtomicLong(0)).addAndGet(durationMs);
        log.info("[AiAgent] 调用完成: agent={}, duration={}ms, tokens={}", agentName, durationMs, tokenConsumed);
    }

    /**
     * 记录 Agent 调用失败
     *
     * @param agentName Agent 名称
     * @param error     错误信息
     */
    public void onAgentError(String agentName, String error) {
        log.error("[AiAgent] 调用失败: agent={}, error={}", agentName, error);
    }

    /**
     * 记录 Tool 调用
     *
     * @param toolName Tool 名称
     * @param durationMs 执行耗时（毫秒）
     */
    public void onToolCall(String toolName, long durationMs) {
        toolCallCount.computeIfAbsent(toolName, k -> new AtomicLong(0)).incrementAndGet();
        log.debug("[AiTool] 工具调用: tool={}, duration={}ms", toolName, durationMs);
    }

    /**
     * 记录 Token 消耗
     *
     * @param modelName 模型名称
     * @param tokens    消耗的 Token 数
     */
    public void onTokenUsage(String modelName, long tokens) {
        tokenUsage.computeIfAbsent(modelName, k -> new AtomicLong(0)).addAndGet(tokens);
    }

    /**
     * 获取 Agent 调用统计
     *
     * @return Agent 调用次数和耗时统计
     */
    public Map<String, Long> getAgentCallStats() {
        Map<String, Long> stats = new ConcurrentHashMap<>();
        agentCallCount.forEach((name, count) -> stats.put(name + ".calls", count.get()));
        agentTotalTime.forEach((name, time) -> stats.put(name + ".totalTimeMs", time.get()));
        return stats;
    }

    /**
     * 获取 Tool 调用统计
     *
     * @return Tool 调用次数统计
     */
    public Map<String, Long> getToolCallStats() {
        Map<String, Long> stats = new ConcurrentHashMap<>();
        toolCallCount.forEach((name, count) -> stats.put(name, count.get()));
        return stats;
    }

    /**
     * 获取 Token 消耗统计
     *
     * @return 按模型统计的 Token 消耗
     */
    public Map<String, Long> getTokenUsageStats() {
        Map<String, Long> stats = new ConcurrentHashMap<>();
        tokenUsage.forEach((model, tokens) -> stats.put(model, tokens.get()));
        return stats;
    }

    /**
     * 重置所有统计数据
     */
    public void reset() {
        agentCallCount.clear();
        agentTotalTime.clear();
        toolCallCount.clear();
        tokenUsage.clear();
    }
}
