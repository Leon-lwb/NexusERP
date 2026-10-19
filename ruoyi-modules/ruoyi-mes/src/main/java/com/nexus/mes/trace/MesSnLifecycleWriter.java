package com.nexus.mes.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.nexus.mes.trace.MesTraceContext.need;
import static com.nexus.mes.trace.MesTraceSql.*;

/** SN terminal state is a source fact, committed with its event before final card aggregation. */
@Service @DS("mes") @RequiredArgsConstructor
public class MesSnLifecycleWriter {
    private final JdbcTemplate jdbc;
    private final MesTraceContext context;
    private final MesTraceStore events;
    @Transactional(rollbackFor=Exception.class)
    public void complete(Long snId) {
        context.requireWrite();var sn=lock(snId);if("COMPLETED".equals(sn.get("sn_status")))return;
        need("IN_PRODUCTION".equals(sn.get("sn_status")),"SN当前状态不允许完工");
        String tenant=context.tenant();
        need(positive(id(sn.get("mes_wo_id"))) && positive(id(sn.get("card_id"))),"实际生产过站必须具备真实工单和流转卡");
        var last=one(jdbc,"SELECT * FROM pro_flow_card_step WHERE tenant_id=? AND card_id=? AND del_flag='0' ORDER BY sequence_num DESC LIMIT 1 FOR UPDATE",tenant,sn.get("card_id"));
        need(last!=null && Objects.equals(id(last.get("sequence_num")),id(sn.get("current_step_seq"))) && Set.of("IN_PROGRESS","COMPLETED").contains(text(last.get("step_status"))),"SN尚未经过实际末道工序");
        var pass=one(jdbc,"SELECT * FROM mes_station_report WHERE tenant_id=? AND mes_wo_id=? AND card_id=? AND step_record_id=? AND sn_code=? AND report_type='SINGLE' ORDER BY report_id DESC LIMIT 1 FOR UPDATE",tenant,sn.get("mes_wo_id"),sn.get("card_id"),last.get("step_record_id"),sn.get("sn_code"));
        need(pass!=null && "PASS".equals(pass.get("result")) && positive(id(pass.get("operator_id"))) && text(pass.get("request_fingerprint"))!=null,"SN完工必须有实际末道合格过站凭据");
        need(jdbc.update("UPDATE pro_sn SET sn_status='COMPLETED' WHERE tenant_id=? AND sn_id=? AND sn_status='IN_PRODUCTION' AND handoff_status='NONE' AND del_flag='0'",tenant,snId)==1,"SN已变化或已冻结交付");
        var payload=new LinkedHashMap<String,Object>();payload.put("localSnId",snId);payload.put("identityId",sn.get("identity_id"));payload.put("mesWoId",sn.get("mes_wo_id"));payload.put("cardId",sn.get("card_id"));payload.put("reportId",pass.get("report_id"));payload.put("sourceVersion",sn.get("identity_version"));
        publish(snId,"SN_COMPLETED",payload);
    }
    @Transactional(rollbackFor=Exception.class)
    public void scrap(Long snId,String reason) {
        context.requireWrite();need(reason!=null && !reason.isBlank() && reason.length()<=500,"SN报废必须记录原因");var sn=lock(snId);
        if("SCRAPPED".equals(sn.get("sn_status")))return;
        need(Set.of("IN_PRODUCTION","COMPLETED").contains(text(sn.get("sn_status"))) && "NONE".equals(sn.get("handoff_status")),"已冻结清单的SN须走返工/交付纠正，不能改写原SN结果");
        need(jdbc.update("UPDATE pro_sn SET sn_status='SCRAPPED',remark=? WHERE tenant_id=? AND sn_id=? AND handoff_status='NONE' AND sn_status IN ('IN_PRODUCTION','COMPLETED') AND del_flag='0'",reason,context.tenant(),snId)==1,"SN状态并发变化");
        publish(snId,"SN_SCRAPPED",Map.of("localSnId",snId,"identityId",sn.get("identity_id"),"reason",reason,"sourceVersion",sn.get("identity_version")));
    }
    private Map<String,Object> lock(Long snId) {
        String tenant=context.tenant();var hint=one(jdbc,"SELECT mes_wo_id FROM pro_sn WHERE tenant_id=? AND sn_id=? AND del_flag='0'",tenant,snId);need(hint!=null,"SN不存在");
        if(positive(id(hint.get("mes_wo_id")))) {
            var wo=one(jdbc,"SELECT * FROM pro_work_order WHERE tenant_id=? AND mes_wo_id=? AND del_flag='0' FOR UPDATE",tenant,hint.get("mes_wo_id"));
            need(wo!=null,"SN实际工单不存在");
        }
        var sn=one(jdbc,"SELECT * FROM pro_sn WHERE tenant_id=? AND sn_id=? AND del_flag='0' FOR UPDATE",tenant,snId);
        need(sn!=null && positive(id(sn.get("identity_id"))),"SN缺少正式身份，须先审计映射");
        if(positive(id(sn.get("card_id")))) {
            var card=one(jdbc,"SELECT * FROM pro_flow_card WHERE tenant_id=? AND card_id=? AND del_flag='0' FOR UPDATE",tenant,sn.get("card_id"));
            need(card!=null && Objects.equals(id(card.get("mes_wo_id")),id(sn.get("mes_wo_id"))) && Objects.equals(id(card.get("material_id")),id(sn.get("material_id"))),"SN流转卡不属于实际工单/物料");
        }
        return sn;
    }
    private void publish(Long snId,String type,Object payload) {
        // The SN row is held; a legacy fact starts its new publication stream at one.
        var prior=jdbc.queryForList("SELECT aggregate_version FROM mes_trace_outbox WHERE tenant_id=? AND source_module='MES' AND aggregate_type='SN_LIFECYCLE' AND aggregate_id=? ORDER BY aggregate_version DESC LIMIT 1 FOR UPDATE",context.tenant(),snId);
        long version=prior.isEmpty()?1:id(prior.get(0).get("aggregate_version"))+1;
        events.append(type,"SN_LIFECYCLE",snId,version,payload);
    }
}
