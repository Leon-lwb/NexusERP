package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

/**
 * 已发生但尚未分配至具体产品的正向实际用料，只能建立工单范围的可能影响。
 *
 * @param module 事实所属业务模块。
 * @param consumptionId 已经发生的工单实际消耗记录。
 * @param mesWoId MES 生产工单主键。
 * @param componentMaterialId 实际消耗的组件物料标识。
 * @param consumedQuantity 已经记录的实际消耗数量。
 * @param allocatedQuantity 已经分配到具体产品的净用量。
 */
public record WorkOrderTraceImpact(
        String module,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long consumptionId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long mesWoId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long componentMaterialId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal consumedQuantity,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal allocatedQuantity) {
    public String scope() {
        return "WORK_ORDER";
    }

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public BigDecimal unallocatedQuantity() {
        return consumedQuantity.subtract(allocatedQuantity);
    }
}
