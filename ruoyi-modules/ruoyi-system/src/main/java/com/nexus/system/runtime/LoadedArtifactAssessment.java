package com.nexus.system.runtime;

import com.nexus.runtime.attestation.RuntimeAttestationAgent;
import com.nexus.runtime.attestation.ProcurementRuntimeBoundary;
import com.nexus.runtime.attestation.WitnessSnapshot;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

/**
 * 获取真实 premain 的进程内见证；不接受调用方 JSON、磁盘摘要或配置证明标志。
 * 回调输入与成功 Class 身份对账仍不是绝对最终字节证明，独占链未闭合时只能为 NONE。
 * 无 agent 的既有来源检查仅保留兼容诊断，绝不提升为签名发布或加载证明。
 */
public final class LoadedArtifactAssessment {
    private LoadedArtifactAssessment() { }

    /** 必须覆盖实际采购执行、发消息及消费边界，不能用单一锚类替代。 */
    public static final List<String> REQUIRED_CLASSES = ProcurementRuntimeBoundary.REQUIRED_CLASSES;

    public record Observation(String proofStatus, String diagnosticCategory,
                              String supportedProofDomain, String originKind,
                              String manifestObservation, WitnessSnapshot witness,
                              String boundaryContract, String requiredClassSetSha256) {
        public Observation {
            if (!"UNPROVEN".equals(proofStatus) || !"NONE".equals(supportedProofDomain)
                || !ProcurementRuntimeBoundary.VERSION.equals(boundaryContract)
                || !ProcurementRuntimeBoundary.requiredClassSetSha256().equals(requiredClassSetSha256)) {
                throw new IllegalArgumentException("ATTESTATION_EXCLUSIVE_CHAIN_UNPROVEN");
            }
        }
        public Observation(String proofStatus, String diagnosticCategory, String supportedProofDomain,
                           String originKind, String manifestObservation, WitnessSnapshot witness) {
            this(proofStatus, diagnosticCategory, supportedProofDomain, originKind, manifestObservation, witness,
                ProcurementRuntimeBoundary.VERSION, ProcurementRuntimeBoundary.requiredClassSetSha256());
        }
        public Observation(String proofStatus, String diagnosticCategory, String supportedProofDomain,
                           String originKind, String manifestObservation) {
            this(proofStatus, diagnosticCategory, supportedProofDomain, originKind, manifestObservation, null);
        }
    }

    public static Observation inspect(Class<?> anchor, List<String> arguments, boolean hotReload) {
        WitnessSnapshot witness = RuntimeAttestationAgent.snapshot(anchor, REQUIRED_CLASSES);
        ClassLoader actualLoader = anchor.getClassLoader();
        if (hotReload || actualLoader != null && actualLoader.getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("restart")) {
            return new Observation("UNPROVEN", "ATTESTATION_HOTRELOAD_UNSUPPORTED", "NONE", "UNPROVEN", "UNPROVEN", witness);
        }
        if (!"ABSENT".equals(witness.state())) {
            String category = witness.diagnosticCategory();
            if (witness.manifestSha256() != null && (!ProcurementRuntimeBoundary.VERSION.equals(witness.boundaryContract())
                || !ProcurementRuntimeBoundary.requiredClassSetSha256().equals(witness.requiredClassSetSha256()))) {
                category = "ATTESTATION_BOUNDARY_CONTRACT_UNSUPPORTED";
            }
            return new Observation("UNPROVEN", category, "NONE",
                "LOAD_IDENTITY_CONFIRMED".equals(witness.state()) ? "STANDARD_DIRECT_JAR" : "UNPROVEN",
                witness.manifestSha256() == null ? "UNPROVEN" : "SIGNED_RELEASE_AUTHENTICATED", witness);
        }
        return diagnoseWithoutAgent(anchor, arguments, witness);
    }

    private static Observation diagnoseWithoutAgent(Class<?> anchor, List<String> arguments, WitnessSnapshot witness) {
        if (arguments.stream().anyMatch(value -> value.startsWith("-javaagent:")
            || value.startsWith("-agentlib:") || value.startsWith("-agentpath:"))) {
            return denied(witness, "ATTESTATION_AGENTS_UNSUPPORTED", "UNPROVEN", "UNPROVEN");
        }
        try {
            URL origin = anchor.getProtectionDomain().getCodeSource().getLocation();
            if (origin == null) return denied(witness, "ATTESTATION_BUILD_UNPROVEN", "UNKNOWN", "UNPROVEN");
            if ("file".equals(origin.getProtocol())) {
                Path path = Path.of(origin.toURI());
                if (Files.isDirectory(path)) {
                    return denied(witness, "ATTESTATION_LOOSE_CLASSES_UNSUPPORTED", "LOOSE_CLASSES", "UNPROVEN");
                }
                if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
                    // 仅判断是否存在；绝不读取类字节、计算其哈希或签发证书。
                    try (JarFile jar = new JarFile(path.toFile(), false)) {
                        String presence = jar.getJarEntry("META-INF/nexus/runtime-build-manifest.json") == null
                            ? "ABSENT" : "UNTRUSTED_PRESENT";
                        return denied(witness, "ATTESTATION_BUILD_UNPROVEN", "DIRECT_JAR", presence);
                    }
                }
            }
            if ("jar".equals(origin.getProtocol()) || "nested".equals(origin.getProtocol())) {
                return denied(witness, "ATTESTATION_BUILD_UNPROVEN", "NESTED_JAR", "UNPROVEN");
            }
        } catch (Exception ignored) {
            // URL、路径、manifest 和库错误文本可能包含凭据；绝不回显。
        }
        return denied(witness, "ATTESTATION_BUILD_UNPROVEN", "UNKNOWN", "UNPROVEN");
    }

    private static Observation denied(WitnessSnapshot witness, String category, String origin, String manifest) {
        return new Observation("UNPROVEN", category, "NONE", origin, manifest, witness);
    }
}
