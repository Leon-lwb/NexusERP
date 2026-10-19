package com.nexus.mes.integration.trace;

import com.nexus.common.trace.*;
import com.nexus.common.trace.port.WorkOrderTrackingPolicyPort;
import com.nexus.mes.trace.MesTraceContext;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MesWorkOrderTrackingPolicyReaderTest {
    JdbcTemplate jdbc;
    MesTraceContext context;
    WorkOrderTrackingPolicyPort bank;
    MesWorkOrderTrackingPolicyReader reader;

    @BeforeEach
    void setup() {
        jdbc = mock(JdbcTemplate.class);
        context = mock(MesTraceContext.class);
        when(context.tenant()).thenReturn("000001");
        bank = mock(WorkOrderTrackingPolicyPort.class);
        reader = new MesWorkOrderTrackingPolicyReader(jdbc, context, bank);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("erp_wo_id", 20L)));
    }

    WorkOrderTrackingPolicySnapshot snapshot() {
        var at = Instant.parse("2026-10-18T00:00:00Z");
        return new WorkOrderTrackingPolicySnapshot(
                "000001",
                20L,
                3L,
                WorkOrderTrackingPolicySnapshot.Provenance.ERP_RELEASE,
                new TrackingPolicy(true, false, true, 1L, TrackingStage.AUDIT),
                1,
                "a".repeat(64),
                "b".repeat(64),
                "c".repeat(64),
                "ERP_RELEASE/20",
                at,
                at,
                null,
                null);
    }

    @Test
    void knownPolicyUsesImmutableReleaseVersionAndRule() {
        when(bank.find("000001", 20L, 3L)).thenReturn(snapshot());
        var frozen = reader.requirePolicy(10L, 3L);
        assertEquals(1L, frozen.version());
        assertTrue(frozen.allowMixedLot());
        verify(context).requireNoTransaction();
    }

    @Test
    void missingHistoricalPolicyDoesNotBecomeCurrentMasterData() {
        when(bank.find("000001", 20L, 3L))
                .thenReturn(
                        WorkOrderTrackingPolicySnapshot.unknown(
                                "000001", 20L, 3L, "NO_RELEASE_POLICY_EVIDENCE"));
        assertThrows(RuntimeException.class, () -> reader.requirePolicy(10L, 3L));
    }

    @Test
    void anotherWorkOrderOrMaterialCannotSupplyThePolicy() {
        when(bank.find("000001", 20L, 9L)).thenReturn(snapshot());
        assertThrows(RuntimeException.class, () -> reader.requirePolicy(10L, 9L));
    }
}
