package com.nexus.mes.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;
import com.nexus.common.trace.jdbc.JdbcTraceJournal;
import com.nexus.common.trace.port.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static com.nexus.mes.trace.MesTraceContext.need;

/** Proxy boundary for the shared journal. No transport loop or cross-datasource writes. */
@Service @DS("mes")
public class MesTraceJournalService implements TraceOutboxPort,TraceInboxPort {
    private final JdbcTraceJournal journal;
    private final MesTraceContext context;
    public MesTraceJournalService(JdbcTemplate jdbc,ObjectMapper json,MesTraceContext context) {
        this.journal=new JdbcTraceJournal(jdbc,json,"MES");this.context=context;
    }
    @Override public String sourceModule(){return "MES";}
    @Override public String targetModule(){return "MES";}
    @Override @Transactional(propagation=Propagation.MANDATORY,rollbackFor=Exception.class)
    public void append(MaterialTraceEvent event){context.requireWrite();context.requireTenant(event.tenantId());journal.append(event);}
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public List<MaterialTraceEvent> claim(String owner,int limit,int leaseSeconds){context.requireConnection();return journal.claim(owner,limit,leaseSeconds);}
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public void brokerAccepted(String eventId,String owner){context.requireConnection();journal.brokerAccepted(eventId,owner);}
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public void retry(String eventId,String owner,String errorCode){context.requireConnection();journal.retry(eventId,owner,errorCode);}
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public void acknowledge(String tenant,String eventId,String hash,String result){context.requireWrite();context.requireTenant(tenant);journal.acknowledge(tenant,eventId,hash,result);}
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true)
    public TraceDeliveryResult findApplied(MaterialTraceEvent event) {
        context.requireTenant(event.tenantId());
        return journal.readApplied(event);
    }
    @Override @Transactional(propagation=Propagation.REQUIRES_NEW,rollbackFor=Exception.class)
    public TraceDeliveryResult accept(MaterialTraceEvent event,TraceEventHandler handler,Object prepared) {
        context.requireWrite();context.requireTenant(event.tenantId());
        need(handler!=null && "MES".equals(handler.targetModule()) && handler.supports(event.eventType()),"MES事件处理器与目标不匹配");
        return journal.accept(event,()->handler.apply(event,prepared),()->handler.apply(event,prepared));
    }
}
