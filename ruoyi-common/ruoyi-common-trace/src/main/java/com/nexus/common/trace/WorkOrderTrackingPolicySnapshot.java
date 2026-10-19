package com.nexus.common.trace;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/** A missing legacy release policy is UNKNOWN, never a reconstruction of current master data. */
public record WorkOrderTrackingPolicySnapshot(
    String tenantId,
    @JsonFormat(shape=JsonFormat.Shape.STRING) Long erpWoId,
    @JsonFormat(shape=JsonFormat.Shape.STRING) Long materialId,
    Provenance provenance,
    TrackingPolicy policy,
    int snapshotVersion,
    String policyHash,
    String sourceHash,
    String snapshotHash,
    String evidenceRef,
    Instant frozenAt,
    Instant effectiveFrom,
    @JsonFormat(shape=JsonFormat.Shape.STRING) Long actorId,
    String unknownReason
) {
    public enum Provenance { ERP_RELEASE, SOURCE_POLICY_ADOPTION, UNKNOWN }
    public WorkOrderTrackingPolicySnapshot {
        if (tenantId==null || !tenantId.matches("[0-9]{6,20}") || erpWoId==null || erpWoId<=0
            || materialId==null || materialId<=0 || provenance==null)
            throw new IllegalArgumentException("Invalid work-order policy identity");
        if (provenance==Provenance.UNKNOWN) {
            if (policy!=null || snapshotVersion!=0 || policyHash!=null || sourceHash!=null || snapshotHash!=null
                || evidenceRef!=null || frozenAt!=null || effectiveFrom!=null || actorId!=null
                || unknownReason==null || unknownReason.isBlank())
                throw new IllegalArgumentException("UNKNOWN cannot contain fabricated policy evidence");
        } else if (policy==null || snapshotVersion!=1 || !hash(policyHash) || !hash(sourceHash) || !hash(snapshotHash)
            || evidenceRef==null || evidenceRef.isBlank() || frozenAt==null || effectiveFrom==null
            || !effectiveFrom.equals(frozenAt) || unknownReason!=null
            || provenance==Provenance.SOURCE_POLICY_ADOPTION && (actorId==null || actorId<=0)) {
            throw new IllegalArgumentException("Incomplete frozen work-order policy evidence");
        }
    }
    private static boolean hash(String value) { return value!=null && value.matches("[0-9a-f]{64}"); }
    public boolean known() { return provenance!=Provenance.UNKNOWN; }
    public boolean originalRelease() { return provenance==Provenance.ERP_RELEASE; }
    public static WorkOrderTrackingPolicySnapshot unknown(String tenantId, Long erpWoId, Long materialId, String reason) {
        return new WorkOrderTrackingPolicySnapshot(tenantId,erpWoId,materialId,Provenance.UNKNOWN,null,0,
            null,null,null,null,null,null,null,reason);
    }
}
