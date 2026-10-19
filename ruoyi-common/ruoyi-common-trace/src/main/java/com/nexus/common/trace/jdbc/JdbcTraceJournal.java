package com.nexus.common.trace.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.sql.Clob;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.Function;

/** 两个业务库复用的事件日志SQL内核；表名固定，事务与数据源由各模块真实代理负责。 */
public final class JdbcTraceJournal {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final String module,outbox,inbox;
    public JdbcTraceJournal(JdbcTemplate jdbc,ObjectMapper json,String module) {
        if(!Set.of("ERP","MES").contains(module)) throw new IllegalArgumentException("事件所属模块无效");
        this.jdbc=jdbc;this.json=json;this.module=module;
        this.outbox=module.equals("ERP")?"wm_trace_outbox":"mes_trace_outbox";
        this.inbox=module.equals("ERP")?"wm_trace_inbox":"mes_trace_inbox";
    }
    public void append(MaterialTraceEvent event) {
        writeTransaction();
        if(!module.equals(event.sourceModule())) throw new IllegalArgumentException("事件必须在来源模块业务事务中写入");
        jdbc.update("INSERT INTO "+outbox+"(event_id,tenant_id,source_module,event_type,aggregate_type,aggregate_id,aggregate_version,schema_version,payload,content_hash,status,attempts,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,'NEW',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE event_id=event_id",
            event.eventId(),event.tenantId(),event.sourceModule(),event.eventType(),event.aggregateType(),event.aggregateId(),event.aggregateVersion(),event.schemaVersion(),event.payload(),event.contentHash());
        var row=one("SELECT * FROM "+outbox+" WHERE event_id=? AND tenant_id=? FOR UPDATE",event.eventId(),event.tenantId());
        if(!sameEvent(row,event)) throw new IllegalStateException("同一事件号或聚合版本不能修改内容");
    }
    public List<MaterialTraceEvent> claim(String owner,int limit,int leaseSeconds) {
        writeTransaction();
        if(owner==null || owner.isBlank() || owner.length()>64 || limit<1 || limit>500 || leaseSeconds<1 || leaseSeconds>300)
            throw new IllegalArgumentException("事件领取参数无效");
        Instant now=dbNow();Timestamp until=Timestamp.from(now.plusSeconds(leaseSeconds));
        var rows=jdbc.queryForList("SELECT * FROM "+outbox+" WHERE status IN ('NEW','BROKER_SENT') AND (next_attempt_at IS NULL OR next_attempt_at<=?) AND (lease_until IS NULL OR lease_until<?) ORDER BY created_at,event_id LIMIT ? FOR UPDATE",Timestamp.from(now),Timestamp.from(now),limit);
        List<MaterialTraceEvent> events=new ArrayList<>();
        for(var row:rows) {
            jdbc.update("UPDATE "+outbox+" SET lease_owner=?,lease_until=?,attempts=attempts+1,updated_at=CURRENT_TIMESTAMP WHERE event_id=? AND status IN ('NEW','BROKER_SENT')",owner,until,row.get("event_id"));
            events.add(event(row));
        }
        return List.copyOf(events);
    }
    public void brokerAccepted(String eventId,String owner) {
        writeTransaction();
        var row=one("SELECT * FROM "+outbox+" WHERE event_id=? AND lease_owner=? FOR UPDATE",eventId,owner);
        if(row==null || "APPLIED".equals(text(row,"status"))) return;
        int attempts=(int)number(row,"attempts");
        String status="TRACE_APPLIED".equals(text(row,"event_type"))?"APPLIED":"BROKER_SENT";
        jdbc.update("UPDATE "+outbox+" SET status=?,lease_owner=NULL,lease_until=NULL,next_attempt_at=?,updated_at=CURRENT_TIMESTAMP WHERE event_id=? AND lease_owner=?",status,Timestamp.from(dbNow().plusSeconds(backoff(attempts))),eventId,owner);
    }
    public void retry(String eventId,String owner,String errorCode) {
        writeTransaction();
        var row=one("SELECT * FROM "+outbox+" WHERE event_id=? AND lease_owner=? FOR UPDATE",eventId,owner);
        if(row==null || "APPLIED".equals(text(row,"status"))) return;
        int attempts=(int)number(row,"attempts");String safe=errorCode==null?"TRANSPORT_FAILURE":errorCode.substring(0,Math.min(128,errorCode.length()));
        jdbc.update("UPDATE "+outbox+" SET status=?,last_error=?,lease_owner=NULL,lease_until=NULL,next_attempt_at=?,updated_at=CURRENT_TIMESTAMP WHERE event_id=? AND lease_owner=?",attempts>=10?"CONFLICT":"NEW",safe,Timestamp.from(dbNow().plusSeconds(backoff(attempts))),eventId,owner);
    }
    public void acknowledge(String tenant,String eventId,String hash,String result) {
        writeTransaction();
        var row=one("SELECT * FROM "+outbox+" WHERE tenant_id=? AND event_id=? FOR UPDATE",tenant,eventId);
        String payload=row==null?null:text(row,"payload");
        if(row==null || payload==null || !Objects.equals(hash,text(row,"content_hash")) || !Objects.equals(hash,TraceHashes.sha256(payload)))
            throw new IllegalStateException("应用回执不属于完整的原事件");
        if("APPLIED".equals(text(row,"status")) && text(row,"result_payload")!=null && !Objects.equals(result,text(row,"result_payload")))
            throw new IllegalStateException("重复回执不能改变业务结果");
        jdbc.update("UPDATE "+outbox+" SET status='APPLIED',result_payload=?,applied_at=CURRENT_TIMESTAMP,lease_owner=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND event_id=?",result,tenant,eventId);
    }
    public TraceDeliveryResult accept(MaterialTraceEvent event,Supplier<String> effect) {
        return accept(event,effect,()->{});
    }
    /** The receipt projection shares the source transaction; a failed projection cannot acknowledge delivery. */
    public TraceDeliveryResult accept(MaterialTraceEvent event,Supplier<String> effect,Runnable acknowledgementEffect) {
        writeTransaction();
        var row=lockIncoming(event);
        if("APPLIED".equals(text(row,"status"))) {
            if(!"TRACE_APPLIED".equals(event.eventType())) receipt(event,text(row,"result_payload"));
            return new TraceDeliveryResult("APPLIED",text(row,"result_payload"));
        }
        if(!"TRACE_APPLIED".equals(event.eventType())) {
            Long previous=jdbc.queryForObject("SELECT COALESCE(MAX(aggregate_version),0) FROM "+inbox+" WHERE tenant_id=? AND source_module=? AND aggregate_type=? AND aggregate_id=? AND status='APPLIED'",Long.class,event.tenantId(),event.sourceModule(),event.aggregateType(),event.aggregateId());
            if(previous==null || event.aggregateVersion()!=previous+1) return new TraceDeliveryResult("WAITING",null);
        }
        String result;
        if("TRACE_APPLIED".equals(event.eventType())) {
            try {
                var ack=json.readTree(event.payload());
                acknowledge(event.tenantId(),ack.path("eventId").asText(),ack.path("contentHash").asText(),ack.path("resultPayload").isNull()?null:ack.path("resultPayload").asText());
                acknowledgementEffect.run();
                result="{}";
            } catch(java.io.IOException e) {throw new IllegalArgumentException("回执载荷无效",e);}
        } else result=effect.get();
        jdbc.update("UPDATE "+inbox+" SET status='APPLIED',result_payload=?,applied_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND event_id=?",result,event.tenantId(),event.eventId());
        if(!"TRACE_APPLIED".equals(event.eventType())) receipt(event,result);
        return new TraceDeliveryResult("APPLIED",result);
    }
    /** 只读重放探测；即使尚未应用，也必须拒绝同事件号不同内容。 */
    public TraceDeliveryResult readApplied(MaterialTraceEvent event) {
        incoming(event);
        var row=one("SELECT * FROM "+inbox+" WHERE event_id=?",event.eventId());
        if(row==null)return null;
        if(!sameEvent(row,event))throw new IllegalStateException("事件号、版本或内容冲突");
        return "APPLIED".equals(text(row,"status"))?new TraceDeliveryResult("APPLIED",text(row,"result_payload")):null;
    }
    /** 只有两条 MES owner outbox 均已实证的取消命令，才可竞争原 completion 的应用终态。 */
    public CompletionCancellationResult preemptCompletion(CompletionCancellationProof proof,
            Function<TraceDeliveryResult,CompletionCancellationResult> decision) {
        writeTransaction();
        if(!"ERP".equals(module) || proof==null || decision==null)
            throw new IllegalArgumentException("取消预占需要 ERP 目标及可信来源凭据");
        MaterialTraceEvent original=proof.originalEvent();
        // 与正常 completion 一致：先锁原 inbox，再由业务决策锁同一工单。
        var row=lockIncoming(original);
        String status=text(row,"status"),stored=text(row,"result_payload");
        if(!Set.of("WAITING","APPLIED").contains(status))throw new IllegalStateException("原完工事件状态不可取消");
        CompletionCancellationResult result=decision.apply(new TraceDeliveryResult(status,stored));
        if(!matchesCancellation(result,proof,true))throw new IllegalArgumentException("取消结果不属于已验证的来源和清单");
        if("APPLIED".equals(status)) {
            if("CANCELLED".equals(result.outcome())) {
                CompletionCancellationResult previous;
                try{previous=json.readValue(stored,CompletionCancellationResult.class);}
                catch(java.io.IOException|IllegalArgumentException e){throw new IllegalStateException("已应用完工不能被取消终态覆盖",e);}
                if(!matchesCancellation(previous,proof,false) || !"CANCELLED".equals(previous.outcome()))
                    throw new IllegalStateException("已应用完工不能被取消终态覆盖");
            }
            receipt(original,stored);
        } else if("CANCELLED".equals(result.outcome())) {
            String tombstone;
            try{tombstone=json.writeValueAsString(result.originalAcknowledgement());}
            catch(java.io.IOException e){throw new IllegalStateException("取消终态无法冻结",e);}
            if(!accept(original,()->tombstone).applied())throw new IllegalStateException("原完工事件版本缺口阻止取消终态");
        }
        // WAITING + COMPENSATION_REQUIRED 不代表原完工已应用，不能生成其 ACK。
        return result;
    }
    private boolean matchesCancellation(CompletionCancellationResult result,CompletionCancellationProof proof,boolean current) {
        if(result==null)return false;
        var command=proof.request();var original=proof.originalEvent();
        return Objects.equals(result.sourceEventId(),current?proof.cancellationEvent().eventId():original.eventId())
            && (!current || Objects.equals(result.cancellationEventId(),proof.cancellationEvent().eventId()))
            && Objects.equals(result.originalEventId(),original.eventId()) && Objects.equals(result.originalEventHash(),original.contentHash())
            && Objects.equals(result.manifestId(),command.manifestId()) && result.revision()==command.revision()
            && Objects.equals(result.manifestHash(),command.manifestHash());
    }
    private void incoming(MaterialTraceEvent event) {
        if(event==null || module.equals(event.sourceModule()) || !Set.of("ERP","MES").contains(event.sourceModule()))
            throw new IllegalArgumentException("事件目标模块错误");
    }
    private Map<String,Object> lockIncoming(MaterialTraceEvent event) {
        incoming(event);
        jdbc.update("INSERT INTO "+inbox+"(event_id,tenant_id,source_module,event_type,aggregate_type,aggregate_id,aggregate_version,schema_version,payload,content_hash,status,attempts,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,'WAITING',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE event_id=event_id",
            event.eventId(),event.tenantId(),event.sourceModule(),event.eventType(),event.aggregateType(),event.aggregateId(),event.aggregateVersion(),event.schemaVersion(),event.payload(),event.contentHash());
        var row=one("SELECT * FROM "+inbox+" WHERE tenant_id=? AND event_id=? FOR UPDATE",event.tenantId(),event.eventId());
        if(!sameEvent(row,event))throw new IllegalStateException("事件号、版本或内容冲突");
        return row;
    }
    private void receipt(MaterialTraceEvent source,String result) {
        try {
            Map<String,Object> ack=new LinkedHashMap<>();ack.put("eventId",source.eventId());ack.put("contentHash",source.contentHash());ack.put("resultPayload",result);
            String payload=json.writeValueAsString(ack), id="ACK_"+TraceHashes.sha256(source.eventId()).substring(0,48);
            append(new MaterialTraceEvent(id,source.tenantId(),module,"TRACE_APPLIED","ACK_"+TraceHashes.sha256(source.sourceModule()+":"+source.aggregateType()).substring(0,32),source.aggregateId(),source.aggregateVersion(),1,payload,TraceHashes.sha256(payload)));
            jdbc.update("UPDATE "+outbox+" SET status='NEW',next_attempt_at=NULL,lease_owner=NULL,lease_until=NULL,updated_at=CURRENT_TIMESTAMP WHERE event_id=? AND tenant_id=? AND status='APPLIED'",id,source.tenantId());
        } catch(java.io.IOException e) {throw new IllegalStateException("应用回执无法冻结",e);}
    }
    private MaterialTraceEvent event(Map<String,Object> row) {
        return new MaterialTraceEvent(text(row,"event_id"),text(row,"tenant_id"),text(row,"source_module"),text(row,"event_type"),text(row,"aggregate_type"),number(row,"aggregate_id"),number(row,"aggregate_version"),(int)number(row,"schema_version"),text(row,"payload"),text(row,"content_hash"));
    }
    private static boolean sameEvent(Map<String,Object> row,MaterialTraceEvent event) {
        return row!=null && event.tenantId().equals(text(row,"tenant_id")) && event.sourceModule().equals(text(row,"source_module"))
            && event.eventType().equals(text(row,"event_type")) && event.aggregateType().equals(text(row,"aggregate_type"))
            && event.aggregateId()==number(row,"aggregate_id") && event.aggregateVersion()==number(row,"aggregate_version")
            && event.schemaVersion()==number(row,"schema_version") && event.contentHash().equals(text(row,"content_hash"))
            && event.payload().equals(text(row,"payload"));
    }
    private Map<String,Object> one(String sql,Object... args) {var rows=jdbc.queryForList(sql,args);if(rows.size()>1) throw new IllegalStateException("事件日志不唯一");return rows.isEmpty()?null:rows.get(0);}
    private Instant dbNow() {return Objects.requireNonNull(jdbc.queryForObject("SELECT CURRENT_TIMESTAMP",Timestamp.class)).toInstant();}
    private static int backoff(int attempts) {return Math.min(600,5*(1<<Math.min(attempts,7)));}
    private static void writeTransaction() {if(!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw new IllegalStateException("事件日志需要实际业务库写事务");}
    private static long number(Map<String,Object> row,String key) {return ((Number)row.get(key)).longValue();}
    private static String text(Map<String,Object> row,String key) {
        Object value=row.get(key);if(value==null) return null;
        if(value instanceof Clob clob) try {return clob.getSubString(1,Math.toIntExact(clob.length()));} catch(java.sql.SQLException e) {throw new IllegalStateException("日志内容不可读",e);}
        return value.toString();
    }
}
