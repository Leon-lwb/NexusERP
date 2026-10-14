package com.nexus.report.runtime;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Bounded phase/outcome labels; actor, tenant, parameters and business exception messages are never
 * labels.
 */
public final class ReportRuntimeMetrics {
    public enum Phase {
        QUERY,
        CALCULATION,
        COMPILE,
        SNAPSHOT,
        LAYOUT,
        SERIALIZATION,
        EXPORT,
        DELIVERY
    }

    private final MeterRegistry registry;

    public ReportRuntimeMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public static ReportRuntimeMetrics local() {
        return new ReportRuntimeMetrics(
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

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

    public void record(Phase phase, boolean success, long nanos) {
        Timer.builder("nexus.report.duration")
                .tag("phase", phase.name())
                .tag("outcome", success ? "succeeded" : "failed")
                .register(registry)
                .record(Math.max(0, nanos), TimeUnit.NANOSECONDS);
    }
}
