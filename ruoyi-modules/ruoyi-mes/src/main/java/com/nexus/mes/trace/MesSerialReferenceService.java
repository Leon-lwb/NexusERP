package com.nexus.mes.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.trace.port.CodeResolverPort;
import com.nexus.common.trace.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.*;
import static com.nexus.mes.trace.MesTraceContext.need;
import com.nexus.mes.pro.domain.ProSn;
import com.nexus.mes.pro.mapper.ProSnMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service @DS("mes") @RequiredArgsConstructor
public class MesSerialReferenceService {
    private final CodeResolverPort codes;
    private final ProSnMapper mapper;
    private final MesTraceContext context;
    public record Resolved(String tenantId,Long identityId,Long localSnId,String canonicalSnCode,String localSnCode,Long materialId,ProSn sn) { }
    /** Called after the local effect, or on its verified replay, in the same MES write transaction. */
    @Transactional(propagation=Propagation.MANDATORY)
    public Map<String,Object> submissionStatus(Resolved reference) {
        need(reference != null,"Submission requires its verified local identity");context.requireTenant(reference.tenantId());
        need(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
            && !org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()
            && "mes".equals(com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder.peek()),"Submission status requires the source MES transaction");
        var current=mapper.selectList(Wrappers.<ProSn>lambdaQuery().eq(ProSn::getTenantId,reference.tenantId())
            .eq(ProSn::getSnId,reference.localSnId()).eq(ProSn::getIdentityId,reference.identityId()).last("FOR UPDATE"));
        need(current != null && current.size()==1,"Submission identity is missing or ambiguous");
        var sn=current.get(0);
        need(reference.tenantId().equals(sn.getTenantId()) && Objects.equals(reference.localSnId(),sn.getSnId())
            && Objects.equals(reference.identityId(),sn.getIdentityId()) && "0".equals(sn.getDelFlag())
            && Objects.equals(reference.sn().getMesWoId(),sn.getMesWoId()) && Objects.equals(reference.sn().getCardId(),sn.getCardId()),"Submission identity, work order or tenant changed");
        Map<String,Object> response=new LinkedHashMap<>();response.put("submissionState","SERVER_COMMITTED");response.put("handoffStatus","NONE");
        var rows=mapper.completionSubmissions(reference.tenantId(),sn.getMesWoId(),sn.getSnId(),sn.getIdentityId());
        need(rows != null,"Completion delivery evidence is unavailable");if(rows.isEmpty())return response;
        var first=new TreeMap<String,Object>(String.CASE_INSENSITIVE_ORDER);first.putAll(rows.get(0));
        if(rows.size()>1) {
            var second=new TreeMap<String,Object>(String.CASE_INSENSITIVE_ORDER);second.putAll(rows.get(1));
            need(!Objects.equals(MesTraceSql.id(first.get("manifest_id")),MesTraceSql.id(second.get("manifest_id"))),"Original completion has duplicate identity or publication evidence");
        }
        Long manifestId=MesTraceSql.id(first.get("manifest_id"));Long revision=MesTraceSql.id(first.get("revision"));
        String hash=MesTraceSql.text(first.get("manifest_hash")),status=MesTraceSql.text(first.get("status"));
        need(MesTraceSql.positive(manifestId) && revision!=null && revision>0 && hash!=null && hash.matches("[0-9a-f]{64}")
            && Set.of("FROZEN","APPLIED","RECEIVED","CANCEL_REQUESTED","CANCELLED").contains(Objects.toString(status,"")),"Original completion metadata is invalid");
        var manifest=new LinkedHashMap<String,Object>();manifest.put("manifestId",manifestId);manifest.put("revision",Math.toIntExact(revision));
        manifest.put("manifestHash",hash);manifest.put("status",status);response.put("manifestRef",manifest);
        String payload=MesTraceSql.text(first.get("source_payload")),sourceHash=MesTraceSql.text(first.get("source_hash"));
        need(first.get("source_event_id")!=null && payload!=null && TraceHashes.sha256(payload).equals(sourceHash),"Completion has no intact owner publication");
        if("CANCELLED".equals(status))return response;
        if(Set.of("FROZEN","CANCEL_REQUESTED").contains(status)) {
            response.put("submissionState","CROSS_MODULE_PENDING");response.put("handoffStatus",status);return response;
        }
        String result=MesTraceSql.text(first.get("source_result"));
        need("APPLIED".equals(MesTraceSql.text(first.get("source_status"))) && result!=null && !result.isBlank(),"Confirmed completion requires its durable owner acknowledgement");
        if("GOOD".equals(MesTraceSql.text(first.get("line_result")))) {
            need(Set.of("RECEIVED","SHIPPED").contains(Objects.toString(sn.getHandoffStatus(),"")),"Good output receipt projection is incomplete");
            response.put("handoffStatus",sn.getHandoffStatus());
        } else {
            need("SCRAP".equals(MesTraceSql.text(first.get("line_result"))),"Completion output classification is invalid");
            response.put("handoffStatus",Objects.toString(sn.getHandoffStatus(),"FROZEN"));
        }
        response.put("submissionState","CONFIRMED");
        manifest.put("outcome",MesTraceSql.qty(first.get("good_qty")).signum()==0?"NO_GOOD_OUTPUT":"POSTED");return response;
    }
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    public Resolved resolve(String rawCode) {
        String tenant=context.tenant();need(rawCode!=null && !rawCode.isBlank(),"SN code is required");
        var resolved=codes.resolve(tenant,new CodeResolveRequest(rawCode.trim(),"MES_SN",null,null,null,null,null,null),Set.of(CodeObjectType.SN));
        need(resolved!=null,"ERP identity resolution is unavailable");if(resolved.status()==ResolveStatus.NOT_FOUND)return null;
        need(resolved.status()==ResolveStatus.RESOLVED && resolved.objectType()==CodeObjectType.SN && resolved.identityId()!=null && resolved.identityId()>0 && resolved.canonicalSnCode()!=null && !resolved.canonicalSnCode().isBlank(),"SN code has no unique verified ERP identity");
        var rows=mapper.selectList(Wrappers.<ProSn>lambdaQuery().eq(ProSn::getTenantId,tenant).eq(ProSn::getDelFlag,"0").eq(ProSn::getIdentityId,resolved.identityId()));
        need(rows!=null && rows.size()<=1,"ERP identity has ambiguous local MES mappings");if(rows.isEmpty())return null;
        var sn=rows.get(0);need(tenant.equals(sn.getTenantId()) && "0".equals(sn.getDelFlag()) && Objects.equals(sn.getIdentityId(),resolved.identityId()) && sn.getSnId()!=null && sn.getSnId()>0 && Objects.equals(sn.getMaterialId(),resolved.materialId()) && sn.getSnCode()!=null && !sn.getSnCode().isBlank(),"Local MES identity, tenant or material differs from the canonical identity");
        need(resolved.localSnId()==null || resolved.localSnId().equals(sn.getSnId()),"Resolved local SN does not match the actual MES identity mapping");
        return new Resolved(tenant,resolved.identityId(),sn.getSnId(),resolved.canonicalSnCode(),sn.getSnCode(),sn.getMaterialId(),sn);
    }
}
