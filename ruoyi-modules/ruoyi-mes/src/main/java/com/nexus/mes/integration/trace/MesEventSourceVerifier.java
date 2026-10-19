package com.nexus.mes.integration.trace;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.TraceSourceVerificationPort;
import com.nexus.common.trace.jdbc.JdbcTraceSourceVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service @DS("mes") @RequiredArgsConstructor
public class MesEventSourceVerifier implements TraceSourceVerificationPort {
    private final JdbcTemplate jdbc;private final com.nexus.mes.trace.MesTraceContext context;
    @Override public String sourceModule(){return "MES";}
    @Override @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    public void verify(MaterialTraceEvent event){context.requireTenant(event.tenantId());JdbcTraceSourceVerifier.verify(jdbc,"MES",event);}
}
