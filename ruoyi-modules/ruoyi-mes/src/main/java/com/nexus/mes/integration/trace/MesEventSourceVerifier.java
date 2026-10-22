package com.nexus.mes.integration.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.trace.*;
import com.nexus.common.trace.jdbc.JdbcTraceSourceVerifier;
import com.nexus.common.trace.port.TraceSourceVerificationPort;
import com.nexus.mes.trace.MesTraceContext;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/**
 * 核验制造库已提交的原事件头与完整内容，提供目标事务之前的来源实证。
 */
@Service
@DS("mes")
@RequiredArgsConstructor
public class MesEventSourceVerifier implements TraceSourceVerificationPort {
    private final JdbcTemplate jdbc;
    private final MesTraceContext context;

    @Override
    public String sourceModule() {
        return "MES";
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void verify(MaterialTraceEvent event) {
        context.requireTenant(event.tenantId());
        JdbcTraceSourceVerifier.verify(jdbc, "MES", event);
    }
}
