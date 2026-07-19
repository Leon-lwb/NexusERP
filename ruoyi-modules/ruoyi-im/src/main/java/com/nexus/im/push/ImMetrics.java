package com.nexus.im.push;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * P4-9: IM 可观测性指标（Micrometer）。
 * <p>埋点维度：
 * <ul>
 *   <li>{@code im.push.success} — 推送成功计数（tag: type=private/group）</li>
 *   <li>{@code im.push.failure} — 推送失败计数（tag: type, reason）</li>
 *   <li>{@code im.push.latency} — 推送耗时分布（tag: type）</li>
 *   <li>{@code im.message.send} — 消息发送计数（tag: type=private/group）</li>
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
}
