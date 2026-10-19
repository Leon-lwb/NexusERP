package com.nexus.mes.integration.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.WorkOrderTrackingPolicyPort;
import com.nexus.mes.trace.MesTraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.Objects;

/** Resolves committed ERP release evidence before an operation holds MES business row locks. */
@Service @DS("mes") @RequiredArgsConstructor
public class MesWorkOrderTrackingPolicyReader {
    private final JdbcTemplate jdbc;
    private final MesTraceContext context;
    private final WorkOrderTrackingPolicyPort policies;
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public WorkOrderTrackingPolicySnapshot find(Long mesWoId,Long materialId){
        context.requireNoTransaction();String tenant=context.tenant();
        if(mesWoId==null || mesWoId<=0 || materialId==null || materialId<=0)throw new ServiceException("工单策略查询缺少真实工单或物料");
        var rows=jdbc.queryForList("SELECT erp_wo_id FROM pro_work_order WHERE tenant_id=? AND mes_wo_id=? AND del_flag='0'",tenant,mesWoId);
        if(rows.size()!=1 || !(rows.get(0).get("erp_wo_id") instanceof Number source) || source.longValue()<=0)throw new ServiceException("工单缺少经核实的ERP来源，不能构造追踪策略");
        Long erpWoId=((Number)rows.get(0).get("erp_wo_id")).longValue();
        var frozen=policies.find(tenant,erpWoId,materialId);
        if(frozen==null || !tenant.equals(frozen.tenantId()) || !Objects.equals(erpWoId,frozen.erpWoId()) || !Objects.equals(materialId,frozen.materialId()))throw new ServiceException("工单策略证据与实际来源不一致");
        return frozen;
    }
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public TrackingPolicy requirePolicy(Long mesWoId,Long materialId){
        var frozen=find(mesWoId,materialId);
        if(!frozen.known())throw new ServiceException("历史工单缺少原冻结策略证据，保持待核实；请核实在途业务或采用有来源的新工单，不以当前主数据补造旧策略");
        return frozen.policy();
    }
}
