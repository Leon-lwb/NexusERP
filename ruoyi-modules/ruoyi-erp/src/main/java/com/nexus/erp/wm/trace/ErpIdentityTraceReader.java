package com.nexus.erp.wm.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.IdentityTraceReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static com.nexus.common.trace.TraceReadValues.*;

@Service @DS("erp") @RequiredArgsConstructor
public class ErpIdentityTraceReader implements IdentityTraceReadPort {
    private final JdbcTemplate jdbc;private final ErpTraceContext context;
    @Override public String sourceModule(){return "ERP";}
    @Override @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW,isolation=Isolation.REPEATABLE_READ)
    public RecallSubjectSnapshot recallSubject(String tenant,String scope,Long source,int capacity){
        context.requireTenant(tenant);return TraceRecallSnapshots.read(this,tenant,scope,source,capacity);
    }
    @Override @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public IdentityTracePart identity(String tenant,Long identity,String section,String cursor,int limit){
        context.requireTenant(tenant);validate(identity,limit);var serials=jdbc.queryForList("SELECT sn_id,sn_code,registration_command FROM wm_sn WHERE tenant_id=? AND sn_id=? AND del_flag='0'",tenant,identity);
        if(serials.size()!=1)throw new ServiceException("当前租户正式身份不存在");
        Set<String> allowed=Set.of("STOCK","MOVEMENTS","ALIASES");if(section!=null && !allowed.contains(section))throw new ServiceException("ERP追溯范围无效");
        var sections=new LinkedHashMap<String,TracePage<Map<String,Object>>>();
        if(section==null || section.equals("STOCK"))sections.put("STOCK",page(tenant,identity,"STOCK",section==null?null:cursor,limit,"SELECT stock_id,material_id,batch_id,batch_code,warehouse_id,location_id,stock_quantity,reserved_quantity,unit_cost,total_cost,tracking_version,valuation_group_id FROM wm_material_stock WHERE tenant_id=? AND sn_id=? AND del_flag='0' AND stock_quantity>0 AND stock_id>? ORDER BY stock_id LIMIT ?","stock_id"));
        if(section==null || section.equals("MOVEMENTS"))sections.put("MOVEMENTS",page(tenant,identity,"MOVEMENTS",section==null?null:cursor,limit,"SELECT t.tx_id,t.tx_type,t.source_doc_type,t.source_doc_id,t.source_doc_line_id,t.source_doc_code,t.material_stock_id,t.tx_flag,CASE WHEN t.tx_flag=1 THEN 'IN' WHEN t.tx_flag=-1 THEN 'OUT' ELSE 'UNKNOWN' END direction,t.tx_quantity,t.unit_cost,t.tx_amount,t.related_tx_id,t.warehouse_id,t.location_id,t.batch_id,t.batch_code,t.tx_date,t.posting_group_id,t.tracking_version,sh.client_id customer_id,sh.client_code customer_code,sh.client_name customer_name,sh.so_id sales_order_id,sh.so_no sales_order_no FROM wm_material_stock_tx t LEFT JOIN wm_sales_issue sh ON sh.tenant_id=t.tenant_id AND sh.issue_id=t.source_doc_id AND t.source_doc_type='WM_SALES_ISSUE' AND sh.del_flag='0' WHERE t.tenant_id=? AND t.sn_id=? AND t.del_flag='0' AND t.tx_id>? ORDER BY t.tx_id LIMIT ?","tx_id"));
        if(section==null || section.equals("ALIASES"))sections.put("ALIASES",page(tenant,identity,"ALIASES",section==null?null:cursor,limit,"SELECT alias_id,source_type,source_id,alias_code,created_at FROM wm_sn_alias WHERE tenant_id=? AND identity_id=? AND alias_id>? ORDER BY alias_id LIMIT ?","alias_id"));
        List<String> gaps=serials.get(0).get("registration_command")==null?List.of("历史正式SN缺少统一注册原凭据，未推定其早期单件轨迹"):List.of();
        return new IdentityTracePart("ERP",identity,null,(String)serials.get(0).get("sn_code"),"IDENTITY",sections,gaps);
    }
    @Override @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public BatchTracePart batch(String tenant,Long batch,String section,String cursor,int limit){
        context.requireTenant(tenant);validate(batch,limit);
        if(section!=null && !Set.of("SOURCE","STOCK","MOVEMENTS").contains(section))throw new ServiceException("ERP批次追溯范围无效");
        var sources=jdbc.queryForList("SELECT batch_id,batch_code,material_id,material_code,vendor_id,vendor_code,vendor_name,vendor_batch_code,source_doc_type,source_doc_id,source_doc_no,warehouse_id source_warehouse_id,production_date,expiry_date,quantity registered_quantity,received_qty,issued_qty,status FROM wm_batch WHERE tenant_id=? AND batch_id=? AND del_flag='0'",tenant,batch);
        if(sources.size()!=1)throw new ServiceException("当前租户批次不存在或身份冲突");
        var sections=new LinkedHashMap<String,TracePage<Map<String,Object>>>();
        if(section==null || section.equals("SOURCE")){
            long after=TraceCursor.decode(section==null?null:cursor,tenant,batch,"BATCH_SOURCE");
            sections.put("SOURCE",new TracePage<>(after>=batch?List.of():sources.stream().map(TraceReadValues::wire).toList(),null,false));
        }
        if(section==null || section.equals("STOCK"))sections.put("STOCK",page(tenant,batch,"BATCH_STOCK",section==null?null:cursor,limit,"SELECT stock_id,material_id,batch_id,batch_code,sn_id,sn_code,warehouse_id,location_id,stock_quantity,reserved_quantity,unit_cost,total_cost,tracking_version,valuation_group_id FROM wm_material_stock WHERE tenant_id=? AND batch_id=? AND del_flag='0' AND stock_quantity>0 AND stock_id>? ORDER BY stock_id LIMIT ?","stock_id"));
        if(section==null || section.equals("MOVEMENTS"))sections.put("MOVEMENTS",page(tenant,batch,"BATCH_MOVEMENTS",section==null?null:cursor,limit,"SELECT t.tx_id,t.tx_type,t.source_doc_type,t.source_doc_id,t.source_doc_line_id,t.source_doc_code,t.sn_id,t.sn_code,t.material_stock_id,t.tx_flag,CASE WHEN t.tx_flag=1 THEN 'IN' WHEN t.tx_flag=-1 THEN 'OUT' ELSE 'UNKNOWN' END direction,t.tx_quantity,t.unit_cost,t.tx_amount,t.related_tx_id,t.warehouse_id,t.location_id,t.batch_id,t.batch_code,t.tx_date,t.posting_group_id,t.tracking_version,sh.client_id customer_id,sh.client_code customer_code,sh.client_name customer_name,sh.so_id sales_order_id,sh.so_no sales_order_no FROM wm_material_stock_tx t LEFT JOIN wm_sales_issue sh ON sh.tenant_id=t.tenant_id AND sh.issue_id=t.source_doc_id AND t.source_doc_type='WM_SALES_ISSUE' AND sh.del_flag='0' WHERE t.tenant_id=? AND t.batch_id=? AND t.del_flag='0' AND t.tx_id>? ORDER BY t.tx_id LIMIT ?","tx_id"));
        return new BatchTracePart("ERP",batch,"BATCH",sections,List.of("批次主档仓库仅表示来源；当前所在仓库和库位以正数量库存为准","批次事实不推定为逐件SN关联；未知历史方向保留UNKNOWN，不改写原流水"));
    }
    private TracePage<Map<String,Object>> page(String tenant,Long identity,String section,String cursor,int limit,String sql,String key){
        long after=TraceCursor.decode(cursor,tenant,identity,section);var rows=jdbc.queryForList(sql,tenant,identity,after,limit+1);
        boolean more=rows.size()>limit;var result=rows.stream().limit(limit).map(TraceReadValues::wire).toList();
        String next=more?TraceCursor.encode(tenant,identity,section,id(rows.get(limit-1).get(key))):null;return new TracePage<>(result,next,more);
    }
    @Override @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public Set<Long> batchesForIdentities(String tenant,List<Long> identities){
        context.requireTenant(tenant);if(identities==null || identities.isEmpty())return Set.of();
        if(identities.size()>500 || identities.stream().anyMatch(id->id==null || id<=0))throw new ServiceException("批次关联查询参数无效");
        var args=new ArrayList<Object>();args.add(tenant);args.addAll(identities);
        return new HashSet<>(jdbc.queryForList("SELECT DISTINCT batch_id FROM wm_sn WHERE tenant_id=? AND del_flag='0' AND batch_id IS NOT NULL AND sn_id IN ("+String.join(",",Collections.nCopies(identities.size(),"?"))+")",Long.class,args.toArray()));
    }
    @Override @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public TracePage<Long> affectedByBatch(String tenant,Long batch,long after,int limit){
        context.requireTenant(tenant);validate(batch,limit);if(after<0)throw new ServiceException("召回游标无效");
        var rows=jdbc.queryForList("SELECT sn_id FROM wm_sn WHERE tenant_id=? AND batch_id=? AND del_flag='0' AND sn_id>? ORDER BY sn_id LIMIT ?",Long.class,tenant,batch,after,limit+1);
        boolean more=rows.size()>limit;var selected=rows.stream().limit(limit).toList();return new TracePage<>(selected,more?selected.get(selected.size()-1).toString():null,more);
    }
    private static void validate(Long identity,int limit){if(identity==null || identity<=0 || limit<1 || limit>500)throw new ServiceException("身份或分页参数无效");}
}
