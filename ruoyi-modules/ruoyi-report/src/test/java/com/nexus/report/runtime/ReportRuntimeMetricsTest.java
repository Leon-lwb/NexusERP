package com.nexus.report.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

class ReportRuntimeMetricsTest {
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
                            .allMatch(
                                    t ->
                                            java.util.Set.of("phase", "outcome")
                                                    .contains(t.getKey())));
    }
}
