package com.nexus.runtime.attestation;

/** 只携带固定类别，避免把路径、凭据或原始异常写入报告。 */
final class AttestationFailure extends RuntimeException {
    AttestationFailure(String category) { super(category, null, false, false); }
}
