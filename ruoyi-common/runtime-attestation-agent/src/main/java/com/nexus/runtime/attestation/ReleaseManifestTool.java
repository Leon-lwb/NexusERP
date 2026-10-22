package com.nexus.runtime.attestation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 显式使用运营方已有私钥签名；不生成密钥，也不安装或自动信任公钥。 */
public final class ReleaseManifestTool {
    private ReleaseManifestTool() { }
    static byte[] create(String release, String main, Path agent, List<Path> applications) {
        if (release == null || !release.matches("[0-9a-f]{40}") || applications == null || applications.isEmpty()) {
            throw new AttestationFailure("ATTESTATION_RELEASE_MANIFEST_INVALID");
        }
        ReleaseManifest.checkBinary(main); ReleaseManifest.checkFile(agent.getFileName().toString()); JarInventory.approvedAgent(agent);
        Map<String, String> artifacts = new TreeMap<>(); Map<String, ReleaseManifest.ClassEntry> classes = new TreeMap<>();
        try {
            String agentHash = Digests.of(agent);
            for (Path path : applications) {
                String name = path.getFileName().toString(); ReleaseManifest.checkFile(name);
                if (name.equals(agent.getFileName().toString()) || artifacts.containsKey(name)) throw new AttestationFailure("ATTESTATION_PACKAGING_UNSUPPORTED");
                String digest = Digests.of(path);
                for (var entry : JarInventory.classes(path).entrySet()) {
                    if (classes.putIfAbsent(entry.getKey(), new ReleaseManifest.ClassEntry(name, entry.getValue())) != null) {
                        throw new AttestationFailure("ATTESTATION_PACKAGING_UNSUPPORTED");
                    }
                }
                if (!digest.equals(Digests.of(path))) throw new AttestationFailure("ATTESTATION_RELEASE_INPUT_CHANGED");
                artifacts.put(name, digest);
            }
            if (!classes.containsKey(main) || !agentHash.equals(Digests.of(agent))) throw new AttestationFailure("ATTESTATION_PACKAGING_UNSUPPORTED");
            StringBuilder content = new StringBuilder("NEXUS-RELEASE-1\nrelease ").append(release)
                .append("\nmode ").append(ReleaseManifest.MODE).append("\nmain ").append(main).append("\nagent ")
                .append(agent.getFileName()).append(' ').append(agentHash).append('\n');
            artifacts.forEach((name, hash) -> content.append("jar ").append(name).append(' ').append(hash).append('\n'));
            classes.forEach((name, entry) -> content.append("class ").append(name).append(' ').append(entry.artifact()).append(' ').append(entry.sha256()).append('\n'));
            byte[] result = content.toString().getBytes(StandardCharsets.US_ASCII);
            if (result.length > ReleaseManifest.MAX_BYTES) throw new AttestationFailure("ATTESTATION_RELEASE_MANIFEST_INVALID");
            return result;
        } catch (AttestationFailure failure) { throw failure; }
        catch (Exception ignored) { throw new AttestationFailure("ATTESTATION_RELEASE_INPUT_UNAVAILABLE"); }
    }

    static byte[] createProcurementV2(String release, String main, Path agent, List<Path> applications) {
        String generic = new String(create(release, main, agent, applications), StandardCharsets.US_ASCII);
        for (String name : ProcurementRuntimeBoundary.REQUIRED_CLASSES) if (!generic.contains("\nclass " + name + " ")) {
            throw new AttestationFailure("ATTESTATION_BOUNDARY_CLASS_SET_INCOMPLETE");
        }
        return ("NEXUS-RELEASE-2\nboundary " + ProcurementRuntimeBoundary.VERSION + " "
            + ProcurementRuntimeBoundary.requiredClassSetSha256() + "\n" + generic.substring("NEXUS-RELEASE-1\n".length())).getBytes(StandardCharsets.US_ASCII);
    }

    public static void main(String[] args) {
        try {
            if (args.length < 7 || !args[0].equals("sign-procurement-v2")) throw new AttestationFailure("ATTESTATION_SIGN_COMMAND_INVALID");
            Path privateKey = Path.of(args[1]);
            if (Files.isSymbolicLink(privateKey) || Files.size(privateKey) > 4096) throw new AttestationFailure("ATTESTATION_SIGN_KEY_UNAVAILABLE");
            byte[] keyBytes = Files.readAllBytes(privateKey);
            PrivateKey key;
            try { key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(keyBytes)); }
            finally { Arrays.fill(keyBytes, (byte) 0); }
            byte[] content = createProcurementV2(args[2], args[3], Path.of(args[4]), Arrays.stream(args).skip(6).map(Path::of).toList());
            Signature signer = Signature.getInstance("Ed25519"); signer.initSign(key); signer.update(content); byte[] signature = signer.sign();
            Path output = Path.of(args[5]).toAbsolutePath().normalize();
            if (!Files.isDirectory(output) || Files.isSymbolicLink(output)) throw new AttestationFailure("ATTESTATION_SIGN_OUTPUT_UNAVAILABLE");
            Files.write(output.resolve("release.manifest"), content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Files.write(output.resolve("release.sig"), signature, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            System.out.println("RELEASE_MANIFEST_SIGNED_NOT_RUNTIME_PROOF");
        } catch (AttestationFailure failure) { System.err.println(failure.getMessage()); System.exit(2); }
        catch (Exception ignored) { System.err.println("ATTESTATION_SIGNING_FAILED"); System.exit(2); }
    }
}
