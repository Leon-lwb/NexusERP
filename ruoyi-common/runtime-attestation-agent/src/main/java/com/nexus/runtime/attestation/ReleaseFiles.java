package com.nexus.runtime.attestation;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** 签名发布输入的冻结映射；磁盘摘要不代表任何已定义 Class。 */
final class ReleaseFiles {
    private final ReleaseManifest release;
    private final Path agent;
    private final Map<String, Path> artifacts;
    private ReleaseFiles(ReleaseManifest release, Path agent, Map<String, Path> artifacts) {
        this.release = release; this.agent = agent; this.artifacts = Collections.unmodifiableMap(new TreeMap<>(artifacts));
    }
    static ReleaseFiles read(Path directory, ReleaseManifest release) {
        try {
            Path base = directory.toAbsolutePath().normalize();
            if (!Files.isDirectory(base) || Files.isSymbolicLink(base) || !base.equals(base.toRealPath())) throw new AttestationFailure("ATTESTATION_PACKAGING_UNSUPPORTED");
            Path agent = regular(base.resolve(release.agent()));
            if (!release.agentSha256().equals(Digests.of(agent))) throw new AttestationFailure("ATTESTATION_RELEASE_INPUT_CHANGED");
            Map<String, Path> artifacts = new TreeMap<>(); Map<String, ReleaseManifest.ClassEntry> actual = new TreeMap<>();
            for (var entry : release.artifacts().entrySet()) {
                Path path = regular(base.resolve(entry.getKey()));
                if (!entry.getValue().equals(Digests.of(path))) throw new AttestationFailure("ATTESTATION_RELEASE_INPUT_CHANGED");
                JarInventory.classes(path).forEach((name, digest) -> {
                    if (actual.putIfAbsent(name, new ReleaseManifest.ClassEntry(entry.getKey(), digest)) != null) throw new AttestationFailure("ATTESTATION_PACKAGING_UNSUPPORTED");
                });
                if (!entry.getValue().equals(Digests.of(path))) throw new AttestationFailure("ATTESTATION_RELEASE_INPUT_CHANGED");
                artifacts.put(entry.getKey(), path);
            }
            if (!actual.equals(release.classes()) || !release.agentSha256().equals(Digests.of(agent))) throw new AttestationFailure("ATTESTATION_RELEASE_INPUT_CHANGED");
            return new ReleaseFiles(release, agent, artifacts);
        } catch (AttestationFailure failure) { throw failure; }
        catch (Exception ignored) { throw new AttestationFailure("ATTESTATION_RELEASE_INPUT_UNAVAILABLE"); }
    }
    private static Path regular(Path path) throws Exception {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path) || !path.equals(path.toRealPath())) throw new AttestationFailure("ATTESTATION_PACKAGING_UNSUPPORTED");
        return path;
    }
    static Path source(URL location) throws Exception {
        if (location == null || !location.getProtocol().equals("file")) throw new AttestationFailure("ATTESTATION_CLASS_SOURCE_UNSUPPORTED");
        // 这里仅绑定 CodeSource 身份，不读取文件、资源或时间戳。
        return Path.of(location.toURI()).toAbsolutePath().normalize();
    }
    ReleaseManifest release() { return release; }
    Path agent() { return agent; }
    Path artifact(String name) { return artifacts.get(name); }
}
