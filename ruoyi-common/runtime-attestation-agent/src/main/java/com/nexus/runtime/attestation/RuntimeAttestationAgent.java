package com.nexus.runtime.attestation;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** 只支持 premain；没有 attach 入口、强制通过开关或测试信任根。 */
public final class RuntimeAttestationAgent {
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static volatile WitnessSession session;
    private static volatile String disabledCategory = "ATTESTATION_AGENT_ABSENT";
    private RuntimeAttestationAgent() { }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        try {
            var root = ExternalTrustRoot.load();
            if (agentArgs == null || agentArgs.isBlank()) throw new AttestationFailure("ATTESTATION_LAUNCH_MODE_UNSUPPORTED");
            Path directory = ProtectedFiles.requireProtected(Path.of(agentArgs));
            Path manifest = ProtectedFiles.requireProtected(directory.resolve("release.manifest"));
            Path signature = ProtectedFiles.requireProtected(directory.resolve("release.sig"));
            byte[] content; byte[] detached;
            try (var input = Files.newInputStream(manifest)) { content = input.readNBytes(ReleaseManifest.MAX_BYTES + 1); }
            try (var input = Files.newInputStream(signature)) { detached = input.readNBytes(65); }
            ReleaseManifest release = ReleaseManifest.verify(content, detached, root);
            ProtectedFiles.requireProtected(directory.resolve(release.agent()));
            for (String name : release.artifacts().keySet()) ProtectedFiles.requireProtected(directory.resolve(name));
            JarInventory.approvedAgent(directory.resolve(release.agent()));
            activate(instrumentation, directory, release);
        } catch (AttestationFailure failure) { disabled(failure.getMessage()); }
        catch (Throwable ignored) { disabled("ATTESTATION_AGENT_STARTUP_FAILED"); }
    }

    // 包内入口只供 premain 的已验签快照使用；隔离测试 premain 不进入生产发布 jar。
    static void activate(Instrumentation instrumentation, Path directory, ReleaseManifest release) {
        if (!STARTED.compareAndSet(false, true)) throw new AttestationFailure("ATTESTATION_DUPLICATE_AGENT_UNSUPPORTED");
        if (instrumentation == null || instrumentation.getClass().getClassLoader() != null
            || instrumentation.getClass().getModule() != Instrumentation.class.getModule()) {
            throw new AttestationFailure("ATTESTATION_INSTRUMENTATION_UNPROVEN");
        }
        int version = Runtime.version().feature();
        if (version != 17 && version != 21) throw new AttestationFailure("ATTESTATION_JDK_UNSUPPORTED");
        RestrictedLaunch.validate(ManagementFactory.getRuntimeMXBean().getInputArguments(), System.getenv(),
            System.getProperty("java.class.path"), System.getProperty("sun.java.command"), directory, release);
        try {
            var hotspot = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
            if (hotspot == null || !"true".equals(hotspot.getVMOption("DisableAttachMechanism").getValue())) throw new AttestationFailure("ATTESTATION_LAUNCH_MODE_UNSUPPORTED");
            ReleaseFiles files = ReleaseFiles.read(directory, release);
            if (RuntimeAttestationAgent.class.getClassLoader() != ClassLoader.getSystemClassLoader()
                || !ReleaseFiles.source(RuntimeAttestationAgent.class.getProtectionDomain().getCodeSource().getLocation()).equals(files.agent())
                || !instrumentation.isRetransformClassesSupported()) throw new AttestationFailure("ATTESTATION_INSTRUMENTATION_UNPROVEN");
            WitnessSession candidate = new WitnessSession(instrumentation, files);
            for (Class<?> type : instrumentation.getAllLoadedClasses()) if (candidate.isApplicationClass(type)) {
                throw new AttestationFailure("ATTESTATION_PREMAIN_TOO_LATE");
            }
            instrumentation.addTransformer(candidate, true); session = candidate;
        } catch (AttestationFailure failure) { throw failure; }
        catch (Throwable ignored) { throw new AttestationFailure("ATTESTATION_AGENT_STARTUP_FAILED"); }
    }
    static void disabled(String category) {
        disabledCategory = category;
        WitnessSession active = session; if (active != null) active.invalidate(category);
    }
    public static WitnessSnapshot snapshot(Class<?> anchor, List<String> requiredClasses) {
        WitnessSession active = session;
        if (active == null) return WitnessSnapshot.absent("ATTESTATION_AGENT_ABSENT".equals(disabledCategory) ? "ABSENT" : "DISABLED", disabledCategory);
        try { return active.snapshot(anchor, requiredClasses); }
        catch (Throwable ignored) { active.invalidate("ATTESTATION_WITNESS_FAILED"); return WitnessSnapshot.absent("INVALIDATED", "ATTESTATION_WITNESS_FAILED"); }
    }
}
