package com.nexus.common.ai.decision.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 已发布排程的内部资源观测负荷：内部任务须与逐日负荷及本计划班次预订核对；
 * 委外任务校验身份、时间和工时，但不借用或虚构内部资源产能。
 * 日历 plannedHours 须覆盖同租户全方案预订合计 ledgerHours，后者须覆盖本计划预订；
 * 差额可能是既有计划工时，不能据此推断归属；同库账册不能证明特权改库后事实的真实性。
 */
public final class SavedScheduleCapacity {
    private SavedScheduleCapacity() { }

    public static Observation measure(Map<String, Object> schedule) {
        if (schedule == null || positiveId(schedule.get("planId")) == null
            || !(schedule.get("tenantId") instanceof String tenant) || tenant.isBlank()
            || !"PUBLISHED".equals(schedule.get("status"))
            || !Integer.valueOf(2).equals(schedule.get("capacityCalcVersion"))
            || !(schedule.get("tasks") instanceof List<?> tasks) || tasks.isEmpty() || tasks.size() > 1000
            || !(schedule.get("taskDayLoads") instanceof List<?> loads) || loads.isEmpty() || loads.size() > 100000
            || !(schedule.get("capacityBookings") instanceof List<?> bookings)
            || bookings.isEmpty() || bookings.size() > 100000
            || !(schedule.get("capacityCalendars") instanceof List<?> calendars)
            || calendars.isEmpty() || calendars.size() > 10000) return null;

        String planId = (String) schedule.get("planId");
        try {
            Map<String, Task> taskById = new HashMap<>();
            for (Object item : tasks) {
                if (!(item instanceof Map<?, ?> row)) return null;
                String id = positiveId(row.get("taskId"));
                String resource = positiveId(row.get("resourceId"));
                String type = type(row.get("resourceType"));
                boolean outsourced = "OUTSOURCE".equals(row.get("resourceType"));
                LocalDate start = taskDate(row.get("startTime"));
                LocalDate end = taskDate(row.get("endTime"));
                BigDecimal process = decimal(row.get("processHours"));
                BigDecimal setup = row.get("setupHours") == null ? BigDecimal.ZERO : decimal(row.get("setupHours"));
                if (id == null || positiveId(row.get("woId")) == null
                    || (outsourced ? row.get("resourceId") != null : resource == null || type == null)
                    || start == null || end == null || start.isAfter(end)
                    || ChronoUnit.DAYS.between(start, end) > 90
                    || process == null || process.signum() < 0 || setup == null || setup.signum() < 0
                    || (!outsourced && process.add(setup).signum() <= 0)
                    || taskById.putIfAbsent(id, new Task(start, end,
                        outsourced ? "OUTSOURCE" : type, resource, process.add(setup))) != null)
                    return null;
            }

            Map<String, Calendar> calendarById = new HashMap<>();
            for (Object item : calendars) {
                if (!(item instanceof Map<?, ?> row)) return null;
                String id = positiveId(row.get("calendarId"));
                LocalDate day = isoDay(row.get("calendarDate"));
                String resource = positiveId(row.get("resourceId"));
                String type = type(row.get("resourceType"));
                BigDecimal planned = decimal(row.get("plannedHours"));
                BigDecimal ledger = decimal(row.get("ledgerHours"));
                BigDecimal effective = decimal(row.get("effectiveHours"));
                if (id == null || day == null || resource == null || type == null
                    || !tenant.equals(row.get("tenantId")) || !"ACTIVE".equals(row.get("status"))
                    || planned == null || planned.signum() < 0 || ledger == null
                    || ledger.compareTo(planned) > 0 || effective == null || effective.signum() < 0
                    || (effective.signum() == 0 && planned.signum() > 0)
                    || calendarById.putIfAbsent(id, new Calendar(day, type, resource, planned, effective, ledger)) != null)
                    return null;
            }

            Map<TaskDay, BigDecimal> daily = new HashMap<>();
            Map<String, BigDecimal> taskLoadTotals = new HashMap<>();
            Map<String, LocalDate> firstPositiveDays = new HashMap<>();
            Map<String, LocalDate> lastPositiveDays = new HashMap<>();
            Set<String> loadIds = new HashSet<>();
            for (Object item : loads) {
                if (!(item instanceof Map<?, ?> row)) return null;
                String id = positiveId(row.get("loadId"));
                String taskId = positiveId(row.get("taskId"));
                Task task = taskById.get(taskId);
                LocalDate day = isoDay(row.get("loadDate"));
                BigDecimal hours = positiveHours(row.get("plannedHours"));
                if (id == null || !loadIds.add(id) || !planId.equals(row.get("planId"))
                    || !tenant.equals(row.get("tenantId")) || task == null
                    || "OUTSOURCE".equals(task.type) || day == null
                    || day.isBefore(task.start) || day.isAfter(task.end)
                    || !task.type.equals(row.get("resourceType")) || !task.resource.equals(row.get("resourceId"))
                    || hours == null || daily.putIfAbsent(new TaskDay(taskId, day), hours) != null)
                    return null;
                taskLoadTotals.merge(taskId, hours, BigDecimal::add);
                firstPositiveDays.merge(taskId, day, (a, b) -> a.isBefore(b) ? a : b);
                lastPositiveDays.merge(taskId, day, (a, b) -> a.isAfter(b) ? a : b);
            }
            for (var entry : taskById.entrySet()) {
                Task task = entry.getValue();
                /** 委外工时是任务事实，不属于可由内部日历/班次预订观测的产能。 */
                if ("OUTSOURCE".equals(task.type)) continue;
                String taskId = entry.getKey();
                if (task.hours.compareTo(taskLoadTotals.getOrDefault(taskId, BigDecimal.ZERO)) != 0
                    || !task.start.equals(firstPositiveDays.get(taskId))
                    || !task.end.equals(lastPositiveDays.get(taskId))) return null;
            }

            Map<TaskDay, BigDecimal> bookedByDay = new HashMap<>();
            Map<String, BigDecimal> bookedByCalendar = new HashMap<>();
            Set<String> bookingIds = new HashSet<>();
            Set<TaskCalendar> taskCalendars = new HashSet<>();
            for (Object item : bookings) {
                if (!(item instanceof Map<?, ?> row)) return null;
                String id = positiveId(row.get("bookingId"));
                String taskId = positiveId(row.get("taskId"));
                String calendarId = positiveId(row.get("calendarId"));
                LocalDate day = isoDay(row.get("loadDate"));
                BigDecimal hours = positiveHours(row.get("bookedHours"));
                Task task = taskById.get(taskId);
                Calendar calendar = calendarById.get(calendarId);
                if (id == null || !bookingIds.add(id) || !planId.equals(row.get("planId"))
                    || !tenant.equals(row.get("tenantId")) || task == null
                    || "OUTSOURCE".equals(task.type) || calendar == null
                    || day == null || hours == null || !daily.containsKey(new TaskDay(taskId, day))
                    || !calendar.day.equals(day) || !calendar.type.equals(task.type)
                    || !calendar.resource.equals(task.resource)
                    || !taskCalendars.add(new TaskCalendar(taskId, calendarId)))
                    return null;
                bookedByDay.merge(new TaskDay(taskId, day), hours, BigDecimal::add);
                bookedByCalendar.merge(calendarId, hours, BigDecimal::add);
            }
            for (var entry : daily.entrySet()) {
                if (entry.getValue().compareTo(bookedByDay.getOrDefault(entry.getKey(), BigDecimal.ZERO)) != 0)
                    return null;
            }

            /**
             * 观测只可纳入本计划已核验预订实际引用的日历。
             * 计划工时可能包含账册之外的历史占用；仅本计划的预订可证明本计划自身的负荷。
             */
            Map<Key, Hours> observed = new HashMap<>();
            for (var entry : bookedByCalendar.entrySet()) {
                Calendar calendar = calendarById.get(entry.getKey());
                if (calendar.ledger.compareTo(entry.getValue()) < 0 || calendar.effective.signum() <= 0) return null;
                observed.merge(new Key(calendar.day, calendar.type, calendar.resource),
                    new Hours(calendar.planned, calendar.effective),
                    (a, b) -> new Hours(a.planned.add(b.planned), a.effective.add(b.effective)));
            }
            if (observed.isEmpty()) return null;
            BigDecimal max = BigDecimal.ZERO;
            Set<String> overloaded = new HashSet<>();
            for (var entry : observed.entrySet()) {
                Key key = entry.getKey();
                Hours hours = entry.getValue();
                BigDecimal rate = hours.planned.multiply(BigDecimal.valueOf(100))
                    .divide(hours.effective, 4, RoundingMode.HALF_UP);
                if (rate.compareTo(BigDecimal.valueOf(100)) > 0) overloaded.add(key.type + ":" + key.resource);
                max = max.max(rate);
            }
            return new Observation(max.setScale(1, RoundingMode.HALF_UP), overloaded.size());
        } catch (ArithmeticException | DateTimeException badFact) {
            return null;
        }
    }

