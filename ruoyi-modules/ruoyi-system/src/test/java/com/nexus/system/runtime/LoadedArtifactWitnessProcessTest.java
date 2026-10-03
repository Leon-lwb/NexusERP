package com.nexus.system.runtime;

import com.nexus.runtime.attestation.ProcurementRuntimeBoundary;
import com.nexus.runtime.attestation.RuntimeAttestationAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import static org.junit.jupiter.api.Assertions.*;

/** 真正 premain 到实际 system 评估器的隔离桥接；业务类为临时空类，不是生产验收。 */
class LoadedArtifactWitnessProcessTest {
    @TempDir Path directory;
    private static final String PREMAIN = """
        package com.nexus.runtime.attestation;
        import java.lang.instrument.Instrumentation;
        import java.nio.file.*;
        import java.security.*;
        import java.security.spec.X509EncodedKeySpec;
        public final class IsolatedBridgePremain {
            public static void premain(String args, Instrumentation actual) {
                try {
                    Path directory = Path.of(args);
                    var key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Files.readAllBytes(directory.resolve("fixture-root.der"))));
                    var release = ReleaseManifest.verify(Files.readAllBytes(directory.resolve("release.manifest")), Files.readAllBytes(directory.resolve("release.sig")), key);
                    RuntimeAttestationAgent.activate(actual, directory, release);
                } catch (AttestationFailure failure) { RuntimeAttestationAgent.disabled(failure.getMessage()); }
                catch (Exception ignored) { RuntimeAttestationAgent.disabled("ATTESTATION_FIXTURE_FAILED"); }
            }
        }
        """;
    private static final String MAIN = """
        package fixture;
        import com.nexus.system.runtime.LoadedArtifactAssessment;
        import java.util.List;
        public final class BridgeMain {
            public static void main(String[] args) throws Exception {
                for (String name : LoadedArtifactAssessment.REQUIRED_CLASSES) if (!SKIP_BORN || !name.endsWith("IqcBirthService")) Class.forName(name);
                var assessment = LoadedArtifactAssessment.inspect(BridgeMain.class, List.of(), false);
                System.out.println("category=" + assessment.diagnosticCategory() + ";domain=" + assessment.supportedProofDomain()
                    + ";proof=" + assessment.proofStatus() + ";boundary=" + assessment.boundaryContract()
                    + ";state=" + assessment.witness().state() + ";classes=" + assessment.witness().classes().size());
            }
        }
        """;
    private static String hash(byte[] input) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)); }
    private static void add(Map<String, byte[]> output, Path origin, String prefix) throws Exception {
        if (Files.isDirectory(origin)) {
            try (var paths = Files.walk(origin.resolve(prefix))) {
                for (Path path : paths.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".class")).toList()) {
                    String name = origin.relativize(path).toString().replace('\\', '/'); if (!name.contains("Test")) output.put(name, Files.readAllBytes(path));
                }
            }
        } else {
            try (JarFile jar = new JarFile(origin.toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) { var entry = entries.nextElement(); String name = entry.getName();
                    if (name.startsWith(prefix) && name.endsWith(".class") && !name.contains("Test")) try (var input = jar.getInputStream(entry)) { output.put(name, input.readAllBytes()); }
                }
            }
        }
    }
    private static void jar(Path output, Map<String, byte[]> entries, String premain) throws Exception {
        Manifest manifest = new Manifest(); manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        if (premain != null) { manifest.getMainAttributes().putValue("Premain-Class", premain); manifest.getMainAttributes().putValue("Can-Retransform-Classes", "true"); }
        try (var jar = new JarOutputStream(Files.newOutputStream(output), manifest)) {
            for (var entry : entries.entrySet()) { jar.putNextEntry(new JarEntry(entry.getKey())); jar.write(entry.getValue()); jar.closeEntry(); }
        }
    }
    private String fixture(boolean signedV2, boolean skipBorn) throws Exception {
        Path agentClasses = Path.of(RuntimeAttestationAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path systemClasses = Path.of(LoadedArtifactAssessment.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path compiled = directory.resolve("compiled"); Files.createDirectory(compiled); List<String> sources = new ArrayList<>();
        for (String name : ProcurementRuntimeBoundary.REQUIRED_CLASSES) {
            int separator = name.lastIndexOf('.'); Path source = directory.resolve(name.substring(separator + 1) + ".java");
            Files.writeString(source, "package " + name.substring(0, separator) + "; public final class " + name.substring(separator + 1) + " { }"); sources.add(source.toString());
        }
        Path premain = directory.resolve("IsolatedBridgePremain.java"); Files.writeString(premain, PREMAIN); sources.add(premain.toString());
        Path main = directory.resolve("BridgeMain.java"); Files.writeString(main, MAIN.replace("SKIP_BORN", Boolean.toString(skipBorn))); sources.add(main.toString());
        List<String> compiler = new ArrayList<>(List.of("--release", "17", "-encoding", "UTF-8", "-cp", agentClasses + File.pathSeparator + systemClasses, "-d", compiled.toString()));
        compiler.addAll(sources); assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, compiler.toArray(String[]::new)));
        Map<String, byte[]> agentEntries = new TreeMap<>(); add(agentEntries, agentClasses, "com/nexus/runtime/attestation");
        add(agentEntries, compiled, "com/nexus/runtime/attestation"); Path agent = directory.resolve("witness.jar");
        jar(agent, agentEntries, "com.nexus.runtime.attestation.IsolatedBridgePremain");
        Map<String, byte[]> appEntries = new TreeMap<>();
        try (var paths = Files.walk(compiled)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".class")).toList()) {
                String name = compiled.relativize(path).toString().replace('\\', '/'); if (!name.startsWith("com/nexus/runtime/")) appEntries.put(name, Files.readAllBytes(path));
            }
        }
        Map<String, byte[]> system = new TreeMap<>(); add(system, systemClasses, "com/nexus/system/runtime");
        system.forEach((name, bytes) -> { if (name.startsWith("com/nexus/system/runtime/LoadedArtifactAssessment")) appEntries.put(name, bytes); });
        Path app = directory.resolve("app.jar"); jar(app, appEntries, null);
        StringBuilder content = new StringBuilder(signedV2 ? "NEXUS-RELEASE-2\nboundary " + ProcurementRuntimeBoundary.VERSION + " " + ProcurementRuntimeBoundary.requiredClassSetSha256() + "\n" : "NEXUS-RELEASE-1\n");
        content.append("release ").append("a".repeat(40)).append("\nmode DIRECT_SYSTEM_JARS\nmain fixture.BridgeMain\nagent witness.jar ")
            .append(hash(Files.readAllBytes(agent))).append("\njar app.jar ").append(hash(Files.readAllBytes(app))).append('\n');
        // 清单按二进制类名排序，不能用带 .class 扩展名的 jar 路径排序替代。
        Map<String, String> classDigests = new TreeMap<>();
        for (var entry : appEntries.entrySet()) classDigests.put(entry.getKey().substring(0, entry.getKey().length() - 6).replace('/', '.'), hash(entry.getValue()));
        classDigests.forEach((name, digest) -> content.append("class ").append(name).append(" app.jar ").append(digest).append('\n'));
        var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); var signer = Signature.getInstance("Ed25519");
        byte[] release = content.toString().getBytes(StandardCharsets.US_ASCII); signer.initSign(key.getPrivate()); signer.update(release);
        Files.write(directory.resolve("release.manifest"), release); Files.write(directory.resolve("release.sig"), signer.sign()); Files.write(directory.resolve("fixture-root.der"), key.getPublic().getEncoded());
        ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-XX:+DisableAttachMechanism",
            "-javaagent:" + agent + "=" + directory, "-cp", app.toString(), "fixture.BridgeMain").redirectErrorStream(true);
        for (String name : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")) builder.environment().remove(name);
        Process process = builder.start(); if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("无端口子 JVM 超时"); }
        String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), result); assertTrue(result.contains("domain=NONE"), result); assertTrue(result.contains("proof=UNPROVEN"), result); return result;
    }
    @Test void actualSystemBridgeRequiresSignedV2AndAllDeclaredSuccessfulClassIdentities() throws Exception {
        String result = fixture(true, false); assertTrue(result.contains("category=ATTESTATION_EXCLUSIVE_CHAIN_UNPROVEN"), result);
        assertTrue(result.contains("state=LOAD_IDENTITY_CONFIRMED"), result); assertTrue(result.contains("classes=" + (ProcurementRuntimeBoundary.REQUIRED_CLASSES.size() + 1)), result);
        assertTrue(result.contains("boundary=PROCUREMENT_RECEIPT_IQC_BIRTH_V2"), result);
    }
    @Test void genericOldReleaseCannotCoverBornV2EvenWithMatchingNewClassHashes() throws Exception {
        String result = fixture(false, false); assertTrue(result.contains("category=ATTESTATION_BOUNDARY_CONTRACT_UNSUPPORTED"), result);
    }
    @Test void signedManifestPresenceDoesNotSubstituteForActuallyLoadedBornClasses() throws Exception {
        String result = fixture(true, true); assertTrue(result.contains("category=ATTESTATION_REQUIRED_CLASS_NOT_LOADED"), result);
        assertTrue(result.contains("state=PENDING_DEFINITIONS"), result); assertTrue(result.contains("classes=" + (ProcurementRuntimeBoundary.REQUIRED_CLASSES.size() - 1)), result);
    }
}
