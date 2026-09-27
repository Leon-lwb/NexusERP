package com.nexus.common.ai.decision.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 已保存任务使用资源的观测负荷，不是 APS 假设方案的利用率。
 * 日期缺失或来源事实异常时返回未知，不虚构零值。
 */
public final class SavedScheduleCapacity {
    private SavedScheduleCapacity() { }

    public static Observation measure(Map<String, Object> schedule) {
        if (schedule == null || positiveId(schedule.get("planId")) == null
            || !(schedule.get("tasks") instanceof List<?> tasks) || tasks.isEmpty() || tasks.size() > 1000
            || !(schedule.get("capacityCalendars") instanceof List<?> calendars)
            || calendars.isEmpty() || calendars.size() > 10000) return null;
        // 限定范围的已保存计划可能与其他资源日历共用同一车间。
        // 基线仅使用任务实际关联资源的日历。
        Set<String> taskResources = new HashSet<>();
        for (Object item : tasks) {
            if (!(item instanceof Map<?, ?> row)) return null;
            String type = type(row.get("resourceType"));
            String resource = positiveId(row.get("resourceId"));
            if (type == null || resource == null) return null;
            taskResources.add(type + ":" + resource);
        }
        Map<Key, Hours> available = new HashMap<>();
        Set<String> ids = new HashSet<>();
        try {
            for (Object item : calendars) {
                if (!(item instanceof Map<?, ?> row)) return null;
                String id = positiveId(row.get("calendarId"));
                LocalDate day = date(row.get("calendarDate"));
                String resource = positiveId(row.get("resourceId"));
                String type = type(row.get("resourceType"));
                if (resource == null || type == null) return null;
                if (!taskResources.contains(type + ":" + resource)) continue;
                BigDecimal planned = decimal(row.get("plannedHours"));
                BigDecimal effective = decimal(row.get("effectiveHours"));
                if (id == null || !ids.add(id) || day == null || resource == null || type == null
                    || planned == null || planned.signum() < 0 || effective == null || effective.signum() <= 0)
                    return null;
                available.merge(new Key(day, type, resource), new Hours(planned, effective),
                    (a, b) -> new Hours(a.planned.add(b.planned), a.effective.add(b.effective)));
            }
            Set<Key> covered = new HashSet<>();
            Map<String, BigDecimal> taskHoursByResource = new HashMap<>();
            for (Object item : tasks) {
                if (!(item instanceof Map<?, ?> row) || positiveId(row.get("taskId")) == null
                    || positiveId(row.get("woId")) == null) return null;
                LocalDate start = date(row.get("startTime"));
                LocalDate end = date(row.get("endTime"));
                String resource = positiveId(row.get("resourceId"));
                String type = type(row.get("resourceType"));
                BigDecimal process = decimal(row.get("processHours"));
                BigDecimal setup = row.get("setupHours") == null ? BigDecimal.ZERO : decimal(row.get("setupHours"));
                if (process == null || process.signum() < 0 || setup == null || setup.signum() < 0)
                    return null;
                taskHoursByResource.merge(type + ":" + resource, process.add(setup), BigDecimal::add);
                if (start == null || end == null || resource == null || type == null
                    || ChronoUnit.DAYS.between(start, end) < 0 || ChronoUnit.DAYS.between(start, end) > 90)
                    return null;
                for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                    Key key = new Key(day, type, resource);
                    if (!available.containsKey(key)) return null;
                    covered.add(key);
                }
            }
            if (covered.isEmpty()) return null;
            Map<String, BigDecimal> plannedByResource = new HashMap<>();
            for (Key key : covered) {
                plannedByResource.merge(key.type + ":" + key.resource,
                    available.get(key).planned, BigDecimal::add);
            }
            if (taskHoursByResource.entrySet().stream().anyMatch(entry ->
                plannedByResource.getOrDefault(entry.getKey(), BigDecimal.ZERO).compareTo(entry.getValue()) < 0))
                return null;
            BigDecimal max = BigDecimal.ZERO;
            Set<String> overloaded = new HashSet<>();
            for (Key key : covered) {
                Hours hours = available.get(key);
                BigDecimal rate = hours.planned.multiply(BigDecimal.valueOf(100))
                    .divide(hours.effective, 4, RoundingMode.HALF_UP);
                if (rate.compareTo(BigDecimal.valueOf(100)) > 0) overloaded.add(key.type + ":" + key.resource);
                max = max.max(rate);
            }
            return new Observation(max.setScale(1, RoundingMode.HALF_UP), overloaded.size());
        } catch (ArithmeticException | IllegalArgumentException badFact) {
            return null;
        }
    }

    private static LocalDate date(Object value) {
        if (!(value instanceof String text) || !text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}($|T.*)"))
            return null;
        return LocalDate.parse(text.substring(0, 10));
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
        if (!(value instanceof String text) || text.length() > 32) return null;
        try { return new BigDecimal(text); }
        catch (NumberFormatException invalid) { return null; }
    }
    private record Key(LocalDate day, String type, String resource) { }
    private record Hours(BigDecimal planned, BigDecimal effective) { }
    public record Observation(BigDecimal maxUtilization, int overloadedResources) { }
}