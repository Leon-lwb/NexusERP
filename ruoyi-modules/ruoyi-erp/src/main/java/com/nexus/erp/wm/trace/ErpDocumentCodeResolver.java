package com.nexus.erp.wm.trace;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.trace.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

/** Document tables and permissions are fixed by the server; raw code never selects a table or URL. */
@Service @DS("erp") @RequiredArgsConstructor
public class ErpDocumentCodeResolver {
    private final JdbcTemplate jdbc;private final ErpTraceContext context;
    record Spec(String type,String table,String id,String code,String permission,Set<String> scenes,boolean logicalDelete){}
    private static final List<Spec> SPECS=List.of(
        new Spec("WM_MATERIAL_RECEIVE","wm_material_receive","receive_id","receive_code","wm:receive:query",Set.of("RECEIVE"),true),
        new Spec("WM_PRODUCTION_RECPT","wm_production_recpt","recpt_id","recpt_code","wm:productionrecpt:query",Set.of("RECEIVE","PRODUCTION_RECPT"),true),
        new Spec("WM_PRODUCTION_ISSUE","wm_production_issue","issue_id","issue_code","wm:productionissue:query",Set.of("ISSUE","PRODUCTION_ISSUE"),true),
        new Spec("WM_SALES_ISSUE","wm_sales_issue","issue_id","issue_code","wm:salesIssue:query",Set.of("ISSUE","SALES_ISSUE"),true),
        new Spec("WM_TRANSFER","wm_transfer","transfer_id","transfer_no","wm:transfer:query",Set.of("TRANSFER"),true),
        new Spec("WM_MATERIAL_RETURN","wm_material_return","return_id","return_no","wm:materialReturn:query",Set.of("RETURN"),false));
    static Spec binding(String type){
        if(type==null)return null;
        String canonical="RECEIVE_IN".equals(type)?"WM_MATERIAL_RECEIVE":type;
        return SPECS.stream().filter(spec->spec.type().equals(canonical)).findFirst().orElse(null);
    }
    public static boolean hasDocumentPermission(){return SPECS.stream().anyMatch(spec->StpUtil.hasPermission(spec.permission()));}
    @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public List<CodeCandidate> resolve(String tenant,CodeResolveRequest request){
        context.requireTenant(tenant);var result=new ArrayList<CodeCandidate>();String scene=request.scene()==null?"QUERY":request.scene().toUpperCase(Locale.ROOT);
        if(!StpUtil.isLogin())return result;
        List<Spec> eligible=SPECS.stream().filter(spec->(Set.of("QUERY","TRACE","DOCUMENT").contains(scene) || spec.scenes().contains(scene))
            && (request.sourceDocType()==null || request.sourceDocType().equals(spec.type())) && StpUtil.hasPermission(spec.permission())).toList();
        if(eligible.isEmpty())return result;
        String raw=request.rawCode().trim();
        for(var spec:eligible){
            var args=new ArrayList<Object>();args.add(tenant);args.add(raw);
            String sql="SELECT "+spec.id()+" object_id,"+spec.code()+" document_code FROM "+spec.table()+" WHERE tenant_id=? AND "+spec.code()+"=?"+(spec.logicalDelete()?" AND del_flag='0'":"");
            if(request.sourceDocId()!=null){sql+=" AND "+spec.id()+"=?";args.add(request.sourceDocId());}
            for(var row:bounded(jdbc.queryForList(sql+" LIMIT 101",args.toArray())))result.add(candidate(spec,row.get("object_id"),row.get("document_code")));
        }
        for(var carrier:bounded(jdbc.queryForList("SELECT * FROM wm_barcode WHERE tenant_id=? AND barcode_code=? AND del_flag='0' LIMIT 101",tenant,raw))){
            if(!"DOCUMENT".equals(carrier.get("primary_object_type")))continue;
            Spec spec=binding(string(carrier.get("biz_type")));
            if(spec==null)throw new CarrierRejected("条码登记的业务单据类型无效");
            if(!eligible.contains(spec))continue;
            Object expiry=carrier.get("expire_time");
            if(!"ACTIVE".equals(carrier.get("status")) || expiry instanceof java.util.Date date && date.before(new java.util.Date()))throw new CarrierRejected("原单据条码已停用或过期");
            if(carrier.get("sn_id")!=null || carrier.get("batch_id")!=null || carrier.get("material_id")!=null || carrier.get("stock_id")!=null || carrier.get("biz_line_id")!=null)
                throw new CarrierRejected("单据载体含未经核实的其它主体或行断言");
            if(request.materialId()!=null || request.lineId()!=null)throw new CarrierRejected("单据载体不能替代物料或单据行的实际身份核验");
            Long source=number(carrier.get("biz_id"));if(source==null || source<=0)throw new CarrierRejected("单据载体缺少真实来源ID");
            if(request.sourceDocId()!=null && !request.sourceDocId().equals(source))continue;
            var rows=jdbc.queryForList("SELECT * FROM "+spec.table()+" WHERE tenant_id=? AND "+spec.id()+"=?"+(spec.logicalDelete()?" AND del_flag='0'":""),tenant,source);
            if(rows.size()!=1)throw new CarrierRejected("条码所指单据不存在、已删除或跨租户");
            var row=rows.get(0);
            if(request.supplierId()!=null && !request.supplierId().equals(number(row.get("vendor_id"))) || request.customerId()!=null && !request.customerId().equals(number(row.get("customer_id"))))
                throw new CarrierRejected("单据载体与来源主体断言不一致");
            result.add(candidate(spec,source,row.get(spec.code())));
        }
        return result;
    }
    static final class CarrierRejected extends RuntimeException {CarrierRejected(String message){super(message);}}
    private static List<Map<String,Object>> bounded(List<Map<String,Object>> rows){if(rows.size()>=101)throw new CarrierRejected("单据候选过多，请补充来源类型和单据ID");return rows;}
    private static CodeCandidate candidate(Spec spec,Object id,Object code){return new CodeCandidate(CodeObjectType.DOCUMENT,((Number)id).longValue(),null,null,null,null,null,null,null,null,spec.type(),string(code));}
    private static String string(Object value){return value==null?null:value.toString();}
    private static Long number(Object value){return value instanceof Number n?n.longValue():null;}
}
