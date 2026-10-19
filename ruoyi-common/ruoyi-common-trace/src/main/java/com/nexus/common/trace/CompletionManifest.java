package com.nexus.common.trace;
import com.fasterxml.jackson.annotation.JsonFormat;
/** 已冻结的最终工单交付清单头，内容改变必须产生新修订。 */
public record CompletionManifest(@JsonFormat(shape=JsonFormat.Shape.STRING) Long manifestId,int revision,String tenantId,@JsonFormat(shape=JsonFormat.Shape.STRING) Long mesWoId,@JsonFormat(shape=JsonFormat.Shape.STRING) Long erpWoId,
    @JsonFormat(shape=JsonFormat.Shape.STRING) Long materialId,@JsonFormat(shape=JsonFormat.Shape.STRING) long lineCount,@JsonFormat(shape=JsonFormat.Shape.STRING) java.math.BigDecimal goodQty,
    @JsonFormat(shape=JsonFormat.Shape.STRING) java.math.BigDecimal scrapQty,String contentHash,String status) { }
