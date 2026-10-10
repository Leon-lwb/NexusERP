package com.nexus.common.ai.decision.exception;

import java.io.Serial;

/**
 * 仅表达已确认的分析范围或业务证据拒绝，不公开任意错误码、供应商消息或异常原因。
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
        /** 需求、库存、到货单位或有限计算任一未完成时，不承诺完整风险或无风险。 */
        SHORTAGE_SIMULATION_INCOMPLETE("缺料推演证据不完整或计算未完成，不能发布完整风险结论。"),
        CAPACITY_EVIDENCE_MISSING("缺少可信产能日历证据，不能发布风险结论。"),
        /** 仅限排程方案终态过滤后的空工单范围，不把派生出的空产能误报为来源证据缺失。 */
        NO_ANALYZABLE_WORK_ORDER("当前决策范围没有可分析工单，不能发布风险结论。");

        private final String publicMessage;
        Reason(String publicMessage) { this.publicMessage = publicMessage; }
        public String code() { return name(); }
        public String publicMessage() { return publicMessage; }
    }
}
