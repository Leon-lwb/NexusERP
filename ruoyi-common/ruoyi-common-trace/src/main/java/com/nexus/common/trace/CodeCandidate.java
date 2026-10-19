package com.nexus.common.trace;
import com.fasterxml.jackson.annotation.JsonFormat;
/** 一个已通过访问范围检查的扫码候选。编码为正式编码，原扫码内容保留在调用方请求。 */
public record CodeCandidate(CodeObjectType objectType, @JsonFormat(shape=JsonFormat.Shape.STRING) Long objectId, @JsonFormat(shape=JsonFormat.Shape.STRING) Long identityId, @JsonFormat(shape=JsonFormat.Shape.STRING) Long localSnId,
                            String canonicalSnCode, @JsonFormat(shape=JsonFormat.Shape.STRING) Long materialId, String materialCode,
                            @JsonFormat(shape=JsonFormat.Shape.STRING) Long batchId, String batchCode, TrackingPolicy policy, String documentType, String documentCode) {
    public CodeCandidate(CodeObjectType type,Long objectId,Long identityId,Long localSnId,String code,Long materialId,
        String materialCode,Long batchId,String batchCode,TrackingPolicy policy) {
        this(type,objectId,identityId,localSnId,code,materialId,materialCode,batchId,batchCode,policy,null,null);
    }
    public CodeCandidate(CodeObjectType type, Long identityId, Long localSnId, String code, Long materialId,
                         String materialCode, Long batchId, String batchCode, TrackingPolicy policy) {
        this(type, type == CodeObjectType.SN ? identityId : type == CodeObjectType.BATCH ? batchId : materialId,
            identityId,localSnId,code,materialId,materialCode,batchId,batchCode,policy);
    }
    public String snCode() { return canonicalSnCode; }
}
