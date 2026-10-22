package com.nexus.runtime.attestation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.tools.ToolProvider;

/** 真正无端口子 JVM；测试根只进入测试 premain，绝不触碰固定生产信任路径。 */
class PremainIntegrationTest {
    @TempDir Path directory;
    private Path core;
    private Path compiled;
    private Path agent;
    private Path app;
    private static final String ISOLATED = """
        package com.nexus.runtime.attestation;
        import java.lang.instrument.*;
        import java.lang.management.ManagementFactory;
        import java.nio.file.*;
        import java.security.*;
        import java.security.spec.X509EncodedKeySpec;
        public final class IsolatedPremain {
            private static Instrumentation instrument;
            public static void premain(String args, Instrumentation actual) {
                try {
                    Path directory = Path.of(args);
                    PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Files.readAllBytes(directory.resolve("fixture-root.der"))));
                    ReleaseManifest release = ReleaseManifest.verify(Files.readAllBytes(directory.resolve("release.manifest")), Files.readAllBytes(directory.resolve("release.sig")), key);
                    instrument = actual;
                    RuntimeAttestationAgent.activate(actual, directory, release);
                    String mode = Files.readString(directory.resolve("fixture-mode"));
                    if (mode.equals("late-change") || mode.equals("failed-definition")) actual.addTransformer(new ClassFileTransformer() {
                        public byte[] transform(ClassLoader loader, String name, Class<?> redef, java.security.ProtectionDomain domain, byte[] bytes) {
                            if (mode.equals("failed-definition") && name.equals("fixture/Bad")) return new byte[] {0};
                            if (!mode.equals("late-change") || !name.equals("fixture/Anchor")) return null;
                            byte[] changed = bytes.clone(); byte[] from = "ORIGINAL".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                            byte[] to = "MUTATED!".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                            for (int index = 0; index <= changed.length - from.length; index++) {
                                boolean matches = true; for (int offset = 0; offset < from.length; offset++) if (changed[index + offset] != from[offset]) matches = false;
                                if (matches) System.arraycopy(to, 0, changed, index, to.length);
                            }
                            return changed;
                        }
                    }, true);
                } catch (AttestationFailure failure) { RuntimeAttestationAgent.disabled(failure.getMessage()); }
                catch (Exception ignored) { RuntimeAttestationAgent.disabled("ATTESTATION_FIXTURE_FAILED"); }
            }
            public static void retransform(Class<?> type) throws Exception { instrument.retransformClasses(type); }
        }
        """;
    private static final String APPLICATION = """
        package fixture;
        import com.nexus.runtime.attestation.RuntimeAttestationAgent;
        import java.nio.file.*;
        import java.net.*;
        import java.util.List;
        public final class Main {
            public static void main(String[] args) throws Exception {
                String mode = args.length == 0 ? "normal" : args[0];
                Class<?> anchor = Class.forName("fixture.Anchor");
                if (mode.equals("duplicate-loader")) try (var loader = new URLClassLoader(new URL[] {Main.class.getProtectionDomain().getCodeSource().getLocation()}, null)) {
                    Class.forName("fixture.Anchor", false, loader);
                }
                if (mode.equals("retransform")) Class.forName("com.nexus.runtime.attestation.IsolatedPremain").getMethod("retransform", Class.class).invoke(null, anchor);
                boolean failed = false;
                if (mode.equals("failed-definition")) try { Class.forName("fixture.Bad"); } catch (LinkageError expected) { failed = true; }
                var required = mode.equals("failed-definition") ? List.of("fixture.Main", "fixture.Anchor", "fixture.Bad") : List.of("fixture.Main", "fixture.Anchor");
                var snapshot = RuntimeAttestationAgent.snapshot(Main.class, required);
                System.out.println("state=" + snapshot.state() + ";category=" + snapshot.diagnosticCategory() + ";domain=" + snapshot.supportedProofDomain()
                    + ";semantics=" + snapshot.witnessSemantics() + ";classes=" + snapshot.classes().size() + ";behavior=" + anchor.getMethod("value").invoke(null)
                    + ";failed=" + failed + ";badConfirmed=" + snapshot.classes().containsKey("fixture.Bad") + ";final=" + snapshot.finalBytesProven());
            }
        }
        """;
    @BeforeEach void buildSignedIsolatedFixture() throws Exception {
        Class<?> type;
        try { type = Class.forName("com.nexus.runtime.attestation.RuntimeAttestationAgent"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("缺少真实 premain 与加载身份对账实现", missing); }
        core = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        compiled = directory.resolve("compiled"); Files.createDirectory(compiled);
        Path premain = directory.resolve("IsolatedPremain.java"); Files.writeString(premain, ISOLATED);
        Path main = directory.resolve("Main.java"); Files.writeString(main, APPLICATION);
        Path anchor = directory.resolve("Anchor.java"); Files.writeString(anchor, "package fixture; public final class Anchor { public static String value() { return \"ORIGINAL\"; } }");
        Path bad = directory.resolve("Bad.java"); Files.writeString(bad, "package fixture; public final class Bad { }");
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-encoding", "UTF-8", "-cp", core.toString(), "-d", compiled.toString(), premain.toString(), main.toString(), anchor.toString(), bad.toString()));
        agent = directory.resolve("witness.jar"); writeAgent(agent, "com.nexus.runtime.attestation.IsolatedPremain");
        app = directory.resolve("app.jar"); Map<String, byte[]> classes = new TreeMap<>();
        try (var files = Files.walk(compiled.resolve("fixture"))) {
            for (Path file : files.filter(Files::isRegularFile).toList()) classes.put(compiled.relativize(file).toString().replace('\\', '/'), Files.readAllBytes(file));
        }
        ManifestToolTest.jar(app, classes);
        StringBuilder content = new StringBuilder("NEXUS-RELEASE-1\nrelease ").append("a".repeat(40))
            .append("\nmode DIRECT_SYSTEM_JARS\nmain fixture.Main\nagent witness.jar ").append(Digests.of(agent))
            .append("\njar app.jar ").append(Digests.of(app)).append('\n');
        JarInventory.classes(app).forEach((name, digest) -> content.append("class ").append(name).append(" app.jar ").append(digest).append('\n'));
        var root = KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); var signer = Signature.getInstance("Ed25519");
        byte[] manifest = content.toString().getBytes(StandardCharsets.US_ASCII); signer.initSign(root.getPrivate()); signer.update(manifest);
        Files.write(directory.resolve("release.manifest"), manifest); Files.write(directory.resolve("release.sig"), signer.sign());
        Files.write(directory.resolve("fixture-root.der"), root.getPublic().getEncoded());
        Files.writeString(directory.resolve("fixture-mode"), "normal");
    }
    private void writeAgent(Path target, String premain) throws Exception {
        Manifest manifest = new Manifest(); manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Premain-Class", premain); manifest.getMainAttributes().putValue("Can-Retransform-Classes", "true");
        try (var out = new JarOutputStream(Files.newOutputStream(target), manifest)) {
            for (Path source : List.of(core.resolve("com/nexus/runtime/attestation"), compiled.resolve("com/nexus/runtime/attestation"))) {
                Path prefix = source.getParent().getParent().getParent().getParent();
                try (var files = Files.walk(source)) {
                    for (Path file : files.filter(Files::isRegularFile).filter(file -> file.getFileName().toString().endsWith(".class")).toList()) {
                        // 隔离 agent 不包含 JUnit 测试类，也不向应用暴露测试根装载入口。
                        if (file.getFileName().toString().contains("Test") || file.getFileName().toString().equals("ManifestToolTest.class")) continue;
                        out.putNextEntry(new JarEntry(prefix.relativize(file).toString().replace('\\', '/'))); out.write(Files.readAllBytes(file)); out.closeEntry();
                    }
                }
            }
        }
    }
    private String run(String fixtureMode, String applicationMode, String... extraOptions) throws Exception {
        Files.writeString(directory.resolve("fixture-mode"), fixtureMode);
        List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-XX:+DisableAttachMechanism", "-javaagent:" + agent + "=" + directory));
        command.addAll(List.of(extraOptions)); command.addAll(List.of("-cp", app.toString(), "fixture.Main"));
        // 应用模式通过测试 fixture 的已签名 Main 常量选择，不添加生产模式不允许的启动参数。
        if (!applicationMode.equals("normal")) {
            Path main = directory.resolve("Main.java"); Files.writeString(main, APPLICATION.replace("args.length == 0 ? \"normal\" : args[0]", "\"" + applicationMode + "\""));
            assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-encoding", "UTF-8", "-cp", core.toString(), "-d", compiled.toString(), main.toString()));
            Map<String, byte[]> entries = new TreeMap<>();
            for (String name : List.of("Main", "Anchor", "Bad")) entries.put("fixture/" + name + ".class", Files.readAllBytes(compiled.resolve("fixture/" + name + ".class")));
            ManifestToolTest.jar(app, entries);
            var root = KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); var signer = Signature.getInstance("Ed25519");
            StringBuilder content = new StringBuilder("NEXUS-RELEASE-1\nrelease ").append("a".repeat(40)).append("\nmode DIRECT_SYSTEM_JARS\nmain fixture.Main\nagent witness.jar ")
                .append(Digests.of(agent)).append("\njar app.jar ").append(Digests.of(app)).append('\n');
            JarInventory.classes(app).forEach((name, digest) -> content.append("class ").append(name).append(" app.jar ").append(digest).append('\n'));
            byte[] bytes = content.toString().getBytes(StandardCharsets.US_ASCII); signer.initSign(root.getPrivate()); signer.update(bytes);
            Files.write(directory.resolve("release.manifest"), bytes); Files.write(directory.resolve("release.sig"), signer.sign()); Files.write(directory.resolve("fixture-root.der"), root.getPublic().getEncoded());
        }
        ProcessBuilder process = new ProcessBuilder(command).redirectErrorStream(true);
        for (String key : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")) process.environment().remove(key);
        Process child = process.start();
        if (!child.waitFor(30, TimeUnit.SECONDS)) { child.destroyForcibly(); fail("隔离子 JVM 超时"); }
        String result = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, child.exitValue(), result); assertTrue(result.contains("domain=NONE"), result); assertTrue(result.contains("final=false"), result);
        return result;
    }
    @Test void realPremainHashCallbackIsReconciledWithSuccessfullyLoadedClassIdentity() throws Exception {
        String result = run("normal", "normal"); assertTrue(result.contains("state=LOAD_IDENTITY_CONFIRMED"), result);
        assertTrue(result.contains("classes=2"), result); assertTrue(result.contains("category=ATTESTATION_EXCLUSIVE_CHAIN_UNPROVEN"), result);
        assertTrue(result.contains("semantics=PREDEFINE_CALLBACK_AND_LOADED_CLASS_IDENTITY"), result);
    }
    @Test void subsequentTransformerCanChangeBehaviorDespiteMatchingCallbackButNeverAuthorize() throws Exception {
        String result = run("late-change", "normal"); assertTrue(result.contains("behavior=MUTATED!"), result);
        assertTrue(result.contains("state=LOAD_IDENTITY_CONFIRMED"), result); assertTrue(result.contains("category=ATTESTATION_EXCLUSIVE_CHAIN_UNPROVEN"), result);
    }
    @Test void failedDefinitionAfterCallbackIsNeverConfirmed() throws Exception {
        String result = run("failed-definition", "failed-definition"); assertTrue(result.contains("failed=true"), result);
        assertTrue(result.contains("badConfirmed=false"), result); assertTrue(result.contains("category=ATTESTATION_REQUIRED_CLASS_NOT_LOADED"), result);
    }
    @Test void exactLoaderObjectIsRequiredAndUnexpectedLoaderPermanentlyInvalidates() throws Exception {
        String result = run("normal", "duplicate-loader"); assertTrue(result.contains("category=ATTESTATION_CLASS_LOADER_UNSUPPORTED"), result);
        assertTrue(result.contains("state=INVALIDATED"), result);
    }
    @Test void actualRetransformationPermanentlyInvalidatesTheWitness() throws Exception {
        String result = run("normal", "retransform"); assertTrue(result.contains("category=ATTESTATION_REDEFINITION_UNSUPPORTED"), result);
    }
    @Test void jvmOverrideRejectsEvenWithValidSignature() throws Exception {
        String result = run("normal", "normal", "-Dspring.devtools.restart.enabled=true"); assertTrue(result.contains("category=ATTESTATION_LAUNCH_MODE_UNSUPPORTED"), result);
    }
    @Test void modifiedArtifactIsRejectedBeforeAClassWitnessIsIssued() throws Exception {
        Files.write(app, new byte[] {1}, StandardOpenOption.APPEND);
        String result = run("normal", "normal"); assertTrue(result.contains("category=ATTESTATION_RELEASE_INPUT_CHANGED"), result);
    }
    @Test void additionalActualJavaAgentIsRejectedBeforeWitnessActivation() throws Exception {
        Path source = directory.resolve("OtherPremain.java");
        Files.writeString(source, "package fixture; public final class OtherPremain { public static void premain(String args, java.lang.instrument.Instrumentation instrumentation) { } }");
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17", "-d", compiled.toString(), source.toString()));
        Path other = ManifestToolTest.jar(directory.resolve("other-agent.jar"), Map.of("fixture/OtherPremain.class", Files.readAllBytes(compiled.resolve("fixture/OtherPremain.class"))), "Premain-Class", "fixture.OtherPremain");
        String result = run("normal", "normal", "-javaagent:" + other);
        assertTrue(result.contains("state=DISABLED"), result); assertTrue(result.contains("category=ATTESTATION_LAUNCH_MODE_UNSUPPORTED"), result);
    }
    @Test void productionPremainNeverUsesTheIsolatedFixtureRoot() throws Exception {
        Path production = directory.resolve("production-agent.jar"); writeAgent(production, "com.nexus.runtime.attestation.RuntimeAttestationAgent");
        agent = production;
        String result = run("normal", "normal");
        assertFalse(result.contains("state=LOAD_IDENTITY_CONFIRMED"), result);
        assertTrue(result.contains("category=ATTESTATION_TRUST_ROOT_UNAVAILABLE") || result.contains("category=ATTESTATION_RELEASE_INPUT_CHANGED")
            || result.contains("category=ATTESTATION_IMMUTABLE_FILES_UNPROVEN"), result);
    }
}
