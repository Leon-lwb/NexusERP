package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/**
 * 历史工单缺少下发策略时保持未知，不使用当前主数据重建旧策略。
 *
 * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
 * @param erpWoId ERP 生产工单主键。
 * @param materialId 当前业务行实际物料标识。
 * @param provenance 策略证据来源，未知来源保持未知。
 * @param policy 来源业务已冻结的追踪策略。
 * @param snapshotVersion 来源快照版本。
 * @param policyHash 冻结策略内容摘要。
 * @param sourceHash 原业务来源快照摘要。
 * @param snapshotHash 冻结策略或业务快照的完整摘要。
 * @param evidenceRef 可回查原凭据的稳定引用。
 * @param frozenAt 来源清单冻结时间。
 * @param effectiveFrom 原档授权的生效时间。
 * @param actorId 本次业务操作的认证人员标识。
 * @param unknownReason 来源证据不足的具体原因。
 */
public record WorkOrderTrackingPolicySnapshot(
        String tenantId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long erpWoId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long materialId,
        Provenance provenance,
        TrackingPolicy policy,
        int snapshotVersion,
        String policyHash,
        String sourceHash,
        String snapshotHash,
        String evidenceRef,
        Instant frozenAt,
        Instant effectiveFrom,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long actorId,
        String unknownReason) {
    /**
     * 工单策略的证据来源分类；未知来源不能使用当前主数据补造。
     */
    public enum Provenance {
        /**
         * 真实库存工单下发时冻结的原策略。
         */
        ERP_RELEASE,
        /**
         * 经独立批准的原档策略采纳结果。
         */
        SOURCE_POLICY_ADOPTION,
        /**
         * 原档证据不足，不能使用现行策略补造。
         */
        UNKNOWN
    }

    public WorkOrderTrackingPolicySnapshot {
        if (tenantId == null
                || !tenantId.matches("[0-9]{6,20}")
                || erpWoId == null
                || erpWoId <= 0
                || materialId == null
                || materialId <= 0
                || provenance == null) {
            throw new IllegalArgumentException("Invalid work-order policy identity");
        }
        if (provenance == Provenance.UNKNOWN) {
            if (policy != null
                    || snapshotVersion != 0
                    || policyHash != null
                    || sourceHash != null
                    || snapshotHash != null
                    || evidenceRef != null
                    || frozenAt != null
                    || effectiveFrom != null
                    || actorId != null
                    || unknownReason == null
                    || unknownReason.isBlank()) {
                throw new IllegalArgumentException(
                        "UNKNOWN cannot contain fabricated policy evidence");
            }
        } else if (policy == null
                || snapshotVersion != 1
                || !hash(policyHash)
                || !hash(sourceHash)
                || !hash(snapshotHash)
                || evidenceRef == null
                || evidenceRef.isBlank()
                || frozenAt == null
                || effectiveFrom == null
                || !effectiveFrom.equals(frozenAt)
                || unknownReason != null
                || provenance == Provenance.SOURCE_POLICY_ADOPTION
                        && (actorId == null || actorId <= 0)) {
            throw new IllegalArgumentException("Incomplete frozen work-order policy evidence");
        }
    }

    private static boolean hash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    public boolean known() {
        return provenance != Provenance.UNKNOWN;
    }

    public boolean originalRelease() {
        return provenance == Provenance.ERP_RELEASE;
    }

    public static WorkOrderTrackingPolicySnapshot unknown(
            String tenantId, Long erpWoId, Long materialId, String reason) {
        return new WorkOrderTrackingPolicySnapshot(
                tenantId,
                erpWoId,
                materialId,
                Provenance.UNKNOWN,
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                reason);
    }
}
