package com.nexus.runtime.attestation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 发布输入摘要与回调摘要共用算法，但二者的证据含义绝不混淆。 */
final class Digests {
    private Digests() { }
    static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException ignored) { throw new AttestationFailure("ATTESTATION_CRYPTO_UNAVAILABLE"); }
    }
    static String of(byte[] bytes) { return HexFormat.of().formatHex(sha256().digest(bytes)); }
    static String of(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) { return of(input, Long.MAX_VALUE); }
    }
    static String of(InputStream input, long limit) throws IOException {
        MessageDigest digest = sha256(); byte[] buffer = new byte[8192]; long size = 0;
        for (int count; (count = input.read(buffer)) != -1;) {
            size += count;
            if (size > limit) throw new AttestationFailure("ATTESTATION_RELEASE_MANIFEST_INVALID");
            digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
