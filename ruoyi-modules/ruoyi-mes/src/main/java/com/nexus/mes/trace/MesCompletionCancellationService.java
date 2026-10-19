package com.nexus.mes.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import com.nexus.common.trace.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.nexus.mes.trace.MesTraceContext.need;
import static com.nexus.mes.trace.MesTraceSql.*;

/** Requests an ERP cancellation barrier; only a verified ERP ACK can release a frozen revision. */
@Service @DS("mes") @RequiredArgsConstructor
public class MesCompletionCancellationService {
    private final JdbcTemplate jdbc;
    private final MesTraceContext context;
    private final MesTraceStore events;

    @Transactional(rollbackFor=Exception.class)
    public String request(String commandId,Long manifestId,int revision,String reason,Long actorId) {
        context.requireWrite();need(positive(manifestId) && revision>0 && positive(actorId) && reason!=null && !reason.isBlank() && reason.length()<=500,"Cancellation requires a stable command, manifest, reason and authenticated operator");
        var command=events.command(commandId,"COMPLETION_MANIFEST_CANCEL",Arrays.asList(manifestId,revision,reason,actorId));
        if("APPLIED".equals(command.get("status")))return events.result(command).path("requestEventId").asText();
        String tenant=context.tenant();
        var hint=one(jdbc,"SELECT mes_wo_id FROM pro_completion_manifest WHERE tenant_id=? AND manifest_id=? AND revision=?",tenant,manifestId,revision);need(hint!=null,"Frozen manifest does not exist");
        var wo=one(jdbc,"SELECT * FROM pro_work_order WHERE tenant_id=? AND mes_wo_id=? AND del_flag='0' FOR UPDATE",tenant,hint.get("mes_wo_id"));need(wo!=null && "COMPLETED".equals(wo.get("wo_status")),"Cancellation requires the real completed parent work order");
        var latest=one(jdbc,"SELECT * FROM pro_completion_manifest WHERE tenant_id=? AND mes_wo_id=? ORDER BY revision DESC LIMIT 1 FOR UPDATE",tenant,wo.get("mes_wo_id"));
        need(latest!=null && Objects.equals(id(latest.get("manifest_id")),manifestId) && id(latest.get("revision"))==revision && Set.of("FROZEN","APPLIED","RECEIVED").contains(text(latest.get("status"))),"Only the current frozen/applied revision may request cancellation");
        var sns=jdbc.queryForList("SELECT sn_status,handoff_status FROM pro_sn WHERE tenant_id=? AND mes_wo_id=? AND del_flag='0' ORDER BY sn_id FOR UPDATE",tenant,wo.get("mes_wo_id"));
        need(sns.stream().noneMatch(s->"SHIPPED".equals(s.get("sn_status")) || "SHIPPED".equals(s.get("handoff_status"))),"Shipped identities require the actual ERP return/reversal workflow");
        var original=one(jdbc,"SELECT * FROM mes_trace_outbox WHERE tenant_id=? AND event_type='COMPLETION_MANIFEST_FROZEN' AND aggregate_type='COMPLETION_MANIFEST' AND aggregate_id=? FOR UPDATE",tenant,manifestId);
        need(original!=null,"Cancellation requires the original durable completion publication");var frozen=events.payload(original);
        need(frozen.path("revision").asInt()==revision && Objects.equals(latest.get("content_hash"),frozen.path("manifestHash").asText()) && Objects.equals(latest.get("material_hash"),frozen.path("materialHash").asText()),"Original publication differs from frozen manifest");
        var previous=jdbc.queryForList("SELECT aggregate_version FROM mes_trace_outbox WHERE tenant_id=? AND aggregate_type='COMPLETION_MANIFEST_CANCELLATION' AND aggregate_id=? ORDER BY aggregate_version DESC LIMIT 1 FOR UPDATE",tenant,manifestId);
        long version=previous.isEmpty()?1:id(previous.get(0).get("aggregate_version"))+1;
        var originalEvent=new MaterialTraceEvent(text(original.get("event_id")),text(original.get("tenant_id")),text(original.get("source_module")),text(original.get("event_type")),text(original.get("aggregate_type")),id(original.get("aggregate_id")),id(original.get("aggregate_version")),Math.toIntExact(id(original.get("schema_version"))),text(original.get("payload")),text(original.get("content_hash")));
        // Keep the common immutable request and owner event intact; source audit belongs in this same envelope.
        var request=new CompletionCancellationRequest(manifestId,revision,text(latest.get("content_hash")),originalEvent);
        var payload=(com.fasterxml.jackson.databind.node.ObjectNode)events.read(events.encode(request));
        payload.put("commandId",commandId);payload.put("reason",reason);payload.put("actorId",actorId.toString());
        need(jdbc.update("UPDATE pro_completion_manifest SET status='CANCEL_REQUESTED' WHERE tenant_id=? AND manifest_id=? AND revision=? AND status=?",tenant,manifestId,revision,latest.get("status"))==1,"Cancellation state changed concurrently");
        var event=events.append(CompletionCancellationRequest.EVENT_TYPE,CompletionCancellationRequest.AGGREGATE_TYPE,manifestId,version,payload);
        events.finish(command,Map.of("requestEventId",event.eventId(),"previousStatus",latest.get("status")));return event.eventId();
    }
}
