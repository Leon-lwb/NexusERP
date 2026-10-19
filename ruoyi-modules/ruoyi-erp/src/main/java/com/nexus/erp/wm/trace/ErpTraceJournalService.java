package com.nexus.erp.wm.trace;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;
import com.nexus.common.trace.jdbc.JdbcTraceJournal;
import com.nexus.common.trace.port.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * ERP事件日志真实数据源代理；业务追加与本次过账处于同一事务。
 */
@Service("erpTraceJournal")
@DS("erp")
@RequiredArgsConstructor
public class ErpTraceJournalService implements TraceOutboxPort, TraceInboxPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ErpTraceContext context;

    private JdbcTraceJournal journal() {
        return new JdbcTraceJournal(jdbc, json, "ERP");
    }

    @Override
    public String sourceModule() {
        return "ERP";
    }

    @Override
    public String targetModule() {
        return "ERP";
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void append(MaterialTraceEvent event) {
        context.requireWrite(event.tenantId());
        journal().append(event);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public List<MaterialTraceEvent> claim(String owner, int limit, int leaseSeconds) {
        context.requireConnection();
        return journal().claim(owner, limit, leaseSeconds);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void brokerAccepted(String eventId, String owner) {
        context.requireConnection();
        journal().brokerAccepted(eventId, owner);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void retry(String eventId, String owner, String errorCode) {
        context.requireConnection();
        journal().retry(eventId, owner, errorCode);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void acknowledge(String tenant, String eventId, String hash, String result) {
        context.requireWrite(tenant);
        journal().acknowledge(tenant, eventId, hash, result);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public TraceDeliveryResult findApplied(MaterialTraceEvent event) {
        context.requireTenant(event.tenantId());
        return journal().readApplied(event);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public TraceDeliveryResult accept(
            MaterialTraceEvent event, TraceEventHandler handler, Object prepared) {
        context.requireWrite(event.tenantId());
        if (!"TRACE_APPLIED".equals(event.eventType())
                && (handler == null
                        || !"ERP".equals(handler.targetModule())
                        || !handler.supports(event.eventType()))) {
            throw new IllegalArgumentException("ERP不支持该事件类型");
        }
        return journal()
                .accept(
                        event,
                        () -> handler.apply(event, prepared),
                        () -> {
                            if (handler != null
                                    && "ERP".equals(handler.targetModule())
                                    && handler.supports("TRACE_APPLIED")) {
                                handler.apply(event, prepared);
                            }
                        });
    }
}
