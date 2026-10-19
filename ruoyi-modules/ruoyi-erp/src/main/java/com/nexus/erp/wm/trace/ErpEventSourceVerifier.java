package com.nexus.erp.wm.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.nexus.common.trace.*;
import com.nexus.common.trace.port.TraceSourceVerificationPort;
import com.nexus.common.trace.jdbc.JdbcTraceSourceVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/**
 * 核验库存库已提交的原事件头与完整内容，提供目标事务之前的来源实证。
 */
@Service
@DS("erp")
@RequiredArgsConstructor
public class ErpEventSourceVerifier implements TraceSourceVerificationPort {
    private final JdbcTemplate jdbc;
    private final ErpTraceContext context;

    @Override
    public String sourceModule() {
        return "ERP";
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public void verify(MaterialTraceEvent event) {
        context.requireTenant(event.tenantId());
        JdbcTraceSourceVerifier.verify(jdbc, "ERP", event);
    }
}
