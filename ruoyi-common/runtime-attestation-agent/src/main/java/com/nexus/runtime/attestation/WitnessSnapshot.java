package com.nexus.runtime.attestation;

import java.util.Map;

/** 真正加载回调及成功 Class 身份的有限见证，不是最终字节证书或业务写授权。 */
public record WitnessSnapshot(String state, String diagnosticCategory, String release,
                              String manifestSha256, String signerSha256, String boundaryContract,
                              String requiredClassSetSha256, Map<String, LoadedClassWitness> classes) {
    public WitnessSnapshot { classes = Map.copyOf(classes); }
    public WitnessSnapshot(String state, String diagnosticCategory, String release, String manifestSha256,
                           String signerSha256, Map<String, LoadedClassWitness> classes) {
        this(state, diagnosticCategory, release, manifestSha256, signerSha256, null, null, classes);
    }
    public String supportedProofDomain() { return "NONE"; }
    public String proofStatus() { return "UNPROVEN"; }
    public boolean finalBytesProven() { return false; }
    public String witnessSemantics() { return "PREDEFINE_CALLBACK_AND_LOADED_CLASS_IDENTITY"; }
    public record LoadedClassWitness(String callbackSha256, String manifestClassSha256, String artifactSha256,
                                     String loaderIdentity, String classIdentity) { }
    static WitnessSnapshot absent(String state, String category) { return new WitnessSnapshot(state, category, null, null, null, Map.of()); }
}
