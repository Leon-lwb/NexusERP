package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

/** Positive actual use not yet assigned to individual products; it establishes work-order scope only. */
public record WorkOrderTraceImpact(String module,
    @JsonFormat(shape=JsonFormat.Shape.STRING) Long consumptionId,
    @JsonFormat(shape=JsonFormat.Shape.STRING) Long mesWoId,
    @JsonFormat(shape=JsonFormat.Shape.STRING) Long componentMaterialId,
    @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal consumedQuantity,
    @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal allocatedQuantity) {
    public String scope(){return "WORK_ORDER";}
    @JsonFormat(shape=JsonFormat.Shape.STRING)
    public BigDecimal unallocatedQuantity(){return consumedQuantity.subtract(allocatedQuantity);}
}
