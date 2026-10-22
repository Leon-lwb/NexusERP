package com.nexus.erp.wm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.nexus.common.trace.*;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.bo.WmTraceAuthorizationBo;
import com.nexus.erp.wm.domain.vo.WmBatchVo;
import com.nexus.erp.wm.mapper.*;
import com.nexus.erp.wm.service.*;
import com.nexus.erp.wm.trace.ErpTraceContext;

import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;

class WmSerialAuthorizationServiceTest {
    @Test
    void productionLotAuthorizationUsesRealWorkOrderBatchAndAppPayload() throws Exception {
        var store = mock(WmSerialInventoryRepository.class);
        var guard = mock(WmProductionRecptWriteGuard.class);
        var batches = mock(IWmBatchService.class);
        var jdbc = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(store, "jdbc", jdbc);
        var wo = new PrdWorkOrder();
        wo.setWoId(1L);
        wo.setWoNo("W1");
        wo.setMaterialId(20L);
        wo.setPlannedQty(new BigDecimal("2"));
        wo.setWoStatus("RELEASED");
        when(guard.lockWorkOrder(1L)).thenReturn(wo);
        when(store.tenant()).thenReturn("t1");
        when(store.policy(20L))
                .thenReturn(new TrackingPolicy(true, true, true, 7L, TrackingStage.STRICT));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        ObjectMapper app = new ObjectMapper();
        when(store.encode(any())).thenAnswer(a -> app.writeValueAsString(a.getArgument(0)));
        WmBatchVo batch = new WmBatchVo();
        batch.setBatchId(50L);
        when(batches.createBatchByMaterial(20L, "PRD_WORK_ORDER", 1L, "W1", 30L, null))
                .thenReturn(batch);
        var bo = new WmTraceAuthorizationBo();
        bo.setErpWoId(1L);
        bo.setMesWoId(2L);
        bo.setWarehouseId(30L);
        bo.setLocationId(40L);
        bo.setPolicyVersion(7);
        bo.setCommandId("lot1");
        var result = new WmTraceAuthorizationService(store, guard, batches).productionLot(bo);
        assertEquals(50L, result.erpBatchId());
        var sequence = inOrder(guard, batches, store);
        sequence.verify(guard).lockWorkOrder(1L);
        sequence.verify(store).policy(20L);
        sequence.verify(guard).validateDraft(eq(wo), any());
        sequence.verify(batches).createBatchByMaterial(20L, "PRD_WORK_ORDER", 1L, "W1", 30L, null);
        var payload = ArgumentCaptor.forClass(String.class);
        verify(store)
                .appendEvent(
                        eq("ERP-LOT-50"),
                        eq("PRODUCTION_LOT"),
                        eq(50L),
                        eq(1L),
                        eq("ERP_PRODUCTION_LOT_AUTHORIZED"),
                        payload.capture());
        JsonNode json = app.readTree(payload.getValue());
        assertEquals("1", json.path("erpWoId").asText());
        assertEquals("2", json.path("mesWoId").asText());
        assertEquals("1", json.path("sourceId").asText());
        assertEquals("2", json.path("quantity").asText());
        assertTrue(json.path("erpBatchId").isTextual());
    }

    @Test
    void batchOnlyDifferentCommandReusesOriginalWorkOrderLotBeforeMesFreeze() throws Exception {
        var f = new LotFixture(false);
        f.authorized(2L, 30L, 40L, 7L);
        var result = f.service.productionLot(f.bo);
        assertEquals(1L, result.sourceId());
        assertEquals(50L, result.erpBatchId());
        assertEquals("ERP-LOT-50", result.eventId());
        verifyNoInteractions(f.batches);
        verify(f.store, never())
                .appendEvent(
                        anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString());
        verify(f.store).authorizedProductionBatchCode(1L, 2L, 50L, 30L);
    }

