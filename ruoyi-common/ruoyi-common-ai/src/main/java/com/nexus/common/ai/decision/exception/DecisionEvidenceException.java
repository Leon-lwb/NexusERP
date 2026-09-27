package com.nexus.common.ai.decision.exception;

import java.io.Serial;

/**
 * 仅表达已确认的业务证据缺失，不公开任意错误码、供应商消息或异常原因。
 */
public final class DecisionEvidenceException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;
    private final Reason reason;

    public DecisionEvidenceException(Reason reason) {
        super(requireReason(reason).publicMessage());
        this.reason = reason;
    }
    public Reason reason() { return reason; }
    private static Reason requireReason(Reason reason) {
        if (reason == null) throw new IllegalArgumentException("证据拒绝类型不能为空");
        return reason;
    }

    public enum Reason {
        ROUTING_EVIDENCE_MISSING("缺少生产工艺路线证据，不能发布风险结论。"),
        SCHEDULE_EVIDENCE_MISSING("缺少当前基线排程证据，不能发布风险结论。"),
        SHORTAGE_SIMULATION_INCOMPLETE("缺少工单 MRP 需求证据，不能发布风险结论。"),
        CAPACITY_EVIDENCE_MISSING("缺少可信产能日历证据，不能发布风险结论。");

        private final String publicMessage;
        Reason(String publicMessage) { this.publicMessage = publicMessage; }
        public String code() { return name(); }
        public String publicMessage() { return publicMessage; }
    }
}
