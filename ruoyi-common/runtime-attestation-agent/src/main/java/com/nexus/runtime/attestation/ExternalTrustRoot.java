package com.nexus.runtime.attestation;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

/** 仅读取固定的外部管理员信任路径；没有参数、环境变量或内嵌测试根回退。 */
final class ExternalTrustRoot {
    private ExternalTrustRoot() { }
    static Path location() {
        return File.separatorChar == '\\' ? Path.of("C:/ProgramData/Nexus/RuntimeAttestation/release-root.der")
            : Path.of("/etc/nexus/runtime-attestation/release-root.der");
    }
    static PublicKey load() {
        try {
            Path path = ProtectedFiles.requireProtected(location());
            byte[] encoded;
            try (var input = Files.newInputStream(path)) { encoded = input.readNBytes(129); }
            if (encoded.length == 0 || encoded.length > 128) throw new AttestationFailure("ATTESTATION_TRUST_ROOT_UNAVAILABLE");
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception ignored) { throw new AttestationFailure("ATTESTATION_TRUST_ROOT_UNAVAILABLE"); }
    }
}
