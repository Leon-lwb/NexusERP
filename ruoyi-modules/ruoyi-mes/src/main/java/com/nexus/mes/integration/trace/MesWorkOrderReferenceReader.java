package com.nexus.mes.integration.trace;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.trace.port.ProductionWorkOrderReferencePort;
import com.nexus.mes.trace.MesTraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.Set;
@Service @DS("mes") @RequiredArgsConstructor
public class MesWorkOrderReferenceReader implements ProductionWorkOrderReferencePort {
    private final JdbcTemplate jdbc;private final MesTraceContext context;
    @Override @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public void verify(String tenant,Long mesWo,Long erpWo){
        context.requireTenant(tenant);if(mesWo==null || mesWo<=0 || erpWo==null || erpWo<=0)throw new IllegalArgumentException("生产授权工单身份无效");
        var rows=jdbc.queryForList("SELECT mes_wo_id,erp_wo_id,wo_status FROM pro_work_order WHERE tenant_id=? AND mes_wo_id=? AND erp_wo_id=? AND del_flag='0'",tenant,mesWo,erpWo);
        if(rows.size()!=1 || !Set.of("RELEASED","IN_PROGRESS").contains(rows.get(0).get("wo_status")))throw new IllegalStateException("ERP来源未关联有效的实际MES工单");
    }
}
