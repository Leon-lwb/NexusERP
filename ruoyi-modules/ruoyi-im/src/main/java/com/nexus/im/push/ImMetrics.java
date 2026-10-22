package com.nexus.im.push;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * P4-9: IM 可观测性指标（Micrometer）。
 * <p>埋点维度：
 * <ul>
 *   <li>{@code im.push.success} — 推送成功计数（tag: type=private/group）</li>
 *   <li>{@code im.push.failure} — 推送失败计数（tag: type, reason）</li>
 *   <li>{@code im.push.latency} — 推送耗时分布（tag: type）</li>
 *   <li>{@code im.message.send} — 消息发送计数（tag: type=private/group）</li>
 * </ul>
 * <p>P5-1 扩展：
 * <ul>
 *   <li>{@code im.push.executor.rejected} — CallerRunsPolicy 触发计数（tag: type）</li>
 *   <li>通过 {@link ExecutorServiceMetrics} 自动注册 executor.active / queue.size / completed 等指标</li>
 * </ul>
 * <p>P5-2 扩展：
 * <ul>
 *   <li>{@code im.cache.hit} — 缓存命中计数（tag: type）</li>
 *   <li>{@code im.cache.miss} — 缓存未命中计数（tag: type）</li>
 *   <li>{@code im.db.slow} — 慢 SQL 计数（tag: sql_id 简化为 "default"）</li>
 *   <li>{@code im.db.query.latency} — DB 查询耗时分布</li>
 * </ul>
 * <p>访问 {@code /actuator/prometheus} 获取 Prometheus 格式指标。
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
public class ImMetrics {

    private final MeterRegistry registry;

    /** 推送成功计数 */
    public void recordPushSuccess(String chatType) {
        registry.counter("im.push.success", "type", chatType).increment();
    }

    /** 推送失败计数（含失败原因 tag） */
    public void recordPushFailure(String chatType, String reason) {
        registry.counter("im.push.failure", "type", chatType, "reason", reason).increment();
    }

    /** 开始计时（返回 Timer.Sample，结束时调 stopPushTimer） */
    public Timer.Sample startPushTimer() {
        return Timer.start(registry);
    }

    /** 结束计时并记录耗时分布 */
    public void stopPushTimer(Timer.Sample sample, String chatType) {
        if (sample == null) return;
        sample.stop(registry.timer("im.push.latency", "type", chatType));
    }

    /** 消息发送计数（在 sendPrivate/sendGroup 末尾调用） */
    public void recordMessageSend(String chatType) {
        registry.counter("im.message.send", "type", chatType).increment();
    }

    // ==================== P5-1: 推送线程池监控 ====================

    /**
     * P5-1: 推送线程池拒绝任务计数（CallerRunsPolicy 触发）。
     * <p>触发场景：imPushExecutor 队列满 + 线程数达到 maxPoolSize。
     * <p>观察意义：拒绝次数持续增长 → 需扩容线程池或调优 queueCapacity。
     */
    public void recordPushExecutorRejected(String chatType) {
        registry.counter("im.push.executor.rejected", "type", chatType == null ? "other" : chatType).increment();
    }

    /**
     * P5-1: 注册 ExecutorService 监控指标（active / queue.size / completed / pool.size 等）。
     * <p>使用 Micrometer 内置 {@link ExecutorServiceMetrics} 绑定器，自动采集
     * {@code executor.active}, {@code executor.queued}, {@code executor.completed}, {@code executor.pool.size} 等指标。
     * <p>指标前缀：{@code im.push.executor.}（通过 executor Bean 名 "imPushExecutor" 区分）。
     *
     * @param executor 推送线程池
     */
    public void registerPushExecutor(ExecutorService executor) {
        // Micrometer 内置绑定器：注册后自动采集 active/queued/completed/pool.size
        // 注意：必须用带 executor bean name 的重载，避免与全局 executor 指标冲突
        new ExecutorServiceMetrics(executor, "imPushExecutor", List.of()).bindTo(registry);
    }

    // ==================== P5-2: 缓存命中率监控 ====================

    /** 缓存命中计数（type=conversation 等） */
    public void recordCacheHit(String cacheType) {
        registry.counter("im.cache.hit", "type", cacheType).increment();
    }

    /** 缓存未命中计数（type=conversation 等） */
    public void recordCacheMiss(String cacheType) {
        registry.counter("im.cache.miss", "type", cacheType).increment();
    }

    // ==================== P5-2: 慢 SQL 监控 ====================

    /** 慢 SQL 计数（默认 tag "default"，后续可扩展按 mapper 方法名） */
    public void recordSlowSql(String sqlId) {
        registry.counter("im.db.slow", "sql", sqlId == null ? "default" : sqlId).increment();
    }

    /** 记录 DB 查询耗时分布（所有查询都记录，配合 slow 阈值识别慢查询） */
    public void recordDbQueryLatency(long elapsedMs) {
        registry.timer("im.db.query.latency").record(elapsedMs, TimeUnit.MILLISECONDS);
    }
}
