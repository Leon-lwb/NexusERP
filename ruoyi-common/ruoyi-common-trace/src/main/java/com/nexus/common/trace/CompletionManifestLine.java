package com.nexus.common.trace;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
/** 一条冻结交付事实；SN行一件，非SN行按批次数量。 */
public record CompletionManifestLine(@JsonFormat(shape=JsonFormat.Shape.STRING) long lineNo,@JsonFormat(shape=JsonFormat.Shape.STRING) Long identityId,@JsonFormat(shape=JsonFormat.Shape.STRING) Long localSnId,String snCode,@JsonFormat(shape=JsonFormat.Shape.STRING) Long batchId,
    @JsonFormat(shape=JsonFormat.Shape.STRING) Long localLotId,@JsonFormat(shape=JsonFormat.Shape.STRING) Long materialId,@JsonFormat(shape=JsonFormat.Shape.STRING) BigDecimal quantity,
    String result,@JsonFormat(shape=JsonFormat.Shape.STRING) long sourceVersion) { }
