package com.nexus.erp.wm.trace;

import com.fasterxml.jackson.databind.*;
import com.nexus.common.trace.MaterialTraceEvent;
import com.nexus.common.trace.port.TraceEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.*;

/** Actual MES process facts are retained by the ERP inbox. They do not issue warehouse stock again. */
@Component @RequiredArgsConstructor
public class ErpProcessTraceEventHandler implements TraceEventHandler {
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final ErpTraceContext context;
    private static final Set<String> TYPES=Set.of("LOT_ALLOCATION_RECORDED","MATERIAL_CONSUMPTION_RECORDED","SN_CONSUMPTION_ALLOCATED","SN_CONSUMPTION_REVERSED","PRODUCT_LOT_ASSIGNED","SN_COMPLETED","SN_SCRAPPED");
    @Override public String targetModule(){return "ERP";}
    @Override public boolean supports(String type){return TYPES.contains(type);}
    @Override public String apply(MaterialTraceEvent event,Object prepared){
        context.requireWrite(event.tenantId());if(!"MES".equals(event.sourceModule()) || !supports(event.eventType()))throw new IllegalArgumentException("MES工序事实来源无效");
        JsonNode payload;try{payload=json.readTree(event.payload());}catch(Exception e){throw new IllegalArgumentException("MES工序事实载荷无效");}
        if(payload==null || !payload.isObject())throw new IllegalArgumentException("MES工序事实不是对象");
        if("SN_SCRAPPED".equals(event.eventType())){
            long identity;try{identity=new java.math.BigDecimal(payload.path("identityId").asText()).longValueExact();}catch(Exception e){throw new IllegalArgumentException("在制报废缺少正式身份");}
            String reason=payload.path("reason").asText("");if(identity<=0 || reason.isBlank())throw new IllegalArgumentException("在制报废缺少正式身份及原因");
            var rows=jdbc.queryForList("SELECT sn_id,status FROM wm_sn WHERE tenant_id=? AND sn_id=? AND del_flag='0' FOR UPDATE",event.tenantId(),identity);
            if(rows.size()!=1 || !Set.of("REGISTERED","SCRAPPED").contains(rows.get(0).get("status")))throw new IllegalStateException("在制报废身份已进入其他业务状态，需补偿处理");
            Long positive=jdbc.queryForObject("SELECT COUNT(*) FROM wm_material_stock WHERE tenant_id=? AND sn_id=? AND del_flag='0' AND stock_quantity>0",Long.class,event.tenantId(),identity);
            if(positive==null || positive!=0)throw new IllegalStateException("在库实物报废必须由真实ERP报废单处理");
            jdbc.update("UPDATE wm_sn SET status='SCRAPPED',identity_version=identity_version+1 WHERE tenant_id=? AND sn_id=? AND status='REGISTERED'",event.tenantId(),identity);
        }
        try{return json.writeValueAsString(Map.of("sourceEventId",event.eventId(),"status","RECORDED"));}catch(Exception e){throw new IllegalStateException("工序事实回执无法冻结");}
    }
}
