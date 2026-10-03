package com.nexus.runtime.attestation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

/** 读取发布输入的完整清单；不用于证明 JVM 最终定义的字节。 */
final class JarInventory {
    private static final String UNSUPPORTED = "ATTESTATION_PACKAGING_UNSUPPORTED";
    private JarInventory() { }
    static Map<String, String> classes(Path path) {
        try {
            if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)) throw new AttestationFailure(UNSUPPORTED);
            ReleaseManifest.checkFile(path.getFileName().toString());
            Map<String, String> result = new TreeMap<>(); var names = new HashSet<String>();
            try (JarFile jar = new JarFile(path.toFile(), false)) {
                Attributes attributes = jar.getManifest() == null ? new Attributes() : jar.getManifest().getMainAttributes();
                for (String forbidden : new String[] {"Class-Path", "Launcher-Agent-Class", "Agent-Class", "Premain-Class", "Boot-Class-Path"}) {
                    if (attributes.getValue(forbidden) != null) throw new AttestationFailure(UNSUPPORTED);
                }
                if (attributes.getValue("Multi-Release") != null) throw new AttestationFailure(UNSUPPORTED);
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement(); String name = entry.getName();
                    if (!names.add(name) || name.contains("..") || name.startsWith("/") || name.contains("\\")
                        || name.startsWith("META-INF/versions/") || name.endsWith(".jar")) throw new AttestationFailure(UNSUPPORTED);
                    if (entry.isDirectory() || !name.endsWith(".class")) continue;
                    if (name.equals("module-info.class")) throw new AttestationFailure(UNSUPPORTED);
                    String binary = name.substring(0, name.length() - 6).replace('/', '.'); ReleaseManifest.checkBinary(binary);
                    try (var input = jar.getInputStream(entry)) { result.put(binary, Digests.of(input, ReleaseManifest.MAX_BYTES)); }
                    if (result.size() > 200000) throw new AttestationFailure(UNSUPPORTED);
                }
            }
            return Collections.unmodifiableMap(result);
        } catch (AttestationFailure failure) { throw failure; }
        catch (Exception ignored) { throw new AttestationFailure(UNSUPPORTED); }
    }
    static void approvedAgent(Path path) {
        try (JarFile jar = new JarFile(path.toFile(), false)) {
            if (jar.getManifest() == null) throw new AttestationFailure(UNSUPPORTED);
            Attributes attrs = jar.getManifest().getMainAttributes();
            if (!"com.nexus.runtime.attestation.RuntimeAttestationAgent".equals(attrs.getValue("Premain-Class"))
                || !"true".equals(attrs.getValue("Can-Retransform-Classes"))
                || attrs.getValue("Agent-Class") != null || attrs.getValue("Boot-Class-Path") != null
                || attrs.getValue("Launcher-Agent-Class") != null || attrs.getValue("Class-Path") != null
                || attrs.getValue("Multi-Release") != null || "true".equals(attrs.getValue("Can-Redefine-Classes"))) {
                throw new AttestationFailure(UNSUPPORTED);
            }
        } catch (AttestationFailure failure) { throw failure; }
        catch (Exception ignored) { throw new AttestationFailure(UNSUPPORTED); }
    }
}
