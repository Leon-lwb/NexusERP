package com.nexus.mes.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.databind.*;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.*;
import com.nexus.mes.integration.trace.MesWorkOrderTrackingPolicyReader;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import static com.nexus.mes.trace.MesTraceContext.need;
import static com.nexus.mes.trace.MesTraceSql.*;
import static com.nexus.mes.trace.MesTraceStore.number;

/** Read facts for the existing PC detail endpoints; no source allocation, receipt or state is created here. */
@Service @DS("mes") @RequiredArgsConstructor
public class MesProductionDetailFactsService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final MesTraceContext context;
    private final MesWorkOrderTrackingPolicyReader frozenPolicies;
    @Value("${nexus.trace.relay.enabled:false}") private boolean traceRelayEnabled;

    @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW,isolation=Isolation.REPEATABLE_READ)
    public Map<Long,Map<String,Object>> productOutputs(Long cardId,Long woId,Long materialId) {
        String tenant=context.tenant();if(!traceRelayEnabled)return Map.of();
        try {
            var wo=workOrder(tenant,woId,materialId);var card=one(jdbc,"SELECT * FROM pro_flow_card WHERE tenant_id=? AND card_id=? AND del_flag='0'",tenant,cardId);
            need(card!=null && Objects.equals(woId,id(card.get("mes_wo_id"))) && Objects.equals(materialId,id(card.get("material_id"))),"产品产出上下文缺少实际同租户流转卡");
            // The main-owned NOT_SUPPORTED proxy suspends this read transaction before its ERP bank read.
            var frozen=frozenPolicies.find(woId,materialId);
            need(frozen!=null && frozen.known() && tenant.equals(frozen.tenantId()) && Objects.equals(id(wo.get("erp_wo_id")),frozen.erpWoId()) && Objects.equals(materialId,frozen.materialId()),"工单原冻结策略未知或与当前详情来源不一致");
            TrackingPolicy policy=frozen.policy();
            var steps=jdbc.queryForList("SELECT * FROM pro_flow_card_step WHERE tenant_id=? AND card_id=? AND del_flag='0' ORDER BY sequence_num,step_record_id",tenant,cardId);
            Long last=null;for(var step:steps){need(Objects.equals(woId,id(step.get("mes_wo_id"))),"产出工序归属冲突");if(!"SKIPPED".equals(step.get("step_status")))last=id(step.get("step_record_id"));}
            var outputs=jdbc.queryForList("SELECT * FROM pro_product_lot_output WHERE tenant_id=? AND card_id=? ORDER BY output_id",tenant,cardId);
            var sources=new ArrayList<Map<String,Object>>();
            boolean batchOnly=policy.batchManaged() && !policy.serialManaged();
            boolean writable=Set.of("RELEASED","IN_PROGRESS","PARTIAL_COMPLETED").contains(text(wo.get("wo_status")))
                && Set.of("IN_PROGRESS","COMPLETED").contains(text(card.get("card_status")))
                && one(jdbc,"SELECT manifest_id FROM pro_completion_manifest WHERE tenant_id=? AND mes_wo_id=? LIMIT 1",tenant,woId)==null;
            if(batchOnly && writable)for(var allocation:jdbc.queryForList("SELECT * FROM lt_lot_allocation WHERE tenant_id=? AND material_id=? AND source_type='PRODUCTION_OUTPUT' AND source_id=? ORDER BY allocation_id",tenant,materialId,wo.get("erp_wo_id"))) {
                var source=productionSource(tenant,wo,allocation);
                if(source==null)continue;
                if(one(jdbc,"SELECT output_id FROM pro_product_lot_output WHERE tenant_id=? AND lot_id=? LIMIT 1",tenant,allocation.get("lot_id"))==null)sources.add(source);
            }
            var result=new LinkedHashMap<Long,Map<String,Object>>();
            for(var step:steps) {
                if(!Set.of("IN_PROGRESS","COMPLETED").contains(text(step.get("step_status"))))continue;
                Long stepId=id(step.get("step_record_id"));boolean finalStep=Objects.equals(last,stepId);
                var recorded=new ArrayList<Map<String,Object>>();
                for(var output:outputs)if(Objects.equals(stepId,id(output.get("step_record_id")))) {
                    need(Objects.equals(woId,id(output.get("mes_wo_id"))) && Objects.equals(materialId,id(output.get("material_id"))) && qty(output.get("quantity")).signum()>0
                        && Set.of("GOOD","SCRAP").contains(text(output.get("result"))) && positive(id(output.get("source_version"))),"已登记产出归属或数量无效");
                    var allocation=one(jdbc,"SELECT * FROM lt_lot_allocation WHERE tenant_id=? AND allocation_id=?",tenant,output.get("source_allocation_id"));
                    need(allocation!=null && Objects.equals(id(output.get("lot_id")),id(allocation.get("lot_id"))) && productionSource(tenant,wo,allocation)!=null,"已登记产出缺少原生产来源");
                    recorded.add(map("outputId",str(output.get("output_id")),"commandId",text(output.get("command_id")),"lotId",str(output.get("lot_id")),
                        "sourceAllocationId",str(output.get("source_allocation_id")),"quantity",plain(qty(output.get("quantity"))),"result",output.get("result")));
                }
                var choices=sources.stream().filter(source->finalStep || "SCRAPPED".equals(source.get("status"))).toList();
                if(batchOnly && choices.isEmpty() && recorded.isEmpty())continue;
                result.put(stepId,map("cardId",cardId.toString(),"stepRecordId",stepId.toString(),"mesWoId",woId.toString(),"materialId",materialId.toString(),
                    "isFinalStep",finalStep,"policy",policy,"sources",choices,"outputs",recorded,"hasMore",false,"nextCursor",null));
            }
            return result;
        } catch(ServiceException|DataAccessException|IllegalArgumentException|IllegalStateException error) { return Map.of(); }
    }

    /** All local revisions are read, with an explicit null latest only when no manifest exists. */
    @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> completionState(Long woId,Long erpWoId,Long materialId) {
        String tenant=context.tenant();
        try {
            var wo=workOrder(tenant,woId,materialId);need(Objects.equals(erpWoId,id(wo.get("erp_wo_id"))),"完工上下文ERP来源不一致");
            var rows=jdbc.queryForList("SELECT * FROM pro_completion_manifest WHERE tenant_id=? AND mes_wo_id=? ORDER BY revision DESC,manifest_id DESC",tenant,woId);
            var revisions=new ArrayList<Map<String,Object>>();var owners=new HashMap<Long,Map<String,Object>>();var versions=new HashSet<Long>();
            for(var row:rows) {
                need(versions.add(id(row.get("revision"))),"同一工单修订存在歧义");var owner=completionOwner(tenant,wo,row);owners.put(id(row.get("manifest_id")),owner);
                if(Set.of("APPLIED","RECEIVED").contains(text(row.get("status"))))confirmedReceipt(owner,row);
                revisions.add(header(row));
            }
            if(rows.isEmpty())return map("latest",null,"revisions",revisions,"cancellation",null,"hasMore",false,"nextCursor",null,"truncated",false);
            var latest=rows.get(0);var original=owners.get(id(latest.get("manifest_id")));var cancellation=cancellation(tenant,latest,original);
            if("CANCELLED".equals(latest.get("status"))) {
                need("COMPLETED".equals(wo.get("wo_status")) && cancellation!=null && "CANCELLED".equals(cancellation.get("status")) && Boolean.TRUE.equals(cancellation.remove("acknowledged")),"已取消清单尚缺匹配的ERP持久ACK，不能开放修订");
                if(original.get("result_payload")!=null)need("CANCELLED".equals(read(original.get("result_payload")).path("outcome").asText()),"原完工业务已应用，必须补偿");
            } else if(cancellation!=null) {
                cancellation.remove("acknowledged");
                need("CANCEL_REQUESTED".equals(latest.get("status")) || "COMPENSATION_REQUIRED".equals(cancellation.get("status")),"取消状态缺少终态证据");
            } else need(!"CANCEL_REQUESTED".equals(latest.get("status")),"取消申请缺少原发布凭据");
            return map("latest",revisions.get(0),"revisions",revisions,"cancellation",cancellation,"hasMore",false,"nextCursor",null,"truncated",false);
        } catch(ServiceException|DataAccessException|IllegalArgumentException|IllegalStateException error) { return null; }
    }

    private Map<String,Object> workOrder(String tenant,Long woId,Long materialId) {
        need(positive(woId) && positive(materialId),"详情缺少实际工单或物料");
        var wo=one(jdbc,"SELECT * FROM pro_work_order WHERE tenant_id=? AND mes_wo_id=? AND del_flag='0'",tenant,woId);
        need(wo!=null && Objects.equals(materialId,id(wo.get("material_id"))),"详情不属于当前租户工单");return wo;
    }
    private Map<String,Object> productionSource(String tenant,Map<String,Object> wo,Map<String,Object> allocation) {
        String evidence=text(allocation.get("evidence_json"));need(evidence!=null && TraceHashes.sha256(evidence).equals(allocation.get("evidence_hash")),"产品分配凭据摘要不一致");
        var proof=read(evidence);String eventId=proof.path("sourceEventId").asText();
        var inbox=one(jdbc,"SELECT * FROM mes_trace_inbox WHERE tenant_id=? AND event_id=? AND source_module='ERP' AND status='APPLIED'",tenant,eventId);
        need(inbox!=null && "ERP_PRODUCTION_LOT_AUTHORIZED".equals(inbox.get("event_type")),"产品批次缺少已核实的原ERP授权");event(inbox);
        var source=read(inbox.get("payload"));var verified=read(inbox.get("result_payload"));
        need("ERP_OUTBOX".equals(verified.path("verifiedSource").asText()) && eventId.equals(verified.path("sourceEventId").asText())
            && Objects.equals(inbox.get("content_hash"),verified.path("sourceContentHash").asText()),"产品来源未通过ERP owner凭据核验");
        if(!Objects.equals(id(wo.get("mes_wo_id")),number(source,"mesWoId")))return null;
        need("PRODUCTION_OUTPUT".equals(allocation.get("source_type")) && positive(number(source,"sourceLineId")) && "PRODUCTION_OUTPUT".equals(source.path("sourceType").asText()) && Objects.equals(id(wo.get("erp_wo_id")),number(source,"erpWoId"))
            && Objects.equals(id(wo.get("erp_wo_id")),number(source,"sourceId")) && Objects.equals(id(wo.get("material_id")),number(source,"materialId"))
            && Objects.equals(id(allocation.get("source_id")),number(source,"sourceId")) && Objects.equals(id(allocation.get("source_line_id")),number(source,"sourceLineId"))
            && Objects.equals(id(allocation.get("erp_batch_id")),number(source,"erpBatchId")) && positive(number(source,"erpBatchId"))
            && proof.path("source").equals(source) && proof.path("sourceVersion").equals(verified.path("sourceVersion")) && positive(number(verified,"sourceVersion"))
            && proof.path("policy").equals(verified.path("policy")) && verified.path("policy").path("batchManaged").asBoolean() && !verified.path("policy").path("serialManaged").asBoolean(),"产品分配与原ERP工单/批次/策略不一致");
        var lot=one(jdbc,"SELECT * FROM lt_lot WHERE tenant_id=? AND lot_id=? AND del_flag='0'",tenant,allocation.get("lot_id"));
        need(lot!=null && Objects.equals(id(wo.get("material_id")),id(lot.get("material_id"))) && qty(lot.get("quantity")).signum()>0 && qty(lot.get("consumed_qty")).signum()==0,"实际产品lot来源或数量无效");
        BigDecimal quantity=qty(lot.get("quantity"));
        var siblings=jdbc.queryForList("SELECT quantity FROM lt_lot_allocation WHERE tenant_id=? AND source_type='PRODUCTION_OUTPUT' AND source_id=? AND source_line_id=?",tenant,allocation.get("source_id"),allocation.get("source_line_id"));
        var mappings=jdbc.queryForList("SELECT quantity,erp_batch_id FROM lt_lot_allocation WHERE tenant_id=? AND lot_id=?",tenant,allocation.get("lot_id"));
        need(qty(allocation.get("quantity")).compareTo(quantity)>=0 && sum(siblings).compareTo(MesMaterialTraceWriter.decimal(source,"quantity"))<=0
            && sum(mappings).compareTo(quantity)<=0 && mappings.stream().allMatch(row->Objects.equals(id(row.get("erp_batch_id")),number(source,"erpBatchId"))),"产品lot批次映射或来源额度不守恒");
        boolean good="IN_STOCK".equals(lot.get("status")) && "PASS".equals(lot.get("qc_status"));
        boolean scrap="SCRAPPED".equals(lot.get("status")) && Set.of("PASS","FAIL").contains(text(lot.get("qc_status")));
        if(!good && !scrap)return null;
        need(jdbc.queryForList("SELECT sn_id FROM pro_sn WHERE tenant_id=? AND product_lot_id=? AND del_flag='0'",tenant,allocation.get("lot_id")).isEmpty(),"批次产品lot已有单件产出归属");
        return map("lotId",str(lot.get("lot_id")),"lotCode",text(lot.get("lot_code")),"sourceAllocationId",str(allocation.get("allocation_id")),
            "materialId",str(lot.get("material_id")),"mesWoId",str(wo.get("mes_wo_id")),"erpBatchId",str(allocation.get("erp_batch_id")),"quantity",plain(quantity),"status",lot.get("status"),"qcStatus",lot.get("qc_status"));
    }
    private Map<String,Object> completionOwner(String tenant,Map<String,Object> wo,Map<String,Object> manifest) {
        need(Set.of("FROZEN","APPLIED","RECEIVED","CANCEL_REQUESTED","CANCELLED").contains(text(manifest.get("status")))
            && Objects.equals(id(wo.get("erp_wo_id")),id(manifest.get("erp_wo_id"))) && Objects.equals(id(wo.get("material_id")),id(manifest.get("material_id"))),"完工清单归属或状态无效");
        var owner=one(jdbc,"SELECT * FROM mes_trace_outbox WHERE tenant_id=? AND source_module='MES' AND event_type='COMPLETION_MANIFEST_FROZEN' AND aggregate_type='COMPLETION_MANIFEST' AND aggregate_id=?",tenant,manifest.get("manifest_id"));
        need(owner!=null,"清单缺少原owner发布凭据");event(owner);var body=read(owner.get("payload"));
        need(Objects.equals(id(manifest.get("manifest_id")),number(body,"manifestId")) && Objects.equals(id(manifest.get("revision")),number(body,"revision"))
            && Objects.equals(id(wo.get("mes_wo_id")),number(body,"mesWoId")) && Objects.equals(id(wo.get("erp_wo_id")),number(body,"erpWoId")) && Objects.equals(id(wo.get("material_id")),number(body,"materialId"))
            && Objects.equals(manifest.get("content_hash"),body.path("manifestHash").asText()) && Objects.equals(manifest.get("material_hash"),body.path("materialHash").asText())
            && Objects.equals(id(manifest.get("line_count")),number(body,"lineCount")) && Objects.equals(id(manifest.get("material_line_count")),number(body,"materialLineCount"))
            && qty(manifest.get("good_qty")).compareTo(MesMaterialTraceWriter.decimal(body,"completedQty"))==0 && qty(manifest.get("scrap_qty")).compareTo(MesMaterialTraceWriter.decimal(body,"scrapQty"))==0,"清单摘要与原不可变事件不一致");return owner;
    }
    private void confirmedReceipt(Map<String,Object> owner,Map<String,Object> manifest) {
        need("APPLIED".equals(owner.get("status")) && owner.get("result_payload")!=null,"已确认清单缺少持久owner ACK");var result=read(owner.get("result_payload"));
        need(Objects.equals(owner.get("event_id"),result.path("sourceEventId").asText()) && Objects.equals(id(manifest.get("manifest_id")),number(result,"manifestId"))
            && Objects.equals(id(manifest.get("revision")),number(result,"revision")) && Objects.equals(manifest.get("content_hash"),result.path("manifestHash").asText()),"完工回执不属于此清单");
        boolean noGood=qty(manifest.get("good_qty")).signum()==0;String outcome=result.path("outcome").asText();
        need(noGood?"NO_GOOD_OUTPUT".equals(outcome) && nullId(result,"receiptId") && nullId(result,"postingGroupId"):
            (outcome.isEmpty() || "POSTED".equals(outcome)) && positive(number(result,"receiptId")) && positive(number(result,"postingGroupId")),"完工业务效果未知或矛盾");
    }
    private Map<String,Object> cancellation(String tenant,Map<String,Object> manifest,Map<String,Object> owner) {
        var row=one(jdbc,"SELECT * FROM mes_trace_outbox WHERE tenant_id=? AND source_module='MES' AND event_type='COMPLETION_MANIFEST_CANCEL_REQUESTED' AND aggregate_type=? AND aggregate_id=? ORDER BY aggregate_version DESC LIMIT 1",tenant,CompletionCancellationRequest.AGGREGATE_TYPE,manifest.get("manifest_id"));
        if(row==null)return null;event(row);CompletionCancellationRequest request;
        try{request=json.treeToValue(read(row.get("payload")),CompletionCancellationRequest.class);}catch(java.io.IOException error){throw new IllegalArgumentException("取消来源无效",error);}
        need(request.originalEvent().equals(event(owner)) && Objects.equals(request.manifestId(),id(manifest.get("manifest_id"))) && request.revision()==id(manifest.get("revision"))
            && request.manifestHash().equals(manifest.get("content_hash")),"取消申请不属于当前原清单");
        JsonNode applied=row.get("result_payload")==null?null:read(row.get("result_payload"));boolean acknowledged="APPLIED".equals(row.get("status")) && applied!=null;
        if(applied==null) {
            var typed=one(jdbc,"SELECT * FROM mes_trace_inbox WHERE tenant_id=? AND source_module='ERP' AND event_type='COMPLETION_MANIFEST_CANCELLATION_RESULT' AND aggregate_id=? AND aggregate_version=? AND status='APPLIED'",tenant,manifest.get("manifest_id"),row.get("aggregate_version"));
            if(typed!=null) {
                event(typed);var proof=read(typed.get("result_payload"));need("ERP_OUTBOX".equals(proof.path("verifiedSource").asText()) && Objects.equals(typed.get("event_id"),proof.path("sourceEventId").asText()) && Objects.equals(typed.get("content_hash"),proof.path("sourceContentHash").asText()),"取消投影缺少ERP凭据核验");applied=read(typed.get("payload"));
            }
        }
        if(applied==null)return map("status","CANCEL_REQUESTED","reason",read(row.get("payload")).path("reason").asText(),"acknowledged",false);
        CompletionCancellationResult result;try{result=json.treeToValue(applied,CompletionCancellationResult.class);}catch(java.io.IOException error){throw new IllegalArgumentException("取消回执无效",error);}
        var original=request.originalEvent();need(Objects.equals(row.get("event_id"),result.sourceEventId()) && Objects.equals(row.get("event_id"),result.cancellationEventId())
            && original.eventId().equals(result.originalEventId()) && original.contentHash().equals(result.originalEventHash()) && request.manifestId().equals(result.manifestId()) && request.revision()==result.revision()
            && request.manifestHash().equals(result.manifestHash()),"取消回执不匹配原申请");return map("status",result.outcome(),"reason",result.reason(),"acknowledged",acknowledged);
    }
    private Map<String,Object> header(Map<String,Object> row) {
        return map("manifestId",str(row.get("manifest_id")),"revision",Math.toIntExact(id(row.get("revision"))),"mesWoId",str(row.get("mes_wo_id")),"erpWoId",str(row.get("erp_wo_id")),"materialId",str(row.get("material_id")),
            "goodQty",plain(qty(row.get("good_qty"))),"scrapQty",plain(qty(row.get("scrap_qty"))),"contentHash",text(row.get("content_hash")),"lineCount",str(row.get("line_count")),"status",text(row.get("status")));
    }
    private MaterialTraceEvent event(Map<String,Object> row) {
        return new MaterialTraceEvent(text(row.get("event_id")),text(row.get("tenant_id")),text(row.get("source_module")),text(row.get("event_type")),text(row.get("aggregate_type")),id(row.get("aggregate_id")),id(row.get("aggregate_version")),Math.toIntExact(id(row.get("schema_version"))),text(row.get("payload")),text(row.get("content_hash")));
    }
    private JsonNode read(Object value) {try{need(value!=null,"缺少不可变来源内容");return json.readTree(text(value));}catch(java.io.IOException error){throw new IllegalArgumentException("来源内容无效",error);}}
    private static boolean nullId(JsonNode node,String key){return node.path(key).isMissingNode() || node.path(key).isNull();}
    private static String str(Object value){Long key=id(value);return key==null?null:key.toString();}
    private static String plain(BigDecimal value){return value.stripTrailingZeros().toPlainString();}
    private static BigDecimal sum(List<Map<String,Object>> rows){return rows.stream().map(row->qty(row.get("quantity"))).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private static Map<String,Object> map(Object... pairs){var result=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;}
}
