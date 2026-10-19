package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Set;

/** 取消应用结果，不携带或伪造生产收货单、库存过账组。 */
public record CompletionCancellationResult(
    String sourceEventId,String cancellationEventId,String originalEventId,String originalEventHash,
    @JsonFormat(shape=JsonFormat.Shape.STRING) Long manifestId,
    int revision,String manifestHash,String outcome,String reason) {
    public static final String EVENT_TYPE="COMPLETION_MANIFEST_CANCELLATION_RESULT";
    public static final String AGGREGATE_TYPE="COMPLETION_CANCELLATION_RESULT";
    public CompletionCancellationResult {
        if(!eventId(sourceEventId) || !eventId(cancellationEventId) || !eventId(originalEventId)
            || cancellationEventId.equals(originalEventId) || !hash(originalEventHash) || !hash(manifestHash)
            || manifestId==null || manifestId<=0 || revision<1 || outcome==null || !Set.of("CANCELLED","COMPENSATION_REQUIRED").contains(outcome)
            || reason==null || !reason.matches("[A-Z0-9_]{1,64}")
            || !(sourceEventId.equals(cancellationEventId) || "CANCELLED".equals(outcome) && sourceEventId.equals(originalEventId)))
            throw new IllegalArgumentException("取消结果缺少准确来源、清单身份或合法业务终态");
    }
    /** 仅取消成功可作为原 completion 的应用终态；补偿要求不能冒充原业务成功。 */
    public CompletionCancellationResult originalAcknowledgement(){
        if(!"CANCELLED".equals(outcome))throw new IllegalStateException("补偿要求不能生成原完工事件的应用回执");
        return new CompletionCancellationResult(originalEventId,cancellationEventId,originalEventId,originalEventHash,
            manifestId,revision,manifestHash,outcome,reason);
    }
    private static boolean eventId(String value){return value!=null && !value.isBlank() && value.length()<=64;}
    private static boolean hash(String value){return value!=null && value.matches("[0-9a-f]{64}");}
}
