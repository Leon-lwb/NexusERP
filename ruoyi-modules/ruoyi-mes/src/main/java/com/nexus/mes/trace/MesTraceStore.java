package com.nexus.mes.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.nexus.mes.trace.MesTraceContext.need;
import static com.nexus.mes.trace.MesTraceSql.*;

/** Local MES persistence only. The cross-datasource relay owns delivery and retry leases. */
@Service @DS("mes") @RequiredArgsConstructor
public class MesTraceStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final MesTraceContext context;
    private final MesTraceJournalService journal;

    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public MaterialTraceEvent append(String type,String aggregateType,Long aggregateId,long version,Object payload) {
        context.requireWrite(); String tenant=context.tenant(); String body=encode(payload);
        String eventId=TraceHashes.fingerprint(tenant,"MES",aggregateType,aggregateId,version);
        MaterialTraceEvent event=new MaterialTraceEvent(eventId,tenant,"MES",type,aggregateType,aggregateId,version,1,body,TraceHashes.sha256(body));
        journal.append(event);
        sameEvent(event,one(jdbc,"SELECT * FROM mes_trace_outbox WHERE tenant_id=? AND event_id=? FOR UPDATE",tenant,eventId));
        return event;
    }

    /** A command and its immutable request/result share the same transaction as the business write. */
    Map<String,Object> command(String commandId,String type,Object request) {
        context.requireWrite(); MesTraceSql.command(commandId); String tenant=context.tenant(),body=encode(request);
        String id=TraceHashes.fingerprint(tenant,type,commandId);
        var event=new MaterialTraceEvent(id,tenant,"MES",type,"COMMAND_"+type,
            Long.parseLong(TraceHashes.sha256(commandId).substring(0,15),16),1,1,body,TraceHashes.sha256(body));
        insert("mes_trace_inbox",event);
        var row=one(jdbc,"SELECT * FROM mes_trace_inbox WHERE tenant_id=? AND event_id=? FOR UPDATE",tenant,id);
        sameEvent(event,row); return row;
    }
    void finish(Map<String,Object> command,Object result) {
        need(jdbc.update("UPDATE mes_trace_inbox SET status='APPLIED',result_payload=?,applied_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND event_id=?",
            encode(result),context.tenant(),command.get("event_id"))==1,"命令结果未能持久化");
    }
    public String encode(Object object) {
        try{return json.writeValueAsString(object);}catch(Exception e){throw new IllegalStateException("追溯载荷编码失败");}
    }
    JsonNode payload(Map<String,Object> row) {
        need(row!=null,"追溯来源凭据不存在");
        try { return json.readTree(text(row.get("payload"))); }catch(Exception e){throw new IllegalStateException("追溯载荷无效");}
    }
    JsonNode result(Map<String,Object> row) {
        try { return json.readTree(text(row.get("result_payload"))); }catch(Exception e){throw new IllegalStateException("命令结果无效");}
    }
    Map<String,Object> source(String eventId) {
        var row=one(jdbc,"SELECT * FROM mes_trace_inbox WHERE tenant_id=? AND event_id=? FOR UPDATE",context.tenant(),eventId);
        need(row!=null && "ERP".equals(row.get("source_module")) && "APPLIED".equals(row.get("status")),"必须提供已确认的ERP来源凭据");
        need(row.get("result_payload")!=null,"MES来源缺少真实ERP事务凭据核验，不能人工塞入授权");
        JsonNode proof=result(row);
        need("ERP_OUTBOX".equals(proof.path("verifiedSource").asText()) && eventId.equals(proof.path("sourceEventId").asText())
            && Objects.equals(row.get("content_hash"),proof.path("sourceContentHash").asText()) && TraceHashes.sha256(text(row.get("payload"))).equals(row.get("content_hash")),"MES来源缺少真实ERP事务凭据核验，不能人工塞入授权");
        return row;
    }

    com.nexus.common.trace.TrackingPolicy sourcePolicy(Map<String,Object> source) {
        try {var policy=json.treeToValue(result(source).path("policy"),com.nexus.common.trace.TrackingPolicy.class);need(policy!=null,"ERP来源缺少冻结物料策略");return policy;}
        catch(java.io.IOException error){throw new IllegalStateException("ERP来源策略无效");}
    }
    long sourceVersion(Map<String,Object> source) {
        Long version=number(result(source),"sourceVersion");need(positive(version),"ERP来源缺少已核验的事实版本");return version;
    }

    /** Business effect only. JdbcTraceJournal owns the inbound delivery record and idempotence. */
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public boolean applyErpEvent(MaterialTraceEvent event) {
        context.requireWrite();context.requireTenant(event.tenantId());need("ERP".equals(event.sourceModule()),"只接受ERP来源的已核验事件");
        JsonNode p=read(event.payload());
        switch(event.eventType()) {
            case "ERP_MATERIAL_SOURCE_POSTED", "ERP_BACKFLUSH_ALLOCATION_AUTHORIZED", "ERP_PRODUCTION_LOT_AUTHORIZED" -> {
                need(MesMaterialTraceWriter.decimal(p,"quantity").signum()>0 && positive(number(p,"sourceId"))
                    && (positive(number(p,"erpBatchId")) || positive(number(p,"identityId")) && positive(number(p,"sourceStockId")) && java.math.BigDecimal.ONE.compareTo(MesMaterialTraceWriter.decimal(p,"quantity"))==0)
                    && positive(number(p,"materialId"))
                    && positive(number(p,"mesWoId")) && !p.path("sourceType").asText().isBlank(),"ERP分配来源缺少实际凭据");
                if("ERP_MATERIAL_SOURCE_POSTED".equals(event.eventType())) need(positive(number(p,"postingGroupId")) && p.path("backflushFlag").asInt(-1)==0,"预发料必须有真实过账组");
                if("ERP_BACKFLUSH_ALLOCATION_AUTHORIZED".equals(event.eventType())) need(p.path("backflushFlag").asInt(-1)==1,"倒冲授权标志不一致");
                if("ERP_PRODUCTION_LOT_AUTHORIZED".equals(event.eventType())) need("PRODUCTION_OUTPUT".equals(p.path("sourceType").asText()),"成品批次来源无效");
            }
            case "ERP_SUBCONTRACT_SOURCE_CONFIRMED" -> need(positive(number(p,"subcontractId")) && positive(number(p,"materialId"))
                && !p.path("subcontractNo").asText().isBlank() && MesMaterialTraceWriter.decimal(p,"quantity").signum()>0,"委外真实来源缺失");
            case "ERP_SERIAL_RESERVATION_CONFIRMED", "ERP_COMPLETION_RECEIPT_POSTED", "COMPLETION_MANIFEST_CANCELLATION_RESULT" -> projectResult(p.path("sourceEventId").asText(),p,null);
            case "ERP_SHIPMENT_POSTED" -> ship(p);
            case "ERP_INVENTORY_POSTED" -> { /* Immutable inventory fact; no MES stock mutation. */ }
            default -> throw new IllegalArgumentException("不支持的ERP追溯事件类型");
        }
        return true;
    }
    /** Optional common-journal ACK callback: applies business projection without rewriting its result. */
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public String applyAcknowledgement(MaterialTraceEvent acknowledgement) {
        context.requireWrite();context.requireTenant(acknowledgement.tenantId());
        need("ERP".equals(acknowledgement.sourceModule()) && "TRACE_APPLIED".equals(acknowledgement.eventType()),"MES应用回执来源无效");
        JsonNode ack=read(acknowledgement.payload());String eventId=ack.path("eventId").asText();
        JsonNode result=ack.path("resultPayload").isTextual()?read(ack.path("resultPayload").asText()):ack.path("resultPayload");
        projectResult(eventId,result,ack.path("contentHash").asText());return "{}";
    }
    private void projectResult(String sourceEventId,JsonNode result,String expectedHash) {
        String tenant=context.tenant();
        var origin=one(jdbc,"SELECT * FROM mes_trace_outbox WHERE tenant_id=? AND event_id=? FOR UPDATE",tenant,sourceEventId);
        need(origin!=null && (expectedHash==null || expectedHash.equals(origin.get("content_hash"))),"回执未关联MES原始事件或摘要不一致");
        String type=text(origin.get("event_type"));
        if("COMPLETION_MANIFEST_CANCEL_REQUESTED".equals(type)) {
            projectCancellation(origin,result);return;
        }
        if(!Set.of("SERIAL_RESERVATION_CONFIRM","COMPLETION_MANIFEST_FROZEN").contains(type))return;
        need(sourceEventId.equals(result.path("sourceEventId").asText()),"回执结果未引用原MES事件");
        JsonNode frozen=payload(origin);
        if("COMPLETION_MANIFEST_FROZEN".equals(type) && "CANCELLED".equals(result.path("outcome").asText())) {originalCancellation(origin,result);return;}
        if("SERIAL_RESERVATION_CONFIRM".equals(type)) {
            need(Objects.equals(number(frozen,"reservationId"),number(result,"reservationId")) && frozen.path("commandId").equals(result.path("commandId"))
                && frozen.path("fingerprint").equals(result.path("fingerprint")),"发号回执不匹配冻结命令");
            // The generic source outbox APPLIED + immutable inbox result is the durable confirmation state.
        } else {
            need(Objects.equals(number(frozen,"manifestId"),number(result,"manifestId")) && frozen.path("revision").asInt()==result.path("revision").asInt()
                && frozen.path("manifestHash").equals(result.path("manifestHash")),"完工回执不匹配冻结清单");
            var good=MesMaterialTraceWriter.decimal(frozen,"completedQty");need(good.signum()>=0,"冻结良品数量无效");
            JsonNode outcome=result.path("outcome"),receipt=result.path("receiptId"),group=result.path("postingGroupId");
            if(good.signum()==0) {
                need("NO_GOOD_OUTPUT".equals(outcome.asText()) && (receipt.isMissingNode() || receipt.isNull())
                    && (group.isMissingNode() || group.isNull()),"全报废回执必须明确无良品，禁止伪造收货单或过账组");
            } else {
                need((outcome.isMissingNode() || outcome.isNull() || "POSTED".equals(outcome.asText()))
                    && positive(number(result,"receiptId")) && positive(number(result,"postingGroupId")),"良品回执缺少真实收货或过账凭据");
                jdbc.update("UPDATE pro_sn SET handoff_status='RECEIVED' WHERE tenant_id=? AND handoff_status IN ('FROZEN','RECEIVED') AND sn_status='COMPLETED' AND sn_id IN (SELECT local_sn_id FROM pro_completion_manifest_line WHERE tenant_id=? AND manifest_id=? AND revision=? AND result='GOOD')",
                    tenant,tenant,number(result,"manifestId"),result.path("revision").asInt());
            }
            need(jdbc.update("UPDATE pro_completion_manifest SET status=CASE WHEN status='CANCEL_REQUESTED' THEN status ELSE 'APPLIED' END WHERE tenant_id=? AND manifest_id=? AND revision=? AND content_hash=? AND status IN ('FROZEN','APPLIED','RECEIVED','CANCEL_REQUESTED')",
                tenant,number(result,"manifestId"),result.path("revision").asInt(),result.path("manifestHash").asText())==1,"原清单与ERP已应用回执不一致");
        }
    }
    /** Generic journal ACK and this cancellation projection commit or roll back together. */
    private void projectCancellation(Map<String,Object> origin,JsonNode payload) {
        String tenant=context.tenant();CompletionCancellationResult result=cancellation(payload);
        if(origin.get("result_payload")!=null)need(result.equals(cancellation(read(text(origin.get("result_payload"))))),"Typed cancellation result differs from the immutable generic acknowledgement");
        CompletionCancellationRequest request;
        try{request=json.treeToValue(payload(origin),CompletionCancellationRequest.class);}catch(java.io.IOException error){throw new IllegalArgumentException("Invalid frozen cancellation request",error);}
        MaterialTraceEvent originalEvent=request.originalEvent();
        need(Objects.equals(origin.get("event_id"),result.sourceEventId()) && Objects.equals(origin.get("event_id"),result.cancellationEventId())
            && originalEvent.eventId().equals(result.originalEventId()) && originalEvent.contentHash().equals(result.originalEventHash())
            && request.manifestId().equals(result.manifestId()) && request.revision()==result.revision() && request.manifestHash().equals(result.manifestHash()),"Cancellation receipt does not match the immutable request");
        var original=one(jdbc,"SELECT * FROM mes_trace_outbox WHERE tenant_id=? AND event_id=? FOR UPDATE",tenant,originalEvent.eventId());sameEvent(originalEvent,original);
        JsonNode completion=payload(original);
        var wo=one(jdbc,"SELECT * FROM pro_work_order WHERE tenant_id=? AND mes_wo_id=? AND del_flag='0' FOR UPDATE",tenant,number(completion,"mesWoId"));need(wo!=null,"Cancellation requires the real MES parent work order");
        var manifest=one(jdbc,"SELECT * FROM pro_completion_manifest WHERE tenant_id=? AND manifest_id=? AND revision=? FOR UPDATE",tenant,request.manifestId(),request.revision());
        need(manifest!=null && Objects.equals(manifest.get("content_hash"),request.manifestHash()) && Objects.equals(manifest.get("material_hash"),completion.path("materialHash").asText()),"Cancellation changed the original immutable manifest");
        var latest=one(jdbc,"SELECT event_id FROM mes_trace_outbox WHERE tenant_id=? AND aggregate_type=? AND aggregate_id=? ORDER BY aggregate_version DESC LIMIT 1 FOR UPDATE",tenant,CompletionCancellationRequest.AGGREGATE_TYPE,request.manifestId());
        need(latest!=null,"Cancellation publication is missing");
        // A superseded request still owns its immutable ACK, but cannot project over the later request/revision.
        if(!Objects.equals(latest.get("event_id"),origin.get("event_id"))) {need("COMPENSATION_REQUIRED".equals(result.outcome()),"A stale cancellation cannot release a newer request");return;}
        if("CANCELLED".equals(result.outcome()))requireUnconsumedCompletion(original);
        if("CANCELLED".equals(manifest.get("status"))) {need("CANCELLED".equals(result.outcome()),"Cancellation outcomes conflict");return;}
        if("COMPENSATION_REQUIRED".equals(result.outcome())) {
            if(!"CANCEL_REQUESTED".equals(manifest.get("status"))) {need(Set.of("FROZEN","APPLIED","RECEIVED").contains(text(manifest.get("status"))),"Cancellation state conflicts");return;}
            String previous="APPLIED".equals(original.get("status"))?"APPLIED":"FROZEN";
            need(jdbc.update("UPDATE pro_completion_manifest SET status=? WHERE tenant_id=? AND manifest_id=? AND revision=? AND status='CANCEL_REQUESTED'",previous,tenant,request.manifestId(),request.revision())==1,"Compensation projection conflicted");return;
        }
        need("CANCEL_REQUESTED".equals(manifest.get("status")) && "CANCELLED".equals(result.outcome()),"Cancellation is not pending for this revision");
        var sns=jdbc.queryForList("SELECT sn_id,sn_status,handoff_status FROM pro_sn WHERE tenant_id=? AND mes_wo_id=? AND del_flag='0' ORDER BY sn_id FOR UPDATE",tenant,wo.get("mes_wo_id"));
        need(sns.stream().noneMatch(row->"SHIPPED".equals(row.get("sn_status")) || "SHIPPED".equals(row.get("handoff_status"))),"Shipped identities cannot be released by a completion cancellation");
        need(jdbc.update("UPDATE pro_completion_manifest SET status='CANCELLED' WHERE tenant_id=? AND manifest_id=? AND revision=? AND status='CANCEL_REQUESTED'",tenant,request.manifestId(),request.revision())==1,"Cancellation projection conflicted");
        jdbc.update("UPDATE mes_trace_outbox SET status=CASE WHEN status='APPLIED' THEN status ELSE 'CANCELLED' END,lease_owner=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND event_id=?",tenant,originalEvent.eventId());
        // Only the transfer hold is released. Production reports, quantities, SN states and DHR stay intact.
        jdbc.update("UPDATE pro_sn SET handoff_status='NONE' WHERE tenant_id=? AND mes_wo_id=? AND handoff_status IN ('FROZEN','RECEIVED') AND del_flag='0'",tenant,wo.get("mes_wo_id"));
    }
    /** NO_GOOD_OUTPUT consumes the completion too; absent stock IDs do not authorize cancellation. */
    private void requireUnconsumedCompletion(Map<String,Object> original) {
        JsonNode applied=original.get("result_payload")==null?null:read(text(original.get("result_payload")));
        if(applied==null) {
            need(!"APPLIED".equals(original.get("status")),"Applied completion lacks its durable result and cannot be cancelled");return;
        }
        need("CANCELLED".equals(applied.path("outcome").asText()) && !positive(number(applied,"receiptId")) && !positive(number(applied,"postingGroupId")),
            "Applied completion, including NO_GOOD_OUTPUT, requires the ERP compensation workflow");
        // An original-event cancellation ACK may arrive before this request's ACK or typed result.
        originalCancellation(original,applied);
    }
    private CompletionCancellationResult cancellation(JsonNode payload) {
        try{return json.treeToValue(payload,CompletionCancellationResult.class);}catch(java.io.IOException error){throw new IllegalArgumentException("Invalid ERP cancellation receipt",error);}
    }
    /** A late original-event cancellation ACK records history and never projects a receipt/SN transfer. */
    private void originalCancellation(Map<String,Object> origin,JsonNode payload) {
        var result=cancellation(payload);need("CANCELLED".equals(result.outcome()) && Objects.equals(origin.get("event_id"),result.sourceEventId()) && Objects.equals(origin.get("event_id"),result.originalEventId()) && Objects.equals(origin.get("content_hash"),result.originalEventHash()),"Original cancellation acknowledgement does not match the completion event");
        var request=one(jdbc,"SELECT * FROM mes_trace_outbox WHERE tenant_id=? AND event_id=? AND event_type=? AND aggregate_type=? FOR UPDATE",context.tenant(),result.cancellationEventId(),CompletionCancellationRequest.EVENT_TYPE,CompletionCancellationRequest.AGGREGATE_TYPE);
        need(request!=null,"Original cancellation ACK lacks its actual MES cancellation request");
        JsonNode frozen=payload(origin),cancel=payload(request);
        need(Objects.equals(number(frozen,"manifestId"),result.manifestId()) && frozen.path("revision").asInt()==result.revision() && frozen.path("manifestHash").asText().equals(result.manifestHash())
            && cancel.path("originalEvent").path("eventId").asText().equals(result.originalEventId()) && cancel.path("originalEvent").path("contentHash").asText().equals(result.originalEventHash()),"Original cancellation ACK changed its immutable source");
        var header=one(jdbc,"SELECT status FROM pro_completion_manifest WHERE tenant_id=? AND manifest_id=? AND revision=? FOR UPDATE",context.tenant(),result.manifestId(),result.revision());need(header!=null && Set.of("CANCEL_REQUESTED","CANCELLED").contains(text(header.get("status"))),"Original cancellation ACK does not belong to a pending or cancelled revision");
    }
    JsonNode read(String body) {try{return json.readTree(body);}catch(java.io.IOException e){throw new IllegalArgumentException("追溯载荷无效");}}
    /** Read-only ERP snapshot supplied by the preflight reader; this is a local source credential, not a transport delivery. */
    void subcontractProof(MaterialTraceEvent proof) {
        context.requireWrite();context.requireTenant(proof.tenantId());
        need("ERP_SUBCONTRACT_SOURCE_CONFIRMED".equals(proof.eventType()) && "ERP".equals(proof.sourceModule()),"真实委外来源快照缺失");
        applyErpEvent(proof);var payload=read(proof.payload());
        var command=command("SUBCONTRACT:"+number(payload,"subcontractId"),"SUBCONTRACT_SOURCE",payload);
        finish(command,Map.of("verified",true));
    }
    Map<String,Object> subcontractSource(Long id) {
        return one(jdbc,"SELECT * FROM mes_trace_inbox WHERE tenant_id=? AND event_id=? AND status='APPLIED' FOR UPDATE",context.tenant(),
            TraceHashes.fingerprint(context.tenant(),"SUBCONTRACT_SOURCE","SUBCONTRACT:"+id));
    }
    private void ship(JsonNode p) {
        need(positive(number(p,"postingGroupId")) && positive(number(p,"sourceDocId")) && !p.path("sourceDocType").asText().isBlank()
            && p.path("identityIds").isArray(),"发货必须来自ERP已过账单据");
        var ids=new TreeSet<Long>(); for(JsonNode id:p.path("identityIds")) need(positive(numberValue(id)) && ids.add(numberValue(id)),"发货身份无效或重复");
        for(Long identity:ids) {
            var sn=one(jdbc,"SELECT * FROM pro_sn WHERE tenant_id=? AND identity_id=? AND del_flag='0' FOR UPDATE",context.tenant(),identity);
            need(sn!=null && Set.of("COMPLETED","SHIPPED").contains(text(sn.get("sn_status"))),"ERP发货身份尚未在MES完工");
            jdbc.update("UPDATE pro_sn SET sn_status='SHIPPED',handoff_status='SHIPPED' WHERE tenant_id=? AND identity_id=? AND sn_status='COMPLETED' AND del_flag='0'",context.tenant(),identity);
        }
    }
    private void insert(String table,MaterialTraceEvent e) {
        jdbc.update("INSERT INTO "+table+"(event_id,tenant_id,source_module,aggregate_type,aggregate_id,aggregate_version,event_type,schema_version,payload,content_hash,status) VALUES(?,?,?,?,?,?,?,?,?,?,'PENDING') ON DUPLICATE KEY UPDATE event_id=event_id",
            e.eventId(),e.tenantId(),e.sourceModule(),e.aggregateType(),e.aggregateId(),e.aggregateVersion(),e.eventType(),e.schemaVersion(),e.payload(),e.contentHash());
    }
    private void sameEvent(MaterialTraceEvent event,Map<String,Object> row) {
        need(row!=null && event.tenantId().equals(row.get("tenant_id")) && event.sourceModule().equals(row.get("source_module"))
            && event.eventType().equals(row.get("event_type")) && event.aggregateType().equals(row.get("aggregate_type"))
            && Objects.equals(event.aggregateId(),id(row.get("aggregate_id"))) && event.aggregateVersion()==id(row.get("aggregate_version"))
            && event.contentHash().equals(row.get("content_hash")) && event.payload().equals(text(row.get("payload"))),"同一事件或命令不能更换内容");
    }
    static Long numberValue(JsonNode n) { try{return new java.math.BigDecimal(n.asText()).longValueExact();}catch(Exception e){return null;} }
    static Long number(JsonNode p,String name) { JsonNode n=p.path(name);if(n.isMissingNode() || n.isNull())return null;try{return new java.math.BigDecimal(n.asText()).longValueExact();}catch(Exception e){return null;} }
}
