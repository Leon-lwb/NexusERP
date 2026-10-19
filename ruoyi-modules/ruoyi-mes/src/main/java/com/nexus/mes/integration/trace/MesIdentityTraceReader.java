package com.nexus.mes.integration.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.IdentityTraceReadPort;
import com.nexus.mes.trace.MesTraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static com.nexus.common.trace.TraceReadValues.*;

@Service @DS("mes") @RequiredArgsConstructor
public class MesIdentityTraceReader implements IdentityTraceReadPort {
    private final JdbcTemplate jdbc;private final MesTraceContext context;private final com.nexus.common.trace.port.SerialIdentityPort canonicalIdentities;
    @Override public String sourceModule(){return "MES";}
    @Override @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW,isolation=Isolation.REPEATABLE_READ)
    public RecallSubjectSnapshot recallSubject(String tenant,String scope,Long source,int capacity){
        context.requireTenant(tenant);return TraceRecallSnapshots.read(this,tenant,scope,source,capacity);
    }
    @Override @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public IdentityTracePart identity(String tenant,Long identity,String section,String cursor,int limit){
        context.requireTenant(tenant);validate(identity,limit);
        var serials=jdbc.queryForList("SELECT sn_id,sn_code,material_id,mes_wo_id,identity_version FROM pro_sn WHERE tenant_id=? AND identity_id=? AND del_flag='0'",tenant,identity);
        if(serials.size()>1)throw new ServiceException("MES正式身份映射冲突，须先对账");
        if(serials.isEmpty())return new IdentityTracePart("MES",identity,null,null,"IDENTITY",Map.of(),List.of("当前身份未找到经证据确认的MES映射，不使用同码补联"));
        var sn=serials.get(0);Long local=id(sn.get("sn_id"));
        var formal=canonicalIdentities.find(tenant,identity);
        if(formal!=null && (!Objects.equals(identity,formal.identityId()) || !Objects.equals(id(sn.get("material_id")),formal.materialId())))throw new ServiceException("MES映射与ERP正式身份的物料不一致，须先核实原映射");
        Set<String> allowed=Set.of("CONSUMPTION","TEST","IPQC_WORK_ORDER","MANIFEST");
        if(section!=null && !allowed.contains(section))throw new ServiceException("MES追溯范围无效");
        var sections=new LinkedHashMap<String,TracePage<Map<String,Object>>>();
        if(section==null || section.equals("CONSUMPTION")){
            long after=TraceCursor.decode(section==null?null:cursor,tenant,identity,"CONSUMPTION");
            var rows=jdbc.queryForList("SELECT c.entry_id,c.component_material_id,c.component_lot_id,l.lot_code,l.erp_batch_id,JSON_UNQUOTE(JSON_EXTRACT(a.evidence_json,'$.source.identityId')) component_identity_id,c.quantity,c.wo_consumption_id,c.process_id,c.entry_type,c.original_entry_id,c.policy_version,c.created_at FROM lt_sn_consumption c LEFT JOIN lt_lot l ON CAST(l.tenant_id AS BINARY(80))=CAST(c.tenant_id AS BINARY(80)) AND l.lot_id=c.component_lot_id LEFT JOIN lt_wo_consumption w ON CAST(w.tenant_id AS BINARY(80))=CAST(c.tenant_id AS BINARY(80)) AND w.consumption_id=c.wo_consumption_id LEFT JOIN lt_lot_allocation a ON CAST(a.tenant_id AS BINARY(80))=CAST(w.tenant_id AS BINARY(80)) AND a.allocation_id=w.source_allocation_id WHERE c.tenant_id=? AND c.identity_id=? AND c.entry_id>? ORDER BY c.entry_id LIMIT ?",tenant,identity,after,limit+1);
            sections.put("CONSUMPTION",page(tenant,identity,"CONSUMPTION",rows,"entry_id",limit));
        }
        if(section==null || section.equals("TEST")){
            long after=TraceCursor.decode(section==null?null:cursor,tenant,identity,"TEST");
            var rows=jdbc.queryForList("SELECT test_record_id,test_no,process_id,item_code,item_name,result,numeric_value,text_value,test_time,content_version FROM mes_test_record WHERE tenant_id=? AND sn_id=? AND del_flag='0' AND test_record_id>? ORDER BY test_record_id LIMIT ?",tenant,local,after,limit+1);
            sections.put("TEST",page(tenant,identity,"TEST",rows,"test_record_id",limit));
        }
        if(section==null || section.equals("IPQC_WORK_ORDER")){
            long after=TraceCursor.decode(section==null?null:cursor,tenant,identity,"IPQC_WORK_ORDER");
            var rows=jdbc.queryForList("SELECT ipqc_id,ipqc_no,mes_wo_id,process_id,sample_qty,defect_qty,inspect_result,inspect_time,'WORK_ORDER' trace_scope FROM qc_ipqc WHERE tenant_id=? AND mes_wo_id=? AND del_flag='0' AND ipqc_id>? ORDER BY ipqc_id LIMIT ?",tenant,sn.get("mes_wo_id"),after,limit+1);
            sections.put("IPQC_WORK_ORDER",page(tenant,identity,"IPQC_WORK_ORDER",rows,"ipqc_id",limit));
        }
        if(section==null || section.equals("MANIFEST")){
            long after=TraceCursor.decode(section==null?null:cursor,tenant,identity,"MANIFEST");
            var rows=jdbc.queryForList("SELECT m.manifest_id,m.revision,m.mes_wo_id,m.erp_wo_id,m.content_hash,m.status,l.line_no,l.result,l.batch_id,l.local_lot_id,l.source_version FROM pro_completion_manifest_line l JOIN pro_completion_manifest m ON CAST(m.tenant_id AS BINARY(80))=CAST(l.tenant_id AS BINARY(80)) AND m.manifest_id=l.manifest_id AND m.revision=l.revision WHERE l.tenant_id=? AND l.identity_id=? AND m.manifest_id>? ORDER BY m.manifest_id LIMIT ?",tenant,identity,after,limit+1);
            sections.put("MANIFEST",page(tenant,identity,"MANIFEST",rows,"manifest_id",limit));
        }
        var gaps=new ArrayList<String>();gaps.add("工单抽样IPQC仅表示WORK_ORDER范围；签署DHR原件未因当前追溯查询而改写");
        if(formal==null)gaps.add("ERP正式身份注册未确认，本地原码仅保留为历史事实，不能冒充正式码");
        return new IdentityTracePart("MES",identity,local,formal==null?null:formal.snCode(),"IDENTITY",sections,gaps,(String)sn.get("sn_code"));
    }
    @Override @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public BatchTracePart batch(String tenant,Long batch,String section,String cursor,int limit){
        context.requireTenant(tenant);validate(batch,limit);if(section!=null && !section.equals("LOTS"))throw new ServiceException("MES批次追溯范围无效");
        long after=TraceCursor.decode(cursor,tenant,batch,"BATCH_LOTS");
        var rows=jdbc.queryForList("SELECT a.allocation_id,a.erp_batch_id,a.lot_id,a.material_id,a.quantity allocated_quantity,a.source_type,a.source_id,a.source_line_id,a.allocation_version,l.lot_code,l.quantity,l.consumed_qty,l.status,l.produced_date,l.expiry_date FROM lt_lot_allocation a JOIN lt_lot l ON CAST(l.tenant_id AS BINARY(80))=CAST(a.tenant_id AS BINARY(80)) AND l.lot_id=a.lot_id AND l.del_flag='0' WHERE a.tenant_id=? AND a.erp_batch_id=? AND a.allocation_id>? ORDER BY a.allocation_id LIMIT ?",tenant,batch,after,limit+1);
        boolean more=rows.size()>limit;var page=new TracePage<>(rows.stream().limit(limit).map(TraceReadValues::wire).toList(),more?TraceCursor.encode(tenant,batch,"BATCH_LOTS",id(rows.get(limit-1).get("allocation_id"))):null,more);
        return new BatchTracePart("MES",batch,"BATCH",Map.of("LOTS",page),List.of("仅列有ERP来源凭据的MES子lot及额度；车间lot状态不替代ERP库存当前位置"));
    }
    private TracePage<Map<String,Object>> page(String tenant,Long identity,String section,List<Map<String,Object>> rows,String key,int limit){
        boolean more=rows.size()>limit;return new TracePage<>(rows.stream().limit(limit).map(TraceReadValues::wire).toList(),more?TraceCursor.encode(tenant,identity,section,id(rows.get(limit-1).get(key))):null,more);
    }
    @Override @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public TracePage<Long> affectedByIdentity(String tenant,Long component,long after,int limit){
        context.requireTenant(tenant);validate(component,limit);if(after<0)throw new ServiceException("实物追溯游标无效");
        var rows=jdbc.queryForList("SELECT DISTINCT net.identity_id FROM (SELECT c.identity_id,c.wo_consumption_id,SUM(CASE WHEN c.entry_type='CONSUME' THEN c.quantity WHEN c.entry_type='REVERSE' THEN -c.quantity ELSE 0 END) net_quantity FROM lt_sn_consumption c JOIN lt_wo_consumption w ON CAST(w.tenant_id AS BINARY(80))=CAST(c.tenant_id AS BINARY(80)) AND w.consumption_id=c.wo_consumption_id JOIN lt_lot_allocation a ON CAST(a.tenant_id AS BINARY(80))=CAST(w.tenant_id AS BINARY(80)) AND a.allocation_id=w.source_allocation_id WHERE c.tenant_id=? AND c.identity_id>? AND JSON_UNQUOTE(JSON_EXTRACT(a.evidence_json,'$.source.identityId'))=? GROUP BY c.identity_id,c.wo_consumption_id HAVING net_quantity>0) net ORDER BY net.identity_id LIMIT ?",Long.class,tenant,after,component.toString(),limit+1);
        boolean more=rows.size()>limit;var selected=rows.stream().limit(limit).toList();return new TracePage<>(selected,more?selected.get(selected.size()-1).toString():null,more);
    }
    @Override @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public Set<Long> batchesForIdentities(String tenant,List<Long> identities){
        context.requireTenant(tenant);if(identities==null || identities.isEmpty())return Set.of();
        if(identities.size()>500 || identities.stream().anyMatch(id->id==null || id<=0))throw new ServiceException("批次关联查询参数无效");
        var args=new ArrayList<Object>();args.add(tenant);args.addAll(identities);
        return new HashSet<>(jdbc.queryForList("SELECT DISTINCT a.erp_batch_id FROM pro_sn n JOIN lt_lot_allocation a ON CAST(a.tenant_id AS BINARY(80))=CAST(n.tenant_id AS BINARY(80)) AND a.lot_id=n.product_lot_id WHERE n.tenant_id=? AND n.del_flag='0' AND n.identity_id IN ("+String.join(",",Collections.nCopies(identities.size(),"?"))+")",Long.class,args.toArray()));
    }
    @Override @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public TracePage<Long> affectedByBatch(String tenant,Long batch,long after,int limit){
        context.requireTenant(tenant);validate(batch,limit);if(after<0)throw new ServiceException("召回游标无效");
        var rows=jdbc.queryForList("SELECT DISTINCT net.identity_id FROM (SELECT c.identity_id,c.component_material_id,c.component_lot_id,SUM(CASE WHEN c.entry_type='CONSUME' THEN c.quantity WHEN c.entry_type='REVERSE' THEN -c.quantity ELSE 0 END) net_quantity FROM lt_sn_consumption c WHERE c.tenant_id=? AND c.identity_id>? AND EXISTS (SELECT 1 FROM lt_lot_allocation a WHERE a.tenant_id=? AND CAST(a.tenant_id AS BINARY(80))=CAST(c.tenant_id AS BINARY(80)) AND a.lot_id=c.component_lot_id AND a.erp_batch_id=?) GROUP BY c.identity_id,c.component_material_id,c.component_lot_id HAVING net_quantity>0) net ORDER BY net.identity_id LIMIT ?",Long.class,tenant,after,tenant,batch,limit+1);
        boolean more=rows.size()>limit;var selected=rows.stream().limit(limit).toList();return new TracePage<>(selected,more?selected.get(selected.size()-1).toString():null,more);
    }
    @Override @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public TracePage<WorkOrderTraceImpact> unallocatedByBatch(String tenant,Long batch,long after,int limit){
        return unallocated(tenant,batch,after,limit,false);
    }
    @Override @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public TracePage<WorkOrderTraceImpact> unallocatedByIdentity(String tenant,Long identity,long after,int limit){
        return unallocated(tenant,identity,after,limit,true);
    }
    private TracePage<WorkOrderTraceImpact> unallocated(String tenant,Long source,long after,int limit,boolean serial){
        context.requireTenant(tenant);validate(source,limit);if(after<0)throw new ServiceException("工单范围游标无效");
        String predicate=serial?"a.allocation_id=w.source_allocation_id AND JSON_UNQUOTE(JSON_EXTRACT(a.evidence_json,'$.source.identityId'))=?":"a.erp_batch_id=?";
        String sql="SELECT w.consumption_id,w.wo_id,w.material_id,w.consumed_qty,COALESCE(net.assigned_quantity,0) assigned_quantity FROM lt_wo_consumption w LEFT JOIN (SELECT c.wo_consumption_id,SUM(CASE WHEN c.entry_type='CONSUME' THEN c.quantity WHEN c.entry_type='REVERSE' THEN -c.quantity ELSE 0 END) assigned_quantity FROM lt_sn_consumption c WHERE c.tenant_id=? GROUP BY c.wo_consumption_id) net ON net.wo_consumption_id=w.consumption_id WHERE w.tenant_id=? AND w.del_flag='0' AND w.consumption_id>? AND w.consumed_qty>COALESCE(net.assigned_quantity,0) AND EXISTS (SELECT 1 FROM lt_lot_allocation a WHERE a.tenant_id=? AND CAST(a.tenant_id AS BINARY(80))=CAST(w.tenant_id AS BINARY(80)) AND a.lot_id=w.lot_id AND (w.source_allocation_id IS NULL OR a.allocation_id=w.source_allocation_id) AND "+predicate+") ORDER BY w.consumption_id LIMIT ?";
        var rows=jdbc.queryForList(sql,tenant,tenant,after,tenant,serial?source.toString():source,limit+1);
        boolean more=rows.size()>limit;var selected=rows.stream().limit(limit).map(row->new WorkOrderTraceImpact("MES",id(row.get("consumption_id")),id(row.get("wo_id")),id(row.get("material_id")),decimal(row.get("consumed_qty")),decimal(row.get("assigned_quantity")))).toList();
        return new TracePage<>(selected,more?selected.get(selected.size()-1).consumptionId().toString():null,more);
    }
    @Override @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public TracePage<Long> productIdentitiesByWorkOrder(String tenant,Long wo,long after,int limit){
        context.requireTenant(tenant);validate(wo,limit);if(after<0)throw new ServiceException("工单实物游标无效");
        return idPage(jdbc.queryForList("SELECT identity_id FROM pro_sn WHERE tenant_id=? AND mes_wo_id=? AND del_flag='0' AND identity_id>? ORDER BY identity_id LIMIT ?",Long.class,tenant,wo,after,limit+1),limit);
    }
    @Override @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public TracePage<Long> productBatchesByWorkOrder(String tenant,Long wo,long after,int limit){
        context.requireTenant(tenant);validate(wo,limit);if(after<0)throw new ServiceException("工单产品批次游标无效");
        return idPage(jdbc.queryForList("SELECT DISTINCT a.erp_batch_id FROM pro_product_lot_output o JOIN lt_lot_allocation a ON CAST(a.tenant_id AS BINARY(80))=CAST(o.tenant_id AS BINARY(80)) AND a.allocation_id=o.source_allocation_id WHERE o.tenant_id=? AND o.mes_wo_id=? AND a.erp_batch_id>? ORDER BY a.erp_batch_id LIMIT ?",Long.class,tenant,wo,after,limit+1),limit);
    }
    private static java.math.BigDecimal decimal(Object value){return value==null?java.math.BigDecimal.ZERO:new java.math.BigDecimal(value.toString());}
    private static TracePage<Long> idPage(List<Long> rows,int limit){boolean more=rows.size()>limit;var selected=rows.stream().limit(limit).toList();return new TracePage<>(selected,more?selected.get(selected.size()-1).toString():null,more);}
    private static void validate(Long identity,int limit){if(identity==null || identity<=0 || limit<1 || limit>500)throw new ServiceException("身份或分页参数无效");}
}
