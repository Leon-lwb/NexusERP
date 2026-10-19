package com.nexus.mes.integration.trace;

import com.nexus.common.trace.*;
import com.nexus.common.trace.port.SerialIdentityPort;
import com.nexus.mes.trace.MesTraceContext;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MesIdentityTraceCanonicalTest {
    JdbcTemplate jdbc;
    SerialIdentityPort identities;
    MesIdentityTraceReader reader;

    @BeforeEach
    void setup() {
        jdbc = mock(JdbcTemplate.class);
        identities = mock(SerialIdentityPort.class);
        reader = new MesIdentityTraceReader(jdbc, mock(MesTraceContext.class), identities);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenAnswer(
                        call ->
                                call.<String>getArgument(0).startsWith("SELECT sn_id,sn_code")
                                        ? List.of(
                                                Map.of(
                                                        "sn_id",
                                                        10L,
                                                        "sn_code",
                                                        "MES-OLD",
                                                        "material_id",
                                                        3L,
                                                        "mes_wo_id",
                                                        4L,
                                                        "identity_version",
                                                        1L))
                                        : List.of());
    }

    @Test
    void mappedLegacyLocalCodeNeverMasqueradesAsTheCanonicalErpCode() {
        when(identities.find("000001", 100L))
                .thenReturn(new SerialIdentity(100L, "FORMAL-100", 3L, null, null));
        var result = reader.identity("000001", 100L, "TEST", null, 500);
        assertEquals("FORMAL-100", result.canonicalSnCode());
        assertEquals(10L, result.localSnId());
        assertEquals("MES-OLD", result.localSnCode());
    }

    @Test
    void aMissingCanonicalRegistrationRemainsAnExplicitEvidenceGap() {
        var result = reader.identity("000001", 100L, "TEST", null, 500);
        assertNull(result.canonicalSnCode());
        assertTrue(result.evidenceGaps().stream().anyMatch(gap -> gap.contains("ERP")));
    }

    @Test
    void aMappedIdentityCannotChangeMaterialThroughALocalCodeAlias() {
        when(identities.find("000001", 100L))
                .thenReturn(new SerialIdentity(100L, "FORMAL-100", 99L, null, null));
        assertThrows(
                RuntimeException.class, () -> reader.identity("000001", 100L, "TEST", null, 500));
    }
}