    @Test
    void batchOnlyOriginalLotCannotBeRepromisedToOtherMesOrderStorageOrPolicy() throws Exception {
        for (int mismatch = 0; mismatch < 4; mismatch++) {
            var f = new LotFixture(false);
            f.authorized(
                    mismatch == 0 ? 3L : 2L,
                    mismatch == 1 ? 31L : 30L,
                    mismatch == 2 ? 41L : 40L,
                    mismatch == 3 ? 8L : 7L);
            assertThrows(RuntimeException.class, () -> f.service.productionLot(f.bo));
            verifyNoInteractions(f.batches);
            verify(f.store, never())
                    .appendEvent(
                            anyString(),
                            anyString(),
                            anyLong(),
                            anyLong(),
                            anyString(),
                            anyString());
        }
    }

    @Test
    void batchOnlyReuseRejectsTamperedAuthorizationRatherThanMintingAnotherLot() throws Exception {
        var f = new LotFixture(false);
        f.authorized(2L, 30L, 40L, 7L);
        f.events.get(0).put("content_hash", "0".repeat(64));
        assertThrows(RuntimeException.class, () -> f.service.productionLot(f.bo));
        verifyNoInteractions(f.batches);
    }

    @Test
    void serialFinishedGoodsMayAuthorizeAnotherErpBatchWithMixedLotDisabled() throws Exception {
        var f = new LotFixture(true);
        f.authorized(2L, 30L, 40L, 7L);
        assertEquals(51L, f.service.productionLot(f.bo).erpBatchId());
        verify(f.batches).createBatchByMaterial(20L, "PRD_WORK_ORDER", 1L, "W1", 30L, null);
    }

    static class LotFixture {
        final WmSerialInventoryRepository store = mock(WmSerialInventoryRepository.class);
        final WmProductionRecptWriteGuard guard = mock(WmProductionRecptWriteGuard.class);
        final IWmBatchService batches = mock(IWmBatchService.class);
        final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        final WmTraceAuthorizationBo bo = new WmTraceAuthorizationBo();
        final WmTraceAuthorizationService service =
                new WmTraceAuthorizationService(store, guard, batches);
        final ObjectMapper app = new ObjectMapper();
        final List<Map<String, Object>> events = new ArrayList<>();

        LotFixture(boolean serial) throws Exception {
            ReflectionTestUtils.setField(store, "jdbc", jdbc);
            when(store.tenant()).thenReturn("t1");
            var wo = new PrdWorkOrder();
            wo.setWoId(1L);
            wo.setWoNo("W1");
            wo.setMaterialId(20L);
            wo.setPlannedQty(new BigDecimal("5"));
            wo.setWoStatus("RELEASED");
            when(guard.lockWorkOrder(1L)).thenReturn(wo);
            when(store.policy(20L))
                    .thenReturn(new TrackingPolicy(true, serial, false, 7L, TrackingStage.STRICT));
            when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(a -> events);
            when(store.encode(any())).thenAnswer(a -> app.writeValueAsString(a.getArgument(0)));
            when(store.authorizedProductionBatchCode(1L, 2L, 50L, 30L)).thenReturn("B50");
            WmBatchVo batch = new WmBatchVo();
            batch.setBatchId(51L);
            when(batches.createBatchByMaterial(20L, "PRD_WORK_ORDER", 1L, "W1", 30L, null))
                    .thenReturn(batch);
            bo.setErpWoId(1L);
            bo.setMesWoId(2L);
            bo.setWarehouseId(30L);
            bo.setLocationId(40L);
            bo.setPolicyVersion(7);
            bo.setCommandId("new-lot-command");
        }

        void authorized(long mes, long warehouse, long location, long policy) throws Exception {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sourceType", "PRODUCTION_OUTPUT");
            payload.put("sourceId", "1");
            payload.put("erpWoId", "1");
            payload.put("mesWoId", Long.toString(mes));
            payload.put("materialId", "20");
            payload.put("erpBatchId", "50");
            payload.put("quantity", "5");
            payload.put("commandId", "original-command");
            payload.put("warehouseId", Long.toString(warehouse));
            payload.put("locationId", Long.toString(location));
            payload.put("policyVersion", Long.toString(policy));
            payload.put(
                    "fingerprint",
                    TraceHashes.fingerprint(
                            "original-command", 1L, mes, warehouse, location, policy));
            String text = app.writeValueAsString(payload);
            var event = new HashMap<String, Object>();
            event.put("payload", text);
            event.put("event_id", "ERP-LOT-50");
            event.put("content_hash", TraceHashes.sha256(text));
            events.add(event);
            when(jdbc.queryForList(contains("FROM wm_batch "), any(Object[].class)))
                    .thenReturn(List.of(Map.of("batch_id", 50L)));
        }
    }

