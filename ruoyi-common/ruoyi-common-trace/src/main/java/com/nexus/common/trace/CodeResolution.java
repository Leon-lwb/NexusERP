package com.nexus.common.trace;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
/** 稳定的多端解析结果；歧义结果不包含默认选中的根对象。 */
public record CodeResolution(ResolveStatus status, CodeObjectType objectType, @JsonFormat(shape=JsonFormat.Shape.STRING) Long objectId, @JsonFormat(shape=JsonFormat.Shape.STRING) Long identityId,
                             @JsonFormat(shape=JsonFormat.Shape.STRING) Long localSnId, String canonicalSnCode, String snCode, @JsonFormat(shape=JsonFormat.Shape.STRING) Long materialId,
                             String materialCode, @JsonFormat(shape=JsonFormat.Shape.STRING) Long batchId, String batchCode, TrackingPolicy policy,
                             List<CodeCandidate> candidates, String reason, String documentType, String documentCode) {
    public CodeResolution { candidates = candidates == null ? List.of() : List.copyOf(candidates); }
    public static CodeResolution resolved(CodeCandidate c) {
        return new CodeResolution(ResolveStatus.RESOLVED,c.objectType(),c.objectId(),c.identityId(),c.localSnId(),
            c.canonicalSnCode(),c.canonicalSnCode(),c.materialId(),c.materialCode(),c.batchId(),c.batchCode(),c.policy(),List.of(c),null,c.documentType(),c.documentCode());
    }
    public static CodeResolution ambiguous(List<CodeCandidate> candidates) {
        if (candidates == null || candidates.size() < 2) throw new IllegalArgumentException("歧义结果需要多个候选");
        return empty(ResolveStatus.AMBIGUOUS,candidates,"同一码匹配多个对象，请补充来源上下文");
    }
    public static CodeResolution missing(String reason) { return empty(ResolveStatus.NOT_FOUND,List.of(),reason); }
    public static CodeResolution invalid(String reason) { return empty(ResolveStatus.INVALID,List.of(),reason); }
    private static CodeResolution empty(ResolveStatus status,List<CodeCandidate> candidates,String reason) {
        return new CodeResolution(status,null,null,null,null,null,null,null,null,null,null,null,candidates,reason,null,null);
    }
}
