package com.nexus.erp.prd.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.WorkOrderTrackingPolicyPort;
import com.nexus.erp.bd.domain.vo.IndentedBomLineVo;
import com.nexus.erp.bd.service.IBdBomService;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.wm.trace.ErpTraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Release policy evidence belongs to the real local ERP source transaction, never to a live-policy cache. */
@Service @DS("erp") @RequiredArgsConstructor
public class PrdWorkOrderTrackingPolicyService implements WorkOrderTrackingPolicyPort {
    private final JdbcTemplate jdbc;
    private final ErpTraceContext context;
    private final ObjectMapper json;
    private final IBdBomService bom;

    /** Called only by release, before its CREATED/APPROVED claim; any later source failure rolls it back. */
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public String freezeForRelease(Long erpWoId) { return freeze(erpWoId,null); }

    /** Bind the initially resolved release payload to the same now-locked source facts. */
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public String freezeForRelease(PrdWorkOrder expected) {
        require(expected!=null,"原工单release来源缺失");
        return freeze(expected.getWoId(),expected);
    }
    private String freeze(Long erpWoId,PrdWorkOrder expected) {
        String tenant=context.tenant();context.requireWrite(tenant);positive(erpWoId);
        var wo=one("SELECT * FROM prd_work_order WHERE tenant_id=? AND wo_id=? AND del_flag='0' FOR UPDATE",tenant,erpWoId);
        if(expected!=null) require(tenant.equals(expected.getTenantId())
            && Objects.equals(expected.getMaterialId(),number(wo,"material_id"))
            && Objects.equals(expected.getBomId(),number(wo,"bom_id"))
            && Objects.equals(expected.getBomVersion(),text(wo,"bom_version"))
            && Objects.equals(expected.getSourceType(),text(wo,"source_type"))
            && Objects.equals(expected.getSourceId(),number(wo,"source_id"))
            && Objects.equals(expected.getPlanId(),number(wo,"plan_id"))
            && Objects.equals(expected.getWoStatus(),text(wo,"wo_status"))
            && Objects.equals(expected.getBackflushFlag()==null?null:expected.getBackflushFlag().longValue(),number(wo,"backflush_flag")),
            "工单来源在下达读取后已改变，请刷新后重试，不能冻结或发布不同来源");
        require(Set.of("CREATED","APPROVED").contains(text(wo,"wo_status")),"已下达工单不能补造历史release策略，请核实原来源证据");
        Source source=source(tenant,erpWoId,wo);
        var previous=rows(tenant,erpWoId,true);
        if(!previous.isEmpty()) {
            verify(tenant,erpWoId,previous);
            require(previous.get(0).provenance()==WorkOrderTrackingPolicySnapshot.Provenance.ERP_RELEASE
                && previous.get(0).sourceHash().equals(TraceHashes.sha256(source.json())),"工单release来源已改变，不能覆盖原冻结策略");
            return previous.get(0).snapshotHash();
        }
        var policies=new TreeMap<Long,String>();
        for(Long material:source.materials()) {
            var row=one("SELECT * FROM bd_material WHERE tenant_id=? AND material_id=? AND del_flag='0' FOR UPDATE",tenant,material);
            require(Long.valueOf(1L).equals(number(row,"status")),"工单追踪策略物料未启用");
            policies.put(material,encode(policy(row)));
        }
        Instant now=databaseNow();
        return persist(tenant,erpWoId,source.json(),policies,WorkOrderTrackingPolicySnapshot.Provenance.ERP_RELEASE,
            "ERP_RELEASE/"+erpWoId,null,now);
    }

