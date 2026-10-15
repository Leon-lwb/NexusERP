package com.nexus.report.runtime;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** 指标只使用有界阶段与结果标签，不使用用户、租户、参数或异常消息作为标签。 */
public final class ReportRuntimeMetrics {
    /** Phase，负责当前模块对应的契约或处理职责。 */
    public enum Phase {
        /** 保存当前 {@code QUERY} 值。 */
        QUERY,
        /** 保存当前 {@code CALCULATION} 值。 */
        CALCULATION,
        /** 保存当前 {@code COMPILE} 值。 */
        COMPILE,
        /** 保存当前 {@code SNAPSHOT} 值。 */
        SNAPSHOT,
        /** 保存当前 {@code LAYOUT} 值。 */
        LAYOUT,
        /** 保存当前 {@code SERIALIZATION} 值。 */
        SERIALIZATION,
        /** 保存当前 {@code EXPORT} 值。 */
        EXPORT,
        /** 保存当前 {@code DELIVERY} 值。 */
        DELIVERY
    }

    /** 代码契约注册表。 */
    private final MeterRegistry registry;

    /**
     * 初始化报表运行指标并保存所需依赖或不可变值。
     *
     * @param registry 代码契约注册表
     */
    public ReportRuntimeMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * 完成 {@code local} 对应的报表运行指标处理。
     *
     * @return 当前处理返回的固定结果
     */
    public static ReportRuntimeMetrics local() {
        return new ReportRuntimeMetrics(
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    /**
     * 采集固定阶段的耗时与结果。
     *
     * @param phase 当前操作的 {@code phase} 输入
     * @param action 资源操作
     * @return 当前处理返回的固定结果
     */
    public <T> T measure(Phase phase, Supplier<T> action) {
        long started = System.nanoTime();
        boolean success = false;
        try {
            T value = action.get();
            success = true;
            return value;
        } finally {
            record(phase, success, System.nanoTime() - started);
        }
    }

    /**
     * 记录有界阶段和结果指标。
     *
     * @param phase 当前操作的 {@code phase} 输入
     * @param success 当前操作的 {@code success} 输入
     * @param nanos 当前操作的 {@code nanos} 输入
     */
    public void record(Phase phase, boolean success, long nanos) {
        Timer.builder("nexus.report.duration")
                .tag("phase", phase.name())
                .tag("outcome", success ? "succeeded" : "failed")
                .register(registry)
                .record(Math.max(0, nanos), TimeUnit.NANOSECONDS);
    }
}
