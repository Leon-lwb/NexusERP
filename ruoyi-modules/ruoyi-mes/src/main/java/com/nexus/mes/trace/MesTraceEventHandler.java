package com.nexus.mes.trace;

import com.nexus.common.trace.MaterialTraceEvent;
import com.nexus.common.trace.port.TraceEventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Set;

/**
 * 目标业务效果在制造接收事务内执行，事件接收与幂等状态由公共日志维护。
 */
@Component
@RequiredArgsConstructor
public class MesTraceEventHandler implements TraceEventHandler {
    private final MesTraceStore business;
    private final MesErpSourceReader sourceEvidence;
    private static final Set<String> TYPES =
            Set.of(
                    "ERP_MATERIAL_SOURCE_POSTED",
                    "ERP_BACKFLUSH_ALLOCATION_AUTHORIZED",
                    "ERP_PRODUCTION_LOT_AUTHORIZED",
                    "ERP_SUBCONTRACT_SOURCE_CONFIRMED",
                    "ERP_SERIAL_RESERVATION_CONFIRMED",
                    "ERP_COMPLETION_RECEIPT_POSTED",
                    "ERP_SHIPMENT_POSTED",
                    "ERP_INVENTORY_POSTED",
                    "COMPLETION_MANIFEST_CANCELLATION_RESULT",
                    "TRACE_APPLIED");

    @Override
    public String targetModule() {
        return "MES";
    }

    @Override
    public boolean supports(String type) {
        return TYPES.contains(type);
    }

    @Override
    public Object prepare(MaterialTraceEvent event) {
        return sourceEvidence.verifyEvent(event);
    }

    @Override
    public String apply(MaterialTraceEvent event, Object prepared) {
        MesTraceContext.need(
                prepared instanceof MesErpSourceReader.VerifiedEvent proof
                        && event.eventId().equals(proof.eventId())
                        && event.tenantId().equals(proof.tenantId())
                        && event.contentHash().equals(proof.contentHash()),
                "来源事件未经过真实ERP凭据核验");
        if ("TRACE_APPLIED".equals(event.eventType())) {
            return business.applyAcknowledgement(event);
        }
        business.applyErpEvent(event);
        var proof = (MesErpSourceReader.VerifiedEvent) prepared;
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("sourceEventId", event.eventId());
        result.put("applied", true);
        result.put("verifiedSource", "ERP_OUTBOX");
        result.put("sourceContentHash", event.contentHash());
        if (proof.policy() != null) {
            result.put("policy", proof.policy());
            result.put("sourceVersion", proof.sourceVersion());
        }
        return business.encode(result);
    }
}
