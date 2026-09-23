package com.nexus.common.ai.decision.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
