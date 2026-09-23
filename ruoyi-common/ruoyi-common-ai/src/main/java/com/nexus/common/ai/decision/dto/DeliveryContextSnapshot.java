package com.nexus.common.ai.decision.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DeliveryContextSnapshot(
    DecisionBusinessRef rootBusinessRef,
    List<OrderFact> salesOrders,
    List<WorkOrderFact> workOrders,
    Map<Long, List<RoutingStepFact>> routingByMaterial,
    List<RequirementFact> requirements,
    List<StockFact> stocks,
    List<ArrivalFact> arrivals,
    Map<LocalDate, Map<String, BigDecimal>> capacityByDate,
    List<LocalDate> holidays,
    Map<String, Object> currentSchedule,
    List<SupplierMetricFact> supplierMetrics,
    String snapshotHash,
    LocalDateTime snapshotAt,
    List<String> warnings
) implements Serializable {

    public DeliveryContextSnapshot {
        salesOrders = copyList(salesOrders);
        workOrders = copyList(workOrders);
        routingByMaterial = copyRoutes(routingByMaterial);
        requirements = copyList(requirements);
        stocks = copyList(stocks);
        arrivals = copyList(arrivals);
        capacityByDate = copyCapacity(capacityByDate);
        holidays = copyList(holidays);
        currentSchedule = copySchedule(currentSchedule);
        supplierMetrics = copyList(supplierMetrics);
        warnings = copyList(warnings);
    }

    private static <T> List<T> copyList(List<T> source) {
        return source == null ? null : Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static Map<Long, List<RoutingStepFact>> copyRoutes(Map<Long, List<RoutingStepFact>> source) {
        if (source == null) return null;
        Map<Long, List<RoutingStepFact>> copy = new LinkedHashMap<>();
        source.forEach((materialId, steps) -> copy.put(materialId, copyList(steps)));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<LocalDate, Map<String, BigDecimal>> copyCapacity(
        Map<LocalDate, Map<String, BigDecimal>> source) {
        if (source == null) return null;
        Map<LocalDate, Map<String, BigDecimal>> copy = new LinkedHashMap<>();
        source.forEach((date, byResource) -> copy.put(date, byResource == null ? null
            : Collections.unmodifiableMap(new LinkedHashMap<>(byResource))));
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Object> copySchedule(Map<String, Object> source) {
        if (source == null) return null;
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, copyScheduleValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    /** The dynamic schedule is JSON-shaped; unsupported mutable values cannot be hashed safely. */
    private static Object copyScheduleValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                if (!(key instanceof String name)) {
                    throw new IllegalArgumentException("排产快照 Map 的键必须为 String");
                }
                copy.put(name, copyScheduleValue(nested));
            });
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(element -> copy.add(copyScheduleValue(element)));
            return Collections.unmodifiableList(copy);
        }
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Character
            || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
            || value instanceof Float || value instanceof Double || value instanceof BigInteger
            || value instanceof BigDecimal || value instanceof LocalDate || value instanceof LocalDateTime
            || value instanceof Instant || value instanceof OffsetDateTime || value instanceof ZonedDateTime
            || value instanceof UUID || value instanceof Enum<?>) {
            return value;
        }
        throw new IllegalArgumentException("排产快照包含不支持的可变值: " + value.getClass().getName());
    }

    public record OrderFact(Long id, String code, LocalDate promiseDate, String status) implements Serializable {
    }

    public record WorkOrderFact(Long id, String code, Long materialId, BigDecimal remainingQty,
                                LocalDate planEndDate, Long priority, Long workshopId) implements Serializable {
    }

    public record RoutingStepFact(Long routingId, Long stepId, Integer seqNum, Long workstationId,
                                  Long workshopId, BigDecimal standardHours) implements Serializable {
    }

    public record RequirementFact(Long materialId, String materialCode, String materialName,
                                  LocalDate requiredDate, BigDecimal requiredQty,
                                  Long workOrderId, String workOrderNo) implements Serializable {
    }

    public record StockFact(Long materialId, BigDecimal stockQty, BigDecimal reservedQty,
                            BigDecimal safetyStock) implements Serializable {
    }

    public record ArrivalFact(Long materialId, Long purchaseLineId, LocalDate expectedDate,
                              BigDecimal remainingQty, Long vendorId) implements Serializable {
    }

    public record SupplierMetricFact(Long vendorId, BigDecimal onTimeRate, int sampleCount,
                                     LocalDateTime observedAt) implements Serializable {
    }
}
