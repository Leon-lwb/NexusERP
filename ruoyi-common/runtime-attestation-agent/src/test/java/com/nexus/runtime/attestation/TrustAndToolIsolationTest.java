package com.nexus.runtime.attestation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

/** 不安装根、不修改系统目录，只确认测试输入无法变成生产信任。 */
class TrustAndToolIsolationTest {
    @TempDir Path directory;
    @Test void currentProcessWritableFilesCannotBecomeImmutableTrustOrPublication() throws Exception {
        Path file = directory.resolve("private-fixture.der"); Files.write(file, new byte[] {1});
        var failure = assertThrows(AttestationFailure.class, () -> ProtectedFiles.requireProtected(file));
        assertEquals("ATTESTATION_IMMUTABLE_FILES_UNPROVEN", failure.getMessage());
        assertFalse(failure.toString().contains(directory.toString())); assertNull(failure.getCause());
    }
    @Test void fixedExternalRootCannotBeRedirectedByAnApplicationProperty() {
        String key = "nexus.runtime.attestation.trustRoot"; String previous = System.getProperty(key);
        try {
            Path expected = ExternalTrustRoot.location(); System.setProperty(key, directory.resolve("private-root.der").toString());
            assertEquals(expected, ExternalTrustRoot.location()); assertFalse(expected.startsWith(directory));
        } finally { if (previous == null) System.clearProperty(key); else System.setProperty(key, previous); }
    }
    @Test void snapshotCopyCannotAcquireAProofDomainOrASetterByClaimingVerified() {
        var claimed =
                new WitnessSnapshot(
                        "VERIFIED",
                        "fixture",
                        "a".repeat(40),
                        "b".repeat(64),
                        "c".repeat(64),
                        Map.of());
        assertEquals("NONE", claimed.supportedProofDomain()); assertEquals("UNPROVEN", claimed.proofStatus()); assertFalse(claimed.finalBytesProven());
        assertThrows(UnsupportedOperationException.class, () -> claimed.classes().put("fixture.Main", null));
        assertTrue(
                Arrays.stream(RuntimeAttestationAgent.class.getMethods())
                        .noneMatch(method -> method.getName().equals("agentmain")));
    }
}
