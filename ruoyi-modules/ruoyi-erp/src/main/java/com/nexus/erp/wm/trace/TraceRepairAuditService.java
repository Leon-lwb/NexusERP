package com.nexus.erp.wm.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.TraceHashes;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Online ERP snapshot reports. It never updates identities, stock, original evidence, or signed DHR. */
@Service @DS("erp") @RequiredArgsConstructor
public class TraceRepairAuditService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ErpTraceContext context;
    public record Request(Long materialId,String sourceDocType,Long sourceDocId) { }
    @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public Map<String,Object> audit(Request request) {
        String tenant=context.tenant();context.requireWrite(tenant);
        if(request==null) request=new Request(null,null,null);
        if(request.materialId()!=null && request.materialId()<=0) throw new ServiceException("物料ID无效");
        if(request.sourceDocType()!=null || request.sourceDocId()!=null) throw new ServiceException("在线审计按物料生成；按原单对账请使用离线完整快照工具");
        Long material=request.materialId();
        if(material!=null && jdbc.queryForObject("SELECT COUNT(*) FROM bd_material WHERE tenant_id=? AND material_id=? AND del_flag='0'",Long.class,tenant,material)!=1)
            throw new ServiceException("当前租户物料不存在");
        String scope="s.tenant_id=? AND s.del_flag='0'"+(material==null?"":" AND s.material_id=?");
        Object[] args=material==null?new Object[]{tenant}:new Object[]{tenant,material};
        var findings=new ArrayList<Map<String,Object>>();
        finding(findings,"MISSING_SERIAL",count("SELECT COUNT(*) FROM wm_material_stock s JOIN bd_material m ON m.tenant_id=s.tenant_id AND m.material_id=s.material_id WHERE "+scope+" AND s.stock_quantity>0 AND m.is_serial_manage=1 AND s.sn_id IS NULL",args),"在库追踪物料缺少单件身份，需要实物盘点，不能推算历史SN");
        finding(findings,"INVALID_SERIAL_QUANTITY",count("SELECT COUNT(*) FROM wm_material_stock s WHERE "+scope+" AND s.stock_quantity>0 AND s.sn_id IS NOT NULL AND (s.stock_quantity<>1 OR COALESCE(s.reserved_quantity,0) NOT IN (0,1))",args),"SN正库存数量或预留量不满足单件约束");
        finding(findings,"DUPLICATE_POSITIVE_SERIAL",count("SELECT COUNT(*) FROM (SELECT s.sn_id FROM wm_material_stock s WHERE "+scope+" AND s.stock_quantity>0 AND s.sn_id IS NOT NULL GROUP BY s.sn_id HAVING COUNT(*)>1) conflicts",args),"同一实物存在多条正数量库存，不自动选择或删除");
        finding(findings,"SERIAL_PROJECTION_MISMATCH",count("SELECT COUNT(*) FROM wm_material_stock s LEFT JOIN wm_sn n ON n.tenant_id=s.tenant_id AND n.sn_id=s.sn_id AND n.del_flag='0' WHERE "+scope+" AND s.stock_quantity>0 AND s.sn_id IS NOT NULL AND (n.sn_id IS NULL OR n.material_id<>s.material_id OR n.sn_code<>s.sn_code OR n.stock_id IS NULL OR n.stock_id<>s.stock_id)",args),"正式SN、库存与当前位置投影不一致，须核对来源");
        finding(findings,"MISSING_BATCH",count("SELECT COUNT(*) FROM wm_material_stock s JOIN bd_material m ON m.tenant_id=s.tenant_id AND m.material_id=s.material_id LEFT JOIN wm_batch b ON b.tenant_id=s.tenant_id AND b.batch_id=s.batch_id AND b.del_flag='0' WHERE "+scope+" AND s.stock_quantity>0 AND m.is_batch_manage=1 AND (b.batch_id IS NULL OR b.material_id<>s.material_id OR b.batch_code<>s.batch_code OR s.batch_code IS NULL)",args),"批次身份或物料上下文缺失，不能用同码推定");
        finding(findings,"INVALID_RESERVATION",count("SELECT COUNT(*) FROM wm_material_stock s WHERE "+scope+" AND (COALESCE(s.reserved_quantity,0)<0 OR COALESCE(s.reserved_quantity,0)>COALESCE(s.stock_quantity,0))",args),"库存预留不守恒");
        var totals=jdbc.queryForList("SELECT s.material_id,s.warehouse_id,s.batch_id,s.valuation_group_id,SUM(s.stock_quantity) quantity,SUM(COALESCE(s.reserved_quantity,0)) reservedQuantity,SUM(COALESCE(s.total_cost,0)) amount FROM wm_material_stock s WHERE "+scope+" GROUP BY s.material_id,s.warehouse_id,s.batch_id,s.valuation_group_id ORDER BY s.material_id,s.warehouse_id,s.batch_id,s.valuation_group_id LIMIT 501",args);
        boolean truncated=totals.size()>500;
        var report=new LinkedHashMap<String,Object>();String id="AUDIT_"+UUID.randomUUID().toString().replace("-","");
        report.put("runId",id);report.put("tenantId",tenant);report.put("materialId",material==null?null:material.toString());report.put("status","REVIEW_REQUIRED");report.put("scope","ERP");
        report.put("summary","ERP只读快照："+findings.size()+"类差异；跨库及历史凭据仍须联合对账");
        report.put("items",findings);report.put("totals",totals.stream().limit(500).map(TraceRepairAuditService::wireRow).toList());report.put("truncated",truncated);
        report.put("legacyGaps",List.of("MES组件、质量及已签署DHR未纳入本ERP快照；使用离线工具联合两个来源快照核对", "无单件来源证明的历史保持待核实；本报告不构成已审阅修复授权"));
        String body=encode(report),hash=TraceHashes.sha256(body);
        jdbc.update("INSERT INTO wm_trace_audit_run(audit_id,tenant_id,material_id,status,summary,report_json,content_hash) VALUES(?,?,?,'REVIEW_REQUIRED',?,?,?)",id,tenant,material,report.get("summary"),body,hash);
        return report;
    }
    private static Map<String,Object> wireRow(Map<String,Object> row) {
        var converted=new LinkedHashMap<String,Object>();
        row.forEach((key,value)->converted.put(key,value instanceof java.math.BigDecimal decimal?decimal.toPlainString():value instanceof Long || value instanceof java.math.BigInteger?value.toString():value));
        return converted;
    }
    private void finding(List<Map<String,Object>> rows,String code,long count,String description) {
        if(count>0) rows.add(Map.of("code",code,"count",Long.toString(count),"status","UNVERIFIED","description",description));
    }
    private long count(String sql,Object[] args) {return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args));}
    @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public Map<String,Object> runs(int page,int size,Long material,String status) {
        String tenant=context.tenant();if(page<1 || page>1000 || size<1 || size>100) throw new ServiceException("分页参数无效");
        var args=new ArrayList<Object>();args.add(tenant);String where="tenant_id=?";
        if(material!=null){where+=" AND material_id=?";args.add(material);}
        if(status!=null && !status.isBlank()){if(status.length()>24)throw new ServiceException("状态参数无效");where+=" AND status=?";args.add(status);}
        long total=count("SELECT COUNT(*) FROM wm_trace_audit_run WHERE "+where,args.toArray());
        args.add(size);args.add((page-1)*size);
        var rows=jdbc.queryForList("SELECT audit_id runId,status,summary,created_at createdAt,content_hash manifestDigest FROM wm_trace_audit_run WHERE "+where+" ORDER BY created_at DESC,audit_id DESC LIMIT ? OFFSET ?",args.toArray());
        return Map.of("rows",rows,"total",total);
    }
    @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public Map<String,Object> detail(String id) {
        String tenant=context.tenant();if(id==null || !id.matches("AUDIT_[A-Za-z0-9]{32}")) throw new ServiceException("审计运行号无效");
        var rows=jdbc.queryForList("SELECT report_json,content_hash FROM wm_trace_audit_run WHERE audit_id=? AND tenant_id=?",id,tenant);
        if(rows.size()!=1) throw new ServiceException("当前租户审计记录不存在");
        String body=text(rows.get(0).get("report_json"));
        if(!TraceHashes.sha256(body).equals(rows.get(0).get("content_hash"))) throw new ServiceException("审计报告摘要不一致");
        try{return json.readValue(body,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw new ServiceException("审计报告无法读取");}
    }
    private String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new ServiceException("审计报告无法冻结");}}
    private static String text(Object value){if(value instanceof java.sql.Clob c)try{return c.getSubString(1,Math.toIntExact(c.length()));}catch(Exception e){throw new ServiceException("审计内容不可读");}return value.toString();}
}
