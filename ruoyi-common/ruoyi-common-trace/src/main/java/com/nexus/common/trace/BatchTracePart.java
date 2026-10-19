package com.nexus.common.trace;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.*;
/** A batch is a quantity scope; it never impersonates an individual serial identity. */
public record BatchTracePart(String module,@JsonFormat(shape=JsonFormat.Shape.STRING) Long batchId,String scope,
    Map<String,TracePage<Map<String,Object>>> sections,List<String> evidenceGaps){
    public BatchTracePart{sections=Map.copyOf(sections);evidenceGaps=List.copyOf(evidenceGaps);}
}
