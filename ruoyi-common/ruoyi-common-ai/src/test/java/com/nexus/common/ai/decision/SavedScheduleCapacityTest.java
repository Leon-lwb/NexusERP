package com.nexus.common.ai.decision;

import com.nexus.common.ai.decision.util.SavedScheduleCapacity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class SavedScheduleCapacityTest {
    private Map<String, Object> task(String start, String end) {
        return Map.of("taskId", "100", "woId", "7", "resourceType", "WORKSTATION",
            "resourceId", "12", "startTime", start, "endTime", end, "processHours", "2");
    }
    private Map<String, Object> calendar(String date, String effective, String planned) {
        return Map.of("calendarId", date.endsWith("03") ? "23" : "24", "calendarDate", date,
            "resourceType", "WORKSTATION", "resourceId", "12",
            "effectiveHours", effective, "plannedHours", planned);
    }

    @Test void measuresObservedPlanLoadFromSameResourceAndCompleteDays() {
        var source = Map.<String, Object>of("planId", "1",
            "tasks", List.of(task("2026-10-03T00:00:00", "2026-10-04T23:59:59")),
            "capacityCalendars", List.of(calendar("2026-10-03", "8", "4"),
                calendar("2026-10-04", "2", "3")));
        var observed = SavedScheduleCapacity.measure(source);
        assertNotNull(observed);
        assertEquals(0, new BigDecimal("150").compareTo(observed.maxUtilization()));
        assertEquals(1, observed.overloadedResources());
    }

    @Test void overloadedResourceCountDoesNotMultiplyAcrossDays() {
        var source = Map.<String, Object>of("planId", "1",
            "tasks", List.of(task("2026-10-03", "2026-10-04")),
            "capacityCalendars", List.of(calendar("2026-10-03", "2", "3"),
                calendar("2026-10-04", "2", "3")));
        assertEquals(1, SavedScheduleCapacity.measure(source).overloadedResources());
    }
    @Test void missingDayOrMismatchedResourceNeverBecomesZeroUtilization() {
        var source = Map.<String, Object>of("planId", "1",
            "tasks", List.of(task("2026-10-03", "2026-10-04")),
            "capacityCalendars", List.of(calendar("2026-10-03", "8", "4")));
        assertNull(SavedScheduleCapacity.measure(source));
        assertNull(SavedScheduleCapacity.measure(Map.of("planId", "1", "tasks", source.get("tasks"),
            "capacityCalendars", List.of(Map.of("calendarId", "23", "calendarDate", "2026-10-03",
                "resourceType", "WORKSTATION", "resourceId", "999", "effectiveHours", "8", "plannedHours", "4")))));
    }

    @Test void unrelatedCalendarWithoutLoadDoesNotPoisonAScopedResource() {
        var unrelated = new java.util.HashMap<String, Object>();
        unrelated.put("calendarId", "99"); unrelated.put("calendarDate", "2026-10-03");
        unrelated.put("resourceType", "WORKSHOP"); unrelated.put("resourceId", "999");
        unrelated.put("effectiveHours", null); unrelated.put("plannedHours", null);
        var source = Map.<String, Object>of("planId", "1",
            "tasks", List.of(task("2026-10-03", "2026-10-03")),
            "capacityCalendars", List.of(calendar("2026-10-03", "8", "4"), unrelated));
        assertEquals(0, new BigDecimal("50").compareTo(
            SavedScheduleCapacity.measure(source).maxUtilization()));
    }
    @Test void aSavedTaskCannotClaimZeroPlannedHoursOnItsResource() {
        var source = Map.<String, Object>of("planId", "1",
            "tasks", List.of(task("2026-10-03", "2026-10-03")),
            "capacityCalendars", List.of(calendar("2026-10-03", "8", "0")));
        assertNull(SavedScheduleCapacity.measure(source));
    }
    @Test void zeroOrDuplicateEffectiveCapacityFailsClosed() {
        var source = Map.<String, Object>of("planId", "1",
            "tasks", List.of(task("2026-10-03", "2026-10-03")),
            "capacityCalendars", List.of(calendar("2026-10-03", "0", "3")));
        assertNull(SavedScheduleCapacity.measure(source));
        assertNull(SavedScheduleCapacity.measure(Map.of("planId", "1", "tasks", source.get("tasks"),
            "capacityCalendars", List.of(calendar("2026-10-03", "8", "4"),
                calendar("2026-10-03", "8", "4")))));
    }
}