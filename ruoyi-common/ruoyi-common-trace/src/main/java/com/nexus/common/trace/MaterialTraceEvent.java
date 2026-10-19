package com.nexus.common.trace;
import com.fasterxml.jackson.annotation.JsonFormat;
/** 同源业务事务内冻结的事件；版本在来源模块和业务聚合范围内递增。 */
public record MaterialTraceEvent(String eventId,String tenantId,String sourceModule,String eventType,
    String aggregateType,@JsonFormat(shape=JsonFormat.Shape.STRING) Long aggregateId,@JsonFormat(shape=JsonFormat.Shape.STRING) long aggregateVersion,int schemaVersion,String payload,String contentHash) {
    public MaterialTraceEvent {
        if(eventId==null || eventId.isBlank() || eventId.length()>64 || tenantId==null || tenantId.isBlank() || tenantId.length()>20
            || !java.util.Set.of("ERP","MES").contains(sourceModule) || eventType==null || eventType.isBlank() || eventType.length()>64
            || aggregateType==null || aggregateType.isBlank() || aggregateType.length()>64 || aggregateId==null || aggregateId<=0
            || aggregateVersion<1 || schemaVersion!=1 || payload==null || !TraceHashes.sha256(payload).equals(contentHash))
            throw new IllegalArgumentException("追溯事件身份、版本或内容摘要无效");
    }
}
