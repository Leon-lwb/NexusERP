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
import java.util.Comparator;
import java.util.Date;
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
    List<String> warnings,
    List<WorkOrderSalesLinkFact> workOrderSalesLinks
) implements Serializable {

    private static final long JS_MAX_SAFE_INTEGER = 9007199254740991L;

    /** Keep the previous Java constructor descriptor for already compiled clients. */
    public DeliveryContextSnapshot(DecisionBusinessRef rootBusinessRef, List<OrderFact> salesOrders,
                                   List<WorkOrderFact> workOrders, Map<Long, List<RoutingStepFact>> routingByMaterial,
                                   List<RequirementFact> requirements, List<StockFact> stocks,
                                   List<ArrivalFact> arrivals, Map<LocalDate, Map<String, BigDecimal>> capacityByDate,
                                   List<LocalDate> holidays, Map<String, Object> currentSchedule,
                                   List<SupplierMetricFact> supplierMetrics, String snapshotHash,
                                   LocalDateTime snapshotAt, List<String> warnings) {
        this(rootBusinessRef, salesOrders, workOrders, routingByMaterial, requirements, stocks, arrivals,
            capacityByDate, holidays, currentSchedule, supplierMetrics, snapshotHash, snapshotAt, warnings, List.of());
    }

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
        workOrderSalesLinks = copyWorkOrderSalesLinks(workOrderSalesLinks);
    }

    private static List<WorkOrderSalesLinkFact> copyWorkOrderSalesLinks(List<WorkOrderSalesLinkFact> source) {
        if (source == null) return List.of(); // missing in legacy JSON payloads
        List<WorkOrderSalesLinkFact> copy = new ArrayList<>(source);
        if (copy.contains(null)) throw new IllegalArgumentException("工单销售订单关系不能为空");
        copy.sort(Comparator.comparing(WorkOrderSalesLinkFact::workOrderId)
            .thenComparing(WorkOrderSalesLinkFact::salesOrderId));
        return List.copyOf(copy);
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

    /** Audit/view data only: algorithms consume typed facts and the APS port, never cast schedule values. */
    private static Map<String, Object> copySchedule(Map<String, Object> source) {
        if (source == null) return null;
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, copyScheduleEntry(key, value)));
        return Collections.unmodifiableMap(copy);
    }

    /** ID conventions apply to keys, never to types such as java.util.Date. */
    private static Object copyScheduleEntry(String key, Object value) {
        if (key == null) throw new IllegalArgumentException("排产快照 Map 的键必须为 String");
        if (isIdKey(key)) return decimalId(value, key);
        if (isIdListKey(key)) {
            if (value == null) return null;
            if (!(value instanceof List<?> ids)) {
                throw new IllegalArgumentException("排产快照的 ID 列表必须是 List: " + key);
            }
            List<Object> copy = new ArrayList<>(ids.size());
            ids.forEach(id -> copy.add(decimalId(id, key)));
            return Collections.unmodifiableList(copy);
        }
        return copyScheduleValue(value);
    }

    private static boolean isIdKey(String key) {
        return key.equalsIgnoreCase("id") || key.endsWith("Id") || key.endsWith("ID")
            || key.endsWith("_id") || key.endsWith("_ID");
    }

    private static boolean isIdListKey(String key) {
        return key.equalsIgnoreCase("ids") || key.endsWith("Ids") || key.endsWith("IDs")
            || key.endsWith("_ids") || key.endsWith("_IDS");
    }

    private static String decimalId(Object value, String key) {
        if (value == null) return null;
        String id;
        if (value instanceof String text) {
            id = text;
        } else if (value instanceof Long || value instanceof Integer || value instanceof Short
            || value instanceof Byte || value instanceof BigInteger) {
            id = value.toString();
        } else {
            throw new IllegalArgumentException("排产快照 ID 必须是整数或十进制字符串: " + key);
        }
        if (!id.matches("[0-9]+")) {
            throw new IllegalArgumentException("排产快照 ID 必须是十进制字符串: " + key);
        }
        return id;
    }

    /** Canonical JSON-safe scalars keep the hash and JSON round-trip stable under production Jackson. */
    private static Object copyScheduleValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                if (!(key instanceof String name)) {
                    throw new IllegalArgumentException("排产快照 Map 的键必须为 String");
                }
                copy.put(name, copyScheduleEntry(name, nested));
            });
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(element -> copy.add(copyScheduleValue(element)));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Long number) {
            if (number > JS_MAX_SAFE_INTEGER || number < -JS_MAX_SAFE_INTEGER) {
                throw new IllegalArgumentException("排产快照的非 ID Long 超出 JS 安全整数范围");
            }
            if (number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
                return Integer.valueOf(number.intValue());
            }
            return number;
        }
        if (value instanceof BigDecimal decimal) return decimal.toPlainString();
        if (value instanceof BigInteger integer) return integer.toString();
        if (value instanceof LocalDate || value instanceof LocalDateTime || value instanceof Instant
            || value instanceof OffsetDateTime || value instanceof ZonedDateTime || value instanceof UUID) {
            return value.toString();
        }
        if (value instanceof Date date) return Instant.ofEpochMilli(date.getTime()).toString();
        if (value instanceof Enum<?> code) return code.name();
        if (value instanceof Character character) return character.toString();
        if (value instanceof Float number) {
            if (!Float.isFinite(number)) throw new IllegalArgumentException("排产快照不接受非有限数值");
            return Double.valueOf(number.toString());
        }
        if (value instanceof Double number) {
            if (!Double.isFinite(number)) throw new IllegalArgumentException("排产快照不接受非有限数值");
            return number;
        }
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Byte
            || value instanceof Short || value instanceof Integer) {
            return value;
        }
        throw new IllegalArgumentException("排产快照包含不支持的可变值: " + value.getClass().getName());
    }

    public record WorkOrderSalesLinkFact(Long workOrderId, Long salesOrderId) implements Serializable {
        public WorkOrderSalesLinkFact {
            if (workOrderId == null || workOrderId <= 0 || salesOrderId == null || salesOrderId <= 0) {
                throw new IllegalArgumentException("工单销售订单关系 ID 必须为正数");
            }
        }
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
