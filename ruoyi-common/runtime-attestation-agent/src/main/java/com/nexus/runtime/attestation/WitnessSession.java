package com.nexus.runtime.attestation;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** 回调只记录定义前输入；必须另行对账成功加载的真实 Class 与 loader 对象。 */
final class WitnessSession implements ClassFileTransformer {
    private record Pending(String name, ClassLoader loader, String sha256) { }
    private final Instrumentation instrumentation;
    private final ReleaseFiles files;
    private final ClassLoader loader = ClassLoader.getSystemClassLoader();
    private final String loaderIdentity = UUID.randomUUID().toString();
    private final Object lock = new Object();
    private final Map<String, Pending> pending = new HashMap<>();
    private final IdentityHashMap<Class<?>, String> confirmed = new IdentityHashMap<>();
    private final AtomicReference<String> invalid = new AtomicReference<>();

    WitnessSession(Instrumentation instrumentation, ReleaseFiles files) { this.instrumentation = instrumentation; this.files = files; }
    void invalidate(String category) { invalid.compareAndSet(null, category); }
    boolean isApplicationClass(Class<?> type) { return files.release().classes().containsKey(type.getName()); }

    @Override public byte[] transform(Module module, ClassLoader definingLoader, String internalName,
                                     Class<?> classBeingRedefined, ProtectionDomain domain, byte[] bytes) {
        try {
            if (classBeingRedefined != null) { invalidate("ATTESTATION_REDEFINITION_UNSUPPORTED"); return null; }
            if (invalid.get() != null) return null;
            if (module != null && module.isNamed() && (module.getName().startsWith("java.") || module.getName().startsWith("jdk."))
                && ModuleLayer.boot().findModule(module.getName()).orElse(null) == module) return null;
            if (definingLoader != loader) { invalidate("ATTESTATION_CLASS_LOADER_UNSUPPORTED"); return null; }
            if (domain == null || domain.getCodeSource() == null) { invalidate("ATTESTATION_CLASS_SOURCE_UNSUPPORTED"); return null; }
            var origin = ReleaseFiles.source(domain.getCodeSource().getLocation());
            if (origin.equals(files.agent()) && internalName != null && internalName.startsWith("com/nexus/runtime/attestation/")) return null;
            if (internalName == null || bytes == null) { invalidate("ATTESTATION_DYNAMIC_CLASS_UNSUPPORTED"); return null; }
            String name = internalName.replace('/', '.'); ReleaseManifest.ClassEntry expected = files.release().classes().get(name);
            if (expected == null || !origin.equals(files.artifact(expected.artifact()))) { invalidate("ATTESTATION_CLASS_SOURCE_UNSUPPORTED"); return null; }
            String digest = Digests.of(bytes);
            if (!expected.sha256().equals(digest)) { invalidate("ATTESTATION_CALLBACK_BYTES_MISMATCH"); return null; }
            synchronized (lock) {
                if (pending.putIfAbsent(name, new Pending(name, definingLoader, digest)) != null) invalidate("ATTESTATION_DUPLICATE_DEFINITION_UNSUPPORTED");
            }
        } catch (Throwable ignored) {
            // 官方接口中抛异常不会阻止定义；因此只作粘性失效，不声称拦截了 JVM。
            invalidate("ATTESTATION_WITNESS_FAILED");
        }
        return null;
    }

    WitnessSnapshot snapshot(Class<?> anchor, List<String> requiredClasses) {
        if (invalid.get() != null) return invalidSnapshot();
        if (anchor == null || anchor.getClassLoader() != loader || requiredClasses == null) {
            invalidate("ATTESTATION_CLASS_LOADER_UNSUPPORTED"); return invalidSnapshot();
        }
        // 不调用 Class.forName 或读取资源；枚举只能确认真正定义成功的 Class。
        Class<?>[] loaded = instrumentation.getAllLoadedClasses();
        Map<String, List<Class<?>>> byName = new HashMap<>();
        for (Class<?> type : loaded) if (files.release().classes().containsKey(type.getName())) {
            byName.computeIfAbsent(type.getName(), ignored -> new ArrayList<>()).add(type);
        }
        List<String> required = new ArrayList<>(requiredClasses); if (!required.contains(anchor.getName())) required.add(anchor.getName());
        Map<String, WitnessSnapshot.LoadedClassWitness> result = new TreeMap<>(); boolean missing = false;
        synchronized (lock) {
            for (String name : required) {
                List<Class<?>> matches = byName.get(name); Pending observed = pending.get(name);
                if (matches == null || matches.isEmpty() || observed == null) { missing = true; continue; }
                if (matches.size() != 1) { invalidate("ATTESTATION_DUPLICATE_DEFINITION_UNSUPPORTED"); break; }
                Class<?> actual = matches.get(0); var expected = files.release().classes().get(name);
                try {
                    if (actual.getClassLoader() != observed.loader() || actual.getClassLoader() != loader
                        || name.equals(anchor.getName()) && actual != anchor || actual.getProtectionDomain().getCodeSource() == null
                        || !ReleaseFiles.source(actual.getProtectionDomain().getCodeSource().getLocation()).equals(files.artifact(expected.artifact()))) {
                        invalidate("ATTESTATION_LOADED_CLASS_IDENTITY_MISMATCH"); break;
                    }
                    String identity = confirmed.computeIfAbsent(actual, ignored -> UUID.randomUUID().toString());
                    result.put(name, new WitnessSnapshot.LoadedClassWitness(observed.sha256(), expected.sha256(),
                        files.release().artifacts().get(expected.artifact()), loaderIdentity, identity));
                } catch (Exception ignored) { invalidate("ATTESTATION_LOADED_CLASS_IDENTITY_MISMATCH"); break; }
            }
        }
        if (invalid.get() != null) return invalidSnapshot();
        return new WitnessSnapshot(missing ? "PENDING_DEFINITIONS" : "LOAD_IDENTITY_CONFIRMED",
            missing ? "ATTESTATION_REQUIRED_CLASS_NOT_LOADED" : "ATTESTATION_EXCLUSIVE_CHAIN_UNPROVEN",
            files.release().release(), files.release().manifestSha256(), files.release().signerSha256(),
            files.release().boundaryContract(), files.release().requiredClassSetSha256(), result);
    }
    private WitnessSnapshot invalidSnapshot() {
        return new WitnessSnapshot("INVALIDATED", invalid.get(), files.release().release(), files.release().manifestSha256(), files.release().signerSha256(),
            files.release().boundaryContract(), files.release().requiredClassSetSha256(), Map.of());
    }
}