    @Test
    void unReleasedWorkOrderCannotEmitAnAuthorization() {
        var store = mock(WmSerialInventoryRepository.class);
        var guard = mock(WmProductionRecptWriteGuard.class);
        var batches = mock(IWmBatchService.class);
        var wo = new PrdWorkOrder();
        wo.setWoId(1L);
        wo.setWoStatus("CREATED");
        when(guard.lockWorkOrder(1L)).thenReturn(wo);
        var bo = new WmTraceAuthorizationBo();
        bo.setErpWoId(1L);
        assertThrows(
                RuntimeException.class,
                () -> new WmTraceAuthorizationService(store, guard, batches).productionLot(bo));
        verifyNoInteractions(batches);
        verify(store, never())
                .appendEvent(
                        anyString(), anyString(), anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void outboxUsesClaimableNewStatusAndInjectedObjectMapper() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        var context = mock(ErpTraceContext.class);
        when(context.tenant()).thenReturn("t1");
        ObjectMapper app = spy(new ObjectMapper());
        var store =
                new WmSerialInventoryRepository(
                        jdbc,
                        mock(WmMaterialStockMapper.class),
                        mock(WmMaterialStockTxMapper.class),
                        context,
                        app);
        store.appendEvent(
                "e1",
                "PRODUCTION_LOT",
                50L,
                1,
                "ERP_PRODUCTION_LOT_AUTHORIZED",
                "{\"erpBatchId\":\"9007199254740993\",\"quantity\":\"1.0000\"}");
        var invocation =
                mockingDetails(jdbc).getInvocations().stream()
                        .filter(i -> i.getMethod().getName().equals("update"))
                        .findFirst()
                        .orElseThrow();
        assertTrue(invocation.getArgument(0, String.class).contains("'NEW'"));
        Object[] params = (Object[]) invocation.getRawArguments()[1];
        assertEquals(TraceHashes.sha256(params[6].toString()), params[7]);
        verify(app).writeValueAsString(any(JsonNode.class));
        verify(context).requireWrite("t1");
    }

    @Test
    void actualReservationSupportsOriginalStockWithoutLocation() {
        var f = new ReservationFixture();
        assertDoesNotThrow(() -> f.service.reserveBackflush(f.bo));
        verify(f.jdbc, atLeastOnce())
                .update(contains("reserved_quantity=reserved_quantity+"), any(Object[].class));
    }

    @Test
    void invalidReservationSelectionFailsAsDomainErrorBeforeStockWrites() {
        var f = new ReservationFixture();
        f.bo.setSelections(
                Arrays.asList(
                        new WmTraceAuthorizationBo.Selection(101L, 1001L, 50L, BigDecimal.ONE),
                        null));
        RuntimeException error =
                assertThrows(RuntimeException.class, () -> f.service.reserveBackflush(f.bo));
        assertFalse(error instanceof NullPointerException);
        verify(f.store, never()).stock(any());
        verify(f.jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void publicationVersionFollowsRegisteredStreamInsteadOfIdentityOrCallerVersion() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(4L);
        var context = mock(ErpTraceContext.class);
        when(context.tenant()).thenReturn("t1");
        var store =
                new WmSerialInventoryRepository(
                        jdbc,
                        mock(WmMaterialStockMapper.class),
                        mock(WmMaterialStockTxMapper.class),
                        context,
                        new ObjectMapper());
        store.appendEvent(
                "e5",
                "STOCK_POSTING",
                50L,
                91,
                "ERP_INVENTORY_POSTED",
                "{\"identityVersion\":\"91\"}");
        var written =
                mockingDetails(jdbc).getInvocations().stream()
                        .filter(i -> i.getMethod().getName().equals("update"))
                        .findFirst()
                        .orElseThrow();
        Object[] args = (Object[]) written.getRawArguments()[1];
        assertEquals(5L, ((Number) args[4]).longValue());
        verify(jdbc)
                .queryForObject(
                        contains("MAX(aggregate_version)"),
                        eq(Long.class),
                        eq("t1"),
                        eq("STOCK_POSTING"),
                        eq(50L));
    }

    @Test
    void actualBatchReservationCannotRoundAwayFractionalDatabaseQuantity() {
        var f = new ReservationFixture();
        when(f.store.policy(20L))
                .thenReturn(new TrackingPolicy(true, false, true, 7L, TrackingStage.STRICT));
        f.bo.setSelections(
                List.of(
                        new WmTraceAuthorizationBo.Selection(
                                101L, null, 50L, new BigDecimal("0.00001"))));
        var stock = f.store.stock(101L);
        stock.setSnId(null);
        assertThrows(RuntimeException.class, () -> f.service.reserveBackflush(f.bo));
        verify(f.jdbc, never()).update(anyString(), any(Object[].class));
    }

    static class ReservationFixture {
        final WmSerialInventoryRepository store = mock(WmSerialInventoryRepository.class);
        final WmProductionRecptWriteGuard guard = mock(WmProductionRecptWriteGuard.class);
        final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        final WmTraceAuthorizationBo bo = new WmTraceAuthorizationBo();
        final WmTraceAuthorizationService service =
                new WmTraceAuthorizationService(store, guard, mock(IWmBatchService.class));

        ReservationFixture() {
            ReflectionTestUtils.setField(store, "jdbc", jdbc);
            when(store.tenant()).thenReturn("t1");
            var wo = new PrdWorkOrder();
            wo.setWoId(1L);
            wo.setPlanId(9L);
            wo.setWoStatus("RELEASED");
            wo.setBackflushFlag(1);
            when(guard.lockWorkOrder(1L)).thenReturn(wo);
            when(store.policy(20L))
                    .thenReturn(new TrackingPolicy(true, true, true, 7L, TrackingStage.STRICT));
            when(store.row(anyString(), any(Object[].class)))
                    .thenReturn(
                            Map.of(
                                    "required_quantity",
                                    BigDecimal.ONE,
                                    "issued_quantity",
                                    BigDecimal.ZERO));
            when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
            when(jdbc.queryForObject(anyString(), eq(BigDecimal.class), any(Object[].class)))
                    .thenReturn(BigDecimal.ZERO);
            when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
            var stock = new WmMaterialStock();
            stock.setStockId(101L);
            stock.setMaterialId(20L);
            stock.setBatchId(50L);
            stock.setSnId(1001L);
            stock.setWarehouseId(30L);
            stock.setTrackingVersion(2L);
            stock.setVersion(1);
            stock.setFrozenFlag(0L);
            stock.setDelFlag("0");
            stock.setStockQuantity(BigDecimal.ONE);
            stock.setReservedQuantity(BigDecimal.ZERO);
            when(store.stock(101L)).thenReturn(stock);
            when(store.stocks(1001L)).thenReturn(List.of(stock));
            when(store.identity(1001L))
                    .thenReturn(
                            new WmSerialInventoryRepository.Identity(
                                    1001L, 20L, 50L, "SN1001", "IN_STOCK"));
            bo.setErpWoId(1L);
            bo.setMesWoId(2L);
            bo.setRequirementDetailId(500L);
            bo.setMaterialId(20L);
            bo.setWarehouseId(30L);
            bo.setPolicyVersion(7);
            bo.setCommandId("actual-reserve");
            bo.setSelections(
                    List.of(
                            new WmTraceAuthorizationBo.Selection(
                                    101L, 1001L, 50L, BigDecimal.ONE)));
        }
    }
}