    private static LocalDate taskDate(Object value) {
        if (!(value instanceof String text)) return null;
        if (text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return LocalDate.parse(text);
        if (text.length() > 32 || !text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T.*")) return null;
        return LocalDateTime.parse(text).toLocalDate();
    }
    private static LocalDate isoDay(Object value) {
        if (!(value instanceof String text) || !text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return null;
        return LocalDate.parse(text);
    }
    private static String positiveId(Object value) {
        if (!(value instanceof String text) || !text.matches("[1-9][0-9]{0,18}")) return null;
        try { Long.parseLong(text); return text; }
        catch (NumberFormatException invalid) { return null; }
    }
    private static String type(Object value) {
        if (!(value instanceof String text) || !Set.of("WORKSTATION", "WORKSHOP", "LINE").contains(text))
            return null;
        return text;
    }
    private static BigDecimal decimal(Object value) {
        if (!(value instanceof String text) || text.length() > 32 || !text.matches("[0-9]+(\\.[0-9]+)?"))
            return null;
        try { return new BigDecimal(text); }
        catch (NumberFormatException invalid) { return null; }
    }
    private static BigDecimal positiveHours(Object value) {
        BigDecimal hours = decimal(value);
        return hours != null && hours.signum() > 0 ? hours : null;
    }
    private record Task(LocalDate start, LocalDate end, String type, String resource, BigDecimal hours) { }
    private record Calendar(LocalDate day, String type, String resource, BigDecimal planned,
                            BigDecimal effective, BigDecimal ledger) { }
    private record TaskDay(String taskId, LocalDate day) { }
    private record TaskCalendar(String taskId, String calendarId) { }
    private record Key(LocalDate day, String type, String resource) { }
    private record Hours(BigDecimal planned, BigDecimal effective) { }
    public record Observation(BigDecimal maxUtilization, int overloadedResources) { }
}
