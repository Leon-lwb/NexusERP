package com.nexus.runtime.attestation;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 必要启动限制，不把参数清单冒充全局 transformer 或 native 链的证明。 */
final class RestrictedLaunch {
    private static final String UNSUPPORTED = "ATTESTATION_LAUNCH_MODE_UNSUPPORTED";
    private RestrictedLaunch() { }
    static void validate(List<String> arguments, Map<String, String> environment, String classpath,
                         String command, Path directory, ReleaseManifest release) {
        try {
            for (String key : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")) {
                if (environment.get(key) != null && !environment.get(key).isBlank()) throw new AttestationFailure(UNSUPPORTED);
            }
            Path base = directory.toAbsolutePath().normalize(); String approved = "-javaagent:" + base.resolve(release.agent()) + "=" + base;
            boolean disabledAttach = false; int agents = 0;
            for (String argument : arguments) {
                if (argument.equals("-XX:+DisableAttachMechanism")) {
                    if (disabledAttach) throw new AttestationFailure(UNSUPPORTED);
                    disabledAttach = true;
                } else if (argument.equals(approved)) { agents++; }
                else if (!argument.matches("-Xm[sx][1-9][0-9]*[kKmMgG]?")
                    && !argument.equals("-Dspring.profiles.active=dev")) throw new AttestationFailure(UNSUPPORTED);
            }
            if (!disabledAttach || agents != 1 || classpath == null || command == null) throw new AttestationFailure(UNSUPPORTED);
            Set<Path> expected = new HashSet<>(); release.artifacts().keySet().forEach(name -> expected.add(base.resolve(name)));
            Set<Path> actual = new HashSet<>();
            for (String item : classpath.split(Pattern.quote(File.pathSeparator), -1)) {
                if (item.isEmpty() || item.contains("*") || !actual.add(Path.of(item).toAbsolutePath().normalize())) throw new AttestationFailure(UNSUPPORTED);
            }
            if (!expected.equals(actual)) throw new AttestationFailure(UNSUPPORTED);
            Path mainJar = base.resolve(release.classes().get(release.main()).artifact());
            if (!command.equals(release.main()) && !command.equals(mainJar.toString())) throw new AttestationFailure(UNSUPPORTED);
        } catch (AttestationFailure failure) { throw failure; }
        catch (Exception ignored) { throw new AttestationFailure(UNSUPPORTED); }
    }
}
