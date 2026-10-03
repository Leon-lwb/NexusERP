package com.nexus.runtime.attestation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.util.HexFormat;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** 密钥只在隔离测试内生成；绝不安装或自动信任为生产根。 */
class ReleaseManifestTest {
    private KeyPair root;
    @BeforeEach void isolatedRoot() throws Exception { root = KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); }
    private byte[] manifest() {
        return ("NEXUS-RELEASE-1\nrelease " + "a".repeat(40) + "\nmode DIRECT_SYSTEM_JARS\nmain fixture.Main\n"
            + "agent witness.jar " + "b".repeat(64) + "\njar app.jar " + "c".repeat(64)
            + "\nclass fixture.Main app.jar " + "d".repeat(64) + "\n").getBytes(StandardCharsets.US_ASCII);
    }
    private byte[] sign(byte[] content) throws Exception {
        Signature signer = Signature.getInstance("Ed25519"); signer.initSign(root.getPrivate()); signer.update(content); return signer.sign();
    }
    private Object verify(byte[] content, byte[] signature, PublicKey key) throws Exception {
        Class<?> type;
        try { type = Class.forName("com.nexus.runtime.attestation.ReleaseManifest"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("缺少真正的签名发布清单核验器", missing); }
        try { return type.getDeclaredMethod("verify", byte[].class, byte[].class, PublicKey.class).invoke(null, content, signature, key); }
        catch (InvocationTargetException error) { throw (Exception) error.getCause(); }
    }
    private Object property(Object value, String name) throws Exception { return value.getClass().getDeclaredMethod(name).invoke(value); }
    private void rejected(String category, byte[] content, byte[] signature, PublicKey key) {
        Exception error = assertThrows(Exception.class, () -> verify(content, signature, key));
        assertEquals(category, error.getMessage()); assertNull(error.getCause(), "拒绝信息不携带原始异常或路径");
    }
    @Test void acceptsOnlyExactSignedCanonicalManifestAndFreezesItsMaps() throws Exception {
        byte[] content = manifest(); Object release = verify(content, sign(content), root.getPublic());
        assertEquals("a".repeat(40), property(release, "release"));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)), property(release, "manifestSha256"));
        Map<?, ?> entries = (Map<?, ?>) property(release, "classes");
        assertEquals(1, entries.size()); assertThrows(UnsupportedOperationException.class, entries::clear);
        content[0] = 'X'; assertEquals("a".repeat(40), property(release, "release"));
    }
    @Test void wrongExternalRootIsRejected() throws Exception {
        rejected("ATTESTATION_RELEASE_SIGNATURE_INVALID", manifest(), sign(manifest()), KeyPairGenerator.getInstance("Ed25519").generateKeyPair().getPublic());
    }
    @Test void modifiedManifestIsNotAuthenticatedByItsOwnHash() throws Exception {
        byte[] original = manifest(); byte[] changed = original.clone(); changed[changed.length - 2] = 'e';
        rejected("ATTESTATION_RELEASE_SIGNATURE_INVALID", changed, sign(original), root.getPublic());
    }
    @Test void arbitraryJsonAndVerifiedFlagHaveNoManifestMeaning() throws Exception {
        byte[] json = "{\"verified\":true,\"gitCommit\":\"private-fixture\"}".getBytes(StandardCharsets.UTF_8);
        rejected("ATTESTATION_RELEASE_MANIFEST_INVALID", json, sign(json), root.getPublic());
    }
    @Test void traversalAndDuplicateClassAreRejectedEvenIfSigned() throws Exception {
        byte[] traversal = new String(manifest(), StandardCharsets.US_ASCII).replace("app.jar", "../app.jar").getBytes(StandardCharsets.US_ASCII);
        rejected("ATTESTATION_RELEASE_MANIFEST_INVALID", traversal, sign(traversal), root.getPublic());
        byte[] duplicate = (new String(manifest(), StandardCharsets.US_ASCII) + "class fixture.Main app.jar " + "d".repeat(64) + "\n").getBytes(StandardCharsets.US_ASCII);
        rejected("ATTESTATION_RELEASE_MANIFEST_INVALID", duplicate, sign(duplicate), root.getPublic());
    }
    @Test void signedUnknownModeAndUnlistedArtifactAreRejected() throws Exception {
        for (String invalid : new String[] {new String(manifest(), StandardCharsets.US_ASCII).replace("DIRECT_SYSTEM_JARS", "FINAL_DEFINED_BYTES"),
            new String(manifest(), StandardCharsets.US_ASCII).replace("class fixture.Main app.jar", "class fixture.Main hidden.jar")}) {
            byte[] content = invalid.getBytes(StandardCharsets.US_ASCII);
            rejected("ATTESTATION_RELEASE_MANIFEST_INVALID", content, sign(content), root.getPublic());
        }
    }
    @Test void noncanonicalWhitespaceAndLineEndingsCannotShareAnIdentity() throws Exception {
        for (String invalid : new String[] {new String(manifest(), StandardCharsets.US_ASCII).replace("\n", "\r\n"),
            new String(manifest(), StandardCharsets.US_ASCII).replace("main ", "main  ")}) {
            byte[] content = invalid.getBytes(StandardCharsets.US_ASCII);
            rejected("ATTESTATION_RELEASE_MANIFEST_INVALID", content, sign(content), root.getPublic());
        }
    }
    @Test void invalidSignatureLengthAndOversizeInputAreBoundedBeforeParsing() throws Exception {
        rejected("ATTESTATION_RELEASE_SIGNATURE_INVALID", manifest(), new byte[0], root.getPublic());
        rejected("ATTESTATION_RELEASE_MANIFEST_INVALID", new byte[16 * 1024 * 1024 + 1], new byte[64], root.getPublic());
    }
}
