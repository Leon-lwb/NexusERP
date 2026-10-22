package com.nexus.runtime.attestation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

/** 此处校验发布输入，不把 jar 内容当成已加载字节。 */
class ManifestToolTest {
    @TempDir Path directory;
    static Path jar(Path target, Map<String, byte[]> entries, String... attributes) throws Exception {
        Manifest manifest = new Manifest(); manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        for (int index = 0; index < attributes.length; index += 2) manifest.getMainAttributes().putValue(attributes[index], attributes[index + 1]);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(target), manifest)) {
            for (var entry : entries.entrySet()) { out.putNextEntry(new JarEntry(entry.getKey())); out.write(entry.getValue()); out.closeEntry(); }
        }
        return target;
    }
    private byte[] create(Path agent, List<Path> applications) throws Exception {
        Class<?> type;
        try { type = Class.forName("com.nexus.runtime.attestation.ReleaseManifestTool"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("缺少真实发布清单工具", missing); }
        try { return (byte[]) type.getDeclaredMethod("create", String.class, String.class, Path.class, List.class)
            .invoke(null, "a".repeat(40), "fixture.Main", agent, applications); }
        catch (InvocationTargetException error) { throw (Exception) error.getCause(); }
    }
    private Path agent() throws Exception { return jar(directory.resolve("witness.jar"), Map.of(),
        "Premain-Class", "com.nexus.runtime.attestation.RuntimeAttestationAgent", "Can-Retransform-Classes", "true"); }
    @Test void publicationCoversAgentEveryJarAndEveryClassInCanonicalOrder() throws Exception {
        Path app = jar(directory.resolve("app.jar"), Map.of("fixture/Main.class", new byte[] {1, 2, 3}, "fixture/Other.class", new byte[] {4, 5}));
        String content = new String(create(agent(), List.of(app)), StandardCharsets.US_ASCII);
        assertTrue(content.startsWith("NEXUS-RELEASE-1\nrelease " + "a".repeat(40)));
        assertTrue(content.contains("agent witness.jar " + Digests.of(directory.resolve("witness.jar"))));
        assertTrue(content.contains("jar app.jar " + Digests.of(app)));
        assertTrue(content.contains("class fixture.Main app.jar " + Digests.of(new byte[] {1, 2, 3})));
        assertTrue(content.indexOf("class fixture.Main") < content.indexOf("class fixture.Other"));
        assertFalse(content.contains(directory.toString()), "发布清单不包含机器本地路径");
    }
    @Test void multiReleaseImplicitClasspathAndEmbeddedAgentAreNotApprovedPackaging() throws Exception {
        Path agent = agent();
        for (String[] pair : new String[][] {{"Multi-Release", "true"}, {"Class-Path", "private.jar"}, {"Launcher-Agent-Class", "fixture.Other"}}) {
            Path app = jar(directory.resolve("app.jar"), Map.of("fixture/Main.class", new byte[] {1}), pair);
            Exception error = assertThrows(Exception.class, () -> create(agent, List.of(app)));
            assertEquals("ATTESTATION_PACKAGING_UNSUPPORTED", error.getMessage());
        }
    }
    @Test void duplicateClassesAcrossArtifactsCannotSelectAClasspathWinner() throws Exception {
        Path agent = agent(); Path app = jar(directory.resolve("app.jar"), Map.of("fixture/Main.class", new byte[] {1}));
        Path other = jar(directory.resolve("other.jar"), Map.of("fixture/Main.class", new byte[] {2}));
        assertEquals("ATTESTATION_PACKAGING_UNSUPPORTED", assertThrows(Exception.class, () -> create(agent, List.of(app, other))).getMessage());
    }
    @Test void versionedEntriesAndUnsafePathsAreRejected() throws Exception {
        Path agent = agent();
        for (String name : List.of("META-INF/versions/17/fixture/Main.class", "../fixture/Main.class")) {
            Path app = jar(directory.resolve("app.jar"), Map.of(name, new byte[] {1}, "fixture/Main.class", new byte[] {2}));
            assertEquals("ATTESTATION_PACKAGING_UNSUPPORTED", assertThrows(Exception.class, () -> create(agent, List.of(app))).getMessage());
        }
    }
    @Test void procurementPublicationCannotSignAnOldBoundaryMissingNewBornAndReadbackClasses() throws Exception {
        Path app = jar(directory.resolve("app.jar"), Map.of("fixture/Main.class", new byte[] {1})); Path agent = agent();
        Method method;
        try { method = Class.forName("com.nexus.runtime.attestation.ReleaseManifestTool").getDeclaredMethod("createProcurementV2", String.class, String.class, Path.class, List.class); }
        catch (NoSuchMethodException missing) { throw new AssertionError("发布工具必须强制核对 Born V2 类覆盖", missing); }
        var error = assertThrows(InvocationTargetException.class, () -> method.invoke(null, "a".repeat(40), "fixture.Main", agent, List.of(app)));
        assertEquals("ATTESTATION_BOUNDARY_CLASS_SET_INCOMPLETE", error.getCause().getMessage());
    }

    @Test void completeBornV2PublicationHasSignedBoundaryAndCannotDropAnyNewClass() throws Exception {
        Class<?> boundary;
        try { boundary = Class.forName("com.nexus.runtime.attestation.ProcurementRuntimeBoundary"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("缺少签名发布与 system 共享的 V2 类集", missing); }
        @SuppressWarnings("unchecked") List<String> required = (List<String>) boundary.getField("REQUIRED_CLASSES").get(null);
        Map<String, byte[]> entries = new TreeMap<>();
        entries.put("fixture/Main.class", new byte[] {1});
        for (String name : required) entries.put(name.replace('.', '/') + ".class", new byte[] {2});
        Path app = jar(directory.resolve("app.jar"), entries); Path agent = agent();
        var method = Class.forName("com.nexus.runtime.attestation.ReleaseManifestTool").getDeclaredMethod("createProcurementV2", String.class, String.class, Path.class, List.class);
        byte[] content = (byte[]) method.invoke(null, "a".repeat(40), "fixture.Main", agent, List.of(app));
        var root = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var signer = Signature.getInstance("Ed25519");
        signer.initSign(root.getPrivate()); signer.update(content); var release = ReleaseManifest.verify(content, signer.sign(), root.getPublic());
        assertEquals("PROCUREMENT_RECEIPT_IQC_BIRTH_V2", release.getClass().getDeclaredMethod("boundaryContract").invoke(release));
        assertTrue(release.classes().keySet().containsAll(required));
        String old = new String(content, StandardCharsets.US_ASCII).replaceAll("(?m)^class com\\.nexus\\.erp\\.wm\\.service\\.WmReceiptIqcBirthService [^\n]*\n", "");
        byte[] missing = old.getBytes(StandardCharsets.US_ASCII); signer.initSign(root.getPrivate()); signer.update(missing); byte[] signed = signer.sign();
        assertEquals("ATTESTATION_BOUNDARY_CLASS_SET_INCOMPLETE", assertThrows(RuntimeException.class, () -> ReleaseManifest.verify(missing, signed, root.getPublic())).getMessage());
    }

    /** 子进程不继承宿主注入参数，标准输出和标准错误必须独立。 */
    private static ProcessBuilder isolatedSigningProcess(List<String> command) {
        ProcessBuilder process = new ProcessBuilder(command).redirectErrorStream(false);
        for (String name : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")) process.environment().remove(name);
        return process;
    }

    @Test void actualSigningCliUsesOnlyExplicitExistingKeyAndNeverOverwritesOrInstallsTrust() throws Exception {
        Map<String, byte[]> entries = new TreeMap<>();
        entries.put("fixture/Main.class", new byte[] {1});
        for (String name : ProcurementRuntimeBoundary.REQUIRED_CLASSES) entries.put(name.replace('.', '/') + ".class", new byte[] {2});
        Path app = jar(directory.resolve("app.jar"), entries); Path agent = agent();
        var root = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path key = directory.resolve("private-fixture-key.der");
        Files.write(key, root.getPrivate().getEncoded()); Path output = Files.createDirectory(directory.resolve("signed"));
        String core = Path.of(ReleaseManifestTool.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        List<String> command = List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp", core, ReleaseManifestTool.class.getName(),
            "sign-procurement-v2", key.toString(), "a".repeat(40), "fixture.Main", agent.toString(), output.toString(), app.toString());
        // 显式提供无副作用的参数，稳定重现启动器把环境提示写入标准错误的场景。
        ProcessBuilder signing = isolatedSigningProcess(command);
        assertFalse(signing.redirectErrorStream());
        for (String name : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")) assertFalse(signing.environment().containsKey(name));
        signing.environment().put("JDK_JAVA_OPTIONS", "-Dfile.encoding=UTF-8");
        Process child = signing.start();
        try {
            assertTrue(child.waitFor(30, TimeUnit.SECONDS));
            String stdout = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(child.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, child.exitValue());
            assertEquals("RELEASE_MANIFEST_SIGNED_NOT_RUNTIME_PROOF", stdout.strip());
            assertTrue(stderr.contains("NOTE: Picked up JDK_JAVA_OPTIONS:"), "启动器环境提示应留在标准错误流");
            assertFalse(stderr.contains("RELEASE_MANIFEST_SIGNED_NOT_RUNTIME_PROOF"));
        } finally { if (child.isAlive()) child.destroyForcibly(); }
        byte[] content = Files.readAllBytes(output.resolve("release.manifest"));
        byte[] detached = Files.readAllBytes(output.resolve("release.sig"));
        var verified = ReleaseManifest.verify(content, detached, root.getPublic());
        assertEquals(ProcurementRuntimeBoundary.VERSION, verified.boundaryContract());
        Process repeated = isolatedSigningProcess(command).start();
        try {
            assertTrue(repeated.waitFor(30, TimeUnit.SECONDS));
            String stdout = new String(repeated.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(repeated.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(2, repeated.exitValue());
            assertEquals("", stdout.strip(), "重复发布不能再次输出成功标志");
            assertEquals("ATTESTATION_SIGNING_FAILED", stderr.strip());
        } finally { if (repeated.isAlive()) repeated.destroyForcibly(); }
        assertArrayEquals(content, Files.readAllBytes(output.resolve("release.manifest")));
        assertArrayEquals(detached, Files.readAllBytes(output.resolve("release.sig")));
        assertArrayEquals(root.getPrivate().getEncoded(), Files.readAllBytes(key));
        try (var files = Files.list(output)) {
            assertEquals(
                    Set.of("release.manifest", "release.sig"),
                    files.map(path -> path.getFileName().toString()).collect(Collectors.toSet()),
                    "工具只输出清单与分离签名，不安装根或输出私钥");
        }
    }

}
