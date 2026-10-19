package com.nexus.erp.wm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.trace.*;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.prd.service.impl.PrdWorkOrderServiceImpl;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.service.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class WmSerialNoGoodOutputTest {
    WmSerialInventoryRepository store;
    WmProductionRecptWriteGuard source;
    WmTraceMaterialConsumptionService material;
    PrdWorkOrderServiceImpl orders;
    JdbcTemplate jdbc;
    WmTraceNoGoodOutputService service;
    PrdWorkOrder wo;
    CompletionManifest manifest;
    List<CompletionManifestLine> lines;

    @BeforeEach
    void setup() {
        store = mock(WmSerialInventoryRepository.class);
        source = mock(WmProductionRecptWriteGuard.class);
        material = mock(WmTraceMaterialConsumptionService.class);
        orders = mock(PrdWorkOrderServiceImpl.class);
        jdbc = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(store, "jdbc", jdbc);
        service = new WmTraceNoGoodOutputService(store, source, material, orders);
        when(store.tenant()).thenReturn("t1");
        wo = new PrdWorkOrder();
        wo.setWoId(1L);
        wo.setMaterialId(20L);
        wo.setWoStatus("IN_PROGRESS");
        wo.setCompletedQty(BigDecimal.ZERO);
        wo.setScrapQty(BigDecimal.ONE);
        when(source.lockWorkOrder(1L)).thenReturn(wo);
        when(source.receipts(wo)).thenReturn(List.of());
        lines =
                List.of(
                        new CompletionManifestLine(
                                1,
                                101L,
                                201L,
                                "SN101",
                                null,
                                null,
                                20L,
                                BigDecimal.ONE,
                                "SCRAP",
                                2));
        manifest =
                new CompletionManifest(
                        900L,
                        1,
                        "t1",
                        2L,
                        1L,
                        20L,
                        1,
                        BigDecimal.ZERO,
                        BigDecimal.ONE,
                        CompletionManifestIntegrity.hash(lines),
                        "FROZEN");
        when(store.policy(20L))
                .thenReturn(new TrackingPolicy(false, true, true, 7L, TrackingStage.STRICT));
        when(store.identity(101L))
                .thenReturn(
                        new WmSerialInventoryRepository.Identity(
                                101L, 20L, null, "SN101", "REGISTERED"));
        when(store.stocks(101L)).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        ObjectMapper app = new ObjectMapper();
        when(store.encode(any())).thenAnswer(a -> app.writeValueAsString(a.getArgument(0)));
    }

    @Test
    void allScrapPersistsOnlyCompletionEvidenceAndActualConsumption() {
        assertFalse(service.post(manifest, lines, List.of()).replay());
        verify(source).validateNoGoodOutput(wo, manifest);
        verify(orders).completeFromManifest(manifest);
        verify(material).consume(manifest, List.of());
        verify(store)
                .appendEvent(
                        eq("ERP-NO-GOOD-900-1"),
                        eq("COMPLETION_RESULT"),
                        eq(900L),
                        eq(1L),
                        eq("ERP_INVENTORY_POSTED"),
                        contains("NO_GOOD_OUTPUT"));
        verify(store, never()).insertStock(any());
        verify(store, never()).insertTx(any());
        verify(store, never()).finish(any(), any(), any());
    }

    @Test
    void alreadyStockedProductCannotBeHiddenByNoGoodOutput() {
        WmMaterialStock stock = new WmMaterialStock();
        stock.setStockQuantity(BigDecimal.ONE);
        stock.setReservedQuantity(BigDecimal.ZERO);
        when(store.stocks(101L)).thenReturn(List.of(stock));
        assertThrows(RuntimeException.class, () -> service.post(manifest, lines, List.of()));
        verify(store, never())
                .appendEvent(
                        anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString());
        verify(store, never()).insertStock(any());
    }

    @Test
    void appliedNoGoodOutputReplaysImmutableProofWithoutReadingCurrentStock() {
        wo.setWoStatus("COMPLETED");
        String payload =
                "{\"outcome\":\"NO_GOOD_OUTPUT\",\"manifestHash\":\""
                        + manifest.contentHash()
                        + "\",\"materialHash\":\""
                        + CompletionAllocationIntegrity.hash(List.of())
                        + "\",\"erpWoId\":\"1\"}";
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "payload",
                                        payload,
                                        "content_hash",
                                        TraceHashes.sha256(payload))));
        assertTrue(service.post(manifest, lines, List.of()).replay());
        verify(material).verifyReplay(manifest, List.of());
        verifyNoInteractions(orders);
        verify(store, never()).stocks(any());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void noGoodReplayRequiresOriginalZeroGoodAndExactScrapQuantity() {
        wo.setWoStatus("COMPLETED");
        wo.setCompletedQty(BigDecimal.ONE);
        String payload =
                "{\"outcome\":\"NO_GOOD_OUTPUT\",\"manifestHash\":\""
                        + manifest.contentHash()
                        + "\",\"materialHash\":\""
                        + CompletionAllocationIntegrity.hash(List.of())
                        + "\",\"erpWoId\":\"1\"}";
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "payload",
                                        payload,
                                        "content_hash",
                                        TraceHashes.sha256(payload))));
        assertThrows(RuntimeException.class, () -> service.post(manifest, lines, List.of()));
        verify(material, never()).verifyReplay(any(), any());
    }

    @Test
    void batchOnlyAllScrapRequiresGenuineProductionLotAuthorization() {
        when(store.policy(20L))
                .thenReturn(new TrackingPolicy(true, false, true, 7L, TrackingStage.STRICT));
        lines =
                List.of(
                        new CompletionManifestLine(
                                1, null, null, null, 50L, 60L, 20L, BigDecimal.ONE, "SCRAP", 2));
        manifest =
                new CompletionManifest(
                        900L,
                        1,
                        "t1",
                        2L,
                        1L,
                        20L,
                        1,
                        BigDecimal.ZERO,
                        BigDecimal.ONE,
                        CompletionManifestIntegrity.hash(lines),
                        "FROZEN");
        when(store.productionLotTarget(1L, 2L, 50L))
                .thenThrow(new IllegalStateException("missing original authorization"));
        assertThrows(RuntimeException.class, () -> service.post(manifest, lines, List.of()));
        verifyNoInteractions(orders, material);
        verify(store, never())
                .appendEvent(
                        anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString());
    }
}
