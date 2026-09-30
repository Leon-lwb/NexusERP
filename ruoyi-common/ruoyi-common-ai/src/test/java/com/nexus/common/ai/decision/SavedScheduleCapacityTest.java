package com.nexus.common.ai.decision;

import com.nexus.common.ai.decision.util.SavedScheduleCapacity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("dev")
class SavedScheduleCapacityTest {
    private static final String TENANT = "000000";

    /** 保留 v2 之前的快照作为回归测试夹具：它们都不能证明本计划自身的预订工时。 */
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
        assertNull(SavedScheduleCapacity.measure(source)); /** 旧快照缺少 v2 预订证据。 */
    }

    @Test void overloadedResourceCountDoesNotMultiplyAcrossDays() {
        var source = Map.<String, Object>of("planId", "1",
            "tasks", List.of(task("2026-10-03", "2026-10-04")),
            "capacityCalendars", List.of(calendar("2026-10-03", "2", "3"),
                calendar("2026-10-04", "2", "3")));
        assertNull(SavedScheduleCapacity.measure(source)); /** v1 日历不能证明 v2 预订归属。 */
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
        var unrelated = new HashMap<String, Object>();
        unrelated.put("calendarId", "99"); unrelated.put("calendarDate", "2026-10-03");
        unrelated.put("resourceType", "WORKSHOP"); unrelated.put("resourceId", "999");
        unrelated.put("effectiveHours", null); unrelated.put("plannedHours", null);
        var source = Map.<String, Object>of("planId", "1",
            "tasks", List.of(task("2026-10-03", "2026-10-03")),
            "capacityCalendars", List.of(calendar("2026-10-03", "8", "4"), unrelated));
        assertNull(SavedScheduleCapacity.measure(source)); /** 范围限定后的日历仍缺少预订。 */
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

    private Map<String, Object> savedTask(String id, String start, String end, String process,
                                          String setup, String resource) {
        return Map.of("taskId", id, "woId", "7", "resourceType", "WORKSTATION",
            "resourceId", resource, "startTime", start, "endTime", end,
            "processHours", process, "setupHours", setup);
    }
    private Map<String, Object> load(String id, String taskId, String date, String hours, String resource) {
        return Map.of("loadId", id, "tenantId", TENANT, "planId", "1", "taskId", taskId,
            "resourceType", "WORKSTATION", "resourceId", resource, "loadDate", date,
            "plannedHours", hours);
    }
    private Map<String, Object> booking(String id, String taskId, String calendarId,
                                         String date, String hours) {
        return Map.of("bookingId", id, "tenantId", TENANT, "planId", "1", "taskId", taskId,
            "calendarId", calendarId, "loadDate", date, "bookedHours", hours);
    }
    private Map<String, Object> outsourcedTask(String id, String start, String end,
                                                String process, String setup) {
        var row = new HashMap<String, Object>();
        row.put("taskId", id); row.put("woId", "7");
        row.put("resourceType", "OUTSOURCE"); row.put("resourceId", null);
        row.put("startTime", start); row.put("endTime", end);
        row.put("processHours", process); row.put("setupHours", setup);
        return row;
    }
    private Map<String, Object> activeCalendar(String id, String date, String resource,
                                                 String effective, String planned) {
        /** 手填总账测试夹具：信任由 ERP 装配出的总账，非证明来源（查库由适配器测试负责）。 */
        return Map.of("calendarId", id, "tenantId", TENANT, "status", "ACTIVE",
            "calendarDate", date, "resourceType", "WORKSTATION", "resourceId", resource,
            "effectiveHours", effective, "plannedHours", planned, "ledgerHours", planned);
    }
    private Map<String, Object> published(List<Map<String, Object>> tasks, List<Map<String, Object>> loads,
                                           List<Map<String, Object>> bookings, List<Map<String, Object>> calendars) {
        return Map.of("planId", "1", "tenantId", TENANT, "status", "PUBLISHED",
            "capacityCalcVersion", 2, "tasks", tasks, "taskDayLoads", loads,
            "capacityBookings", bookings, "capacityCalendars", calendars);
    }
    private Map<String, Object> oneDay() {
        return published(List.of(savedTask("100", "2026-10-03", "2026-10-03", "2", "1", "12")),
            List.of(load("31", "100", "2026-10-03", "3", "12")),
            List.of(booking("41", "100", "23", "2026-10-03", "3")),
            List.of(activeCalendar("23", "2026-10-03", "12", "8", "4")));
    }
    private Map<String, Object> with(Map<String, Object> source, String field, Object value) {
        var result = new HashMap<>(source);
        result.put(field, value);
        return result;
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> rowWith(Map<String, Object> source, String listName, int index,
                                         String field, Object value) {
        var rows = new ArrayList<>((List<Map<String, Object>>) source.get(listName));
        rows.set(index, with(rows.get(index), field, value));
        return with(source, listName, rows);
    }

    private Map<String, Object> mixedWithOutsource() {
        return published(List.of(savedTask("100", "2026-10-03", "2026-10-03", "2", "1", "12"),
                outsourcedTask("200", "2026-10-02", "2026-10-05", "5", "1")),
            List.of(load("31", "100", "2026-10-03", "3", "12")),
            List.of(booking("41", "100", "23", "2026-10-03", "3")),
            List.of(activeCalendar("23", "2026-10-03", "12", "2", "4")));
    }

    @Test void observesOnlyInternalCapacityInPublishedPlanWithValidOutsourcedTask() {
        var observed = SavedScheduleCapacity.measure(mixedWithOutsource());
        assertNotNull(observed);
        assertEquals(0, new BigDecimal("200.0").compareTo(observed.maxUtilization()));
        assertEquals(1, observed.overloadedResources());
    }

    @Test void onlyOutsourcedTasksWithoutInternalObservationRemainUnknown() {
        var source = published(List.of(outsourcedTask("200", "2026-10-02", "2026-10-05", "5", "1")),
            List.of(), List.of(), List.of());
        assertNull(SavedScheduleCapacity.measure(source));
    }

    @Test void rejectsInternalDayLoadOrBookingPointingAtAnOutsourcedTask() {
        var source = mixedWithOutsource();
        assertNull(SavedScheduleCapacity.measure(with(source, "taskDayLoads", List.of(
            load("31", "100", "2026-10-03", "3", "12"),
            load("32", "200", "2026-10-03", "6", "12")))));
        assertNull(SavedScheduleCapacity.measure(with(source, "capacityBookings", List.of(
            booking("41", "100", "23", "2026-10-03", "3"),
            booking("42", "200", "23", "2026-10-03", "1")))));
    }

    @Test void rejectsInvalidOutsourcedTaskFactsInsteadOfIgnoringThem() {
        var source = mixedWithOutsource();
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "tasks", 1, "startTime", "2026-02-30")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "tasks", 1, "endTime", "2026-10-01")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "tasks", 1, "taskId", null)));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "tasks", 1, "woId", null)));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "tasks", 1, "resourceId", "12")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "tasks", 1, "resourceType", "UNKNOWN")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "tasks", 1, "processHours", "-1")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "tasks", 1, "setupHours", "-1")));
    }

    @Test void outsourceNeedsNoInternalLoadEvenWithZeroTaskHours() {
        var source = rowWith(rowWith(mixedWithOutsource(), "tasks", 1, "processHours", "0"),
            "tasks", 1, "setupHours", "0");
        var observed = SavedScheduleCapacity.measure(source);
        assertNotNull(observed);
        assertEquals(0, new BigDecimal("200.0").compareTo(observed.maxUtilization()));
    }
    @Test void aPublishedTwoDayPlanMeasuresOnlyItsBookedResourcesAndCountsOverloadOnce() {
        var source = published(List.of(savedTask("100", "2026-10-03T08:00:00", "2026-10-04T18:00:00", "4", "1", "12"),
                savedTask("200", "2026-10-04", "2026-10-04", "1", "0", "13")),
            List.of(load("31", "100", "2026-10-03", "3", "12"),
                load("32", "100", "2026-10-04", "2", "12"),
                load("33", "200", "2026-10-04", "1", "13")),
            List.of(booking("41", "100", "23", "2026-10-03", "3"),
                booking("42", "100", "24", "2026-10-04", "2"),
                booking("43", "200", "25", "2026-10-04", "1")),
            List.of(activeCalendar("23", "2026-10-03", "12", "4", "6"),
                activeCalendar("24", "2026-10-04", "12", "2", "3"),
                activeCalendar("25", "2026-10-04", "13", "4", "1")));
        var observed = SavedScheduleCapacity.measure(source);
        assertNotNull(observed);
        assertEquals(0, new BigDecimal("150.0").compareTo(observed.maxUtilization()));
        assertEquals(1, observed.overloadedResources());
    }

    @Test void multiShiftBookingsSumPerDayButAnUnbookedCalendarNeverSuppliesEvidenceOrUtilization() {
        var source = published(List.of(savedTask("100", "2026-10-03", "2026-10-04", "5", "1", "12")),
            List.of(load("31", "100", "2026-10-03", "5", "12"),
                load("32", "100", "2026-10-04", "1", "12")),
            List.of(booking("41", "100", "23", "2026-10-03", "2"),
                booking("42", "100", "26", "2026-10-03", "3"),
                booking("43", "100", "24", "2026-10-04", "1")),
            List.of(activeCalendar("23", "2026-10-03", "12", "2", "3"),
                activeCalendar("26", "2026-10-03", "12", "4", "4"),
                activeCalendar("24", "2026-10-04", "12", "2", "3"),
                activeCalendar("99", "2026-10-03", "12", "1", "100")));
        var observed = SavedScheduleCapacity.measure(source);
        assertNotNull(observed);
        assertEquals(0, new BigDecimal("150.0").compareTo(observed.maxUtilization()));
        assertEquals(1, observed.overloadedResources());
    }

    @Test void aHolidayWithoutLoadOrBookingNeedsNoCalendar() {
        var source = published(List.of(savedTask("100", "2026-10-03", "2026-10-05", "2", "1", "12")),
            List.of(load("31", "100", "2026-10-03", "1", "12"),
                load("32", "100", "2026-10-05", "2", "12")),
            List.of(booking("41", "100", "23", "2026-10-03", "1"),
                booking("42", "100", "25", "2026-10-05", "2")),
            List.of(activeCalendar("23", "2026-10-03", "12", "8", "1"),
                activeCalendar("25", "2026-10-05", "12", "8", "2")));
        var observed = SavedScheduleCapacity.measure(source);
        assertNotNull(observed);
        assertEquals(0, new BigDecimal("25.0").compareTo(observed.maxUtilization()));
        assertEquals(0, observed.overloadedResources());
    }

    @Test void unpublishedOrBookinglessPlanCannotBorrowAnotherPlansCalendarOccupancy() {
        var source = oneDay();
        assertNull(SavedScheduleCapacity.measure(with(source, "status", "DRAFT")));
        assertNull(SavedScheduleCapacity.measure(with(source, "capacityBookings", List.of())));
        assertNull(SavedScheduleCapacity.measure(with(source, "taskDayLoads", List.of())));
        assertNull(SavedScheduleCapacity.measure(with(source, "status", null)));
    }

    @Test void missingOrWrongVersionAndTenantFailClosed() {
        var source = oneDay();
        assertNull(SavedScheduleCapacity.measure(with(source, "capacityCalcVersion", null)));
        assertNull(SavedScheduleCapacity.measure(with(source, "capacityCalcVersion", 1)));
        assertNull(SavedScheduleCapacity.measure(with(source, "tenantId", null)));
        assertNull(SavedScheduleCapacity.measure(with(source, "tenantId", "")));
        assertNull(SavedScheduleCapacity.measure(with(source, "planId", "2")));
    }

    @Test void rejectsTaskWhoseFirstPositiveLoadIsLaterThanItsSavedStart() {
        var source = published(List.of(savedTask("100", "2026-10-03", "2026-10-04", "2", "0", "12")),
            List.of(load("31", "100", "2026-10-04", "2", "12")),
            List.of(booking("41", "100", "24", "2026-10-04", "2")),
            List.of(activeCalendar("24", "2026-10-04", "12", "8", "2")));
        /** 每笔记录的工时都能核对一致，但缺失的第一天会虚构更长的跨度。 */
        assertNull(SavedScheduleCapacity.measure(source));
    }

    @Test void rejectsTaskWhoseLastPositiveLoadIsEarlierThanItsSavedEnd() {
        var source = published(List.of(savedTask("100", "2026-10-03", "2026-10-04", "2", "0", "12")),
            List.of(load("31", "100", "2026-10-03", "2", "12")),
            List.of(booking("41", "100", "23", "2026-10-03", "2")),
            List.of(activeCalendar("23", "2026-10-03", "12", "8", "2")));
        /** 仅有总和相符，不能把末尾无负荷的一天计入总工期。 */
        assertNull(SavedScheduleCapacity.measure(source));
    }

    @Test void oneDayTaskWithOnePositiveLoadAndBookingRemainsObservable() {
        var observed = SavedScheduleCapacity.measure(oneDay());
        assertNotNull(observed);
        assertEquals(0, new BigDecimal("50.0").compareTo(observed.maxUtilization()));
    }
    @Test void taskHoursMustExactlyEqualTheirDailyLoadsEvenAcrossDays() {
        var source = published(List.of(savedTask("100", "2026-10-03", "2026-10-04", "2", "1", "12")),
            List.of(load("31", "100", "2026-10-03", "1", "12"),
                load("32", "100", "2026-10-04", "2", "12")),
            List.of(booking("41", "100", "23", "2026-10-03", "1"),
                booking("42", "100", "24", "2026-10-04", "2")),
            List.of(activeCalendar("23", "2026-10-03", "12", "8", "2"),
                activeCalendar("24", "2026-10-04", "12", "8", "3")));
        assertNotNull(SavedScheduleCapacity.measure(source));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "tasks", 0, "processHours", "3")));
        assertNull(SavedScheduleCapacity.measure(with(source, "taskDayLoads", List.of(load("31", "100", "2026-10-03", "1", "12")))));
        assertNull(SavedScheduleCapacity.measure(with(source, "taskDayLoads", List.of(
            load("31", "100", "2026-10-03", "1", "12"), load("32", "100", "2026-10-04", "2", "12"),
            load("33", "100", "2026-10-04", "2", "12")))));
    }

    @Test void eachDailyLoadMustExactlyEqualThatTasksShiftBookings() {
        var source = oneDay();
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityBookings", 0, "bookedHours", "2")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "taskDayLoads", 0, "plannedHours", "2")));
        assertNull(SavedScheduleCapacity.measure(with(source, "capacityBookings", List.of(
            booking("41", "100", "23", "2026-10-03", "3"),
            booking("42", "100", "23", "2026-10-03", "3")))));
    }

    @Test void forgedDayLoadsAndDuplicateIdentityNeverCountAsScheduleEvidence() {
        var source = oneDay();
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "taskDayLoads", 0, "tenantId", "000001")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "taskDayLoads", 0, "planId", "2")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "taskDayLoads", 0, "taskId", "999")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "taskDayLoads", 0, "resourceId", "13")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "taskDayLoads", 0, "loadDate", "2026-10-04")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "taskDayLoads", 0, "plannedHours", "0")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "taskDayLoads", 0, "plannedHours", "-3")));
        assertNull(SavedScheduleCapacity.measure(with(source, "taskDayLoads", List.of(
            load("31", "100", "2026-10-03", "3", "12"), load("31", "100", "2026-10-03", "3", "12")))));
    }

    @Test void bookingMustBelongToSameTenantPlanTaskDateAndUniqueCalendar() {
        var source = oneDay();
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityBookings", 0, "tenantId", "000001")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityBookings", 0, "planId", "2")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityBookings", 0, "taskId", "999")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityBookings", 0, "loadDate", "2026-10-04")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityBookings", 0, "calendarId", "999")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityBookings", 0, "bookedHours", "0")));
        assertNull(SavedScheduleCapacity.measure(with(source, "capacityBookings", List.of(
            booking("41", "100", "23", "2026-10-03", "3"),
            booking("41", "100", "23", "2026-10-03", "3")))));
        assertNull(SavedScheduleCapacity.measure(with(source, "capacityBookings", List.of(
            booking("41", "100", "23", "2026-10-03", "1"),
            booking("42", "100", "23", "2026-10-03", "2")))));
    }

    @Test void bookingCalendarMustBeActiveUniqueAndMatchTenantResourceAndDate() {
        var source = oneDay();
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 0, "tenantId", "000001")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 0, "resourceId", "13")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 0, "resourceType", "LINE")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 0, "calendarDate", "2026-10-04")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 0, "status", "CLOSED")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 0, "effectiveHours", "0")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 0, "plannedHours", "2")));
        assertNull(SavedScheduleCapacity.measure(with(source, "capacityCalendars", List.of(
            activeCalendar("23", "2026-10-03", "12", "8", "4"),
            activeCalendar("23", "2026-10-03", "12", "8", "4")))));
    }

    @Test void calendarPlannedHoursMustCoverAllBookingsForThatShiftNotJustOneTask() {
        var source = published(List.of(savedTask("100", "2026-10-03", "2026-10-03", "2", "0", "12"),
                savedTask("200", "2026-10-03", "2026-10-03", "2", "0", "12")),
            List.of(load("31", "100", "2026-10-03", "2", "12"),
                load("32", "200", "2026-10-03", "2", "12")),
            List.of(booking("41", "100", "23", "2026-10-03", "2"),
                booking("42", "200", "23", "2026-10-03", "2")),
            List.of(activeCalendar("23", "2026-10-03", "12", "8", "3")));
        assertNull(SavedScheduleCapacity.measure(source));
        var observed = SavedScheduleCapacity.measure(with(source, "capacityCalendars",
            List.of(activeCalendar("23", "2026-10-03", "12", "8", "5"))));
        assertNotNull(observed);
        assertEquals(0, new BigDecimal("62.5").compareTo(observed.maxUtilization()));
    }

    @Test void calendarLedgerMustBeNumericWithinPlannedHoursAndCoverOwnBookings() {
        var source = oneDay(); /** 本计划预订工时为 3，同租户全部方案的账册合计与计划工时均为 4。 */
        assertNotNull(SavedScheduleCapacity.measure(source));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 0, "ledgerHours", null)));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 0, "ledgerHours", "abc")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 0, "ledgerHours", "-1")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 0, "ledgerHours", "0")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 0, "ledgerHours", "2")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 0, "ledgerHours", "5")));
    }

    @Test void bookedCalendarAllowsExistingPlannedHoursBeyondAllPlanBookingLedger() {
        var source = rowWith(oneDay(), "capacityCalendars", 0, "plannedHours", "5");
        /** 本计划预订工时为 3，全部方案预订工时为 4，planned=5 包含合法的既有工时。 */
        var observed = SavedScheduleCapacity.measure(source);
        assertNotNull(observed);
        assertEquals(0, new BigDecimal("62.5").compareTo(observed.maxUtilization()));
        assertEquals(0, observed.overloadedResources());
    }

    @Test void idleActiveCalendarWithZeroEffectiveHoursDoesNotInvalidateBookedDay() {
        var source = oneDay(); /** C1：本计划预订工时为 3，有效产能为正值 8。 */
        var idle = activeCalendar("99", "2026-10-04", "12", "0", "0"); /** C2：范围相同，无负荷。 */
        var observed = SavedScheduleCapacity.measure(with(source, "capacityCalendars", List.of(
            activeCalendar("23", "2026-10-03", "12", "8", "4"), idle)));
        assertNotNull(observed);
        assertEquals(0, new BigDecimal("50.0").compareTo(observed.maxUtilization()));
        assertEquals(0, observed.overloadedResources());
    }
    @Test void bookedCalendarWithZeroEffectiveHoursStillFailsClosed() {
        assertNull(SavedScheduleCapacity.measure(rowWith(oneDay(), "capacityCalendars", 0,
            "effectiveHours", "0")));
    }

    @Test void idleCalendarWithInvalidFactsStillFailsClosed() {
        var source = with(oneDay(), "capacityCalendars", List.of(
            activeCalendar("23", "2026-10-03", "12", "8", "4"),
            activeCalendar("99", "2026-10-04", "12", "0", "0")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 1, "effectiveHours", null)));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 1, "effectiveHours", "-1")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 1, "plannedHours", null)));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 1, "plannedHours", "-1")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 1, "ledgerHours", null)));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 1, "ledgerHours", "-1")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 1, "resourceId", null)));
    }

    @Test void zeroEffectiveHoursCannotCoverPositivePlannedHoursEvenWithoutOwnBooking() {
        var source = with(oneDay(), "capacityCalendars", List.of(
            activeCalendar("23", "2026-10-03", "12", "8", "4"),
            with(activeCalendar("99", "2026-10-04", "12", "0", "5"), "ledgerHours", "0")));
        assertNull(SavedScheduleCapacity.measure(source));
    }
    @Test void unbookedCalendarMayHaveLegacyPlannedHoursWithZeroLedger() {
        var source = oneDay();
        var legacyCalendar = with(activeCalendar("99", "2026-10-03", "12", "8", "5"), "ledgerHours", "0");
        var observed = SavedScheduleCapacity.measure(with(source, "capacityCalendars", List.of(
            activeCalendar("23", "2026-10-03", "12", "8", "4"), legacyCalendar)));
        assertNotNull(observed);
        /** 未被预订的日历不得抬高本计划观测到的 4/8 利用率。 */
        assertEquals(0, new BigDecimal("50.0").compareTo(observed.maxUtilization()));
    }

    @Test void fullBookingLedgerAboveCalendarPlannedHoursFailsEvenWhenOwnBookingFits() {
        var source = oneDay(); /** 本计划预订工时为 3，全部方案的账册合计为 4。 */
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 0, "plannedHours", "3")));
        assertNull(SavedScheduleCapacity.measure(rowWith(source, "capacityCalendars", 0, "ledgerHours", "5")));
    }

    @Test void aBookedFourHoursCannotCertifyADraftOrUnbookedTwoHourPlan() {
        var planA = published(List.of(savedTask("100", "2026-10-03", "2026-10-03", "4", "0", "12")),
            List.of(load("31", "100", "2026-10-03", "4", "12")),
            List.of(booking("41", "100", "23", "2026-10-03", "4")),
            List.of(activeCalendar("23", "2026-10-03", "12", "8", "4")));
        assertNotNull(SavedScheduleCapacity.measure(planA));

        var b = published(List.of(savedTask("200", "2026-10-03", "2026-10-03", "2", "0", "12")),
            List.of(load("32", "200", "2026-10-03", "2", "12")), List.of(),
            List.of(activeCalendar("23", "2026-10-03", "12", "8", "4")));
        b = with(b, "planId", "2");
        b = rowWith(b, "taskDayLoads", 0, "planId", "2");
        assertNull(SavedScheduleCapacity.measure(b)); /** A 的 4 小时预订不能证明 B 的 2 小时预订。 */

        var bDraft = with(b, "status", "DRAFT");
        bDraft = with(bDraft, "capacityBookings", List.of(with(booking("42", "200", "23", "2026-10-03", "2"),
            "planId", "2")));
        bDraft = with(bDraft, "capacityCalendars", List.of(activeCalendar("23", "2026-10-03", "12", "8", "6")));
        assertNull(SavedScheduleCapacity.measure(bDraft)); /** 即使存在本计划自身的预订，也不代表草稿已发布。 */

        /** 即使 planned=6，全部方案账册合计为 1 也不能覆盖 B 自身预订的 2 小时。 */
        var bWithInsufficientLedger = with(bDraft, "status", "PUBLISHED");
        bWithInsufficientLedger = rowWith(bWithInsufficientLedger, "capacityCalendars", 0, "ledgerHours", "1");
        assertNull(SavedScheduleCapacity.measure(bWithInsufficientLedger));
    }
    @Test void malformedDatesFailClosedInsteadOfEscapingFromMeasure() {
        assertNull(SavedScheduleCapacity.measure(rowWith(oneDay(), "taskDayLoads", 0, "loadDate", "2026-02-30")));
        assertNull(SavedScheduleCapacity.measure(rowWith(oneDay(), "capacityCalendars", 0, "calendarDate", "2026-02-30")));
        assertNull(SavedScheduleCapacity.measure(rowWith(oneDay(), "tasks", 0, "startTime", "2026-02-30")));
    }
}
