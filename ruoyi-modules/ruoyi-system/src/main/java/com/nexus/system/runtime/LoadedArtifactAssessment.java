package com.nexus.system.runtime;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

/**
 * 仅用于诊断的来源评估；此处不提供肯定性证明的签发路径。
 * CodeSource/manifest 的存在不能证明实际由
 * JVM 定义的字节。本任务没有独立的构件签名方或构建信任根；即使
 * jar 看似已签名或不可变，也不能通过对当前磁盘上的资源计算哈希
 * 或读取调用方或配置提供的 Git 提交来认证。
 */
public final class LoadedArtifactAssessment {
    private LoadedArtifactAssessment() { }

    public record Observation(String proofStatus, String diagnosticCategory,
                              String supportedProofDomain, String originKind,
                              String manifestObservation) { }

    public static Observation inspect(Class<?> anchor, List<String> arguments, boolean hotReload) {
        if (arguments.stream().anyMatch(value -> value.startsWith("-javaagent:")
            || value.startsWith("-agentlib:") || value.startsWith("-agentpath:"))) {
            return denied("ATTESTATION_AGENTS_UNSUPPORTED", "UNPROVEN", "UNPROVEN");
        }
        ClassLoader loader = anchor.getClassLoader();
        if (hotReload || loader != null && loader.getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("restart")) {
            return denied("ATTESTATION_HOTRELOAD_UNSUPPORTED", "UNPROVEN", "UNPROVEN");
        }
        try {
            URL origin = anchor.getProtectionDomain().getCodeSource().getLocation();
            if (origin == null) return denied("ATTESTATION_BUILD_UNPROVEN", "UNKNOWN", "UNPROVEN");
            if ("file".equals(origin.getProtocol())) {
                Path path = Path.of(origin.toURI());
                if (Files.isDirectory(path)) {
                    return denied("ATTESTATION_LOOSE_CLASSES_UNSUPPORTED", "LOOSE_CLASSES", "UNPROVEN");
                }
                if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar")) {
                    // 仅判断是否存在；绝不读取类字节、计算其哈希或签发证书。
                    try (JarFile jar = new JarFile(path.toFile(), false)) {
                        String presence = jar.getJarEntry("META-INF/nexus/runtime-build-manifest.json") == null
                            ? "ABSENT" : "UNTRUSTED_PRESENT";
                        return denied("ATTESTATION_BUILD_UNPROVEN", "DIRECT_JAR", presence);
                    }
                }
            }
            if ("jar".equals(origin.getProtocol()) || "nested".equals(origin.getProtocol())) {
                return denied("ATTESTATION_BUILD_UNPROVEN", "NESTED_JAR", "UNPROVEN");
            }
        } catch (Exception ignored) {
            // URL、路径、manifest 和库错误文本可能包含凭据；绝不回显。
        }
        return denied("ATTESTATION_BUILD_UNPROVEN", "UNKNOWN", "UNPROVEN");
    }

    private static Observation denied(String category, String origin, String manifest) {
        return new Observation("UNPROVEN", category, "NONE", origin, manifest);
    }
}
