package com.nexus.common.trace;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
/** One actual MES work-order consumption record. Source quota IDs remain in its immutable evidence. */
public record CompletionMaterialAllocation(@JsonFormat(shape=JsonFormat.Shape.STRING) Long allocationId,@JsonFormat(shape=JsonFormat.Shape.STRING) Long materialId,@JsonFormat(shape=JsonFormat.Shape.STRING) Long reservationId,@JsonFormat(shape=JsonFormat.Shape.STRING) Long reservationLineId,
    @JsonFormat(shape=JsonFormat.Shape.STRING) Long postingGroupId,@JsonFormat(shape=JsonFormat.Shape.STRING) Long identityId,@JsonFormat(shape=JsonFormat.Shape.STRING) Long sourceStockId,@JsonFormat(shape=JsonFormat.Shape.STRING) Long erpBatchId,
    @JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal quantity,@JsonFormat(shape=JsonFormat.Shape.STRING) long sourceVersion,String evidenceHash,String sourceEventId) { }
