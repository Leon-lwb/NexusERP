package com.nexus.erp.wm.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.*;
import org.springframework.stereotype.Component;
import java.util.List;

/** 独立取消入口；原主 handler 及 MES 冻结状态规则保持各自职责。 */
@Component
public class ErpCompletionCancellationHandler implements TraceEventHandler {
    private final ObjectMapper json;
    private final CompletionCancellationPort cancellation;
    private final List<TraceSourceVerificationPort> verifiers;
    public ErpCompletionCancellationHandler(ObjectMapper json,CompletionCancellationPort cancellation,List<TraceSourceVerificationPort> verifiers){
        this.json=json;this.cancellation=cancellation;this.verifiers=List.copyOf(verifiers);
    }
    @Override public String targetModule(){return "ERP";}
    @Override public boolean supports(String type){return CompletionCancellationRequest.EVENT_TYPE.equals(type);}
    @Override public Object prepare(MaterialTraceEvent event){
        var sources=verifiers.stream().filter(v->"MES".equals(v.sourceModule())).toList();
        if(sources.size()!=1)throw new IllegalStateException("取消缺少唯一 MES 来源实证校验器");
        return CompletionCancellationProof.verify(event,json,sources.get(0));
    }
    @Override public String apply(MaterialTraceEvent event,Object prepared){
        if(!(prepared instanceof CompletionCancellationProof proof) || !event.equals(proof.cancellationEvent()))
            throw new IllegalArgumentException("取消准备凭据不属于当前事件");
        try{return json.writeValueAsString(cancellation.cancel(proof));}
        catch(java.io.IOException e){throw new IllegalStateException("取消应用结果不能冻结",e);}
    }
}
