package com.nexus.common.trace;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.*;
/** Each section declares its own scope and pagination; missing historical evidence is never reconstructed. */
public record IdentityTracePart(String module,@JsonFormat(shape=JsonFormat.Shape.STRING) Long identityId,@JsonFormat(shape=JsonFormat.Shape.STRING) Long localSnId,String canonicalSnCode,String scope,
    Map<String,TracePage<Map<String,Object>>> sections,List<String> evidenceGaps,String localSnCode) {
    public IdentityTracePart(String module,Long identityId,Long localSnId,String canonicalSnCode,String scope,Map<String,TracePage<Map<String,Object>>> sections,List<String> evidenceGaps){this(module,identityId,localSnId,canonicalSnCode,scope,sections,evidenceGaps,null);}
    public IdentityTracePart {sections=Map.copyOf(sections);evidenceGaps=List.copyOf(evidenceGaps);}
}