    @Override @Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true)
    public WorkOrderTrackingPolicySnapshot find(String tenantId,Long erpWoId,Long materialId) {
        context.requireTenant(tenantId);positive(erpWoId);positive(materialId);
        var rows=rows(tenantId,erpWoId,false);
        if(rows.isEmpty()) return WorkOrderTrackingPolicySnapshot.unknown(tenantId,erpWoId,materialId,"NO_RELEASE_POLICY_EVIDENCE");
        verify(tenantId,erpWoId,rows);
        return rows.stream().filter(row->row.material().equals(materialId)).findFirst()
            .map(row->new WorkOrderTrackingPolicySnapshot(tenantId,erpWoId,materialId,row.provenance(),decode(row.policyJson()),
                row.revision(),row.policyHash(),row.sourceHash(),row.snapshotHash(),row.evidenceRef(),row.frozenAt(),row.effectiveFrom(),row.actorId(),null))
            .orElseGet(()->WorkOrderTrackingPolicySnapshot.unknown(tenantId,erpWoId,materialId,"MATERIAL_NOT_IN_FROZEN_SCOPE"));
    }

    public record AdoptionSource(String sourceJson,String sourceHash,List<Long> materialIds) {
        public AdoptionSource {materialIds=List.copyOf(materialIds);}
    }
    @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public AdoptionSource previewAdoption(Long erpWoId) {
        String tenant=context.tenant();context.requireWrite(tenant);positive(erpWoId);
        var wo=one("SELECT * FROM prd_work_order WHERE tenant_id=? AND wo_id=? AND del_flag='0' FOR UPDATE",tenant,erpWoId);
        var source=source(tenant,erpWoId,wo);
        return new AdoptionSource(source.json(),TraceHashes.sha256(source.json()),new ArrayList<>(source.materials()));
    }
    Instant databaseNow() {return Objects.requireNonNull(jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(3)",Timestamp.class)).toInstant();}
    String persistAdopted(String tenant,Long woId,AdoptionSource source,SortedMap<Long,String> policies,
                          Map<String,Object> audit,Long approvedBy,Long adoptionId,Instant appliedAt) {
        context.requireWrite(tenant);
        require(rows(tenant,woId,true).isEmpty(),"工单已有冻结策略，不允许用采纳覆盖原release或其他已采纳证据");
        require(new ArrayList<>(policies.keySet()).equals(source.materialIds()),"原始策略证据没有完整覆盖当前锁定WO范围");
        var document=(com.fasterxml.jackson.databind.node.ObjectNode)tree(source.sourceJson());document.set("adoption",json.valueToTree(audit));
        return persist(tenant,woId,encode(document),policies,WorkOrderTrackingPolicySnapshot.Provenance.SOURCE_POLICY_ADOPTION,
            "SOURCE_POLICY_ADOPTION/"+adoptionId,approvedBy,appliedAt);
    }
    String verifyAdoptionResult(String tenant,Long woId,Long adoptionId,String requestHash,String expectedHash,Instant appliedAt) {
        var frozen=rows(tenant,woId,true);require(!frozen.isEmpty(),"原采纳结果缺少不可变工单策略");verify(tenant,woId,frozen);
        var first=frozen.get(0);
        require(first.provenance()==WorkOrderTrackingPolicySnapshot.Provenance.SOURCE_POLICY_ADOPTION
            && first.evidenceRef().equals("SOURCE_POLICY_ADOPTION/"+adoptionId) && first.snapshotHash().equals(expectedHash)
            && first.frozenAt().equals(appliedAt) && requestHash.equals(tree(first.sourceJson()).path("adoption").path("requestHash").asText()),
            "采纳审批与原不可变结果不符");return first.snapshotHash();
    }
    private String persist(String tenant,Long woId,String sourceJson,SortedMap<Long,String> policies,
                           WorkOrderTrackingPolicySnapshot.Provenance provenance,String ref,Long actor,Instant now) {
        String sourceHash=TraceHashes.sha256(sourceJson);var pending=new ArrayList<FrozenRow>();
        for(var entry:policies.entrySet()) pending.add(new FrozenRow(entry.getKey(),1,decode(entry.getValue()).version(),
            entry.getValue(),TraceHashes.sha256(entry.getValue()),sourceJson,sourceHash,null,provenance,ref,now,now,actor));
        String hash=groupHash(tenant,woId,pending);
        for(var row:pending) require(jdbc.update("""
            INSERT INTO wm_work_order_tracking_policy(snapshot_id,tenant_id,erp_wo_id,material_id,snapshot_version,
              policy_version,policy_json,policy_hash,source_json,source_hash,snapshot_hash,provenance,evidence_ref,
              frozen_at,effective_from,actor_id) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,IdWorker.getId(),tenant,woId,row.material(),row.revision(),row.policyVersion(),row.policyJson(),row.policyHash(),
            row.sourceJson(),row.sourceHash(),hash,row.provenance().name(),row.evidenceRef(),Timestamp.from(now),Timestamp.from(now),actor)==1,
            "工单追踪策略冻结失败");
        return hash;
    }
    private Source source(String tenant,Long woId,Map<String,Object> wo) {
        Long root=number(wo,"material_id");positive(root);
        var materials=new TreeSet<Long>();materials.add(root);
        var evidence=new LinkedHashMap<String,Object>();
        evidence.put("erpWoId",woId.toString());evidence.put("tenantId",tenant);evidence.put("rootMaterialId",root.toString());
        for(String field:List.of("bom_id","bom_version","source_type","source_id","plan_id","backflush_flag"))
            evidence.put(field,text(wo,field));
        Long bomId=number(wo,"bom_id");var components=new ArrayList<Map<String,Object>>();
        if(bomId!=null) {
            var header=one("SELECT * FROM bd_bom WHERE tenant_id=? AND bom_id=? AND del_flag='0' FOR UPDATE",tenant,bomId);
            require(root.equals(number(header,"parent_material_id")),"工单BOM根物料不符");
            require(text(wo,"bom_version")==null || Objects.equals(text(wo,"bom_version"),text(header,"bom_version")),"工单BOM版本不符");
            List<IndentedBomLineVo> exploded=bom.explodeBom(bomId);
            require(exploded!=null,"工单BOM实际展开证据缺失");
            for(var line:exploded) {
                require(line!=null,"工单BOM存在空组件来源");positive(line.getMaterialId());materials.add(line.getMaterialId());
                var component=new LinkedHashMap<String,Object>();component.put("materialId",line.getMaterialId().toString());
                component.put("bomId",string(line.getBomId()));component.put("detailId",string(line.getDetailId()));
                component.put("quantity",line.getCumulativeQuantity()==null?null:line.getCumulativeQuantity().toPlainString());
                component.put("level",line.getLevel());components.add(component);
            }
            components.sort(Comparator.comparing(this::encode));
        }
        evidence.put("bomComponents",components);
        var requirements=new ArrayList<Map<String,Object>>();
        if("MATERIAL_REQUIREMENT".equals(text(wo,"source_type"))) {
            Long req=number(wo,"source_id");positive(req);positive(number(wo,"plan_id"));
            var head=one("SELECT * FROM prd_material_requirement WHERE tenant_id=? AND requirement_id=? AND del_flag='0' FOR UPDATE",tenant,req);
            require(Objects.equals(number(wo,"plan_id"),number(head,"plan_id")),"工单来源需求不属于原计划");
            var details=jdbc.queryForList("SELECT * FROM prd_material_requirement_detail WHERE tenant_id=? AND requirement_id=? AND del_flag='0' ORDER BY detail_id FOR UPDATE",tenant,req);
            for(var detail:details) {
                Long material=number(detail,"material_id");positive(material);materials.add(material);
                var item=new LinkedHashMap<String,Object>();
                for(String field:List.of("detail_id","requirement_id","material_id","required_quantity","backflush_flag")) item.put(field,text(detail,field));
                requirements.add(item);
            }
        }
        evidence.put("requirementComponents",requirements);evidence.put("materialIds",materials.stream().map(Object::toString).toList());
        return new Source(materials,encode(evidence));
    }
    private record Source(SortedSet<Long> materials,String json) { }
    private record FrozenRow(Long material,int revision,Long policyVersion,String policyJson,String policyHash,
        String sourceJson,String sourceHash,String snapshotHash,WorkOrderTrackingPolicySnapshot.Provenance provenance,
        String evidenceRef,Instant frozenAt,Instant effectiveFrom,Long actorId) { }

    private List<FrozenRow> rows(String tenant,Long woId,boolean lock) {
        return jdbc.query("SELECT * FROM wm_work_order_tracking_policy WHERE tenant_id=? AND erp_wo_id=? ORDER BY material_id"+(lock?" FOR UPDATE":""),
            (rs,i)->new FrozenRow(rs.getLong("material_id"),rs.getInt("snapshot_version"),rs.getLong("policy_version"),
                rs.getString("policy_json"),rs.getString("policy_hash"),rs.getString("source_json"),rs.getString("source_hash"),
                rs.getString("snapshot_hash"),WorkOrderTrackingPolicySnapshot.Provenance.valueOf(rs.getString("provenance")),
                rs.getString("evidence_ref"),rs.getTimestamp("frozen_at").toInstant(),rs.getTimestamp("effective_from").toInstant(),
                rs.getObject("actor_id")==null?null:rs.getLong("actor_id")),tenant,woId);
    }
    private void verify(String tenant,Long woId,List<FrozenRow> rows) {
        var first=rows.get(0);var ids=new ArrayList<Long>();
        for(var row:rows) {
            positive(row.material());ids.add(row.material());
            require(row.revision()==1 && row.policyHash().equals(TraceHashes.sha256(row.policyJson()))
                && row.sourceHash().equals(TraceHashes.sha256(row.sourceJson())) && row.policyVersion().equals(decode(row.policyJson()).version()),
                "工单冻结策略内容或版本证据不符");
            require(row.sourceJson().equals(first.sourceJson()) && row.sourceHash().equals(first.sourceHash())
                && row.snapshotHash().equals(first.snapshotHash()) && row.provenance()==first.provenance()
                && row.evidenceRef().equals(first.evidenceRef()) && row.frozenAt().equals(first.frozenAt())
                && row.effectiveFrom().equals(first.effectiveFrom()) && row.frozenAt().equals(row.effectiveFrom())
                && Objects.equals(row.actorId(),first.actorId()),"工单冻结策略集合来源不一致");
            require(row.provenance()!=WorkOrderTrackingPolicySnapshot.Provenance.UNKNOWN,"持久化策略不能伪装UNKNOWN");
        }
        JsonNode source=tree(first.sourceJson());
        require(tenant.equals(source.path("tenantId").asText()) && woId.toString().equals(source.path("erpWoId").asText())
            && source.path("materialIds").isArray(),"工单冻结策略租户或来源身份不符");
        var expected=new ArrayList<Long>();for(var id:source.path("materialIds")) {
            try {expected.add(Long.valueOf(id.asText()));} catch(RuntimeException invalid) {throw new ServiceException("工单冻结策略物料集合无效");}
        }
        require(ids.equals(expected) && new HashSet<>(ids).size()==ids.size(),"工单冻结策略集合缺失或重复");
        require(first.snapshotHash().equals(groupHash(tenant,woId,rows)),"工单冻结策略整组摘要不符");
        if(first.provenance()==WorkOrderTrackingPolicySnapshot.Provenance.ERP_RELEASE)
            require(first.evidenceRef().equals("ERP_RELEASE/"+woId),"原工单release证据来源不符");
        else {
            var policies=new TreeMap<Long,TrackingPolicy>();for(var row:rows)policies.put(row.material(),decode(row.policyJson()));
            PrdWorkOrderPolicyAdoptionService.verifyFrozen(json,tenant,woId,first.frozenAt(),first.actorId(),first.evidenceRef(),source,policies);
        }
    }
    private static String groupHash(String tenant,Long woId,List<FrozenRow> rows) {
        var first=rows.get(0);
        String policies=rows.stream().map(row->row.material()+":"+row.policyHash()).collect(Collectors.joining(","));
        return TraceHashes.fingerprint("WO_POLICY_V1",tenant,woId,first.revision(),first.provenance(),first.evidenceRef(),
            first.sourceHash(),first.frozenAt().toEpochMilli(),first.effectiveFrom().toEpochMilli(),first.actorId(),policies);
    }
    private TrackingPolicy policy(Map<String,Object> row) {
        for(String field:List.of("is_batch_manage","is_serial_manage","allow_mixed_lot","tracking_version","tracking_stage"))
            require(row.containsKey(field) && row.get(field)!=null,"新release缺少明确追踪策略元数据，须完成迁移或核实后下达");
        Long batch=number(row,"is_batch_manage"),serial=number(row,"is_serial_manage"),mixed=number(row,"allow_mixed_lot"),version=number(row,"tracking_version");
        require((batch==0 || batch==1) && (serial==0 || serial==1) && (mixed==0 || mixed==1) && version>0,"物料追踪开关或版本无效");
        try {return new TrackingPolicy(batch==1,serial==1,mixed==1,version,TrackingStage.valueOf(text(row,"tracking_stage")));}
        catch(IllegalArgumentException invalid) {throw new ServiceException("物料追踪策略阶段无效");}
    }
    private Map<String,Object> one(String sql,Object... args) {
        var rows=jdbc.queryForList(sql,args);require(rows.size()==1,"工单、BOM、需求或物料来源不存在或不属于当前租户");return rows.get(0);
    }
    private String encode(Object value) {try{return json.writeValueAsString(value);}catch(Exception invalid){throw new ServiceException("工单追踪策略JSON冻结失败");}}
    private TrackingPolicy decode(String value) {try{return json.readValue(value,TrackingPolicy.class);}catch(Exception invalid){throw new ServiceException("工单追踪策略JSON证据无效");}}
    private JsonNode tree(String value) {try{return json.readTree(value);}catch(Exception invalid){throw new ServiceException("工单来源JSON证据无效");}}
    private static String text(Map<String,Object> row,String key) {return string(row.get(key));}
    private static String string(Object value) {return value==null?null:value.toString();}
    private static Long number(Map<String,Object> row,String key) {
        Object value=row.get(key);if(value==null)return null;if(value instanceof Boolean flag)return flag?1L:0L;
        require(value instanceof Number,"工单策略来源字段类型无效");return ((Number)value).longValue();
    }
    private static void positive(Long id) {require(id!=null && id>0,"工单追踪策略来源ID无效");}
    private static void require(boolean allowed,String message) {if(!allowed)throw new ServiceException(message);}
}
