package com.nexus.report.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import java.util.Set;

/** 报表运行指标测试，负责当前模块对应的契约或处理职责。 */
class ReportRuntimeMetricsTest {
    /** 验证 {@code recordsSuccessfulAndFailedPhasesWithoutBusinessLabels} 场景下的结果与边界条件。 */
    @Test
    void recordsSuccessfulAndFailedPhasesWithoutBusinessLabels() {
        var registry = new SimpleMeterRegistry();
        var metrics = new ReportRuntimeMetrics(registry);
        assertEquals("value", metrics.measure(ReportRuntimeMetrics.Phase.QUERY, () -> "value"));
        assertThrows(
                IllegalStateException.class,
                () ->
                        metrics.measure(
                                ReportRuntimeMetrics.Phase.QUERY,
                                () -> {
                                    throw new IllegalStateException("private business value");
                                }));
        assertEquals(
                1,
                registry.get("nexus.report.duration")
                        .tags("phase", "QUERY", "outcome", "succeeded")
                        .timer()
                        .count());
        assertEquals(
                1,
                registry.get("nexus.report.duration")
                        .tags("phase", "QUERY", "outcome", "failed")
                        .timer()
                        .count());
        for (var meter : registry.getMeters())
            assertTrue(
                    meter.getId().getTags().stream()
                            .allMatch(t -> Set.of("phase", "outcome").contains(t.getKey())));
    }
}
