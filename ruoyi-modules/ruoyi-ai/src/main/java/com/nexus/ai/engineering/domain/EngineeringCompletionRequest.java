package com.nexus.ai.engineering.domain;

/** 完成请求只提交租约与有界诊断，任务终态仍由服务端判定。 */
public record EngineeringCompletionRequest(
        int attempt, String leaseToken, long expectedRevision,
        EngineeringConnectionDiagnostic diagnostic) {
    public EngineeringLease lease() {
        return new EngineeringLease(attempt, leaseToken, expectedRevision);
    }

    @Override
    public String toString() {
        return "EngineeringCompletionRequest[租约已隐藏]";
    }
}
