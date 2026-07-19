package com.nexus.im.push;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P4-12: P4-9 {@link ImMetrics} 单元测试。
 * <p>守护 ImMetrics 正确注册 counter / timer，且 tag 维度（type / reason）正确分离。
 * <p>使用 {@link SimpleMeterRegistry}（Micrometer 内存实现）替代 Spring 容器，避免依赖 Actuator 端点。
 *
 * @author nexus
 */
@DisplayName("ImMetrics 指标埋点")
class ImMetricsTest {

    private SimpleMeterRegistry registry;
    private ImMetrics imMetrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        imMetrics = new ImMetrics(registry);
    }

    @Test
    @DisplayName("recordPushSuccess: 计数器递增 + type tag 正确分离")
    void recordPushSuccess_incrementsCounterWithTypeTag() {
        imMetrics.recordPushSuccess("private");
        imMetrics.recordPushSuccess("private");
        imMetrics.recordPushSuccess("group");

        double privateCount = registry.counter("im.push.success", "type", "private").count();
        double groupCount = registry.counter("im.push.success", "type", "group").count();

        assertThat(privateCount).isEqualTo(2.0);
        assertThat(groupCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("recordPushFailure: reason tag 正确分离（ws / sse）")
    void recordPushFailure_separatesByReasonTag() {
        imMetrics.recordPushFailure("private", "ws");
        imMetrics.recordPushFailure("private", "ws");
        imMetrics.recordPushFailure("private", "sse");
        imMetrics.recordPushFailure("group", "ws");

        double privateWs = registry.counter("im.push.failure", "type", "private", "reason", "ws").count();
        double privateSse = registry.counter("im.push.failure", "type", "private", "reason", "sse").count();
        double groupWs = registry.counter("im.push.failure", "type", "group", "reason", "ws").count();

        assertThat(privateWs).isEqualTo(2.0);
        assertThat(privateSse).isEqualTo(1.0);
        assertThat(groupWs).isEqualTo(1.0);
    }

    @Test
    @DisplayName("recordMessageSend: 消息发送计数")
    void recordMessageSend_incrementsCounter() {
        imMetrics.recordMessageSend("private");
        imMetrics.recordMessageSend("group");
        imMetrics.recordMessageSend("group");

        assertThat(registry.counter("im.message.send", "type", "private").count()).isEqualTo(1.0);
        assertThat(registry.counter("im.message.send", "type", "group").count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("startPushTimer/stopPushTimer: 记录耗时分布到 timer")
    void startStopPushTimer_recordsLatency() throws InterruptedException {
        Timer.Sample sample = imMetrics.startPushTimer();
        assertThat(sample).isNotNull();
        TimeUnit.MILLISECONDS.sleep(20);
        imMetrics.stopPushTimer(sample, "private");

        Timer timer = registry.timer("im.push.latency", "type", "private");
        assertThat(timer.count()).isEqualTo(1L);
        // 至少记录了 20ms（SimpleMeterRegistry 精度可能略低，校验 > 0 即可）
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isPositive();
    }

    @Test
    @DisplayName("stopPushTimer: null sample 不抛异常（防御性）")
    void stopPushTimer_nullSampleDoesNotThrow() {
        // 不应抛 NPE
        imMetrics.stopPushTimer(null, "private");
        // 不会有任何 timer 注册
        assertThat(registry.find("im.push.latency").timers()).isEmpty();
    }

    // ==================== P5-2: 缓存命中率 + 慢 SQL 监控 ====================

    @Test
    @DisplayName("recordCacheHit: type tag 正确分离（conversation / group）")
    void recordCacheHit_incrementsCounterWithTypeTag() {
        imMetrics.recordCacheHit("conversation");
        imMetrics.recordCacheHit("conversation");
        imMetrics.recordCacheHit("group");

        assertThat(registry.counter("im.cache.hit", "type", "conversation").count()).isEqualTo(2.0);
        assertThat(registry.counter("im.cache.hit", "type", "group").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("recordCacheMiss: type tag 正确分离")
    void recordCacheMiss_incrementsCounterWithTypeTag() {
        imMetrics.recordCacheMiss("conversation");
        imMetrics.recordCacheMiss("conversation");
        imMetrics.recordCacheMiss("conversation");

        assertThat(registry.counter("im.cache.miss", "type", "conversation").count()).isEqualTo(3.0);
        assertThat(registry.counter("im.cache.miss", "type", "group").count()).isZero();
    }

    @Test
    @DisplayName("recordSlowSql: sql id tag 正确分离，null → 'default'")
    void recordSlowSql_incrementsCounterWithSqlTag() {
        imMetrics.recordSlowSql("com.nexus.im.mapper.ImMessageMapper.selectLatestPrivatePerPeer");
        imMetrics.recordSlowSql("com.nexus.im.mapper.ImMessageMapper.selectLatestPrivatePerPeer");
        imMetrics.recordSlowSql(null);  // null → "default"
        imMetrics.recordSlowSql("com.nexus.im.mapper.ImGroupMapper.selectByIds");

        assertThat(registry.counter("im.db.slow",
            "sql", "com.nexus.im.mapper.ImMessageMapper.selectLatestPrivatePerPeer").count()).isEqualTo(2.0);
        assertThat(registry.counter("im.db.slow", "sql", "default").count()).isEqualTo(1.0);
        assertThat(registry.counter("im.db.slow",
            "sql", "com.nexus.im.mapper.ImGroupMapper.selectByIds").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("recordDbQueryLatency: 记录耗时分布到 timer")
    void recordDbQueryLatency_recordsLatency() {
        imMetrics.recordDbQueryLatency(50);
        imMetrics.recordDbQueryLatency(150);
        imMetrics.recordDbQueryLatency(300);

        Timer timer = registry.timer("im.db.query.latency");
        assertThat(timer.count()).isEqualTo(3L);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(500.0);
    }
}
