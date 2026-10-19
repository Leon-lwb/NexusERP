package com.nexus.common.trace;
import com.fasterxml.jackson.annotation.JsonFormat;
/** The component snapshot is sealed in the same MES transaction as the product manifest. */
public record CompletionAllocationManifest(String tenantId,@JsonFormat(shape=JsonFormat.Shape.STRING) Long manifestId,int revision,@JsonFormat(shape=JsonFormat.Shape.STRING) long lineCount,String contentHash) { }
