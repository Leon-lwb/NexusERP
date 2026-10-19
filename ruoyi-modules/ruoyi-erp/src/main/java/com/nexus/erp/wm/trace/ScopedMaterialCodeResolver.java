package com.nexus.erp.wm.trace;

import cn.dev33.satoken.stp.StpUtil;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import java.util.*;

/** Authenticated scope intersection and document adapters surround the shared read-only identity kernel. */
@Service @Primary @RequiredArgsConstructor
public class ScopedMaterialCodeResolver implements CodeResolverPort {
    private final ErpCodeResolverService identities;
    private final ErpDocumentCodeResolver documents;
    private final ObjectProvider<SerialVisibilityPort> visibility;
    private final StructuredLabelResolver labels;
    @Override public CodeResolution resolve(String tenant,CodeResolveRequest request,Set<CodeObjectType> allowed){
        if(request!=null && labels.supports(request.rawCode())){
            CodeResolution carrier=request.rawCode().length()<=512?resolvePlain(tenant,request,allowed):null;
            if(carrier!=null && carrier.status()==ResolveStatus.INVALID)return carrier;
            if(carrier!=null && carrier.status()!=ResolveStatus.RESOLVED && carrier.status()!=ResolveStatus.AMBIGUOUS)carrier=null;
            return labels.resolve(request,allowed,carrier,(flat,types)->resolvePlain(tenant,flat,types));
        }
        return resolvePlain(tenant,request,allowed);
    }
    private CodeResolution resolvePlain(String tenant,CodeResolveRequest request,Set<CodeObjectType> allowed){
        if(request==null || request.rawCode()==null || request.rawCode().isBlank() || request.rawCode().length()>512)return CodeResolution.invalid("扫码内容为空或过长");
        if(allowed==null || allowed.isEmpty())return CodeResolution.invalid("当前身份无查询权限");
        Set<CodeObjectType> identityTypes=EnumSet.noneOf(CodeObjectType.class);identityTypes.addAll(allowed);identityTypes.remove(CodeObjectType.DOCUMENT);
        CodeResolution core=identityTypes.isEmpty()?CodeResolution.missing("未找到物料身份"):identities.resolve(tenant,request,identityTypes);
        if(core.status()==ResolveStatus.INVALID)return core;
        var matches=new ArrayList<>(core.candidates());
        if(StpUtil.isLogin() && TraceCodeAccess.mesSerialOnly()){
            var port=visibility.getIfAvailable();Set<Long> ids=new HashSet<>();matches.forEach(candidate->{if(candidate.identityId()!=null)ids.add(candidate.identityId());});
            Set<Long> visible=port==null?Set.of():port.visibleIdentities(tenant,ids);
            matches.removeIf(candidate->candidate.objectType()==CodeObjectType.SN && !visible.contains(candidate.identityId()));
        }
        if(StpUtil.isLogin() && (StpUtil.hasPermission("mes:pro:sn:query") || StpUtil.hasPermission("mes:pro:sn:list") || StpUtil.hasPermission("mes:pro:trace:query"))){
            var port=visibility.getIfAvailable();Set<Long> ids=new HashSet<>();matches.forEach(candidate->{if(candidate.identityId()!=null)ids.add(candidate.identityId());});
            Map<Long,Long> local=port==null || ids.isEmpty()?Map.of():port.localSnIds(tenant,ids);
            matches.replaceAll(candidate->candidate.objectType()==CodeObjectType.SN && local.containsKey(candidate.identityId())
                ?new CodeCandidate(candidate.objectType(),candidate.objectId(),candidate.identityId(),local.get(candidate.identityId()),candidate.canonicalSnCode(),candidate.materialId(),candidate.materialCode(),candidate.batchId(),candidate.batchCode(),candidate.policy(),candidate.documentType(),candidate.documentCode()):candidate);
        }
        if(allowed.contains(CodeObjectType.DOCUMENT)){
            try{matches.addAll(documents.resolve(tenant,request));}
            catch(ErpDocumentCodeResolver.CarrierRejected invalid){return CodeResolution.invalid(invalid.getMessage());}
        }
        var distinct=new LinkedHashMap<String,CodeCandidate>();for(var candidate:matches)distinct.putIfAbsent(candidate.objectType()+":"+candidate.objectId()+":"+candidate.documentType(),candidate);
        if(distinct.isEmpty())return CodeResolution.missing("当前授权范围未找到对应身份");
        if(distinct.size()>1)return CodeResolution.ambiguous(new ArrayList<>(distinct.values()));
        return CodeResolution.resolved(distinct.values().iterator().next());
    }
}
