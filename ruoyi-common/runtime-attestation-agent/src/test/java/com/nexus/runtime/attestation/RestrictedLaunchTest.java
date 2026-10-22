package com.nexus.runtime.attestation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** JVM 标志只作为必要限制；这些测试不宣称能够枚举所有 transformer。 */
class RestrictedLaunchTest {
    @TempDir Path directory;
    private ReleaseManifest release;
    @BeforeEach void release() throws Exception {
        Files.write(directory.resolve("app.jar"), new byte[0]); Files.write(directory.resolve("witness.jar"), new byte[0]);
        byte[] content = ("NEXUS-RELEASE-1\nrelease " + "a".repeat(40) + "\nmode DIRECT_SYSTEM_JARS\nmain fixture.Main\nagent witness.jar "
            + "b".repeat(64) + "\njar app.jar " + "c".repeat(64) + "\nclass fixture.Main app.jar " + "d".repeat(64) + "\n").getBytes(StandardCharsets.US_ASCII);
        var root = KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); var signer = Signature.getInstance("Ed25519");
        signer.initSign(root.getPrivate()); signer.update(content); release = ReleaseManifest.verify(content, signer.sign(), root.getPublic());
    }
    private List<String> allowed() { return List.of("-XX:+DisableAttachMechanism", "-javaagent:" + directory.resolve("witness.jar") + "=" + directory, "-Xmx128m"); }
    private void check(List<String> args, Map<String, String> environment, String classpath, String command) throws Exception {
        Class<?> type;
        try { type = Class.forName("com.nexus.runtime.attestation.RestrictedLaunch"); }
        catch (ClassNotFoundException missing) { throw new AssertionError("缺少受限启动核验器", missing); }
        try { type.getDeclaredMethod("validate", List.class, Map.class, String.class, String.class, Path.class, ReleaseManifest.class)
            .invoke(null, args, environment, classpath, command, directory, release); }
        catch (InvocationTargetException error) { throw (Exception) error.getCause(); }
    }
    private void rejects(List<String> args, Map<String, String> environment, String classpath, String command) {
        assertEquals("ATTESTATION_LAUNCH_MODE_UNSUPPORTED", assertThrows(Exception.class, () -> check(args, environment, classpath, command)).getMessage());
    }
    @Test void exactlyOneApprovedAgentAndDisabledAttachAreNecessaryButNotProof() throws Exception {
        check(allowed(), Map.of(), directory.resolve("app.jar").toString(), "fixture.Main");
    }
    @Test void additionalJavaOrNativeAgentAndAttachOverridesAreRejected() {
        for (String option : List.of("-javaagent:private-other.jar", "-agentlib:jdwp=server=y", "-agentpath:private.dll", "-XX:-DisableAttachMechanism", "-Djdk.attach.allowAttachSelf=true")) {
            List<String> args = new ArrayList<>(allowed()); args.add(option); rejects(args, Map.of(), directory.resolve("app.jar").toString(), "fixture.Main");
        }
        rejects(allowed().subList(1, 3), Map.of(), directory.resolve("app.jar").toString(), "fixture.Main");
    }
    @Test void environmentAndModuleOrBootstrapOverlaysAreRejected() {
        for (String key : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")) rejects(allowed(), Map.of(key, "private-option"), directory.resolve("app.jar").toString(), "fixture.Main");
        for (String option : List.of("--patch-module=java.base=private", "--add-opens=java.instrument/sun.instrument=ALL-UNNAMED", "-Xbootclasspath/a:private", "-Djava.system.class.loader=fixture.Loader", "-Dspring.devtools.restart.enabled=true")) {
            List<String> args = new ArrayList<>(allowed()); args.add(option); rejects(args, Map.of(), directory.resolve("app.jar").toString(), "fixture.Main");
        }
    }
    @Test void looseExtraOrDuplicateClasspathAndUnapprovedApplicationArgumentsAreRejected() {
        String app = directory.resolve("app.jar").toString();
        for (String classpath :
                List.of(
                        directory.toString(),
                        app + File.pathSeparator + directory,
                        app + File.pathSeparator + app))
            rejects(allowed(), Map.of(), classpath, "fixture.Main");
        rejects(allowed(), Map.of(), app, "fixture.Main --spring.config.location=private");
    }
}
