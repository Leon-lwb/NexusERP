package com.nexus.runtime.attestation;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** 只有外部指定根验签成功才可构造；没有 JSON、自签根或已验证标志入口。 */
final class ReleaseManifest {
    static final int MAX_BYTES = 16 * 1024 * 1024;
    static final String MODE = "DIRECT_SYSTEM_JARS";
    private static final String INVALID = "ATTESTATION_RELEASE_MANIFEST_INVALID";
    record ClassEntry(String artifact, String sha256) { }
    private final String boundaryContract;
    private final String requiredClassSetSha256;
    private final String release;
    private final String main;
    private final String agent;
    private final String agentSha256;
    private final String manifestSha256;
    private final String signerSha256;
    private final Map<String, String> artifacts;
    private final Map<String, ClassEntry> classes;

    private ReleaseManifest(String boundaryContract, String requiredClassSetSha256,
                            String release, String main, String agent, String agentSha256,
                            String manifestSha256, String signerSha256,
                            Map<String, String> artifacts, Map<String, ClassEntry> classes) {
        this.boundaryContract = boundaryContract; this.requiredClassSetSha256 = requiredClassSetSha256;
        this.release = release; this.main = main; this.agent = agent; this.agentSha256 = agentSha256;
        this.manifestSha256 = manifestSha256; this.signerSha256 = signerSha256;
        this.artifacts = Collections.unmodifiableMap(new TreeMap<>(artifacts));
        this.classes = Collections.unmodifiableMap(new TreeMap<>(classes));
    }

    static ReleaseManifest verify(byte[] content, byte[] detachedSignature, PublicKey externalRoot) {
        if (content == null || content.length == 0 || content.length > MAX_BYTES) throw new AttestationFailure(INVALID);
        // 在验签和解析期间只使用复制后的同一份字节，防止调用方并发更改输入。
        byte[] frozen = content.clone();
        if (detachedSignature == null || detachedSignature.length != 64 || externalRoot == null) {
            throw new AttestationFailure("ATTESTATION_RELEASE_SIGNATURE_INVALID");
        }
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(externalRoot); verifier.update(frozen);
            if (!verifier.verify(detachedSignature.clone())) throw new AttestationFailure("ATTESTATION_RELEASE_SIGNATURE_INVALID");
        } catch (AttestationFailure failure) { throw failure; }
        catch (Exception ignored) { throw new AttestationFailure("ATTESTATION_RELEASE_SIGNATURE_INVALID"); }
        for (byte value : frozen) if ((value < 32 && value != 10) || value > 126 || value < 0) throw new AttestationFailure(INVALID);
        String text = new String(frozen, StandardCharsets.US_ASCII);
        if (!text.endsWith("\n")) throw new AttestationFailure(INVALID);
        String[] lines = text.substring(0, text.length() - 1).split("\n", -1);
        String boundaryContract = null; String classSetHash = null; int offset = 0;
        if (lines.length >= 8 && "NEXUS-RELEASE-2".equals(lines[0])) {
            String[] boundary = fields(lines[1], "boundary", 3);
            if (!boundary[1].equals(ProcurementRuntimeBoundary.VERSION)
                || !boundary[2].equals(ProcurementRuntimeBoundary.requiredClassSetSha256())) throw new AttestationFailure("ATTESTATION_BOUNDARY_CONTRACT_UNSUPPORTED");
            boundaryContract = boundary[1]; classSetHash = boundary[2]; offset = 1;
        } else if (lines.length < 7 || !"NEXUS-RELEASE-1".equals(lines[0])) throw new AttestationFailure(INVALID);
        String[] release = fields(lines[1 + offset], "release", 2);
        if (!release[1].matches("[0-9a-f]{40}")) throw new AttestationFailure(INVALID);
        if (!lines[2 + offset].equals("mode " + MODE)) throw new AttestationFailure(INVALID);
        String[] main = fields(lines[3 + offset], "main", 2); checkBinary(main[1]);
        String[] agent = fields(lines[4 + offset], "agent", 3); checkFile(agent[1]); checkHash(agent[2]);
        Map<String, String> artifacts = new TreeMap<>(); Map<String, ClassEntry> classes = new TreeMap<>();
        String lastArtifact = ""; String lastClass = ""; boolean inClasses = false;
        for (int index = 5 + offset; index < lines.length; index++) {
            if (lines[index].startsWith("jar ") && !inClasses) {
                String[] item = fields(lines[index], "jar", 3); checkFile(item[1]); checkHash(item[2]);
                if (item[1].compareTo(lastArtifact) <= 0 || item[1].equals(agent[1])) throw new AttestationFailure(INVALID);
                artifacts.put(item[1], item[2]); lastArtifact = item[1];
            } else {
                inClasses = true;
                String[] item = fields(lines[index], "class", 4); checkBinary(item[1]); checkFile(item[2]); checkHash(item[3]);
                if (item[1].compareTo(lastClass) <= 0 || !artifacts.containsKey(item[2])) throw new AttestationFailure(INVALID);
                classes.put(item[1], new ClassEntry(item[2], item[3])); lastClass = item[1];
            }
        }
        if (artifacts.isEmpty() || !classes.containsKey(main[1]) || classes.size() > 200000) throw new AttestationFailure(INVALID);
        if (boundaryContract != null && !classes.keySet().containsAll(ProcurementRuntimeBoundary.REQUIRED_CLASSES)) {
            throw new AttestationFailure("ATTESTATION_BOUNDARY_CLASS_SET_INCOMPLETE");
        }
        return new ReleaseManifest(boundaryContract, classSetHash, release[1], main[1], agent[1], agent[2], Digests.of(frozen),
            Digests.of(externalRoot.getEncoded()), artifacts, classes);
    }

    private static String[] fields(String line, String prefix, int size) {
        String[] parts = line.split(" ", -1);
        if (parts.length != size || !parts[0].equals(prefix)) throw new AttestationFailure(INVALID);
        for (String part : parts) if (part.isEmpty()) throw new AttestationFailure(INVALID);
        return parts;
    }
    static void checkHash(String value) { if (!value.matches("[0-9a-f]{64}")) throw new AttestationFailure(INVALID); }
    static void checkFile(String value) {
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,159}\\.jar") || value.contains("..")) throw new AttestationFailure(INVALID);
    }
    static void checkBinary(String value) {
        if (!value.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")) throw new AttestationFailure(INVALID);
    }
    String boundaryContract() { return boundaryContract; }
    String requiredClassSetSha256() { return requiredClassSetSha256; }
    String release() { return release; }
    String main() { return main; }
    String agent() { return agent; }
    String agentSha256() { return agentSha256; }
    String manifestSha256() { return manifestSha256; }
    String signerSha256() { return signerSha256; }
    Map<String, String> artifacts() { return artifacts; }
    Map<String, ClassEntry> classes() { return classes; }
}
