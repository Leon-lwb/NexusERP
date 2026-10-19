package com.nexus.common.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.port.TraceSourceVerificationPort;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Objects;

/** 仅在目标事务外，核验两条真实 MES owner outbox 后签发的进程内准备凭据；不作传输 DTO。 */
public final class CompletionCancellationProof {
    private final MaterialTraceEvent cancellationEvent;
    private final CompletionCancellationRequest request;
    private final Long erpWoId,mesWoId,materialId;
    private CompletionCancellationProof(MaterialTraceEvent event,CompletionCancellationRequest request,Long erpWoId,Long mesWoId,Long materialId){
        this.cancellationEvent=event;this.request=request;this.erpWoId=erpWoId;this.mesWoId=mesWoId;this.materialId=materialId;
    }
    /** verifier 必须来自受信来源注册表，遵循原有 source owner outbox 实证契约。 */
    public static CompletionCancellationProof verify(MaterialTraceEvent event,ObjectMapper json,TraceSourceVerificationPort verifier){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("取消来源实证必须在目标事务外准备");
        if(event==null || verifier==null || !"MES".equals(verifier.sourceModule()) || !"MES".equals(event.sourceModule())
            || !CompletionCancellationRequest.EVENT_TYPE.equals(event.eventType()) || !CompletionCancellationRequest.AGGREGATE_TYPE.equals(event.aggregateType()))
            throw new IllegalArgumentException("取消必须来自独立的 MES 取消事件流");
        verifier.verify(event);
        CompletionCancellationRequest command;
        JsonNode original;
        try{command=json.readValue(event.payload(),CompletionCancellationRequest.class);original=json.readTree(command.originalEvent().payload());}
        catch(java.io.IOException e){throw new IllegalArgumentException("取消来源载荷无效",e);}
        MaterialTraceEvent source=command.originalEvent();
        if(!event.tenantId().equals(source.tenantId()) || !Objects.equals(event.aggregateId(),command.manifestId()) || event.eventId().equals(source.eventId()))
            throw new IllegalArgumentException("取消事件与原完工事件的租户或清单不一致");
        verifier.verify(source);
        if(original==null || !original.isObject() || !Objects.equals(command.manifestId(),positive(original,"manifestId"))
            || command.revision()!=positive(original,"revision") || !command.manifestHash().equals(original.path("manifestHash").asText()))
            throw new IllegalArgumentException("取消修订或摘要不匹配原始完工事件");
        return new CompletionCancellationProof(event,command,positive(original,"erpWoId"),positive(original,"mesWoId"),positive(original,"materialId"));
    }
    private static Long positive(JsonNode node,String key){
        try{long value=new java.math.BigDecimal(node.path(key).asText("")).longValueExact();if(value>0)return value;}
        catch(NumberFormatException|ArithmeticException e){throw new IllegalArgumentException("取消来源整数无效："+key,e);}
        throw new IllegalArgumentException("取消来源身份无效："+key);
    }
    public MaterialTraceEvent cancellationEvent(){return cancellationEvent;}
    public CompletionCancellationRequest request(){return request;}
    public MaterialTraceEvent originalEvent(){return request.originalEvent();}
    public Long erpWoId(){return erpWoId;}
    public Long mesWoId(){return mesWoId;}
    public Long materialId(){return materialId;}
}
